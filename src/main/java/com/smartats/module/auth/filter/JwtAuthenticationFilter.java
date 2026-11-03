package com.smartats.module.auth.filter;

import com.smartats.common.constants.RedisKeyConstants;
import com.smartats.module.auth.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT authentication filter
 * <p>
 * Responsibilities:
 * 1. Extract the JWT token from the request headers
 * 2. Validate the token (signature, expiry)
 * 3. Check that the token exists in Redis (to reject revoked tokens)
 * 4. Parse the user information and store it in the SecurityContext
 * <p>
 * Redis storage strategy:
 * - Key: jwt:token:{userId}
 * - Value: accessToken
 * - TTL: matches the token expiry (2 hours)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // Step 1: extract the token from the request headers
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        // Check that the header is present and starts with "Bearer "
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(BEARER_PREFIX)) {
            log.debug("The request carries no valid Authorization header; skipping JWT authentication");
            filterChain.doFilter(request, response);
            return;
        }

        // Extract the token (strip the "Bearer " prefix)
        String token = authHeader.substring(BEARER_PREFIX.length());

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // Step 2: parse the token to obtain the user information
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        try {
            // Parse the token claims
            var claims = jwtUtil.parseToken(token);

            // Extract the user information
            Long userId = claims.get("userId", Long.class);
            String username = claims.getSubject();
            String role = claims.get("role", String.class);

            log.debug("JWT token parsed successfully: userId={}, username={}, role={}", userId, username, role);

            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            // Step 3: check that the token exists in Redis (to reject revoked tokens)
            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

            String redisKey = RedisKeyConstants.JWT_TOKEN_KEY_PREFIX + userId;
            log.debug("Looking up the token in Redis: key={}", redisKey);

            String storedToken = redisTemplate.opsForValue().get(redisKey);

            if (storedToken == null) {
                log.warn("The token is not present in Redis and may have been revoked: userId={}, redisKey={}", userId, redisKey);
                filterChain.doFilter(request, response);
                return;
            }

            // Verify that the token matches (prevents token substitution)
            if (!storedToken.equals(token)) {
                log.warn("The token does not match the one stored in Redis: userId={}, storedToken={}, requestToken={}",
                        userId, storedToken.substring(0, Math.min(20, storedToken.length())) + "...",
                        token.substring(0, Math.min(20, token.length())) + "...");
                filterChain.doFilter(request, response);
                return;
            }

            log.debug("Token passed Redis validation: userId={}", userId);

            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            // Step 4: validate the token (signature, expiry)
            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

            if (!jwtUtil.validateToken(token)) {
                log.warn("Token validation failed (invalid or expired): userId={}", userId);
                filterChain.doFilter(request, response);
                return;
            }

            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            // Step 5: check whether the SecurityContext is already authenticated (avoids duplicate authentication)
            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

            if (SecurityContextHolder.getContext().getAuthentication() != null) {
                log.debug("The SecurityContext already holds authentication info; skipping JWT authentication");
                filterChain.doFilter(request, response);
                return;
            }

            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            // Step 6: create the Authentication object and store it in the SecurityContext
            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

            // Build the authority list (a simple implementation for now; it can later be extended to RBAC)
            var authorities = Collections.singletonList(
                    new SimpleGrantedAuthority(RedisKeyConstants.ROLE_PREFIX + role)
            );

            // Create the authentication object
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userId,           // principal: use userId as the subject
                            null,            // credentials: the password is not needed
                            authorities       // authorities: the user permissions
                    );

            // Set the authentication details (including IP, SessionId and so on)
            authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request)
            );

            // Store it in the SecurityContext
            SecurityContextHolder.getContext().setAuthentication(authentication);

            log.info("JWT authentication succeeded: userId={}, username={}, role={}, ip={}",
                    userId, username, role, request.getRemoteAddr());

        } catch (Exception e) {
            log.error("JWT authentication error: {}", e.getMessage(), e);
            // Clear the SecurityContext on failure
            SecurityContextHolder.clearContext();
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // Step 7: continue with the remaining filters
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        filterChain.doFilter(request, response);
    }
}