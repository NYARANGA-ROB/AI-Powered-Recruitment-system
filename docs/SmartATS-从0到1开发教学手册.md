# SmartATS — From Zero to One Development Guide (Java Beginner Edition)

> **Target Audience:** Developers learning to build an independent Java project for the first time.  
> **Goal:** Follow this guide step by step to build a functional, demonstrable, and extensible SmartATS backend application.  
> **Reference Document:** SmartATS Design Document (v1.0)

---

## 0. Set the Right Expectations First (Very Important)

This project is not a simple practice project involving just a few API endpoints. It is a complete backend system that includes:

1. User authentication.
2. File uploads and asynchronous processing.
3. AI-powered parsing (Spring AI).
4. Vector retrieval (RAG).
5. Caching, rate limiting, and distributed locking.
6. Message queues (RabbitMQ).

You should develop the project in stages. Do not start with the most difficult AI search functionality.

The correct development sequence is:

- **First, get the basic project structure running.**
- **Then, implement business CRUD operations.**
- **Next, implement asynchronous processing.**
- **Finally, integrate AI and vector search.**

---

## 1. Project Overview (Understand What You Are Building)

You can think of SmartATS as having four main business workflows:

1. **Authentication Workflow:** Registration / Login / JWT Authentication.
2. **Upload Workflow:** Resume Upload → Database Storage → MQ Publishing → Background Parsing.
3. **Search Workflow:** Keyword Search / Semantic Candidate Search.
4. **Recruitment Workflow:** Job Management → Job Applications → Interview Records.

Every time you complete one workflow, you should have a verifiable result that can be tested and evaluated.

---

## 2. Development Environment Setup (macOS)

> **Principle:** Configure your development environment completely before starting development to avoid troubleshooting environment issues while implementing business functionality.

### 2.1 Required Software

1. JDK 21 (LTS recommended).
2. Maven 3.9+.
3. Docker Desktop.
4. IntelliJ IDEA (Community Edition is sufficient).
5. Postman or Apifox (for API testing).
6. DBeaver / DataGrip (for database management).
7. Redis GUI Client (optional).

### 2.2 Verify Successful Installation

Verify each component individually:

1. Confirm that the Java version is correct.
2. Confirm that Maven is installed and working.
3. Confirm that Docker starts successfully.
4. Confirm that your IDE can import Maven projects.

### 2.3 Common Beginner Mistakes

1. **Incorrect Java version:** For example, mixing JDK 17 and JDK 8.
2. **Incorrect Maven configuration:** Using the wrong `settings.xml` file.
3. **Insufficient Docker memory:** This can cause MySQL or Milvus to malfunction.
4. **Port conflicts:** Local ports `3306`, `6379`, or `5672` may already be occupied.

It is recommended that you record port usage before starting development.

---

## 3. Initial Setup: Start Infrastructure Before Writing Business Logic

### 3.1 Prepare Containers According to the Design Document

Start the following services:

1. MySQL 8.0.
2. Redis 7.
3. RabbitMQ (including the Management UI).
4. Milvus (or PgVector initially; choose one).
5. MinIO (for resume file storage; recommended).

### 3.2 Service Health Check Checklist

After starting the services, verify each one:

1. MySQL is accessible and the `smartats` database exists.
2. Redis responds successfully to the `PING` command.
3. The RabbitMQ Management UI is accessible and you can log in.
4. The MinIO Console opens successfully.
5. The Milvus port is accessible (this can be postponed if RAG is not being implemented yet).

### 3.3 Acceptance Criteria

You must achieve the following:

- After restarting your computer, you should be able to start all infrastructure dependencies again within 10 minutes.

---

## 4. Project Initialization (Backend Foundation)

### 4.1 Create a Spring Boot Project

Select dependencies according to the technology stack in the design document:

1. Spring Web.
2. Validation.
3. Spring Security.
4. MyBatis-Plus.
5. MySQL Driver.
6. Redis + Redisson.
7. RabbitMQ.
8. Spring AI (Chat + Embedding).
9. Lombok (optional).

