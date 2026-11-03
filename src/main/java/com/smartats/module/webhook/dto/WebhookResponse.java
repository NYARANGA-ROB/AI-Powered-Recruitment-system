package com.smartats.module.webhook.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Webhook configuration response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookResponse {

    private Long id;
    private String url;
    private List<String> events;
    private String description;
    private Boolean enabled;
    private Integer failureCount;
    private LocalDateTime lastSuccessAt;
    private LocalDateTime lastFailureAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Secret hint (only the first 4 and last 4 characters are shown)
     */
    private String secretHint;
}
