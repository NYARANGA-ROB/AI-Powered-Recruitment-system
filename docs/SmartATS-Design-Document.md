# SmartATS - Intelligent Recruitment and Resume Analysis System

## 📌 Project Overview

**SmartATS** is an intelligent recruitment management system for HR. The system supports core capabilities such as batch resume upload, automatic AI parsing that extracts structured information, and RAG-based semantic talent search.

### Technology Stack

| Component | Technology | Purpose |
|-----------|------------|---------|
| Core framework | Spring Boot 3.x | Base framework |
| ORM | MyBatis-Plus | Database access |
| Database | MySQL 8.0 | Business data storage |
| Cache / rate limiting / locks | Redis + Redisson | High-performance caching, distributed locks, rate limiting |
| Message queue | RabbitMQ | Asynchronous task decoupling |
| AI integration | Spring AI | LLM calls, embeddings, RAG |
| Vector database | Milvus / PgVector | Resume vector storage and retrieval |

---

## 🏗️ System Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              SmartATS Architecture                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   ┌─────────┐     ┌─────────────────────────────────────────────────────┐  │
│   │   HR    │────▶│                   API Gateway                       │  │
│   │  Client │     │  (Rate Limiting via Redis + Token Bucket/Lua)       │  │
│   └─────────┘     └─────────────────────────────────────────────────────┘  │
│                                      │                                      │
│                                      ▼                                      │
│   ┌─────────────────────────────────────────────────────────────────────┐  │
│   │                        Spring Boot Application                       │  │
│   │  ┌───────────────┐  ┌───────────────┐  ┌───────────────────────┐   │  │
│   │  │ Resume Module │  │  Job Module   │  │   Search Module       │   │  │
│   │  │  - Upload     │  │  - CRUD       │  │   - AI Semantic Search│   │  │
│   │  │  - Status     │  │  - Cache      │  │   - Hybrid Query      │   │  │
│   │  └───────┬───────┘  └───────────────┘  └───────────────────────┘   │  │
│   │          │                                         │                │  │
│   └──────────┼─────────────────────────────────────────┼────────────────┘  │
│              │                                         │                    │
│              ▼                                         ▼                    │
│   ┌──────────────────┐                    ┌──────────────────────────┐     │
│   │    RabbitMQ      │                    │      Spring AI           │     │
│   │  ┌────────────┐  │                    │  ┌────────────────────┐  │     │
│   │  │ resume.queue│ │                    │  │ Embedding Model    │  │     │
│   │  └─────┬──────┘  │                    │  │ (text-embedding)   │  │     │
│   │        │         │                    │  ├────────────────────┤  │     │
│   │  ┌─────▼──────┐  │                    │  │ Chat Model (GPT/   │  │     │
│   │  │ DLX Queue  │  │                    │  │ DeepSeek/Ollama)   │  │     │
│   │  │ (dead letter)│ │                    │  └────────────────────┘  │     │
│   │  └────────────┘  │                    └──────────────────────────┘     │
│   └──────────────────┘                                 │                    │
│              │                                         │                    │
│              ▼                                         ▼                    │
│   ┌──────────────────────────────────────────────────────────────────┐     │
│   │                        Resume Parser Consumer                     │     │
│   │   1. Acquire Redisson Lock (by file MD5)                         │     │
│   │   2. Call Spring AI for extraction                               │     │
│   │   3. Store structured data to MySQL                              │     │
│   │   4. Store embedding to Vector DB                                │     │
│   │   5. Update Redis task status                                    │     │
│   └──────────────────────────────────────────────────────────────────┘     │
│                                                                             │
│   ┌───────────────┐   ┌───────────────┐   ┌───────────────────────────┐   │
│   │    MySQL      │   │    Redis      │   │    Milvus/PgVector        │   │
│   │  - jobs       │   │  - task:*     │   │  - resume_vectors         │   │
│   │  - candidates │   │  - rate:*     │   │    (id, embedding,        │   │
│   │  - resumes    │   │  - lock:*     │   │     metadata)             │   │
│   │  - users      │   │  - cache:*    │   │                           │   │
│   └───────────────┘   └───────────────┘   └───────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 📊 Database Design (MySQL)

### ER Diagram

