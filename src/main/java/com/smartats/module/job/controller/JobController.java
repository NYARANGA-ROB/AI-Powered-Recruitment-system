package com.smartats.module.job.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartats.common.annotation.AuditLog;
import com.smartats.common.result.Result;
import com.smartats.module.job.dto.request.CreateJobRequest;
import com.smartats.module.job.dto.request.JobQueryRequest;
import com.smartats.module.job.dto.request.UpdateJobRequest;
import com.smartats.module.job.dto.response.JobResponse;
import com.smartats.module.job.service.JobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Job management controller
 */
@Tag(name = "Job Management", description = "Job CRUD, publish/close, hot ranking, view counting")
@RestController
@RequestMapping("/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    /**
     * Create a job
     * POST /api/v1/jobs
     */
    @Operation(summary = "Create job")
    @AuditLog(module = "Job Management", operation = "CREATE", description = "Create job")
    @PostMapping
    public Result<Long> createJob(@Valid @RequestBody CreateJobRequest request, Authentication authentication) {
        Long creatorId = (Long) authentication.getPrincipal();
        Long jobId = jobService.createJob(request, creatorId);
        return Result.success(jobId);
    }

    /**
     * Update a job
     * PUT /api/v1/jobs
     */
    @Operation(summary = "Update job")
    @AuditLog(module = "Job Management", operation = "UPDATE", description = "Update job")
    @PutMapping
    public Result<Void> updateJob(@Valid @RequestBody UpdateJobRequest request, Authentication authentication) {
        Long operatorId = (Long) authentication.getPrincipal();
        jobService.updateJob(request, operatorId);
        return Result.success();
    }

    /**
     * Get job detail
     * GET /api/v1/jobs/{id}
     */
    @Operation(summary = "Get job detail", description = "Redis cache first, 30-minute TTL")
    @GetMapping("/{id}")
    public Result<JobResponse> getJobDetail(@PathVariable Long id) {
        JobResponse response = jobService.getJobDetail(id);
        return Result.success(response);
    }

    /**
     * Job list
     * GET /api/v1/jobs
     */
    @Operation(summary = "Job list", description = "Supports pagination + multi-dimensional filtering")
    @GetMapping
    public Result<Page<JobResponse>> getJobList(JobQueryRequest request) {
        Page<JobResponse> page = jobService.getJobList(request);
        return Result.success(page);
    }

    /**
     * Publish a job
     * POST /api/v1/jobs/{id}/publish
     */
    @Operation(summary = "Publish job")
    @AuditLog(module = "Job Management", operation = "PUBLISH", description = "Publish job")
    @PostMapping("/{id}/publish")
    public Result<Void> publishJob(@PathVariable Long id, Authentication authentication) {
        Long operatorId = (Long) authentication.getPrincipal();
        jobService.publishJob(id, operatorId);
        return Result.success();
    }

    /**
     * Close a job
     * POST /api/v1/jobs/{id}/close
     */
    @Operation(summary = "Close job")
    @AuditLog(module = "Job Management", operation = "CLOSE", description = "Close job")
    @PostMapping("/{id}/close")
    public Result<Void> closeJob(@PathVariable Long id, Authentication authentication) {
        Long operatorId = (Long) authentication.getPrincipal();
        jobService.closeJob(id, operatorId);
        return Result.success();
    }

    /**
     * Delete a job
     * DELETE /api/v1/jobs/{id}
     */
    @Operation(summary = "Delete job")
    @AuditLog(module = "Job Management", operation = "DELETE", description = "Delete job")
    @DeleteMapping("/{id}")
    public Result<Void> deleteJob(@PathVariable Long id, Authentication authentication) {
        Long operatorId = (Long) authentication.getPrincipal();
        jobService.deleteJob(id, operatorId);
        return Result.success();
    }

    /**
     * Hot jobs
     * GET /api/v1/jobs/hot
     */
    @Operation(summary = "Hot jobs", description = "Redis ZSet leaderboard, 10-minute TTL")
    @GetMapping("/hot")
    public Result<List<JobResponse>> getHotJobs(@Parameter(description = "Number of items to return") @RequestParam(defaultValue = "10") Integer limit) {
        List<JobResponse> jobs = jobService.getHotJobs(limit);
        return Result.success(jobs);
    }
}