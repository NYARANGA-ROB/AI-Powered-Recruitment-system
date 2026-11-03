package com.smartats.module.analytics.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartats.common.constants.RedisKeyConstants;
import com.smartats.common.enums.ApplicationStatus;
import com.smartats.common.enums.JobStatus;
import com.smartats.module.analytics.dto.AnalyticsQueryRequest;
import com.smartats.module.analytics.dto.FunnelStageDTO;
import com.smartats.module.analytics.dto.RecruitmentOverviewDTO;
import com.smartats.module.analytics.mapper.AnalyticsMapper;
import com.smartats.module.candidate.mapper.CandidateMapper;
import com.smartats.module.job.entity.Job;
import com.smartats.module.job.mapper.JobMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Recruitment analytics service
 * <p>
 * Core responsibilities:
 * <ul>
 *   <li>Recruitment funnel aggregation (grouped by status, with conversion rates)</li>
 *   <li>Core metric summaries (offer rate, average match score, average time-to-hire, etc.)</li>
 *   <li>Monthly trend statistics</li>
 *   <li>Redis cache (5-minute TTL, Cache-Aside pattern)</li>
 * </ul>
 *
 * <p>
 * Caching strategy:
 * <ul>
 *   <li>Analytics data is aggregate statistics, so its real-time requirements are lower than those of detail endpoints</li>
 *   <li>The 5-minute TTL balances data freshness against database load</li>
 *   <li>SSE pushes ensure the frontend still perceives real-time changes (triggering a client refetch)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private static final long CACHE_TTL_SECONDS = 300; // 5 minutes

    private final AnalyticsMapper analyticsMapper;
    private final JobMapper jobMapper;
    private final CandidateMapper candidateMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Funnel stage ordering (forward flow)
     * <p>
     * Logical order of the recruitment pipeline: applied -> screening -> interview -> offer
     * REJECTED and WITHDRAWN are displayed separately as non-funnel stages
     */
    private static final List<String> FUNNEL_ORDER = List.of(
            "PENDING", "SCREENING", "INTERVIEW", "OFFER"
    );

    /**
     * Get recruitment overview data (cached)
     *
     * @param request query parameters (supports filtering by job and date range)
     * @return the full recruitment overview
     */
    public RecruitmentOverviewDTO getOverview(AnalyticsQueryRequest request) {
        // Build the cache key
        String cacheKey = buildCacheKey(request);
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StringUtils.hasText(cached)) {
            try {
                log.debug("Analytics cache hit: {}", cacheKey);
                return objectMapper.readValue(cached, RecruitmentOverviewDTO.class);
            } catch (JsonProcessingException e) {
                log.warn("Analytics cache deserialization failed, recomputing: {}", e.getMessage());
            }
        }

        // Cache miss, run the aggregate queries
        RecruitmentOverviewDTO overview = buildOverview(request);

        // Write to the cache
        try {
            String json = objectMapper.writeValueAsString(overview);
            stringRedisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL_SECONDS, TimeUnit.SECONDS);
            log.debug("Analytics data cached: {}", cacheKey);
        } catch (JsonProcessingException e) {
            log.warn("Analytics cache serialization failed: {}", e.getMessage());
        }

        return overview;
    }

    /**
     * Invalidate the cache for the given query conditions
     * <p>
     * Called when triggered by an SSE event so the next query returns fresh data
     */
    public void evictCache(AnalyticsQueryRequest request) {
        String cacheKey = buildCacheKey(request);
        stringRedisTemplate.delete(cacheKey);

        // Also clear the unconditional global cache
        String globalKey = buildCacheKey(new AnalyticsQueryRequest());
        stringRedisTemplate.delete(globalKey);
    }

    // ==================== Internal implementation ====================

    /**
     * Build the complete recruitment overview data
     */
    private RecruitmentOverviewDTO buildOverview(AnalyticsQueryRequest request) {
        LocalDateTime startDateTime = request.getStartDate() != null
                ? request.getStartDate().atStartOfDay() : null;
        LocalDateTime endDateTime = request.getEndDate() != null
                ? request.getEndDate().atTime(LocalTime.MAX) : null;
        Long jobId = request.getJobId();

        // 1. Status distribution statistics
        List<Map<String, Object>> statusCounts = analyticsMapper.countGroupByStatus(
                jobId, startDateTime, endDateTime);
        Map<String, Long> statusMap = new LinkedHashMap<>();
        long totalApplications = 0;
        for (Map<String, Object> row : statusCounts) {
            String status = (String) row.get("status");
            long cnt = ((Number) row.get("cnt")).longValue();
            statusMap.put(status, cnt);
            totalApplications += cnt;
        }

        // 2. Build the funnel data
        List<FunnelStageDTO> funnel = buildFunnel(statusMap, totalApplications);

        // 3. Monthly trend
        List<Map<String, Object>> monthCounts = analyticsMapper.countByMonth(
                jobId, startDateTime, endDateTime);
        Map<String, Long> applicationTrend = new LinkedHashMap<>();
        for (Map<String, Object> row : monthCounts) {
            String month = (String) row.get("month");
            long cnt = ((Number) row.get("cnt")).longValue();
            applicationTrend.put(month, cnt);
        }

        // 4. Average match score
        BigDecimal avgMatchScore = analyticsMapper.avgMatchScore(jobId, startDateTime, endDateTime);
        if (avgMatchScore != null) {
            avgMatchScore = avgMatchScore.setScale(1, RoundingMode.HALF_UP);
        }

        // 5. Average time-to-hire
        Double avgDays = analyticsMapper.avgDaysToOffer(jobId, startDateTime, endDateTime);
        if (avgDays != null) {
            avgDays = Math.round(avgDays * 10) / 10.0;
        }

        // 6. Interview count
        long interviewCount = analyticsMapper.countInterviews(startDateTime, endDateTime);

        // 7. Offer count
        long offersExtended = statusMap.getOrDefault("OFFER", 0L);

        // 8. Total candidates (global metric, not filtered by jobId)
        long totalCandidates = candidateMapper.selectCount(null);

        // 9. Job statistics
        long totalJobs = jobMapper.selectCount(null);
        long openJobs = jobMapper.selectCount(
                new LambdaQueryWrapper<Job>().eq(Job::getStatus, JobStatus.PUBLISHED.getCode()));

        // 10. Offer conversion rate
        double offerRate = totalApplications > 0
                ? Math.round(offersExtended * 1000.0 / totalApplications) / 10.0
                : 0.0;

        return RecruitmentOverviewDTO.builder()
                .totalApplications(totalApplications)
                .totalCandidates(totalCandidates)
                .totalJobs(totalJobs)
                .openJobs(openJobs)
                .interviewsScheduled(interviewCount)
                .offersExtended(offersExtended)
                .offerRate(offerRate)
                .avgMatchScore(avgMatchScore)
                .avgDaysToOffer(avgDays)
                .funnel(funnel)
                .applicationTrend(applicationTrend)
                .build();
    }

    /**
     * Build the recruitment funnel
     * <p>
     * Funnel logic: each stage cumulatively counts applications that reached at least that stage.
     * <ul>
     *   <li>PENDING (applied): all applications</li>
     *   <li>SCREENING: all applications except those still in PENDING</li>
     *   <li>INTERVIEW: applications in INTERVIEW or OFFER status</li>
     *   <li>OFFER (hired): OFFER status only</li>
     * </ul>
     * REJECTED and WITHDRAWN are shown as additional stages and are excluded from conversion-rate calculations.
     */
    private List<FunnelStageDTO> buildFunnel(Map<String, Long> statusMap, long total) {
        List<FunnelStageDTO> funnel = new ArrayList<>();

        if (total == 0) {
            for (String stage : FUNNEL_ORDER) {
                ApplicationStatus appStatus = ApplicationStatus.fromCode(stage);
                funnel.add(FunnelStageDTO.builder()
                        .stage(stage)
                        .stageLabel(appStatus != null ? appStatus.getDescription() : stage)
                        .count(0)
                        .percentage(0)
                        .conversionRate(0)
                        .build());
            }
            return funnel;
        }

        // Compute the cumulative counts reaching each stage
        // Applications in OFFER must have passed through INTERVIEW and SCREENING
        long offerCount = statusMap.getOrDefault("OFFER", 0L);
        long interviewCount = statusMap.getOrDefault("INTERVIEW", 0L) + offerCount;
        long screeningCount = statusMap.getOrDefault("SCREENING", 0L) + interviewCount;
        long pendingCount = total; // Every application went through the applied stage

        Map<String, Long> cumulativeCounts = new LinkedHashMap<>();
        cumulativeCounts.put("PENDING", pendingCount);
        cumulativeCounts.put("SCREENING", screeningCount);
        cumulativeCounts.put("INTERVIEW", interviewCount);
        cumulativeCounts.put("OFFER", offerCount);

        long previousCount = 0;
        for (String stage : FUNNEL_ORDER) {
            long count = cumulativeCounts.getOrDefault(stage, 0L);
            ApplicationStatus appStatus = ApplicationStatus.fromCode(stage);

            double percentage = Math.round(count * 1000.0 / total) / 10.0;
            double conversionRate;
            if (stage.equals("PENDING")) {
                conversionRate = 100.0;
            } else {
                conversionRate = previousCount > 0
                        ? Math.round(count * 1000.0 / previousCount) / 10.0
                        : 0.0;
            }

            funnel.add(FunnelStageDTO.builder()
                    .stage(stage)
                    .stageLabel(appStatus != null ? appStatus.getDescription() : stage)
                    .count(count)
                    .percentage(percentage)
                    .conversionRate(conversionRate)
                    .build());

            previousCount = count;
        }

        // Additional: REJECTED and WITHDRAWN (non-funnel stages, absolute counts only)
        for (String extra : List.of("REJECTED", "WITHDRAWN")) {
            long count = statusMap.getOrDefault(extra, 0L);
            ApplicationStatus appStatus = ApplicationStatus.fromCode(extra);
            funnel.add(FunnelStageDTO.builder()
                    .stage(extra)
                    .stageLabel(appStatus != null ? appStatus.getDescription() : extra)
                    .count(count)
                    .percentage(total > 0 ? Math.round(count * 1000.0 / total) / 10.0 : 0.0)
                    .conversionRate(0) // No conversion rate for non-funnel stages
                    .build());
        }

        return funnel;
    }

    /**
     * Build the cache key
     * <p>
     * Format: cache:analytics:overview:{jobId}:{startDate}:{endDate}
     * Empty parameters are replaced with "all"
     */
    private String buildCacheKey(AnalyticsQueryRequest request) {
        String jobPart = request.getJobId() != null ? request.getJobId().toString() : "all";
        String startPart = request.getStartDate() != null ? request.getStartDate().toString() : "all";
        String endPart = request.getEndDate() != null ? request.getEndDate().toString() : "all";
        return RedisKeyConstants.CACHE_ANALYTICS_KEY_PREFIX + jobPart + ":" + startPart + ":" + endPart;
    }
}
