package com.smartats.module.analytics.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

/**
 * Analytics query request parameters
 * <p>
 * Supports filtering by job ID and date range.
 * All fields are optional; omitted fields query across all data.
 */
@Data
@Schema(description = "Recruitment analytics query parameters")
public class AnalyticsQueryRequest {

    @Schema(description = "Job ID (filters analytics data to a specific job)")
    private Long jobId;

    @Schema(description = "Start date (inclusive)", example = "2026-01-01")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @Schema(description = "End date (inclusive)", example = "2026-12-31")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;
}
