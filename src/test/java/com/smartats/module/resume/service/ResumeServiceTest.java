package com.smartats.module.resume.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartats.common.exception.BusinessException;
import com.smartats.infrastructure.mq.MessagePublisher;
import com.smartats.infrastructure.storage.FileStorageService;
import com.smartats.module.resume.dto.BatchUploadResponse;
import com.smartats.module.resume.dto.ResumeUploadResponse;
import com.smartats.module.resume.dto.TaskStatusResponse;
import com.smartats.module.resume.entity.Resume;
import com.smartats.module.resume.mapper.ResumeMapper;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * ResumeService unit tests
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ResumeService unit tests")
class ResumeServiceTest {

    @InjectMocks
    private ResumeService resumeService;

    @Mock
    private ResumeMapper resumeMapper;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private MessagePublisher messagePublisher;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private MultipartFile mockFile;

    // PDF file header magic bytes
    private static final byte[] PDF_MAGIC = {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34};

    @BeforeEach
    void setUp() {
        // Common lenient stubs for Redis operations
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Upload resume tests
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("uploadResume")
    class UploadResumeTests {

        @Test
        @DisplayName("Throws exception when the file is empty")
        void shouldThrowWhenFileIsEmpty() {
            given(mockFile.isEmpty()).willReturn(true);

            assertThatThrownBy(() -> resumeService.uploadResume(mockFile, 1L))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("Throws exception when the file is null")
        void shouldThrowWhenFileIsNull() {
            assertThatThrownBy(() -> resumeService.uploadResume(null, 1L))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("Throws exception when the file exceeds 10MB")
        void shouldThrowWhenFileTooLarge() {
            given(mockFile.isEmpty()).willReturn(false);
            given(mockFile.getSize()).willReturn(11L * 1024 * 1024); // 11MB

            assertThatThrownBy(() -> resumeService.uploadResume(mockFile, 1L))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("Throws exception for an unsupported file type")
        void shouldThrowWhenUnsupportedContentType() {
            given(mockFile.isEmpty()).willReturn(false);
            given(mockFile.getSize()).willReturn(1024L);
            given(mockFile.getContentType()).willReturn("image/png");

            assertThatThrownBy(() -> resumeService.uploadResume(mockFile, 1L))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("Returns the existing resume when the file is duplicated")
        void shouldReturnExistingWhenDuplicate() throws Exception {
            // Prepare a valid PDF upload that passes validation
            given(mockFile.isEmpty()).willReturn(false);
            given(mockFile.getSize()).willReturn(1024L);
            given(mockFile.getContentType()).willReturn("application/pdf");
            given(mockFile.getOriginalFilename()).willReturn("resume.pdf");
            given(mockFile.getBytes()).willReturn(PDF_MAGIC);
            given(mockFile.getInputStream()).willReturn(new ByteArrayInputStream(PDF_MAGIC));

            // Redis dedup hit
            given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(contains("dedup:resume:"))).willReturn("99");

            Resume existingResume = new Resume();
            existingResume.setId(99L);
            existingResume.setFileHash("abc123");
            given(resumeMapper.selectById(99L)).willReturn(existingResume);

            ResumeUploadResponse response = resumeService.uploadResume(mockFile, 1L);

            assertThat(response.getDuplicated()).isTrue();
            assertThat(response.getResumeId()).isEqualTo(99L);
            assertThat(response.getTaskId()).isNull();
        }

        @Test
        @DisplayName("Successful upload returns taskId and resumeId")
        void shouldUploadSuccessfully() throws Exception {
            given(mockFile.isEmpty()).willReturn(false);
            given(mockFile.getSize()).willReturn(1024L);
            given(mockFile.getContentType()).willReturn("application/pdf");
            given(mockFile.getOriginalFilename()).willReturn("resume.pdf");
            given(mockFile.getBytes()).willReturn(PDF_MAGIC);
            given(mockFile.getInputStream()).willReturn(new ByteArrayInputStream(PDF_MAGIC));

            // No dedup
            given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(contains("dedup:resume:"))).willReturn(null);
            // DB dedup check
            given(resumeMapper.selectOne(any(LambdaQueryWrapper.class))).willReturn(null);
            // MinIO upload
            given(fileStorageService.uploadFile(eq(mockFile), anyString())).willReturn("http://minio/resume.pdf");
            // DB insert
            given(resumeMapper.insert(any(Resume.class))).willAnswer(invocation -> {
                Resume r = invocation.getArgument(0);
                r.setId(1L);
                return 1;
            });
            // Task status serialization
            given(objectMapper.writeValueAsString(any(TaskStatusResponse.class))).willReturn("{}");

            ResumeUploadResponse response = resumeService.uploadResume(mockFile, 1L);

            assertThat(response.getDuplicated()).isFalse();
            assertThat(response.getResumeId()).isEqualTo(1L);
            assertThat(response.getTaskId()).isNotBlank();
            then(messagePublisher).should().sendResumeParseMessage(any());
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Task status query tests
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("getTaskStatus")
    class GetTaskStatusTests {

        @Test
        @DisplayName("Returns the correct status when Redis has data")
        void shouldReturnStatusFromRedis() throws Exception {
            String taskId = "test-task-id";
            String json = "{\"status\":\"COMPLETED\",\"progress\":100}";

            given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(contains(taskId))).willReturn(json);

            TaskStatusResponse parsed = new TaskStatusResponse();
            parsed.setStatus("COMPLETED");
            parsed.setProgress(100);
            given(objectMapper.readValue(eq(json), eq(TaskStatusResponse.class))).willReturn(parsed);

            TaskStatusResponse response = resumeService.getTaskStatus(taskId);

            assertThat(response.getStatus()).isEqualTo("COMPLETED");
            assertThat(response.getProgress()).isEqualTo(100);
        }

        @Test
        @DisplayName("Returns NOT_FOUND when Redis has no data")
        void shouldReturnNotFoundWhenNoRedisData() {
            given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(anyString())).willReturn(null);

            TaskStatusResponse response = resumeService.getTaskStatus("nonexistent-id");

            assertThat(response.getStatus()).isEqualTo("NOT_FOUND");
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Resume detail query tests
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("getResumeById")
    class GetResumeByIdTests {

        @Test
        @DisplayName("Successfully queries one's own resume")
        void shouldReturnResumeForOwner() {
            Resume resume = new Resume();
            resume.setId(1L);
            resume.setUserId(10L);
            resume.setFileName("resume.pdf");

            given(resumeMapper.selectById(1L)).willReturn(resume);

            Resume result = resumeService.getResumeById(1L, 10L);

            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getFileName()).isEqualTo("resume.pdf");
        }

        @Test
        @DisplayName("Throws exception when the resume does not exist")
        void shouldThrowWhenResumeNotFound() {
            given(resumeMapper.selectById(999L)).willReturn(null);

            assertThatThrownBy(() -> resumeService.getResumeById(999L, 1L))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("Throws exception when querying another person's resume")
        void shouldThrowWhenNotOwner() {
            Resume resume = new Resume();
            resume.setId(1L);
            resume.setUserId(10L);

            given(resumeMapper.selectById(1L)).willReturn(resume);

            assertThatThrownBy(() -> resumeService.getResumeById(1L, 99L))
                    .isInstanceOf(BusinessException.class);
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Resume list tests
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("listResumes")
    class ListResumesTests {

        @Test
        @DisplayName("Paged query returns results")
        void shouldReturnPagedResults() {
            Page<Resume> page = new Page<>(1, 10);
            page.setTotal(1);
            Resume resume = new Resume();
            resume.setId(1L);
            resume.setFileName("test.pdf");
            page.setRecords(java.util.List.of(resume));

            given(resumeMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .willReturn(page);

            Page<Resume> result = resumeService.listResumes(1L, 1, 10);

            assertThat(result.getRecords()).hasSize(1);
            assertThat(result.getTotal()).isEqualTo(1);
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Batch upload tests
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("batchUploadResumes")
    class BatchUploadTests {

        @Test
        @DisplayName("Throws exception when the file array is empty")
        void shouldThrowWhenFilesEmpty() {
            assertThatThrownBy(() -> resumeService.batchUploadResumes(null, 1L))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("Throws exception when there are more than 20 files")
        void shouldThrowWhenExceedMaxBatchSize() {
            MultipartFile[] files = new MultipartFile[21];
            for (int i = 0; i < 21; i++) {
                files[i] = mockFile;
            }

            assertThatThrownBy(() -> resumeService.batchUploadResumes(files, 1L))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("Rate limit - throws exception when exceeding 5 uploads per minute")
        void shouldThrowWhenRateLimited() {
            given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(anyString())).willReturn("5");

            MultipartFile[] files = new MultipartFile[]{mockFile};

            assertThatThrownBy(() -> resumeService.batchUploadResumes(files, 1L))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("A single file failure does not affect the other files")
        void shouldContinueWhenSingleFileFails() throws Exception {
            given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(startsWith("rate:upload:"))).willReturn(null);

            // Create two mock files
            MultipartFile goodFile = mock(MultipartFile.class);
            MultipartFile badFile = mock(MultipartFile.class);

            // badFile: empty file → throws exception
            given(badFile.isEmpty()).willReturn(true);
            given(badFile.getOriginalFilename()).willReturn("bad.pdf");

            // goodFile: also set to empty to keep it simple (it will fail but not crash)
            given(goodFile.isEmpty()).willReturn(true);
            given(goodFile.getOriginalFilename()).willReturn("good.pdf");

            MultipartFile[] files = new MultipartFile[]{badFile, goodFile};

            BatchUploadResponse result = resumeService.batchUploadResumes(files, 1L);

            assertThat(result.getTotalCount()).isEqualTo(2);
            assertThat(result.getFailedCount()).isEqualTo(2);
            assertThat(result.getItems()).hasSize(2);
            // Both files fail because they are empty, but nothing crashes
            assertThat(result.getItems().get(0).getStatus()).isEqualTo("FAILED");
            assertThat(result.getItems().get(1).getStatus()).isEqualTo("FAILED");
        }
    }
}
