package com.smartats.module.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartats.common.constants.RedisKeyConstants;
import com.smartats.common.enums.ApplicationStatus;
import com.smartats.common.enums.JobStatus;
import com.smartats.common.exception.BusinessException;
import com.smartats.common.result.ResultCode;
import com.smartats.module.application.dto.*;
import com.smartats.module.application.entity.JobApplication;
import com.smartats.module.application.mapper.JobApplicationMapper;
import com.smartats.module.candidate.entity.Candidate;
import com.smartats.module.candidate.mapper.CandidateMapper;
import com.smartats.module.job.entity.Job;
import com.smartats.module.job.mapper.JobMapper;
import com.smartats.module.analytics.event.AnalyticsUpdateEvent;
import com.smartats.module.webhook.enums.WebhookEventType;
import com.smartats.module.webhook.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Job application service
 * <p>
 * Core business logic:
 * <ul>
 *   <li>Creating applications (duplicate prevention, job/candidate validation)</li>
 *   <li>Status transitions (legality checks + Webhook notifications)</li>
 *   <li>Multi-dimensional queries (by job, by candidate, paged filtering)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobApplicationService {

    private final JobApplicationMapper jobApplicationMapper;
    private final JobMapper jobMapper;
    private final CandidateMapper candidateMapper;
    private final WebhookService webhookService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MatchScoreService matchScoreService;
    private final ApplicationEventPublisher eventPublisher;

    /** Cache TTL (minutes) */
    private static final long CACHE_TTL_MINUTES = 30;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Create application
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * Create a job application
     *
     * @param request creation request
     * @return ID of the newly created application
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createApplication(CreateApplicationRequest request) {
        Long jobId = request.getJobId();
        Long candidateId = request.getCandidateId();

        log.info("Creating job application: jobId={}, candidateId={}", jobId, candidateId);

        // ① Validate that the job exists and is published
        Job job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Position not found");
        }
        if (!JobStatus.PUBLISHED.getCode().equals(job.getStatus())) {
            throw new BusinessException(ResultCode.APPLICATION_JOB_NOT_PUBLISHED);
        }

        // ② Validate that the candidate exists
        Candidate candidate = candidateMapper.selectById(candidateId);
        if (candidate == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Candidate not found");
        }

        // ③ Duplicate prevention: the same candidate cannot apply to the same job twice
        LambdaQueryWrapper<JobApplication> dupCheck = new LambdaQueryWrapper<>();
        dupCheck.eq(JobApplication::getJobId, jobId)
                .eq(JobApplication::getCandidateId, candidateId);
        if (jobApplicationMapper.selectCount(dupCheck) > 0) {
            throw new BusinessException(ResultCode.APPLICATION_DUPLICATE);
        }

        // ④ Create the application record
        JobApplication application = new JobApplication();
        application.setJobId(jobId);
        application.setCandidateId(candidateId);
        application.setStatus(ApplicationStatus.PENDING.getCode());
        application.setHrNotes(request.getHrNotes());
        application.setAppliedAt(LocalDateTime.now());
        application.setUpdatedAt(LocalDateTime.now());

        jobApplicationMapper.insert(application);

        log.info("Job application created successfully: id={}, jobId={}, candidateId={}", application.getId(), jobId, candidateId);

        // ⑤ Trigger the Webhook event
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("applicationId", application.getId());
        eventData.put("jobId", jobId);
        eventData.put("jobTitle", job.getTitle());
        eventData.put("candidateId", candidateId);
        eventData.put("candidateName", candidate.getName());
        eventData.put("status", ApplicationStatus.PENDING.getCode());
        webhookService.sendEvent(WebhookEventType.APPLICATION_SUBMITTED, eventData);

        // ⑥ Asynchronously calculate the AI match score (does not block the creation flow)
        matchScoreService.calculateAndSaveAsync(application.getId());

        // ⑦ Publish the analytics update event (SSE push)
        eventPublisher.publishEvent(new AnalyticsUpdateEvent(this,
                "APPLICATION_CREATED",
                String.format("New application: %s applied for %s", candidate.getName(), job.getTitle())));

        return application.getId();
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Update status
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * Update the application status
     *
     * @param id      application ID
     * @param request status update request
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, UpdateApplicationStatusRequest request) {
        log.info("Updating application status: id={}, targetStatus={}", id, request.getStatus());

        JobApplication application = jobApplicationMapper.selectById(id);
        if (application == null) {
            throw new BusinessException(ResultCode.APPLICATION_NOT_FOUND);
        }

        String currentStatus = application.getStatus();
        String targetStatus = request.getStatus();

        // Validate the legality of the status transition (delegated to the enum state machine)
        ApplicationStatus currentEnum = ApplicationStatus.fromCode(currentStatus);
        ApplicationStatus targetEnum = ApplicationStatus.fromCode(targetStatus);

        if (currentEnum == null || targetEnum == null || !currentEnum.canTransitionTo(targetEnum)) {
            throw new BusinessException(ResultCode.APPLICATION_STATUS_INVALID,
                    String.format("Transition from [%s] to [%s] is not allowed, allowed target statuses: %s",
                            currentStatus, targetStatus,
                            currentEnum != null ? currentEnum.getAllowedTargets() : "[]"));
        }

        // Update the status
        application.setStatus(targetStatus);
        application.setUpdatedAt(LocalDateTime.now());
        if (StringUtils.hasText(request.getHrNotes())) {
            application.setHrNotes(request.getHrNotes());
        }

        jobApplicationMapper.updateById(application);
        evictCache(id);

        log.info("Application status updated successfully: id={}, {} → {}", id, currentStatus, targetStatus);

        // Trigger the Webhook event
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("applicationId", id);
        eventData.put("jobId", application.getJobId());
        eventData.put("candidateId", application.getCandidateId());
        eventData.put("previousStatus", currentStatus);
        eventData.put("currentStatus", targetStatus);
        eventData.put("hrNotes", application.getHrNotes());
        webhookService.sendEvent(WebhookEventType.APPLICATION_STATUS_CHANGED, eventData);

        // Publish the analytics update event (SSE push)
        eventPublisher.publishEvent(new AnalyticsUpdateEvent(this,
                "STATUS_CHANGED",
                String.format("Application #%d status changed: %s → %s", id, currentStatus, targetStatus)));
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Queries
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * Get application detail (Redis cache)
     */
    public ApplicationResponse getById(Long id) {
        log.info("Querying application detail: id={}", id);

        String cacheKey = RedisKeyConstants.CACHE_APPLICATION_KEY_PREFIX + id;

        // 1. Check the cache
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, ApplicationResponse.class);
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize application cache, falling back to database: id={}", id, e);
            }
        }

        // 2. Query the database
        JobApplication application = jobApplicationMapper.selectById(id);
        if (application == null) {
            throw new BusinessException(ResultCode.APPLICATION_NOT_FOUND);
        }

        ApplicationResponse response = convertToResponse(application);

        // 3. Backfill the cache
        try {
            redisTemplate.opsForValue().set(
                    cacheKey,
                    objectMapper.writeValueAsString(response),
                    CACHE_TTL_MINUTES,
                    TimeUnit.MINUTES
            );
        } catch (JsonProcessingException e) {
            log.warn("Failed to write application to cache (no business impact): id={}", id, e);
        }

        return response;
    }

    /**
     * List applications by job ID (HR view)
     */
    public Page<ApplicationResponse> listByJobId(Long jobId, Integer pageNum, Integer pageSize) {
        log.info("Querying application list for position: jobId={}, page={}", jobId, pageNum);

        Page<JobApplication> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<JobApplication> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(JobApplication::getJobId, jobId)
                .orderByDesc(JobApplication::getAppliedAt);

        Page<JobApplication> resultPage = jobApplicationMapper.selectPage(page, wrapper);
        return convertPage(resultPage);
    }

    /**
     * List applications by candidate ID
     */
    public Page<ApplicationResponse> listByCandidateId(Long candidateId, Integer pageNum, Integer pageSize) {
        log.info("Querying application list for candidate: candidateId={}, page={}", candidateId, pageNum);

        Page<JobApplication> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<JobApplication> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(JobApplication::getCandidateId, candidateId)
                .orderByDesc(JobApplication::getAppliedAt);

        Page<JobApplication> resultPage = jobApplicationMapper.selectPage(page, wrapper);
        return convertPage(resultPage);
    }

    /**
     * Comprehensive query (supports multi-dimensional filtering + sorting + pagination)
     */
    public Page<ApplicationResponse> listApplications(ApplicationQueryRequest request) {
        log.info("Comprehensive application list query: {}", request);

        int pageNum = Math.max(1, request.getPageNum());
        int pageSize = Math.min(100, Math.max(1, request.getPageSize()));

        Page<JobApplication> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<JobApplication> wrapper = new LambdaQueryWrapper<>();

        // Filter conditions
        if (request.getJobId() != null) {
            wrapper.eq(JobApplication::getJobId, request.getJobId());
        }
        if (request.getCandidateId() != null) {
            wrapper.eq(JobApplication::getCandidateId, request.getCandidateId());
        }
        if (StringUtils.hasText(request.getStatus())) {
            wrapper.eq(JobApplication::getStatus, request.getStatus());
        }

        // Sorting
        boolean asc = "asc".equalsIgnoreCase(request.getOrderDirection());
        switch (request.getOrderBy() != null ? request.getOrderBy() : "applied_at") {
            case "match_score" -> {
                if (asc) wrapper.orderByAsc(JobApplication::getMatchScore);
                else wrapper.orderByDesc(JobApplication::getMatchScore);
            }
            case "updated_at" -> {
                if (asc) wrapper.orderByAsc(JobApplication::getUpdatedAt);
                else wrapper.orderByDesc(JobApplication::getUpdatedAt);
            }
            default -> {
                if (asc) wrapper.orderByAsc(JobApplication::getAppliedAt);
                else wrapper.orderByDesc(JobApplication::getAppliedAt);
            }
        }

        Page<JobApplication> resultPage = jobApplicationMapper.selectPage(page, wrapper);
        return convertPage(resultPage);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Private methods
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * Convert the entity to the response DTO, joining the job title and candidate name (used for single-record queries only)
     */
    private ApplicationResponse convertToResponse(JobApplication application) {
        ApplicationResponse response = buildBaseResponse(application);

        // Join the job title
        Job job = jobMapper.selectById(application.getJobId());
        if (job != null) {
            response.setJobTitle(job.getTitle());
        }

        // Join the candidate name
        Candidate candidate = candidateMapper.selectById(application.getCandidateId());
        if (candidate != null) {
            response.setCandidateName(candidate.getName());
        }

        return response;
    }

    /**
     * Build the base response object (without joins, reused for single and batch conversion)
     */
    private ApplicationResponse buildBaseResponse(JobApplication application) {
        ApplicationResponse response = new ApplicationResponse();
        response.setId(application.getId());
        response.setJobId(application.getJobId());
        response.setCandidateId(application.getCandidateId());
        response.setMatchScore(application.getMatchScore());
        response.setMatchCalculatedAt(application.getMatchCalculatedAt());
        response.setStatus(application.getStatus());
        response.setStatusDesc(getStatusDesc(application.getStatus()));
        response.setHrNotes(application.getHrNotes());
        response.setAppliedAt(application.getAppliedAt());
        response.setUpdatedAt(application.getUpdatedAt());

        // JSON-deserialize matchReasons
        if (StringUtils.hasText(application.getMatchReasons())) {
            try {
                List<String> reasons = objectMapper.readValue(
                        application.getMatchReasons(), new TypeReference<>() {});
                response.setMatchReasons(reasons);
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize matchReasons: id={}", application.getId(), e);
            }
        }

        return response;
    }

    /**
     * Paged result conversion (batch-loads related data to avoid N+1)
     */
    private Page<ApplicationResponse> convertPage(Page<JobApplication> sourcePage) {
        List<JobApplication> records = sourcePage.getRecords();
        if (records.isEmpty()) {
            Page<ApplicationResponse> responsePage = new Page<>(
                    sourcePage.getCurrent(), sourcePage.getSize(), sourcePage.getTotal());
            responsePage.setRecords(List.of());
            return responsePage;
        }

        // Collect all jobIds and candidateIds in one pass
        Set<Long> jobIds = records.stream().map(JobApplication::getJobId).collect(Collectors.toSet());
        Set<Long> candidateIds = records.stream().map(JobApplication::getCandidateId).collect(Collectors.toSet());

        // Query all related jobs and candidates at once (avoids N+1)
        Map<Long, Job> jobMap = jobMapper.selectBatchIds(jobIds).stream()
                .collect(Collectors.toMap(Job::getId, Function.identity()));
        Map<Long, Candidate> candidateMap = candidateMapper.selectBatchIds(candidateIds).stream()
                .collect(Collectors.toMap(Candidate::getId, Function.identity()));

        // Convert using the batch-loaded data
        List<ApplicationResponse> responseList = records.stream()
                .map(app -> convertToResponseWithMaps(app, jobMap, candidateMap))
                .toList();

        Page<ApplicationResponse> responsePage = new Page<>(
                sourcePage.getCurrent(), sourcePage.getSize(), sourcePage.getTotal());
        responsePage.setRecords(responseList);
        return responsePage;
    }

    /**
     * Convert the entity to the response DTO using pre-loaded Maps to avoid extra queries
     */
    private ApplicationResponse convertToResponseWithMaps(
            JobApplication application, Map<Long, Job> jobMap, Map<Long, Candidate> candidateMap) {
        ApplicationResponse response = buildBaseResponse(application);

        Job job = jobMap.get(application.getJobId());
        if (job != null) {
            response.setJobTitle(job.getTitle());
        }

        Candidate candidate = candidateMap.get(application.getCandidateId());
        if (candidate != null) {
            response.setCandidateName(candidate.getName());
        }

        return response;
    }

    /**
     * Status description (delegated to the enum)
     */
    private String getStatusDesc(String status) {
        if (status == null) return "";
        return ApplicationStatus.getDescriptionByCode(status);
    }

    /**
     * Evict the application detail cache
     */
    private void evictCache(Long id) {
        redisTemplate.delete(RedisKeyConstants.CACHE_APPLICATION_KEY_PREFIX + id);
        log.debug("Application cache invalidated: id={}", id);
    }
}
