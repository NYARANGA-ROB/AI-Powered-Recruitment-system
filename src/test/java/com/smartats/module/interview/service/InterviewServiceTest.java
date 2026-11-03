package com.smartats.module.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartats.common.enums.ApplicationStatus;
import com.smartats.common.enums.InterviewStatus;
import com.smartats.common.exception.BusinessException;
import com.smartats.module.application.entity.JobApplication;
import com.smartats.module.application.mapper.JobApplicationMapper;
import com.smartats.module.auth.entity.User;
import com.smartats.module.auth.mapper.UserMapper;
import com.smartats.module.interview.dto.InterviewResponse;
import com.smartats.module.interview.dto.ScheduleInterviewRequest;
import com.smartats.module.interview.dto.SubmitFeedbackRequest;
import com.smartats.module.interview.entity.InterviewRecord;
import com.smartats.module.interview.mapper.InterviewRecordMapper;
import com.smartats.module.webhook.service.WebhookService;
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
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * InterviewService unit tests
 * <p>
 * Uses Mockito to isolate external dependencies and test the core business logic.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InterviewService unit tests")
class InterviewServiceTest {

    @InjectMocks
    private InterviewService interviewService;

    @Mock
    private InterviewRecordMapper interviewRecordMapper;
    @Mock
    private JobApplicationMapper jobApplicationMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private WebhookService webhookService;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private ObjectMapper objectMapper;

    private JobApplication mockApplication;
    private User mockInterviewer;
    private ScheduleInterviewRequest scheduleRequest;

