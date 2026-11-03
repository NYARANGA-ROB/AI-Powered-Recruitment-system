package com.smartats.module.candidate.service;

import com.smartats.common.exception.BusinessException;
import com.smartats.common.result.ResultCode;
import com.smartats.infrastructure.vector.EmbeddingService;
import com.smartats.infrastructure.vector.VectorStoreService;
import com.smartats.infrastructure.vector.VectorStoreService.SearchResult;
import com.smartats.module.candidate.dto.SmartSearchRequest;
import com.smartats.module.candidate.dto.SmartSearchResponse;
import com.smartats.module.candidate.dto.SmartSearchResponse.MatchedCandidate;
import com.smartats.module.candidate.entity.Candidate;
import com.smartats.module.candidate.mapper.CandidateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Candidate semantic search service
 * <p>
 * Flow:
 * 1. Generate a vector from the query text via embedding-3
 * 2. Run an ANN search in Milvus to get the topK most similar candidates
 * 3. Filter by the similarity threshold
 * 4. Fetch candidate details from MySQL
 * 5. Assemble and return the response
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmartSearchService {

    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final CandidateMapper candidateMapper;

    /**
     * Execute the semantic search
     *
     * @param request search request
     * @return search results
     */
    public SmartSearchResponse search(SmartSearchRequest request) {
        log.info("Starting semantic search: query='{}', topK={}, minScore={}",
                request.getQuery(), request.getTopK(), request.getMinScore());

        // 1. Generate the query vector
        List<Float> queryEmbedding;
        try {
            queryEmbedding = embeddingService.generateQueryEmbedding(request.getQuery());
        } catch (Exception e) {
            log.error("Failed to vectorize query text: query='{}'", request.getQuery(), e);
            throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "AI vectorization service unavailable");
        }

        // 2. Milvus vector search
        List<SearchResult> searchResults;
        try {
            searchResults = vectorStoreService.search(queryEmbedding, request.getTopK());
        } catch (Exception e) {
            log.error("Milvus vector search failed", e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "Vector search service unavailable");
        }

        // 3. Filter by threshold
        double minScore = request.getMinScore() != null ? request.getMinScore() : 0.3;
        List<SearchResult> filteredResults = searchResults.stream()
                .filter(r -> r.getScore() >= minScore)
                .toList();

        log.info("Vector search results: total={}, filtered(minScore={})={}",
                searchResults.size(), minScore, filteredResults.size());

        if (filteredResults.isEmpty()) {
            return buildEmptyResponse(request.getQuery());
        }

        // 4. Batch-fetch candidate details (avoid N+1)
        List<Long> candidateIds = filteredResults.stream()
                .map(SearchResult::getCandidateId)
                .toList();

        List<Candidate> candidates = candidateMapper.selectBatchIds(candidateIds);
        Map<Long, Candidate> candidateMap = candidates.stream()
                .collect(Collectors.toMap(Candidate::getId, Function.identity()));

        // 5. Assemble the response (preserving similarity order)
        List<MatchedCandidate> matchedCandidates = new ArrayList<>();
        for (SearchResult sr : filteredResults) {
            Candidate candidate = candidateMap.get(sr.getCandidateId());
            if (candidate == null) {
                log.warn("Candidate not found in MySQL, possibly deleted: candidateId={}", sr.getCandidateId());
                continue;
            }

            MatchedCandidate mc = new MatchedCandidate();
            mc.setCandidateId(candidate.getId());
            mc.setName(candidate.getName());
            mc.setMatchScore(Math.round(sr.getScore() * 10000.0) / 10000.0);  // keep 4 decimal places
            mc.setCurrentPosition(candidate.getCurrentPosition());
            mc.setCurrentCompany(candidate.getCurrentCompany());
            mc.setEducation(candidate.getEducation());
            mc.setWorkYears(candidate.getWorkYears());
            mc.setSkills(candidate.getSkills());
            mc.setAiSummary(candidate.getAiSummary());
            matchedCandidates.add(mc);
        }

        SmartSearchResponse response = new SmartSearchResponse();
        response.setQuery(request.getQuery());
        response.setTotalMatches(matchedCandidates.size());
        response.setCandidates(matchedCandidates);

        log.info("Semantic search completed: query='{}', matches={}", request.getQuery(), matchedCandidates.size());
        return response;
    }

    private SmartSearchResponse buildEmptyResponse(String query) {
        SmartSearchResponse response = new SmartSearchResponse();
        response.setQuery(query);
        response.setTotalMatches(0);
        response.setCandidates(List.of());
        return response;
    }
}
