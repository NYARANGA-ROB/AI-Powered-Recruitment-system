package com.smartats.module.webhook.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Webhook event payload
 * Data format sent to the callback URL configured by the user
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebhookPayload {

    /**
     * Event ID (unique identifier)
     */
    private String eventId;

    /**
     * Event type (e.g. resume.uploaded, resume.parse_completed)
     */
    private String eventType;

    /**
     * Time when the event occurred
     */
    private LocalDateTime timestamp;

    /**
     * Event data (content varies by event type)
     */
    private Map<String, Object> data;

    /**
     * Signature (used to verify request authenticity)
     */
    private String signature;

    /**
     * Webhook version
     */
    private String version = "1.0";
}
