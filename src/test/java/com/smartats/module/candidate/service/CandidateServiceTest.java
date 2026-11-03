package com.smartats.module.candidate.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartats.common.exception.BusinessException;
import com.smartats.module.candidate.dto.CandidateQueryRequest;
import com.smartats.module.candidate.dto.CandidateUpdateRequest;
import com.smartats.module.candidate.entity.Candidate;
import com.smartats.module.candidate.mapper.CandidateMapper;
import com.smartats.module.resume.dto.CandidateInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * CandidateService unit tests
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CandidateService unit tests")
class CandidateServiceTest {

    @InjectMocks
    private CandidateService candidateService;

    @Mock
    private CandidateMapper candidateMapper;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private CandidateVectorService candidateVectorService;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private Candidate testCandidate;
    private CandidateInfo testCandidateInfo;

    @BeforeEach
    void setUp() {
        testCandidate = new Candidate();
        testCandidate.setId(1L);
        testCandidate.setResumeId(100L);
        testCandidate.setName("Zhang San");
        testCandidate.setPhone("13800138000");
        testCandidate.setEmail("zhangsan@test.com");
        testCandidate.setGender("MALE");
        testCandidate.setAge(28);
        testCandidate.setEducation("Bachelor");
        testCandidate.setSchool("Tsinghua University");
        testCandidate.setMajor("Computer Science");
        testCandidate.setWorkYears(5);
        testCandidate.setCurrentCompany("Alibaba");
        testCandidate.setCurrentPosition("Senior Engineer");
        testCandidate.setSkills(List.of("Java", "Spring", "MySQL"));
        testCandidate.setCreatedAt(LocalDateTime.now());
        testCandidate.setUpdatedAt(LocalDateTime.now());

        testCandidateInfo = new CandidateInfo();
        testCandidateInfo.setName("Zhang San");
        testCandidateInfo.setPhone("13800138000");
        testCandidateInfo.setEmail("zhangsan@test.com");
        testCandidateInfo.setGender("男");
        testCandidateInfo.setAge(28);
        testCandidateInfo.setEducation("Bachelor");
        testCandidateInfo.setSchool("Tsinghua University");
        testCandidateInfo.setMajor("Computer Science");
        testCandidateInfo.setWorkYears(5);
        testCandidateInfo.setCurrentCompany("Alibaba");
        testCandidateInfo.setCurrentPosition("Senior Engineer");
        testCandidateInfo.setSkills(List.of("Java", "Spring", "MySQL"));
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Create candidate
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("createCandidate")
    class CreateCandidateTests {

        @Test
        @DisplayName("Creates a new candidate successfully")
        void shouldCreateNewCandidate() {
            given(candidateMapper.selectOne(any(LambdaQueryWrapper.class))).willReturn(null);
            given(candidateMapper.insert(any(Candidate.class))).willAnswer(invocation -> {
                Candidate c = invocation.getArgument(0);
                c.setId(1L);
                return 1;
            });

            Candidate result = candidateService.createCandidate(100L, testCandidateInfo, "{}");

            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getName()).isEqualTo("Zhang San");
            assertThat(result.getResumeId()).isEqualTo(100L);
            assertThat(result.getGender()).isEqualTo("MALE"); // normalized
            then(candidateMapper).should().insert(any(Candidate.class));
        }

        @Test
        @DisplayName("Updates the candidate when one already exists")
        void shouldUpdateWhenAlreadyExists() {
            given(candidateMapper.selectOne(any(LambdaQueryWrapper.class))).willReturn(testCandidate);
            given(candidateMapper.updateById(any(Candidate.class))).willReturn(1);
            given(candidateMapper.selectById(1L)).willReturn(testCandidate);
            given(redisTemplate.delete(anyString())).willReturn(true);

            Candidate result = candidateService.createCandidate(100L, testCandidateInfo, "{}");

            assertThat(result).isNotNull();
            then(candidateMapper).should().updateById(any(Candidate.class));
            then(candidateMapper).should(never()).insert(any(Candidate.class));
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Query candidate by ID
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("getById")
    class GetByIdTests {

        @Test
        @DisplayName("Returns directly on a cache hit")
        void shouldReturnFromCacheWhenHit() throws Exception {
            String cachedJson = "{\"id\":1,\"name\":\"Zhang San\"}";
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(contains("cache:candidate:1"))).willReturn(cachedJson);
            given(objectMapper.readValue(eq(cachedJson), eq(Candidate.class))).willReturn(testCandidate);

            Candidate result = candidateService.getById(1L);

            assertThat(result.getName()).isEqualTo("Zhang San");
            then(candidateMapper).should(never()).selectById(anyLong());
        }

        @Test
        @DisplayName("Queries the database and fills the cache on a cache miss")
        void shouldQueryDbAndFillCacheWhenMiss() throws Exception {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(anyString())).willReturn(null);
            given(candidateMapper.selectById(1L)).willReturn(testCandidate);
            given(objectMapper.writeValueAsString(testCandidate)).willReturn("{}");

            Candidate result = candidateService.getById(1L);

            assertThat(result.getName()).isEqualTo("Zhang San");
            then(candidateMapper).should().selectById(1L);
            then(valueOperations).should().set(anyString(), eq("{}"), eq(30L), any());
        }

        @Test
        @DisplayName("Returns null when the record is also missing from the database")
        void shouldReturnNullWhenNotExist() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(anyString())).willReturn(null);
            given(candidateMapper.selectById(999L)).willReturn(null);

