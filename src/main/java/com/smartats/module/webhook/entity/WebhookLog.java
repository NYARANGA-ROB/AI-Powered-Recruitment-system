package com.smartats.module.webhook.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Webhook invocation log
 * Records the details of each Webhook invocation for debugging and retries
 */
@Data
@TableName("webhook_logs")
public class WebhookLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Webhook configuration ID
     */
    private Long webhookId;

    /**
     * Event type
     */
    private String eventType;

    /**
     * Request payload (JSON)
     */
    private String payload;

    /**
     * HTTP response status code
     */
    private Integer responseStatus;

    /**
     * HTTP response body
     */
    private String responseBody;

    /**
     * Error message (if delivery failed)
     */
    private String errorMessage;

    /**
     * Invocation status: SUCCESS, FAILED, RETRYING
     */
    private String status;

    /**
     * Retry count
     */
    private Integer retryCount;

    /**
     * Invocation duration (milliseconds)
     */
    private Long duration;

    /**
     * Creation time
     */
    private LocalDateTime createdAt;
}
