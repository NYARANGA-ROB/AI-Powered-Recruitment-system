package com.smartaps.module.resume.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.smartaps.common.constants.RedisKeyConstants;
import com.smartaps.common.enums.ResumeStatus;
import com.smartaps.config.RabbitMQConfig;
import com.smartaps.module.resume.dto.ResumeParseMessage;
import com.smartaps.module.resume.dto.TaskStatusResponse;
import com.smartaps.module.resume.entity.Resume;
import com.smartaps.module.resume.mapper.ResumeMapper;
import com.smartaps.module.candidate.entity.Candidate;
import com.smartaps.module.candidate.service.CandidateService;
import com.smartaps.module.candidate.service.CandidateVectorService;
import com.smartaps.module.resume.dto.CandidateInfo;
import com.smartaps.module.resume.service.ResumeContentExtractor;
import com.smartaps.module.resume.service.ResumeParseService;
import com.smartaps.module.webhook.enums.WebhookEventType;
import com.smartaps.module.webhook.service.WebhookService;
import org.redisson.api.RLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.data.redis.core.StringRedisTemplate;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.MDC;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResumeParseConsumer {
    private final ResumeContentExtractor contentExtractor;
    private final ResumeParseService parseService;
    private final CandidateService candidateService;
    private final CandidateVectorService candidateVectorService;
    private final RedissonClient redissonClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final WebhookService webhookService;
    private final ResumeMapper resumeMapper;
    private final MeterRegistry meterRegistry;

    private static final String TASK_STATUS_KEY_PREFIX = RedisKeyConstants.RESUME_TASK_KEY_PREFIX;
    private static final String LOCK_KEY_PREFIX = RedisKeyConstants.RESUME_LOCK_KEY_PREFIX;

    /**
     * Consume resume parsing messages
     */
    @RabbitListener(queues = RabbitMQConfig.RESUME_PARSE_QUEUE)
    public void consumeResumeParse(
            ResumeParseMessage message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag
    ) throws IOException {
        String taskId = message.getTaskId();
        Long resumeId = message.getResumeId();
        String fileHash = message.getFileHash();

        log.info("Received resume parsing message: taskId={}, resumeId={}", taskId, resumeId);

        // 1. Idempotency check (Redis marker)
        String idempotentKey = "idempotent:resume:" + resumeId;
        Boolean alreadyProcessed = redisTemplate.opsForValue()
                .setIfAbsent(idempotentKey, "1", 1, java.util.concurrent.TimeUnit.HOURS);

        if (Boolean.FALSE.equals(alreadyProcessed)) {
            log.warn("Resume already processed, skipping: resumeId={}", resumeId);
            recordOutcome("duplicate");
            channel.basicAck(deliveryTag, false);
            return;
        }

        // 2. Acquire the distributed lock (prevents duplicate parsing)
        String lockKey = LOCK_KEY_PREFIX + fileHash;
        RLock lock = redissonClient.getLock(lockKey);

        MDC.put("taskId", taskId);
        try {
            // Try to acquire the lock, waiting at most 10 seconds, lock auto-release time 300 seconds
            boolean acquired = lock.tryLock(10, 300, java.util.concurrent.TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("Failed to acquire the lock, another instance may be processing: resumeId={}", resumeId);
                redisTemplate.delete(idempotentKey);
                recordOutcome("retrying");
                retryOrReject(channel, deliveryTag, message);
                return;
            }

            log.info("Lock acquired, starting processing: resumeId={}", resumeId);

            // 3. Update the task status to PROCESSING
            updateTaskStatus(taskId, "PROCESSING", 10);

            // 4. Query the resume information
            Resume resume = resumeMapper.selectById(resumeId);
            if (resume == null) {
                log.error("Resume not found: resumeId={}", resumeId);
                updateTaskStatus(taskId, "FAILED", 0, "Resume not found");
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 5. Extract the file content
            log.info("Start extracting file content: resumeId={}", resumeId);
            String content = contentExtractor.extractText(resume.getFilePath(), resume.getFileType());
            log.info("File content extraction complete: contentLength={}", content.length());
            updateTaskStatus(taskId, "PROCESSING", 30);

            // 6. AI parsing
            log.info("Start AI parsing: resumeId={}", resumeId);
            ResumeParseService.ParseResult parseResult = parseService.parseResumeWithRaw(content);
            CandidateInfo candidateInfo = parseResult.candidateInfo();
            String rawJson = parseResult.rawResponse();
            log.info("AI parsing completed for resumeId={}", resumeId);
            updateTaskStatus(taskId, "PROCESSING", 70);

            // 7. Save the candidate information
            log.info("Saving candidate information: resumeId={}", resumeId);
            Candidate candidate = candidateService.createCandidate(resumeId, candidateInfo, rawJson);
            log.info("Candidate information saved successfully: candidateId={}", candidate.getId());
            updateTaskStatus(taskId, "PROCESSING", 85);

            // 7.5 Vectorize the candidate (generate embeddings and store them in Milvus)
            log.info("Start vectorizing candidate: candidateId={}", candidate.getId());
            candidateVectorService.vectorizeCandidate(candidate);
            updateTaskStatus(taskId, "PROCESSING", 95);

            // 8. Update the task status to COMPLETED
            updateTaskStatus(taskId, "COMPLETED", 100);

            // 9. Update the resume status (clearing any lingering error information)
            resume.setStatus(ResumeStatus.COMPLETED.getCode());
            resume.setErrorMessage(null);
            resumeMapper.updateById(resume);
            recordOutcome("completed");

            log.info("Resume parsing complete: taskId={}, resumeId={}, candidateId={}", taskId, resumeId, candidate.getId());

            // 10. Trigger the Webhook event (passing the candidate information)
            triggerWebhookEvent(WebhookEventType.RESUME_PARSE_COMPLETED, resume, taskId, null, candidate.getId());

            // 11. Manually acknowledge the message
            channel.basicAck(deliveryTag, false);

        } catch (InterruptedException e) {
            int retryCount = message.getRetryCount() == null ? 0 : message.getRetryCount();
            redisTemplate.delete(idempotentKey);

            if (retryCount < 3) {
                recordOutcome("retrying");
                log.warn("Resume parsing interrupted, preparing retry attempt {}: taskId={}", retryCount + 1, taskId);
                updateRetryingStatus(taskId, retryCount + 1, 3,
                        "Parsing interrupted; retry scheduled");
            } else {
                log.error("Resume parsing interrupted after retries were exhausted: taskId={}", taskId);
                recordOutcome("failed");
                handleFailedTask(taskId, "Parsing interrupted (after 3 retries)");
                updateResumeStatusToFailed(resumeId, "Parsing interrupted");
            }

            retryOrReject(channel, deliveryTag, message);
            Thread.currentThread().interrupt();

        } catch (Exception e) {
            int retryCount = message.getRetryCount() == null ? 0 : message.getRetryCount();
            redisTemplate.delete(idempotentKey);

            if (retryCount < 3) {
                // Retries remain → mark RETRYING, do not mark as finally failed
                long nextDelay = getRetryDelay(retryCount);
                log.warn("Resume parsing failed, preparing retry attempt {} (in {} seconds): taskId={}, errorType={}",
                        retryCount + 1, nextDelay / 1000, taskId, e.getClass().getSimpleName());
                recordOutcome("retrying");
                updateRetryingStatus(taskId, retryCount + 1, 3,
                        "Resume parsing failed; retry scheduled");
                // Keep the DB in PARSING status, do not update to FAILED; do not trigger the failure Webhook
            } else {
                // Retries exhausted → final failure
                log.error("Resume parsing failed after retries were exhausted: taskId={}, errorType={}",
                        taskId, e.getClass().getSimpleName());
                recordOutcome("failed");
                handleFailedTask(taskId, "Resume parsing failed after retries");

                Resume resume = resumeMapper.selectById(resumeId);
                if (resume != null) {
                    resume.setStatus(ResumeStatus.FAILED.getCode());
                    resume.setErrorMessage("Resume parsing failed after retries");
                    resumeMapper.updateById(resume);
                    log.info("Updated resume database status to FAILED: resumeId={}", resumeId);
                    triggerWebhookEvent(WebhookEventType.RESUME_PARSE_FAILED, resume, taskId, e.getMessage(), null);
                }
            }

            retryOrReject(channel, deliveryTag, message);

        } finally {
            try {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                    log.info("Resume parsing lock released: resumeId={}", resumeId);
                }
            } finally {
                MDC.remove("taskId");
            }
        }
    }

    /**
     * Trigger a Webhook event
     */
    private void triggerWebhookEvent(WebhookEventType eventType, Resume resume, String taskId, String errorMessage, Long candidateId) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("taskId", taskId);
            data.put("resumeId", resume.getId());
            data.put("fileName", resume.getFileName());
            data.put("userId", resume.getUserId());
            data.put("fileSize", resume.getFileSize());
            data.put("fileType", resume.getFileType());
            data.put("status", resume.getStatus());

            if (candidateId != null) {
                data.put("candidateId", candidateId);
            }

            if (errorMessage != null) {
                data.put("errorMessage", errorMessage);
            }

            webhookService.sendEvent(eventType, data);
        } catch (Exception e) {
            log.error("Failed to trigger Webhook event: event={}, resumeId={}", eventType.getCode(), resume.getId(), e);
        }
    }

    /**
     * Update the task status
     */
    private void updateTaskStatus(String taskId, String status, int progress) throws Exception {
        updateTaskStatus(taskId, status, progress, null);
    }

    private void updateTaskStatus(String taskId, String status, int progress, String errorMessage) throws Exception {
        String taskKey = TASK_STATUS_KEY_PREFIX + taskId;

        TaskStatusResponse taskStatus = new TaskStatusResponse();
        taskStatus.setStatus(status);
        taskStatus.setProgress(progress);
        taskStatus.setErrorMessage(errorMessage);

        // Manually serialize to a JSON string
        String json = objectMapper.writeValueAsString(taskStatus);
        redisTemplate.opsForValue().set(taskKey, json, 24, java.util.concurrent.TimeUnit.HOURS);

        log.info("Task status updated: taskId={}, status={}, progress={}", taskId, status, progress);
    }

    /**
     * Handle a failed task
     */
    private void handleFailedTask(String taskId, String errorMessage) {
        try {
            updateTaskStatus(taskId, "FAILED", 0, errorMessage);
        } catch (Exception e) {
            log.error("Exception updating failed task status: taskId={}", taskId, e);
        }
    }

    /**
     * Update the task status to RETRYING (with retry information)
     */
    private void updateRetryingStatus(String taskId, int retryCount, int maxRetries, String errorMessage) {
        try {
            String taskKey = TASK_STATUS_KEY_PREFIX + taskId;
            TaskStatusResponse taskStatus = new TaskStatusResponse();
            taskStatus.setStatus("RETRYING");
            taskStatus.setProgress(0);
            taskStatus.setErrorMessage(errorMessage);
            taskStatus.setRetryCount(retryCount);
            taskStatus.setMaxRetries(maxRetries);

            String json = objectMapper.writeValueAsString(taskStatus);
            redisTemplate.opsForValue().set(taskKey, json, 24, java.util.concurrent.TimeUnit.HOURS);
            log.info("Task status updated: taskId={}, status=RETRYING, retry={}/{}", taskId, retryCount, maxRetries);
        } catch (Exception e) {
            log.error("Failed to update RETRYING status: taskId={}", taskId, e);
        }
    }

    /**
     * Update the resume database status to FAILED (for interruption scenarios where the resume object cannot be obtained)
     */
    private void updateResumeStatusToFailed(Long resumeId, String errorMessage) {
        try {
            Resume resume = resumeMapper.selectById(resumeId);
            if (resume != null) {
                resume.setStatus(ResumeStatus.FAILED.getCode());
                resume.setErrorMessage(errorMessage);
                resumeMapper.updateById(resume);
                log.info("Updated resume database status to FAILED: resumeId={}", resumeId);
            }
        } catch (Exception e) {
            log.error("Failed to update resume database status: resumeId={}", resumeId, e);
        }
    }

    /**
     * Compute the exponential backoff delay in milliseconds
     * retry 1: 10 seconds, retry 2: 30 seconds, retry 3: 60 seconds
     */
    private long getRetryDelay(int retryCount) {
        return switch (retryCount) {
            case 0 -> 10_000L;   // 1st retry: 10 seconds
            case 1 -> 30_000L;   // 2nd retry: 30 seconds
            case 2 -> 60_000L;   // 3rd retry: 60 seconds
            default -> 60_000L;
        };
    }

    /**
     * Retry or reject the message (uses the delay queue to implement exponential backoff)
     */
    private void retryOrReject(Channel channel, long deliveryTag, ResumeParseMessage message) throws IOException {
        int retryCount = message.getRetryCount() == null ? 0 : message.getRetryCount();

        if (retryCount < 3) {
            // Delete the idempotency marker so the retried message is not skipped
            String idempotentKey = "idempotent:resume:" + message.getResumeId();
            redisTemplate.delete(idempotentKey);

            long delay = getRetryDelay(retryCount);
            log.info("Message retry: retryCount={}, sending retry attempt {}, delay {} seconds", retryCount, retryCount + 1, delay / 1000);
            message.setRetryCount(retryCount + 1);
            try {
                // Send to the delay queue, setting a per-message TTL to implement exponential backoff
                // Once the message expires it is automatically routed back to the main queue resume.parse.queue via the DLX
                String json = objectMapper.writeValueAsString(message);
                channel.basicPublish(
                        RabbitMQConfig.RESUME_EXCHANGE,
                        RabbitMQConfig.RESUME_PARSE_DELAY_ROUTING_KEY,
                        new com.rabbitmq.client.AMQP.BasicProperties.Builder()
                                .contentType("application/json")
                                .expiration(String.valueOf(delay))  // per-message TTL (milliseconds)
                                .build(),
                        json.getBytes(java.nio.charset.StandardCharsets.UTF_8)
                );
                channel.waitForConfirmsOrDie(5_000L);
            } catch (Exception e) {
                log.error("Failed to publish retry message; requeueing original: retryCount={}, errorType={}",
                        retryCount, e.getClass().getSimpleName());
                channel.basicNack(deliveryTag, false, true);
                return;
            }
            // ACK the original message to avoid duplicate consumption
            channel.basicAck(deliveryTag, false);
        } else {
            // Reject, entering the dead-letter queue
            log.error("Message retry limit exceeded, entering the dead-letter queue: retryCount={}", retryCount);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    private void recordOutcome(String outcome) {
        meterRegistry.counter("smartaps.resume.parse.outcomes", "outcome", outcome).increment();
    }
}