            Candidate result = candidateService.getById(999L);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Falls back gracefully to the database when cache deserialization fails")
        void shouldFallbackToDbWhenCacheDeserializationFails() throws Exception {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(anyString())).willReturn("invalid-json");
            given(objectMapper.readValue(eq("invalid-json"), eq(Candidate.class)))
                    .willThrow(new JsonProcessingException("parse error") {});
            given(candidateMapper.selectById(1L)).willReturn(testCandidate);
            given(objectMapper.writeValueAsString(testCandidate)).willReturn("{}");

            Candidate result = candidateService.getById(1L);

            assertThat(result.getName()).isEqualTo("Zhang San");
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Manual candidate update (updateManual)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("updateManual")
    class UpdateManualTests {

        @Test
        @DisplayName("Partial update succeeds")
        void shouldPartialUpdateSuccessfully() throws Exception {
            CandidateUpdateRequest request = new CandidateUpdateRequest();
            request.setName("Li Si");
            request.setPhone("13900139000");
            // other fields null → not updated

            given(candidateMapper.selectById(1L)).willReturn(testCandidate);
            given(candidateMapper.updateById(any(Candidate.class))).willReturn(1);
            given(redisTemplate.delete(anyString())).willReturn(true);
            // For getById after update
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(anyString())).willReturn(null);
            Candidate updated = new Candidate();
            updated.setId(1L);
            updated.setName("Li Si");
            updated.setPhone("13900139000");
            given(candidateMapper.selectById(1L)).willReturn(updated);
            given(objectMapper.writeValueAsString(any(Candidate.class))).willReturn("{}");

            Candidate result = candidateService.updateManual(1L, request);

            assertThat(result.getName()).isEqualTo("Li Si");
            then(candidateVectorService).should().vectorizeCandidateAsync(any(Candidate.class));
        }

        @Test
        @DisplayName("Throws when the candidate does not exist")
        void shouldThrowWhenNotFound() {
            CandidateUpdateRequest request = new CandidateUpdateRequest();
            request.setName("Li Si");
            given(candidateMapper.selectById(999L)).willReturn(null);

            assertThatThrownBy(() -> candidateService.updateManual(999L, request))
                    .isInstanceOf(BusinessException.class);
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Delete candidate
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("deleteById")
    class DeleteByIdTests {

        @Test
        @DisplayName("Delete succeeds and clears both the cache and the vector")
        void shouldDeleteAndEvictCacheAndVector() {
            given(candidateMapper.deleteById(1L)).willReturn(1);
            given(redisTemplate.delete(anyString())).willReturn(true);

            candidateService.deleteById(1L);

            then(candidateMapper).should().deleteById(1L);
            then(redisTemplate).should().delete(contains("cache:candidate:1"));
            then(candidateVectorService).should().deleteVector(1L);
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Paged query tests
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("listCandidates")
    class ListCandidatesTests {

        @Test
        @DisplayName("Paged query without filters")
        void shouldListWithoutFilters() {
            CandidateQueryRequest request = new CandidateQueryRequest();
            request.setPage(1);
            request.setPageSize(10);

            Page<Candidate> page = new Page<>(1, 10);
            page.setTotal(1);
            page.setRecords(List.of(testCandidate));

            given(candidateMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .willReturn(page);

            Page<Candidate> result = candidateService.listCandidates(request);

            assertThat(result.getRecords()).hasSize(1);
            assertThat(result.getTotal()).isEqualTo(1);
        }

        @Test
        @DisplayName("Query with a keyword filter")
        void shouldListWithKeywordFilter() {
            CandidateQueryRequest request = new CandidateQueryRequest();
            request.setPage(1);
            request.setPageSize(10);
            request.setKeyword("Zhang San");

            Page<Candidate> page = new Page<>(1, 10);
            page.setTotal(1);
            page.setRecords(List.of(testCandidate));

            given(candidateMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .willReturn(page);

            Page<Candidate> result = candidateService.listCandidates(request);

            assertThat(result.getRecords()).hasSize(1);
        }

        @Test
        @DisplayName("pageSize is clamped between 1 and 100")
        void shouldClampPageSize() {
            CandidateQueryRequest request = new CandidateQueryRequest();
            request.setPage(1);
            request.setPageSize(200); // > 100

            Page<Candidate> page = new Page<>(1, 100);
            page.setTotal(0);
            page.setRecords(List.of());

            given(candidateMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .willReturn(page);

            candidateService.listCandidates(request);

            then(candidateMapper).should().selectPage(any(Page.class), any(LambdaQueryWrapper.class));
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Query by resumeId
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("getByResumeId")
    class GetByResumeIdTests {

        @Test
        @DisplayName("Returns the candidate when it exists")
        void shouldReturnCandidate() {
            given(candidateMapper.selectOne(any(LambdaQueryWrapper.class))).willReturn(testCandidate);

            Candidate result = candidateService.getByResumeId(100L);

            assertThat(result.getName()).isEqualTo("Zhang San");
        }

        @Test
        @DisplayName("Returns null when it does not exist")
        void shouldReturnNullWhenNotExist() {
            given(candidateMapper.selectOne(any(LambdaQueryWrapper.class))).willReturn(null);

            Candidate result = candidateService.getByResumeId(999L);

            assertThat(result).isNull();
        }
    }
}
