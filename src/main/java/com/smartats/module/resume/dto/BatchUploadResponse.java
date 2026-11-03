package com.smartats.module.resume.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Batch resume upload response
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchUploadResponse {

    /** Total number of files */
    private int totalCount;

    /** Success count */
    private int successCount;

    /** Failed count */
    private int failedCount;

    /** Processing result for each file */
    private List<BatchUploadItem> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchUploadItem {
        /** Task ID (present on success) */
        private String taskId;

        /** Resume ID */
        private Long resumeId;

        /** Original file name */
        private String fileName;

        /** Status: QUEUED / DUPLICATE / FAILED */
        private String status;

        /** Description message */
        private String message;
    }
}
