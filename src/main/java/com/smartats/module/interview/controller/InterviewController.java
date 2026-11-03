package com.smartats.module.interview.controller;

import com.smartats.common.annotation.AuditLog;
import com.smartats.common.result.Result;
import com.smartats.module.interview.dto.InterviewResponse;
import com.smartats.module.interview.dto.ScheduleInterviewRequest;
import com.smartats.module.interview.dto.SubmitFeedbackRequest;
import com.smartats.module.interview.service.InterviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Interview Management", description = "Interview scheduling, feedback submission, cancellation, multi-round queries")
@Slf4j
@RestController
@RequestMapping("/interviews")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;

    /**
     * Schedule interview
     * POST /api/v1/interviews
     */
    @Operation(summary = "Schedule interview", description = "Linked to a job application; triggers Webhook notifications")
    @AuditLog(module = "Interview Management", operation = "SCHEDULE", description = "Schedule interview")
    @PostMapping
    public Result<Long> scheduleInterview(
            @Valid @RequestBody ScheduleInterviewRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        log.info("Scheduling interview: userId={}, applicationId={}", userId, request.getApplicationId());

        Long interviewId = interviewService.scheduleInterview(request);
        return Result.success(interviewId);
    }

    /**
     * Submit interview feedback
     * PUT /api/v1/interviews/{id}/feedback
     */
    @Operation(summary = "Submit interview feedback", description = "Includes score, recommendation and review comments")
    @AuditLog(module = "Interview Management", operation = "FEEDBACK", description = "Submit interview feedback")
    @PutMapping("/{id}/feedback")
    public Result<Void> submitFeedback(
            @PathVariable Long id,
            @Valid @RequestBody SubmitFeedbackRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        log.info("Submitting interview feedback: userId={}, interviewId={}", userId, id);

        interviewService.submitFeedback(id, request);
        return Result.success();
    }

    /**
     * Cancel interview
     * POST /api/v1/interviews/{id}/cancel
     */
    @Operation(summary = "Cancel interview")
    @AuditLog(module = "Interview Management", operation = "CANCEL", description = "Cancel interview")
    @PostMapping("/{id}/cancel")
    public Result<Void> cancelInterview(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        log.info("Cancelling interview: userId={}, interviewId={}", userId, id);

        interviewService.cancelInterview(id);
        return Result.success();
    }

    /**
     * Get interview record details
     * GET /api/v1/interviews/{id}
     */
    @Operation(summary = "Get interview record details")
    @GetMapping("/{id}")
    public Result<InterviewResponse> getById(@PathVariable Long id) {
        InterviewResponse response = interviewService.getById(id);
        return Result.success(response);
    }

    /**
     * Query all interview rounds by application
     * GET /api/v1/interviews/application/{applicationId}
     */
    @Operation(summary = "Query all interview rounds by application")
    @GetMapping("/application/{applicationId}")
    public Result<List<InterviewResponse>> listByApplicationId(@PathVariable Long applicationId) {
        List<InterviewResponse> interviews = interviewService.listByApplicationId(applicationId);
        return Result.success(interviews);
    }
}
