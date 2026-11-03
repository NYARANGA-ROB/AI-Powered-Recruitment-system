-- Resume table
USE smartats;

DROP TABLE IF EXISTS `resumes`;

CREATE TABLE `resumes` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Resume ID',
    `user_id` BIGINT NOT NULL COMMENT 'User ID',
    `file_name` VARCHAR(255) NOT NULL COMMENT 'File name',
    `file_path` VARCHAR(500) NOT NULL COMMENT 'File path (MinIO objectName)',
    `file_url` VARCHAR(500) NOT NULL COMMENT 'File access URL',
    `file_size` BIGINT NOT NULL COMMENT 'File size (bytes)',
    `file_hash` VARCHAR(32) NOT NULL UNIQUE COMMENT 'MD5 hash of the file (deduplication)',
    `file_type` VARCHAR(50) NOT NULL COMMENT 'File type (e.g. application/pdf)',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PARSING' COMMENT 'Parsing status: PARSING, COMPLETED, FAILED',
    `error_message` TEXT COMMENT 'Error message (recorded on failure)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',

    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_file_hash` (`file_hash`),
    INDEX `idx_status` (`status`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Resume table';

-- Verify that the table was created successfully
SELECT 'Table resumes created successfully' AS result;