### 4.2 Establish the Project Directory Structure

Follow the recommended layered architecture in the design document:

- `common` — Shared API responses, exceptions, annotations, and aspects.
- `config` — Configuration classes.
- `module` — Business modules (`auth`, `resume`, `candidate`, `job`, `ai`).
- `infrastructure` — Infrastructure components (`redis`, `mq`, `storage`).

### 4.3 Implement Four Essential Shared Capabilities First

1. Unified API response structure: `Result`.
2. Global exception handling.
3. Standardized responses for parameter validation failures.
4. Global logging format (including at least `traceId`, request path, and execution time).

### 4.4 Phase Acceptance Criteria

You should be able to open a health-check endpoint in your browser and receive a response in the standardized API format.

Once this works, your backend foundation is ready.

---

## 5. Database Development (Establish a Solid Data Foundation)

### 5.1 Database and Table Creation Order

Follow the dependency relationships and create the tables in this order:

1. `users`
2. `jobs`
3. `resumes`
4. `candidates`
5. `job_applications`
6. `interview_records`

### 5.2 Why This Order?

Later tables depend on primary key relationships established by earlier tables. Creating foundational entities first and workflow-related entities afterward makes development and debugging easier.

### 5.3 Three Tasks to Complete for Every Table

1. Create the Entity class.
2. Create the Mapper, including basic CRUD operations.
3. Write a minimal database query test to verify that create, read, update, and delete operations work correctly.

### 5.4 Database Acceptance Checklist

1. All tables are visible in your database client.
2. Database indexes are created correctly.
3. Test data can be inserted without errors.
4. JSON fields can be read and written correctly.

---

## 6. First Complete Business Workflow: Authentication Module

> **Reason:** All subsequent business APIs depend on user identity and authentication.

### 6.1 Target API Endpoints

1. User registration.
2. User login.
3. Token refresh.

### 6.2 Components to Implement First

1. User DTOs (request and response objects).
2. User service layer.
3. Password encryption using BCrypt.
4. JWT generation and validation.
5. Spring Security filter chain.

### 6.3 Recommended Development Sequence

Follow these steps in order:

1. Implement registration first and verify that users can be saved to the `users` table.
2. Implement login and return an `accessToken`.
3. Integrate authentication filters (all endpoints except login and registration should require a valid token).
4. Finally, implement `refreshToken` functionality.

### 6.4 Acceptance Criteria

1. Unauthenticated requests to protected business endpoints return HTTP 401.
2. Authenticated users can access protected endpoints using a valid token.
3. Expired access tokens can be refreshed successfully.

---

## 7. Second Business Workflow: Job Management

This is a moderately difficult module and an excellent opportunity to practice CRUD operations.

### 7.1 Target API Endpoints

1. Create a job.
2. Retrieve job details.
3. Publish / Close a job.
4. Retrieve trending jobs.

### 7.2 Key Learning Areas

1. Paginated queries.
2. Conditional filtering.
3. Redis caching and retrieval strategies.
4. Cache invalidation (deleting cached data after updates).

### 7.3 Caching Strategy (Must Understand)

1. **Reading job details:** Check Redis first. If the data does not exist, retrieve it from MySQL and populate the cache.
2. **Updating job status:** Update MySQL first, then delete the corresponding cache entry.
3. **Trending jobs:** Use Redis Sorted Sets (ZSet) to maintain job popularity scores.

### 7.4 Acceptance Criteria

When you call the job details endpoint repeatedly, the second request should be noticeably faster than the first because it retrieves data from the cache.

---

## 8. Third Business Workflow: Resume Upload and Asynchronous Parsing

> This is the most important workflow in the project. Divide it into two components: the upload side and the consumer side.

### 8.1 Upload Side (Return Quickly and Synchronously)

#### Upload Steps (Follow This Exact Order)

1. Receive the uploaded file and validate its type and size.
2. Calculate the file's MD5 hash.
3. Check Redis for duplicate file markers.
4. Save the file locally or
