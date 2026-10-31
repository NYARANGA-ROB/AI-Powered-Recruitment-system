# SmartATS Technical Interview Question Bank

> Interviewer’s perspective: In-depth technical interview questions for the SmartATS Applicant Tracking System.

---

## 1. Spring Framework and Core Principles (Theory + Practical Application)

### 1.1 Spring Boot Auto-Configuration

**Theory question:** What is the principle behind Spring Boot auto-configuration? What does the `@SpringBootApplication` annotation do?

**Practical question:** The project uses multiple `@Configuration` classes (such as `SecurityConfig`, `RabbitMQConfig`, and `RedissonConfig`). Explain how their loading order and dependencies are managed.

---

### 1.2 Transaction Management

**Theory question:** Under what circumstances can the `@Transactional` annotation fail? Why does the project consistently use `@Transactional(rollbackFor = Exception.class)`?

**Practical question:** The resume parsing workflow in `ResumeParseConsumer` involves file extraction, AI parsing, candidate persistence, vectorization, and several other operations. Why is a transaction not applied to the entire workflow? If one of the intermediate steps fails, how is data consistency maintained?

---

### 1.3 Spring AOP

**Theory question:** What proxy mechanisms does Spring AOP support? What are the differences between JDK dynamic proxies and CGLIB?

**Practical question:** Why must the project's `@Async` method be extracted into a separate `CacheEvictionService` for asynchronous execution to work? What happens if an asynchronous method is called directly from within `JobService`?

---

### 1.4 Spring Security

**Theory question:** How do the authentication and authorization processes work in Spring Security? How is JWT integrated into Spring Security?

**Practical question:** The project uses stateless JWT authentication, with access tokens valid for 2 hours and refresh tokens valid for 7 days. Analyze the following:

1. Where are JWTs stored?
2. Why is the refresh token's expiration period designed to be 7 days?
3. What happens when a user attempts to log in again after not accessing the system for 7 consecutive days?

---

## 2. Advanced Redis Applications (Core Interview Topics)

### 2.1 Caching Strategies

**Theory question:** What are the differences between the four caching patterns: Cache-Aside, Read-Through, Write-Through, and Write-Behind?

**Practical question:** The project uses the Cache-Aside pattern. Explain the caching logic in `JobService.getJobDetail()`:

```java
// Atomically increment the Redis counter
Long redisIncrement = redisTemplate.opsForValue().increment(counterKey);

// Attempt to retrieve the cached data
String cachedJson = redisTemplate.opsForValue().get(cacheKey);
if (cachedJson != null) {
    JobResponse cached = objectMapper.readValue(cachedJson, JobResponse.class);
    cached.setViewCount(baseViewCount + redisIncrement.intValue());
    return cached;
}

// Cache miss: query the database and populate the cache
Job job = jobMapper.selectById(id);
response.setViewCount(baseViewCount + redisIncrement.intValue());
redisTemplate.opsForValue().set(cacheKey, json, 30, TimeUnit.MINUTES);
```

**Follow-up questions:**

- Why use an atomic counter instead of directly updating the `view_count` field in the database?
- If the service restarts, will the counter stored in Redis be lost?

---

### 2.2 Delayed Double Deletion

**Theory question:** What is delayed double deletion? Why is a delay necessary? How should the delay duration be determined?

**Practical question:** The delayed double-deletion implementation in `CacheEvictionService` is as follows:

```java
@Async("asyncExecutor")
public void asyncDeleteCache(String cacheKey) {
    Thread.sleep(500);
    redisTemplate.delete(cacheKey);
}
```

**Follow-up questions:**

1. Why is the delay set to 500 ms rather than 1 second?
2. The first cache deletion occurs at the beginning of `JobService.updateJob()`, while the second deletion is performed asynchronously after the database update. Can this order be reversed? Why or why not?
3. What are the consequences if the asynchronous delayed deletion fails, for example, because the thread pool rejects the task?

---

### 2.3 Distributed Locks

**Theory question:** How does Redisson implement distributed locks? How does the Watchdog mechanism work?

**Practical question:** `ResumeParseConsumer` uses a distributed lock based on `fileHash`:

```java
RLock lock = redissonClient.getLock(lockKey);
boolean acquired = lock.tryLock(10, 300, TimeUnit.SECONDS);
if (!acquired) {
    channel.basicAck(deliveryTag, false);
    return;
}
```

