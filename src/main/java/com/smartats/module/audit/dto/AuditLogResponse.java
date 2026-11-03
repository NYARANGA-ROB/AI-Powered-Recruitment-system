package com.smartats.module.audit.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Audit log response DTO
 */
@Data
public class AuditLogResponse {

    private Long id;

    private Long userId;

    private String username;

    /** Business module */
    private String module;

    /** Operation type */
    private String operation;

    /** Operation description */
    private String description;

    /** Method signature */
    private String method;

    /** Request URL */
    private String requestUrl;

    /** HTTP method */
    private String requestMethod;

    /** Request parameters */
    private String requestParams;

    /** Request IP */
    private String requestIp;

    /** Operation result: SUCCESS / FAILED */
    private String status;

    /** Error message */
    private String errorMessage;

    /** Execution duration (ms) */
    private Long duration;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
