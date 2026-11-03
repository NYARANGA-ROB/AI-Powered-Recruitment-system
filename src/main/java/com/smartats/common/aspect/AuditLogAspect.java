package com.smartats.common.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartats.common.annotation.AuditLog;
import com.smartats.module.audit.entity.AuditLogEntity;
import com.smartats.module.audit.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Audit log AOP aspect
 * <p>
 * Intercepts all methods annotated with {@link AuditLog} and records the operation log automatically.
 * <p>
 * Key design points:
 * <ul>
 *   <li>Uses @Around advice to capture both success and failure scenarios</li>
 *   <li>Log persistence runs asynchronously ({@link AuditLogService#saveAsync}) and does not block the main business flow</li>
 *   <li>Extracts the current user, request details and execution duration automatically</li>
 *   <li>Sensitive parameters are masked automatically (passwords, tokens, etc.)</li>
 * </ul>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    /** Maximum length of serialized request parameters (keeps logs from growing too large) */
    private static final int MAX_PARAMS_LENGTH = 2000;

    /** Set of sensitive field names (masked when a parameter name contains one of these keywords) */
    private static final String[] SENSITIVE_FIELDS = {"password", "secret", "token", "authorization", "credential"};

    /**
     * Pointcut: all methods annotated with @AuditLog
     */
    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        long startTime = System.currentTimeMillis();

        // Pre-build the audit log entity (request details are collected before the method runs)
        AuditLogEntity entity = buildAuditLogEntity(joinPoint, auditLog);

        Object result;
        try {
            result = joinPoint.proceed();
            entity.setStatus("SUCCESS");
        } catch (Throwable e) {
            entity.setStatus("FAILED");
            entity.setErrorMessage(truncate(e.getMessage(), 500));
            throw e; // Re-throw; never swallow the exception
        } finally {
            entity.setDuration(System.currentTimeMillis() - startTime);
            entity.setCreatedAt(LocalDateTime.now());

            // Persist asynchronously
            auditLogService.saveAsync(entity);
        }

        return result;
    }

    /**
     * Build the audit log entity
     */
    private AuditLogEntity buildAuditLogEntity(ProceedingJoinPoint joinPoint, AuditLog auditLog) {
        AuditLogEntity entity = new AuditLogEntity();

        // ① Annotation metadata
        entity.setModule(auditLog.module());
        entity.setOperation(auditLog.operation());
        entity.setDescription(auditLog.description());

        // ② Method signature
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();
        entity.setMethod(className + "." + methodName);

        // ③ Current user info (obtained from the Spring Security context)
        extractCurrentUser(entity);

        // ④ HTTP request info
        extractRequestInfo(entity, joinPoint, auditLog, signature);

        return entity;
    }

    /**
     * Extract the currently authenticated user's info
     */
    private void extractCurrentUser(AuditLogEntity entity) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()
                    && authentication.getPrincipal() instanceof Long userId) {
                entity.setUserId(userId);
                // The username comes from Authentication#getName (when available)
                entity.setUsername(authentication.getName());
            }
        } catch (Exception e) {
            log.debug("Failed to get the current user info (possibly an anonymous endpoint): {}", e.getMessage());
        }
    }

    /**
     * Extract HTTP request info
     */
    private void extractRequestInfo(AuditLogEntity entity, ProceedingJoinPoint joinPoint,
                                     AuditLog auditLog, MethodSignature signature) {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return;
            }

            HttpServletRequest request = attributes.getRequest();
            entity.setRequestUrl(request.getRequestURI());
            entity.setRequestMethod(request.getMethod());
            entity.setRequestIp(getClientIp(request));
            entity.setUserAgent(truncate(request.getHeader("User-Agent"), 500));

            // ⑤ Request parameters (recorded based on the annotation configuration)
            if (auditLog.saveParams()) {
                String params = extractParams(joinPoint, signature);
                entity.setRequestParams(truncate(params, MAX_PARAMS_LENGTH));
            }
        } catch (Exception e) {
            log.debug("Failed to extract request info: {}", e.getMessage());
        }
    }

    /**
     * Extract the method parameters as a JSON string
     * <p>
     * Automatically skips parameters that cannot be serialized, such as Authentication and MultipartFile,
     * and masks sensitive fields.
     */
    private String extractParams(ProceedingJoinPoint joinPoint, MethodSignature signature) {
        try {
            String[] paramNames = signature.getParameterNames();
            Object[] paramValues = joinPoint.getArgs();
            if (paramNames == null || paramValues == null) {
                return null;
            }

            Map<String, Object> paramsMap = new LinkedHashMap<>();
            for (int i = 0; i < paramNames.length; i++) {
                String name = paramNames[i];
                Object value = paramValues[i];

                // Skip parameter types that cannot be serialized
                if (value instanceof Authentication
                        || value instanceof HttpServletRequest
                        || value instanceof MultipartFile) {
                    if (value instanceof MultipartFile file) {
                        paramsMap.put(name, "FILE:" + file.getOriginalFilename()
                                + "(" + file.getSize() + " bytes)");
                    }
                    continue;
                }

                // Mask sensitive fields
                if (isSensitiveField(name)) {
                    paramsMap.put(name, "******");
                } else {
                    paramsMap.put(name, value);
                }
            }

            return objectMapper.writeValueAsString(paramsMap);
        } catch (Exception e) {
            log.debug("Failed to serialize request parameters: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Determine whether a field is sensitive
     */
    private boolean isSensitiveField(String fieldName) {
        if (fieldName == null) return false;
        String lower = fieldName.toLowerCase();
        for (String sensitive : SENSITIVE_FIELDS) {
            if (lower.contains(sensitive)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the real client IP (compatible with reverse proxies)
     */
    private String getClientIp(HttpServletRequest request) {
        String[] headers = {
                "X-Forwarded-For",
                "X-Real-IP",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP"
        };

        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
                // X-Forwarded-For may contain several IPs; take the first one
                return ip.split(",")[0].trim();
            }
        }

        return request.getRemoteAddr();
    }

    /**
     * Truncate a string
     */
    private String truncate(String str, int maxLength) {
        if (str == null) return null;
        return str.length() > maxLength ? str.substring(0, maxLength) + "..." : str;
    }
}
