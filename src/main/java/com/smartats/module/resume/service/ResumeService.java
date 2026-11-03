package com.smartats.module.resume.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartats.common.constants.RedisKeyConstants;
import com.smartats.common.enums.ResumeStatus;
import com.smartats.common.exception.BusinessException;
import com.smartats.common.result.ResultCode;
import com.smartats.common.util.FileValidationUtil;
import com.smartats.infrastructure.mq.MessagePublisher;
import com.smartats.module.resume.dto.BatchUploadResponse;
import com.smartats.module.resume.dto.BatchUploadResponse.BatchUploadItem;
import com.smartats.module.resume.dto.ResumeParseMessage;
import com.smartats.module.resume.dto.ResumeUploadResponse;
import com.smartats.module.resume.dto.TaskStatusResponse;
import com.smartats.module.resume.entity.Resume;
import com.smartats.module.resume.mapper.ResumeMapper;
import com.smartats.infrastructure.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Resume service
 * <p>
 * Features:
 * 1. Resume upload (file storage + duplicate check)
 * 2. Task status query
 * 3. Asynchronous parsing (the MQ consumer is implemented in ResumeParseConsumer)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeService {

    private final ResumeMapper resumeMapper;
    private final FileStorageService fileStorageService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final MessagePublisher messagePublisher;

    private static final String RESUME_DEDUP_KEY_PREFIX = RedisKeyConstants.RESUME_DEDUP_KEY_PREFIX;
    private static final String TASK_STATUS_KEY_PREFIX = RedisKeyConstants.RESUME_TASK_KEY_PREFIX;
    private static final long TASK_STATUS_TTL = 24; // 24 hours

    /**
     * Upload a resume
     */
    @Transactional(rollbackFor = Exception.class)
    public ResumeUploadResponse uploadResume(MultipartFile file, Long userId) {
        // 1. Validate the file
        validateFile(file);

        // 2. Compute the MD5
        String fileHash;
        try {
            fileHash = DigestUtils.md5Hex(file.getInputStream());
        } catch (IOException e) {
            log.error("Failed to compute file MD5", e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "File processing failed");
        }

        // 3. Check for duplicates (Redis + DB)
        Resume existingResume = checkDuplicate(fileHash);
        if (existingResume != null) {
            log.info("File already exists: hash={}, userId={}", fileHash, userId);
            // taskId is null: a duplicate file needs no parsing task, and the client does not need to poll the status
            return new ResumeUploadResponse(null, existingResume.getId(), true, "File already exists, using the existing resume");
        }

        // 4. Generate the file path
        String objectName = generateObjectName(fileHash, file.getOriginalFilename());

        // 5. Upload the file to MinIO
        String fileUrl;
        try {
            fileUrl = fileStorageService.uploadFile(file, objectName);
        } catch (BusinessException e) {
            // Business exceptions are rethrown directly
            throw e;
        } catch (Exception e) {
            log.error("File upload failed: objectName={}", objectName, e);
            throw new BusinessException(ResultCode.FILE_UPLOAD_ERROR);
        }

        // 6. Save the database record
        Resume resume = new Resume();
        resume.setUserId(userId);
        // 🔒 Security: use the sanitized filename
        resume.setFileName(FileValidationUtil.sanitizeFilename(file.getOriginalFilename()));
        resume.setFilePath(objectName);
        resume.setFileUrl(fileUrl);
        resume.setFileSize(file.getSize());
        resume.setFileHash(fileHash);
        resume.setFileType(file.getContentType());
        resume.setStatus(ResumeStatus.PARSING.getCode());
        resume.setCreatedAt(LocalDateTime.now());
        resume.setUpdatedAt(LocalDateTime.now());

        resumeMapper.insert(resume);

        // 7. Write the dedup marker (Redis)
        String dedupKey = RESUME_DEDUP_KEY_PREFIX + fileHash;
        stringRedisTemplate.opsForValue().set(dedupKey, resume.getId().toString(), 7, TimeUnit.DAYS);

        // 8. Generate the task ID
        String taskId = UUID.randomUUID().toString();

        // 9. Write the task status (Redis)
        TaskStatusResponse taskStatus = new TaskStatusResponse();
        taskStatus.setStatus("QUEUED");
        taskStatus.setProgress(0);

        String taskKey = TASK_STATUS_KEY_PREFIX + taskId;
        try {
            String json = objectMapper.writeValueAsString(taskStatus);
            stringRedisTemplate.opsForValue().set(taskKey, json, TASK_STATUS_TTL, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("Failed to serialize task status: taskKey={}", taskKey, e);
        }

        // 10. Send the MQ message
        try {
            ResumeParseMessage message = new ResumeParseMessage(taskId, resume.getId(), userId, fileHash, 0);

            messagePublisher.sendResumeParseMessage(message);

            log.info("Parse message sent successfully: taskId={}, resumeId={}", taskId, resume.getId());

        } catch (Exception e) {
            log.error("Failed to send parse message: taskId={}", taskId, e);
            // On MQ send failure, update the task status to FAILED so users do not see QUEUED forever
            try {
                TaskStatusResponse failedStatus = new TaskStatusResponse();
                failedStatus.setStatus(ResumeStatus.FAILED.getCode());
                failedStatus.setProgress(0);
                failedStatus.setErrorMessage("Message queue send failed, please retry later");
                stringRedisTemplate.opsForValue().set(taskKey,
                        objectMapper.writeValueAsString(failedStatus), TASK_STATUS_TTL, TimeUnit.HOURS);
            } catch (Exception ex) {
                log.error("Exception while updating failed status: taskId={}", taskId, ex);
            }
        }

        log.info("Resume uploaded successfully: resumeId={}, taskId={}, hash={}", resume.getId(), taskId, fileHash);

        return new ResumeUploadResponse(taskId, resume.getId(), false, "Resume uploaded successfully, parsing in progress");
    }

    /**
     * Query task status
     */
    public TaskStatusResponse getTaskStatus(String taskId) {
        String taskKey = TASK_STATUS_KEY_PREFIX + taskId;

        log.debug("Querying task status: taskKey={}", taskKey);

        // 1. Check Redis first
        String json = stringRedisTemplate.opsForValue().get(taskKey);

        if (json != null) {
            try {
                TaskStatusResponse status = objectMapper.readValue(json, TaskStatusResponse.class);
                log.debug("Task status queried successfully: taskId={}, status={}", taskId, status.getStatus());
                return status;
            } catch (Exception e) {
                log.error("Failed to deserialize task status: taskKey={}, json={}", taskKey, json, e);
            }
        }

        // 2. Not in Redis, return the default status
        log.debug("Task status not found: taskId={}", taskId);
        return new TaskStatusResponse("NOT_FOUND", null, null, null, 0, null, null);
    }

    /**
     * Query resume detail by ID (only the user's own resumes)
     */
    public Resume getResumeById(Long id, Long userId) {
        Resume resume = resumeMapper.selectById(id);
        if (resume == null) {
            throw new BusinessException(ResultCode.RESUME_NOT_FOUND);
        }
        // Security check: users can only view their own resumes
        if (!resume.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "No permission to access this resume");
        }
        return resume;
    }

    /**
     * Paged query of the current user's resume list
     */
    public Page<Resume> listResumes(Long userId, int pageNum, int pageSize) {
        Page<Resume> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Resume> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Resume::getUserId, userId)
               .orderByDesc(Resume::getCreatedAt);
        return resumeMapper.selectPage(page, wrapper);
    }

    /**
     * Validate the file
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "File must not be empty");
        }

        // Validate the file size (10MB)
        long maxSize = 10 * 1024 * 1024;
        if (file.getSize() > maxSize) {
            throw new BusinessException(ResultCode.FILE_SIZE_EXCEEDED);
        }

        // Validate the file type (via Content-Type)
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equals("application/pdf")
                && !contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                && !contentType.equals("application/msword"))) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_SUPPORTED);
        }

        // 🔒 Security hardening: verify the real file type via the file header (magic bytes)
        // Use getBytes() instead of getInputStream() to avoid stream mark/reset support issues
        try {
            byte[] fileBytes = file.getBytes();
            boolean isValid = FileValidationUtil.validateFileType(
                    fileBytes,
                    contentType,
                    file.getOriginalFilename()
            );

            if (!isValid) {
                log.warn("File type validation failed: filename={}, contentType={}", file.getOriginalFilename(), contentType);
                throw new BusinessException(ResultCode.FILE_TYPE_NOT_SUPPORTED, "File content does not match the declared type");
            }
        } catch (IOException e) {
            log.error("Failed to read file content", e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "File validation failed");
        }
    }

    /**
     * Check whether the file already exists
     */
    private Resume checkDuplicate(String fileHash) {
        // 1. Check the Redis dedup marker first
        String dedupKey = RESUME_DEDUP_KEY_PREFIX + fileHash;
        String cachedResumeId = stringRedisTemplate.opsForValue().get(dedupKey);

        if (cachedResumeId != null) {
            Long resumeId = Long.valueOf(cachedResumeId);
            return resumeMapper.selectById(resumeId);
        }

        // 2. Not in Redis, check the database
        LambdaQueryWrapper<Resume> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Resume::getFileHash, fileHash);
        wrapper.last("LIMIT 1");

        Resume resume = resumeMapper.selectOne(wrapper);

        // 3. If it exists in the database, backfill Redis
        if (resume != null) {
            stringRedisTemplate.opsForValue().set(dedupKey, resume.getId().toString(), 7, TimeUnit.DAYS);
        }

        return resume;
    }

    /**
     * Generate the object name (file path)
     * Format: resumes/2026/02/19/{first 8 chars of md5}_{sanitized filename}
     * 🔒 Security: use the sanitized filename to prevent path traversal attacks
     */
    private String generateObjectName(String fileHash, String originalFilename) {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String prefix = fileHash.substring(0, 8);
        // 🔒 Sanitize the filename to prevent path traversal (e.g. ../../etc/passwd)
        String safeFilename = FileValidationUtil.sanitizeFilename(originalFilename);
        return String.format("resumes/%s/%s_%s", date, prefix, safeFilename);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Batch upload
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private static final int MAX_BATCH_SIZE = 20;
    private static final int MAX_BATCH_UPLOADS_PER_MINUTE = 5;

    /**
     * Batch upload resumes
     * <p>
     * Limits: at most 20 files, at most 5 batch uploads per minute
     * Each file is processed independently; a single failure does not affect the others
     */
    public BatchUploadResponse batchUploadResumes(MultipartFile[] files, Long userId) {
        if (files == null || files.length == 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "File must not be empty");
        }
        if (files.length > MAX_BATCH_SIZE) {
            throw new BusinessException(ResultCode.BATCH_UPLOAD_LIMIT_EXCEEDED);
        }

        // Rate limiting
        checkBatchUploadRateLimit(userId);

        List<BatchUploadItem> items = new ArrayList<>();
        int successCount = 0;
        int failedCount = 0;

        for (MultipartFile file : files) {
            String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unnamed";
            try {
                ResumeUploadResponse result = uploadResume(file, userId);
                if (Boolean.TRUE.equals(result.getDuplicated())) {
                    items.add(new BatchUploadItem(null, result.getResumeId(), fileName, "DUPLICATE", result.getMessage()));
                } else {
                    items.add(new BatchUploadItem(result.getTaskId(), result.getResumeId(), fileName, "QUEUED", result.getMessage()));
                }
                successCount++;
            } catch (BusinessException e) {
                log.warn("Single file failed during batch upload: fileName={}, error={}", fileName, e.getMessage());
                items.add(new BatchUploadItem(null, null, fileName, "FAILED", e.getMessage()));
                failedCount++;
            } catch (Exception e) {
                log.error("Unexpected error on single file during batch upload: fileName={}", fileName, e);
                items.add(new BatchUploadItem(null, null, fileName, "FAILED", "Processing failed"));
                failedCount++;
            }
        }

        log.info("Batch upload complete: userId={}, total={}, success={}, failed={}",
                userId, files.length, successCount, failedCount);

        return new BatchUploadResponse(files.length, successCount, failedCount, items);
    }

    /**
     * Batch upload rate limit: at most 5 times per minute
     */
    private void checkBatchUploadRateLimit(Long userId) {
        String rateLimitKey = RedisKeyConstants.UPLOAD_RATE_LIMIT_KEY_PREFIX + userId;
        String countStr = stringRedisTemplate.opsForValue().get(rateLimitKey);
        int count = countStr != null ? Integer.parseInt(countStr) : 0;
        if (count >= MAX_BATCH_UPLOADS_PER_MINUTE) {
            throw new BusinessException(ResultCode.UPLOAD_RATE_LIMITED);
        }
        stringRedisTemplate.opsForValue().increment(rateLimitKey);
        if (count == 0) {
            stringRedisTemplate.expire(rateLimitKey, 60, TimeUnit.SECONDS);
        }
    }
}
