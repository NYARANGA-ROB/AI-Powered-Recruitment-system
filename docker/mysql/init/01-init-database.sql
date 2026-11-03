-- SmartATS database initialization script
-- Created: 2026-02-14

-- 1. Users table
CREATE TABLE IF NOT EXISTS `users` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'User ID',
    `username` VARCHAR(50) NOT NULL UNIQUE COMMENT 'Username',
    `password` VARCHAR(255) NOT NULL COMMENT 'Password (BCrypt hashed)',
    `email` VARCHAR(100) NOT NULL UNIQUE COMMENT 'Email address',
    `role` ENUM('ADMIN', 'HR', 'INTERVIEWER') NOT NULL DEFAULT 'HR' COMMENT 'Role',
    `daily_ai_quota` INT NOT NULL DEFAULT 100 COMMENT 'Daily AI call quota',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT 'Status: 0-disabled, 1-enabled',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
    INDEX `idx_email` (`email`),
    INDEX `idx_role` (`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User table';

-- 2. Jobs table
CREATE TABLE IF NOT EXISTS `jobs` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Job ID',
    `title` VARCHAR(100) NOT NULL COMMENT 'Job title',
    `department` VARCHAR(50) COMMENT 'Department',
    `description` TEXT NOT NULL COMMENT 'Job description',
    `requirements` TEXT NOT NULL COMMENT 'Job requirements',
    `required_skills` JSON COMMENT 'Required skill tags ["Java", "Spring"]',
    `salary_min` INT COMMENT 'Salary floor (K)',
    `salary_max` INT COMMENT 'Salary ceiling (K)',
    `experience_min` INT DEFAULT 0 COMMENT 'Minimum years of experience',
    `experience_max` INT COMMENT 'Maximum years of experience',
    `education` ENUM('不限', '大专', '本科', '硕士', '博士') DEFAULT '不限' COMMENT 'Required education level; the Chinese labels are the values the application stores and compares',
    `job_type` ENUM('FULL_TIME', 'PART_TIME', 'INTERN') DEFAULT 'FULL_TIME' COMMENT 'Employment type',
    `status` ENUM('DRAFT', 'PUBLISHED', 'CLOSED') DEFAULT 'DRAFT' COMMENT 'Status',
    `creator_id` BIGINT NOT NULL COMMENT 'Creator ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
    INDEX `idx_status` (`status`),
    INDEX `idx_creator` (`creator_id`),
    FULLTEXT INDEX `ft_content` (`title`, `description`, `requirements`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Jobs table';

-- 3. Resume file table
CREATE TABLE IF NOT EXISTS `resumes` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Resume ID',
    `uploader_id` BIGINT NOT NULL COMMENT 'Uploader ID',
    `file_name` VARCHAR(255) NOT NULL COMMENT 'Original file name',
    `file_path` VARCHAR(1024) NOT NULL COMMENT 'Storage path',
    `file_hash` VARCHAR(64) NOT NULL COMMENT 'MD5 hash (used for deduplication)',
    `file_size` BIGINT NOT NULL COMMENT 'File size (bytes)',
    `file_type` VARCHAR(20) NOT NULL COMMENT 'File type: pdf/docx/doc',
    `status` ENUM('PENDING', 'QUEUED', 'PROCESSING', 'SUCCESS', 'FAILED') DEFAULT 'PENDING' COMMENT 'Parsing status',
    `error_message` VARCHAR(500) COMMENT 'Reason the parsing failed',
    `retry_count` INT DEFAULT 0 COMMENT 'Number of retries',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    `processed_at` DATETIME COMMENT 'Time parsing completed',
    UNIQUE INDEX `uk_file_hash` (`file_hash`),
    INDEX `idx_uploader` (`uploader_id`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Resume file table';

-- 4. Candidates table (structured data extracted by AI)
CREATE TABLE IF NOT EXISTS `candidates` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Candidate ID',
    `resume_id` BIGINT NOT NULL UNIQUE COMMENT 'ID of the related resume',
    `name` VARCHAR(50) COMMENT 'Full name',
    `phone` VARCHAR(20) COMMENT 'Mobile phone number',
    `email` VARCHAR(100) COMMENT 'Email address',
    `gender` ENUM('MALE', 'FEMALE', 'UNKNOWN') DEFAULT 'UNKNOWN' COMMENT 'Gender',
    `birth_year` INT COMMENT 'Birth year (maps to Candidate.age)',
    `experience_years` INT COMMENT 'Years of work experience (maps to Candidate.workYears)',
    `highest_education` VARCHAR(20) COMMENT 'Highest education level (maps to Candidate.education)',
    `graduate_school` VARCHAR(100) COMMENT 'Graduating institution (maps to Candidate.school)',
    `major` VARCHAR(100) COMMENT 'Major',
    `graduation_year` INT COMMENT 'Graduation year (maps to Candidate.graduationYear)',
    `current_company` VARCHAR(100) COMMENT 'Current company',
    `current_position` VARCHAR(100) COMMENT 'Current position',
    `skills` JSON COMMENT 'Skill tags ["Java", "Spring Boot"]',
    `work_experiences` JSON COMMENT 'Work experience (maps to Candidate.workExperience)',
    `self_evaluation` TEXT COMMENT 'Self evaluation (maps to Candidate.selfEvaluation)',
    `project_experience` JSON COMMENT 'Project experience (maps to Candidate.projectExperience)',
    `education_history` JSON COMMENT 'Education history (kept, not mapped to the entity)',
    `raw_extracted_json` JSON COMMENT 'Raw AI extraction result (maps to Candidate.rawJson)',
    `confidence_score` DOUBLE COMMENT 'AI parsing confidence (maps to Candidate.confidenceScore)',
    `parsed_at` DATETIME COMMENT 'AI parsing time (maps to Candidate.parsedAt)',
    `vector_id` VARCHAR(100) COMMENT 'Vector database ID',
    `ai_summary` TEXT COMMENT 'Candidate summary generated by AI',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
    INDEX `idx_resume` (`resume_id`),
    INDEX `idx_name` (`name`),
    INDEX `idx_experience` (`experience_years`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Candidates table';

-- 5. Job application table
CREATE TABLE IF NOT EXISTS `job_applications` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Application ID',
    `job_id` BIGINT NOT NULL COMMENT 'Job ID',
    `candidate_id` BIGINT NOT NULL COMMENT 'Candidate ID',
    `match_score` DECIMAL(5,2) COMMENT 'AI match score, 0-100',
    `match_reasons` JSON COMMENT 'Explanation of the match result',
    `match_calculated_at` DATETIME COMMENT 'Time the match score was calculated',
    `status` ENUM('PENDING', 'SCREENING', 'INTERVIEW', 'OFFER', 'REJECTED', 'WITHDRAWN') DEFAULT 'PENDING' COMMENT 'Status',
    `hr_notes` TEXT COMMENT 'HR notes',
    `applied_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Application time',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
    UNIQUE INDEX `uk_job_candidate` (`job_id`, `candidate_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_score` (`match_score` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Job application table';

-- 6. Interview record table
CREATE TABLE IF NOT EXISTS `interview_records` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Interview record ID',
    `application_id` BIGINT NOT NULL COMMENT 'Application ID',
    `interviewer_id` BIGINT NOT NULL COMMENT 'Interviewer ID',
    `round` INT NOT NULL DEFAULT 1 COMMENT 'Interview round number',
    `interview_type` ENUM('PHONE', 'VIDEO', 'ONSITE', 'WRITTEN_TEST') DEFAULT 'VIDEO' COMMENT 'Interview type',
    `scheduled_at` DATETIME NOT NULL COMMENT 'Interview time',
    `duration_minutes` INT DEFAULT 60 COMMENT 'Planned duration',
    `status` ENUM('SCHEDULED', 'COMPLETED', 'CANCELLED', 'NO_SHOW') DEFAULT 'SCHEDULED' COMMENT 'Status',
    `feedback` TEXT COMMENT 'Interview feedback',
    `score` INT COMMENT 'Rating from 1 to 10',
    `recommendation` ENUM('STRONG_YES', 'YES', 'NEUTRAL', 'NO', 'STRONG_NO') COMMENT 'Recommendation level',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
    INDEX `idx_application` (`application_id`),
    INDEX `idx_interviewer` (`interviewer_id`),
    INDEX `idx_scheduled` (`scheduled_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Interview record table';

-- Insert test users
INSERT INTO `users` (`username`, `password`, `email`, `role`, `daily_ai_quota`) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'admin@smartats.com', 'ADMIN', 1000),
('hr01', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'hr01@smartats.com', 'HR', 100)
ON DUPLICATE KEY UPDATE `email` = VALUES(`email`);

-- Note: the passwords above are the BCrypt hashes of 'password123'
