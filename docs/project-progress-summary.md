# SmartATS Project Progress Report

**Update Date**: February 23, 2026  
**Project Version**: 1.0.0  
**Overall Completion**: **~92%** (Core Business Logic)

---

## Project Overview

SmartATS is an intelligent recruitment management system designed for HR professionals. It is built using Spring Boot 3.2.5 + JDK 21 and integrates modern technologies such as AI-powered resume parsing (Zhipu AI), asynchronous task processing (RabbitMQ), distributed locking (Redisson), and caching (Redis).

### Core Technology Stack

| Component | Technology | Version | Status |
|------|------|------|------|
| Core Framework | Spring Boot | 3.2.5 | ✅ |
| Runtime | JDK | 21 | ✅ |
| ORM | MyBatis-Plus | 3.5.10.1 | ✅ |
| Database | MySQL | 8.0 | ✅ |
| Cache | Redis | 7.0 | ✅ StringRedisTemplate |
| Distributed Lock | Redisson | 3.25.0 | ✅ |
| Message Queue | RabbitMQ | 3.12 | ✅ |
| Object Storage | MinIO | 8.5.10 | ✅ |
| AI Integration | Spring AI + Zhipu AI | 1.0.0-M4 | ✅ |
| Document Processing | Apache POI + PDFBox | 5.2.5 / 2.0.29 | ✅ |
| Security | Spring Security + JWT | Bundled with Boot 3.2.5 | ✅ |
| JSON | Fastjson2 | 2.0.43 | ✅ |
| Utility Library | Hutool | 5.8.23 | ✅ |

---

## Module Completion Status

### 1. Common Module (`common/`) — 100% ✅

8 files:

- `Result.java` — Unified response wrapper
- `ResultCode.java` — Error code enumeration (6 modules: 10xxx–43xxx)
- `BusinessException.java` — Business exception
- `GlobalExceptionHandler.java` — Global exception handling (8 handler types)
- `RedisKeyConstants.java` — Centralized management of Redis key constants
- `FileValidationUtil.java` — File validation (file type + magic number checks)
- `DataMaskUtil.java` — Data masking (phone numbers and email addresses)
- `JsonTypeHandler.java` — MyBatis JSON type handler

---

### 2. Authentication Module (`module/auth/`) — 98% ✅

12 files, 5 API endpoints:

| Method | Path | Authentication | Description |
|------|------|------|------|
| POST | `/api/v1/auth/register` | ❌ | User registration (BCrypt encryption; ADMIN self-registration prohibited) |
| POST | `/api/v1/auth/login` | ❌ | Login (accessToken: 2h + refreshToken: 7d) |
| POST | `/api/v1/auth/send-verification-code` | ❌ | Send email verification code |
| POST | `/api/v1/auth/refresh` | ❌ | Refresh Token |
| GET | `/api/v1/auth/test` | ✅ | Test authentication status |

---

### 3. Job Management Module (`module/job/`) — 95% ✅

10 files, 8 API endpoints:

| Method | Path | Authentication | Description |
|------|------|------|------|
| POST | `/api/v1/jobs` | ✅ | Create a job |
| PUT | `/api/v1/jobs` | ✅ | Update a job |
| GET | `/api/v1/jobs/{id}` | ❌ | Retrieve job details (Redis cache: 30 minutes) |
| GET | `/api/v1/jobs` | ❌ | Paginated query |
| POST | `/api/v1/jobs/{id}/publish` | ✅ | Publish a job |
| POST | `/api/v1/jobs/{id}/close` | ✅ | Close a job |
| DELETE | `/api/v1/jobs/{id}` | ✅ | Delete a job |
| GET | `/api/v1/jobs/hot` | ❌ | Popular jobs ranking (ZSet) |

Caching strategy: Cache-aside + delayed double deletion (`CacheEvictionService`) + atomic view count synchronization using `GETDEL`.

---

### 4. Resume Module (`module/resume/`) — 95% ✅

11 files, 4 API endpoints:

| Method | Path | Authentication | Description |
|------|------|------|------|
| POST | `/api/v1/resumes/upload` | ✅ | Single-file upload (PDF/DOC/DOCX, ≤10MB) |
| GET | `/api/v1/resumes/tasks/{taskId}` | ✅ | Query resume parsing task status |
| GET | `/api/v1/resumes/{id}` | ✅ | Retrieve resume details |
| GET | `/api/v1/resumes` | ✅ | Paginated query |

Processing workflow:

Upload → MD5 Deduplication → MinIO Storage → MQ Message → Consumer (Idempotency Check + Redisson Lock + AI Parsing + Candidate Saving + Webhook Notification).

---

### 5. Candidate Module (`module/candidate/`) — 95% ✅

7 files, 5 API endpoints:

| Method | Path | Authentication | Description |
|------|------|------|------|
| GET | `/api/v1/candidates/resume/{resumeId}` | ✅ | Retrieve candidate by resume ID |
| GET | `/api/v1/candidates/{id}` | ✅ | Retrieve candidate details (Redis caching) |
| PUT | `/api/v1/candidates/{id}` | ✅ | Update candidate information (`@Valid`) |
| DELETE | `/api/v1/candidates/{id}` | ✅ | Delete a candidate |
| GET | `/api/v1/candidates` | ✅ | Paginated query (multi-dimensional filtering + data masking) |

---

### 6. Job Application Module (`module/application/`) — 95% ✅

8 files, 6 API endpoints:

| Method | Path | Authentication | Description |
|------|------|------|------|
| POST | `/api/v1/applications` | ✅ | Create an application (duplicate prevention) |
| PUT | `/api/v1/applications/{id}/status` | ✅ | Update application status |
| GET | `/api/v1/applications/{id}` | ✅ | Retrieve application details |
| GET | `/api/v1/applications/job/{jobId}` | ✅ | Retrieve applications for a specific job |
| GET | `/api/v1/applications/candidate/{candidateId}` | ✅ | Retrieve applications submitted by a specific candidate |
| GET | `/api/v1/applications` | ✅ | Paginated query |

Status transitions:

`PENDING → REVIEWING → INTERVIEW → OFFER / REJECTED`

---

### 7. Interview Records Module (`module/interview/`) — 95% ✅

7 files, 5 API endpoints:

| Method | Path | Authentication | Description |
|------|------|------|------|
| POST | `/api/v1/interviews` | ✅ | Schedule an interview |
| PUT | `/api/v1/interviews/{id}/feedback` | ✅ | Submit interview feedback |
| POST | `/api/v1/interviews/{id}/cancel` | ✅ | Cancel an interview |
| GET | `/api/v1/interviews/{id}` | ✅ | Retrieve interview details |
| GET | `/api/v1/interviews/application/{applicationId}` | ✅ | Retrieve interview rounds for a specific application |

---

### 8. Webhook Module (`module/webhook/`) — 95% ✅

10 files, 4 API endpoints, and 13 event types.

| Method | Path | Authentication | Description |
|------|------|------|------|
| POST | `/api/v1/webhooks` | ✅ | Create a Webhook |
| GET | `/api/v1/webhooks` | ✅ | Retrieve Webhook list |
| DELETE | `/api/v1/webhooks/{id}` | ✅ | Delete a Webhook |
| POST | `/api/v1/webhooks/{id}/test` | ✅ | Test a Webhook |

---

### 9. Infrastructure Layer + Configuration Layer — 100% ✅

- **Infrastructure (4 files):** `EmailService`, `MessagePublisher`, `FileStorageService`, `Min
