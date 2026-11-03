package com.smartats.module.audit.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartats.common.result.Result;
import com.smartats.module.audit.dto.AuditLogQueryRequest;
import com.smartats.module.audit.dto.AuditLogResponse;
import com.smartats.module.audit.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * Audit log management API
 * <p>
 * Provides audit log querying; accessible to administrators only.
 */
@Tag(name = "Audit Logs", description = "Operational audit log queries (collected automatically via AOP)")
@Slf4j
@RestController
@RequestMapping("/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    /**
     * Query audit logs with pagination
     * <p>
     * Supports filtering by user, module, operation type, time range and keyword
     */
    @Operation(summary = "Query audit logs", description = "Multi-dimensional filtering: user, module, operation type, time range, keyword")
    @GetMapping
    public Result<Page<AuditLogResponse>> queryAuditLogs(AuditLogQueryRequest request) {
        log.info("Querying audit logs: module={}, operation={}, userId={}", request.getModule(),
                request.getOperation(), request.getUserId());
        Page<AuditLogResponse> page = auditLogService.queryAuditLogs(request);
        return Result.success(page);
    }
}
