package com.smartats.module.resume.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumeUploadResponse {

    /**
     * Task ID (used by the frontend to poll status)
     */
    private String taskId;

    /**
     * Resume ID
     */
    private Long resumeId;

    /**
     * Whether the file already exists (deduplication)
     */
    private Boolean duplicated;

    /**
     * Hint message
     */
    private String message;
}
