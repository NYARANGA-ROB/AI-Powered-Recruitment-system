# SmartATS Development Handbook from Zero to One (Java Beginner Edition)

> Target audience: learners building their first standalone Java project  
> Goal: you can "follow along step by step" to complete a runnable, demonstrable, extensible SmartATS backend project  
> Based on: SmartATS Design Document (v1.0)

---

## 0. Set the Right Expectations First (Very Important)

This project is not a throwaway practice project where you "just write a few APIs"; it is a complete backend system that includes:
1. User authentication
2. File upload and asynchronous processing
3. AI parsing (Spring AI)
4. Vector retrieval (RAG)
5. Caching, rate limiting, distributed locks
6. Message queue (RabbitMQ)

You must progress by "phases"; don't start with the hardest part, AI search. The correct order is:
- **First get the basic skeleton running** → **then build the business CRUD** → **then build asynchronous parsing** → **finally build AI and vector search**.

---

## 1. The Project at a Glance (Understand What You Are Building First)

Think of SmartATS as 4 main flows:

1. **Login flow**: registration / login / JWT authentication
2. **Upload flow**: upload resume → save to database → publish to MQ → background parsing
3. **Search flow**: keyword/semantic candidate search
4. **Recruitment workflow**: job management → applications → interview records

Each time you finish one flow, you have an "acceptable, verifiable result".

---

## 2. Preparation Before Development (macOS Environment)

> Principle: set up the complete environment once at the start, to avoid fixing environment issues while developing later.

### 2.1 Mandatory Software List

1. JDK 21 (LTS recommended)
2. Maven 3.9+
3. Docker Desktop
4. IntelliJ IDEA (Community edition is fine)
5. Postman or Apifox (for testing APIs)
6. DBeaver / DataGrip (for inspecting the database)
7. Redis GUI tool (optional)

### 2.2 Verify the Installation Succeeded

Verify each one:
1. Java version is correct
2. Maven works
3. Docker starts normally
4. The IDE can import a Maven project

### 2.3 Most Common Beginner Pitfalls

1. **Wrong Java version** (e.g. mixing JDK 17/8)
2. **Maven using the wrong settings.xml**
3. **Docker memory too small**, causing MySQL/Milvus failures
4. **Local ports 3306/6379/5672 already occupied**

Start by recording which ports are occupied from day one.

---

## 3. First Setup: Start Only the Infrastructure, Write No Business Code

### 3.1 Prepare the Containers per the Design Document

Start the following services:
1. MySQL 8.0
2. Redis 7
3. RabbitMQ (including the management console)
4. Milvus (or use PgVector first — pick one of the two)
5. MinIO (for resume file storage; recommended to include)

### 3.2 Service Health Checklist

After startup, check each item:
1. MySQL is reachable and you can see the `smartats` database
2. Redis PING returns success
3. The RabbitMQ management page is accessible and you can log in
4. The MinIO console opens
5. The Milvus port is reachable (if you are not doing RAG yet, this can be deferred)

### 3.3 Acceptance Criteria

You must reach this level:
- After rebooting your machine, you can bring up all dependencies again within 10 minutes.

---

## 4. Project Initialization (Backend Skeleton)

### 4.1 Create the Spring Boot Project

Pick dependencies according to the Technology Stack in the design document:
1. Spring Web
2. Validation
3. Spring Security
4. MyBatis-Plus
5. MySQL Driver
6. Redis + Redisson
7. RabbitMQ
8. Spring AI (Chat + Embedding)
9. Lombok (optional)

### 4.2 Establish the Directory Structure

Strictly follow the layered structure recommended by the design document:
- common (shared response, exceptions, annotations, aspects)
- config (configuration classes)
- module (auth/resume/candidate/job/ai)
- infrastructure (redis/mq/storage)

### 4.3 Unified Foundation Capabilities (Build These 4 First)

1. Unified response structure `Result`
2. Global exception handling
3. A standard response format for parameter validation failures
4. Global log format (at minimum: traceId, request path, duration)

### 4.4 Phase Acceptance

You can open the health endpoint in a browser and see a response in the unified format — that means the skeleton is complete.

---

## 5. Database Phase (Lay the "Data Foundation" Solid First)

### 5.1 Table Creation Order (by Dependency)

Recommended order:
1. `users`
2. `jobs`
3. `resumes`
4. `candidates`
5. `job_applications`
6. `interview_records`

### 5.2 Why This Order

Because later tables depend on the primary-key relationships of earlier ones. Build the base entities before the workflow entities, and debugging becomes much easier.

### 5.3 Three Things to Do for Every Table

1. Write the entity class (Entity)
2. Write the Mapper (with basic CRUD)
3. Write a minimal query test (make sure create/read/update/delete work)

### 5.4 Database Acceptance Checklist

1. All tables are visible in your database client
2. Indexes are created correctly
3. Inserting test data raises no errors
4. JSON fields can be read and written normally

---

## 6. The First Complete Business Flow: Auth Module (Build This First)

> Reason: every later API depends on user identity.

### 6.1 Target APIs

1. Register
2. Login
3. Refresh token

