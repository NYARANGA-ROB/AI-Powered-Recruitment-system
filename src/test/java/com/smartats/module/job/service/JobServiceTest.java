package com.smartats.module.job.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartats.common.enums.JobStatus;
import com.smartats.common.exception.BusinessException;
import com.smartats.module.job.dto.request.CreateJobRequest;
import com.smartats.module.job.dto.request.JobQueryRequest;
import com.smartats.module.job.dto.request.UpdateJobRequest;
import com.smartats.module.job.dto.response.JobResponse;
import com.smartats.module.job.entity.Job;
import com.smartats.module.job.mapper.JobMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
 * JobService unit tests
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JobService unit tests")
class JobServiceTest {

    @InjectMocks
    private JobService jobService;

    @Mock
    private JobMapper jobMapper;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private CacheEvictionService cacheEvictionService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Create job tests
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("Create job")
    class CreateJobTests {

        @Test
        @DisplayName("Successful job creation - initial status is DRAFT")
        void createJob_success() {
            // Given
            CreateJobRequest request = new CreateJobRequest();
            request.setTitle("Senior Java Engineer");
            request.setDepartment("Engineering Department");
            request.setDescription("Responsible for core system development");

            given(jobMapper.insert(any(Job.class))).willAnswer(invocation -> {
                Job job = invocation.getArgument(0);
                job.setId(1L);
                return 1;
            });

            // When
            Long jobId = jobService.createJob(request, 100L);

            // Then
            assertThat(jobId).isEqualTo(1L);

            ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
            then(jobMapper).should().insert(captor.capture());
            Job savedJob = captor.getValue();
            assertThat(savedJob.getStatus()).isEqualTo(JobStatus.DRAFT.getCode());
            assertThat(savedJob.getCreatorId()).isEqualTo(100L);
            assertThat(savedJob.getTitle()).isEqualTo("Senior Java Engineer");
            assertThat(savedJob.getViewCount()).isZero();
        }

