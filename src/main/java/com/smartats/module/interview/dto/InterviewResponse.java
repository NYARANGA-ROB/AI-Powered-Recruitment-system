package com.smartats.module.interview.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Interview record response DTO
 */
@Data
public class InterviewResponse {

    private Long id;

    // ========== Related information ==========

    private Long applicationId;

    /** Interviewer user ID */
    private Long interviewerId;

    /** Interviewer name (resolved relationally) */
    private String interviewerName;

    // ========== Interview scheduling ==========

    /** Interview round */
    private Integer round;

    /** Interview type: PHONE / VIDEO / ONSITE / WRITTEN_TEST */
    private String interviewType;

    /** Human-readable interview type description */
    private String interviewTypeDesc;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime scheduledAt;

    /** Interview duration (minutes) */
    private Integer durationMinutes;

    // ========== Interview status and outcome ==========

    /** Interview status: SCHEDULED / COMPLETED / CANCELLED / NO_SHOW */
    private String status;

    /** Human-readable status description */
    private String statusDesc;

    /** Interview feedback comments */
    private String feedback;

    /** Interview score (1-10) */
    private Integer score;

    /** Recommendation */
    private String recommendation;

    /** Human-readable recommendation description */
    private String recommendationDesc;

    // ========== Metadata ==========

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
