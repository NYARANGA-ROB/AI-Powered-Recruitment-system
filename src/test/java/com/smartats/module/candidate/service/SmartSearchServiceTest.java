package com.smartats.module.candidate.service;

import com.smartats.common.exception.BusinessException;
import com.smartats.infrastructure.vector.EmbeddingService;
import com.smartats.infrastructure.vector.VectorStoreService;
import com.smartats.infrastructure.vector.VectorStoreService.SearchResult;
import com.smartats.module.candidate.dto.SmartSearchRequest;
import com.smartats.module.candidate.dto.SmartSearchResponse;
import com.smartats.module.candidate.entity.Candidate;
import com.smartats.module.candidate.mapper.CandidateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
 * SmartSearchService unit tests
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SmartSearchService unit tests")
class SmartSearchServiceTest {

    @InjectMocks
    private SmartSearchService smartSearchService;

    @Mock
    private EmbeddingService embeddingService;
    @Mock
    private VectorStoreService vectorStoreService;
    @Mock
    private CandidateMapper candidateMapper;

    private SmartSearchRequest request;
    private List<Float> mockEmbedding;

    @BeforeEach
    void setUp() {
        request = new SmartSearchRequest();
        request.setQuery("3+ years of Java backend development");
        request.setTopK(10);
        request.setMinScore(0.3);

        mockEmbedding = new ArrayList<>(Collections.nCopies(1024, 0.1f));
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Normal search flow
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("Normal search flow")
    class NormalSearchTests {

        @Test
        @DisplayName("Returns matched candidates successfully")
        void shouldReturnMatchedCandidates() {
            // Given
            given(embeddingService.generateQueryEmbedding(anyString())).willReturn(mockEmbedding);

            SearchResult sr1 = new SearchResult();
            sr1.setCandidateId(1L);
            sr1.setCandidateName("Zhang San");
            sr1.setScore(0.85f);

            SearchResult sr2 = new SearchResult();
            sr2.setCandidateId(2L);
            sr2.setCandidateName("Li Si");
            sr2.setScore(0.72f);

            given(vectorStoreService.search(anyList(), eq(10))).willReturn(List.of(sr1, sr2));

            Candidate c1 = buildCandidate(1L, "Zhang San", "Senior backend engineer", "Tencent", 5);
            Candidate c2 = buildCandidate(2L, "Li Si", "Backend engineer", "Alibaba", 3);
            given(candidateMapper.selectBatchIds(anyList())).willReturn(List.of(c1, c2));

            // When
            SmartSearchResponse response = smartSearchService.search(request);

            // Then
            assertThat(response.getQuery()).isEqualTo("3+ years of Java backend development");
            assertThat(response.getTotalMatches()).isEqualTo(2);
            assertThat(response.getCandidates()).hasSize(2);
            assertThat(response.getCandidates().get(0).getCandidateId()).isEqualTo(1L);
            assertThat(response.getCandidates().get(0).getMatchScore()).isGreaterThan(0.8);
            assertThat(response.getCandidates().get(1).getCandidateId()).isEqualTo(2L);
        }

        @Test
        @DisplayName("Filters out low-scoring results by similarity threshold")
        void shouldFilterByMinScore() {
            // Given
            given(embeddingService.generateQueryEmbedding(anyString())).willReturn(mockEmbedding);

            SearchResult sr1 = new SearchResult();
            sr1.setCandidateId(1L);
            sr1.setCandidateName("Zhang San");
            sr1.setScore(0.85f);

            SearchResult sr2 = new SearchResult();
            sr2.setCandidateId(2L);
            sr2.setCandidateName("Li Si");
            sr2.setScore(0.15f);  // below the threshold

            given(vectorStoreService.search(anyList(), eq(10))).willReturn(List.of(sr1, sr2));

            Candidate c1 = buildCandidate(1L, "Zhang San", "Backend", "Tencent", 5);
            given(candidateMapper.selectBatchIds(anyList())).willReturn(List.of(c1));

            // When
            SmartSearchResponse response = smartSearchService.search(request);

            // Then
            assertThat(response.getTotalMatches()).isEqualTo(1);
            assertThat(response.getCandidates()).hasSize(1);
            assertThat(response.getCandidates().get(0).getCandidateId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("Skips candidates missing from MySQL")
        void shouldSkipMissingCandidates() {
            // Given
            given(embeddingService.generateQueryEmbedding(anyString())).willReturn(mockEmbedding);

            SearchResult sr1 = new SearchResult();
            sr1.setCandidateId(1L);
            sr1.setCandidateName("Zhang San");
            sr1.setScore(0.85f);

            SearchResult sr2 = new SearchResult();
            sr2.setCandidateId(999L);
            sr2.setCandidateName("Deleted");
            sr2.setScore(0.75f);

            given(vectorStoreService.search(anyList(), eq(10))).willReturn(List.of(sr1, sr2));

            // returns only candidateId=1, not 999
            Candidate c1 = buildCandidate(1L, "Zhang San", "Backend", "Tencent", 5);
            given(candidateMapper.selectBatchIds(anyList())).willReturn(List.of(c1));

            // When
            SmartSearchResponse response = smartSearchService.search(request);

            // Then
            assertThat(response.getTotalMatches()).isEqualTo(1);
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Empty result scenarios
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("Empty result scenarios")
    class EmptyResultTests {

        @Test
        @DisplayName("No matching results from Milvus")
        void shouldReturnEmptyWhenNoMilvusResults() {
            given(embeddingService.generateQueryEmbedding(anyString())).willReturn(mockEmbedding);
            given(vectorStoreService.search(anyList(), eq(10))).willReturn(List.of());

            SmartSearchResponse response = smartSearchService.search(request);

            assertThat(response.getTotalMatches()).isZero();
            assertThat(response.getCandidates()).isEmpty();
        }

        @Test
        @DisplayName("All results below the threshold")
        void shouldReturnEmptyWhenAllBelowThreshold() {
            given(embeddingService.generateQueryEmbedding(anyString())).willReturn(mockEmbedding);

            SearchResult sr = new SearchResult();
            sr.setCandidateId(1L);
            sr.setCandidateName("Zhang San");
            sr.setScore(0.1f);  // below the default 0.3

            given(vectorStoreService.search(anyList(), eq(10))).willReturn(List.of(sr));

            SmartSearchResponse response = smartSearchService.search(request);

            assertThat(response.getTotalMatches()).isZero();
            assertThat(response.getCandidates()).isEmpty();
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Exception scenarios
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("Exception scenarios")
    class ExceptionTests {

        @Test
        @DisplayName("Embedding service failure throws BusinessException")
        void shouldThrowWhenEmbeddingFails() {
            given(embeddingService.generateQueryEmbedding(anyString()))
                    .willThrow(new RuntimeException("API timeout"));

            assertThatThrownBy(() -> smartSearchService.search(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("AI vectorization service unavailable");
        }

        @Test
        @DisplayName("Milvus search failure throws BusinessException")
        void shouldThrowWhenMilvusSearchFails() {
            given(embeddingService.generateQueryEmbedding(anyString())).willReturn(mockEmbedding);
            given(vectorStoreService.search(anyList(), eq(10)))
                    .willThrow(new RuntimeException("Connection refused"));

            assertThatThrownBy(() -> smartSearchService.search(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Vector search service unavailable");
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Helper
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private Candidate buildCandidate(Long id, String name, String position, String company, int workYears) {
        Candidate c = new Candidate();
        c.setId(id);
        c.setName(name);
        c.setCurrentPosition(position);
        c.setCurrentCompany(company);
        c.setWorkYears(workYears);
        c.setSkills(List.of("Java", "Spring Boot"));
        return c;
    }
}
