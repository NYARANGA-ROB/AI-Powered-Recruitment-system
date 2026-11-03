package com.smartats.module.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartats.common.exception.BusinessException;
import com.smartats.common.result.ResultCode;
import com.smartats.infrastructure.vector.EmbeddingService;
import com.smartats.infrastructure.vector.VectorStoreService;
import com.smartats.module.application.dto.MatchScoreResponse;
import com.smartats.module.application.entity.JobApplication;
import com.smartats.module.application.mapper.JobApplicationMapper;
import com.smartats.module.candidate.entity.Candidate;
import com.smartats.module.candidate.mapper.CandidateMapper;
import com.smartats.module.job.entity.Job;
import com.smartats.module.job.mapper.JobMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI job-candidate intelligent matching score service
 * <p>
 * Computes the match between a candidate and a job across multiple dimensions:
 * <ul>
 *   <li><b>Vector semantic similarity (30%)</b>: based on Milvus cosine similarity, measuring overall profile match</li>
 *   <li><b>Skill match (35%)</b>: overlap rate between the JD's required skills and the candidate's skill tags</li>
 *   <li><b>Experience match (20%)</b>: how the candidate's working years fit the job's required range</li>
 *   <li><b>Education match (15%)</b>: how the candidate's education fits the job's education requirement</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchScoreService {

    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final JobMapper jobMapper;
    private final CandidateMapper candidateMapper;
    private final JobApplicationMapper jobApplicationMapper;
    private final ObjectMapper objectMapper;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Weight configuration
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /** Semantic similarity weight */
    private static final double WEIGHT_SEMANTIC = 0.30;
    /** Skill match weight */
    private static final double WEIGHT_SKILL = 0.35;
    /** Experience match weight */
    private static final double WEIGHT_EXPERIENCE = 0.20;
    /** Education match weight */
    private static final double WEIGHT_EDUCATION = 0.15;

    /** Education level mapping (values are the Chinese tokens stored in jobs.education and candidates.education) */
    private static final Map<String, Integer> EDUCATION_LEVEL = Map.of(
            "不限", 0,
            "大专", 1,
            "本科", 2,
            "硕士", 3,
            "博士", 4
    );

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Public methods
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * Calculate and save the match score
     *
     * @param applicationId job application ID
     * @return match score result
     */
    @Transactional(rollbackFor = Exception.class)
    public MatchScoreResponse calculateAndSave(Long applicationId) {
        log.info("Start calculating match score: applicationId={}", applicationId);

        // ① Query the application record
        JobApplication application = jobApplicationMapper.selectById(applicationId);
        if (application == null) {
            throw new BusinessException(ResultCode.APPLICATION_NOT_FOUND);
        }

        // ② Query the job and the candidate
        Job job = jobMapper.selectById(application.getJobId());
        if (job == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Position not found");
        }

        Candidate candidate = candidateMapper.selectById(application.getCandidateId());
        if (candidate == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Candidate not found");
        }

        // ③ Multi-dimensional scoring
        MatchScoreResponse response = calculateScore(job, candidate);
        response.setApplicationId(applicationId);
        response.setJobId(job.getId());
        response.setCandidateId(candidate.getId());
        response.setCalculatedAt(LocalDateTime.now());

        // ④ Persist to the job_applications table
        application.setMatchScore(response.getTotalScore());
        application.setMatchCalculatedAt(response.getCalculatedAt());
        try {
            application.setMatchReasons(objectMapper.writeValueAsString(response.getMatchReasons()));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize match reasons: {}", e.getMessage());
        }
        jobApplicationMapper.updateById(application);

        log.info("Match score calculation complete: applicationId={}, score={}", applicationId, response.getTotalScore());
        return response;
    }

    /**
     * Asynchronously calculate the match score (auto-triggered when creating an application)
     */
    @Async("asyncExecutor")
    public void calculateAndSaveAsync(Long applicationId) {
        try {
            calculateAndSave(applicationId);
        } catch (Exception e) {
            log.error("Async match score calculation failed: applicationId={}, error={}", applicationId, e.getMessage(), e);
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Core scoring logic
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * Calculate the overall match score between the candidate and the job
     */
    private MatchScoreResponse calculateScore(Job job, Candidate candidate) {
        List<String> reasons = new ArrayList<>();

        // 1. Vector semantic similarity (0-100)
        double semanticScore = calculateSemanticScore(job, candidate);
        reasons.add(formatSemanticReason(semanticScore));

        // 2. Skill match (0-100)
        double skillScore = calculateSkillScore(job, candidate, reasons);

        // 3. Experience match (0-100)
        double experienceScore = calculateExperienceScore(job, candidate, reasons);

        // 4. Education match (0-100)
        double educationScore = calculateEducationScore(job, candidate, reasons);

        // 5. Weighted total score
        double totalScore = semanticScore * WEIGHT_SEMANTIC
                + skillScore * WEIGHT_SKILL
                + experienceScore * WEIGHT_EXPERIENCE
                + educationScore * WEIGHT_EDUCATION;

        // Build the response
        MatchScoreResponse response = new MatchScoreResponse();
        response.setTotalScore(BigDecimal.valueOf(totalScore).setScale(2, RoundingMode.HALF_UP));
        response.setMatchReasons(reasons);

        MatchScoreResponse.ScoreBreakdown breakdown = new MatchScoreResponse.ScoreBreakdown();
        breakdown.setSemanticScore(BigDecimal.valueOf(semanticScore).setScale(2, RoundingMode.HALF_UP));
        breakdown.setSkillScore(BigDecimal.valueOf(skillScore).setScale(2, RoundingMode.HALF_UP));
        breakdown.setExperienceScore(BigDecimal.valueOf(experienceScore).setScale(2, RoundingMode.HALF_UP));
        breakdown.setEducationScore(BigDecimal.valueOf(educationScore).setScale(2, RoundingMode.HALF_UP));
        response.setBreakdown(breakdown);

        return response;
    }

    /**
     * Vector semantic similarity calculation
     * <p>
     * Builds the job JD into query text -> Embedding -> Milvus search for the candidate -> returns the cosine similarity
     */
    private double calculateSemanticScore(Job job, Candidate candidate) {
        try {
            // Build the job text used for the Embedding
            String jobText = buildJobText(job);

            // Generate the query vector
            List<Float> queryEmbedding = embeddingService.generateQueryEmbedding(jobText);

            // Search in Milvus (topK=50) to find the target candidate
            List<VectorStoreService.SearchResult> results = vectorStoreService.search(queryEmbedding, 50);

            // Find the target candidate's score among the search results
            for (VectorStoreService.SearchResult result : results) {
                if (result.getCandidateId().equals(candidate.getId())) {
                    // COSINE similarity 0~1, converted to a 0~100 score
                    return result.getScore() * 100;
                }
            }

            // Candidate not in the top-50, assign a base score
            log.debug("Candidate not found in vector search top-50: candidateId={}", candidate.getId());
            return 20.0;

        } catch (Exception e) {
            log.warn("Semantic similarity calculation failed, using default score: {}", e.getMessage());
            return 50.0; // Assign a middle score on failure so other dimensions are unaffected
        }
    }

    /**
     * Skill match calculation
     * <p>
     * Compares the Jaccard similarity coefficient between the job's requiredSkills and the candidate's skills
     */
    private double calculateSkillScore(Job job, Candidate candidate, List<String> reasons) {
        List<String> requiredSkills = parseJsonArray(job.getRequiredSkills());
        List<String> candidateSkills = candidate.getSkills();

        if (requiredSkills == null || requiredSkills.isEmpty()) {
            reasons.add("No skill requirements set for the position, skill match defaults to full score");
            return 100.0;
        }

        if (candidateSkills == null || candidateSkills.isEmpty()) {
            reasons.add("Candidate has no skill tags, skill match is zero");
            return 0.0;
        }

        // Compare after normalization (lowercased)
        Set<String> required = requiredSkills.stream()
                .map(String::toLowerCase)
                .map(String::trim)
                .collect(Collectors.toSet());

        Set<String> owned = candidateSkills.stream()
                .map(String::toLowerCase)
                .map(String::trim)
                .collect(Collectors.toSet());

        // Compute the matched skills
        Set<String> matched = new HashSet<>(required);
        matched.retainAll(owned);

        // Compute the missing skills
        Set<String> missing = new HashSet<>(required);
        missing.removeAll(owned);

        double score = (double) matched.size() / required.size() * 100;

        if (!matched.isEmpty()) {
            reasons.add(String.format("Matched skills %d/%d: %s", matched.size(), required.size(),
                    String.join(", ", matched)));
        }
        if (!missing.isEmpty()) {
            reasons.add(String.format("Missing skills: %s", String.join(", ", missing)));
        }

        return score;
    }

    /**
     * Experience match calculation
     * <p>
     * Checks whether the candidate's working years fall within the job's required range
     */
    private double calculateExperienceScore(Job job, Candidate candidate, List<String> reasons) {
        Integer candidateYears = candidate.getWorkYears();
        Integer minYears = job.getExperienceMin();
        Integer maxYears = job.getExperienceMax();

        if (minYears == null && maxYears == null) {
            reasons.add("No experience requirements set for the position, experience match defaults to full score");
            return 100.0;
        }

        if (candidateYears == null) {
            reasons.add("Candidate's working years unknown");
            return 50.0; // Assign a middle score when unknown
        }

        int min = minYears != null ? minYears : 0;
        int max = maxYears != null ? maxYears : Integer.MAX_VALUE;

        if (candidateYears >= min && candidateYears <= max) {
            reasons.add(String.format("%d years of experience, within required range %d-%s years",
                    candidateYears, min, maxYears != null ? String.valueOf(maxYears) : "no limit"));
            return 100.0;
        }

        // Out of range, score decreases with the gap
        int gap;
        if (candidateYears < min) {
            gap = min - candidateYears;
            reasons.add(String.format("%d years of experience, below the required minimum of %d years (short by %d years)",
                    candidateYears, min, gap));
        } else {
            gap = candidateYears - max;
            reasons.add(String.format("%d years of experience, above the required maximum of %d years (over by %d years)",
                    candidateYears, max, gap));
        }

        // Deduct 15 points per year of gap, minimum 10 points
        return Math.max(10.0, 100.0 - gap * 15.0);
    }

    /**
     * Education match calculation
     */
    private double calculateEducationScore(Job job, Candidate candidate, List<String> reasons) {
        String requiredEdu = job.getEducation();
        String candidateEdu = candidate.getEducation();

        if (requiredEdu == null || "不限".equals(requiredEdu)) {
            reasons.add("Position has no education requirement, education match is full score");
            return 100.0;
        }

        if (!StringUtils.hasText(candidateEdu)) {
            reasons.add("Candidate's education unknown");
            return 50.0;
        }

        int required = EDUCATION_LEVEL.getOrDefault(requiredEdu, 0);
        int actual = EDUCATION_LEVEL.getOrDefault(candidateEdu, 0);

        if (actual >= required) {
            reasons.add(String.format("Education [%s] meets the requirement [%s]", candidateEdu, requiredEdu));
            return 100.0;
        }

        // Deduct 25 points per level of gap
        int gap = required - actual;
        double score = Math.max(0, 100.0 - gap * 25.0);
        reasons.add(String.format("Education [%s] below the requirement [%s] (short by %d levels)", candidateEdu, requiredEdu, gap));
        return score;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Helper methods
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * Build the job description text (used to generate the Embedding)
     */
    private String buildJobText(Job job) {
        StringBuilder sb = new StringBuilder();
        sb.append("Position: ").append(job.getTitle()).append("\n");
        if (StringUtils.hasText(job.getDepartment())) {
            sb.append("Department: ").append(job.getDepartment()).append("\n");
        }
        if (StringUtils.hasText(job.getDescription())) {
            sb.append("Description: ").append(job.getDescription()).append("\n");
        }
        if (StringUtils.hasText(job.getRequirements())) {
            sb.append("Requirements: ").append(job.getRequirements()).append("\n");
        }
        List<String> skills = parseJsonArray(job.getRequiredSkills());
        if (skills != null && !skills.isEmpty()) {
            sb.append("Skills: ").append(String.join(", ", skills)).append("\n");
        }
        if (job.getEducation() != null) {
            sb.append("Education: ").append(job.getEducation()).append("\n");
        }
        if (job.getExperienceMin() != null) {
            sb.append("Experience: ").append(job.getExperienceMin());
            if (job.getExperienceMax() != null) {
                sb.append("-").append(job.getExperienceMax());
            } else {
                sb.append("+");
            }
            sb.append(" years\n");
        }
        return sb.toString();
    }

    /**
     * Parse a JSON array string into List<String>
     */
    private List<String> parseJsonArray(String json) {
        if (!StringUtils.hasText(json)) return null;
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse JSON array: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Format the semantic similarity match reason
     */
    private String formatSemanticReason(double score) {
        if (score >= 80) return String.format("Semantic match %.0f%% (high match)", score);
        if (score >= 60) return String.format("Semantic match %.0f%% (moderate match)", score);
        if (score >= 40) return String.format("Semantic match %.0f%% (fair match)", score);
        return String.format("Semantic match %.0f%% (low match)", score);
    }
}
