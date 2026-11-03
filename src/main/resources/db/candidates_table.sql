-- ==================== SmartATS candidates table creation script ====================
-- Version: v1.0
-- Created: 2026-02-20
-- Purpose: Stores the structured candidate information produced by AI parsing
-- ========================================================================

-- Drop the table first if it already exists (development environment)
-- DROP TABLE IF EXISTS candidates;

-- Create the candidates table
CREATE TABLE `candidates` (
    -- Primary key
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key ID',

    -- Related resume (1:1 relationship)
    `resume_id` BIGINT NOT NULL UNIQUE COMMENT 'Resume ID',

    -- ========== Basic information ==========
    `name` VARCHAR(100) COMMENT 'Full name',
    `phone` VARCHAR(20) COMMENT 'Mobile phone number',
    `email` VARCHAR(100) COMMENT 'Email address',
    `gender` VARCHAR(10) COMMENT 'Gender (male/female/other)',
    `age` INT COMMENT 'Age',

    -- ========== Education information ==========
    `education` VARCHAR(50) COMMENT 'Education level (senior high school/college/bachelor/master/doctorate)',
    `school` VARCHAR(200) COMMENT 'Graduating institution',
    `major` VARCHAR(200) COMMENT 'Major',
    `graduation_year` INT COMMENT 'Graduation year',

    -- ========== Work information ==========
    `work_years` INT COMMENT 'Years of work experience',
    `current_company` VARCHAR(200) COMMENT 'Current company',
    `current_position` VARCHAR(200) COMMENT 'Current position',

    -- ========== JSON fields (complex structures)==========

    /**
     * Skill list
     * Storage format: ["Java", "Spring Boot", "MySQL", "Redis", "RabbitMQ"]
     */
    `skills` JSON COMMENT 'Skill list (array)',

    /**
     * Work experience
     * Storage format:
     * [
     *   {
     *     "company": "Example Technology Ltd.",
     *     "position": "Back-end development engineer",
     *     "startDate": "2020-01",
     *     "endDate": "2023-06",
     *     "description": "Responsible for developing and maintaining the core business system..."
     *   }
     * ]
     */
    `work_experience` JSON COMMENT 'Work experience (JSON array)',

    /**
     * Project experience
     * Storage format:
     * [
     *   {
     *     "name": "E-commerce platform refactoring",
     *     "role": "Core developer",
     *     "startDate": "2022-03",
     *     "endDate": "2022-12",
     *     "description": "Refactored the legacy system using Spring Cloud...",
     *     "technologies": ["Java", "Spring Cloud", "MySQL", "Redis"]
     *   }
     * ]
     */
    `project_experience` JSON COMMENT 'Project experience (JSON array)',

    /**
     * Self evaluation
     */
    `self_evaluation` TEXT COMMENT 'Self evaluation',

    -- ========== AI parsing metadata ==========

    /**
     * Raw JSON returned by the AI parser
     * Purpose: debugging, re-parsing, auditing
     */
    `raw_json` TEXT COMMENT 'Raw JSON result produced by AI parsing',

    /**
     * Confidence score
     * Range: 0.00 - 1.00
     * Purpose: judges parsing quality; values below the threshold require manual review
     */
    `confidence_score` DECIMAL(3,2) COMMENT 'Confidence score',

    /**
     * Parsing time
     */
    `parsed_at` DATETIME COMMENT 'Time when AI parsing completed',

    -- ========== Audit fields ==========
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',

    -- ========== Indexes ==========
    INDEX idx_resume_id (resume_id) COMMENT 'Resume ID index',
    INDEX idx_name (name) COMMENT 'Name index',
    INDEX idx_phone (phone) COMMENT 'Mobile phone number index',
    INDEX idx_email (email) COMMENT 'Email address index',
    INDEX idx_education (education) COMMENT 'Education level index',
    INDEX idx_work_years (work_years) COMMENT 'Years of experience index',
    INDEX idx_created_at (created_at) COMMENT 'Creation time index'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Candidate information table';

-- ========== Foreign key constraints (optional)==========
-- Note: foreign keys can hurt performance; in production, validation at the application layer is a viable alternative

-- ALTER TABLE candidates
--     ADD CONSTRAINT fk_candidate_resume
--     FOREIGN KEY (resume_id)
--     REFERENCES resumes(id)
--     ON DELETE CASCADE;

-- ========== Sample data ==========
-- Insert test data (optional)
-- INSERT INTO candidates (
--     resume_id, name, phone, email, gender, age,
--     education, school, major, graduation_year,
--     work_years, current_company, current_position,
--     skills, work_experience, project_experience, self_evaluation,
--     raw_json, confidence_score, parsed_at
-- ) VALUES (
--     1,
--     '张三',
--     '13800138000',
--     'zhangsan@example.com',
--     '男',
--     28,
--     '本科',
--     '清华大学',
--     '计算机科学与技术',
--     2018,
--     5,
--     '腾讯科技',
--     '后端开发工程师',
--     JSON_ARRAY('Java', 'Spring Boot', 'MySQL', 'Redis', 'RabbitMQ'),
--     JSON_ARRAY(
--         JSON_OBJECT(
--             'company', '腾讯科技',
--             'position', '后端开发工程师',
--             'startDate', '2020-01',
--             'endDate', '2023-06',
--             'description', '负责核心业务系统开发和维护'
--         )
--     ),
--     JSON_ARRAY(
--         JSON_OBJECT(
--             'name', '电商平台重构',
--             'role', '核心开发',
--             'startDate', '2022-03',
--             'endDate', '2022-12',
--             'description', '使用 Spring Cloud 重构旧系统',
--             'technologies', JSON_ARRAY('Java', 'Spring Cloud', 'MySQL', 'Redis')
--         )
--     ),
--     '5年后端开发经验，熟悉高并发系统设计',
--     '{"name": "张三", ...}',  -- The complete raw JSON
--     0.85,
--     NOW()
-- );

-- ========== Query examples ==========

-- 1. Query all candidates (newest first by creation time)
-- SELECT * FROM candidates ORDER BY created_at DESC;

-- 2. Search by name
-- SELECT * FROM candidates WHERE name LIKE '%张三%';

-- 3. Query candidates with a specific education level
-- SELECT * FROM candidates WHERE education = '本科';

-- 4. Query candidates with 3 or more years of experience
-- SELECT * FROM candidates WHERE work_years >= 3;

-- 5. Query candidates with a specific skill (JSON query)
-- SELECT * FROM candidates WHERE JSON_CONTAINS(skills, '"Java"');

-- 6. Query candidates with a given employer background (JSON query)
-- SELECT * FROM candidates WHERE JSON_CONTAINS(
--     work_experience,
--     JSON_OBJECT('company', '腾讯')
-- );

-- 7. Count candidates per education level
-- SELECT education, COUNT(*) as count
-- FROM candidates
-- GROUP BY education
-- ORDER BY count DESC;

-- 8. Join candidates with their resume information
-- SELECT
--     c.id as candidate_id,
--     c.name,
--     c.phone,
--     c.email,
--     r.file_name as resume_file_name,
--     r.file_url as resume_file_url
-- FROM candidates c
-- LEFT JOIN resumes r ON c.resume_id = r.id
-- WHERE c.name = '张三';
