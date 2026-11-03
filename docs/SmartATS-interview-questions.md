# SmartATS Project Interview Question Bank

> Interviewer's perspective: in-depth technical interview questions about the SmartATS recruitment management system

---

## I. Spring Framework and Core Principles (Fundamentals + Practice)

### 1.1 Spring Boot Auto-Configuration
**Theory question**: What is the principle behind Spring Boot auto-configuration? What does the @SpringBootApplication annotation do?

**Practical question**: The project uses several @Configuration classes (such as SecurityConfig, RabbitMQConfig, RedissonConfig). Explain how their loading order and dependencies are handled.

---

### 1.2 Transaction Management
**Theory question**: In which situations does the @Transactional annotation stop working? Why does the project consistently use `@Transactional(rollbackFor = Exception.class)`?

**Practical question**: The resume parsing flow in ResumeParseConsumer involves file extraction, AI parsing, candidate saving, vectorization and more. Why is there no transaction around it? If one of the middle steps fails, how is data consistency guaranteed?

---

### 1.3 Spring AOP
**Theory question**: What proxy styles does Spring AOP offer? What is the difference between JDK dynamic proxies and CGLIB?

**Practical question**: Why did the project's @Async annotation only take effect after being extracted into a separate CacheEvictionService? What would happen if an async method were called directly inside JobService?

---

### 1.4 Spring Security
**Theory question**: What is the Spring Security authentication and authorization flow? How is JWT integrated into Spring Security?

**Practical question**: The project uses stateless JWT authentication with a 2-hour access token and a 7-day refresh token. Analyze:
1. Where are JWTs stored?
2. Why was the refresh token expiry designed as 7 days?
3. If a user does not visit for 7 consecutive days, what happens on the next login?

---

## II. Advanced Redis Usage (Key Topics)

### 2.1 Caching Strategy
**Theory question**: What are the differences between the Cache-Aside, Read-Through, Write-Through and Write-Behind caching patterns?

**Practical question**: The project uses the Cache-Aside pattern. Explain the caching logic in JobService.getJobDetail():

```java
// Atomically increment the Redis counter
Long redisIncrement = redisTemplate.opsForValue().increment(counterKey);

// Try to read from the cache
String cachedJson = redisTemplate.opsForValue().get(cacheKey);
if (cachedJson != null) {
    JobResponse cached = objectMapper.readValue(cachedJson, JobResponse.class);
    cached.setViewCount(baseViewCount + redisIncrement.intValue());
    return cached;
}

// Cache miss: query the database and repopulate the cache
Job job = jobMapper.selectById(id);
response.setViewCount(baseViewCount + redisIncrement.intValue());
redisTemplate.opsForValue().set(cacheKey, json, 30, TimeUnit.MINUTES);
```

**Follow-up**: Why use an atomic counter instead of updating the database's view_count column directly? If the service restarts, will the Redis counter be lost?

---

### 2.2 Delayed Double Deletion
**Theory question**: What is delayed double deletion? Why is the delay needed, and how is the delay duration determined?

**Practical question**: The delayed double deletion implementation in CacheEvictionService is as follows:

```java
@Async("asyncExecutor")
public void asyncDeleteCache(String cacheKey) {
    Thread.sleep(500);
    redisTemplate.delete(cacheKey);
}
```

**Follow-up**:
1. Why is the delay 500ms rather than 1 second?
2. The first deletion happens at the beginning of JobService.updateJob(), and the second runs asynchronously after the database update. Can this order be swapped? Why or why not?
3. What is the impact if the asynchronous delayed deletion fails (e.g., the thread pool rejects the task)?

---

### 2.3 Distributed Locks
**Theory question**: What is the implementation principle of a Redisson distributed lock? How does the Watchdog mechanism work?

**Practical question**: ResumeParseConsumer acquires a distributed lock on the fileHash:

```java
RLock lock = redissonClient.getLock(lockKey);
boolean acquired = lock.tryLock(10, 300, TimeUnit.SECONDS);
if (!acquired) {
    channel.basicAck(deliveryTag, false);
    return;
}
```

