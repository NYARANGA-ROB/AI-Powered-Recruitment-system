package com.smartats.module.audit.dto;

import lombok.Data;

/**
 * Audit log query request DTO
 */
@Data
public class AuditLogQueryRequest {

    /** Operator user ID */
    private Long userId;

    /** Business module (exact match) */
    private String module;

    /** Operation type (exact match) */
    private String operation;

    /** Operation result: SUCCESS / FAILED */
    private String status;

    /** Keyword search (matches description) */
    private String keyword;

    /** Start time (format: yyyy-MM-dd HH:mm:ss) */
    private String startTime;

    /** End time (format: yyyy-MM-dd HH:mm:ss) */
    private String endTime;

    /** Page number (defaults to 1) */
    private Integer pageNum = 1;

    /** Page size (defaults to 20) */
    private Integer pageSize = 20;
}
