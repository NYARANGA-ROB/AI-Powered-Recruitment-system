package com.smartats.module.application.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartats.common.annotation.AuditLog;
import com.smartats.common.result.Result;
import com.smartats.module.application.dto.*;
import com.smartats.module.application.service.JobApplicationService;
import com.smartats.module.application.service.MatchScoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Job Application Management", description = "Application creation, status transitions (PENDING→REVIEWING→INTERVIEW→OFFER/REJECTED), multi-dimensional queries")
@Slf4j
@RestController
@RequestMapping("/applications")
@RequiredArgsConstructor
public class JobApplicationController {

    private final JobApplicationService jobApplicationService;
    private final MatchScoreService matchScoreService;

    /**
     * Create a job application
     * POST /api/v1/applications
     */
    @Operation(summary = "Create job application", description = "Automatic de-duplication (the same candidate cannot apply to the same job twice)")
    @AuditLog(module = "Job Applications", operation = "CREATE", description = "Create job application")
    @PostMapping
    public Result<Long> createApplication(
            @Valid @RequestBody CreateApplicationRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        log.info("Creating job application: userId={}, jobId={}, candidateId={}",
                userId, request.getJobId(), request.getCandidateId());

        Long applicationId = jobApplicationService.createApplication(request);
        return Result.success(applicationId);
    }

    /**
     * Update application status
     * PUT /api/v1/applications/{id}/status
     */
    @Operation(summary = "Update application status", description = "Validates status transitions and triggers Webhook notifications")
    @AuditLog(module = "Job Applications", operation = "UPDATE_STATUS", description = "Update application status")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateApplicationStatusRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        log.info("Updating application status: userId={}, applicationId={}, targetStatus={}",
                userId, id, request.getStatus());

        jobApplicationService.updateStatus(id, request);
        return Result.success();
    }

    /**
     * Get application detail
     * GET /api/v1/applications/{id}
     */
    @Operation(summary = "Get application detail")
    @GetMapping("/{id}")
    public Result<ApplicationResponse> getById(@PathVariable Long id) {
        ApplicationResponse response = jobApplicationService.getById(id);
        return Result.success(response);
    }

    /**
     * List applications by job (HR view)
     * GET /api/v1/applications/job/{jobId}
     */
    @Operation(summary = "List applications by job")
    @GetMapping("/job/{jobId}")
    public Result<Page<ApplicationResponse>> listByJobId(
            @PathVariable Long jobId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<ApplicationResponse> page = jobApplicationService.listByJobId(jobId, pageNum, pageSize);
        return Result.success(page);
    }

    /**
     * List applications by candidate
     * GET /api/v1/applications/candidate/{candidateId}
     */
    @Operation(summary = "List applications by candidate")
    @GetMapping("/candidate/{candidateId}")
    public Result<Page<ApplicationResponse>> listByCandidateId(
            @PathVariable Long candidateId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<ApplicationResponse> page = jobApplicationService.listByCandidateId(candidateId, pageNum, pageSize);
        return Result.success(page);
    }

    /**
     * Comprehensive application query (supports multi-dimensional filtering + pagination + sorting)
     * GET /api/v1/applications
     */
    @Operation(summary = "Comprehensive application query", description = "Supports multi-dimensional filtering + pagination + sorting")
    @GetMapping
    public Result<Page<ApplicationResponse>> listApplications(ApplicationQueryRequest request) {
        Page<ApplicationResponse> page = jobApplicationService.listApplications(request);
        return Result.success(page);
    }

    /**
     * Calculate/recalculate the AI match score
     * POST /api/v1/applications/{id}/match-score
     */
    @Operation(summary = "Calculate AI match score", description = "Multi-dimensional scoring: vector semantics (30%) + skills (35%) + experience (20%) + education (15%)")
    @AuditLog(module = "Job Applications", operation = "MATCH_SCORE", description = "Calculate AI match score")
    @PostMapping("/{id}/match-score")
    public Result<MatchScoreResponse> calculateMatchScore(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        log.info("Calculating match score: userId={}, applicationId={}", userId, id);

        MatchScoreResponse response = matchScoreService.calculateAndSave(id);
        return Result.success(response);
    }
}