**Follow-up**:
1. Is the lockKey design reasonable? What happens if two different files collide on the same MD5?
2. Why is the wait time 10 seconds and the automatic lock release time 300 seconds?
3. If the lock is acquired successfully but the service crashes during parsing, will the lock be released automatically?

---

### 2.4 Idempotency Design
**Theory question**: What is idempotency? How do you guarantee idempotency in a distributed system?

**Practical question**: ResumeParseConsumer uses the Redis setIfAbsent command to guarantee idempotency:

```java
String idempotentKey = "idempotent:resume:" + resumeId;
Boolean alreadyProcessed = redisTemplate.opsForValue()
        .setIfAbsent(idempotentKey, "1", 1, TimeUnit.HOURS);
if (Boolean.FALSE.equals(alreadyProcessed)) {
    channel.basicAck(deliveryTag, false);
    return;
}
```

**Follow-up**:
1. The idempotency check has a 1-hour TTL. If the business requires no duplicate parsing within 48 hours, how would you improve it?
2. Why is the idempotency key based on resumeId rather than taskId?
3. If a message is consumed twice (e.g., a RabbitMQ network glitch loses the ACK), can this idempotency check prevent duplicate parsing?

---

### 2.5 Atomic Counters
**Theory question**: What is the implementation principle of the Redis INCR command? How is count accuracy guaranteed under high concurrency?

**Practical question**: The project counts page views with Redis INCR plus periodic sync to the database:

```java
// Increment on every visit
Long redisIncrement = redisTemplate.opsForValue().increment(counterKey);

// Total views = accumulated DB value + Redis increment
response.setViewCount(baseViewCount + redisIncrement.intValue());
```

**Follow-up**:
1. Compared with updating the database directly, what are the pros and cons of this approach?
2. If the Redis counter has to be synced to the database on a schedule, which strategy should be used: GETDEL, or GET followed by DEL?
3. How do you prevent data inconsistency caused by a lost Redis counter?

---

## III. Message Queues and Asynchronous Processing

### 3.1 Core RabbitMQ Concepts
**Theory question**: What are the Exchange, Queue, Binding and Routing Key in RabbitMQ? What are the differences between the Direct, Fanout and Topic exchange types?

**Practical question**: The project's RabbitMQ configuration is as follows:

```java
@Bean
public DirectExchange resumeExchange() {
    return new DirectExchange(RabbitMQConfig.RESUME_EXCHANGE, true, false);
}

@Bean
public Queue resumeParseQueue() {
    return QueueBuilder.durable(RabbitMQConfig.RESUME_PARSE_QUEUE)
            .withArgument("x-dead-letter-exchange", RabbitMQConfig.DLX)
            .withArgument("x-dead-letter-routing-key", RabbitMQConfig.DLQ_ROUTING_KEY)
            .build();
}
```

**Follow-up**:
1. Why choose a Direct Exchange instead of a Topic Exchange?
2. Is the dead-letter exchange (DLX) configuration necessary? Why?
3. What does the durable parameter do? What are the consequences of setting it to false?

---

### 3.2 Message Reliability
**Theory question**: How do you guarantee that RabbitMQ messages are never lost? What must the producer, the broker and the consumer each do?

**Practical question**: The manual ACK handling in ResumeParseConsumer:

```java
try {
    // Business processing
    channel.basicAck(deliveryTag, false);
} catch (Exception e) {
    retryOrReject(channel, deliveryTag, message);
}

private void retryOrReject(Channel channel, long deliveryTag, ResumeParseMessage message) throws IOException {
    if (retryCount < 3) {
        message.setRetryCount(retryCount + 1);
        channel.basicPublish(exchange, routingKey, properties, json.getBytes());
        channel.basicAck(deliveryTag, false);  // ACK the original message
    } else {
        channel.basicNack(deliveryTag, false, false);  // reject without requeueing
    }
}
```

**Follow-up**:
1. Why not use `basicNack(deliveryTag, false, true)` to trigger RabbitMQ's redelivery, and republish the message yourself instead?
2. Is retryCount stored in the message body or the message headers? Why?
3. If the consumer finishes processing successfully but the network drops before the ACK is sent, will the message be consumed twice? Can the idempotency check cover this case?

