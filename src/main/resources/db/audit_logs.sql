-- ============================================================================
-- Audit log table (audit_logs)
-- Created: 2026-02-25
-- Purpose: Records every key business operation in the system for security auditing, issue tracing and compliance
-- ============================================================================

CREATE TABLE IF NOT EXISTS `audit_logs` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'Primary key ID',
    `user_id`        BIGINT       NULL     COMMENT 'ID of the acting user (NULL when not authenticated)',
    `username`       VARCHAR(64)  NULL     COMMENT 'Username of the acting user',
    `module`         VARCHAR(32)  NOT NULL COMMENT 'Business module (JOB/RESUME/CANDIDATE/APPLICATION/INTERVIEW/WEBHOOK/AUTH/ANALYTICS)',
    `operation`      VARCHAR(32)  NOT NULL COMMENT 'Operation type (CREATE/UPDATE/DELETE/LOGIN/UPLOAD, etc.)',
    `description`    VARCHAR(255) NULL     COMMENT 'Operation description',
    `method`         VARCHAR(255) NOT NULL COMMENT 'Java method signature (class.method)',
    `request_url`    VARCHAR(512) NOT NULL COMMENT 'Request URL',
    `request_method` VARCHAR(10)  NOT NULL COMMENT 'HTTP method (GET/POST/PUT/DELETE)',
    `request_params` TEXT         NULL     COMMENT 'Request parameters (JSON, stored after masking, up to 2000 characters)',
    `request_ip`     VARCHAR(45)  NOT NULL COMMENT 'IP address of the requester (IPv6 supported)',
    `user_agent`     VARCHAR(512) NULL     COMMENT 'Client User-Agent',
    `status`         VARCHAR(10)  NOT NULL COMMENT 'Operation result (SUCCESS / FAILED)',
    `error_message`  TEXT         NULL     COMMENT 'Error message when the operation failed',
    `duration`       BIGINT       NOT NULL COMMENT 'Method execution duration (milliseconds)',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Operation time',
    PRIMARY KEY (`id`),

    -- Query by acting user
    INDEX `idx_user_id` (`user_id`),

    -- Query by module + operation type
    INDEX `idx_module_operation` (`module`, `operation`),

    -- Query by time range (common for audit reports)
    INDEX `idx_created_at` (`created_at`),

    -- Filter failed operations by status
    INDEX `idx_status` (`status`),

    -- Composite index: query by user + time range
    INDEX `idx_user_time` (`user_id`, `created_at`)

) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Audit log table';
