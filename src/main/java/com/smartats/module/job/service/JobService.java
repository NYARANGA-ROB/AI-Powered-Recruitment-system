package com.smartats.module.job.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartats.common.constants.RedisKeyConstants;
import com.smartats.common.enums.JobStatus;
import com.smartats.common.exception.BusinessException;
import com.smartats.common.result.ResultCode;
import com.smartats.module.job.dto.request.*;
import com.smartats.module.job.dto.response.JobResponse;
import com.smartats.module.job.entity.Job;
import com.smartats.module.job.mapper.JobMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {

    private final JobMapper jobMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final CacheEvictionService cacheEvictionService;

    /**
     * Create a job
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createJob(CreateJobRequest request, Long creatorId) {
        log.info("Creating position: title={}, creatorId={}", request.getTitle(), creatorId);

        Job job = new Job();
        BeanUtils.copyProperties(request, job);
        job.setCreatorId(creatorId);
        job.setStatus(JobStatus.DRAFT.getCode());
        job.setViewCount(0);

        // Manually set timestamps (temporary replacement for auto-fill)
        job.setCreatedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());

        // JSON serialization: List<String> -> JSON string
        if (request.getRequiredSkills() != null && !request.getRequiredSkills().isEmpty()) {
            try {
                job.setRequiredSkills(objectMapper.writeValueAsString(request.getRequiredSkills()));
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize skill tags", e);
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "Invalid skill tag format");
            }
        }

        int result = jobMapper.insert(job);
        if (result <= 0) {
            log.error("Failed to create position: title={}", request.getTitle());
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "Failed to create position");
        }

        log.info("Position created successfully: id={}, title={}", job.getId(), job.getTitle());
        return job.getId();
    }

    /**
     * Update a job (delayed double-delete)
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateJob(UpdateJobRequest request, Long operatorId) {
        log.info("Updating position: id={}, operatorId={}", request.getId(), operatorId);

        String cacheKey = RedisKeyConstants.CACHE_JOB_KEY_PREFIX + request.getId();

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // Step 1: First cache deletion (before the update)
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        redisTemplate.delete(cacheKey);
        log.debug("Cache deletion pass 1: key={}", cacheKey);

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // Step 2: Check whether the job exists
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        Job job = jobMapper.selectById(request.getId());
        if (job == null) {
            log.warn("Position not found: id={}", request.getId());
            throw new BusinessException(ResultCode.NOT_FOUND, "Position not found");
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // Step 3: Permission check
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        if (!job.getCreatorId().equals(operatorId)) {
            log.warn("No permission to modify position: jobId={}, creatorId={}, operatorId={}", request.getId(), job.getCreatorId(), operatorId);
            throw new BusinessException(ResultCode.FORBIDDEN, "No permission to modify this position");
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // Step 4: Update job information
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        if (StringUtils.hasText(request.getTitle())) {
            job.setTitle(request.getTitle());
        }
        if (StringUtils.hasText(request.getDepartment())) {
            job.setDepartment(request.getDepartment());
        }
        if (StringUtils.hasText(request.getDescription())) {
            job.setDescription(request.getDescription());
        }
        if (StringUtils.hasText(request.getRequirements())) {
            job.setRequirements(request.getRequirements());
        }
        if (request.getRequiredSkills() != null) {
            try {
                job.setRequiredSkills(objectMapper.writeValueAsString(request.getRequiredSkills()));
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize skill tags", e);
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "Invalid skill tag format");
            }
        }
        if (request.getSalaryMin() != null) {
            job.setSalaryMin(request.getSalaryMin());
        }
        if (request.getSalaryMax() != null) {
            job.setSalaryMax(request.getSalaryMax());
        }
        if (request.getExperienceMin() != null) {
            job.setExperienceMin(request.getExperienceMin());
        }
        if (request.getExperienceMax() != null) {
            job.setExperienceMax(request.getExperienceMax());
        }
        if (request.getEducation() != null) {
            job.setEducation(request.getEducation());
        }
        if (request.getJobType() != null) {
            job.setJobType(request.getJobType());
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // Step 5: Update the database
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        int result = jobMapper.updateById(job);
        if (result <= 0) {
            log.error("Failed to update position: id={}", request.getId());
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "Failed to update position");
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // Step 6: Delayed double-delete (async cache deletion, via a separate Service to ensure the proxy takes effect)
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        cacheEvictionService.asyncDeleteCache(cacheKey);

        log.info("Position updated successfully: id={}", job.getId());
    }

    /**
     * Get job detail (uses a Redis atomic counter to optimize view-count tracking)
     * <p>
     * View count formula: view count shown to users = cumulative database value + Redis incremental counter
     */
    public JobResponse getJobDetail(Long id) {
        log.info("Querying position detail: id={}", id);

        String cacheKey = RedisKeyConstants.CACHE_JOB_KEY_PREFIX + id;
        String counterKey = RedisKeyConstants.COUNTER_JOB_VIEW_PREFIX + id;

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // Step 1: Atomically increment the Redis incremental counter (counted on every visit)
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        Long redisIncrement = redisTemplate.opsForValue().increment(counterKey);

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // Step 2: Try to read the job from the cache (skips the DB on cache hit)
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        String cachedJson = redisTemplate.opsForValue().get(cacheKey);
        if (cachedJson != null) {
            try {
                JobResponse cached = objectMapper.readValue(cachedJson, JobResponse.class);
                // The view count in the cache may be stale; override it with the cached baseViewCount + the current Redis increment
                cached.setViewCount(cached.getViewCount() != null ? cached.getViewCount() + 1 : redisIncrement.intValue());
                log.debug("Cache hit: jobId={}", id);
                return cached;
            } catch (Exception e) {
                log.warn("Failed to deserialize position cache, falling back to database: id={}", id, e);
            }
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // Step 3: Cache miss, read from the database
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        Job job = jobMapper.selectById(id);
        if (job == null) {
            log.warn("Position not found: id={}", id);
            throw new BusinessException(ResultCode.NOT_FOUND, "Position not found");
        }

        // The value stored in the database is the historical cumulative view count
        int baseViewCount = job.getViewCount() != null ? job.getViewCount() : 0;

        log.debug("Redis counter incremented: key={}, increment={}, baseViewCount={}",
            counterKey, redisIncrement, baseViewCount);

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // Step 4: Compute the total view count and backfill the cache
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        JobResponse response = convertToResponse(job);

        // Total view count = cumulative database value + Redis incremental counter
        response.setViewCount(baseViewCount + redisIncrement.intValue());

        try {
            redisTemplate.opsForValue().set(cacheKey,
                objectMapper.writeValueAsString(response), 30, TimeUnit.MINUTES);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize cache data", e);
        }

        return response;
    }

    /**
     * Job list (pagination, filtering)
     */
    public Page<JobResponse> getJobList(JobQueryRequest request) {
        log.info("Querying position list: {}", request);

        LambdaQueryWrapper<Job> queryWrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(request.getKeyword())) {
            queryWrapper.and(wrapper -> wrapper.like(Job::getTitle, request.getKeyword()).or().like(Job::getDescription, request.getKeyword()).or().like(Job::getRequirements, request.getKeyword()));
        }

        if (StringUtils.hasText(request.getDepartment())) {
            queryWrapper.eq(Job::getDepartment, request.getDepartment());
        }

        if (StringUtils.hasText(request.getJobType())) {
            queryWrapper.eq(Job::getJobType, request.getJobType());
        }

        if (StringUtils.hasText(request.getEducation())) {
            queryWrapper.eq(Job::getEducation, request.getEducation());
        }

        if (request.getExperienceMin() != null) {
            queryWrapper.le(Job::getExperienceMin, request.getExperienceMin());
        }

        if (request.getSalaryMin() != null) {
            queryWrapper.ge(Job::getSalaryMin, request.getSalaryMin());
        }

        if (StringUtils.hasText(request.getStatus())) {
            queryWrapper.eq(Job::getStatus, request.getStatus());
        } else {
            queryWrapper.eq(Job::getStatus, JobStatus.PUBLISHED.getCode());
        }

        // Sorting
        if ("created_at".equals(request.getOrderBy())) {
            if ("asc".equals(request.getOrderDirection())) {
                queryWrapper.orderByAsc(Job::getCreatedAt);
            } else {
                queryWrapper.orderByDesc(Job::getCreatedAt);
            }
        } else if ("salary_max".equals(request.getOrderBy())) {
            if ("asc".equals(request.getOrderDirection())) {
                queryWrapper.orderByAsc(Job::getSalaryMax);
            } else {
                queryWrapper.orderByDesc(Job::getSalaryMax);
            }
        } else if ("view_count".equals(request.getOrderBy())) {
            if ("asc".equals(request.getOrderDirection())) {
                queryWrapper.orderByAsc(Job::getViewCount);
            } else {
                queryWrapper.orderByDesc(Job::getViewCount);
            }
        }

        Page<Job> page = new Page<>(request.getPageNum(), request.getPageSize());
        Page<Job> jobPage = jobMapper.selectPage(page, queryWrapper);

        Page<JobResponse> responsePage = new Page<>(jobPage.getCurrent(), jobPage.getSize(), jobPage.getTotal());
        List<JobResponse> responseList = jobPage.getRecords().stream().map(this::convertToResponse).collect(Collectors.toList());

        responsePage.setRecords(responseList);

        return responsePage;
    }

    /**
     * Publish a job
     */
    @Transactional(rollbackFor = Exception.class)
    public void publishJob(Long id, Long operatorId) {
        log.info("Publishing position: id={}, operatorId={}", id, operatorId);

        Job job = jobMapper.selectById(id);
        if (job == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Position not found");
        }

        if (!job.getCreatorId().equals(operatorId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "No permission to operate on this position");
        }

        if (JobStatus.PUBLISHED.getCode().equals(job.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Position is already published");
        }

        job.setStatus(JobStatus.PUBLISHED.getCode());
        jobMapper.updateById(job);

        // Evict the cache
        redisTemplate.delete(RedisKeyConstants.CACHE_JOB_KEY_PREFIX + id);

        log.info("Position published successfully: id={}", id);
    }

    /**
     * Close a job
     */
    @Transactional(rollbackFor = Exception.class)
    public void closeJob(Long id, Long operatorId) {
        log.info("Closing position: id={}, operatorId={}", id, operatorId);

        Job job = jobMapper.selectById(id);
        if (job == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Position not found");
        }

        if (!job.getCreatorId().equals(operatorId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "No permission to operate on this position");
        }

        if (JobStatus.CLOSED.getCode().equals(job.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Position is already closed");
        }

        job.setStatus(JobStatus.CLOSED.getCode());
        jobMapper.updateById(job);

        // Evict the cache
        redisTemplate.delete(RedisKeyConstants.CACHE_JOB_KEY_PREFIX + id);

        log.info("Position closed successfully: id={}", id);
    }

    /**
     * Delete a job (soft delete)
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteJob(Long id, Long operatorId) {
        log.info("Deleting position: id={}, operatorId={}", id, operatorId);

        Job job = jobMapper.selectById(id);
        if (job == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Position not found");
        }

        if (!job.getCreatorId().equals(operatorId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "No permission to operate on this position");
        }

        int result = jobMapper.deleteById(id);
        if (result <= 0) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "Failed to delete position");
        }

        // Evict the cache
        redisTemplate.delete(RedisKeyConstants.CACHE_JOB_KEY_PREFIX + id);

        log.info("Position deleted successfully: id={}", id);
    }

    /**
     * Hot job list
     */
    public List<JobResponse> getHotJobs(Integer limit) {
        log.info("Querying hot positions: limit={}", limit);

        // Safe boundary check to avoid SQL concatenation via .last()
        int safeLimit = (limit != null && limit > 0 && limit <= 50) ? limit : 10;

        Page<Job> page = new Page<>(1, safeLimit);
        LambdaQueryWrapper<Job> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Job::getStatus, JobStatus.PUBLISHED.getCode()).orderByDesc(Job::getViewCount);

        Page<Job> resultPage = jobMapper.selectPage(page, queryWrapper);
        return resultPage.getRecords().stream().map(this::convertToResponse).collect(Collectors.toList());
    }

    /**
     * Convert to the response object
     */
    private JobResponse convertToResponse(Job job) {
        JobResponse response = new JobResponse();
        BeanUtils.copyProperties(job, response);

        // JSON deserialization: JSON string -> List<String>
        if (StringUtils.hasText(job.getRequiredSkills())) {
            try {
                response.setRequiredSkills(java.util.Arrays.stream(objectMapper.readValue(job.getRequiredSkills(), String[].class)).collect(Collectors.toList()));
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize skill tags", e);
            }
        }

        // Formatted display
        if (job.getSalaryMin() != null && job.getSalaryMax() != null) {
            response.setSalaryRange(job.getSalaryMin() + "K-" + job.getSalaryMax() + "K");
        }

        if (job.getExperienceMin() != null && job.getExperienceMax() != null) {
            response.setExperienceRange(job.getExperienceMin() + "-" + job.getExperienceMax() + " years");
        }

        // Status description (delegated to the enum)
        response.setStatusDesc(JobStatus.getDescriptionByCode(job.getStatus()));

        return response;
    }
}