        @Test
        @DisplayName("Create job - skill tag serialization")
        void createJob_withSkills() throws Exception {
            CreateJobRequest request = new CreateJobRequest();
            request.setTitle("Test Position");
            request.setRequiredSkills(List.of("Java", "Spring", "Redis"));

            given(objectMapper.writeValueAsString(anyList())).willReturn("[\"Java\",\"Spring\",\"Redis\"]");
            given(jobMapper.insert(any(Job.class))).willReturn(1);

            jobService.createJob(request, 100L);

            ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
            then(jobMapper).should().insert(captor.capture());
            assertThat(captor.getValue().getRequiredSkills()).isEqualTo("[\"Java\",\"Spring\",\"Redis\"]");
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Update job tests
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("Update job")
    class UpdateJobTests {

        private Job existingJob;

        @BeforeEach
        void setUp() {
            existingJob = new Job();
            existingJob.setId(1L);
            existingJob.setTitle("Original title");
            existingJob.setCreatorId(100L);
            existingJob.setStatus(JobStatus.DRAFT.getCode());

            lenient().when(redisTemplate.delete(anyString())).thenReturn(true);
        }

        @Test
        @DisplayName("Successful job update - delayed double delete")
        void updateJob_success() {
            UpdateJobRequest request = new UpdateJobRequest();
            request.setId(1L);
            request.setTitle("New title");

            given(jobMapper.selectById(1L)).willReturn(existingJob);
            given(jobMapper.updateById(any(Job.class))).willReturn(1);

            jobService.updateJob(request, 100L);

            // Verify the delayed double delete
            then(redisTemplate).should().delete(anyString()); // First delete
            then(cacheEvictionService).should().asyncDeleteCache(anyString()); // Delayed double delete
        }

        @Test
        @DisplayName("Non-creator cannot update")
        void updateJob_forbidden() {
            UpdateJobRequest request = new UpdateJobRequest();
            request.setId(1L);

            given(jobMapper.selectById(1L)).willReturn(existingJob);

            assertThatThrownBy(() -> jobService.updateJob(request, 999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("No permission");
        }

        @Test
        @DisplayName("Throws exception when the position does not exist")
        void updateJob_notFound() {
            UpdateJobRequest request = new UpdateJobRequest();
            request.setId(999L);

            given(jobMapper.selectById(999L)).willReturn(null);

            assertThatThrownBy(() -> jobService.updateJob(request, 100L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Position not found");
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Publish/close job tests
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("Publish/close job")
    class PublishCloseTests {

        private Job draftJob;

        @BeforeEach
        void setUp() {
            draftJob = new Job();
            draftJob.setId(1L);
            draftJob.setCreatorId(100L);
            draftJob.setStatus(JobStatus.DRAFT.getCode());

            lenient().when(redisTemplate.delete(anyString())).thenReturn(true);
        }

        @Test
        @DisplayName("Publish a job normally")
        void publishJob_success() {
            given(jobMapper.selectById(1L)).willReturn(draftJob);
            given(jobMapper.updateById(any(Job.class))).willReturn(1);

            jobService.publishJob(1L, 100L);

            ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
            then(jobMapper).should().updateById(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(JobStatus.PUBLISHED.getCode());
        }

        @Test
        @DisplayName("Republishing an already published position throws an exception")
        void publishJob_alreadyPublished() {
            draftJob.setStatus(JobStatus.PUBLISHED.getCode());
            given(jobMapper.selectById(1L)).willReturn(draftJob);

            assertThatThrownBy(() -> jobService.publishJob(1L, 100L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Position is already published");
        }

        @Test
        @DisplayName("Close a job normally")
        void closeJob_success() {
            draftJob.setStatus(JobStatus.PUBLISHED.getCode());
            given(jobMapper.selectById(1L)).willReturn(draftJob);
            given(jobMapper.updateById(any(Job.class))).willReturn(1);

            jobService.closeJob(1L, 100L);

            ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
            then(jobMapper).should().updateById(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(JobStatus.CLOSED.getCode());
        }

        @Test
        @DisplayName("Reclosing an already closed position throws an exception")
        void closeJob_alreadyClosed() {
            draftJob.setStatus(JobStatus.CLOSED.getCode());
            given(jobMapper.selectById(1L)).willReturn(draftJob);

            assertThatThrownBy(() -> jobService.closeJob(1L, 100L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Position is already closed");
        }

        @Test
        @DisplayName("Non-creator cannot publish")
        void publishJob_forbidden() {
            given(jobMapper.selectById(1L)).willReturn(draftJob);

            assertThatThrownBy(() -> jobService.publishJob(1L, 999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("No permission");
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Query tests
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("Query jobs")
    class QueryTests {

        @Test
        @DisplayName("Query by ID - queries the database and backfills on cache miss")
        void getJobDetail_cacheMiss() throws Exception {
            Job job = new Job();
            job.setId(1L);
            job.setTitle("Test Position");
            job.setStatus(JobStatus.PUBLISHED.getCode());
            job.setViewCount(100);
            job.setCreatedAt(LocalDateTime.now());
            job.setUpdatedAt(LocalDateTime.now());

            given(valueOperations.increment(anyString())).willReturn(1L);
            given(valueOperations.get(anyString())).willReturn(null); // Cache miss
            given(jobMapper.selectById(1L)).willReturn(job);
            given(objectMapper.writeValueAsString(any())).willReturn("{}");

            JobResponse response = jobService.getJobDetail(1L);

            assertThat(response).isNotNull();
            assertThat(response.getViewCount()).isEqualTo(101); // 100 + 1
        }

        @Test
        @DisplayName("Throws exception when the position does not exist")
        void getJobDetail_notFound() {
            given(valueOperations.increment(anyString())).willReturn(1L);
            given(valueOperations.get(anyString())).willReturn(null);
            given(jobMapper.selectById(999L)).willReturn(null);

            assertThatThrownBy(() -> jobService.getJobDetail(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Position not found");
        }

        @Test
        @DisplayName("Hot jobs query - default limit of 10")
        void getHotJobs_defaultLimit() {
            Page<Job> emptyPage = new Page<>(1, 10, 0);
            emptyPage.setRecords(List.of());
            given(jobMapper.selectPage(any(), any())).willReturn(emptyPage);

            List<JobResponse> result = jobService.getHotJobs(null);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Hot jobs query - limit does not exceed 50")
        void getHotJobs_maxLimit() {
            Page<Job> emptyPage = new Page<>(1, 50, 0);
            emptyPage.setRecords(List.of());
            given(jobMapper.selectPage(any(), any())).willReturn(emptyPage);

            List<JobResponse> result = jobService.getHotJobs(100);

            assertThat(result).isEmpty();
            // Verify the actual query limit is 50 (safety boundary)
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Delete job tests
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("Delete job")
    class DeleteJobTests {

        @Test
        @DisplayName("Delete a job normally")
        void deleteJob_success() {
            Job job = new Job();
            job.setId(1L);
            job.setCreatorId(100L);

            given(jobMapper.selectById(1L)).willReturn(job);
            given(jobMapper.deleteById(1L)).willReturn(1);
            given(redisTemplate.delete(anyString())).willReturn(true);

            jobService.deleteJob(1L, 100L);

            then(jobMapper).should().deleteById(1L);
            then(redisTemplate).should().delete(anyString());
        }

        @Test
        @DisplayName("Non-creator cannot delete")
        void deleteJob_forbidden() {
            Job job = new Job();
            job.setId(1L);
            job.setCreatorId(100L);

            given(jobMapper.selectById(1L)).willReturn(job);

            assertThatThrownBy(() -> jobService.deleteJob(1L, 999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("No permission");
        }
    }
}
