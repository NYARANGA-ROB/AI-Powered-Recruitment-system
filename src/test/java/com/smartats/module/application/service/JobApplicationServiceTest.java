package com.smartats.module.application.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartats.common.enums.ApplicationStatus;
import com.smartats.common.enums.JobStatus;
import com.smartats.common.exception.BusinessException;
import com.smartats.module.application.dto.CreateApplicationRequest;
import com.smartats.module.application.dto.UpdateApplicationStatusRequest;
import com.smartats.module.application.entity.JobApplication;
import com.smartats.module.application.mapper.JobApplicationMapper;
import com.smartats.module.candidate.entity.Candidate;
import com.smartats.module.candidate.mapper.CandidateMapper;
import com.smartats.module.job.entity.Job;
import com.smartats.module.job.mapper.JobMapper;
import com.smartats.module.webhook.service.WebhookService;
import org.springframework.context.ApplicationEventPublisher;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * JobApplicationService unit tests
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JobApplicationService unit tests")
class JobApplicationServiceTest {

    @InjectMocks
    private JobApplicationService jobApplicationService;

    @Mock
    private JobApplicationMapper jobApplicationMapper;
    @Mock
    private JobMapper jobMapper;
    @Mock
    private CandidateMapper candidateMapper;
    @Mock
    private WebhookService webhookService;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private MatchScoreService matchScoreService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private Job publishedJob;
    private Candidate mockCandidate;

