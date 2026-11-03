package com.smartats.module.candidate.controller;

import com.smartats.common.result.Result;
import com.smartats.module.candidate.dto.SmartSearchRequest;
import com.smartats.module.candidate.dto.SmartSearchResponse;
import com.smartats.module.candidate.service.SmartSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Candidate intelligent semantic search API
 * <p>
 * Based on the Milvus vector database and the Zhipu embedding-3 model,
 * supporting candidate matching through natural language queries.
 */
@Slf4j
@RestController
@RequestMapping("/candidates")
@RequiredArgsConstructor
@Tag(name = "Candidate Smart Search", description = "Vector-database-based semantic search")
public class SmartSearchController {

    private final SmartSearchService smartSearchService;

    /**
     * Semantic candidate search
     * <p>
     * Search for the best-matching candidates using a natural language query.
     * The system converts the query text into a vector and runs an ANN approximate search in Milvus,
     * returning candidates sorted by similarity.
     * <p>
     * Example queries:
     * - "3+ years of Java backend development, familiar with Spring Boot and microservice architecture"
     * - "Front-end engineer from a major tech company, proficient in React and TypeScript"
     * - "Master's in computer science with machine learning and deep learning project experience"
     */
    @PostMapping("/smart-search")
    @Operation(summary = "Semantic candidate search", description = "Search for matching candidates using a natural language query")
    public Result<SmartSearchResponse> smartSearch(@Valid @RequestBody SmartSearchRequest request) {
        log.info("Received semantic search request: query='{}', topK={}", request.getQuery(), request.getTopK());

        SmartSearchResponse response = smartSearchService.search(request);
        return Result.success(response);
    }
}
