package com.smartats.module.candidate.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartats.common.constants.RedisKeyConstants;
import com.smartats.common.exception.BusinessException;
import com.smartats.common.result.ResultCode;
import com.smartats.module.candidate.dto.CandidateQueryRequest;
import com.smartats.module.candidate.dto.CandidateUpdateRequest;
import com.smartats.module.candidate.entity.Candidate;
import com.smartats.module.candidate.mapper.CandidateMapper;
import com.smartats.module.resume.dto.CandidateInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Candidate service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CandidateService {

    private final CandidateMapper candidateMapper;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final CandidateVectorService candidateVectorService;

    /** Candidate cache TTL (minutes) */
    private static final long CACHE_TTL_MINUTES = 30;

    /**
     * Create a candidate record
     */
    @Transactional(rollbackFor = Exception.class)
    public Candidate createCandidate(Long resumeId, CandidateInfo candidateInfo, String rawJson) {
        log.info("Creating candidate record: resumeId={}, name={}", resumeId, candidateInfo.getName());

        // Check whether the record already exists
        LambdaQueryWrapper<Candidate> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Candidate::getResumeId, resumeId);
        Candidate existing = candidateMapper.selectOne(queryWrapper);

        if (existing != null) {
            log.info("Candidate already exists, updating record: candidateId={}", existing.getId());
            return updateCandidate(existing.getId(), candidateInfo, rawJson);
        }

        Candidate candidate = buildFromCandidateInfo(new Candidate(), candidateInfo, rawJson);
        candidate.setResumeId(resumeId);
        LocalDateTime now = LocalDateTime.now();
        candidate.setParsedAt(now);
        candidate.setCreatedAt(now);
        candidate.setUpdatedAt(now);

        candidateMapper.insert(candidate);
        log.info("Candidate record created successfully: candidateId={}", candidate.getId());
        return candidate;
    }

    /**
     * Update a candidate record
     */
    @Transactional(rollbackFor = Exception.class)
    public Candidate updateCandidate(Long id, CandidateInfo candidateInfo, String rawJson) {
        log.info("Updating candidate record: candidateId={}, name={}", id, candidateInfo.getName());

        Candidate candidate = candidateMapper.selectById(id);
        if (candidate == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Candidate not found: id=" + id);
        }

        buildFromCandidateInfo(candidate, candidateInfo, rawJson);
        candidate.setParsedAt(LocalDateTime.now());
        candidate.setUpdatedAt(LocalDateTime.now());

        candidateMapper.updateById(candidate);
        evictCache(id);

        log.info("Candidate record updated successfully: candidateId={}", id);
        return candidateMapper.selectById(id);
    }

    /**
     * Query a candidate by resumeId
     */
    public Candidate getByResumeId(Long resumeId) {
        LambdaQueryWrapper<Candidate> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Candidate::getResumeId, resumeId);
        return candidateMapper.selectOne(queryWrapper);
    }

    /**
     * Query candidate details by ID, reading from the Redis cache first
     */
    public Candidate getById(Long id) {
        String cacheKey = RedisKeyConstants.CACHE_CANDIDATE_KEY_PREFIX + id;

        // 1. Check the cache
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, Candidate.class);
            } catch (JsonProcessingException e) {
                log.warn("Candidate cache deserialization failed, falling back to the database: id={}", id, e);
            }
        }

        // 2. Query the database
        Candidate candidate = candidateMapper.selectById(id);
        if (candidate == null) {
            return null;
        }

        // 3. Repopulate the cache
        try {
            redisTemplate.opsForValue().set(
                    cacheKey,
                    objectMapper.writeValueAsString(candidate),
                    CACHE_TTL_MINUTES,
                    TimeUnit.MINUTES
            );
        } catch (JsonProcessingException e) {
            log.warn("Failed to write candidate to cache (no business impact): id={}", id, e);
        }

        return candidate;
    }

    /**
     * Manually update a candidate (called directly by the controller; fields are already set externally)
     */
    @Transactional(rollbackFor = Exception.class)
    public Candidate saveManual(Candidate candidate) {
        candidate.setUpdatedAt(LocalDateTime.now());
        candidateMapper.updateById(candidate);
        evictCache(candidate.getId());
        log.info("Candidate manually updated successfully: candidateId={}", candidate.getId());
        return getById(candidate.getId());
    }

    /**
     * Partially update candidate fields using the DTO (only non-null fields are updated)
     */
    @Transactional(rollbackFor = Exception.class)
    public Candidate updateManual(Long id, CandidateUpdateRequest request) {
        log.info("Updating candidate information: id={}", id);

        Candidate candidate = candidateMapper.selectById(id);
        if (candidate == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Candidate not found");
        }

        // Only update non-null fields
        if (request.getName() != null) candidate.setName(request.getName());
        if (request.getPhone() != null) candidate.setPhone(request.getPhone());
        if (request.getEmail() != null) candidate.setEmail(request.getEmail());
        if (request.getGender() != null) candidate.setGender(request.getGender());
        if (request.getAge() != null) candidate.setAge(request.getAge());
        if (request.getEducation() != null) candidate.setEducation(request.getEducation());
        if (request.getSchool() != null) candidate.setSchool(request.getSchool());
        if (request.getMajor() != null) candidate.setMajor(request.getMajor());
        if (request.getGraduationYear() != null) candidate.setGraduationYear(request.getGraduationYear());
        if (request.getWorkYears() != null) candidate.setWorkYears(request.getWorkYears());
        if (request.getCurrentCompany() != null) candidate.setCurrentCompany(request.getCurrentCompany());
        if (request.getCurrentPosition() != null) candidate.setCurrentPosition(request.getCurrentPosition());
        if (request.getSkills() != null) candidate.setSkills(request.getSkills());
        if (request.getSelfEvaluation() != null) candidate.setSelfEvaluation(request.getSelfEvaluation());

        candidate.setUpdatedAt(LocalDateTime.now());
        candidateMapper.updateById(candidate);
        evictCache(id);

        // Update the vector asynchronously (re-embedding is required after candidate changes)
        Candidate updated = getById(id);
        candidateVectorService.vectorizeCandidateAsync(updated);

        log.info("Candidate updated successfully: candidateId={}", id);
        return updated;
    }

    /**
     * Delete a candidate
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteById(Long id) {
        candidateMapper.deleteById(id);
        evictCache(id);
        // Synchronously delete the Milvus vector
        candidateVectorService.deleteVector(id);
        log.info("Candidate deleted successfully: candidateId={}", id);
    }

    /**
     * Paginated candidate list query with multi-dimensional advanced filtering
     * <ul>
     *   <li>keyword  — fuzzy match on name/email/company/position</li>
     *   <li>education — exact education match</li>
     *   <li>skill    — skill JSON_CONTAINS</li>
     *   <li>minWorkYears / maxWorkYears — years-of-experience range</li>
     *   <li>currentPosition — current position keyword</li>
     * </ul>
     */
    public Page<Candidate> listCandidates(CandidateQueryRequest request) {
        int page = Math.max(1, request.getPage());
        int pageSize = Math.min(100, Math.max(1, request.getPageSize()));

        Page<Candidate> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<Candidate> wrapper = new LambdaQueryWrapper<>();

        // (1) Common keyword
        if (StringUtils.hasText(request.getKeyword())) {
            String kw = request.getKeyword().trim();
            wrapper.and(w -> w
                    .like(Candidate::getName, kw)
                    .or().like(Candidate::getEmail, kw)
                    .or().like(Candidate::getCurrentCompany, kw)
                    .or().like(Candidate::getCurrentPosition, kw)
            );
        }

        // (2) Exact education match
        if (StringUtils.hasText(request.getEducation())) {
            wrapper.eq(Candidate::getEducation, request.getEducation().trim());
        }

        // (3) Skill JSON_CONTAINS
        if (StringUtils.hasText(request.getSkill())) {
            wrapper.apply("JSON_CONTAINS(skills, JSON_QUOTE({0}))", request.getSkill().trim());
        }

        // (4) Years-of-experience range
        if (request.getMinWorkYears() != null) {
            wrapper.ge(Candidate::getWorkYears, request.getMinWorkYears());
        }
        if (request.getMaxWorkYears() != null) {
            wrapper.le(Candidate::getWorkYears, request.getMaxWorkYears());
        }

        // (5) Current position keyword
        if (StringUtils.hasText(request.getCurrentPosition())) {
            wrapper.like(Candidate::getCurrentPosition, request.getCurrentPosition().trim());
        }

        wrapper.orderByDesc(Candidate::getCreatedAt);
        return candidateMapper.selectPage(pageParam, wrapper);
    }

    /**
     * Evict the candidate details cache (write-through invalidation strategy)
     */
    private void evictCache(Long id) {
        redisTemplate.delete(RedisKeyConstants.CACHE_CANDIDATE_KEY_PREFIX + id);
        log.debug("Candidate cache evicted: id={}", id);
    }

    /**
     * Populate a Candidate entity from CandidateInfo fields (shared by the create and update flows)
     */
    private Candidate buildFromCandidateInfo(Candidate candidate, CandidateInfo info, String rawJson) {
        candidate.setName(info.getName());
        candidate.setPhone(info.getPhone());
        candidate.setEmail(info.getEmail());
        candidate.setGender(normalizeGender(info.getGender()));
        candidate.setAge(info.getAge());

        candidate.setEducation(info.getEducation());
        candidate.setSchool(info.getSchool());
        candidate.setMajor(info.getMajor());
        candidate.setGraduationYear(info.getGraduationYear());

        candidate.setWorkYears(info.getWorkYears());
        candidate.setCurrentCompany(info.getCurrentCompany());
        candidate.setCurrentPosition(info.getCurrentPosition());

        candidate.setSkills(info.getSkills());
        candidate.setWorkExperience(convertToMapList(info.getWorkExperience()));
        candidate.setProjectExperience(convertToMapList(info.getProjectExperience()));
        candidate.setSelfEvaluation(info.getSelfEvaluation());
        candidate.setRawJson(rawJson);
        return candidate;
    }

    /**
     * Convert an object list to {@code List<Map<String, Object>>} (for JSON field storage)
     */
    private List<Map<String, Object>> convertToMapList(List<?> list) {
        if (list == null) {
            return null;
        }
        return list.stream()
                .map(obj -> {
                    try {
                        String json = objectMapper.writeValueAsString(obj);
                        return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
                    } catch (JsonProcessingException e) {
                        log.error("Failed to convert object to Map: obj={}", obj, e);
                        return new HashMap<String, Object>();
                    }
                })
                .toList();
    }

    /**
     * Normalize the gender field to legal database ENUM values
     */
    private String normalizeGender(String raw) {
        if (raw == null || raw.isBlank()) {
            return "UNKNOWN";
        }
        return switch (raw.trim().toUpperCase()) {
            case "男", "MALE", "M", "BOY", "MAN" -> "MALE";
            case "女", "FEMALE", "F", "GIRL", "WOMAN" -> "FEMALE";
            default -> "UNKNOWN";
        };
    }
}
