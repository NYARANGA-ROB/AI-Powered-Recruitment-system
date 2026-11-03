package com.smartats.module.resume.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumeParseMessage {

    /**
     * Task ID
     */
    private String taskId;

    /**
     * Resume ID
     */
    private Long resumeId;

    /**
     * User ID
     */
    private Long userId;

    /**
     * File hash
     */
    private String fileHash;

    /**
     * Retry count
     */
    private Integer retryCount = 0;
}