### 6.2 Components to Build First

1. User DTOs (request/response)
2. User service layer
3. Password encryption (BCrypt)
4. JWT generation and verification
5. Security filter chain

### 6.3 Development Order (Recommended — Just Follow It)

1. Get registration working end to end (able to write into users)
2. Then implement login (returns accessToken)
3. Then wire in the authentication interceptor (everything except login/register requires a token)
4. Finally implement refreshToken

### 6.4 Acceptance Criteria

1. Accessing a business API while not logged in returns 401
2. After login, requests carrying a token succeed
3. An expired token can be refreshed

---

## 7. The Second Business Flow: Job Management (Medium Difficulty, Great for Practicing CRUD)

### 7.1 Target APIs

1. Create a job
2. Job details
3. Publish/close a job
4. Popular jobs

### 7.2 Key Learning Points

1. Paginated queries
2. Conditional filtering
3. Redis cache read/write strategy
4. Cache invalidation (delete the cache after an update)

### 7.3 Caching Strategy (Must Understand)

1. Reading details: query Redis first; on a miss, query MySQL and backfill the cache
2. Changing status: update MySQL, then delete the corresponding cache entry
3. Popular jobs: use a ZSet to store popularity scores

### 7.4 Acceptance Criteria

When you call the job details API twice in a row, the second call should be noticeably faster than the first (cache hit).

---

## 8. The Third Business Flow: Resume Upload and Asynchronous Parsing (Core)

> This is the most critical flow in the project, split into a "producer side" and a "consumer side".

## 8.1 Producer Side (Fast Synchronous Response)

### Upload Steps (Follow This Order Exactly)

1. Receive the file and validate its type/size
2. Compute the file MD5
3. Check the Redis deduplication marker
4. Save the file to local disk or MinIO
5. Write the `resumes` record (initial status)
6. Write the Redis task state `task:resume:{taskId}`
7. Publish an MQ message to the parsing queue
8. Return the taskId immediately

### Key Insight for Beginners

- The upload API must not wait for the AI parsing to finish.
- Parsing is asynchronous; the frontend polls the status by taskId.

## 8.2 Status Query API

When querying the task status:
1. Read Redis first
2. Only fall back to MySQL on a Redis miss

You must keep these 4 state transitions clear:
- `QUEUED` → `PROCESSING` → `SUCCESS`
- On failure, transition to `FAILED`

---

## 9. MQ Consumer: Parsing, Retries, Dead Letters (Advanced)

### 9.1 Main Consumer Flow

1. Receive the message
2. Perform an idempotency check (avoid duplicate processing)
3. Acquire a distributed lock (by fileHash)
4. Update the task status to PROCESSING
5. Call the AI to extract resume information
6. Write `candidates` + update `resumes`
7. Write to the vector database (embedding)
8. Update the task status to SUCCESS
9. ACK the message

### 9.2 Failure Handling Strategy

1. Recoverable errors: retry
2. Retry limit reached: send to the DLQ
3. Unrecoverable errors: fail immediately and record the reason

### 9.3 System Behaviors You Should Observe

1. Changes in the RabbitMQ queue message counts
2. Growing retry counts
3. Failed messages entering the dead-letter queue

---

## 10. AI Parsing Module (Spring AI)

### 10.1 First Achieve "Stable Structured Output"

Your first goal is not "the smartest" but "the most stable and parseable".

What you need to do:
1. Turn the extraction Prompt from the design document into a fixed template
2. Require the model to return strict JSON
3. Be tolerant of missing fields (null)
4. Set a fallback strategy for abnormal model output

### 10.2 AI Call Governance (Extremely Important)

1. Timeout control
2. Retry count limits
3. Traceable error logs
4. Per-user daily quota limits (Redis counters)

### 10.3 Acceptance Criteria

1. Parsing the same resume multiple times yields a consistent result structure
2. On parsing failure, the task status is persisted correctly

---

## 11. Candidate Module (Structured Data Display)

### 11.1 Target APIs

1. Candidate details
2. Candidate list
3. Intelligent search (build this later)

### 11.2 "User Experience Details" for Candidate Details

1. Phone number masking
2. Email masking
3. Cache candidate details (Redis, 30 minutes)

### 11.3 Acceptance Criteria

1. You can link from `resumeId` to the `candidate`
2. The page can display structured fields such as skills, experience, and education

---

## 12. Intelligent Search (RAG) Implementation Roadmap

> This is the most complex feature in the whole project; build it in 4 steps.

### Step 1: Vector Writing (Offline)

After resume parsing completes:
1. Generate a candidate text summary
2. Call the embedding model to generate vectors
3. Store them in the vector database and save the vector_id

### Step 2: Basic Semantic Recall

1. The user enters a query
2. Convert it to an embedding
3. Run a TopK search in the vector database

### Step 3: Metadata Filtering

Layer filter conditions onto the recall:
1. Years of experience
2. Education
3. Skill tags

### Step 4: LLM Reranking and Explanation

1. Hand the recall results to the LLM for ranking
2. Return matchScore, matchReasons, concerns

