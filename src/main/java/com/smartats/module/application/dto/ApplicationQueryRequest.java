package com.smartats.module.application.dto;

import lombok.Data;

/**
 * Job application paged query request DTO
 */
@Data
public class ApplicationQueryRequest {

    // ========== Pagination ==========

    /** Current page (starts from 1) */
    private Integer pageNum = 1;

    /** Page size (max 100) */
    private Integer pageSize = 10;

    // ========== Filters ==========

    /**
     * Job ID (HR view: see all applications for a given job)
     */
    private Long jobId;

    /**
     * Candidate ID (see all applications of a given candidate)
     */
    private Long candidateId;

    /**
     * Application status filter (e.g.: PENDING / SCREENING / INTERVIEW / OFFER / REJECTED / WITHDRAWN)
     */
    private String status;

    // ========== Sorting ==========

    /**
     * Sort field: applied_at (default), match_score, updated_at
     */
    private String orderBy = "applied_at";

    /**
     * Sort direction: asc / desc (default desc)
     */
    private String orderDirection = "desc";
}