---

### 3.3 Message Ordering and Concurrency
**Theory question**: How do you guarantee message ordering? What is the difference between single-threaded and multi-threaded consumption?

**Practical question**: Suppose two resume parsing messages for the same fileHash enter the queue one after another and the consumer processes them with multiple threads. How do you make sure the earlier message is handled first?

---

### 3.4 Message Backlog Handling
**Theory question**: How do you monitor and handle RabbitMQ message backlogs? What should you watch out for when scaling out consumers?

**Practical question**: If 100,000 resume parsing messages pile up, what is your investigation approach? How do you clear them quickly?

---

## IV. Distributed Systems and High Concurrency

### 4.1 Distributed Transactions
**Theory question**: What is the CAP theorem? What is the BASE theory? What are the differences between 2PC, 3PC, TCC, Saga and the local message table pattern?

**Practical question**: The resume upload flow touches MinIO storage, MySQL writes, MQ publishing and Redis caching. How is data consistency guaranteed?

---

### 4.2 API Rate Limiting
**Theory question**: What are the common rate limiting algorithms? What are the differences between token bucket, leaky bucket, fixed window and sliding window?

**Practical question**: The project implements batch upload rate limiting with Redis:

```java
String rateLimitKey = "rate:upload:" + userId;
String countStr = stringRedisTemplate.opsForValue().get(rateLimitKey);
if (countStr != null && Integer.parseInt(countStr) >= 5) {
    throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, "Too many batch upload requests");
}
stringRedisTemplate.opsForValue().increment(rateLimitKey);
stringRedisTemplate.expire(rateLimitKey, 60, TimeUnit.SECONDS);
```

**Follow-up**:
1. Is this implementation a fixed window or a sliding window?
2. If a user uploads 5 files at second 59 and another 5 files at second 61, what is the effective QPS?
3. How would you rewrite this as sliding window rate limiting?

---

### 4.3 Hot Key Cache Breakdown Under High Concurrency
**Theory question**: What are the differences between cache penetration, cache breakdown and cache avalanche? What solutions does each have?

**Practical question**: Assume the cache of a popular job entry expires while 1000 concurrent requests hit that endpoint. How do you prevent cache breakdown?

---

## V. Database and ORM

### 5.1 Index Principles
**Theory question**: What data structure do MySQL indexes use? What are the characteristics of a B+ tree? What is the difference between a clustered and a non-clustered index?

**Practical question**: The file_hash column of the resumes table has a unique index. Why?

```java
public Long getResumeIdByFileHash(String fileHash) {
    LambdaQueryWrapper<Resume> wrapper = new LambdaQueryWrapper<Resume>()
            .eq(Resume::getFileHash, fileHash)
            .select(Resume::getId);  // covering index
    Resume resume = resumeMapper.selectOne(wrapper);
    return resume != null ? resume.getId() : null;
}
```

**Follow-up**:
1. Why add select(Resume::getId)?
2. If the file_hash index were a normal index rather than a unique index, how would query performance be affected?

---

### 5.2 SQL Optimization and the N+1 Problem
**Theory question**: What is the N+1 problem? How do you avoid it?

**Practical question**: Batch loading of candidate information in SmartSearchService:

```java
List<Long> candidateIds = filteredResults.stream()
        .map(SearchResult::getCandidateId)
        .toList();
List<Candidate> candidates = candidateMapper.selectBatchIds(candidateIds);
Map<Long, Candidate> candidateMap = candidates.stream()
        .collect(Collectors.toMap(Candidate::getId, Function.identity()));
```

**Follow-up**:
1. Why not query one by one in a for loop?
2. What SQL does selectBatchIds produce? If candidateIds contains 1000 values, will the SQL exceed the length limit?

---

### 5.3 Transaction Isolation Levels
**Theory question**: What are MySQL's four transaction isolation levels? Which one is the default, and which concurrency problems does it solve?

**Practical question**: Resume creation and candidate saving run in different transactions. If the resume is created successfully but the candidate save fails, how is the data rolled back?