    @BeforeEach
    void setUp() {
        // Build common test data
        mockApplication = new JobApplication();
        mockApplication.setId(1L);
        mockApplication.setJobId(10L);
        mockApplication.setCandidateId(20L);
        mockApplication.setStatus(ApplicationStatus.SCREENING.getCode());

        mockInterviewer = new User();
        mockInterviewer.setId(100L);
        mockInterviewer.setUsername("Interviewer Zhang San");

        scheduleRequest = new ScheduleInterviewRequest();
        scheduleRequest.setApplicationId(1L);
        scheduleRequest.setInterviewerId(100L);
        scheduleRequest.setInterviewType("VIDEO");
        scheduleRequest.setScheduledAt(LocalDateTime.now().plusDays(1));
        scheduleRequest.setDurationMinutes(60);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Schedule interview tests
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("Schedule interview")
    class ScheduleInterviewTests {

        @Test
        @DisplayName("Schedules an interview successfully - auto-increments the round")
        void scheduleInterview_success_autoRound() {
            // Given
            given(jobApplicationMapper.selectById(1L)).willReturn(mockApplication);
            given(userMapper.selectById(100L)).willReturn(mockInterviewer);
            given(interviewRecordMapper.selectCount(any())).willReturn(0L); // no existing rounds
            given(interviewRecordMapper.insert(any(InterviewRecord.class))).willAnswer(invocation -> {
                InterviewRecord record = invocation.getArgument(0);
                record.setId(999L); // simulate auto-increment ID
                return 1;
            });

            // When
            Long interviewId = interviewService.scheduleInterview(scheduleRequest);

            // Then
            assertThat(interviewId).isEqualTo(999L);

            ArgumentCaptor<InterviewRecord> captor = ArgumentCaptor.forClass(InterviewRecord.class);
            then(interviewRecordMapper).should().insert(captor.capture());

            InterviewRecord saved = captor.getValue();
            assertThat(saved.getApplicationId()).isEqualTo(1L);
            assertThat(saved.getInterviewerId()).isEqualTo(100L);
            assertThat(saved.getRound()).isEqualTo(1);
            assertThat(saved.getStatus()).isEqualTo(InterviewStatus.SCHEDULED.getCode());
            assertThat(saved.getInterviewType()).isEqualTo("VIDEO");
        }

        @Test
        @DisplayName("Schedules an interview - application status auto-advances from SCREENING to INTERVIEW")
        void scheduleInterview_autoPromoteStatus() {
            // Given
            given(jobApplicationMapper.selectById(1L)).willReturn(mockApplication);
            given(userMapper.selectById(100L)).willReturn(mockInterviewer);
            given(interviewRecordMapper.selectCount(any())).willReturn(0L);
            given(interviewRecordMapper.insert(any(InterviewRecord.class))).willReturn(1);

            // When
            interviewService.scheduleInterview(scheduleRequest);

            // Then - verify the application status is updated to INTERVIEW
            ArgumentCaptor<JobApplication> appCaptor = ArgumentCaptor.forClass(JobApplication.class);
            then(jobApplicationMapper).should().updateById(appCaptor.capture());
            assertThat(appCaptor.getValue().getStatus()).isEqualTo(ApplicationStatus.INTERVIEW.getCode());
        }

        @Test
        @DisplayName("Schedules an interview - no status promotion when the application is already in INTERVIEW")
        void scheduleInterview_noPromoteWhenAlreadyInterview() {
            // Given
            mockApplication.setStatus(ApplicationStatus.INTERVIEW.getCode());
            given(jobApplicationMapper.selectById(1L)).willReturn(mockApplication);
            given(userMapper.selectById(100L)).willReturn(mockInterviewer);
            // first selectCount = 1L (round calculation), second = 0L (no time conflict)
            given(interviewRecordMapper.selectCount(any()))
                    .willReturn(1L)   // existing interview rounds
                    .willReturn(0L);  // no time conflict
            given(interviewRecordMapper.insert(any(InterviewRecord.class))).willReturn(1);

            // When
            interviewService.scheduleInterview(scheduleRequest);

            // Then - the application status must not be updated again
            then(jobApplicationMapper).should(never()).updateById(any(JobApplication.class));
        }

        @Test
        @DisplayName("Schedules an interview - throws when the application does not exist")
        void scheduleInterview_applicationNotFound() {
            // Given
            given(jobApplicationMapper.selectById(1L)).willReturn(null);

            // When & Then
            assertThatThrownBy(() -> interviewService.scheduleInterview(scheduleRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Application record not found");
        }

        @Test
        @DisplayName("Schedules an interview - throws when the application status does not allow scheduling")
        void scheduleInterview_invalidStatus() {
            // Given
            mockApplication.setStatus(ApplicationStatus.PENDING.getCode());
            given(jobApplicationMapper.selectById(1L)).willReturn(mockApplication);

            // When & Then
            assertThatThrownBy(() -> interviewService.scheduleInterview(scheduleRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("scheduling an interview is not allowed");
        }

        @Test
        @DisplayName("Schedules an interview - throws when the interviewer does not exist")
        void scheduleInterview_interviewerNotFound() {
            // Given
            given(jobApplicationMapper.selectById(1L)).willReturn(mockApplication);
            given(userMapper.selectById(100L)).willReturn(null);

            // When & Then
            assertThatThrownBy(() -> interviewService.scheduleInterview(scheduleRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Interviewer not found");
        }

        @Test
        @DisplayName("Schedules an interview - throws on a time conflict")
        void scheduleInterview_timeConflict() {
            // Given
            given(jobApplicationMapper.selectById(1L)).willReturn(mockApplication);
            given(userMapper.selectById(100L)).willReturn(mockInterviewer);
            // first selectCount returns the existing interview count (round calculation), second returns the conflict count
            given(interviewRecordMapper.selectCount(any()))
                    .willReturn(0L)  // round calculation
                    .willReturn(1L); // conflict detection

            // When & Then
            assertThatThrownBy(() -> interviewService.scheduleInterview(scheduleRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Interview time conflict");
        }

        @Test
        @DisplayName("Schedules an interview - triggers a Webhook event")
        void scheduleInterview_triggerWebhook() {
            // Given
            given(jobApplicationMapper.selectById(1L)).willReturn(mockApplication);
            given(userMapper.selectById(100L)).willReturn(mockInterviewer);
            given(interviewRecordMapper.selectCount(any())).willReturn(0L);
            given(interviewRecordMapper.insert(any(InterviewRecord.class))).willReturn(1);

            // When
            interviewService.scheduleInterview(scheduleRequest);

            // Then
            then(webhookService).should().sendEvent(any(), any());
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Submit feedback tests
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("Submit interview feedback")
    class SubmitFeedbackTests {

        private InterviewRecord existingRecord;
        private SubmitFeedbackRequest feedbackRequest;

        @BeforeEach
        void setUp() {
            existingRecord = new InterviewRecord();
            existingRecord.setId(1L);
            existingRecord.setApplicationId(10L);
            existingRecord.setRound(1);
            existingRecord.setStatus(InterviewStatus.SCHEDULED.getCode());

            feedbackRequest = new SubmitFeedbackRequest();
            feedbackRequest.setScore(8);
            feedbackRequest.setFeedback("Excellent technical skills, clear communication");
            feedbackRequest.setRecommendation("STRONG_YES");

            // Mock Redis operations
            lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            lenient().when(redisTemplate.delete(anyString())).thenReturn(true);
        }

        @Test
        @DisplayName("Submits feedback successfully - interview status becomes COMPLETED")
        void submitFeedback_success() {
            // Given
            given(interviewRecordMapper.selectById(1L)).willReturn(existingRecord);
            given(interviewRecordMapper.updateById(any(InterviewRecord.class))).willReturn(1);

            // When
            interviewService.submitFeedback(1L, feedbackRequest);

            // Then
            ArgumentCaptor<InterviewRecord> captor = ArgumentCaptor.forClass(InterviewRecord.class);
            then(interviewRecordMapper).should().updateById(captor.capture());

            InterviewRecord updated = captor.getValue();
            assertThat(updated.getStatus()).isEqualTo(InterviewStatus.COMPLETED.getCode());
            assertThat(updated.getScore()).isEqualTo(8);
            assertThat(updated.getFeedback()).isEqualTo("Excellent technical skills, clear communication");
            assertThat(updated.getRecommendation()).isEqualTo("STRONG_YES");
        }

        @Test
        @DisplayName("Throws when the interview does not exist")
        void submitFeedback_notFound() {
            given(interviewRecordMapper.selectById(1L)).willReturn(null);

            assertThatThrownBy(() -> interviewService.submitFeedback(1L, feedbackRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Interview record not found");
        }

        @Test
        @DisplayName("Cancelled interviews do not allow submitting feedback")
        void submitFeedback_cancelledInterview() {
            existingRecord.setStatus(InterviewStatus.CANCELLED.getCode());
            given(interviewRecordMapper.selectById(1L)).willReturn(existingRecord);

            assertThatThrownBy(() -> interviewService.submitFeedback(1L, feedbackRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("The interview has been cancelled");
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Cancel interview tests
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("Cancel interview")
    class CancelInterviewTests {

        private InterviewRecord scheduledRecord;

        @BeforeEach
        void setUp() {
            scheduledRecord = new InterviewRecord();
            scheduledRecord.setId(1L);
            scheduledRecord.setApplicationId(10L);
            scheduledRecord.setRound(1);
            scheduledRecord.setStatus(InterviewStatus.SCHEDULED.getCode());

            lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            lenient().when(redisTemplate.delete(anyString())).thenReturn(true);
        }

        @Test
        @DisplayName("Cancels an interview successfully")
        void cancelInterview_success() {
            given(interviewRecordMapper.selectById(1L)).willReturn(scheduledRecord);
            given(interviewRecordMapper.updateById(any(InterviewRecord.class))).willReturn(1);

            interviewService.cancelInterview(1L);

            ArgumentCaptor<InterviewRecord> captor = ArgumentCaptor.forClass(InterviewRecord.class);
            then(interviewRecordMapper).should().updateById(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(InterviewStatus.CANCELLED.getCode());
        }

        @Test
        @DisplayName("Completed interviews cannot be cancelled")
        void cancelInterview_alreadyCompleted() {
            scheduledRecord.setStatus(InterviewStatus.COMPLETED.getCode());
            given(interviewRecordMapper.selectById(1L)).willReturn(scheduledRecord);

            assertThatThrownBy(() -> interviewService.cancelInterview(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("The interview is already completed");
        }

        @Test
        @DisplayName("Cancelled interviews cannot be cancelled again")
        void cancelInterview_alreadyCancelled() {
            scheduledRecord.setStatus(InterviewStatus.CANCELLED.getCode());
            given(interviewRecordMapper.selectById(1L)).willReturn(scheduledRecord);

            assertThatThrownBy(() -> interviewService.cancelInterview(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("The interview has been cancelled");
        }

        @Test
        @DisplayName("Throws when the interview does not exist")
        void cancelInterview_notFound() {
            given(interviewRecordMapper.selectById(1L)).willReturn(null);

            assertThatThrownBy(() -> interviewService.cancelInterview(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Interview record not found");
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Query tests
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("Query interview records")
    class QueryTests {

        @BeforeEach
        void setUp() {
            lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        }

        @Test
        @DisplayName("Query by ID - falls back to the database on a cache miss and repopulates the cache")
        void getById_cacheMiss() throws Exception {
            // Given
            InterviewRecord record = buildFullRecord();
            User interviewer = mockInterviewer;

            given(valueOperations.get(anyString())).willReturn(null); // cache miss
            given(interviewRecordMapper.selectById(1L)).willReturn(record);
            given(userMapper.selectById(100L)).willReturn(interviewer);
            given(objectMapper.writeValueAsString(any())).willReturn("{}");

            // When
            InterviewResponse response = interviewService.getById(1L);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getInterviewerName()).isEqualTo("Interviewer Zhang San");
            then(valueOperations).should().set(anyString(), anyString(), anyLong(), any());
        }

        @Test
        @DisplayName("Query by ID - returns directly on a cache hit")
        void getById_cacheHit() throws Exception {
            // Given
            InterviewResponse cached = new InterviewResponse();
            cached.setId(1L);
            cached.setInterviewerName("Interviewer Zhang San");

            given(valueOperations.get(anyString())).willReturn("{\"id\":1}");
            given(objectMapper.readValue(anyString(), eq(InterviewResponse.class))).willReturn(cached);

            // When
            InterviewResponse response = interviewService.getById(1L);

            // Then
            assertThat(response.getId()).isEqualTo(1L);
            then(interviewRecordMapper).should(never()).selectById(any()); // no database query
        }

        @Test
        @DisplayName("Query by ID - throws when the interview does not exist")
        void getById_notFound() {
            given(valueOperations.get(anyString())).willReturn(null);
            given(interviewRecordMapper.selectById(1L)).willReturn(null);

            assertThatThrownBy(() -> interviewService.getById(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Interview record not found");
        }

        @Test
        @DisplayName("List interviews by application - empty list")
        void listByApplicationId_empty() {
            given(interviewRecordMapper.selectList(any())).willReturn(Collections.emptyList());

            List<InterviewResponse> result = interviewService.listByApplicationId(1L);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("List interviews by application - batch-fetches interviewers to avoid N+1")
        void listByApplicationId_batchQuery() {
            // Given
            InterviewRecord r1 = buildRecord(1L, 100L, 1, InterviewStatus.COMPLETED.getCode());
            InterviewRecord r2 = buildRecord(2L, 200L, 2, InterviewStatus.SCHEDULED.getCode());

            User u1 = buildUser(100L, "Interviewer A");
            User u2 = buildUser(200L, "Interviewer B");

            given(interviewRecordMapper.selectList(any())).willReturn(List.of(r1, r2));
            given(userMapper.selectBatchIds(anyCollection())).willReturn(List.of(u1, u2));

            // When
            List<InterviewResponse> result = interviewService.listByApplicationId(1L);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getInterviewerName()).isEqualTo("Interviewer A");
            assertThat(result.get(1).getInterviewerName()).isEqualTo("Interviewer B");

            // verify exactly one batch query was made (instead of N single queries)
            then(userMapper).should(times(1)).selectBatchIds(anyCollection());
            then(userMapper).should(never()).selectById(any());
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Test helper methods
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private InterviewRecord buildFullRecord() {
        InterviewRecord record = new InterviewRecord();
        record.setId(1L);
        record.setApplicationId(10L);
        record.setInterviewerId(100L);
        record.setRound(1);
        record.setInterviewType("VIDEO");
        record.setScheduledAt(LocalDateTime.now().plusDays(1));
        record.setDurationMinutes(60);
        record.setStatus(InterviewStatus.SCHEDULED.getCode());
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        return record;
    }

    private InterviewRecord buildRecord(Long id, Long interviewerId, int round, String status) {
        InterviewRecord record = new InterviewRecord();
        record.setId(id);
        record.setApplicationId(10L);
        record.setInterviewerId(interviewerId);
        record.setRound(round);
        record.setInterviewType("VIDEO");
        record.setScheduledAt(LocalDateTime.now().plusDays(1));
        record.setDurationMinutes(60);
        record.setStatus(status);
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        return record;
    }

    private User buildUser(Long id, String name) {
        User user = new User();
        user.setId(id);
        user.setUsername(name);
        return user;
    }
}
