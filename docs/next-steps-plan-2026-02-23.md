# SmartATS Next Steps Development Plan

**Plan Date**: 2026-02-23  
**Current Progress**: ~92% (core business logic complete)

---

## Completed Review

| Phase | Content | Status |
|------|------|------|
| Base Framework | Spring Boot 3.2.5 + MyBatis-Plus + Security + Redis + RabbitMQ + MinIO | ✅ |
| Auth Module | Register/Login/JWT/Verification Code/Token Refresh | ✅ |
| Job Management | CRUD + Caching + Trending Rankings + View Count Sync | ✅ |
| Resume Upload | Upload/Deduplication/MQ Async/AI Parsing/Candidate Extraction | ✅ |
| Candidate Module | CRUD + Advanced Filtering + Caching + Data Masking | ✅ |
| Job Applications | Create/Status Change/Multi-dimensional Queries + N+1 Fix | ✅ |
| Interview Records | Schedule/Feedback/Cancel/Query + N+1 Fix | ✅ |
| Webhook | Create/Delete/Test/13 Event Types/Signature Verification | ✅ |
| Code Optimization | 34 Quality Issues Fixed (Security/Performance/Architecture/Exceptions) | ✅ |

---

## Next Steps by Priority

### 🔴 Step 1: Add Unit Tests (Highest Priority)

**Reason**: Current test coverage is about 0%, with only 1 MinIO integration test. Core business logic has no test protection at all.

**Plan**:

| Module | Test Target | Focus |
|------|---------|------|
| auth | UserService | Register/Login/Token refresh logic |
| job | JobService | CRUD + Caching Strategy + trending rankings |
| resume | ResumeService | Upload/deduplication/MQ publishing |
| resume | ResumeParseConsumer | Consumption/idempotency/retry flow |
| candidate | CandidateService | Advanced filtering/caching/update |
| application | JobApplicationService | Create/status transitions/duplicate prevention |
| interview | InterviewService | Schedule/feedback/cancel |

**Technical Approach**:
- JUnit 5 + Mockito + Spring Boot Test
- Service layer: @ExtendWith(MockitoExtension.class), mock Mapper/Redis
- Controller layer: @WebMvcTest + MockMvc
- Target coverage: 60%+

**Estimated Effort**: 1-2 weeks

---

### 🟡 Step 2: Batch Resume Upload

**Reason**: HR needs batch imports in real-world scenarios; single-file uploading is far too slow.

**To Implement**:
- `POST /api/v1/resumes/batch-upload` — supports up to 20 files
- Each file independently goes through MD5 deduplication + MQ async parsing
- Overall response: total count, success count, duplicate count, per-file taskId
- Rate limiting: Redis + Lua (maximum 3 batch requests per minute)

**Estimated Effort**: 1-2 days

---

### 🟡 Step 3: Swagger / OpenAPI Integration

**Reason**: The 37 API endpoints have no online documentation, which hinders frontend integration and debugging.

**Technology Stack**: SpringDoc OpenAPI 3

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.3.0</version>
</dependency>
```

**Configuration Key Points**:
- Access URL: `http://localhost:8080/swagger-ui.html`
- JWT Bearer Token input box
- SecurityConfig allows `/swagger-ui/**` and `/v3/api-docs/**`
- Add `@Operation` + `@Parameter` annotations to key endpoints

**Estimated Effort**: 0.5 days

---

### 🟡 Step 4: Vector Search (RAG Semantic Search)

**Reason**: The project's differentiating highlight; AI semantic search depends on the data accumulated by the preceding modules.

**Technology Stack Recommendation**:

| Option | Pros | Cons | Recommended Scenario |
|------|------|------|---------|
| Redis Vector | Redis already in place, zero-cost extension | Limited capabilities | MVP quick validation |
| PgVector | No extra component needed | Requires database migration | Lightweight scenarios |
| Milvus | Dedicated vector database, high performance | Requires an extra Docker service | Large-scale data |

**Recommend validating quickly with Redis Vector first**, then migrating to Milvus once the data volume grows.

**Implementation Steps**:
1. After a candidate is saved, call the Spring AI `EmbeddingModel` to generate vectors
2. Store them in the vector store (Redis/Milvus)
3. At search time, generate a vector for the query and retrieve Top-K by cosine similarity
4. Fuse the results with keyword search results (RRF algorithm)

**Estimated Effort**: 1 week

---

### 🟢 Step 5: Engineering Polish

**Content**:
- Environment-separated configuration (dev/staging/prod profiles)
- CORS production domain configuration (replace `*`)
- Move sensitive configuration to environment variables
- Docker multi-stage build and Deployment
- Improved monitoring and logging

**Estimated Effort**: 1-2 days

---

## Development Order

```
Now ──────────────────────────────────────────────── Future
  │
  ├─ Step 1: Unit tests (1-2 weeks)
  │
  ├─ Step 2: Batch upload (1-2 days)
  │
  ├─ Step 3: Swagger docs (0.5 days) — can be interleaved
  │
  ├─ Step 4: Vector retrieval / RAG (1 week)
  │
  └─ Step 5: Engineering polish (1-2 days)
```

**Total Estimated Time**: 3-4 weeks

---

## Technical Decision Notes

### Confirmed Design Principles
- Redis caching: cache-aside pattern, delete after write (delayed double deletion)
- Message queue: Direct Exchange + DLQ, manual ACK, republish for retries
- Distributed locks: Redisson Watchdog mode
- ORM: LambdaQueryWrapper (string-based QueryWrapper forbidden)
- Redis client: StringRedisTemplate + ObjectMapper
- Exception handling: unified BusinessException + ResultCode
- JWT extraction: via `Authentication.getPrincipal()`

### Pending Decisions

| Question | Options | Recommendation |
|------|------|------|
| Vector database | Redis Vector / PgVector / Milvus | Redis Vector first |
| Frontend technology | Pure API / simple frontend | Pure API + Swagger |
| Deployment approach | Docker Compose / K8s | Current Docker Compose is sufficient |

---

*Document updated: 2026-02-23*
