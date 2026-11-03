package com.smartats.module.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Recruitment overview data
 * <p>
 * Provides a full-view snapshot of the recruitment pipeline, including:
 * <ul>
 *   <li>Core metrics: total applications, total candidates, offer conversion rate, average match score, average time-to-hire</li>
 *   <li>Funnel distribution: counts and conversion rates per stage</li>
 *   <li>Monthly trend: application volume aggregated by month</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecruitmentOverviewDTO implements Serializable {

    // ==================== Core metrics ====================

    /**
     * Total number of applications
     */
    private long totalApplications;

    /**
     * Total number of candidates
     */
    private long totalCandidates;

    /**
     * Total number of jobs
     */
    private long totalJobs;

    /**
     * Number of open jobs
     */
    private long openJobs;

    /**
     * Number of scheduled interviews
     */
    private long interviewsScheduled;

    /**
     * Number of offers extended
     */
    private long offersExtended;

    /**
     * Offer conversion rate (total offers / total applications)
     */
    private double offerRate;

    /**
     * Average AI match score (0-100)
     */
    private BigDecimal avgMatchScore;

    /**
     * Average time-to-hire (days): average days from application to offer
     */
    private Double avgDaysToOffer;

    // ==================== Funnel distribution ====================

    /**
     * Per-stage statistics of the recruitment funnel
     */
    private List<FunnelStageDTO> funnel;

    // ==================== Trend data ====================

    /**
     * Monthly aggregated application trend {month -> count}
     * <p>
     * key format: yyyy-MM (e.g. "2026-01")
     */
    private Map<String, Long> applicationTrend;
}