**Follow-up questions:**

1. Is the design of `lockKey` reasonable? What happens if two different files have the same MD5 hash due to a collision?
2. Why is the lock acquisition wait time set to 10 seconds and the automatic lock-release time set to 300 seconds?
3. If the lock is acquired successfully but the service crashes during parsing, will the lock be released automatically?

---

### 2.4 Idempotency Design

**Theory question:** What is idempotency? How can idempotency be guaranteed in a distributed system?

**Practical question:** `ResumeParseConsumer` uses Redis `setIfAbsent` to guarantee idempotency:

```java
String idempotentKey = "idempotent:resume:" + resumeId;
Boolean alreadyProcessed = redisTemplate.opsForValue()
        .setIfAbsent(idempotentKey, "1", 1, TimeUnit.HOURS);
if (Boolean.FALSE.equals(alreadyProcessed)) {
    channel.basicAck(deliveryTag, false);
    return;
}
```

**Follow-up questions:**

1. The idempotency check has a TTL of 1 hour. If the business requirement is to prevent duplicate parsing within 48 hours, how should this be improved?
2. Why is `resumeId` used as the idempotency key instead of `taskId`?
3. If a message is consumed more than once, for example, because RabbitMQ loses an ACK due to network instability, can this idempotency check prevent duplicate parsing?

---

### 2.5 Atomic Counters

**Theory question:** How does the Redis `INCR` command work internally? How can accurate counting be maintained under high concurrency?

**Practical question:** The project uses Redis INCR combined with periodic synchronization to the database to track page views:

```java
// Increment the counter on every visit
Long redisIncrement = redisTemplate.opsForValue().increment(counterKey);

// Total views = accumulated database value + Redis increment
response.setViewCount(baseViewCount + redisIncrement.intValue());
```

**Follow-up questions:**

1. What are the advantages and disadvantages of this approach compared with directly updating the database?
2. If Redis counters need to be synchronized to the database periodically, which strategy should be used: `GETDEL` or GET followed by DEL?
3. How can data inconsistency caused by the loss of Redis counters be prevented?

---

## 3. Message Queues and Asynchronous Processing

### 3.1 RabbitMQ Core Concepts

**Theory question:** What are Exchange, Queue, Binding, and Routing Key in RabbitMQ? What are the differences between Direct, Fanout, and Topic exchanges?

**Practical question:** The project's RabbitMQ configuration is as follows:

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

**Follow-up questions:**

1. Why choose a Direct Exchange instead of a Topic Exchange?
2. Is configuring a Dead Letter Exchange (DLX) necessary? Why?
3. What is the purpose of the `durable` parameter? What happens if it is set to `false`?

---

### 3.2 Message Reliability

**Theory question:** How can RabbitMQ messages be prevented from being lost? What should the producer, broker, and consumer each do?

