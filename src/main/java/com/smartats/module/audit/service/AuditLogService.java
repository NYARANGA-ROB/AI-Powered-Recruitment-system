package com.smartats.module.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartats.module.audit.dto.AuditLogQueryRequest;
import com.smartats.module.audit.dto.AuditLogResponse;
import com.smartats.module.audit.entity.AuditLogEntity;
import com.smartats.module.audit.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Audit log service
 * <p>
 * Provides asynchronous writes and paginated queries so audit logging never affects main business performance.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogMapper auditLogMapper;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Save an audit log asynchronously
     * <p>
     * Writes via a dedicated thread pool without blocking business flows.
     * Write failures do not affect business operations (only an ERROR log is printed).
     *
     * @param entity audit log entity
     */
    @Async("asyncExecutor")
    public void saveAsync(AuditLogEntity entity) {
        try {
            auditLogMapper.insert(entity);
            log.debug("Audit log saved successfully: module={}, operation={}, userId={}",
                    entity.getModule(), entity.getOperation(), entity.getUserId());
        } catch (Exception e) {
            // Audit log write failures must not affect business; only log the error
            log.error("Failed to save audit log: module={}, operation={}, error={}",
                    entity.getModule(), entity.getOperation(), e.getMessage(), e);
        }
    }

    /**
     * Query audit logs with pagination (multi-dimensional filtering)
     *
     * @param request query parameters
     * @return the paginated result
     */
    public Page<AuditLogResponse> queryAuditLogs(AuditLogQueryRequest request) {
        int pageNum = Math.max(1, request.getPageNum());
        int pageSize = Math.min(100, Math.max(1, request.getPageSize()));

        Page<AuditLogEntity> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<AuditLogEntity> wrapper = new LambdaQueryWrapper<>();

        // Filter conditions
        if (request.getUserId() != null) {
            wrapper.eq(AuditLogEntity::getUserId, request.getUserId());
        }
        if (StringUtils.hasText(request.getModule())) {
            wrapper.eq(AuditLogEntity::getModule, request.getModule());
        }
        if (StringUtils.hasText(request.getOperation())) {
            wrapper.eq(AuditLogEntity::getOperation, request.getOperation());
        }
        if (StringUtils.hasText(request.getStatus())) {
            wrapper.eq(AuditLogEntity::getStatus, request.getStatus());
        }
        if (StringUtils.hasText(request.getKeyword())) {
            wrapper.like(AuditLogEntity::getDescription, request.getKeyword());
        }

        // Time range
        if (StringUtils.hasText(request.getStartTime())) {
            wrapper.ge(AuditLogEntity::getCreatedAt, LocalDateTime.parse(request.getStartTime(), FORMATTER));
        }
        if (StringUtils.hasText(request.getEndTime())) {
            wrapper.le(AuditLogEntity::getCreatedAt, LocalDateTime.parse(request.getEndTime(), FORMATTER));
        }

        // Order by time, descending
        wrapper.orderByDesc(AuditLogEntity::getCreatedAt);

        Page<AuditLogEntity> resultPage = auditLogMapper.selectPage(page, wrapper);
        return convertPage(resultPage);
    }

    /**
     * Convert the paginated result
     */
    private Page<AuditLogResponse> convertPage(Page<AuditLogEntity> sourcePage) {
        Page<AuditLogResponse> responsePage = new Page<>(
                sourcePage.getCurrent(), sourcePage.getSize(), sourcePage.getTotal());

        responsePage.setRecords(sourcePage.getRecords().stream()
                .map(this::convertToResponse)
                .toList());

        return responsePage;
    }

    /**
     * Entity -> response DTO
     */
    private AuditLogResponse convertToResponse(AuditLogEntity entity) {
        AuditLogResponse response = new AuditLogResponse();
        response.setId(entity.getId());
        response.setUserId(entity.getUserId());
        response.setUsername(entity.getUsername());
        response.setModule(entity.getModule());
        response.setOperation(entity.getOperation());
        response.setDescription(entity.getDescription());
        response.setMethod(entity.getMethod());
        response.setRequestUrl(entity.getRequestUrl());
        response.setRequestMethod(entity.getRequestMethod());
        response.setRequestParams(entity.getRequestParams());
        response.setRequestIp(entity.getRequestIp());
        response.setStatus(entity.getStatus());
        response.setErrorMessage(entity.getErrorMessage());
        response.setDuration(entity.getDuration());
        response.setCreatedAt(entity.getCreatedAt());
        return response;
    }
}
