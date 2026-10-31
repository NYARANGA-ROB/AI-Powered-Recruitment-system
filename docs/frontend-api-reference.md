# SmartATS Frontend API Complete Reference Manual

> Automatically generated on 2026-02-24 based on a full analysis of the backend source code.

---

## Table of Contents
- [Global Conventions](#global-conventions)
- [Unified Response Structure Result\<T\>](#unified-response-structure-resultt)
- [Error Codes ResultCode](#error-codes-resultcode)
- [Authentication and Authorization](#authentication-and-authorization)
- [Module 1: Auth Authentication Management](#module-1-auth-authentication-management)
- [Module 2: Job Position Management](#module-2-job-position-management)
- [Module 3: Resume Management](#module-3-resume-management)
- [Module 4: Candidate Management](#module-4-candidate-management)
- [Module 5: Smart Search](#module-5-smart-search)
- [Module 6: Application Management](#module-6-application-management)
- [Module 7: Interview Management](#module-7-interview-management)
- [Module 8: Webhook Management](#module-8-webhook-management)
- [Pagination Structure Page\<T\>](#pagination-structure-paget)
- [Complete Entity Fields](#complete-entity-fields)
- [TypeScript Type Definitions](#typescript-type-definitions)

---

## Global Conventions

| Configuration Item | Value |
|--------|-----|
| Base URL | `http://localhost:8080/api/v1` |
| Server Port | `8080` |
| Context Path | `/api/v1` |
| Content-Type | `application/json` (use `multipart/form-data` for file uploads only) |
| Authentication Method | `Authorization: Bearer <accessToken>` |
| Date Format (JSON) | `yyyy-MM-dd HH:mm:ss` |
| Time Zone | `Asia/Shanghai` |
| null Value | Not serialized (Jackson `non_null`) |
| File Size Limit | Single file 10MB, request 50MB |
| CORS | Development environment `*`, production reads `CORS_ALLOWED_ORIGINS` environment variable |

---

## Unified Response Structure Result\<T\>

**All API responses are uniformly wrapped as follows:**

```typescript
interface Result<T> {
  code: number;       // 200 = success, other values = error code
  message: string;    // "success" or error description
  data: T | null;     // response payload, null on failure
  timestamp: number;  // millisecond timestamp
}
```

---

## Error Codes ResultCode

| Enum Name | Code | Message | Scenario |
|---------|------|---------|------|
| `SUCCESS` | 200 | Operation successful | — |
| **General Errors** | | | |
| `BAD_REQUEST` | 40001 | Parameter validation failed | Invalid request parameters |
| `UNAUTHORIZED` | 40101 | Not logged in, please log in first | Missing/invalid token |
| `FORBIDDEN` | 40301 | No permission to access | Insufficient role permissions |
| `NOT_FOUND` | 40401 | Resource not found | General 404 |
| **System Errors** | | | |
| `INTERNAL_ERROR` | 50001 | Internal system error | Unexpected exception |
| `AI_SERVICE_ERROR` | 50002 | AI service unavailable | Zhipu AI call failed |
| `FILE_UPLOAD_ERROR` | 50003 | File upload failed | MinIO upload exception |
| **Authentication Module 10xxx** | | | |
| `USER_NOT_FOUND` | 10001 | User does not exist | |
| `USER_ALREADY_EXISTS` | 10002 | User already exists | |
| `USERNAME_ALREADY_EXISTS` | 10003 | Username already exists | |
| `EMAIL_ALREADY_EXISTS` | 10004 | Email has already been registered | |
| `PASSWORD_ERROR` | 10005 | Incorrect password | |
| `INVALID_CREDENTIALS` | 10006 | Username or password is incorrect | |
| `USER_DISABLED` | 10007 | Account has been disabled | |
| `ACCOUNT_DISABLED` | 10008 | Account has been disabled, please contact the administrator | |
| `TOKEN_INVALID` | 10009 | Token is invalid or expired | |
| `TOKEN_REFRESH_FAILED` | 10010 | Token refresh failed | |
| **Verification Codes 11xxx** | | | |
| `VERIFICATION_CODE_SEND_TOO_FREQUENT` | 11001 | Verification code sent too frequently, please try again later | 60-second rate limit |
| `VERIFICATION_CODE_SEND_FAILED` | 11002 | Verification code sending failed, please try again later | |
| `VERIFICATION_CODE_INVALID` | 11003 | Verification code is incorrect or expired | 5-minute validity |
| `VERIFICATION_CODE_REQUIRED` | 11004 | Please obtain a verification code first | |
| **Resume Module 20xxx** | | | |
| `RESUME_NOT_FOUND` | 20001 | Resume does not exist | |
| `RESUME_ALREADY_PARSED` | 20002 | Resume already parsed, do not resubmit | |
| `FILE_TYPE_NOT_SUPPORTED` | 20003 | Unsupported file type | PDF/DOC/DOCX only |
| `FILE_SIZE_EXCEEDED` | 20004 | File size exceeded | >10MB |
| `RESUME_DUPLICATE` | 20005 | Resume file already exists | MD5 deduplication |
| `BATCH_UPLOAD_LIMIT_EXCEEDED` | 20006 | Batch upload file count exceeded (maximum 20) | |
| `UPLOAD_RATE_LIMITED` | 20007 | Uploads are too frequent, please try again later | 5 times/minute |
| **AI 30xxx** | | | |
| `AI_QUOTA_EXCEEDED` | 30001 | AI call quota exceeded | |
| `AI_PARSE_FAILED` | 30002 | Resume parsing failed | |
| **Job Application 42xxx** | | | |
| `APPLICATION_NOT_FOUND` | 42001 | Application record does not exist | |
| `APPLICATION_DUPLICATE` | 42002 | The candidate has already applied for this position | |
| `APPLICATION_STATUS_INVALID` | 42003 | Application status transition is invalid | |
| `APPLICATION_JOB_NOT_PUBLISHED` | 42004 | Position is not published and cannot be applied for | |
| **Interview 43xxx** | | | |
| `INTERVIEW_NOT_FOUND` | 43001 | Interview record does not exist | |
| `INTERVIEW_ALREADY_COMPLETED` | 43002 | Interview is already completed and cannot be modified | |
| `INTERVIEW_ALREADY_CANCELLED` | 43003 | Interview has already been cancelled | |
| `INTERVIEW_TIME_CONFLICT` | 43004 | Interview time conflict | |

---

## Authentication and Authorization

**SecurityConfig public endpoints (no token required):**

| Method | Path | Description |
|--------|------|-------------|
| POST | `/auth/register` | Register |
| POST | `/auth/login` | Log in |
| POST | `/auth/send-verification-code` | Send verification code |
| POST | `/auth/refresh` | Refresh token |
| GET | `/jobs/**` | All job GET endpoints (anonymous browsing) |
| — | `/swagger-ui/**`, `/v3/api-docs/**` | API documentation |

**All other endpoints** require the request header `Authorization: Bearer <accessToken>`.

**User roles** (3 types): `ADMIN` | `HR` | `INTERVIEWER`
- During registration, only `HR` or `INTERVIEWER` can be selected; `ADMIN` cannot self-register.

---

## Module 1: Auth Authentication Management

**Base Path**: `/auth`

### 1.1 User Registration
```
POST /api/v1/auth/register
Auth: ❌
```

**Request Body:**
```typescript
interface RegisterRequest {
  username: string;          // @NotBlank, @Size(min=4, max=20)
  password: string;          // @NotBlank, @Size(min=6, max=20)
  email: string;             // @NotBlank, @Email
  role?: string;             // @Pattern("HR|INTERVIEWER"), optional, default HR
  verificationCode: string;  // @NotBlank, @Pattern("^\\d{6}$"), 6-digit number
}
```

**Response:** `Result<null>`

---

### 1.2 User Login
```
POST /api/v1/auth/login
Auth: ❌
```

**Request Body:**
```typescript
interface LoginRequest {
  username: string;  // @NotBlank
  password: string;  // @NotBlank
}
```

**Response:** `Result<LoginResponse>`
```typescript
interface LoginResponse {
  accessToken: string;    // JWT, valid for 2 hours
  refreshToken: string;   // valid for 7 days
  expiresIn: number;      // expiration time (seconds)
  userInfo: UserInfo;
}

interface UserInfo {
  userId: number;          // Long
  username: string;
  email: string;
  role: string;            // "ADMIN" | "HR" | "INTERVIEWER"
  dailyAiQuota: number;   // daily AI quota
  todayAiUsed: number;    // AI calls used today
}
```

---

### 1.3 Send Email Verification Code
```
POST /api/v1/auth/send-verification-code
Auth: ❌
```

**Request Body:**
```typescript
interface SendVerificationCodeRequest {
  email: string;  // @NotBlank, @Email
}
```

**Response:** `Result<null>`

> ⚠️ 60-second send throttling; verification code valid for 5 minutes.

---

### 1.4 Refresh Token
```
POST /api/v1/auth/refresh
Auth: ❌
```

**Request Body:**
```typescript
interface RefreshTokenRequest {
  refreshToken: string;  // @NotBlank
}
```

**Response:** `Result<LoginResponse>` (same structure as login response)

---

### 1.5 Test JWT Authentication
```
GET /api/v1/auth/test
Auth: ✅
```

**Response:** `Result<TestAuthResponse>`
```typescript
interface TestAuthResponse {
  userId: number;
  authorities: any;
  message: string;    // "JWT authentication successful!"
}
```

---

## Module 2: Job Position Management

**Base Path**: `/jobs`

### 2.1 Create a Job
```
POST /api/v1/jobs
Auth: ✅
```

**Request Body:**
```typescript
interface CreateJobRequest {
  title: string;              // @NotBlank
  department?: string;
  description: string;        // @NotBlank
  requirements: string;       // @NotBlank
  requiredSkills?: string[];  // skill tag array
  salaryMin: number;          // @NotNull, @Min(0), unit: K
  salaryMax: number;          // @NotNull, @Min(0), unit: K
  experienceMin?: number;     // @Min(0), years
  experienceMax?: number;     // @Min(0), years
  education?: string;         // e.g. "Bachelor's", "Master's"
  jobType?: string;           // e.g. "Full-time", "Part-time", "Internship"
}
```

**Response:** `Result<number>` — returns the created job ID (Long)

---

### 2.2 Update a Job
```
PUT /api/v1/jobs
Auth: ✅
```

**Request Body:**
```typescript
interface UpdateJobRequest {
  id: number;                 // @NotNull, job ID
  title?: string;
  department?: string;
  description?: string;
  requirements?: string;
  requiredSkills?: string[];
  salaryMin?: number;         // @Min(0)
  salaryMax?: number;         // @Min(0)
  experienceMin?: number;     // @Min(0)
  experienceMax?: number;     // @Min(0)
  education?: string;
  jobType?: string;
}
```

**Response:** `Result<null>`

---

### 2.3 Get Job Details
```
GET /api/v1/jobs/{id}
Auth: ❌ (public)
Path Params: id (Long)
```

**Response:** `Result<JobResponse>`
```typescript
interface JobResponse {
  id: number;
  title: string;
  department: string;
  description: string;
  requirements: string;
  requiredSkills: string[];
  salaryMin: number;           // K
  salaryMax: number;           // K
  salaryRange: string;         // formatted, e.g. "15K-25K"
  experienceMin: number;
  experienceMax: number;
  experienceRange: string;     // formatted, e.g. "3-5 years"
  education: string;
  jobType: string;
  status: string;              // "DRAFT" | "PUBLISHED" | "CLOSED"
  statusDesc: string;          // localized status description
  creatorId: number;
  viewCount: number;
  createdAt: string;           // "yyyy-MM-dd HH:mm:ss"
  updatedAt: string;           // "yyyy-MM-dd HH:mm:ss"
}
```

---

### 2.4 Job List (Paginated)
```
GET /api/v1/jobs
Auth: ❌ (public)
```

**Query Params:**
```typescript
interface JobQueryRequest {
  keyword?: string;          // keyword search (title/description/requirements)
  department?: string;
  jobType?: string;
  education?: string;
  experienceMin?: number;
  salaryMin?: number;
  status?: string;           // "DRAFT" | "PUBLISHED" | "CLOSED"
  pageNum?: number;          // default 1
  pageSize?: number;         // default 10
  orderBy?: string;          // "created_at" (default) | "salary_max" | "view_count"
  orderDirection?: string;   // "asc" | "desc" (default)
}
```

**Response:** `Result<Page<JobResponse>>`

---

### 2.5 Publish a Job
```
POST /api/v1/jobs/{id}/publish
Auth: ✅
Path Params: id (Long)
```

**Response:** `Result<null>`

---

### 2.6 Close a Job
```
POST /api/v1/jobs/{id}/close
Auth: ✅
Path Params: id (Long)
```

**Response:** `Result<null>`

---

### 2.7 Delete a Job
```
DELETE /api/v1/jobs/{id}
Auth: ✅
Path Params: id (Long)
```

**Response:** `Result<null>`

---

### 2.8 Hot Jobs
```
GET /api/v1/jobs/hot
Auth: ❌ (public)
```

**Query Params:**
| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `limit` | number | 10 | Number of results to return |

**Response:** `Result<JobResponse[]>`

---

## Module 3: Resume Management

**Base Path**: `/resumes`

### 3.1 Upload Resume
```
POST /api/v1/resumes/upload
Auth: ✅
Content-Type: multipart/form-data
```

**Request Body (FormData):**
| Field | Type | Description |
|-------|------|-------------|
| `file` | File | PDF/DOC/DOCX, ≤10MB |

**Response:** `Result<ResumeUploadResponse>`
```typescript
interface ResumeUploadResponse {
  taskId: string;        // used to poll parsing status
  resumeId: number;
  duplicated: boolean;   // whether it already exists (MD5 deduplication)
  message: string;
}
```

---

### 3.2 Batch Upload Resumes
```
POST /api/v1/resumes/batch-upload
Auth: ✅
Content-Type: multipart/form-data
```

**Request Body (FormData):**
| Field | Type | Description |
|-------|------|-------------|
| `files` | File[] | maximum 20 files, limit of 5 per minute |

**Response:** `Result<BatchUploadResponse>`
```typescript
interface BatchUploadResponse {
  totalCount: number;
  successCount: number;
  failedCount: number;
  items: BatchUploadItem[];
}

interface BatchUploadItem {
  taskId: string | null;  // available when successful
  resumeId: number | null;
  fileName: string;
  status: string;          // "QUEUED" | "DUPLICATE" | "FAILED"
  message: string;
}
```

---

### 3.3 Query Parsing Task Status
```
GET /api/v1/resumes/tasks/{taskId}
Auth: ✅
Path Params: taskId (string, UUID)
```

**Response:** `Result<TaskStatusResponse>`
```typescript
interface TaskStatusResponse {
  status: string;           // "PENDING" | "PARSING" | "COMPLETED" | "FAILED"
  resumeId: number | null;  // set after parsing completes
  candidateId: number | null; // set after parsing completes
  errorMessage: string | null;
  progress: number | null;   // 0-100
}
```

> ⚠️ The frontend should poll this endpoint until the status is COMPLETED or FAILED.

---

### 3.4 Get Resume Details
```
GET /api/v1/resumes/{id}
Auth: ✅ (can only view the current user's resumes)
Path Params: id (Long)
```

**Response:** `Result<Resume>`
```typescript
interface Resume {
  id: number;
  userId: number;
  fileName: string;
  filePath: string;
  fileUrl: string;
  fileSize: number;        // bytes
  fileHash: string;        // MD5
  fileType: string;        // "pdf" | "doc" | "docx"
  status: string;          // "PARSING" | "COMPLETED" | "FAILED"
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
}
```

---

### 3.5 Resume List (Paginated)
```
GET /api/v1/resumes
Auth: ✅ (current user's resumes)
```

**Query Params:**
| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | number | 1 | Page number |
| `size` | number | 10 | Number of items per page |

**Response:** `Result<Page<Resume>>`

---

## Module 4: Candidate Management

**Base Path**: `/candidates`

### 4.1 Get Candidate by Resume ID
```
GET /api/v1/candidates/resume/{resumeId}
Auth: ✅
Path Params: resumeId (Long)
```

**Response:** `Result<CandidateResponse>`

---

### 4.2 Get Candidate Details
```
GET /api/v1/candidates/{id}
Auth: ✅
Path Params: id (Long)
```

**Response:** `Result<CandidateResponse>`
```typescript
interface CandidateResponse {
  id: number;
  resumeId: number;

  // Basic information
  name: string;
  phone: string;          // masked: "138****5678"
  email: string;          // masked: "zh****@example.com"
  gender: string;
  age: number;

  // Education information
  education: string;      // highest education
  school: string;         // graduating institution
  major: string;
  graduationYear: number;

  // Work information
  workYears: number;
  currentCompany: string;
  currentPosition: string;

  // Skills and experience
  skills: string[];
  workExperience: WorkExperienceItem[];
  projectExperience: ProjectExperienceItem[];
  selfEvaluation: string;

  // AI metadata
  confidenceScore: number;   // 0-1
  aiSummary: string;

  // Time
  parsedAt: string;
  createdAt: string;
  updatedAt: string;
}

// Single item structure in workExperience array (Map<String, Object>):
interface WorkExperienceItem {
  company: string;
  position: string;
  startDate: string;
  endDate: string;
  description: string;
}

// Single item structure in projectExperience array (Map<String, Object>):
interface ProjectExperienceItem {
  name: string;
  role: string;
  startDate: string;
  endDate: string;
  description: string;
  technologies: string[];
}
```

> ⚠️ `phone` and `email` are always masked; the frontend will not receive the original values.

---

### 4.3 Update Candidate Information
```
PUT /api/v1/candidates/{id}
Auth: ✅
Path Params: id (Long)
```

**Request Body:**
```typescript
interface CandidateUpdateRequest {
  name?: string;
  phone?: string;
  email?: string;
  gender?: string;
  age?: number;
  education?: string;
  school?: string;
  major?: string;
  graduationYear?: number;
  workYears?: number;
  currentCompany?: string;
  currentPosition?: string;
  skills?: string[];
  selfEvaluation?: string;
}
```

> Only non-null fields are updated; vectorization is retriggered asynchronously.

**Response:** `Result<CandidateResponse>`

---

### 4.4 Delete Candidate
```
DELETE /api/v1/candidates/{id}
Auth: ✅
Path Params: id (Long)
```

**Response:** `Result<null>`

> Deletes the Milvus vector and MySQL record synchronously.

---

### 4.5 Candidate List (Paginated + Multi-dimensional Filtering)
```
GET /api/v1/candidates
Auth: ✅
```

**Query Params:**
```typescript
interface CandidateQueryParams {
  page?: number;             // default 1
  pageSize?: number;         // default 10, maximum 100
  keyword?: string;          // fuzzy match: name/email/company/title
  education?: string;        // exact match: "Bachelor's"/"Master's"/"Doctorate"
  skill?: string;            // exact match against skill entries in JSON, e.g. "Java"
  minWorkYears?: number;     // minimum work experience inclusive
  maxWorkYears?: number;     // maximum work experience inclusive
  currentPosition?: string;  // fuzzy match
}
```

**Response:** `Result<Page<CandidateResponse>>`

---

## Module 5: Smart Search

**Base Path**: `/candidates`

### 5.1 Semantic Search for Candidates
```
POST /api/v1/candidates/smart-search
Auth: ✅
```

**Request Body:**
```typescript
interface SmartSearchRequest {
  query: string;          // @NotBlank, @Size(max=1000), natural language query
  topK?: number;          // @Min(1), @Max(50), default 10
  minScore?: number;      // @Min(0), @Max(1), default 0.3, cosine similarity threshold
}
```

> Query examples:
> - "3+ years of Java backend development, familiar with Spring Boot and microservice architecture"
> - "Frontend engineer with large-company experience, proficient in React and TypeScript"

**Response:** `Result<SmartSearchResponse>`
```typescript
interface SmartSearchResponse {
  query: string;
  totalMatches: number;
  candidates: MatchedCandidate[];
}

interface MatchedCandidate {
  candidateId: number;
  name: string;
  matchScore: number;       // 0~1, cosine similarity
  currentPosition: string;
  currentCompany: string;
  education: string;
  workYears: number;
  skills: string[];
  aiSummary: string;
}
```

---

## Module 6: Application Management

**Base Path**: `/applications`

### Status Flow Diagram
```
PENDING → SCREENING → INTERVIEW → OFFER
                                 → REJECTED
                   → REJECTED
         → WITHDRAWN (withdraw at any stage)
```

### 6.1 Create a Job Application
```
POST /api/v1/applications
Auth: ✅
```

**Request Body:**
```typescript
interface CreateApplicationRequest {
  jobId: number;         // @NotNull
  candidateId: number;   // @NotNull
  hrNotes?: string;
}
```

> Automatic deduplication: the same candidate cannot apply to the same position twice.

**Response:** `Result<number>` — returns the application ID (Long)

---

### 6.2 Update Application Status
```
PUT /api/v1/applications/{id}/status
Auth: ✅
Path Params: id (Long)
```

**Request Body:**
```typescript
interface UpdateApplicationStatusRequest {
  status: string;   // @NotBlank, @Pattern("SCREENING|INTERVIEW|OFFER|REJECTED|WITHDRAWN")
  hrNotes?: string; // HR note (reason for status change)
}
```

**Response:** `Result<null>`

---

### 6.3 Get Application Details
```
GET /api/v1/applications/{id}
Auth: ✅
Path Params: id (Long)
```

**Response:** `Result<ApplicationResponse>`
```typescript
interface ApplicationResponse {
  id: number;

  // Related information
  jobId: number;
  jobTitle: string;          // redundant for easy display
  candidateId: number;
  candidateName: string;     // redundant for easy display

  // AI matching
  matchScore: number;        // BigDecimal → number, 0-100
  matchReasons: string[];
  matchCalculatedAt: string; // "yyyy-MM-dd HH:mm:ss"

  // Status
  status: string;            // "PENDING"|"SCREENING"|"INTERVIEW"|"OFFER"|"REJECTED"|"WITHDRAWN"
  statusDesc: string;        // localized description
  hrNotes: string;

  // Time
  appliedAt: string;
  updatedAt: string;
}
```

---

### 6.4 Query Application List by Job
```
GET /api/v1/applications/job/{jobId}
Auth: ✅
Path Params: jobId (Long)
```

**Query Params:**
| Parameter | Type | Default |
|-----------|------|---------|
| `pageNum` | number | 1 |
| `pageSize` | number | 10 |

**Response:** `Result<Page<ApplicationResponse>>`

---

### 6.5 Query Application List by Candidate
```
GET /api/v1/applications/candidate/{candidateId}
Auth: ✅
Path Params: candidateId (Long)
```

**Query Params:**
| Parameter | Type | Default |
|-----------|------|---------|
| `pageNum` | number | 1 |
| `pageSize` | number | 10 |

**Response:** `Result<Page<ApplicationResponse>>`

---

### 6.6 Comprehensive Application Query
```
GET /api/v1/applications
Auth: ✅
```

**Query Params:**
```typescript
interface ApplicationQueryRequest {
  pageNum?: number;        // default 1
  pageSize?: number;       // default 10, maximum 100
  jobId?: number;
  candidateId?: number;
  status?: string;         // "PENDING"|"SCREENING"|"INTERVIEW"|"OFFER"|"REJECTED"|"WITHDRAWN"
  orderBy?: string;        // "applied_at" (default) | "match_score" | "updated_at"
  orderDirection?: string; // "asc" | "desc" (default)
}
```

**Response:** `Result<Page<ApplicationResponse>>`

---

## Module 7: Interview Management

**Base Path**: `/interviews`

### 7.1 Schedule an Interview
```
POST /api/v1/interviews
Auth: ✅
```

**Request Body:**
```typescript
interface ScheduleInterviewRequest {
  applicationId: number;   // @NotNull
  interviewerId: number;   // @NotNull, interviewer user ID
  round?: number;          // @Min(1), interview round, auto-increments by default
  interviewType: string;   // @NotNull, @Pattern("PHONE|VIDEO|ONSITE|WRITTEN_TEST")
  scheduledAt: string;     // @NotNull, @Future, ISO datetime "yyyy-MM-dd HH:mm:ss"
  durationMinutes?: number; // @Min(15), default 60
}
```

> Interview type enum: `PHONE` (phone) | `VIDEO` (video) | `ONSITE` (on-site) | `WRITTEN_TEST` (written test)

**Response:** `Result<number>` — returns the interview record ID (Long)

---

### 7.2 Submit Interview Feedback
```
PUT /api/v1/interviews/{id}/feedback
Auth: ✅
Path Params: id (Long)
```

**Request Body:**
```typescript
interface SubmitFeedbackRequest {
  score: number;            // @Min(1), @Max(10)
  feedback: string;         // @NotBlank
  recommendation: string;   // @NotBlank, @Pattern("STRONG_YES|YES|NEUTRAL|NO|STRONG_NO")
}
```

> Recommendation enum: `STRONG_YES` | `YES` | `NEUTRAL` | `NO` | `STRONG_NO`

**Response:** `Result<null>`

---

### 7.3 Cancel Interview
```
POST /api/v1/interviews/{id}/cancel
Auth: ✅
Path Params: id (Long)
```

**Response:** `Result<null>`

---

### 7.4 Get Interview Record Details
```
GET /api/v1/interviews/{id}
Auth: ✅
Path Params: id (Long)
```

**Response:** `Result<InterviewResponse>`
```typescript
interface InterviewResponse {
  id: number;

  // Related information
  applicationId: number;
  interviewerId: number;
  interviewerName: string;  // interviewer name from related query

  // Interview schedule
  round: number;
  interviewType: string;         // "PHONE"|"VIDEO"|"ONSITE"|"WRITTEN_TEST"
  interviewTypeDesc: string;     // localized description
  scheduledAt: string;           // "yyyy-MM-dd HH:mm:ss"
  durationMinutes: number;

  // Status and result
  status: string;                // "SCHEDULED"|"COMPLETED"|"CANCELLED"|"NO_SHOW"
  statusDesc: string;            // localized description
  feedback: string | null;
  score: number | null;          // 1-10
  recommendation: string | null; // "STRONG_YES"|"YES"|"NEUTRAL"|"NO"|"STRONG_NO"
  recommendationDesc: string | null; // localized description

  // Time
  createdAt: string;
  updatedAt: string;
}
```

---

### 7.5 Query All Interview Rounds by Application
```
GET /api/v1/interviews/application/{applicationId}
Auth: ✅
Path Params: applicationId (Long)
```

**Response:** `Result<InterviewResponse[]>`

---

## Module 8: Webhook Management

**Base Path**: `/webhooks`

### Supported Event Types (12)
| Event Type | Description |
|------------|-------------|
| `resume.uploaded` | Resume uploaded |
| `resume.parse_completed` | Resume parsing completed |
| `resume.parse_failed` | Resume parsing failed |
| `candidate.created` | Candidate created |
| `candidate.updated` | Candidate updated |
| `application.submitted` | Application submitted |
| `application.status_changed` | Application status changed |
| `interview.scheduled` | Interview scheduled |
| `interview.completed` | Interview completed |
| `interview.cancelled` | Interview cancelled |
| `system.error` | System error |
| `system.maintenance` | System maintenance |

### 8.1 Create a Webhook Configuration
```
POST /api/v1/webhooks
Auth: ✅
```

**Request Body:**
```typescript
interface WebhookCreateRequest {
  url: string;         // @NotBlank, @Pattern(HTTP/HTTPS URL)
  events: string[];    // @NotNull, array of event types
  description?: string;
}
```

**Response:** `Result<WebhookResponse>`
```typescript
interface WebhookResponse {
  id: number;
  url: string;
  events: string[];
  description: string;
  enabled: boolean;
  failureCount: number;
  lastSuccessAt: string | null;
  lastFailureAt: string | null;
  createdAt: string;
  updatedAt: string;
  secretHint: string;  // secret hint, e.g. "abc1****xyz9"
}
```

---

### 8.2 Get All Webhook Configurations for the User
```
GET /api/v1/webhooks
Auth: ✅
```

**Response:** `Result<WebhookResponse[]>`

---

### 8.3 Delete a Webhook Configuration
```
DELETE /api/v1/webhooks/{id}
Auth: ✅
Path Params: id (Long)
```

**Response:** `Result<null>`

---

### 8.4 Test a Webhook
```
POST /api/v1/webhooks/{id}/test
Auth: ✅
Path Params: id (Long)
```

**Response:** `Result<boolean>` — true = delivery succeeded

---

### Webhook Event Delivery Format (Payload sent to the user's URL)
```typescript
interface WebhookPayload {
  eventId: string;                    // UUID
  eventType: string;                  // event type
  timestamp: string;                  // ISO datetime
  data: Record<string, any>;          // event data (varies by event type)
  signature: string;                  // HMAC-SHA256 signature
  version: string;                    // "1.0"
}
```

---

## Pagination Structure Page\<T\>

The backend uses MyBatis-Plus `Page<T>`, and the JSON serialized structure is:

```typescript
interface Page<T> {
  records: T[];        // data for the current page
  total: number;       // total number of records
  size: number;        // page size
  current: number;     // current page number (starting from 1)
  pages: number;       // total pages
  // MyBatis-Plus also includes the following fields (can be ignored):
  orders?: any[];
  optimizeCountSql?: boolean;
  searchCount?: boolean;
  optimizeJoinOfCountSql?: boolean;
  maxLimit?: number;
  countId?: string;
}
```

---

## Complete Entity Fields

### User (users table)
| Field | Java Type | Description |
|-------|-----------|-------------|
| `id` | Long | Primary key auto-increment |
| `username` | String | Username (unique) |
| `password` | String | BCrypt-encrypted (not returned to the frontend) |
| `email` | String | Email (unique) |
| `role` | String | "ADMIN" / "HR" / "INTERVIEWER" |
| `dailyAiQuota` | Integer | Daily AI quota |
| `status` | Integer | 0 = disabled, 1 = enabled |
| `createdAt` | LocalDateTime | |
| `updatedAt` | LocalDateTime | |

### Job (jobs table)
| Field | Java Type | Description |
|-------|-----------|-------------|
| `id` | Long | Primary key auto-increment |
| `title` | String | Job title |
| `department` | String | Department |
| `description` | String | Job description |
| `requirements` | String | Job requirements |
| `requiredSkills` | String | JSON-formatted skill tags |
| `salaryMin` | Integer | Minimum salary (K) |
| `salaryMax` | Integer | Maximum salary (K) |
| `experienceMin` | Integer | Minimum experience years |
| `experienceMax` | Integer | Maximum experience years |
| `education` | String | Education requirement |
| `jobType` | String | Job type |
| `status` | String | "DRAFT" / "PUBLISHED" / "CLOSED" |
| `creatorId` | Long | Creator ID |
| `viewCount` | Integer | View count |
| `deleted` | Integer | Logical delete: 0 = normal, 1 = deleted |
| `createdAt` | LocalDateTime | |
| `updatedAt` | LocalDateTime | |

### Resume (resumes table)
| Field | Java Type | Description |
|-------|-----------|-------------|
| `id` | Long | Primary key auto-increment |
| `userId` | Long | Uploader ID |
| `fileName` | String | File name |
| `filePath` | String | MinIO path |
| `fileUrl` | String | Download URL |
| `fileSize` | Long | File size (bytes) |
| `fileHash` | String | MD5 hash (unique index deduplication) |
| `fileType` | String | File type |
| `status` | String | "PARSING" / "COMPLETED" / "FAILED" |
| `errorMessage` | String | Error message |
| `createdAt` | LocalDateTime | |
| `updatedAt` | LocalDateTime | |

### Candidate (candidates table)
| Field | Java Type | DB Column Name | Description |
|-------|-----------|---------------|-------------|
| `id` | Long | id | Primary key auto-increment |
| `resumeId` | Long | resume_id | Resume ID (1:1) |
| `name` | String | name | Name |
| `phone` | String | phone | Phone number |
| `email` | String | email | Email |
| `gender` | String | gender | Gender |
| `age` | Integer | birth_year | Age |
| `education` | String | highest_education | Highest education |
| `school` | String | graduate_school | Graduating school |
| `major` | String | major | Major |
| `graduationYear` | Integer | graduation_year | Graduation year |
| `workYears` | Integer | experience_years | Work experience years |
| `currentCompany` | String | current_company | Current company |
| `currentPosition` | String | current_position | Current position |
| `skills` | List\<String\> | skills | JSON array |
| `workExperience` | List\<Map\> | work_experiences | JSON array |
| `projectExperience` | List\<Map\> | project_experience | JSON array |
| `selfEvaluation` | String | self_evaluation | Self-evaluation |
| `rawJson` | String | raw_extracted_json | AI raw JSON |
| `confidenceScore` | Double | confidence_score | Confidence (0-1) |
| `parsedAt` | LocalDateTime | parsed_at | Parsing time |
| `vectorId` | String | vector_id | Milvus vector ID |
| `aiSummary` | String | ai_summary | AI-generated summary |
| `createdAt` | LocalDateTime | created_at | |
| `updatedAt` | LocalDateTime | updated_at | |

### JobApplication (job_applications table)
| Field | Java Type | Description |
|-------|-----------|-------------|
| `id` | Long | Primary key auto-increment |
| `jobId` | Long | Job ID |
| `candidateId` | Long | Candidate ID |
| `matchScore` | BigDecimal | AI match score (0-100) |
| `matchReasons` | String | JSON-formatted match reasons |
| `matchCalculatedAt` | LocalDateTime | Matching calculation time |
| `status` | String | "PENDING"/"SCREENING"/"INTERVIEW"/"OFFER"/"REJECTED"/"WITHDRAWN" |
| `hrNotes` | String | HR notes |
| `appliedAt` | LocalDateTime | Application time |
| `updatedAt` | LocalDateTime | |

### InterviewRecord (interview_records table)
| Field | Java Type | Description |
|-------|-----------|-------------|
| `id` | Long | Primary key auto-increment |
| `applicationId` | Long | Related application ID |
| `interviewerId` | Long | Interviewer user ID |
| `round` | Integer | Interview round (1 = first round ...) |
| `interviewType` | String | "PHONE"/"VIDEO"/"ONSITE"/"WRITTEN_TEST" |
| `scheduledAt` | LocalDateTime | Planned interview time |
| `durationMinutes` | Integer | Duration (minutes) |
| `status` | String | "SCHEDULED"/"COMPLETED"/"CANCELLED"/"NO_SHOW" |
| `feedback` | String | Interview feedback |
| `score` | Integer | Score (1-10) |
| `recommendation` | String | "STRONG_YES"/"YES"/"NEUTRAL"/"NO"/"STRONG_NO" |
| `createdAt` | LocalDateTime | |
| `updatedAt` | LocalDateTime | |

### WebhookConfig (webhook_configs table)
| Field | Java Type | Description |
|-------|-----------|-------------|
| `id` | Long | Primary key auto-increment |
| `userId` | Long | User ID |
| `url` | String | Callback URL |
| `events` | String | JSON array (subscribed events) |
| `secret` | String | HMAC secret |
| `enabled` | Boolean | Whether enabled |
| `description` | String | Description |
| `failureCount` | Integer | Consecutive failure count |
| `lastFailureAt` | LocalDateTime | |
| `lastSuccessAt` | LocalDateTime | |
| `createdAt` | LocalDateTime | |
| `updatedAt` | LocalDateTime | |

### WebhookLog (webhook_logs table)
| Field | Java Type | Description |
|-------|-----------|-------------|
| `id` | Long | Primary key auto-increment |
| `webhookId` | Long | Webhook configuration ID |
| `eventType` | String | Event type |
| `payload` | String | Request payload JSON |
| `responseStatus` | Integer | HTTP response code |
| `responseBody` | String | Response content |
| `errorMessage` | String | Error message |
| `status` | String | "SUCCESS"/"FAILED"/"RETRYING" |
| `retryCount` | Integer | Retry count |
| `duration` | Long | Duration (ms) |
| `createdAt` | LocalDateTime | |

---

## TypeScript Type Definitions

The following are complete frontend TypeScript type definitions that can be copied directly:

```typescript
// ==================== General ====================

/** Unified response wrapper */
export interface Result<T = any> {
  code: number;
  message: string;
  data: T;
  timestamp: number;
}

/** MyBatis-Plus pagination structure */
export interface Page<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

// ==================== Enum values ====================

/** User role */
export type UserRole = 'ADMIN' | 'HR' | 'INTERVIEWER';

/** Job status */
export type JobStatus = 'DRAFT' | 'PUBLISHED' | 'CLOSED';

/** Resume parsing status */
export type ResumeStatus = 'PARSING' | 'COMPLETED' | 'FAILED';

/** Task status */
export type TaskStatus = 'PENDING' | 'PARSING' | 'COMPLETED' | 'FAILED';

/** Application status */
export type ApplicationStatus = 'PENDING' | 'SCREENING' | 'INTERVIEW' | 'OFFER' | 'REJECTED' | 'WITHDRAWN';

/** Interview type */
export type InterviewType = 'PHONE' | 'VIDEO' | 'ONSITE' | 'WRITTEN_TEST';

/** Interview status */
export type InterviewStatus = 'SCHEDULED' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW';

/** Interview recommendation */
export type Recommendation = 'STRONG_YES' | 'YES' | 'NEUTRAL' | 'NO' | 'STRONG_NO';

/** Batch upload item status */
export type BatchUploadItemStatus = 'QUEUED' | 'DUPLICATE' | 'FAILED';

/** Webhook event type */
export type WebhookEventType =
  | 'resume.uploaded'
  | 'resume.parse_completed'
  | 'resume.parse_failed'
  | 'candidate.created'
  | 'candidate.updated'
  | 'application.submitted'
  | 'application.status_changed'
  | 'interview.scheduled'
  | 'interview.completed'
  | 'interview.cancelled'
  | 'system.error'
  | 'system.maintenance';

// ==================== Auth module ====================

export interface RegisterRequest {
  username: string;
  password: string;
  email: string;
  role?: 'HR' | 'INTERVIEWER';
  verificationCode: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface SendVerificationCodeRequest {
  email: string;
}

export interface RefreshTokenRequest {
  refreshToken: string;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  userInfo: UserInfo;
}

export interface UserInfo {
  userId: number;
  username: string;
  email: string;
  role: UserRole;
  dailyAiQuota: number;
  todayAiUsed: number;
}

// ==================== Job module ====================

export interface CreateJobRequest {
  title: string;
  department?: string;
  description: string;
  requirements: string;
  requiredSkills?: string[];
  salaryMin: number;
  salaryMax: number;
  experienceMin?: number;
  experienceMax?: number;
  education?: string;
  jobType?: string;
}

export interface UpdateJobRequest {
  id: number;
  title?: string;
  department?: string;
  description?: string;
  requirements?: string;
  requiredSkills?: string[];
  salaryMin?: number;
  salaryMax?: number;
  experienceMin?: number;
  experienceMax?: number;
  education?: string;
  jobType?: string;
}

export interface JobQueryRequest {
  keyword?: string;
  department?: string;
  jobType?: string;
  education?: string;
  experienceMin?: number;
  salaryMin?: number;
  status?: JobStatus;
  pageNum?: number;
  pageSize?: number;
  orderBy?: 'created_at' | 'salary_max' | 'view_count';
  orderDirection?: 'asc' | 'desc';
}

export interface JobResponse {
  id: number;
  title: string;
  department: string;
  description: string;
  requirements: string;
  requiredSkills: string[];
  salaryMin: number;
  salaryMax: number;
  salaryRange: string;
  experienceMin: number;
  experienceMax: number;
  experienceRange: string;
  education: string;
  jobType: string;
  status: JobStatus;
  statusDesc: string;
  creatorId: number;
  viewCount: number;
  createdAt: string;
  updatedAt: string;
}

// ==================== Resume module ====================

export interface ResumeUploadResponse {
  taskId: string;
  resumeId: number;
  duplicated: boolean;
  message: string;
}

export interface BatchUploadResponse {
  totalCount: number;
  successCount: number;
  failedCount: number;
  items: BatchUploadItem[];
}

export interface BatchUploadItem {
  taskId: string | null;
  resumeId: number | null;
  fileName: string;
  status: BatchUploadItemStatus;
  message: string;
}

export interface TaskStatusResponse {
  status: TaskStatus;
  resumeId: number | null;
  candidateId: number | null;
  errorMessage: string | null;
  progress: number | null;
}

export interface Resume {
  id: number;
  userId: number;
  fileName: string;
  filePath: string;
  fileUrl: string;
  fileSize: number;
  fileHash: string;
  fileType: string;
  status: ResumeStatus;
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
}

// ==================== Candidate module ====================

export interface CandidateResponse {
  id: number;
  resumeId: number;
  name: string;
  phone: string;              // masked
  email: string;              // masked
  gender: string;
  age: number;
  education: string;
  school: string;
  major: string;
  graduationYear: number;
  workYears: number;
  currentCompany: string;
  currentPosition: string;
  skills: string[];
  workExperience: WorkExperienceItem[];
  projectExperience: ProjectExperienceItem[];
  selfEvaluation: string;
  confidenceScore: number;
  aiSummary: string;
  parsedAt: string;
  createdAt: string;
  updatedAt: string;
}

export interface WorkExperienceItem {
  company: string;
  position: string;
  startDate: string;
  endDate: string;
  description: string;
}

export interface ProjectExperienceItem {
  name: string;
  role: string;
  startDate: string;
  endDate: string;
  description: string;
  technologies: string[];
}

export interface CandidateUpdateRequest {
  name?: string;
  phone?: string;
  email?: string;
  gender?: string;
  age?: number;
  education?: string;
  school?: string;
  major?: string;
  graduationYear?: number;
  workYears?: number;
  currentCompany?: string;
  currentPosition?: string;
  skills?: string[];
  selfEvaluation?: string;
}

export interface CandidateQueryParams {
  page?: number;
  pageSize?: number;
  keyword?: string;
  education?: string;
  skill?: string;
  minWorkYears?: number;
  maxWorkYears?: number;
  currentPosition?: string;
}

// ==================== Smart Search module ====================

export interface SmartSearchRequest {
  query: string;
  topK?: number;
  minScore?: number;
}

export interface SmartSearchResponse {
  query: string;
  totalMatches: number;
  candidates: MatchedCandidate[];
}

export interface MatchedCandidate {
  candidateId: number;
  name: string;
  matchScore: number;
  currentPosition: string;
  currentCompany: string;
  education: string;
  workYears: number;
  skills: string[];
  aiSummary: string;
}

// ==================== Application module ====================

export interface CreateApplicationRequest {
  jobId: number;
  candidateId: number;
  hrNotes?: string;
}

export interface UpdateApplicationStatusRequest {
  status: 'SCREENING' | 'INTERVIEW' | 'OFFER' | 'REJECTED' | 'WITHDRAWN';
  hrNotes?: string;
}

export interface ApplicationResponse {
  id: number;
  jobId: number;
  jobTitle: string;
  candidateId: number;
  candidateName: string;
  matchScore: number;
  matchReasons: string[];
  matchCalculatedAt: string;
  status: ApplicationStatus;
  statusDesc: string;
  hrNotes: string;
  appliedAt: string;
  updatedAt: string;
}

export interface ApplicationQueryRequest {
  pageNum?: number;
  pageSize?: number;
  jobId?: number;
  candidateId?: number;
  status?: ApplicationStatus;
  orderBy?: 'applied_at' | 'match_score' | 'updated_at';
  orderDirection?: 'asc' | 'desc';
}

// ==================== Interview module ====================

export interface ScheduleInterviewRequest {
  applicationId: number;
  interviewerId: number;
  round?: number;
  interviewType: InterviewType;
  scheduledAt: string;
  durationMinutes?: number;
}

export interface SubmitFeedbackRequest {
  score: number;
  feedback: string;
  recommendation: Recommendation;
}

export interface InterviewResponse {
  id: number;
  applicationId: number;
  interviewerId: number;
  interviewerName: string;
  round: number;
  interviewType: InterviewType;
  interviewTypeDesc: string;
  scheduledAt: string;
  durationMinutes: number;
  status: InterviewStatus;
  statusDesc: string;
  feedback: string | null;
  score: number | null;
  recommendation: Recommendation | null;
  recommendationDesc: string | null;
  createdAt: string;
  updatedAt: string;
}

// ==================== Webhook module ====================

export interface WebhookCreateRequest {
  url: string;
  events: WebhookEventType[];
  description?: string;
}

export interface WebhookResponse {
  id: number;
  url: string;
  events: string[];
  description: string;
  enabled: boolean;
  failureCount: number;
  lastSuccessAt: string | null;
  lastFailureAt: string | null;
  createdAt: string;
  updatedAt: string;
  secretHint: string;
}

export interface WebhookPayload {
  eventId: string;
  eventType: WebhookEventType;
  timestamp: string;
  data: Record<string, any>;
  signature: string;
  version: string;
}
```

---

## API Endpoint Summary (40)

| # | Method | Path | Auth | Request | Response |
|---|--------|------|:----:|---------|----------|
| **Auth (5)** | | | | | |
| 1 | POST | `/auth/register` | ❌ | `RegisterRequest` | `Result<null>` |
| 2 | POST | `/auth/login` | ❌ | `LoginRequest` | `Result<LoginResponse>` |
| 3 | POST | `/auth/send-verification-code` | ❌ | `SendVerificationCodeRequest` | `Result<null>` |
| 4 | POST | `/auth/refresh` | ❌ | `RefreshTokenRequest` | `Result<LoginResponse>` |
| 5 | GET | `/auth/test` | ✅ | — | `Result<TestAuthResponse>` |
| **Job (8)** | | | | | |
| 6 | POST | `/jobs` | ✅ | `CreateJobRequest` | `Result<number>` |
| 7 | PUT | `/jobs` | ✅ | `UpdateJobRequest` | `Result<null>` |
| 8 | GET | `/jobs/{id}` | ❌ | — | `Result<JobResponse>` |
| 9 | GET | `/jobs` | ❌ | `JobQueryRequest` (query) | `Result<Page<JobResponse>>` |
| 10 | POST | `/jobs/{id}/publish` | ✅ | — | `Result<null>` |
| 11 | POST | `/jobs/{id}/close` | ✅ | — | `Result<null>` |
| 12 | DELETE | `/jobs/{id}` | ✅ | — | `Result<null>` |
| 13 | GET | `/jobs/hot` | ❌ | `?limit=10` | `Result<JobResponse[]>` |
| **Resume (5)** | | | | | |
| 14 | POST | `/resumes/upload` | ✅ | `multipart: file` | `Result<ResumeUploadResponse>` |
| 15 | POST | `/resumes/batch-upload` | ✅ | `multipart: files[]` | `Result<BatchUploadResponse>` |
| 16 | GET | `/resumes/tasks/{taskId}` | ✅ | — | `Result<TaskStatusResponse>` |
| 17 | GET | `/resumes/{id}` | ✅ | — | `Result<Resume>` |
| 18 | GET | `/resumes` | ✅ | `?page=1&size=10` | `Result<Page<Resume>>` |
| **Candidate (5)** | | | | | |
| 19 | GET | `/candidates/resume/{resumeId}` | ✅ | — | `Result<CandidateResponse>` |
| 20 | GET | `/candidates/{id}` | ✅ | — | `Result<CandidateResponse>` |
| 21 | PUT | `/candidates/{id}` | ✅ | `CandidateUpdateRequest` | `Result<CandidateResponse>` |
| 22 | DELETE | `/candidates/{id}` | ✅ | — | `Result<null>` |
| 23 | GET | `/candidates` | ✅ | `CandidateQueryParams` (query) | `Result<Page<CandidateResponse>>` |
| **Smart Search (1)** | | | | | |
| 24 | POST | `/candidates/smart-search` | ✅ | `SmartSearchRequest` | `Result<SmartSearchResponse>` |
| **Application (6)** | | | | | |
| 25 | POST | `/applications` | ✅ | `CreateApplicationRequest` | `Result<number>` |
| 26 | PUT | `/applications/{id}/status` | ✅ | `UpdateApplicationStatusRequest` | `Result<null>` |
| 27 | GET | `/applications/{id}` | ✅ | — | `Result<ApplicationResponse>` |
| 28 | GET | `/applications/job/{jobId}` | ✅ | `?pageNum=1&pageSize=10` | `Result<Page<ApplicationResponse>>` |
| 29 | GET | `/applications/candidate/{candidateId}` | ✅ | `?pageNum=1&pageSize=10` | `Result<Page<ApplicationResponse>>` |
| 30 | GET | `/applications` | ✅ | `ApplicationQueryRequest` (query) | `Result<Page<ApplicationResponse>>` |
| **Interview (5)** | | | | | |
| 31 | POST | `/interviews` | ✅ | `ScheduleInterviewRequest` | `Result<number>` |
| 32 | PUT | `/interviews/{id}/feedback` | ✅ | `SubmitFeedbackRequest` | `Result<null>` |
| 33 | POST | `/interviews/{id}/cancel` | ✅ | — | `Result<null>` |
| 34 | GET | `/interviews/{id}` | ✅ | — | `Result<InterviewResponse>` |
| 35 | GET | `/interviews/application/{applicationId}` | ✅ | — | `Result<InterviewResponse[]>` |
| **Webhook (4)** | | | | | |
| 36 | POST | `/webhooks` | ✅ | `WebhookCreateRequest` | `Result<WebhookResponse>` |
| 37 | GET | `/webhooks` | ✅ | — | `Result<WebhookResponse[]>` |
| 38 | DELETE | `/webhooks/{id}` | ✅ | — | `Result<null>` |
| 39 | POST | `/webhooks/{id}/test` | ✅ | — | `Result<boolean>` |

> All paths are prefixed with `/api/v1`; total of 39 endpoints (including auth/test there are 40 total).
