package com.smartats.module.application.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI match scoring result DTO
 */
@Data
public class MatchScoreResponse {

    /** Application ID */
    private Long applicationId;

    /** Job ID */
    private Long jobId;

    /** Candidate ID */
    private Long candidateId;

    /** Overall match score (0-100) */
    private BigDecimal totalScore;

    /** Per-dimension score breakdown */
    private ScoreBreakdown breakdown;

    /** AI match reason list */
    private List<String> matchReasons;

    /** Match calculation time */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime calculatedAt;

    /**
     * Per-dimension score breakdown
     */
    @Data
    public static class ScoreBreakdown {
        /** Vector semantic similarity score (0-100, weight 30%) */
        private BigDecimal semanticScore;
        /** Skill match score (0-100, weight 35%) */
        private BigDecimal skillScore;
        /** Experience match score (0-100, weight 20%) */
        private BigDecimal experienceScore;
        /** Education match score (0-100, weight 15%) */
        private BigDecimal educationScore;
    }
}