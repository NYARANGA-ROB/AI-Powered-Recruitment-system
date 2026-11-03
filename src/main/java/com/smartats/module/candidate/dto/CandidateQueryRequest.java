package com.smartats.module.candidate.dto;

import lombok.Data;

/**
 * Candidate paginated query request DTO
 * <p>
 * Supports keyword search plus multi-dimensional advanced filtering.
 */
@Data
public class CandidateQueryRequest {

    // ========== Pagination ==========
    /** Current page (starting from 1) */
    private int page = 1;

    /** Page size (max 100) */
    private int pageSize = 10;

    // ========== Common keyword ==========
    /**
     * Keyword (fuzzy match: name / email / company / position)
     */
    private String keyword;

    // ========== Exact / range filters ==========
    /**
     * Education filter (e.g. Bachelor, Master, PhD; matches the DB ENUM or string)
     * empty value means no filtering
     */
    private String education;

    /**
     * Skill filter (exact match for one skill in the JSON array, e.g. Java, Python)
     * Uses JSON_CONTAINS under the hood;empty value means no filtering
     */
    private String skill;

    /**
     * Minimum years of experience (inclusive); null means no limit
     */
    private Integer minWorkYears;

    /**
     * Maximum years of experience (inclusive); null means no limit
     */
    private Integer maxWorkYears;

    /**
     * Current position keyword (fuzzy match); empty means no filtering
     */
    private String currentPosition;
}