---

### 5.4 MyBatis-Plus
**Theory question**: What advantages does MyBatis-Plus's LambdaQueryWrapper have over the string-based QueryWrapper?

**Practical question**: The project mandates LambdaQueryWrapper everywhere. Explain:

```java
// ✅ Recommended
LambdaQueryWrapper<Job> wrapper = new LambdaQueryWrapper<Job>()
        .eq(Job::getStatus, JobStatus.PUBLISHED.getCode())
        .like(Job::getTitle, keyword);

// ❌ Forbidden
QueryWrapper<Job> wrapper = new QueryWrapper<Job>()
        .eq("status", "PUBLISHED")
        .like("title", keyword);
```

**Follow-up**:
1. If a field name is refactored, which style is safer?
2. Where does LambdaQueryWrapper's performance overhead come from?

---

## VI. Microservices and Containerization

### 6.1 Docker and Container Orchestration
**Theory question**: What is the difference between Docker and virtual machines? What are the core concepts of Kubernetes?

**Practical question**: The project's docker-compose.yml defines MySQL, Redis, RabbitMQ, MinIO and Milvus. What problems would you face deploying this to production?

---

### 6.2 Service Registration and Discovery
**Theory question**: What is the role of a service registry? What are the differences between Eureka, Nacos and Consul?

**Practical question**: The project is currently a monolith. If it were split into resume, job and candidate services, how would you handle inter-service calls?

---

## VII. AI and Vector Databases (Project Highlight)

### 7.1 Vector Database Principles
**Theory question**: What is vector similarity search? Which ANN (approximate nearest neighbour) algorithms exist? What are the differences between IVF_FLAT, HNSW and PQ?

**Practical question**: The project uses an IVF_FLAT index with this parameter:

```java
private static final int NLIST = 128;  // number of IVF_FLAT clusters
```

**Follow-up**:
1. What does the NLIST parameter do, and how do you tune it?
2. Why choose COSINE distance rather than Euclidean distance?
3. With 10 million rows of data, can IVF_FLAT still meet the performance requirements? How would you optimize?

---

### 7.2 Embedding Models
**Theory question**: What are Word2Vec, BERT and GPT? How do you choose the dimensionality of an embedding vector?

**Practical question**: The project uses Zhipu AI's embedding-3 model to produce 1024-dimensional vectors:

```java
public List<Float> generateCandidateEmbedding(Candidate candidate) {
    String text = buildCandidateText(candidate);
    return embed(text);
}

private String buildCandidateText(Candidate candidate) {
    // Concatenate name, education, skills, work experience, etc.
}
```

**Follow-up**:
1. Why concatenate these particular fields, and how are the weights distributed?
2. Why is the text truncated to 6000 characters?
3. If a candidate's self-assessment is very long (say 2000 words), will it drown out the other information?

---

### 7.3 RAG (Retrieval-Augmented Generation)
**Theory question**: What is RAG? What is the difference between RAG and fine-tuning?

**Practical question**: The RAG flow in SmartSearchService is:

```
Query text → Embedding → Milvus search → MySQL detail enrichment → assemble response
```

**Follow-up**:
1. Why enrich the details from MySQL instead of storing everything in Milvus?
2. If the TopK candidates returned by Milvus have already been deleted in MySQL, how do you handle that?
3. What is the basis for setting the similarity threshold to 0.3?

---

## VIII. System Design and Architecture

### 8.1 System Design
**Scenario question**: Assume the system must batch-parse more than 100,000 resumes and support second-level retrieval. How would you design the architecture?

**Follow-up**:
1. How do you guarantee parsing order?
2. How do you handle rate limiting and graceful degradation of the AI service?
3. How do you reach 1000+ QPS for vector search?

---

### 8.2 Geo-Distributed Active-Active
**Theory question**: What is geo-distributed active-active deployment? How is data consistency guaranteed?

**Practical question**: If SmartATS had to be deployed in two regions, Shanghai and Beijing, how would you design it?

---

### 8.3 Monitoring and Alerting
**Theory question**: What are the core APM monitoring metrics? What is the Prometheus + Grafana architecture?