**Practical question:** The manual ACK handling logic in `ResumeParseConsumer` is as follows:

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
        channel.basicNack(deliveryTag, false, false);  // Reject without requeuing
    }
}
```

**Follow-up questions:**

1. Why not use `basicNack(deliveryTag, false, true)` to trigger RabbitMQ's retry mechanism? Why republish the message manually instead?
2. Should `retryCount` be stored in the message body or message header? Why?
3. If the consumer successfully processes a message but the network disconnects before the ACK is sent, will the message be consumed again? Can the idempotency check handle this situation?

---

### 3.3 Message Ordering and Concurrency

**Theory question:** How can message ordering be guaranteed? What are the differences between single-threaded and multi-threaded consumption?

**Practical question:** Suppose two resume parsing messages associated with the same `fileHash` enter the queue sequentially, but the consumer processes messages using multiple threads. How can you guarantee that the first message is processed before the second?

---

### 3.4 Handling Message Backlogs

**Theory question:** How can RabbitMQ message backlogs be monitored and handled? What should be considered when scaling consumers?

**Practical question:** If 100,000 resume parsing messages accumulate in the queue, what troubleshooting steps would you take? How would you resolve the backlog quickly?

---

## 4. Distributed Systems and High Concurrency

### 4.1 Distributed Transactions

**Theory question:** What are the CAP and BASE theories? What are the differences between 2PC, 3PC, TCC, Saga, and the Transactional Outbox pattern?

**Practical question:** The resume upload workflow involves MinIO storage, MySQL writes, MQ publishing, and Redis caching. How would you ensure data consistency across these components?

---

### 4.2 API Rate Limiting

**Theory question:** What are the common rate-limiting algorithms? What are the differences between Token Bucket, Leaky Bucket, Fixed Window, and Sliding Window algorithms?

**Practical question:** The project uses Redis to implement rate limiting for batch uploads:

```java
String rateLimitKey = "rate:upload:" + userId;
String countStr = stringRedisTemplate.opsForValue().get(rateLimitKey);
if (countStr != null && Integer.parseInt(countStr) >= 5) {
    throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, "Batch upload frequency is too high");
}
stringRedisTemplate.opsForValue().increment(rateLimitKey);
stringRedisTemplate.expire(rateLimitKey, 60, TimeUnit.SECONDS);
```

**Follow-up questions:**

1. Is this implementation based on a fixed window or a sliding window?
2. If a user uploads 5 files at second 59 and then uploads another 5 files at second 61, what is the actual request rate?
3. How would you modify this implementation to use sliding-window rate limiting?

---

### 4.3 High-Concurrency Cache Breakdown

**Theory question:** What are cache penetration, cache breakdown, and cache avalanche? What are the solutions to each problem?

**Practical question:** Suppose the cache for a popular job position expires, and 1,000 concurrent requests access the endpoint at the same time. How would you prevent cache breakdown?

---

## 5. Databases and ORM

### 5.1 Index Principles

**Theory question:** What data structure does MySQL use for indexes? What are the characteristics of a B+ tree? What are the differences between clustered and non-clustered indexes?

**Practical question:** The `file_hash` field in the `resumes` table has a unique index. Why?

```java
public Long getResumeIdByFileHash(String fileHash) {
    LambdaQueryWrapper<Resume> wrapper = new LambdaQueryWrapper<Resume>()
            .eq(Resume::getFileHash, fileHash)
            .select(Resume::getId);  // Covering index
    Resume resume = resumeMapper.selectOne(wrapper);
    return resume != null ? resume.getId() : null;
}
```

**Follow-up questions:**

1. Why use `select(Resume::getId)`?
2. If `file_hash` had a regular index instead of a unique index, how would query performance be affected?

---

### 5.2 SQL Optimization and the N+1 Problem

**Theory question:** What is the N+1 problem? How can it be avoided?

**Practical question:** `SmartSearchService` retrieves candidate information in batches:

```java
List<Long> candidateIds = filteredResults.stream()
        .map(SearchResult::getCandidateId)
        .toList();
List<Candidate> candidates = candidateMapper.selectBatchIds(candidateIds);
Map<Long, Candidate> candidateMap = candidates.stream()
        .collect(Collectors.toMap(Candidate::getId, Function.identity()));
```

**Follow-up questions:**

1. Why not use a `for` loop to query each candidate individually?
2. What SQL does `selectBatchIds` generate? If `candidateIds` contains 1,000 IDs, could the SQL statement exceed length limits?

---

### 5.3 Transaction Isolation Levels

**Theory question:** What are the four transaction isolation levels in MySQL? Which one is the default? Which concurrency problems does each level address?

**Practical question:** Resume creation and candidate persistence occur in separate transactions. If resume creation succeeds but candidate persistence fails, how should the data be rolled back?

---

### 5.4 MyBatis-Plus

**Theory question:** What advantages does MyBatis-Plus `LambdaQueryWrapper` have over the string-based `QueryWrapper`?

**Practical question:** The project requires consistent use of `LambdaQueryWrapper`. Explain the following:

```java
// ✅ Recommended
LambdaQueryWrapper<Job> wrapper = new LambdaQueryWrapper<Job>()
        .eq(Job::getStatus, JobStatus.PUBLISHED.getCode())
        .like(Job::getTitle, keyword);

// ❌ Prohibited
QueryWrapper<Job> wrapper = new QueryWrapper<Job>()
        .eq("status", "PUBLISHED")
        .like("title", keyword);
