package com.smartats.infrastructure.vector;

import com.smartats.module.candidate.entity.Candidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.Embedding;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

/**
 * EmbeddingService unit tests
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmbeddingService unit tests")
class EmbeddingServiceTest {

    @InjectMocks
    private EmbeddingService embeddingService;

    @Mock
    private EmbeddingModel embeddingModel;

    private Candidate testCandidate;

    @BeforeEach
    void setUp() {
        testCandidate = new Candidate();
        testCandidate.setId(1L);
        testCandidate.setName("John Smith");
        testCandidate.setGender("MALE");
        testCandidate.setEducation("Bachelor");
        testCandidate.setSchool("Peking University");
        testCandidate.setMajor("Computer Science");
        testCandidate.setWorkYears(5);
        testCandidate.setCurrentCompany("Tencent");
        testCandidate.setCurrentPosition("Senior Backend Engineer");
        testCandidate.setSkills(List.of("Java", "Spring Boot", "MySQL", "Redis"));
        testCandidate.setSelfEvaluation("5 years of backend development experience, skilled in high-concurrency system design");
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // buildCandidateText tests
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("buildCandidateText")
    class BuildCandidateTextTests {

        @Test
        @DisplayName("includes every non-empty field")
        void shouldIncludeAllNonNullFields() {
            String text = embeddingService.buildCandidateText(testCandidate);

            assertThat(text)
                    .contains("Name: John Smith")
                    .contains("Gender: MALE")
                    .contains("Education: Bachelor")
                    .contains("School: Peking University")
                    .contains("Major: Computer Science")
                    .contains("Work Years: 5 years")
                    .contains("Current Company: Tencent")
                    .contains("Current Position: Senior Backend Engineer")
                    .contains("Skills: Java, Spring Boot, MySQL, Redis")
                    .contains("Self Evaluation: 5 years of backend development");
        }

        @Test
        @DisplayName("skips empty fields")
        void shouldSkipNullFields() {
            Candidate minimal = new Candidate();
            minimal.setName("Jane Doe");

            String text = embeddingService.buildCandidateText(minimal);

            assertThat(text).contains("Name: Jane Doe");
            assertThat(text).doesNotContain("Education:");
            assertThat(text).doesNotContain("Skills:");
        }

        @Test
        @DisplayName("includes work experience")
        void shouldIncludeWorkExperience() {
            testCandidate.setWorkExperience(List.of(
                    Map.of("company", "Alibaba", "position", "Java Developer", "description", "Owned the core trading system")
            ));

            String text = embeddingService.buildCandidateText(testCandidate);
            assertThat(text)
                    .contains("Work Experience:")
                    .contains("Alibaba")
                    .contains("Java Developer")
                    .contains("Owned the core trading system");
        }

        @Test
        @DisplayName("includes project experience")
        void shouldIncludeProjectExperience() {
            testCandidate.setProjectExperience(List.of(
                    Map.of("name", "E-commerce Platform", "role", "Technical Lead", "description", "Built the microservice architecture from scratch")
            ));

            String text = embeddingService.buildCandidateText(testCandidate);
            assertThat(text)
                    .contains("Project Experience:")
                    .contains("E-commerce Platform")
                    .contains("Technical Lead")
                    .contains("Built the microservice architecture from scratch");
        }

        @Test
        @DisplayName("truncates oversized text to the maximum length")
        void shouldTruncateLongText() {
            // Build an oversized self evaluation
            String longText = "A".repeat(10000);
            testCandidate.setSelfEvaluation(longText);

            String text = embeddingService.buildCandidateText(testCandidate);
            assertThat(text.length()).isLessThanOrEqualTo(6000);
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // generateCandidateEmbedding tests
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("generateCandidateEmbedding")
    class GenerateCandidateEmbeddingTests {

        @Test
        @DisplayName("generates the candidate embedding successfully")
        void shouldGenerateEmbedding() {
            // Given
            float[] mockEmbedding = new float[1024];
            for (int i = 0; i < 1024; i++) {
                mockEmbedding[i] = (float) Math.random();
            }

            Embedding result = mock(Embedding.class);
            given(result.getOutput()).willReturn(mockEmbedding);

            EmbeddingResponse response = mock(EmbeddingResponse.class);
            given(response.getResult()).willReturn(result);

            given(embeddingModel.call(any(EmbeddingRequest.class))).willReturn(response);

            // When
            List<Float> embedding = embeddingService.generateCandidateEmbedding(testCandidate);

            // Then
            assertThat(embedding).hasSize(1024);
            then(embeddingModel).should().call(any(EmbeddingRequest.class));
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // generateQueryEmbedding tests
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("generateQueryEmbedding")
    class GenerateQueryEmbeddingTests {

        @Test
        @DisplayName("generates the query embedding successfully")
        void shouldGenerateQueryEmbedding() {
            // Given
            float[] mockEmbedding = new float[1024];
            Embedding result = mock(Embedding.class);
            given(result.getOutput()).willReturn(mockEmbedding);

            EmbeddingResponse response = mock(EmbeddingResponse.class);
            given(response.getResult()).willReturn(result);

            given(embeddingModel.call(any(EmbeddingRequest.class))).willReturn(response);

            // When
            List<Float> embedding = embeddingService.generateQueryEmbedding("3 years of Java backend development");

            // Then
            assertThat(embedding).hasSize(1024);
        }
    }
}