### Acceptance Criteria

When the input is "3+ years of Java + Spring Boot + Redis", the ranking should match common sense.

---

## 13. Application and Interview Workflow Module

### 13.1 Implementation Order

1. Create an application
2. Query the application list for a job
3. Update the application status
4. Create an interview
5. Submit interview feedback

### 13.2 Concurrency Safety Point

The apply API must prevent duplicates:
- Use a Redis distributed lock on `jobId + candidateId`

### 13.3 Acceptance Criteria

When the same candidate applies to the same job repeatedly, only one valid record is kept.

---

## 14. Rate Limiting System (Redis + Annotation + AOP)

### 14.1 Capabilities You Must Implement

1. Per-minute rate limiting
2. Per-day quota limiting
3. Atomic operations with Lua

### 14.2 Development Order

1. Start with a simple counter-based rate limiter
2. Then encapsulate the `@RateLimiter` annotation
3. Then intercept uniformly with AOP
4. Finally introduce Lua to guarantee atomicity

### 14.3 Acceptance Criteria

When a user exceeds their quota, the API steadily returns `42901`, without affecting other users.

---

## 15. Phased Testing Plan (Follow This Pace)

### Phase A: Basic Integration

1. Register/login
2. Job CRUD
3. Candidate list

### Phase B: Asynchronous Flow

1. Single-file upload
2. Task status polling
3. Consumer processes successfully

### Phase C: Failure Paths

1. Duplicate file upload
2. AI service timeout
3. MQ retry + DLQ

### Phase D: AI Search

1. Vector ingestion
2. Semantic search
3. Filtering + reranking

---

## 16. How You Should Study Each Day (Beginner Execution Edition)

### Fixed 3-Hour Daily Template

1. 30 minutes: review yesterday's implementation points (draw a flowchart)
2. 90 minutes: work on exactly one small goal (e.g. "get the upload API working end to end")
3. 30 minutes: self-test the APIs + record issues found
4. 30 minutes: write a development log (what I did today, where I failed, tomorrow's plan)

### Why This Works

Beginners fear "tasks that are too big" most of all. If you break tasks down to "one verifiable checkpoint that must be completed today", your progress will be very fast.

---

## 17. Milestone Plan (4-6 Weeks Recommended)

### Week 1

1. Environment setup
2. Project skeleton
3. Database table creation
4. Auth module

### Week 2

1. Job management
2. Basic candidate queries
3. Caching system

### Week 3

1. Resume upload
2. MQ asynchronous parsing
3. Task status tracking

### Week 4

1. AI extraction stability
2. Vector ingestion
3. Basic semantic search

### Weeks 5-6 (Optional Hardening)

1. Rate-limiting annotation + Lua
2. Dead-letter compensation tasks
3. Monitoring, logging, and performance optimization

---

## 18. Completeness Self-Check List (Check Every Item Before Launch)

### Functional Completeness

1. [ ] Auth module complete
2. [ ] Resume upload and parsing complete
3. [ ] Candidate information viewable
4. [ ] Job management complete
5. [ ] Application and interview workflow complete
6. [ ] AI search usable

### Stability

1. [ ] APIs have unified error codes
2. [ ] MQ retry and dead letters work
3. [ ] Redis caching and rate limiting work
4. [ ] Distributed locks prevent concurrent duplicates

### Operability

1. [ ] Key logs are traceable
2. [ ] Key configuration separates dev/prod
3. [ ] The dependency environment starts with one command

---

## 19. Common Beginner Mistakes (Avoid Them in Advance)

1. Starting with AI search right away, which stalls the whole project
2. Not defining the unified response and error codes first
3. Not implementing idempotency, so duplicate consumption creates dirty data
4. Missing cache-update logic, causing dirty reads
5. Improper lock usage causing deadlocks or ineffective locks
6. Writing "asynchronous tasks" as "synchronous blocking calls"

---

## 20. Your Day-One Task List — Start Right Now

Execute in order:

1. Prepare the local environment (JDK/Maven/Docker/IDE)
2. Start MySQL, Redis, RabbitMQ
3. Create the Spring Boot project and directory structure
4. Build the unified response body + global exception handling
5. Create the `users` table and finish the register/login APIs
6. Run your first end-to-end test with an API testing tool

After completing these 6 steps, you will have passed "the hardest part of going from zero to one".

---

## 21. Final Learning Advice for You

What you need most right now is not "more technical topics" but "a steady pace of progress":

1. Attack only one verifiable goal each day
2. Every feature must have acceptance criteria
3. Record the cause and fix for every exception
4. Review the overall architecture diagram once a week, so you don't lose sight of the forest for the trees

As long as you follow this handbook, you can absolutely complete the SmartATS backend development on your own.

---

## 22. How to Use This Handbook

Track your progress by checking things off as you develop:

1. After finishing each chapter, run a self-test
2. When stuck, go back to that chapter's "Acceptance Criteria" to locate the problem
3. Do not skip chapters; proceed strictly in order

> Conclusion: get it "running" first, then pursue "elegant". This is the fastest path for a beginner to finish a real project.
