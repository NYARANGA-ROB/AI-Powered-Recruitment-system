ALTER TABLE `jobs`
    ADD COLUMN `view_count` INT NOT NULL DEFAULT 0 COMMENT 'Job detail view count',
    ADD COLUMN `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT 'Logical delete flag';
