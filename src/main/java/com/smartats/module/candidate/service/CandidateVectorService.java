package com.smartats.module.candidate.service;

import com.smartats.infrastructure.vector.EmbeddingService;
import com.smartats.infrastructure.vector.VectorStoreService;
import com.smartats.module.candidate.entity.Candidate;
import com.smartats.module.candidate.mapper.CandidateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Candidate vectorization service
 * <p>
 * Responsibilities:
 * 1. After a candidate is created/updated, generate the embedding vector and store it in Milvus
 * 2. When a candidate is deleted, delete the vector in Milvus synchronously
 * 3. Update the vector_id and ai_summary fields of the candidates table
 * <p>
 * Vectorization flow:
 *   Candidate → buildCandidateText → embedding-3 → 1024-dimensional vector → Milvus upsert
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CandidateVectorService {

    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final CandidateMapper candidateMapper;

    /**
     * Generate a vector for the candidate and store it in Milvus (synchronous call, used within the parsing pipeline)
     *
     * @param candidate the persisted candidate entity
     */
    public void vectorizeCandidate(Candidate candidate) {
        log.info("Start vectorizing candidate: candidateId={}, name={}", candidate.getId(), candidate.getName());

        try {
            // 1. Build the summary text
            String aiSummary = embeddingService.buildCandidateText(candidate);

            // 2. Generate the embedding vector
            List<Float> embedding = embeddingService.generateCandidateEmbedding(candidate);

            // 3. Store it in Milvus
            String vectorId = vectorStoreService.upsertVector(
                    candidate.getId(),
                    candidate.getName(),
                    embedding
            );

            // 4. Write back to MySQL (update vector_id and ai_summary)
            Candidate update = new Candidate();
            update.setId(candidate.getId());
            update.setVectorId(vectorId);
            update.setAiSummary(aiSummary);
            update.setUpdatedAt(LocalDateTime.now());
            candidateMapper.updateById(update);

            log.info("Candidate vectorization completed: candidateId={}, vectorId={}", candidate.getId(), vectorId);

        } catch (Exception e) {
            // Vectorization failure must not block the main flow; log the error and skip
            log.error("Candidate vectorization failed (no impact on the main flow): candidateId={}", candidate.getId(), e);
        }
    }

    /**
     * Asynchronous vectorization (used when a candidate is updated manually)
     * <p>
     * ⚠ Note: @Async methods only take effect when called from a different bean (Spring AOP proxying).
     *
     * @param candidate the updated candidate entity
     */
    @Async("asyncExecutor")
    public void vectorizeCandidateAsync(Candidate candidate) {
        vectorizeCandidate(candidate);
    }

    /**
     * Delete the candidate vector (synchronous)
     *
     * @param candidateId candidate ID
     */
    public void deleteVector(Long candidateId) {
        try {
            vectorStoreService.deleteVector(candidateId);
            log.info("Candidate vector deleted successfully: candidateId={}", candidateId);
        } catch (Exception e) {
            log.error("Candidate vector deletion failed (no impact on the main flow): candidateId={}", candidateId, e);
        }
    }
}