```
┌──────────────┐       ┌──────────────┐       ┌──────────────────┐
│    users     │       │    jobs      │       │   candidates     │
├──────────────┤       ├──────────────┤       ├──────────────────┤
│ id (PK)      │       │ id (PK)      │       │ id (PK)          │
│ username     │──┐    │ title        │    ┌──│ resume_id (FK)   │
│ password     │  │    │ description  │    │  │ name             │
│ email        │  │    │ requirements │    │  │ phone            │
│ role         │  │    │ salary_range │    │  │ email            │
│ daily_quota  │  │    │ status       │    │  │ skills (JSON)    │
│ created_at   │  │    │ creator_id(FK)│◀──┘  │ experience_years │
└──────────────┘  │    │ created_at   │       │ education        │
                  │    └──────────────┘       │ extracted_data   │
                  │                           │ created_at       │
                  │    ┌───────────────┐       └──────────────────┘
                  │    │   resumes    │              ▲
                  │    ├──────────────┤              │
                  └───▶│ id (PK)      │──────────────┘
                       │ uploader_id  │
                       │ file_path    │
                       │ file_hash    │ (MD5, used for de-duplication)
                       │ file_name    │
                       │ file_size    │
                       │ status       │ (PENDING/PROCESSING/SUCCESS/FAILED)
                       │ error_msg    │
                       │ created_at   │
                       └──────────────┘

┌──────────────────────┐       ┌──────────────────────┐
│  interview_records   │       │   job_applications   │
├──────────────────────┤       ├──────────────────────┤
│ id (PK)              │       │ id (PK)              │
│ candidate_id (FK)    │       │ job_id (FK)          │
│ job_id (FK)          │       │ candidate_id (FK)    │
│ interviewer_id (FK)  │       │ status               │
│ round                │       │ match_score          │ (match score computed by AI)
│ feedback             │       │ applied_at           │
│ score                │       └──────────────────────┘
│ status               │
│ scheduled_at         │
└──────────────────────┘
```

### Detailed Table Definitions