**Practical question**: The project currently emits a lot of log output. How would you design a monitoring and alerting system?

---

## IX. Security and Risk Control

### 9.1 Authentication and Authorization
**Theory question**: What are the four OAuth 2.0 authorization grant types? What is PKCE for?

**Practical question**: The project authenticates with JWT. How would you design third-party login integration (e.g., WeChat QR code login)?

---

### 9.2 Data Masking
**Theory question**: What are the common masking algorithms? What is the difference between dynamic and static masking?

**Practical question**: The masking logic of DataMaskUtil in the project:

```java
public static String maskPhone(String phone) {
    if (phone == null || phone.length() < 7) {
        return phone;
    }
    return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
}
```

**Follow-up**:
1. Why keep the first 3 and last 4 digits of a phone number?
2. Should masking happen at the database layer or the application layer?
3. If administrators need to export the raw data, how would you implement that?

---

### 9.3 File Upload Security
**Theory question**: What are the security risks of file upload? How do you mitigate them?

**Practical question**: The validation logic of FileValidationUtil:

```java
public static void validateFile(String filename, String contentType, byte[] fileBytes) {
    // 1. File extension check
    if (!ALLOWED_EXTENSIONS.contains(getFileExtension(filename))) {
        throw new BusinessException(ResultCode.INVALID_FILE_TYPE, "Unsupported file type");
    }

    // 2. Content-Type check
    if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
        throw new BusinessException(ResultCode.INVALID_FILE_TYPE, "Content-Type mismatch");
    }

    // 3. Magic number check
    if (!validateMagicNumber(fileBytes)) {
        throw new BusinessException(ResultCode.INVALID_FILE_TYPE, "File content does not match its extension");
    }
}
```

**Follow-up**:
1. What is a magic number, and why is it worth validating?
2. If a user uploads an executable disguised as a PDF, will this validation catch it?
3. How does the 10MB file size limit help prevent DoS attacks?

---

## X. Performance Optimization and Tuning

### 10.1 JVM Tuning
**Theory question**: What does the JVM memory model look like? Which GC algorithms exist? What are the differences between G1 and CMS?

**Practical question**: The resume parsing service has to handle large files. How should the JVM parameters be set?

---

### 10.2 Database Optimization
**Theory question**: What is your approach to optimizing slow SQL? How do you read an EXPLAIN execution plan?

**Practical question**: The candidate list query combines multi-condition filtering and pagination. How do you optimize the SQL?

```java
LambdaQueryWrapper<Candidate> queryWrapper = new LambdaQueryWrapper<>();
if (education != null) {
    queryWrapper.eq(Candidate::getEducation, education);
}
if (minWorkYears != null) {
    queryWrapper.ge(Candidate::getWorkYears, minWorkYears);
}
// ... more conditions
```

**Follow-up**:
1. How would you design the composite index?
2. If an index stops being used, what are the possible causes?

---

### 10.3 Cache Performance
**Theory question**: How do you deal with Redis BigKey and HotKey problems?

**Practical question**: If the cache QPS of a popular job posting reaches 100,000+, how do you optimize?

---

## XI. Code Quality and Engineering Practice

### 11.1 Design Patterns
**Theory question**: Which design patterns are common, and how are they applied in real projects?

**Practical question**: Which design patterns does this project use? Give concrete examples.

---

### 11.2 Exception Handling
**Theory question**: What is the difference between checked and unchecked exceptions? When should each be used?

**Practical question**: Exception precedence in GlobalExceptionHandler:

```java
@ExceptionHandler(BusinessException.class)
public Result<?> handleBusinessException(BusinessException e) {
    log.warn("Business exception: {}", e.getMessage());
    return Result.error(e.getCode(), e.getMessage());
}

@ExceptionHandler(Exception.class)
public Result<?> handleException(Exception e) {
    log.error("System exception", e);
    return Result.error(ResultCode.INTERNAL_ERROR);
}
```

**Follow-up**:
1. Why is BusinessException placed first?
2. What would happen if BusinessException were declared after Exception?

---

