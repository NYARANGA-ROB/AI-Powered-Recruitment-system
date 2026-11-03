package com.smartats.module.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Funnel stage data
 * <p>
 * Statistics for each stage of the recruitment funnel, including absolute counts and relative ratios,
 * to support funnel charts and conversion-rate analysis on the frontend.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FunnelStageDTO implements Serializable {

    /**
     * Stage code (corresponds to the ApplicationStatus enum)
     */
    private String stage;

    /**
     * Human-readable stage label
     */
    private String stageLabel;

    /**
     * Number of applications at this stage
     */
    private long count;

    /**
     * Share of total applications, in percent (0-100.0)
     */
    private double percentage;

    /**
     * Conversion rate relative to the previous stage (0-100.0); 100.0 for the first stage
     */
    private double conversionRate;
}
