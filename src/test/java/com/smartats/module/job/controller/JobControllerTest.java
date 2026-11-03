package com.smartats.module.job.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartats.common.enums.JobStatus;
import com.smartats.common.exception.BusinessException;
import com.smartats.common.result.ResultCode;
import com.smartats.config.SecurityConfig;
import com.smartats.module.auth.filter.JwtAuthenticationFilter;
import com.smartats.module.job.dto.request.CreateJobRequest;
import com.smartats.module.job.dto.request.UpdateJobRequest;
import com.smartats.module.job.dto.response.JobResponse;
import com.smartats.module.job.service.JobService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * JobController integration tests
 * <p>
 * Uses a @WebMvcTest slice test, loading only the web layer.
 * @Import SecurityConfig to load the custom security rules (permitAll, etc.).
 * JwtAuthenticationFilter is mocked to pass requests through; only the controller + security policy are tested.
 */
@WebMvcTest(JobController.class)
@Import(SecurityConfig.class)
@DisplayName("JobController integration tests")
class JobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JobService jobService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * Make the mocked JwtAuthenticationFilter pass the request through to the FilterChain,
     * otherwise the Mockito default void method does nothing and the request would be swallowed.
     */
    @BeforeEach
    void configureMockFilter() throws Exception {
        lenient().doAnswer(invocation -> {
            HttpServletRequest req = invocation.getArgument(0);
            HttpServletResponse res = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(req, res);
            return null;
        }).when(jwtAuthenticationFilter)
                .doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class), any(FilterChain.class));
    }

    // ━━━━━━━━━━━━━━ Utility methods ━━━━━━━━━━━━━━

    /**
     * Create a mock authentication (principal=Long userId)
     */
    private static UsernamePasswordAuthenticationToken mockAuth(Long userId) {
        return new UsernamePasswordAuthenticationToken(
                userId, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_HR"))
        );
    }

    private static JobResponse sampleJobResponse() {
        JobResponse resp = new JobResponse();
        resp.setId(1L);
        resp.setTitle("Senior Java Engineer");
        resp.setDepartment("Engineering Department");
        resp.setStatus(JobStatus.PUBLISHED.getCode());
        resp.setStatusDesc("Published");
        resp.setViewCount(100);
        resp.setCreatedAt(LocalDateTime.of(2026, 2, 24, 10, 0, 0));
        resp.setUpdatedAt(LocalDateTime.of(2026, 2, 24, 10, 0, 0));
        return resp;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Create job
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("POST /jobs - create job")
    class CreateJobTests {

        @Test
        @DisplayName("Authenticated user creates a job - 200")
        void createJob_authenticated_success() throws Exception {
            CreateJobRequest request = new CreateJobRequest();
            request.setTitle("Senior Java Engineer");
            request.setDescription("Responsible for the design and development of core systems");
            request.setRequirements("5+ years of Java experience");
            request.setSalaryMin(25);
            request.setSalaryMax(50);

            given(jobService.createJob(any(CreateJobRequest.class), eq(1L))).willReturn(100L);

            mockMvc.perform(post("/jobs")
                            .with(authentication(mockAuth(1L)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(100));
        }

        @Test
        @DisplayName("Unauthenticated job creation - 401/403")
        void createJob_unauthenticated() throws Exception {
            CreateJobRequest request = new CreateJobRequest();
            request.setTitle("Senior Java Engineer");
            request.setDescription("Responsible for the design and development of core systems");
            request.setRequirements("5+ years of Java experience");
            request.setSalaryMin(25);
            request.setSalaryMax(50);

            mockMvc.perform(post("/jobs")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().is4xxClientError());
        }

        @Test
        @DisplayName("Validation failure - missing required fields")
        void createJob_validationFail() throws Exception {
            CreateJobRequest request = new CreateJobRequest();
            // title and description are empty, violating @NotBlank

            mockMvc.perform(post("/jobs")
                            .with(authentication(mockAuth(1L)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())  // @ResponseStatus(BAD_REQUEST)
                    .andExpect(jsonPath("$.code").value(ResultCode.BAD_REQUEST.getCode()));
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Job detail (public endpoint)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("GET /jobs/{id} - job detail (public)")
    class GetJobDetailTests {

        @Test
        @DisplayName("Anonymous access to a public job - 200")
        void getJobDetail_anonymous_success() throws Exception {
            given(jobService.getJobDetail(1L)).willReturn(sampleJobResponse());

            mockMvc.perform(get("/jobs/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.title").value("Senior Java Engineer"))
                    .andExpect(jsonPath("$.data.viewCount").value(100));
        }

        @Test
        @DisplayName("Position not found - business exception")
        void getJobDetail_notFound() throws Exception {
            given(jobService.getJobDetail(999L))
                    .willThrow(new BusinessException(ResultCode.NOT_FOUND, "Position not found"));

            mockMvc.perform(get("/jobs/999"))
                    .andExpect(status().isOk())  // GlobalExceptionHandler returns a 200 body
                    .andExpect(jsonPath("$.code").value(ResultCode.NOT_FOUND.getCode()))
                    .andExpect(jsonPath("$.message").value("Position not found"));
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Publish/close/delete job
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("POST /jobs/{id}/publish & close, DELETE /jobs/{id}")
    class StateChangeTests {

        @Test
        @DisplayName("Publish a job - 200")
        void publishJob_success() throws Exception {
            willDoNothing().given(jobService).publishJob(1L, 1L);

            mockMvc.perform(post("/jobs/1/publish")
                            .with(authentication(mockAuth(1L)))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("Close a job - 200")
        void closeJob_success() throws Exception {
            willDoNothing().given(jobService).closeJob(1L, 1L);

            mockMvc.perform(post("/jobs/1/close")
                            .with(authentication(mockAuth(1L)))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("Delete a job - 200")
        void deleteJob_success() throws Exception {
            willDoNothing().given(jobService).deleteJob(1L, 1L);

            mockMvc.perform(delete("/jobs/1")
                            .with(authentication(mockAuth(1L)))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("Publishing without permission - business exception")
        void publishJob_forbidden() throws Exception {
            willThrow(new BusinessException(ResultCode.FORBIDDEN, "No permission to operate on this position"))
                    .given(jobService).publishJob(1L, 1L);

            mockMvc.perform(post("/jobs/1/publish")
                            .with(authentication(mockAuth(1L)))
                            .with(csrf()))
                    .andExpect(status().isOk())  // Handled by GlobalExceptionHandler
                    .andExpect(jsonPath("$.code").value(ResultCode.FORBIDDEN.getCode()));
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Hot jobs (public endpoint)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("GET /jobs/hot - hot jobs (public)")
    class HotJobsTests {

        @Test
        @DisplayName("Anonymous retrieval of hot jobs - 200")
        void getHotJobs_success() throws Exception {
            given(jobService.getHotJobs(10)).willReturn(List.of(sampleJobResponse()));

            mockMvc.perform(get("/jobs/hot").param("limit", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].title").value("Senior Java Engineer"));
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Update job
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("PUT /jobs - update job")
    class UpdateJobTests {

        @Test
        @DisplayName("Successful update - 200")
        void updateJob_success() throws Exception {
            UpdateJobRequest request = new UpdateJobRequest();
            request.setId(1L);
            request.setTitle("Updated title");

            willDoNothing().given(jobService).updateJob(any(UpdateJobRequest.class), eq(1L));

            mockMvc.perform(put("/jobs")
                            .with(authentication(mockAuth(1L)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Job list (public endpoint)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("GET /jobs - job list (public)")
    class JobListTests {

        @Test
        @DisplayName("Anonymous paged query - 200")
        void listJobs_success() throws Exception {
            Page<JobResponse> page = new Page<>(1, 10, 1);
            page.setRecords(List.of(sampleJobResponse()));

            given(jobService.getJobList(any())).willReturn(page);

            mockMvc.perform(get("/jobs")
                            .param("pageNum", "1")
                            .param("pageSize", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.records[0].title").value("Senior Java Engineer"));
        }
    }
}