    @BeforeEach
    void setUp() {
        publishedJob = new Job();
        publishedJob.setId(1L);
        publishedJob.setTitle("Senior Java Engineer");
        publishedJob.setStatus(JobStatus.PUBLISHED.getCode());

        mockCandidate = new Candidate();
        mockCandidate.setId(10L);
        mockCandidate.setName("Li Si");
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Create application tests
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("Create job application")
    class CreateApplicationTests {

        @Test
        @DisplayName("Successful creation of an application - initial status is PENDING")
        void createApplication_success() {
            // Given
            CreateApplicationRequest request = new CreateApplicationRequest();
            request.setJobId(1L);
            request.setCandidateId(10L);

            given(jobMapper.selectById(1L)).willReturn(publishedJob);
            given(candidateMapper.selectById(10L)).willReturn(mockCandidate);
            given(jobApplicationMapper.selectCount(any())).willReturn(0L); // No duplicate
            given(jobApplicationMapper.insert(any(JobApplication.class))).willAnswer(invocation -> {
                JobApplication app = invocation.getArgument(0);
                app.setId(100L);
                return 1;
            });

            // When
            Long appId = jobApplicationService.createApplication(request);

            // Then
            assertThat(appId).isEqualTo(100L);

            ArgumentCaptor<JobApplication> captor = ArgumentCaptor.forClass(JobApplication.class);
            then(jobApplicationMapper).should().insert(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(ApplicationStatus.PENDING.getCode());
            assertThat(captor.getValue().getJobId()).isEqualTo(1L);
            assertThat(captor.getValue().getCandidateId()).isEqualTo(10L);
        }

        @Test
        @DisplayName("Throws exception when the position does not exist")
        void createApplication_jobNotFound() {
            CreateApplicationRequest request = new CreateApplicationRequest();
            request.setJobId(999L);
            request.setCandidateId(10L);

            given(jobMapper.selectById(999L)).willReturn(null);

            assertThatThrownBy(() -> jobApplicationService.createApplication(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Position not found");
        }

        @Test
        @DisplayName("Throws exception when the position is not published")
        void createApplication_jobNotPublished() {
            publishedJob.setStatus(JobStatus.DRAFT.getCode());
            CreateApplicationRequest request = new CreateApplicationRequest();
            request.setJobId(1L);
            request.setCandidateId(10L);

            given(jobMapper.selectById(1L)).willReturn(publishedJob);

            assertThatThrownBy(() -> jobApplicationService.createApplication(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("The position is not published");
        }

        @Test
        @DisplayName("Throws exception when the candidate does not exist")
        void createApplication_candidateNotFound() {
            CreateApplicationRequest request = new CreateApplicationRequest();
            request.setJobId(1L);
            request.setCandidateId(999L);

            given(jobMapper.selectById(1L)).willReturn(publishedJob);
            given(candidateMapper.selectById(999L)).willReturn(null);

            assertThatThrownBy(() -> jobApplicationService.createApplication(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Candidate not found");
        }

        @Test
        @DisplayName("Throws exception on duplicate application")
        void createApplication_duplicate() {
            CreateApplicationRequest request = new CreateApplicationRequest();
            request.setJobId(1L);
            request.setCandidateId(10L);

            given(jobMapper.selectById(1L)).willReturn(publishedJob);
            given(candidateMapper.selectById(10L)).willReturn(mockCandidate);
            given(jobApplicationMapper.selectCount(any())).willReturn(1L); // Duplicate exists

            assertThatThrownBy(() -> jobApplicationService.createApplication(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("already applied");
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Status update tests
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("Update application status")
    class UpdateStatusTests {

        private JobApplication pendingApp;

        @BeforeEach
        void setUp() {
            pendingApp = new JobApplication();
            pendingApp.setId(1L);
            pendingApp.setJobId(10L);
            pendingApp.setCandidateId(20L);
            pendingApp.setStatus(ApplicationStatus.PENDING.getCode());

            lenient().when(redisTemplate.delete(anyString())).thenReturn(true);
        }

        @Test
        @DisplayName("Legal status transition: PENDING → SCREENING")
        void updateStatus_pendingToScreening() {
            given(jobApplicationMapper.selectById(1L)).willReturn(pendingApp);
            given(jobApplicationMapper.updateById(any(JobApplication.class))).willReturn(1);

            UpdateApplicationStatusRequest request = new UpdateApplicationStatusRequest();
            request.setStatus(ApplicationStatus.SCREENING.getCode());

            jobApplicationService.updateStatus(1L, request);

            ArgumentCaptor<JobApplication> captor = ArgumentCaptor.forClass(JobApplication.class);
            then(jobApplicationMapper).should().updateById(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(ApplicationStatus.SCREENING.getCode());
        }

        @Test
        @DisplayName("Legal status transition: SCREENING → INTERVIEW")
        void updateStatus_screeningToInterview() {
            pendingApp.setStatus(ApplicationStatus.SCREENING.getCode());
            given(jobApplicationMapper.selectById(1L)).willReturn(pendingApp);
            given(jobApplicationMapper.updateById(any(JobApplication.class))).willReturn(1);

            UpdateApplicationStatusRequest request = new UpdateApplicationStatusRequest();
            request.setStatus(ApplicationStatus.INTERVIEW.getCode());

            jobApplicationService.updateStatus(1L, request);

            ArgumentCaptor<JobApplication> captor = ArgumentCaptor.forClass(JobApplication.class);
            then(jobApplicationMapper).should().updateById(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(ApplicationStatus.INTERVIEW.getCode());
        }

        @Test
        @DisplayName("Illegal status transition: PENDING → OFFER (skipping SCREENING and INTERVIEW)")
        void updateStatus_illegalTransition() {
            given(jobApplicationMapper.selectById(1L)).willReturn(pendingApp);

            UpdateApplicationStatusRequest request = new UpdateApplicationStatusRequest();
            request.setStatus(ApplicationStatus.OFFER.getCode());

            assertThatThrownBy(() -> jobApplicationService.updateStatus(1L, request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("not allowed");
        }

        @Test
        @DisplayName("Terminal status cannot change: REJECTED → any status")
        void updateStatus_rejectedCannotTransition() {
            pendingApp.setStatus(ApplicationStatus.REJECTED.getCode());
            given(jobApplicationMapper.selectById(1L)).willReturn(pendingApp);

            UpdateApplicationStatusRequest request = new UpdateApplicationStatusRequest();
            request.setStatus(ApplicationStatus.PENDING.getCode());

            assertThatThrownBy(() -> jobApplicationService.updateStatus(1L, request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("not allowed");
        }

        @Test
        @DisplayName("Throws exception when the application does not exist")
        void updateStatus_notFound() {
            given(jobApplicationMapper.selectById(999L)).willReturn(null);

            UpdateApplicationStatusRequest request = new UpdateApplicationStatusRequest();
            request.setStatus(ApplicationStatus.SCREENING.getCode());

            assertThatThrownBy(() -> jobApplicationService.updateStatus(999L, request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Application record not found");
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Query tests
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("Query applications")
    class QueryTests {

        @BeforeEach
        void setUp() {
            lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        }

        @Test
        @DisplayName("Query by ID - queries from the database on cache miss")
        void getById_cacheMiss() throws Exception {
            JobApplication app = new JobApplication();
            app.setId(1L);
            app.setJobId(10L);
            app.setCandidateId(20L);
            app.setStatus(ApplicationStatus.PENDING.getCode());
            app.setAppliedAt(LocalDateTime.now());
            app.setUpdatedAt(LocalDateTime.now());

            given(valueOperations.get(anyString())).willReturn(null);
            given(jobApplicationMapper.selectById(1L)).willReturn(app);
            given(jobMapper.selectById(10L)).willReturn(publishedJob);
            given(candidateMapper.selectById(20L)).willReturn(mockCandidate);
            given(objectMapper.writeValueAsString(any())).willReturn("{}");

            var response = jobApplicationService.getById(1L);

            assertThat(response).isNotNull();
            assertThat(response.getJobTitle()).isEqualTo("Senior Java Engineer");
            assertThat(response.getCandidateName()).isEqualTo("Li Si");
        }

        @Test
        @DisplayName("Throws exception when the application does not exist")
        void getById_notFound() {
            given(valueOperations.get(anyString())).willReturn(null);
            given(jobApplicationMapper.selectById(999L)).willReturn(null);

            assertThatThrownBy(() -> jobApplicationService.getById(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Application record not found");
        }

        @Test
        @DisplayName("Query by job returns paged results")
        void listByJobId_returnsPaged() {
            Page<JobApplication> emptyPage = new Page<>(1, 10, 0);
            emptyPage.setRecords(java.util.List.of());
            given(jobApplicationMapper.selectPage(any(), any())).willReturn(emptyPage);

            var result = jobApplicationService.listByJobId(1L, 1, 10);

            assertThat(result.getRecords()).isEmpty();
            assertThat(result.getTotal()).isZero();
        }
    }
}
