package com.smartats.module.candidate.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Candidate response DTO
 * <p>
 * Phone numbers and emails are masked; raw sensitive data is never exposed.
 */
@Data
public class CandidateResponse {

    private Long id;
    private Long resumeId;

    // ========== Basic information ==========
    private String name;

    /** Masked phone number, e.g. 138****5678 */
    private String phone;

    /** Masked email, e.g. zh****@example.com */
    private String email;

    private String gender;
    private Integer age;

    // ========== Education information ==========
    private String education;
    private String school;
    private String major;
    private Integer graduationYear;

    // ========== Work information ==========
    private Integer workYears;
    private String currentCompany;
    private String currentPosition;

    // ========== Skills and experience ==========
    private List<String> skills;
    private List<Map<String, Object>> workExperience;
    private List<Map<String, Object>> projectExperience;
    private String selfEvaluation;

    /** AI parsing confidence (0-1) */
    private Double confidenceScore;

    /** AI-generated candidate summary */
    private String aiSummary;

    // ========== Metadata ==========
    private LocalDateTime parsedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

