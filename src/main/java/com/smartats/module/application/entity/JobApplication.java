package com.smartats.module.application.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Job application entity
 * <p>
 * Maps to the job_applications database table and bridges candidates with jobs.
 * Status flow: PENDING → SCREENING → INTERVIEW → OFFER / REJECTED / WITHDRAWN
 */
@Data
@TableName("job_applications")
public class JobApplication {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * Job ID (references the jobs table)
     */
    private Long jobId;

    /**
     * Candidate ID (references the candidates table)
     */
    private Long candidateId;

    /**
     * AI match score (0-100)
     */
    private BigDecimal matchScore;

    /**
     * AI match reasons (JSON format)
     */
    private String matchReasons;

    /**
     * Match calculation time
     */
    private LocalDateTime matchCalculatedAt;

    /**
     * Application status: PENDING / SCREENING / INTERVIEW / OFFER / REJECTED / WITHDRAWN
     */
    private String status;

    /**
     * HR notes
     */
    private String hrNotes;

    /**
     * Applied at
     */
    private LocalDateTime appliedAt;

    /**
     * Updated at
     */
    private LocalDateTime updatedAt;
}