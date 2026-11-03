package com.smartats.module.application.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Job application response DTO
 */
@Data
public class ApplicationResponse {

    private Long id;

    // ========== Related information ==========

    private Long jobId;

    /** Job title (convenient for front-end display, avoids a second request) */
    private String jobTitle;

    private Long candidateId;

    /** Candidate name (convenient for front-end display) */
    private String candidateName;

    // ========== AI match information ==========

    /** AI match score (0-100) */
    private BigDecimal matchScore;

    /** AI match reasons */
    private List<String> matchReasons;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime matchCalculatedAt;

    // ========== Status and notes ==========

    /** Application status: PENDING / SCREENING / INTERVIEW / OFFER / REJECTED / WITHDRAWN */
    private String status;

    /** Status description */
    private String statusDesc;

    /** HR notes */
    private String hrNotes;

    // ========== Metadata ==========

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime appliedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}