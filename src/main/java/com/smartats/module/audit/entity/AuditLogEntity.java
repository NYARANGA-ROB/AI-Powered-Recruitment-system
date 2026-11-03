package com.smartats.module.audit.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Audit log entity
 * <p>
 * Records all key business operations in the system for security auditing, issue tracing and compliance requirements.
 */
@Data
@TableName("audit_logs")
public class AuditLogEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** Operator user ID (null when unauthenticated) */
    private Long userId;

    /** Operator username */
    private String username;

    /** Business module (e.g. Job Management, Resume Management) */
    private String module;

    /** Operation type (e.g. CREATE, UPDATE, DELETE) */
    private String operation;

    /** Operation description */
    private String description;

    /** Java method signature (Class.method) */
    private String method;

    /** Request URL */
    private String requestUrl;

    /** HTTP request method (GET / POST / PUT / DELETE) */
    private String requestMethod;

    /** Request parameters (stored as JSON, masked) */
    private String requestParams;

    /** Requesting IP address */
    private String requestIp;

    /** User agent (User-Agent) */
    private String userAgent;

    /** Operation result status: SUCCESS / FAILED */
    private String status;

    /** Error message on failure */
    private String errorMessage;

    /** Method execution duration (milliseconds) */
    private Long duration;

    /** Operation time */
    private LocalDateTime createdAt;
}
