package com.smartats.module.candidate.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Semantic search request DTO
 * <p>
 * Used by the POST /candidates/smart-search endpoint.
 * Supports natural language queries, e.g. "3+ years of Java backend experience, familiar with Spring Boot and microservices".
 */
@Data
public class SmartSearchRequest {

    /**
     * Search query text (natural language description)
     * <p>
     * Examples:
     * - "3+ years of Java backend development, familiar with Spring Boot"
     * - "Fresh graduate with an internship at a major tech company, knows Python and machine learning"
     * - "Senior front-end engineer, React + TypeScript"
     */
    @NotBlank(message = "Search query must not be blank")
    @Size(max = 1000, message = "Search query text must be at most 1000 characters")
    private String query;

    /**
     * Number of most similar candidates to return (defaults to 10, max 50)
     */
    @Min(value = 1, message = "topK must be at least 1")
    @Max(value = 50, message = "topK must be at most 50")
    private Integer topK = 10;

    /**
     * Minimum similarity threshold (0-1; results below this score are filtered out)
     * <p>
     * COSINE similarity: 0 = completely irrelevant, 1 = exact match.
     * Defaults to 0.3 to filter out clearly irrelevant results.
     */
    @Min(value = 0, message = "Minimum score must not be less than 0")
    @Max(value = 1, message = "Minimum score must not be greater than 1")
    private Double minScore = 0.3;
}
