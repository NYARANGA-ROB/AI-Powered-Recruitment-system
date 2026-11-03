package com.smartats.module.resume.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskStatusResponse {

    /**
     * Task status
     */
    private String status;

    /**
     * Resume ID (present after parsing completes)
     */
    private Long resumeId;

    /**
     * Candidate ID (present after parsing completes)
     */
    private Long candidateId;

    /**
     * Error message (present on failure)
     */
    private String errorMessage;

    /**
     * Progress percentage (0-100)
     */
    private Integer progress;

    /**
     * Current retry count (present in RETRYING status)
     */
    private Integer retryCount;

    /**
     * Maximum retry count
     */
    private Integer maxRetries;
}
