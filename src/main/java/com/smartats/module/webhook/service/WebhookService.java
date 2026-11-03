package com.smartats.module.webhook.service;

import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartats.common.exception.BusinessException;
import com.smartats.common.result.ResultCode;
import com.smartats.module.webhook.dto.WebhookCreateRequest;
import com.smartats.module.webhook.dto.WebhookPayload;
import com.smartats.module.webhook.dto.WebhookResponse;
import com.smartats.module.webhook.entity.WebhookConfig;
import com.smartats.module.webhook.entity.WebhookLog;
import com.smartats.module.webhook.enums.WebhookEventType;
import com.smartats.module.webhook.mapper.WebhookConfigMapper;
import com.smartats.module.webhook.mapper.WebhookLogMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Webhook service
 * Responsible for Webhook configuration management and event delivery
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final WebhookConfigMapper webhookConfigMapper;
    private final WebhookLogMapper webhookLogMapper;
    private final ObjectMapper objectMapper;

    /**
     * HTTP client (with connection pool and timeout configuration)
     */
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    /**
     * Maximum number of consecutive failures
     */
    private static final int MAX_FAILURES = 5;

    /**
     * Create Webhook configuration
     */
    @Transactional(rollbackFor = Exception.class)
    public WebhookResponse createWebhook(Long userId, WebhookCreateRequest request) {
        // Validate the event types
        for (String event : request.getEvents()) {
            if (WebhookEventType.fromCode(event) == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Invalid event type: " + event);
            }
        }

        // Generate a random secret
        String secret = UUID.randomUUID().toString().replace("-", "");

        WebhookConfig config = new WebhookConfig();
        config.setUserId(userId);
        config.setUrl(request.getUrl());
        config.setEvents(String.join(",", request.getEvents()));
        config.setSecret(secret);
        config.setEnabled(true);
        config.setDescription(request.getDescription());
        config.setFailureCount(0);
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());

        webhookConfigMapper.insert(config);

        log.info("Created Webhook configuration: userId={}, webhookId={}, url={}", userId, config.getId(), request.getUrl());

        return toResponse(config);
    }

    /**
     * List all Webhook configurations of the user
     */
    public List<WebhookResponse> getUserWebhooks(Long userId) {
        LambdaQueryWrapper<WebhookConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WebhookConfig::getUserId, userId);
        wrapper.orderByDesc(WebhookConfig::getCreatedAt);

        List<WebhookConfig> configs = webhookConfigMapper.selectList(wrapper);
        return configs.stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Delete Webhook configuration
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteWebhook(Long userId, Long webhookId) {
        LambdaQueryWrapper<WebhookConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WebhookConfig::getId, webhookId);
        wrapper.eq(WebhookConfig::getUserId, userId);

        int deleted = webhookConfigMapper.delete(wrapper);

        if (deleted > 0) {
            log.info("Deleted Webhook configuration: userId={}, webhookId={}", userId, webhookId);
        }
    }

    /**
     * Send a Webhook event (asynchronous)
     */
    @Async("webhookExecutor")
    public void sendEvent(WebhookEventType eventType, Map<String, Object> data) {
        String eventCode = eventType.getCode();

        // Query all Webhook configurations subscribed to this event
        List<WebhookConfig> webhooks = findWebhooksByEventType(eventType);

        if (webhooks.isEmpty()) {
            log.debug("No Webhook subscribed to event: {}", eventCode);
            return;
        }

        log.info("Sending Webhook event: {}, subscriber count: {}", eventCode, webhooks.size());

        // Deliver concurrently to all subscribers
        webhooks.forEach(webhook -> {
            try {
                sendWebhook(webhook, eventType, data);
            } catch (Exception e) {
                log.error("Failed to send Webhook: webhookId={}, event={}", webhook.getId(), eventCode, e);
            }
        });
    }

    /**
     * Send a single Webhook
     */
    private void sendWebhook(WebhookConfig webhook, WebhookEventType eventType, Map<String, Object> data) {
        String eventCode = eventType.getCode();
        long startTime = System.currentTimeMillis();

        // Check whether the configuration is enabled
        if (!webhook.getEnabled()) {
            log.debug("Webhook disabled, skipping delivery: webhookId={}", webhook.getId());
            return;
        }

        // Check the failure count
        if (webhook.getFailureCount() != null && webhook.getFailureCount() >= MAX_FAILURES) {
            log.warn("Webhook failure count too high, auto-disabling: webhookId={}, failureCount={}",
                    webhook.getId(), webhook.getFailureCount());
            webhook.setEnabled(false);
            webhookConfigMapper.updateById(webhook);
            return;
        }

        try {
            // Build the payload
            WebhookPayload payload = buildPayload(eventType, data);

            // Generate the signature
            String signature = generateSignature(payload, webhook.getSecret());
            payload.setSignature(signature);

            // Serialize to JSON
            String jsonPayload = objectMapper.writeValueAsString(payload);

            // Send the HTTP request
            RequestBody body = RequestBody.create(
                    jsonPayload,
                    MediaType.parse("application/json; charset=utf-8")
            );

            Request request = new Request.Builder()
                    .url(webhook.getUrl())
                    .post(body)
                    .addHeader("X-Webhook-Event", eventCode)
                    .addHeader("X-Webhook-Signature", signature)
                    .addHeader("X-Webhook-ID", payload.getEventId())
                    .addHeader("User-Agent", "SmartATS-Webhook/1.0")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                long duration = System.currentTimeMillis() - startTime;

                if (response.isSuccessful()) {
                    handleSuccess(webhook, eventType, jsonPayload, response, duration);
                } else {
                    handleFailure(webhook, eventType, jsonPayload, response, duration, "HTTP " + response.code());
                }
            }

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            handleFailure(webhook, eventType, null, null, duration, e.getMessage());
        }
    }

    /**
     * Handle successful delivery
     */
    private void handleSuccess(WebhookConfig webhook, WebhookEventType eventType,
                               String payload, Response response, long duration) {
        String eventCode = eventType.getCode();

        // Reset the failure count
        webhook.setFailureCount(0);
        webhook.setLastSuccessAt(LocalDateTime.now());
        webhookConfigMapper.updateById(webhook);

        // Record the log
        WebhookLog webhookLog = new WebhookLog();
        webhookLog.setWebhookId(webhook.getId());
        webhookLog.setEventType(eventCode);
        webhookLog.setPayload(payload);
        webhookLog.setResponseStatus(response.code());
        webhookLog.setStatus("SUCCESS");
        webhookLog.setRetryCount(0);
        webhookLog.setDuration(duration);
        webhookLog.setCreatedAt(LocalDateTime.now());
        webhookLogMapper.insert(webhookLog);

        log.info("Webhook delivered successfully: webhookId={}, event={}, duration={}ms",
                webhook.getId(), eventCode, duration);
    }

    /**
     * Handle delivery failure
     */
    private void handleFailure(WebhookConfig webhook, WebhookEventType eventType,
                               String payload, Response response, long duration, String errorMessage) {
        String eventCode = eventType.getCode();

        // Increment the failure count
        int newFailureCount = (webhook.getFailureCount() == null ? 0 : webhook.getFailureCount()) + 1;
        webhook.setFailureCount(newFailureCount);
        webhook.setLastFailureAt(LocalDateTime.now());

        // Threshold reached, disable automatically
        if (newFailureCount >= MAX_FAILURES) {
            webhook.setEnabled(false);
            log.error("Webhook failure count reached the threshold, auto-disabling: webhookId={}, failures={}",
                    webhook.getId(), newFailureCount);
        }

        webhookConfigMapper.updateById(webhook);

        // Record the log
        WebhookLog webhookLog = new WebhookLog();
        webhookLog.setWebhookId(webhook.getId());
        webhookLog.setEventType(eventCode);
        webhookLog.setPayload(payload);
        webhookLog.setResponseStatus(response != null ? response.code() : null);
        webhookLog.setErrorMessage(errorMessage);
        webhookLog.setStatus("FAILED");
        webhookLog.setRetryCount(0);
        webhookLog.setDuration(duration);
        webhookLog.setCreatedAt(LocalDateTime.now());
        webhookLogMapper.insert(webhookLog);

        log.error("Webhook delivery failed: webhookId={}, event={}, error={}",
                webhook.getId(), eventCode, errorMessage);
    }

    /**
     * Query all Webhooks subscribed to the given event
     */
    private List<WebhookConfig> findWebhooksByEventType(WebhookEventType eventType) {
        LambdaQueryWrapper<WebhookConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WebhookConfig::getEnabled, true);
        wrapper.like(WebhookConfig::getEvents, eventType.getCode());
        return webhookConfigMapper.selectList(wrapper);
    }

    /**
     * Build the payload
     */
    private WebhookPayload buildPayload(WebhookEventType eventType, Map<String, Object> data) {
        return WebhookPayload.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType.getCode())
                .timestamp(LocalDateTime.now())
                .data(data)
                .build();
    }

    /**
     * Generate the signature
     * Using the HMAC-SHA256 algorithm
     */
    private String generateSignature(WebhookPayload payload, String secret) {
        try {
            // Convert the payload to JSON (without the signature field)
            String json = objectMapper.writeValueAsString(payload);

            // HMAC-SHA256 signature
            return "sha256=" + SecureUtil.hmacSha256(secret).digestHex(json);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "Failed to generate signature");
        }
    }

    /**
     * Convert to the response object
     */
    private WebhookResponse toResponse(WebhookConfig config) {
        List<String> events = Arrays.asList(config.getEvents().split(","));

        // Secret hint (only the first 4 and last 4 characters are shown)
        String secretHint = "";
        if (config.getSecret() != null && config.getSecret().length() > 8) {
            secretHint = config.getSecret().substring(0, 4) + "****" +
                    config.getSecret().substring(config.getSecret().length() - 4);
        }

        return WebhookResponse.builder()
                .id(config.getId())
                .url(config.getUrl())
                .events(events)
                .description(config.getDescription())
                .enabled(config.getEnabled())
                .failureCount(config.getFailureCount())
                .lastSuccessAt(config.getLastSuccessAt())
                .lastFailureAt(config.getLastFailureAt())
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .secretHint(secretHint)
                .build();
    }

    /**
     * Test the Webhook (synchronous delivery, returns the result immediately)
     *
     * @param userId    current user ID (used for authorization checks)
     * @param webhookId Webhook configuration ID
     */
    public boolean testWebhook(Long userId, Long webhookId) {
        // Query the Webhook configuration (also verifying ownership)
        LambdaQueryWrapper<WebhookConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WebhookConfig::getId, webhookId)
               .eq(WebhookConfig::getUserId, userId);
        WebhookConfig config = webhookConfigMapper.selectOne(wrapper);

        if (config == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Webhook not found or not accessible: webhookId=" + webhookId);
        }

        // Build the test data
        Map<String, Object> testData = new HashMap<>();
        testData.put("message", "This is a test event from SmartATS");
        testData.put("webhookId", webhookId);
        testData.put("timestamp", System.currentTimeMillis());

        WebhookPayload payload = buildPayload(WebhookEventType.RESUME_UPLOADED, testData);
        payload.getData().put("_test", true);

        String signature = generateSignature(payload, config.getSecret());
        payload.setSignature(signature);

        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);

            RequestBody body = RequestBody.create(
                    jsonPayload,
                    MediaType.parse("application/json; charset=utf-8")
            );

            Request request = new Request.Builder()
                    .url(config.getUrl())
                    .post(body)
                    .addHeader("X-Webhook-Event", "test")
                    .addHeader("X-Webhook-Signature", signature)
                    .addHeader("X-Webhook-ID", payload.getEventId())
                    .addHeader("X-Webhook-Test", "true")
                    .addHeader("User-Agent", "SmartATS-Webhook/1.0")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                boolean success = response.isSuccessful();
                log.info("Test Webhook {}: webhookId={}, url={}, status={}",
                        success ? "succeeded" : "failed", webhookId, config.getUrl(), response.code());
                return success;
            }
        } catch (Exception e) {
            log.error("Test Webhook failed: webhookId={}, url={}, error={}",
                    webhookId, config.getUrl(), e.getMessage());
            return false;
        }
    }

    @PreDestroy
    public void destroy() {
        // Shut down the HTTP client connection pool
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
}
