package com.smartats.module.webhook.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Webhook configuration entity
 * Stores the Webhook URL configured by the user and the subscribed event types
 */
@Data
@TableName("webhook_configs")
public class WebhookConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * User ID
     */
    private Long userId;

    /**
     * Webhook URL (callback address that receives events)
     */
    private String url;

    /**
     * Subscribed event types (JSON array, e.g. ["resume.uploaded", "resume.parse_completed"])
     */
    @TableField(value = "events")
    private String events;

    /**
     * Signing secret (HMAC-SHA256)
     */
    private String secret;

    /**
     * Whether the configuration is enabled
     */
    private Boolean enabled;

    /**
     * Description/notes
     */
    private String description;

    /**
     * Failure count (automatically disabled once consecutive failures reach the threshold)
     */
    private Integer failureCount;

    /**
     * Time of the last failure
     */
    private LocalDateTime lastFailureAt;

    /**
     * Time of the last success
     */
    private LocalDateTime lastSuccessAt;

    /**
     * Creation time
     */
    private LocalDateTime createdAt;

    /**
     * Update time
     */
    private LocalDateTime updatedAt;
}
