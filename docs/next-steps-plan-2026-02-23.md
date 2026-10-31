# SmartATS Next Development Roadmap

**Date Created**: 2026-02-23  
**Current Progress**: ~92% (Core business logic completed)

---

## Completed Work Summary

| Phase | Description | Status |
|------|------|------|
| Foundation | Spring Boot 3.2.5 + MyBatis-Plus + Security + Redis + RabbitMQ + MinIO | ✅ |
| Authentication Module | Registration/Login/JWT/CAPTCHA/Token Refresh | ✅ |
| Job Management | CRUD + Caching + Trending Jobs + View Count Synchronization | ✅ |
| Resume Upload | Upload/Deduplication/MQ Asynchronous Processing/AI Parsing/Candidate Extraction | ✅ |
| Candidate Module | CRUD + Advanced Filtering + Caching + Data Masking | ✅ |
| Job Applications | Creation/Status Changes/Multi-dimensional Queries + N+1 Query Fix | ✅ |
| Interview Records | Scheduling/Feedback/Cancellation/Queries + N+1 Query Fix | ✅ |
| Webhook | Creation/Deletion/Testing/13 Event Types/Signature Verification | ✅ |
| Code Optimization | 34 Code Quality Issues Fixed (Security/Performance/Architecture/Exception Handling) | ✅ |

---

## Next Development Priorities

### 🔴 Step 1: Expand Unit Test Coverage (Highest Priority)

**Reason**: Current test coverage is approximately 0%, with only one MinIO integration test. The core business logic currently has no test coverage.

**Plan**:

| Module | Testing Target | Key Areas |
|------|---------|------|
| auth | UserService | Registration/Login/Token Refresh Logic |
| job | JobService | CRUD + Caching Strategy + Trending Jobs |
| resume | ResumeService | Upload/Deduplication/MQ Message Publishing |
| resume | ResumeParseConsumer | Consumption/Idempotency/Retry Workflow |
| candidate | CandidateService | Advanced Filtering/Caching/Updates |
| application | JobApplicationService | Creation/Status Transitions/Duplicate Prevention |
| interview | InterviewService | Scheduling/Feedback/Cancellation |

**Technical Approach**:
- JUnit 5 + Mockito + Spring Boot Test
- Service Layer: `@ExtendWith(MockitoExtension.class)`, mock Mapper/Redis dependencies
- Controller Layer: `@WebMvcTest` + MockMvc
- Target test coverage: 60%+

**Estimated Effort**: 1–2 weeks

---

### 🟡 Step 2: Batch Resume Upload

**Reason**: In real-world HR scenarios, batch importing resumes is necessary. Uploading files individually is inefficient.

**Implementation Requirements**:
- `POST /api/v1/resumes/batch-upload` — Support up to 20 files
- Each file independently goes through MD5 deduplication and MQ asynchronous parsing.
- Overall response: Total count, successful count, duplicate count, and individual `taskId` values for each file.
- Rate limiting: Redis + Lua (maximum of 3 batch requests per minute).

**Estimated Effort**: 1–2 days

---

### 🟡 Step 3: Integrate Swagger / OpenAPI

**Reason**: The 37 API endpoints currently have no online documentation, making frontend integration and debugging difficult.

**Technical Approach**: SpringDoc OpenAPI 3

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.3.0</version>
</dependency>
```

**Configuration Requirements**:
- Access URL: `http://localhost:8080/swagger-ui.html`
- JWT Bearer Token input field
- Configure `SecurityConfig` to permit `/swagger-ui/**` and `/v3/api-docs/**`
- Add `@Operation` and `@Parameter` annotations to key API endpoints.

**Estimated Effort**: 0.5 days

---

### 🟡 Step 4: Vector Search (RAG Semantic Search)

**Reason**: This is a key differentiating feature of the project. AI semantic search depends on data accumulated by the preceding modules.

**Recommended Technology Options**:

| Solution | Advantages | Disadvantages | Recommended Use Case |
|------|------|------|---------|
| Redis Vector | Redis is already available; zero-cost extension | Limited functionality | Rapid MVP validation |
| PgVector | No additional component required | Requires database migration | Lightweight scenarios |
| Milvus | Specialized vector database with high performance | Requires an additional Docker service | Large-scale datasets |

**Recommendation: Start with Redis Vector for rapid validation**, then migrate to Milvus when the data volume increases.

**Implementation Steps**:

1. After saving a candidate, call Spring AI's `EmbeddingModel` to generate a vector embedding.
2. Store the embedding in the vector store (Redis/Milvus).
3. During search, generate an embedding for the query and retrieve the Top-K results using cosine similarity.
4. Combine the results with keyword search results using the RRF algorithm.

**Estimated Effort**: 1 week

---

### 🟢 Step 5: Engineering Improvements

**Scope**:
- Separate environment configurations using `dev/staging/prod` profiles.
- Configure production CORS domains (replace `*`).
- Move sensitive configuration values into environment variables.
- Implement Docker multi-stage builds and deployment.
- Improve monitoring and logging.

**Estimated Effort**: 1–2 days

---

## Development Sequence

```text
Now ───────────────────────────────────────────────── Future
  │
  ├─ Step 1: Unit Testing (1–2 weeks)
  │
  ├─ Step 2: Batch Upload (1–2 days)
  │
  ├─ Step 3: Swagger Documentation (0.5 days) — Can be done in parallel
  │
  ├─ Step 4: Vector Search / RAG (1 week)
  │
  └─ Step 5: Engineering Improvements (1–2 days)
```

**Total Estimated Time**: 3–4 weeks

---

## Technical Decision Notes

### Established Design Principles

- Redis caching: Cache-aside pattern, delete after write (delayed double deletion).
- Message queue: Direct Exchange + DLQ, manual ACK, republish for retries.
- Distributed locking: Redisson Watchdog mechanism.
- ORM: `LambdaQueryWrapper` (string-based `QueryWrapper` is prohibited).
- Redis client: `StringRedisTemplate` + `ObjectMapper`.
- Exception handling: Unified `BusinessException` + `ResultCode`.
- JWT extraction: Use the `Authentication.getPrincipal()` approach.

### Pending Decisions

| Question | Options | Recommendation |
|------|------|------|
| Vector Database | Redis Vector / PgVector / Milvus | Start with Redis Vector |
| Frontend Technology | API Only / Simple Frontend | API Only + Swagger |
| Deployment Method | Docker Compose / Kubernetes (K8s) | Docker Compose is sufficient for the current stage |

---

*Document Last Updated: 2026-02-23*
