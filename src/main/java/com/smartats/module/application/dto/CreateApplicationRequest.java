package com.smartats.module.application.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Create job application request DTO
 */
@Data
public class CreateApplicationRequest {

    /**
     * Job ID
     */
    @NotNull(message = "Job ID must not be empty")
    private Long jobId;

    /**
     * Candidate ID
     */
    @NotNull(message = "Candidate ID must not be empty")
    private Long candidateId;

    /**
     * HR notes (optional)
     */
    private String hrNotes;
}