```

**Follow-up questions:**

1. If a field name is refactored, which approach is safer?
2. Where does the performance overhead of `LambdaQueryWrapper` occur?

---

## 6. Microservices and Containerization

### 6.1 Docker and Container Orchestration

**Theory question:** What are the differences between Docker and virtual machines? What are the core concepts of Kubernetes?

**Practical question:** The project's `docker-compose.yml` configures MySQL, Redis, RabbitMQ, MinIO, and Milvus. What challenges would arise when deploying this configuration to a production environment?

---

### 6.2 Service Registration and Discovery

**Theory question:** What is the purpose of a service registry? What are the differences between Eureka, Nacos, and Consul?

**Practical question:** The project is currently a monolithic application. If it were split into separate resume, job, and candidate services, how would you handle communication between these services?

---

## 7. AI and Vector Databases (Key Differentiators)

### 7.1 Vector Database Principles

**Theory question:** What is vector similarity search? What are the common ANN (Approximate Nearest Neighbor) algorithms? What are the differences between IVF_FLAT, HNSW, and PQ?

**Practical question:** The project uses an IVF_FLAT index with the following parameter configuration:

```java
private static final int NLIST = 128;  // Number of IVF_FLAT clusters
```

**Follow-up questions:**

1. What is the purpose of the `NLIST` parameter? How would you tune it?
2. Why choose COSINE distance instead of Euclidean distance?
3. If the data volume reaches 10 million records, will IVF_FLAT still meet performance requirements? How could it be optimized?

---

### 7.2 Embedding Models

**Theory question:** What are Word2Vec, BERT, and GPT? How should the dimensionality of embedding vectors be selected?

**Practical question:** The project uses Zhipu AI's `embedding-3` model to generate 1,024-dimensional vectors:

```java
public List<Float> generateCandidateEmbedding(Candidate candidate) {
    String text = buildCandidateText(candidate);
    return embed(text);
}

