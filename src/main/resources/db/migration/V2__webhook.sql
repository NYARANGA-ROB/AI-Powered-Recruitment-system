-- ================================================================
-- Database tables for the Webhook feature
-- ================================================================

-- Webhook configuration table
CREATE TABLE IF NOT EXISTS `webhook_configs` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT 'Primary key ID',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT 'User ID',
    `url` VARCHAR(500) NOT NULL COMMENT 'Webhook callback URL',
    `events` VARCHAR(500) NOT NULL COMMENT 'Subscribed event types (comma-separated)',
    `secret` VARCHAR(100) NOT NULL COMMENT 'Signing secret',
    `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'Whether the configuration is enabled',
    `description` VARCHAR(500) DEFAULT NULL COMMENT 'Description/notes',
    `failure_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Consecutive failure count',
    `last_failure_at` DATETIME DEFAULT NULL COMMENT 'Time of the last failure',
    `last_success_at` DATETIME DEFAULT NULL COMMENT 'Time of the last success',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',

    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Webhook configuration table';

-- Webhook delivery log table
CREATE TABLE IF NOT EXISTS `webhook_logs` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT 'Primary key ID',
    `webhook_id` BIGINT UNSIGNED NOT NULL COMMENT 'Webhook configuration ID',
    `event_type` VARCHAR(50) NOT NULL COMMENT 'Event type',
    `payload` TEXT COMMENT 'Request payload (JSON)',
    `response_status` INT UNSIGNED DEFAULT NULL COMMENT 'HTTP response status code',
    `response_body` TEXT COMMENT 'HTTP response body',
    `error_message` TEXT COMMENT 'Error message',
    `status` VARCHAR(20) NOT NULL COMMENT 'Delivery status: SUCCESS, FAILED, RETRYING',
    `retry_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Number of retries',
    `duration` BIGINT UNSIGNED DEFAULT NULL COMMENT 'Delivery duration (milliseconds)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',

    INDEX `idx_webhook_id` (`webhook_id`),
    INDEX `idx_event_type` (`event_type`),
    INDEX `idx_status` (`status`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Webhook delivery log table';
