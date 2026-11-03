package com.smartats.module.webhook.controller;

import com.smartats.common.annotation.AuditLog;
import com.smartats.common.result.Result;
import com.smartats.module.webhook.dto.WebhookCreateRequest;
import com.smartats.module.webhook.dto.WebhookResponse;
import com.smartats.module.webhook.service.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Webhook Management", description = "Webhook configuration CRUD, 13 event types, HMAC-SHA256 signing, asynchronous delivery")
@Slf4j
@RestController
@RequestMapping("/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookService webhookService;

    /**
     * Create Webhook configuration
     * POST /api/v1/webhooks
     */
    @Operation(summary = "Create Webhook configuration", description = "Automatically generates an HMAC secret")
    @AuditLog(module = "Webhook", operation = "CREATE", description = "Create Webhook configuration")
    @PostMapping
    public Result<WebhookResponse> createWebhook(
            @Valid @RequestBody WebhookCreateRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();

        WebhookResponse response = webhookService.createWebhook(userId, request);

        log.info("Creating Webhook: userId={}, url={}", userId, request.getUrl());

        return Result.success(response);
    }

    /**
     * List all Webhook configurations of the user
     * GET /api/v1/webhooks
     */
    @Operation(summary = "List all Webhook configurations of the user")
    @GetMapping
    public Result<List<WebhookResponse>> getWebhooks(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();

        List<WebhookResponse> webhooks = webhookService.getUserWebhooks(userId);

        return Result.success(webhooks);
    }

    /**
     * Delete Webhook configuration
     * DELETE /api/v1/webhooks/{id}
     */
    @Operation(summary = "Delete Webhook configuration")
    @AuditLog(module = "Webhook", operation = "DELETE", description = "Delete Webhook configuration")
    @DeleteMapping("/{id}")
    public Result<Void> deleteWebhook(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();

        webhookService.deleteWebhook(userId, id);

        log.info("Deleting Webhook: userId={}, webhookId={}", userId, id);

        return Result.success();
    }

    /**
     * Test Webhook (sends a test event and returns the result synchronously)
     * POST /api/v1/webhooks/{id}/test
     */
    @Operation(summary = "Test Webhook", description = "Sends a test event and returns the delivery result synchronously")
    @PostMapping("/{id}/test")
    public Result<Boolean> testWebhook(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();

        boolean success = webhookService.testWebhook(userId, id);

        log.info("Testing Webhook: userId={}, webhookId={}, success={}", userId, id, success);

        return Result.success(success);
    }
}
