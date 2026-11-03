package com.smartats.module.candidate.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartats.common.annotation.AuditLog;
import com.smartats.common.exception.BusinessException;
import com.smartats.common.result.Result;
import com.smartats.common.result.ResultCode;
import com.smartats.common.util.DataMaskUtil;
import com.smartats.module.candidate.dto.CandidateQueryRequest;
import com.smartats.module.candidate.dto.CandidateResponse;
import com.smartats.module.candidate.dto.CandidateUpdateRequest;
import com.smartats.module.candidate.entity.Candidate;
import com.smartats.module.candidate.service.CandidateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

/**
 * Candidate management API
 */
@Tag(name = "Candidate Management", description = "CRUD, advanced filtering, data masking, semantic search")
@Slf4j
@RestController
@RequestMapping("/candidates")
@RequiredArgsConstructor
public class CandidateController {

    private final CandidateService candidateService;

    /**
     * Query a candidate by resumeId
     */
    @Operation(summary = "Query candidate by resume ID")
    @GetMapping("/resume/{resumeId}")
    public Result<CandidateResponse> getByResumeId(@PathVariable Long resumeId) {
        log.info("Querying candidate: resumeId={}", resumeId);

        Candidate candidate = candidateService.getByResumeId(resumeId);
        if (candidate == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Candidate not found");
        }

        CandidateResponse response = toResponse(candidate);
        return Result.success(response);
    }

    /**
     * Query candidate details by ID
     */
    @Operation(summary = "Query candidate details", description = "Redis cache first, 30-minute TTL")
    @GetMapping("/{id}")
    public Result<CandidateResponse> getById(@PathVariable Long id) {
        log.info("Querying candidate details: id={}", id);

        Candidate candidate = candidateService.getById(id);
        if (candidate == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Candidate not found");
        }

        CandidateResponse response = toResponse(candidate);
        return Result.success(response);
    }

    /**
     * Update candidate information (update logic delegated to the service layer)
     */
    @Operation(summary = "Update candidate information", description = "Only non-null fields are updated; re-vectorization runs asynchronously")
    @AuditLog(module = "Candidate Management", operation = "UPDATE", description = "Update candidate information")
    @PutMapping("/{id}")
    public Result<CandidateResponse> updateCandidate(
            @PathVariable Long id,
            @Valid @RequestBody CandidateUpdateRequest request) {
        log.info("Updating candidate information: id={}", id);

        Candidate updated = candidateService.updateManual(id, request);
        CandidateResponse response = toResponse(updated);
        return Result.success(response);
    }

    /**
     * Delete a candidate
     */
    @Operation(summary = "Delete candidate", description = "Deletes the Milvus vector and the MySQL record synchronously")
    @AuditLog(module = "Candidate Management", operation = "DELETE", description = "Delete candidate")
    @DeleteMapping("/{id}")
    public Result<Void> deleteCandidate(@PathVariable Long id) {
        log.info("Deleting candidate: id={}", id);

        Candidate candidate = candidateService.getById(id);
        if (candidate == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Candidate not found");
        }

        candidateService.deleteById(id);
        return Result.success(null);
    }

    /**
     * Paginated candidate list query with multi-dimensional filtering
     */
    @Operation(summary = "Candidate list", description = "Supports multi-dimensional filtering by keyword, education, skills, years of experience and more")
    @GetMapping
    public Result<IPage<CandidateResponse>> listCandidates(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String education,
            @RequestParam(required = false) String skill,
            @RequestParam(required = false) Integer minWorkYears,
            @RequestParam(required = false) Integer maxWorkYears,
            @RequestParam(required = false) String currentPosition) {
        log.info("Querying candidate list: page={}, pageSize={}, keyword={}, education={}, skill={}",
                page, pageSize, keyword, education, skill);

        CandidateQueryRequest request = new CandidateQueryRequest();
        request.setPage(page);
        request.setPageSize(pageSize);
        request.setKeyword(keyword);
        request.setEducation(education);
        request.setSkill(skill);
        request.setMinWorkYears(minWorkYears);
        request.setMaxWorkYears(maxWorkYears);
        request.setCurrentPosition(currentPosition);

        Page<Candidate> result = candidateService.listCandidates(request);

        Page<CandidateResponse> responsePage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        responsePage.setRecords(result.getRecords().stream().map(this::toResponse).toList());

        return Result.success(responsePage);
    }

    /**
     * Convert the Candidate entity to an API response and mask sensitive fields
     */
    private CandidateResponse toResponse(Candidate candidate) {
        CandidateResponse response = new CandidateResponse();
        BeanUtils.copyProperties(candidate, response);
        response.setPhone(DataMaskUtil.maskPhone(candidate.getPhone()));
        response.setEmail(DataMaskUtil.maskEmail(candidate.getEmail()));
        return response;
    }
}