#### 1. users - User table
```sql
CREATE TABLE `users` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `username` VARCHAR(50) NOT NULL UNIQUE,
    `password` VARCHAR(255) NOT NULL,
    `email` VARCHAR(100) NOT NULL UNIQUE,
    `role` ENUM('ADMIN', 'HR', 'INTERVIEWER') NOT NULL DEFAULT 'HR',
    `daily_ai_quota` INT NOT NULL DEFAULT 100 COMMENT 'Daily AI call quota',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0-disabled, 1-enabled',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_email` (`email`),
    INDEX `idx_role` (`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

#### 2. jobs - Job table
```sql
CREATE TABLE `jobs` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `title` VARCHAR(100) NOT NULL,
    `department` VARCHAR(50),
    `description` TEXT NOT NULL COMMENT 'Job description',
    `requirements` TEXT NOT NULL COMMENT 'Job requirements',
    `required_skills` JSON COMMENT 'Required skill tags ["Java", "Spring"]',
    `salary_min` INT COMMENT 'Minimum salary (K)',
    `salary_max` INT COMMENT 'Maximum salary (K)',
    `experience_min` INT DEFAULT 0 COMMENT 'Minimum years of experience',
    `experience_max` INT COMMENT 'Maximum years of experience',
    `education` ENUM('ANY', 'COLLEGE', 'BACHELOR', 'MASTER', 'DOCTORATE') DEFAULT 'ANY',
    `job_type` ENUM('FULL_TIME', 'PART_TIME', 'INTERN') DEFAULT 'FULL_TIME',
    `status` ENUM('DRAFT', 'PUBLISHED', 'CLOSED') DEFAULT 'DRAFT',
    `creator_id` BIGINT NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_status` (`status`),
    INDEX `idx_creator` (`creator_id`),
    FULLTEXT INDEX `ft_content` (`title`, `description`, `requirements`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

#### 3. resumes - Resume file table
```sql
CREATE TABLE `resumes` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `uploader_id` BIGINT NOT NULL COMMENT 'Uploader ID',
    `file_name` VARCHAR(255) NOT NULL COMMENT 'Original file name',
    `file_path` VARCHAR(1024) NOT NULL COMMENT 'Storage path',
    `file_hash` VARCHAR(64) NOT NULL COMMENT 'MD5 hash, used for de-duplication',
    `file_size` BIGINT NOT NULL COMMENT 'File size (bytes)',
    `file_type` VARCHAR(20) NOT NULL COMMENT 'pdf/docx/doc',
    `status` ENUM('PENDING', 'QUEUED', 'PROCESSING', 'SUCCESS', 'FAILED') DEFAULT 'PENDING',
    `error_message` VARCHAR(500) COMMENT 'Reason for parsing failure',
    `retry_count` INT DEFAULT 0 COMMENT 'Number of retries',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `processed_at` DATETIME COMMENT 'Parsing completion time',
    UNIQUE INDEX `uk_file_hash` (`file_hash`),
    INDEX `idx_uploader` (`uploader_id`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

#### 4. candidates - Candidate table (structured data extracted by AI)
```sql
CREATE TABLE `candidates` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `resume_id` BIGINT NOT NULL UNIQUE COMMENT 'Linked resume ID',
    `name` VARCHAR(50) COMMENT 'Full name',
    `phone` VARCHAR(20) COMMENT 'Mobile phone number',
    `email` VARCHAR(100) COMMENT 'Email address',
    `gender` ENUM('MALE', 'FEMALE', 'UNKNOWN') DEFAULT 'UNKNOWN',
    `birth_year` INT COMMENT 'Birth year',
    `experience_years` INT COMMENT 'Years of experience',
    `highest_education` VARCHAR(20) COMMENT 'Highest education level',
    `graduate_school` VARCHAR(100) COMMENT 'Graduated school',
    `major` VARCHAR(100) COMMENT 'Major',
    `current_company` VARCHAR(100) COMMENT 'Current company',
    `current_position` VARCHAR(100) COMMENT 'Current position',
    `skills` JSON COMMENT 'Skill tags ["Java", "Spring Boot", "Redis"]',
    `work_experiences` JSON COMMENT 'Work experiences [{company, position, duration, description}]',
    `education_history` JSON COMMENT 'Education history',
    `raw_extracted_json` JSON COMMENT 'Raw extraction result from the AI',
    `vector_id` VARCHAR(100) COMMENT 'ID in the vector database',
    `ai_summary` TEXT COMMENT 'AI-generated candidate summary',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_resume` (`resume_id`),
    INDEX `idx_name` (`name`),
    INDEX `idx_experience` (`experience_years`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

#### 5. job_applications - Job application table
```sql
CREATE TABLE `job_applications` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `job_id` BIGINT NOT NULL,
    `candidate_id` BIGINT NOT NULL,
    `match_score` DECIMAL(5,2) COMMENT 'Match score computed by AI, 0-100',
    `match_reasons` JSON COMMENT 'Analysis of the matching reasons',
    `match_calculated_at` DATETIME COMMENT 'Time the match score was computed',
    `status` ENUM('PENDING', 'SCREENING', 'INTERVIEW', 'OFFER', 'REJECTED', 'WITHDRAWN') DEFAULT 'PENDING',
    `hr_notes` TEXT COMMENT 'HR notes',
    `applied_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX `uk_job_candidate` (`job_id`, `candidate_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_score` (`match_score` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

#### 6. interview_records - Interview record table
```sql
CREATE TABLE `interview_records` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `application_id` BIGINT NOT NULL COMMENT 'Linked application ID',
    `interviewer_id` BIGINT NOT NULL COMMENT 'Interviewer ID',
    `round` INT NOT NULL DEFAULT 1 COMMENT 'Which interview round',
    `interview_type` ENUM('PHONE', 'VIDEO', 'ONSITE', 'WRITTEN_TEST') DEFAULT 'VIDEO',
    `scheduled_at` DATETIME NOT NULL COMMENT 'Interview time',
    `duration_minutes` INT DEFAULT 60 COMMENT 'Expected duration',
    `status` ENUM('SCHEDULED', 'COMPLETED', 'CANCELLED', 'NO_SHOW') DEFAULT 'SCHEDULED',
    `feedback` TEXT COMMENT 'Interview feedback',
    `score` INT COMMENT 'Rating 1-10',
    `recommendation` ENUM('STRONG_YES', 'YES', 'NEUTRAL', 'NO', 'STRONG_NO'),
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_application` (`application_id`),
    INDEX `idx_interviewer` (`interviewer_id`),
    INDEX `idx_scheduled` (`scheduled_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## 🔴 Redis Data Structure Design

### Key Naming Conventions

| Key Pattern | Type | Purpose | TTL |
|-------------|------|---------|-----|
| `task:resume:{taskId}` | Hash | Resume parsing task status | 24h |
| `rate:ai:{userId}:{date}` | String (Counter) | Rate limiting of AI call counts | 24h |
| `rate:upload:{userId}` | String | Rate limiting of upload frequency | 1min |
| `lock:resume:{fileHash}` | String | Distributed lock for resume parsing | 10min |
| `lock:application:{jobId}:{candidateId}` | String | Lock that prevents duplicate applications | 5min |
| `cache:job:{jobId}` | String (JSON) | Job information cache | 30min |
| `cache:job:hot` | ZSet | Ranking of popular jobs | 10min |
| `cache:candidate:{id}` | String (JSON) | Candidate information cache | 30min |
| `dedup:resume:{fileHash}` | String | File de-duplication marker | 7d |

### Detailed Structure Description

#### 1. Task status tracking
```
Key: task:resume:{taskId}
Type: Hash
Fields:
  - status: QUEUED | PROCESSING | SUCCESS | FAILED
  - progress: 0-100 (parsing progress percentage)
  - resumeId: linked resume ID
  - startTime: timestamp when processing started
  - message: status description or error message
TTL: 86400 (24 hours)
```

#### 2. AI rate limit counter (sliding window)
```
Key: rate:ai:{userId}:{yyyyMMdd}
Type: String (Integer)
Value: number of AI calls made that day
TTL: 86400 (expires automatically after 24 hours)

# Additional per-minute rate limiting
Key: rate:ai:minute:{userId}:{yyyyMMddHHmm}
Type: String (Integer)
Value: number of calls in the current minute
TTL: 60
```

#### 3. Distributed lock
```
Key: lock:resume:{fileHash}
Type: String
Value: {uuid}:{threadId}
TTL: 30s (default, renewed automatically by the Redisson Watchdog)
Note: The Redisson Watchdog mechanism must be used, to prevent the lock from
being released early when parsing takes too long.
```

---

## 🐰 RabbitMQ Design

### Exchange and Queue Topology

```
┌─────────────────────────────────────────────────────────────────┐
│                     RabbitMQ Topology                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   Producer                                                      │
│      │                                                          │
│      ▼                                                          │
│   ┌──────────────────────┐                                      │
│   │  smartats.exchange   │  (Direct Exchange)                   │
│   └──────────┬───────────┘                                      │
│              │                                                  │
│              │ routing_key: resume.parse                        │
│              ▼                                                  │
│   ┌──────────────────────┐                                      │
│   │ resume.parse.queue   │◀─────────┐                           │
│   │ (Main queue)         │          │ x-dead-letter-exchange   │
│   │ TTL: 30min           │          │ x-dead-letter-routing-key │
│   └──────────┬───────────┘          │                           │
│              │                      │                           │
│              │ Consumer             │                           │
│              ▼                      │                           │
│   ┌──────────────────────┐          │                           │
│   │  Processing Failed   │──────────┘                           │
│   │  (Nack + Requeue=F)  │                                      │
│   └──────────────────────┘                                      │
│              │                                                  │
│              ▼                                                  │
│   ┌──────────────────────┐                                      │
│   │   smartats.dlx       │  (Dead Letter Exchange)              │
│   └──────────┬───────────┘                                      │
│              │                                                  │
│              │ routing_key: resume.parse.failed                 │
│              ▼                                                  │
│   ┌──────────────────────┐                                      │
│   │ resume.parse.dlq     │  (Dead Letter Queue)                 │
│   │ (holds failed tasks) │                                      │
│   └──────────────────────┘                                      │
│              │                                                  │
│              ▼                                                  │
│   ┌──────────────────────┐                                      │
│   │  DLQ Consumer        │  (Scheduled scan; manual retry)      │
│   └──────────────────────┘                                      │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Message Payload Definition

```json
// ResumeParseMessage
{
  "taskId": "uuid-xxxx-xxxx",
  "resumeId": 12345,
  "filePath": "/data/resumes/2024/01/xxx.pdf",
  "fileHash": "md5-hash-value",
  "uploaderId": 100,
  "timestamp": 1704067200000,
  "retryCount": 0
}
```

### Queue Configuration Parameters

| Parameter | Value | Description |
|-----------|-------|-------------|
| x-message-ttl | 1800000 | Messages not consumed within 30 minutes go to dead letters |
| x-dead-letter-exchange | smartats.dlx | Dead letter exchange |
| x-dead-letter-routing-key | resume.parse.failed | Dead letter routing key |
| x-max-length | 10000 | Maximum queue length |
| durable | true | Persistence |

---

## 🤖 Spring AI Integration Design

### Prompt Templates

#### Resume information extraction prompt

```text
# System Prompt
You are a professional resume parsing assistant. Your task is to extract structured
information from resume text.
Output strictly in the specified JSON format, and do not add any extra explanation
or markdown markers.

## Output format requirements
{
  "name": "full name",
  "phone": "mobile phone number",
  "email": "email address",
  "gender": "MALE/FEMALE/UNKNOWN",
  "birthYear": 1990,
  "experienceYears": 5,
  "highestEducation": "Bachelor/Master/Doctorate",
  "graduateSchool": "graduated school",
  "major": "major",
  "currentCompany": "current company",
  "currentPosition": "current position",
  "skills": ["Java", "Spring Boot", "MySQL"],
  "workExperiences": [
    {
      "company": "company name",
      "position": "position",
      "startDate": "2020-01",
      "endDate": "2023-06",
      "description": "job description"
    }
  ],
  "educationHistory": [
    {
      "school": "school name",
      "degree": "degree",
      "major": "major",
      "startDate": "2015-09",
      "endDate": "2019-06"
    }
  ],
  "summary": "one-sentence summary of this candidate's core strengths"
}

## Notes
1. If a field cannot be extracted from the resume, set it to null
2. In the skills array, extract all technical skills, tools and programming languages
3. Compute experienceYears from the work experience, precise to a whole number
4. Use a unified date format of YYYY-MM

# User Prompt
Please parse the following resume content:

---
{resumeContent}
---
```

#### RAG search prompt

```text
# System Prompt
You are an intelligent talent search assistant. You will receive a user search query
and some relevant candidate resume fragments.
Based on this information, analyze how well each candidate matches the query.

## Your task
1. Understand the user's hiring requirement
2. Analyze each candidate's match score
3. Sort candidates by match score from high to low
4. Provide the matching reasons

## Output format
{
  "analysis": "understanding of the user's requirement",
  "candidates": [
    {
      "candidateId": 123,
      "matchScore": 85,
      "matchReasons": ["3 years of Java experience", "proficient in Spring Boot", "background at a large tech company"],
      "concerns": ["lacks Redis experience"]
    }
  ]
}

# User Prompt
## Hiring requirement
{userQuery}

## Candidate resumes
{retrievedResumes}
```

---

## 📡 API Documentation

### Basic Conventions

- **Base URL:** `/api/v1`
- **Authentication:** JWT Bearer Token
- **Unified response format:**

```json
{
  "code": 200,
  "message": "success",
  "data": { },
  "timestamp": 1704067200000
}
```

- **Error response format:**

```json
{
  "code": 40001,
  "message": "Parameter validation failed",
  "errors": [
    { "field": "email", "message": "Invalid email format" }
  ],
  "timestamp": 1704067200000
}
```

### Error Code Definitions

| Code | Description |
|------|-------------|
| 200 | Success |
| 40001 | Parameter validation failed |
| 40002 | Unsupported file type |
| 40003 | File size exceeds the limit |
| 40004 | Duplicate resume file |
| 40101 | Not logged in |
| 40301 | No permission |
| 42901 | AI call count exceeds the limit |
| 50001 | Internal system error |
| 50002 | AI service unavailable |
| 50003 | File storage failed |

---

### Module 1: Authentication (Auth)

#### 1.1 User registration
```
POST /api/v1/auth/register

Request Body:
{
  "username": "string, 4-20 characters, required",
  "password": "string, 6-20 characters, required",
  "email": "string, email format, required",
  "role": "string, optional, defaults to HR, enum: HR/INTERVIEWER"
}

Response:
{
  "code": 200,
  "data": {
    "userId": 10001,
    "username": "zhangsan"
  }
}

Backend implementation notes:
- Store passwords encrypted with BCrypt
- Validate uniqueness of the username and the email
- Generate the default daily AI quota
```

#### 1.2 User login
```
POST /api/v1/auth/login

Request Body:
{
  "username": "string, required",
  "password": "string, required"
}

Response:
{
  "code": 200,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIs...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
    "expiresIn": 7200,
    "userInfo": {
      "userId": 10001,
      "username": "zhangsan",
      "role": "HR",
      "dailyAiQuota": 100,
      "todayAiUsed": 15
    }
  }
}

Backend implementation notes:
- Validate the username and password
- Generate JWT tokens (Access Token 2 hours, Refresh Token 7 days)
- [Redis] Record the login state, so that users can be forced offline
```

#### 1.3 Refresh token
```
POST /api/v1/auth/refresh

Request Body:
{
  "refreshToken": "string, required"
}

Response: same as the login endpoint
```

---

### Module 2: Resume Management (Resume)

#### 2.1 Upload resume ⭐ Core endpoint
```
POST /api/v1/resumes/upload
Content-Type: multipart/form-data

Request:
- file: file, required, supports pdf/docx/doc, max 10MB

Response:
{
  "code": 200,
  "data": {
    "taskId": "550e8400-e29b-41d4-a716-446655440000",
    "resumeId": 12345,
    "fileName": "ZhangSan-Java-Developer.pdf",
    "status": "QUEUED",
    "message": "File uploaded, queued and waiting to be parsed"
  }
}

Backend implementation notes (the key point!):
1. Compute the file's MD5 hash
2. [Redis] Check whether dedup:resume:{hash} exists; if it exists, return a "duplicate file" error
3. Save the file to disk/OSS
4. Insert a row into the resumes table with status = PENDING
5. [Redis] Set task:resume:{taskId} = {status: QUEUED, resumeId: xxx}
6. [Redis] Set dedup:resume:{hash} = resumeId, TTL 7 days
7. [RabbitMQ] Send a message to resume.parse.queue
8. Return the taskId immediately, without waiting for parsing to finish
```

#### 2.2 Batch upload resumes
```
POST /api/v1/resumes/batch-upload
Content-Type: multipart/form-data

Request:
- files: array of files, required, at most 20 files

Response:
{
  "code": 200,
  "data": {
    "totalCount": 20,
    "successCount": 18,
    "failedCount": 2,
    "tasks": [
      { "taskId": "xxx", "fileName": "ZhangSan.pdf", "status": "QUEUED" },
      { "taskId": null, "fileName": "duplicate-file.pdf", "status": "DUPLICATE", "message": "File already exists" }
    ]
  }
}

Backend implementation notes:
- [Redis] Upload frequency rate limiting: rate:upload:{userId}, at most 5 batch uploads per minute
- Process each file in a loop; a single failure does not affect the others
```

#### 2.3 Query parsing status ⭐ Core endpoint
```
GET /api/v1/resumes/tasks/{taskId}/status

Response (processing):
{
  "code": 200,
  "data": {
    "taskId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "PROCESSING",
    "progress": 60,
    "message": "Extracting resume information..."
  }
}

Response (completed):
{
  "code": 200,
  "data": {
    "taskId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "SUCCESS",
    "progress": 100,
    "resumeId": 12345,
    "candidateId": 67890,
    "candidateName": "ZhangSan",
    "message": "Parsing completed"
  }
}

Backend implementation notes:
- [Redis] Read directly from task:resume:{taskId}; do not query the database
- If the key does not exist in Redis, fall back to querying MySQL
```

#### 2.4 Get resume list
```
GET /api/v1/resumes

Query Parameters:
- page: int, default 1
- size: int, default 20, max 100
- status: string, optional, filter by status
- keyword: string, optional, search the file name

Response:
{
  "code": 200,
  "data": {
    "total": 156,
    "pages": 8,
    "list": [
      {
        "resumeId": 12345,
        "fileName": "ZhangSan-Java-Developer.pdf",
        "fileSize": 102400,
        "status": "SUCCESS",
        "candidateId": 67890,
        "candidateName": "ZhangSan",
        "uploadTime": "2024-01-01 10:30:00"
      }
    ]
  }
}
```

#### 2.5 Reparse a resume
```
POST /api/v1/resumes/{resumeId}/reparse

Response:
{
  "code": 200,
  "data": {
    "taskId": "new-task-id",
    "message": "Parsing task resubmitted"
  }
}

Backend implementation notes:
- Only allow reparsing of resumes in FAILED status
- Reset retry_count
- Send the MQ message again
```

---

### Module 3: Candidate Management (Candidate)

#### 3.1 Get candidate details
```
GET /api/v1/candidates/{candidateId}

Response:
{
  "code": 200,
  "data": {
    "candidateId": 67890,
    "resumeId": 12345,
    "name": "ZhangSan",
    "phone": "138****1234",
    "email": "zhang***@gmail.com",
    "experienceYears": 5,
    "highestEducation": "Bachelor",
    "graduateSchool": "Zhejiang University",
    "currentCompany": "Alibaba",
    "currentPosition": "Senior Java Engineer",
    "skills": ["Java", "Spring Boot", "MySQL", "Redis", "Kafka"],
    "aiSummary": "5 years of Java backend development experience, proficient in distributed systems, background at a large tech company",
    "workExperiences": [...],
    "educationHistory": [...]
  }
}

Backend implementation notes:
- [Redis] Check cache:candidate:{id} first, cached for 30 minutes
- Mask phone numbers and email addresses
```

#### 3.2 Get candidate list
```
GET /api/v1/candidates

Query Parameters:
- page: int
- size: int
- skills: string, optional, comma-separated skill filter
- experienceMin: int, optional
- experienceMax: int, optional
- education: string, optional

Response: paginated list
```

#### 3.3 AI-powered candidate search ⭐ Core endpoint
```
POST /api/v1/candidates/smart-search

Request Body:
{
  "query": "Find me a Java developer proficient in Spring Boot and Redis with 3+ years of experience",
  "filters": {
    "experienceMin": 3,
    "education": "Bachelor"
  },
  "topK": 10
}

Response:
{
  "code": 200,
  "data": {
    "queryAnalysis": "The user needs: Java developer, core skills Spring Boot + Redis, 3+ years of experience",
    "candidates": [
      {
        "candidateId": 67890,
        "name": "ZhangSan",
        "matchScore": 92,
        "matchReasons": ["5 years of Java experience", "proficient in Spring Boot", "3 years of Redis experience"],
        "concerns": [],
        "skills": ["Java", "Spring Boot", "Redis", "MySQL"],
        "currentPosition": "Senior Java Engineer"
      },
      {
        "candidateId": 67891,
        "name": "LiSi",
        "matchScore": 78,
        "matchReasons": ["4 years of Java experience", "familiar with Spring Boot"],
        "concerns": ["limited Redis experience"],
        "skills": ["Java", "Spring Boot", "MySQL"],
        "currentPosition": "Java Developer"
      }
    ]
  }
}

Backend implementation notes (the most complex endpoint!):
1. [Redis rate limiting] Check rate:ai:{userId}:{date}; return a 429 error when the daily quota is exceeded
2. [MySQL] Apply the filters for a first-pass selection to obtain the candidate scope
3. [Spring AI] Convert the query into a vector (embedding)
4. [Vector database] Run a similarity search to obtain the Top K most similar candidates
5. [Spring AI] Call the LLM for fine-grained reranking and reason analysis of the candidates
6. [Redis] After the AI call succeeds, INCR rate:ai:{userId}:{date}
7. Return the results
```

---

### Module 4: Job Management (Job)

#### 4.1 Create a job
```
POST /api/v1/jobs

Request Body:
{
  "title": "Senior Java Engineer",
  "department": "Technology Department",
  "description": "Responsible for developing core business systems...",
  "requirements": "1. 5+ years of Java development experience\n2. Proficient in Spring Boot...",
  "requiredSkills": ["Java", "Spring Boot", "MySQL"],
  "salaryMin": 25,
  "salaryMax": 40,
  "experienceMin": 5,
  "education": "Bachelor"
}

Response:
{
  "code": 200,
  "data": {
    "jobId": 1001,
    "status": "DRAFT"
  }
}
```

#### 4.2 Get job details
```
GET /api/v1/jobs/{jobId}

Backend implementation notes:
- [Redis] Check cache:job:{jobId} first, cached for 30 minutes
```

#### 4.3 Publish / close a job
```
PUT /api/v1/jobs/{jobId}/status

Request Body:
{
  "status": "PUBLISHED"
}

Backend implementation notes:
- [Redis] Delete the cache cache:job:{jobId} after the status changes
```

#### 4.4 Get popular jobs
```
GET /api/v1/jobs/hot

Query Parameters:
- limit: int, default 10

Backend implementation notes:
- [Redis] Read from cache:job:hot (ZSet)
- The ZSet stores jobId, with score as the popularity value (page views + applications)
- Cached for 10 minutes
```

#### 4.5 AI job matching recommendations
```
POST /api/v1/jobs/{jobId}/match-candidates

Request Body:
{
  "topK": 20
}

Response:
{
  "code": 200,
  "data": {
    "jobTitle": "Senior Java Engineer",
    "matches": [
      {
        "candidateId": 67890,
        "matchScore": 88,
        "matchReasons": ["High skill match", "Experience meets the requirements"]
      }
    ]
  }
}

Backend implementation notes:
- Convert the job JD into a vector
- Search the vector store for the most similar candidates
- [Redis rate limiting] This also consumes the AI quota
```

---

### Module 5: Applications and Interviews (Application)

#### 5.1 Apply with a resume to a job
```
POST /api/v1/applications

Request Body:
{
  "jobId": 1001,
  "candidateId": 67890
}

Backend implementation notes:
- [Redis distributed lock] lock:application:{jobId}:{candidateId}, to prevent duplicate applications
- Compute the match score asynchronously (optional, via MQ)
```

#### 5.2 Get the application list of a job
```
GET /api/v1/jobs/{jobId}/applications

Query Parameters:
- status: string, optional
- sortBy: string, default match_score desc
```

#### 5.3 Update application status
```
PUT /api/v1/applications/{applicationId}/status

Request Body:
{
  "status": "INTERVIEW",
  "notes": "Good resume, schedule a technical interview"
}
```

#### 5.4 Create an interview
```
POST /api/v1/interviews

Request Body:
{
  "applicationId": 5001,
  "interviewerId": 200,
  "round": 1,
  "interviewType": "VIDEO",
  "scheduledAt": "2024-01-15 14:00:00",
  "durationMinutes": 60
}
```

#### 5.5 Submit interview feedback
```
PUT /api/v1/interviews/{interviewId}/feedback

Request Body:
{
  "feedback": "Solid technical skills, strong communication...",
  "score": 8,
  "recommendation": "YES"
}
```

---

### Module 6: Statistics (Statistics)

#### 6.1 Get recruitment overview
```
GET /api/v1/statistics/overview

Response:
{
  "code": 200,
  "data": {
    "totalJobs": 25,
    "publishedJobs": 18,
    "totalCandidates": 1560,
    "todayUploads": 23,
    "pendingApplications": 89,
    "todayInterviews": 5
  }
}
```

#### 6.2 Get AI usage statistics
```
GET /api/v1/statistics/ai-usage

Response:
{
  "code": 200,
  "data": {
    "dailyQuota": 100,
    "todayUsed": 45,
    "remaining": 55,
    "usageHistory": [
      { "date": "2024-01-10", "count": 67 },
      { "date": "2024-01-11", "count": 89 }
    ]
  }
}

Backend implementation notes:
- [Redis] Read rate:ai:{userId}:{date}
```

---

## 🎯 Core Coding Challenges (the difficult parts you need to implement)

### Challenge 1: Sliding window rate limiting with a Redis Lua script

**Requirement description:**
Implement a `@RateLimiter` annotation whose rate limiting rules can be configured flexibly. It needs to support:
- N calls limited per minute
- M calls limited per day
- Using a Redis Lua script to guarantee atomicity

**Acceptance criteria:**
```java
@RateLimiter(key = "ai:search", limit = 5, window = 60, windowUnit = TimeUnit.SECONDS)
@RateLimiter(key = "ai:daily", limit = 100, window = 1, windowUnit = TimeUnit.DAYS)
public SearchResult smartSearch(SearchRequest request) {
    // ...
}
```

**Hints:**
- Use an AOP aspect to intercept methods carrying the annotation
- The Lua script must implement the INCR + EXPIRE operations atomically
- Consider the sliding window algorithm (ZSet) or a simple counter (String)

---

### Challenge 2: RabbitMQ consumer idempotency and retry mechanism

**Requirement description:**
Implement the MQ consumer for resume parsing, which must satisfy:
1. **Idempotency**: the same message consumed any number of times yields the same result
2. **Retry mechanism**: automatically retry a failed parse 3 times; after 3 attempts the message goes to the dead letter queue
3. **Distributed lock**: prevent multiple consumers from processing the same resume at the same time

**Acceptance criteria:**
```java
@RabbitListener(queues = "resume.parse.queue")
public void handleResumeParseMessage(ResumeParseMessage message) {
    // 1. Check in Redis whether it has already been processed
    // 2. Acquire the Redisson distributed lock
    // 3. Call Spring AI to parse
    // 4. Update the database and the Redis status
    // 5. Exception handling and retry logic
}
```

**Hints:**
- Use `message.getMessageProperties().getDeliveryTag()` for manual ACK
- Configure `spring.rabbitmq.listener.simple.retry.*` to get automatic retries
- The dead letter queue needs its own listener for processing

---

### Challenge 3: Spring AI structured output and prompt engineering

**Requirement description:**
Design prompts that make the LLM reliably output JSON matching a Java Bean structure, for resume information extraction.

**Acceptance criteria:**
```java
public record ResumeExtraction(
    String name,
    String phone,
    String email,
    Integer experienceYears,
    List<String> skills,
    List<WorkExperience> workExperiences
) {}

// Example call
ResumeExtraction result = aiService.extractResumeInfo(pdfContent);
```

**Hints:**
- Spring AI supports `BeanOutputConverter` to parse JSON into Java objects automatically
- The JSON Schema must be stated explicitly in the prompt
- Handle unstable LLM output (retries, fallback)

---

### Challenge 4: Vector search and metadata filtering

**Requirement description:**
Implement hybrid queries during RAG search:
1. Vector similarity search (semantic matching)
2. Metadata filtering (years of experience >= 3, education = Bachelor)

**Acceptance criteria:**
```java
public List<Candidate> hybridSearch(String query, SearchFilter filter) {
    // 1. Convert the query into an embedding
    // 2. Execute in the vector store: vector similarity + metadata filtering
    // 3. Return the Top K results
}
```

**Hints:**
- With Milvus: you can pass an `expr` expression when searching
- With PgVector: SQL WHERE conditions can be used
- Spring AI's `VectorStore` interface supports `FilterExpression`

---

### Challenge 5: Elegant file de-duplication with a distributed lock

**Requirement description:**
Implement file de-duplication detection on upload, which must:
1. Compute the file MD5 as the unique identifier
2. Use Redis to check whether the file already exists
3. Use a Redisson distributed lock to prevent concurrent uploads of the same file

**Acceptance criteria:**
```java
public UploadResult uploadResume(MultipartFile file) {
    String fileHash = calculateMD5(file);
    
    // 1. Check the Redis de-duplication marker
    // 2. Acquire the distributed lock
    // 3. Double-check whether it exists
    // 4. Save the file and the database record
    // 5. Release the lock
}
```

**Hints:**
- Compute the MD5 with `DigestUtils.md5DigestAsHex()`
- For the Redisson lock use `RLock lock = redissonClient.getLock("lock:resume:" + hash)`
- Pay attention to the lock timeout settings to prevent deadlocks

---

## 📁 Recommended Project Structure

```
smartats/
├── pom.xml
├── src/main/java/com/yourcompany/smartats/
│   ├── SmartAtsApplication.java
│   ├── common/
│   │   ├── result/
│   │   │   ├── Result.java
│   │   │   └── ResultCode.java
│   │   ├── exception/
│   │   │   ├── BusinessException.java
│   │   │   └── GlobalExceptionHandler.java
│   │   ├── annotation/
│   │   │   └── RateLimiter.java
│   │   └── aspect/
│   │       └── RateLimiterAspect.java
│   ├── config/
│   │   ├── RedisConfig.java
│   │   ├── RabbitMQConfig.java
│   │   ├── SecurityConfig.java
│   │   └── SpringAIConfig.java
│   ├── module/
│   │   ├── auth/
│   │   │   ├── controller/
│   │   │   ├── service/
│   │   │   ├── dto/
│   │   │   └── security/
│   │   ├── resume/
│   │   │   ├── controller/
│   │   │   ├── service/
│   │   │   ├── consumer/          # RabbitMQ consumer
│   │   │   ├── dto/
│   │   │   └── entity/
│   │   ├── candidate/
│   │   │   ├── controller/
│   │   │   ├── service/
│   │   │   ├── dto/
│   │   │   └── entity/
│   │   ├── job/
│   │   │   ├── controller/
│   │   │   ├── service/
│   │   │   ├── dto/
│   │   │   └── entity/
│   │   └── ai/
│   │       ├── service/
│   │       │   ├── ResumeExtractionService.java
│   │       │   ├── SemanticSearchService.java
│   │       │   └── EmbeddingService.java
│   │       └── prompt/
│   │           └── PromptTemplates.java
│   └── infrastructure/
│       ├── redis/
│       │   └── RedisService.java
│       ├── mq/
│       │   └── MessagePublisher.java
│       └── storage/
│           └── FileStorageService.java
└── src/main/resources/
    ├── application.yml
    ├── application-dev.yml
    ├── application-prod.yml
    └── mapper/
        └── *.xml
```

---

## 🐳 Deployment Architecture (Docker Compose)

To set up the development environment quickly, it is recommended to manage the infrastructure dependencies with `docker-compose.yml`.

```yaml
version: '3.8'
services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: smartats
    ports:
      - "3306:3306"
    volumes:
      - ./data/mysql:/var/lib/mysql

  redis:
    image: redis:7.0
    ports:
      - "6379:6379"
    volumes:
      - ./data/redis:/data

  rabbitmq:
    image: rabbitmq:3-management
    ports:
      - "5672:5672"
      - "15672:15672"
    environment:
      RABBITMQ_DEFAULT_USER: user
      RABBITMQ_DEFAULT_PASS: password

  milvus: # Vector database (PgVector is an alternative)
    image: milvusdb/milvus:v2.3.4
    ports:
      - "19530:19530"
    # Note: production Milvus requires etcd and MinIO; simplified here

  # It is recommended to add MinIO for resume file storage
  minio:
    image: minio/minio
    command: server /data --console-address ":9001"
    ports:
      - "9000:9000"
      - "9001:9001"
```

---

## 📚 Learning Resources

- [Spring AI official documentation](https://docs.spring.io/spring-ai/reference/)
- [Redisson distributed locks](https://github.com/redisson/redisson/wiki/8.-distributed-locks-and-synchronizers)
- [RabbitMQ dead letter queues](https://www.rabbitmq.com/dlx.html)
- [MyBatis-Plus official documentation](https://baomidou.com/)

---

**Document version:** v1.0  
**Last updated:** 2026-02-14
**Author:** AI Copilot
