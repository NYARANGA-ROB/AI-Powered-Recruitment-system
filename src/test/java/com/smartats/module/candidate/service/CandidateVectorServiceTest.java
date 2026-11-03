package com.smartats.module.candidate.service;

import com.smartats.infrastructure.vector.EmbeddingService;
import com.smartats.infrastructure.vector.VectorStoreService;
import com.smartats.module.candidate.entity.Candidate;
import com.smartats.module.candidate.mapper.CandidateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * CandidateVectorService unit tests
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CandidateVectorService unit tests")
class CandidateVectorServiceTest {

    @InjectMocks
    private CandidateVectorService candidateVectorService;

    @Mock
    private EmbeddingService embeddingService;
    @Mock
    private VectorStoreService vectorStoreService;
    @Mock
    private CandidateMapper candidateMapper;

    private Candidate testCandidate;
    private List<Float> mockEmbedding;

    @BeforeEach
    void setUp() {
        testCandidate = new Candidate();
        testCandidate.setId(1L);
        testCandidate.setName("Zhang San");

        mockEmbedding = new ArrayList<>(Collections.nCopies(1024, 0.1f));
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // vectorizeCandidate tests
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("vectorizeCandidate")
    class VectorizeCandidateTests {

        @Test
        @DisplayName("Vectorizes successfully and writes back to MySQL")
        void shouldVectorizeAndUpdateMySQL() {
            // Given
            given(embeddingService.buildCandidateText(any(Candidate.class)))
                    .willReturn("Name: Zhang San\nEducation: Bachelor");
            given(embeddingService.generateCandidateEmbedding(any(Candidate.class)))
                    .willReturn(mockEmbedding);
            given(vectorStoreService.upsertVector(eq(1L), eq("Zhang San"), anyList()))
                    .willReturn("1");

            // When
            candidateVectorService.vectorizeCandidate(testCandidate);

            // Then
            then(embeddingService).should().buildCandidateText(testCandidate);
            then(embeddingService).should().generateCandidateEmbedding(testCandidate);
            then(vectorStoreService).should().upsertVector(eq(1L), eq("Zhang San"), eq(mockEmbedding));

            // verify the write-back to MySQL
            ArgumentCaptor<Candidate> captor = ArgumentCaptor.forClass(Candidate.class);
            then(candidateMapper).should().updateById(captor.capture());

            Candidate updated = captor.getValue();
            assertThat(updated.getId()).isEqualTo(1L);
            assertThat(updated.getVectorId()).isEqualTo("1");
            assertThat(updated.getAiSummary()).isEqualTo("Name: Zhang San\nEducation: Bachelor");
            assertThat(updated.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("Vectorization failure does not throw (fault tolerance)")
        void shouldNotThrowWhenVectorizationFails() {
            // Given
            given(embeddingService.buildCandidateText(any(Candidate.class)))
                    .willReturn("Zhang San");
            given(embeddingService.generateCandidateEmbedding(any(Candidate.class)))
                    .willThrow(new RuntimeException("Embedding service unavailable"));

            // When & Then - does not throw
            assertThatCode(() -> candidateVectorService.vectorizeCandidate(testCandidate))
                    .doesNotThrowAnyException();

            // Milvus and MySQL must not be touched
            then(vectorStoreService).shouldHaveNoInteractions();
            then(candidateMapper).shouldHaveNoInteractions();
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // deleteVector tests
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("deleteVector")
    class DeleteVectorTests {

        @Test
        @DisplayName("Deletes the vector successfully")
        void shouldDeleteVector() {
            candidateVectorService.deleteVector(1L);

            then(vectorStoreService).should().deleteVector(1L);
        }

        @Test
        @DisplayName("Delete failure does not throw (fault tolerance)")
        void shouldNotThrowWhenDeleteFails() {
            willThrow(new RuntimeException("Connection failed"))
                    .given(vectorStoreService).deleteVector(anyLong());

            assertThatCode(() -> candidateVectorService.deleteVector(1L))
                    .doesNotThrowAnyException();
        }
    }
}
