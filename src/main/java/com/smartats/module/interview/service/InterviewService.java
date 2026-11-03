package com.smartats.module.interview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartats.common.constants.RedisKeyConstants;
import com.smartats.common.exception.BusinessException;
import com.smartats.common.result.ResultCode;
import com.smartats.module.application.entity.JobApplication;
import com.smartats.module.application.mapper.JobApplicationMapper;
import com.smartats.module.auth.entity.User;
import com.smartats.module.auth.mapper.UserMapper;
import com.smartats.module.interview.dto.InterviewResponse;
import com.smartats.module.interview.dto.ScheduleInterviewRequest;
import com.smartats.module.interview.dto.SubmitFeedbackRequest;
import com.smartats.module.interview.entity.InterviewRecord;
import com.smartats.module.interview.mapper.InterviewRecordMapper;
import com.smartats.common.enums.ApplicationStatus;
import com.smartats.common.enums.InterviewStatus;
import com.smartats.common.enums.InterviewType;
import com.smartats.common.enums.Recommendation;
import com.smartats.module.webhook.enums.WebhookEventType;
import com.smartats.module.webhook.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Interview record service
 * <p>
 * Core business logic:
 * <ul>
 *   <li>Scheduling interviews (automatic round increment, application validation, time conflict detection)</li>
 *   <li>Submitting feedback (score + comments + recommendation)</li>
 *   <li>Cancelling interviews</li>
 *   <li>Querying all interview rounds by application</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewService {

    private final InterviewRecordMapper interviewRecordMapper;
    private final JobApplicationMapper jobApplicationMapper;
    private final UserMapper userMapper;
    private final WebhookService webhookService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /** Cache TTL (minutes) */
    private static final long CACHE_TTL_MINUTES = 30;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Schedule interview
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * Schedule an interview
     *
     * @param request interview scheduling request
     * @return ID of the newly created interview record
     */
    @Transactional(rollbackFor = Exception.class)
    public Long scheduleInterview(ScheduleInterviewRequest request) {
        Long applicationId = request.getApplicationId();
        log.info("Scheduling interview: applicationId={}, interviewerId={}, type={}",
                applicationId, request.getInterviewerId(), request.getInterviewType());

        // (1) Validate that the application exists and is in the interview stage
        JobApplication application = jobApplicationMapper.selectById(applicationId);
        if (application == null) {
            throw new BusinessException(ResultCode.APPLICATION_NOT_FOUND);
        }
        // Only applications in SCREENING or INTERVIEW status may have interviews scheduled
        if (!ApplicationStatus.SCREENING.getCode().equals(application.getStatus())
                && !ApplicationStatus.INTERVIEW.getCode().equals(application.getStatus())) {
            throw new BusinessException(ResultCode.APPLICATION_STATUS_INVALID,
                    "Current application status is [" + application.getStatus() + "]; scheduling an interview is not allowed");
        }

        // (2) Validate that the interviewer exists
        User interviewer = userMapper.selectById(request.getInterviewerId());
        if (interviewer == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND, "Interviewer not found");
        }

        // (3) Automatically compute the interview round (unless manually specified)
        Integer round = request.getRound();
        if (round == null) {
            LambdaQueryWrapper<InterviewRecord> countWrapper = new LambdaQueryWrapper<>();
            countWrapper.eq(InterviewRecord::getApplicationId, applicationId);
            long existingRounds = interviewRecordMapper.selectCount(countWrapper);
            round = (int) existingRounds + 1;
        }

        // (4) Interviewer time conflict detection (the same interviewer cannot have another interview within +/- the interview duration)
        int duration = request.getDurationMinutes() != null ? request.getDurationMinutes() : 60;
        LocalDateTime startTime = request.getScheduledAt();
        LocalDateTime endTime = startTime.plusMinutes(duration);

        LambdaQueryWrapper<InterviewRecord> conflictWrapper = new LambdaQueryWrapper<>();
        conflictWrapper.eq(InterviewRecord::getInterviewerId, request.getInterviewerId())
                .eq(InterviewRecord::getStatus, InterviewStatus.SCHEDULED.getCode())
                .and(w -> w
                        // Existing interview starts within the new interview window
                        .between(InterviewRecord::getScheduledAt, startTime, endTime)
                        // Or an existing interview overlaps the new interview start time
                        .or()
                        .le(InterviewRecord::getScheduledAt, startTime)
                        .apply("DATE_ADD(scheduled_at, INTERVAL duration_minutes MINUTE) > {0}", startTime)
                );

        if (interviewRecordMapper.selectCount(conflictWrapper) > 0) {
            throw new BusinessException(ResultCode.INTERVIEW_TIME_CONFLICT);
        }

        // (5) Create the interview record
        InterviewRecord record = new InterviewRecord();
        record.setApplicationId(applicationId);
        record.setInterviewerId(request.getInterviewerId());
        record.setRound(round);
        record.setInterviewType(request.getInterviewType());
        record.setScheduledAt(request.getScheduledAt());
        record.setDurationMinutes(duration);
        record.setStatus(InterviewStatus.SCHEDULED.getCode());
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());

        interviewRecordMapper.insert(record);

        // (6) If the application status is SCREENING, automatically advance it to INTERVIEW
        if (ApplicationStatus.SCREENING.getCode().equals(application.getStatus())) {
            application.setStatus(ApplicationStatus.INTERVIEW.getCode());
            application.setUpdatedAt(LocalDateTime.now());
            jobApplicationMapper.updateById(application);
            log.info("Application status automatically advanced to INTERVIEW: applicationId={}", applicationId);
        }

        log.info("Interview scheduled successfully: id={}, applicationId={}, round={}", record.getId(), applicationId, round);

        // (7) Trigger the Webhook event
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("interviewId", record.getId());
        eventData.put("applicationId", applicationId);
        eventData.put("interviewerId", request.getInterviewerId());
        eventData.put("interviewerName", interviewer.getUsername());
        eventData.put("round", round);
        eventData.put("interviewType", request.getInterviewType());
        eventData.put("scheduledAt", request.getScheduledAt().toString());
        webhookService.sendEvent(WebhookEventType.INTERVIEW_SCHEDULED, eventData);

        return record.getId();
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Submit feedback
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * Submit interview feedback
     *
     * @param id      interview record ID
     * @param request feedback content
     */
    @Transactional(rollbackFor = Exception.class)
    public void submitFeedback(Long id, SubmitFeedbackRequest request) {
        log.info("Submitting interview feedback: id={}, score={}, recommendation={}", id, request.getScore(), request.getRecommendation());

        InterviewRecord record = interviewRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ResultCode.INTERVIEW_NOT_FOUND);
        }

        if (InterviewStatus.CANCELLED.getCode().equals(record.getStatus())) {
            throw new BusinessException(ResultCode.INTERVIEW_ALREADY_CANCELLED);
        }

        // Fill in the feedback and mark the interview as completed
        record.setScore(request.getScore());
        record.setFeedback(request.getFeedback());
        record.setRecommendation(request.getRecommendation());
        record.setStatus(InterviewStatus.COMPLETED.getCode());
        record.setUpdatedAt(LocalDateTime.now());

        interviewRecordMapper.updateById(record);
        evictCache(id);

        log.info("Interview feedback submitted successfully: id={}", id);

        // Trigger the Webhook event
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("interviewId", id);
        eventData.put("applicationId", record.getApplicationId());
        eventData.put("round", record.getRound());
        eventData.put("score", request.getScore());
        eventData.put("recommendation", request.getRecommendation());
        webhookService.sendEvent(WebhookEventType.INTERVIEW_COMPLETED, eventData);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Cancel interview
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * Cancel an interview
     *
     * @param id interview record ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelInterview(Long id) {
        log.info("Cancelling interview: id={}", id);

        InterviewRecord record = interviewRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ResultCode.INTERVIEW_NOT_FOUND);
        }

        if (InterviewStatus.COMPLETED.getCode().equals(record.getStatus())) {
            throw new BusinessException(ResultCode.INTERVIEW_ALREADY_COMPLETED);
        }

        if (InterviewStatus.CANCELLED.getCode().equals(record.getStatus())) {
            throw new BusinessException(ResultCode.INTERVIEW_ALREADY_CANCELLED);
        }

        record.setStatus(InterviewStatus.CANCELLED.getCode());
        record.setUpdatedAt(LocalDateTime.now());
        interviewRecordMapper.updateById(record);
        evictCache(id);

        log.info("Interview cancelled: id={}", id);

        // Trigger the Webhook event
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("interviewId", id);
        eventData.put("applicationId", record.getApplicationId());
        eventData.put("round", record.getRound());
        webhookService.sendEvent(WebhookEventType.INTERVIEW_CANCELLED, eventData);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Queries
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * Get interview record details (Redis cached)
     */
    public InterviewResponse getById(Long id) {
        log.info("Querying interview details: id={}", id);

        String cacheKey = RedisKeyConstants.CACHE_INTERVIEW_KEY_PREFIX + id;

        // 1. Check the cache
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, InterviewResponse.class);
            } catch (JsonProcessingException e) {
                log.warn("Interview cache deserialization failed, falling back to the database: id={}", id, e);
            }
        }

        // 2. Query the database
        InterviewRecord record = interviewRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ResultCode.INTERVIEW_NOT_FOUND);
        }

        InterviewResponse response = convertToResponse(record);

        // 3. Repopulate the cache
        try {
            redisTemplate.opsForValue().set(
                    cacheKey,
                    objectMapper.writeValueAsString(response),
                    CACHE_TTL_MINUTES,
                    TimeUnit.MINUTES
            );
        } catch (JsonProcessingException e) {
            log.warn("Failed to write interview to cache (no business impact): id={}", id, e);
        }

        return response;
    }

    /**
     * Query all interview rounds by application ID (batch-fetch interviewers to avoid N+1)
     */
    public List<InterviewResponse> listByApplicationId(Long applicationId) {
        log.info("Querying interview records for application: applicationId={}", applicationId);

        LambdaQueryWrapper<InterviewRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterviewRecord::getApplicationId, applicationId)
                .orderByAsc(InterviewRecord::getRound);

        List<InterviewRecord> records = interviewRecordMapper.selectList(wrapper);
        if (records.isEmpty()) {
            return List.of();
        }

        // Batch-fetch all interviewers (avoid N+1)
        Set<Long> interviewerIds = records.stream()
                .map(InterviewRecord::getInterviewerId)
                .collect(Collectors.toSet());
        Map<Long, User> interviewerMap = userMapper.selectBatchIds(interviewerIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return records.stream()
                .map(record -> convertToResponseWithMap(record, interviewerMap))
                .toList();
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Private helpers
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * Convert the entity to a response DTO (single-record query; looks up the interviewer relationally)
     */
    private InterviewResponse convertToResponse(InterviewRecord record) {
        InterviewResponse response = buildBaseResponse(record);

        // Look up the interviewer name relationally
        User interviewer = userMapper.selectById(record.getInterviewerId());
        if (interviewer != null) {
            response.setInterviewerName(interviewer.getUsername());
        }

        return response;
    }

    /**
     * Convert the entity to a response DTO (using a preloaded interviewer map; for batch queries)
     */
    private InterviewResponse convertToResponseWithMap(InterviewRecord record, Map<Long, User> interviewerMap) {
        InterviewResponse response = buildBaseResponse(record);

        User interviewer = interviewerMap.get(record.getInterviewerId());
        if (interviewer != null) {
            response.setInterviewerName(interviewer.getUsername());
        }

        return response;
    }

    /**
     * Build the base response object (no relational lookups; shared by single and batch conversion)
     */
    private InterviewResponse buildBaseResponse(InterviewRecord record) {
        InterviewResponse response = new InterviewResponse();
        response.setId(record.getId());
        response.setApplicationId(record.getApplicationId());
        response.setInterviewerId(record.getInterviewerId());
        response.setRound(record.getRound());
        response.setInterviewType(record.getInterviewType());
        response.setInterviewTypeDesc(getInterviewTypeDesc(record.getInterviewType()));
        response.setScheduledAt(record.getScheduledAt());
        response.setDurationMinutes(record.getDurationMinutes());
        response.setStatus(record.getStatus());
        response.setStatusDesc(getStatusDesc(record.getStatus()));
        response.setFeedback(record.getFeedback());
        response.setScore(record.getScore());
        response.setRecommendation(record.getRecommendation());
        response.setRecommendationDesc(getRecommendationDesc(record.getRecommendation()));
        response.setCreatedAt(record.getCreatedAt());
        response.setUpdatedAt(record.getUpdatedAt());
        return response;
    }

    /**
     * Human-readable interview type description (delegated to the enum)
     */
    private String getInterviewTypeDesc(String type) {
        if (type == null) return "";
        return InterviewType.getDescriptionByCode(type);
    }

    /**
     * Human-readable interview status description (delegated to the enum)
     */
    private String getStatusDesc(String status) {
        if (status == null) return "";
        return InterviewStatus.getDescriptionByCode(status);
    }

    /**
     * Human-readable recommendation description (delegated to the enum)
     */
    private String getRecommendationDesc(String recommendation) {
        if (recommendation == null) return "";
        return Recommendation.getDescriptionByCode(recommendation);
    }

    /**
     * Evict the cache
     */
    private void evictCache(Long id) {
        redisTemplate.delete(RedisKeyConstants.CACHE_INTERVIEW_KEY_PREFIX + id);
        log.debug("Interview cache evicted: id={}", id);
    }
}