### 11.3 Testing Strategy
**Theory question**: What are the differences between unit tests, integration tests and end-to-end tests? What is an acceptable test coverage bar?

**Practical question**: The project's SmartSearchServiceTest isolates dependencies with mocks:

```java
@ExtendWith(MockitoExtension.class)
class SmartSearchServiceTest {
    @Mock private EmbeddingService embeddingService;
    @Mock private VectorStoreService vectorStoreService;
    @Mock private CandidateMapper candidateMapper;
    // ...
}
```

**Follow-up**:
1. Why not start a real Milvus instance for integration testing?
2. If the full parsing flow (file upload → AI parsing → candidate saving) had to be tested, how would you design that test?

---

## XII. Scenario and Open-Ended Questions

### 12.1 Improving Resume Parsing
**Scenario question**: Users report that resume parsing is only 85% accurate. How do you improve it?

**Follow-up**:
1. How would you optimize the prompt for the AI model?
2. Should several AI models be combined for cross-validation?
3. How do you collect feedback data from users?

---

### 12.2 Scalability Design
**Scenario question**: If the system had to support resumes in multiple languages (English, Japanese, Korean), what changes would it need?

---

### 12.3 High Availability Design
**Scenario question**: If the Zhipu AI service goes down, how does the resume parsing flow degrade?

---

### 12.4 Data Migration
**Scenario question**: If resume files had to move from MinIO to Alibaba Cloud OSS, how would you design the migration plan?

---

### 12.5 Performance Bottlenecks
**Scenario question**: Load testing shows vector search averaging 500ms while the target is 50ms. How do you optimize?

---

## Interviewer Questioning Guidance

### Focus by Candidate Seniority

| Level | Focus | Recommended Questions |
|------|--------|----------|
| Junior (1-3 years) | Fundamentals | 1.1, 5.1, 5.4, 9.2, 11.2 |
| Mid-level (3-5 years) | Project implementation | 2.1, 2.2, 2.3, 3.2, 7.1 |
| Senior (5-8 years) | System design | 4.1, 4.2, 8.1, 10.2, 12.1 |
| Staff+ (8 years+) | Architectural ability | 8.2, 8.3, 10.3, 12.3, 12.5 |

### Questioning Techniques

1. **Progressive depth**: start from basic concepts and gradually move into project practice
2. **Push for details**: do not settle for surface answers; keep asking "why" and "how would you improve it"
3. **Comparative analysis**: ask candidates to weigh the trade-offs of different approaches
4. **Scenario framing**: pose concrete business scenarios to see how they reason about solutions
5. **Code review**: have candidates read project code and point out potential problems

### Scoring Dimensions

| Dimension | Weight | Criteria |
|------|------|----------|
| Fundamentals | 20% | Mastery of textbook knowledge, depth of understanding the principles |
| Project practice | 30% | Understanding of their own project, ability to justify the Technology Stack choices |
| Problem solving | 25% | Analysis approach to unfamiliar problems, soundness of the proposed solution |
| Code quality | 15% | Awareness of coding standards, application of design patterns |
| Communication | 10% | Logical clarity, ability to express complex concepts concisely |

---

## Appendix: Key Code Snippet Index

| Feature | File Path | Key Code Lines |
|------|----------|------------|
| Delayed double deletion | JobService.java:88, CacheEvictionService.java:33 | 2.2 |
| Distributed locks | ResumeParseConsumer.java:78-88 | 2.3 |
| Idempotency check | ResumeParseConsumer.java:67-75 | 2.4 |
| Atomic counter | JobService.java:187, 229 | 2.5 |
| MQ retry | ResumeParseConsumer.java:237-265 | 3.2 |
| Vector search | SmartSearchService.java:48-121 | 7.1, 7.3 |
| Embedding generation | EmbeddingService.java:44-56 | 7.2 |
| Rate limiting | ResumeService.java:358-365 | 4.2 |
| Data masking | DataMaskUtil.java:21-26, 36-51 | 9.2 |
| File validation | FileValidationUtil.java | 9.3 |

---

**Document version**: v1.0
**Created**: 2026-02-24
**Scope**: technical interviews for the SmartATS project
