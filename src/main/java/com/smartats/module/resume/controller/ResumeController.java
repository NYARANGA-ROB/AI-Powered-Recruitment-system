package com.smartats.module.resume.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartats.common.annotation.AuditLog;
import com.smartats.common.result.Result;
import com.smartats.module.resume.dto.BatchUploadResponse;
import com.smartats.module.resume.dto.ResumeUploadResponse;
import com.smartats.module.resume.dto.TaskStatusResponse;
import com.smartats.module.resume.entity.Resume;
import com.smartats.module.resume.service.ResumeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Resume upload controller
 */
@Tag(name = "Resume Management", description = "Resume upload, AI parsing task status queries, resume details")
@Slf4j
@RestController
@RequestMapping("/resumes")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;

    /**
     * Upload a resume
     *
     * @param file          resume file
     * @param authentication Spring Security authentication info (auto-injected)
     * @return upload result, containing the taskId used to poll the parsing status
     */
    @Operation(summary = "Upload resume", description = "Supports PDF/DOC/DOCX, ≤ 10MB, MD5 de-duplication, asynchronous AI parsing")
    @AuditLog(module = "Resume Management", operation = "UPLOAD", description = "Upload resume")
    @PostMapping("/upload")
    public Result<ResumeUploadResponse> uploadResume(
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        // Get userId from the SecurityContext (already parsed by the JWT filter)
        Long userId = (Long) authentication.getPrincipal();

        log.info("Received resume upload request: userId={}, fileName={}, size={}",
                userId, file.getOriginalFilename(), file.getSize());

        ResumeUploadResponse response = resumeService.uploadResume(file, userId);

        return Result.success(response);
    }

    /**
     * Batch upload resumes
     *
     * @param files          array of resume files (at most 20)
     * @param authentication Spring Security authentication info
     * @return batch upload result, containing the processing status of each file
     */
    @Operation(summary = "Batch upload resumes", description = "Supports up to 20 files, limited to 5 uploads per minute, a single failure does not affect the others")
    @AuditLog(module = "Resume Management", operation = "BATCH_UPLOAD", description = "Batch upload resumes")
    @PostMapping("/batch-upload")
    public Result<BatchUploadResponse> batchUploadResumes(
            @RequestParam("files") MultipartFile[] files,
            Authentication authentication
    ) {
        Long userId = (Long) authentication.getPrincipal();
        log.info("Received batch resume upload request: userId={}, fileCount={}", userId, files.length);
        BatchUploadResponse response = resumeService.batchUploadResumes(files, userId);
        return Result.success(response);
    }

    /**
     * Query task status
     *
     * @param taskId task ID
     * @return task status, including parsing progress, results, etc.
     */
    @Operation(summary = "Query parsing task status", description = "Poll the parsing progress with the taskId returned at upload time")
    @GetMapping("/tasks/{taskId}")
    public Result<TaskStatusResponse> getTaskStatus(@PathVariable String taskId) {
        log.debug("Querying task status: taskId={}", taskId);

        TaskStatusResponse response = resumeService.getTaskStatus(taskId);

        return Result.success(response);
    }

    /**
     * Get resume detail
     *
     * @param id             resume ID
     * @param authentication logged-in user (can only view their own resumes)
     * @return resume detail
     */
    @Operation(summary = "Get resume detail", description = "Only the current user's resumes can be viewed")
    @GetMapping("/{id}")
    public Result<Resume> getResumeById(
            @PathVariable Long id,
            Authentication authentication
    ) {
        Long userId = (Long) authentication.getPrincipal();
        Resume resume = resumeService.getResumeById(id, userId);
        return Result.success(resume);
    }

    /**
     * Paged query of the resume list (the current user's resumes)
     *
     * @param page           page number (default 1)
     * @param size           page size (default 10)
     * @param authentication logged-in user
     * @return paged resume list
     */
    @Operation(summary = "Resume list", description = "Paged list of the current user's resumes")
    @GetMapping
    public Result<Page<Resume>> listResumes(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication
    ) {
        Long userId = (Long) authentication.getPrincipal();
        Page<Resume> result = resumeService.listResumes(userId, page, size);
        return Result.success(result);
    }
}