private String buildCandidateText(Candidate candidate) {
    // Concatenate fields such as name, education, skills, and work experience
}
```

**Follow-up questions:**

1. Why are these fields concatenated? How should their respective weights be assigned?
2. Why is the text truncated to 6,000 characters?
3. If a candidate has a very long self-description (for example, 2,000 Chinese characters), could it overwhelm the other information?

---

### 7.3 RAG (Retrieval-Augmented Generation)

**Theory question:** What is RAG? What are the differences between RAG and fine-tuning?

**Practical question:** The RAG workflow in `SmartSearchService` is as follows:

```text
Query text → Embedding → Milvus search → MySQL retrieves additional details → Assemble response
```

**Follow-up questions:**

1. Why is it necessary to retrieve additional details from MySQL? Why not store all information in Milvus?
2. If a candidate returned in Milvus's Top-K results has already been deleted from MySQL, how should this be handled?
3. What is the basis for setting the similarity threshold to 0.3?

---

## 8. System Design and Architecture

### 8.1 System Design

**Scenario question:** Suppose the system needs to support batch parsing of more than 100,000 resumes and second-level search responses. How would you design the system architecture?

**Follow-up questions:**

1. How would you guarantee parsing order?
2. How would you handle rate limiting and service degradation for the AI service?
3. How would you ensure that vector search achieves a QPS of 1,000 or higher?

---

### 8.2 Multi-Region Active-Active Architecture

**Theory question:** What is multi-region active-active architecture? How can data consistency be maintained?

**Practical question:** If SmartATS needs to be deployed in both Shanghai and Beijing, how would you design the architecture?

---

### 8.3 Monitoring and Alerting

**Theory question:** What are the core metrics of APM monitoring? What is the architecture of Prometheus + Grafana?

**Practical question:** The project currently generates a large volume of logs. How would you design a monitoring and alerting system?

---

## 9. Security and Risk Control

### 9.1 Authentication and Authorization

**Theory question:** What are the four OAuth 2.0 authorization grant types? What is the purpose of PKCE?

**Practical question:** The project uses JWT authentication. If third-party login (such as WeChat QR-code login) needs to be integrated, how would you design it?

---

### 9.2 Data Masking

**Theory question:** What are the common data-masking algorithms? What are the differences between dynamic and static data masking?

**Practical question:** The masking logic in `DataMaskUtil` is as follows:

```java
public static String maskPhone(String phone) {
    if (phone == null || phone.length() < 7) {
        return phone;
    }
    return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
}
```

**Follow-up questions:**

1. Why retain the first 3 and last 4 digits of a phone number?
2. Should data masking be performed at the database layer or the application layer?
3. If an administrator needs to export the original, unmasked data, how should this be implemented?

---

### 9.3 File Upload Security

**Theory question:** What are the security risks associated with file uploads? How can they be prevented?

**Practical question:** The validation logic in `FileValidationUtil` is as follows:

```java
public static void validateFile(String filename, String contentType, byte[] fileBytes) {
    // 1. Validate the file extension
    if (!ALLOWED_EXTENSIONS.contains(getFileExtension(filename))) {
        throw new BusinessException(ResultCode.INVALID_FILE_TYPE, "Unsupported file type");
    }

    // 2. Validate the Content-Type
    if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
        throw new BusinessException(ResultCode.INVALID_FILE_TYPE, "Content-Type mismatch");
    }

    // 3. Validate the Magic Number
    if (!validateMagicNumber(fileBytes)) {
        throw new BusinessException(ResultCode.INVALID_FILE_TYPE, "File content does not match the extension");
    }
}
```

**Follow-up questions:**

1. What is a Magic Number? Why is it necessary to validate it?
2. If a user uploads an executable file disguised as a PDF, can this validation prevent it?
3. How can the 10 MB file size limit help prevent DoS attacks?

---

## 10. Performance Optimization and Tuning

### 10.1 JVM Tuning

**Theory question:** What is the JVM memory model? What garbage collection algorithms are available? What are the differences between G1 and CMS?

**Practical question:** The resume parsing service needs to process large files. How should its JVM parameters be configured?

---

### 10.2 Database Optimization

**Theory question:** What is the approach to optimizing slow SQL queries? How should an execution plan generated by `EXPLAIN` be interpreted?

**Practical question:** The candidate list query includes multiple filtering conditions and pagination. How would you optimize the SQL?

```java
LambdaQueryWrapper<Candidate> queryWrapper = new LambdaQueryWrapper<>();
if (education != null) {
    queryWrapper.eq(Candidate::getEducation, education);
}
if (minWorkYears != null) {
    queryWrapper.ge(Candidate::getWorkYears, minWorkYears);
}
// ... More conditions
```

**Follow-up questions:**

1. How would you design a composite index?
2. What are the possible reasons for an index not being used?

---

### 10.3 Cache Performance

**Theory question:** How can Redis BigKey and HotKey problems be resolved?

**Practical question:** If the cache for a popular job position reaches a QPS of more than 100,000, how would you optimize it?

---

## 11. Code Quality and Engineering Practices

### 11.1 Design Patterns

**Theory question:** What are the common design patterns? How are they applied in projects?

**Practical question:** Which design patterns are used in the project? Give examples.

---

### 11.2 Exception Handling

**Theory question:** What are the differences between Checked Exceptions and Unchecked Exceptions? When should each be used?

**Practical question:** The exception-priority design in `GlobalExceptionHandler` is as follows:

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

**Follow-up questions:**

1. Why is `BusinessException` handled first?
2. What would happen if `BusinessException` were placed after `Exception`?

---

### 11.3 Testing Strategies

**Theory question:** What are the differences between unit testing, integration testing, and end-to-end testing? What are the standards for test coverage?

**Practical question:** `SmartSearchServiceTest` uses mocks to isolate dependencies:

```java
@ExtendWith(MockitoExtension.class)
class SmartSearchServiceTest {
    @Mock private EmbeddingService embeddingService;
    @Mock private VectorStoreService vectorStoreService;
    @Mock private CandidateMapper candidateMapper;
    // ...
}
```

**Follow-up questions:**

1. Why not start a real Milvus instance for integration testing?
2. If you needed to test the complete resume parsing workflow (file upload → AI parsing → candidate persistence), how would you design the test?

---

## 12. Scenario-Based and Open-Ended Questions

### 12.1 Resume Parsing Optimization

**Scenario question:** Users report that resume parsing accuracy is only 85%. How would you improve it?

**Follow-up questions:**

1. How would you optimize the AI model's prompt?
2. Is it necessary to integrate multiple AI models for cross-validation?
3. How would you collect user feedback data?

---

### 12.2 Extensibility Design

**Scenario question:** If the system needs to support multilingual resumes (English, Japanese, and Korean), what changes would be required?

