package com.smartats.module.candidate.dto;

import lombok.Data;

import java.util.List;

/**
 * Semantic search response DTO
 */
@Data
public class SmartSearchResponse {

    /** Original query text */
    private String query;

    /** Number of matched candidates returned */
    private int totalMatches;

    /** List of matched candidates (sorted by similarity, descending) */
    private List<MatchedCandidate> candidates;

    /**
     * A single matched candidate result
     */
    @Data
    public static class MatchedCandidate {

        /** Candidate ID */
        private Long candidateId;

        /** Candidate name */
        private String name;

        /** Similarity score (0-1, COSINE) */
        private double matchScore;

        /** Current position */
        private String currentPosition;

        /** Current company */
        private String currentCompany;

        /** Highest education */
        private String education;

        /** Years of experience */
        private Integer workYears;

        /** Skill tags */
        private List<String> skills;

        /** AI-generated summary */
        private String aiSummary;
    }
}
