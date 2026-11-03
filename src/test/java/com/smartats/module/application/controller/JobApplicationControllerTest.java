package com.smartats.module.application.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartats.common.exception.BusinessException;
import com.smartats.common.result.ResultCode;
import com.smartats.config.SecurityConfig;
import com.smartats.module.application.dto.*;
import com.smartats.module.application.service.JobApplicationService;
import com.smartats.module.application.service.MatchScoreService;
import com.smartats.module.auth.filter.JwtAuthenticationFilter;
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

import java.math.BigDecimal;
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
 * JobApplicationController integration tests
 * <p>
 * All application endpoints require authentication (anyRequest().authenticated()).
 * Uses @Import SecurityConfig to load the custom security rules.
 */
@WebMvcTest(JobApplicationController.class)
@Import(SecurityConfig.class)
@DisplayName("JobApplicationController integration tests")
class JobApplicationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JobApplicationService jobApplicationService;

    @MockBean
    private MatchScoreService matchScoreService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

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

    private static UsernamePasswordAuthenticationToken mockAuth(Long userId) {
        return new UsernamePasswordAuthenticationToken(
                userId, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_HR"))
        );
    }

    private static ApplicationResponse sampleResponse() {
        ApplicationResponse resp = new ApplicationResponse();
        resp.setId(1L);
        resp.setJobId(10L);
        resp.setJobTitle("Senior Java Engineer");
        resp.setCandidateId(20L);
        resp.setCandidateName("Zhang San");
        resp.setStatus("PENDING");
        resp.setStatusDesc("Pending screening");
        resp.setMatchScore(new BigDecimal("85.5"));
        resp.setMatchReasons(List.of("High skill match", "Experience meets the requirement"));
        resp.setAppliedAt(LocalDateTime.of(2026, 2, 24, 10, 0, 0));
        resp.setUpdatedAt(LocalDateTime.of(2026, 2, 24, 10, 0, 0));
        return resp;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Create application
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("POST /applications - create application")
    class CreateApplicationTests {

        @Test
        @DisplayName("Authenticated user creates an application - 200")
        void create_authenticated_success() throws Exception {
            CreateApplicationRequest request = new CreateApplicationRequest();
            request.setJobId(10L);
            request.setCandidateId(20L);

            given(jobApplicationService.createApplication(any(CreateApplicationRequest.class)))
                    .willReturn(1L);

            mockMvc.perform(post("/applications")
                            .with(authentication(mockAuth(1L)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(1));
        }

        @Test
        @DisplayName("Unauthenticated application creation - 401/403")
        void create_unauthenticated() throws Exception {
            CreateApplicationRequest request = new CreateApplicationRequest();
            request.setJobId(10L);
            request.setCandidateId(20L);

            mockMvc.perform(post("/applications")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().is4xxClientError());
        }

        @Test
        @DisplayName("Validation failure - missing jobId")
        void create_validationFail() throws Exception {
            CreateApplicationRequest request = new CreateApplicationRequest();
            // jobId and candidateId are empty, violating @NotNull

            mockMvc.perform(post("/applications")
                            .with(authentication(mockAuth(1L)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ResultCode.BAD_REQUEST.getCode()));
        }

        @Test
        @DisplayName("Duplicate application - business exception")
        void create_duplicate() throws Exception {
            CreateApplicationRequest request = new CreateApplicationRequest();
            request.setJobId(10L);
            request.setCandidateId(20L);

            given(jobApplicationService.createApplication(any(CreateApplicationRequest.class)))
                    .willThrow(new BusinessException(ResultCode.BAD_REQUEST, "This candidate has already applied for the position"));

            mockMvc.perform(post("/applications")
                            .with(authentication(mockAuth(1L)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ResultCode.BAD_REQUEST.getCode()))
                    .andExpect(jsonPath("$.message").value("This candidate has already applied for the position"));
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Update application status
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("PUT /applications/{id}/status - update status")
    class UpdateStatusTests {

        @Test
        @DisplayName("Legal status change - 200")
        void updateStatus_success() throws Exception {
            UpdateApplicationStatusRequest request = new UpdateApplicationStatusRequest();
            request.setStatus("SCREENING");
            request.setHrNotes("Passed initial screening");

            willDoNothing().given(jobApplicationService).updateStatus(eq(1L), any(UpdateApplicationStatusRequest.class));

            mockMvc.perform(put("/applications/1/status")
                            .with(authentication(mockAuth(1L)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("Illegal status change - business exception")
        void updateStatus_invalidTransition() throws Exception {
            UpdateApplicationStatusRequest request = new UpdateApplicationStatusRequest();
            request.setStatus("OFFER");

            willThrow(new BusinessException(ResultCode.APPLICATION_STATUS_INVALID, "Transition from the current status is not allowed"))
                    .given(jobApplicationService).updateStatus(eq(1L), any(UpdateApplicationStatusRequest.class));

            mockMvc.perform(put("/applications/1/status")
                            .with(authentication(mockAuth(1L)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ResultCode.APPLICATION_STATUS_INVALID.getCode()));
        }

        @Test
        @DisplayName("Parameter validation - illegal status value")
        void updateStatus_invalidValue() throws Exception {
            UpdateApplicationStatusRequest request = new UpdateApplicationStatusRequest();
            request.setStatus("INVALID");  // Not among the @Pattern allowed values

            mockMvc.perform(put("/applications/1/status")
                            .with(authentication(mockAuth(1L)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ResultCode.BAD_REQUEST.getCode()));
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Query
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("GET /applications - query")
    class QueryTests {

        @Test
        @DisplayName("Query application detail - 200")
        void getById_success() throws Exception {
            given(jobApplicationService.getById(1L)).willReturn(sampleResponse());

            mockMvc.perform(get("/applications/1")
                            .with(authentication(mockAuth(1L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.jobTitle").value("Senior Java Engineer"))
                    .andExpect(jsonPath("$.data.candidateName").value("Zhang San"))
                    .andExpect(jsonPath("$.data.status").value("PENDING"));
        }

        @Test
        @DisplayName("Application not found - business exception")
        void getById_notFound() throws Exception {
            given(jobApplicationService.getById(999L))
                    .willThrow(new BusinessException(ResultCode.NOT_FOUND, "Application record not found"));

            mockMvc.perform(get("/applications/999")
                            .with(authentication(mockAuth(1L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ResultCode.NOT_FOUND.getCode()))
                    .andExpect(jsonPath("$.message").value("Application record not found"));
        }

        @Test
        @DisplayName("Query application list by job - 200")
        void listByJobId_success() throws Exception {
            Page<ApplicationResponse> page = new Page<>(1, 10, 1);
            page.setRecords(List.of(sampleResponse()));

            given(jobApplicationService.listByJobId(10L, 1, 10)).willReturn(page);

            mockMvc.perform(get("/applications/job/10")
                            .with(authentication(mockAuth(1L)))
                            .param("pageNum", "1")
                            .param("pageSize", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.records[0].jobId").value(10));
        }

        @Test
        @DisplayName("Query application list by candidate - 200")
        void listByCandidateId_success() throws Exception {
            Page<ApplicationResponse> page = new Page<>(1, 10, 1);
            page.setRecords(List.of(sampleResponse()));

            given(jobApplicationService.listByCandidateId(20L, 1, 10)).willReturn(page);

            mockMvc.perform(get("/applications/candidate/20")
                            .with(authentication(mockAuth(1L)))
                            .param("pageNum", "1")
                            .param("pageSize", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.records[0].candidateId").value(20));
        }

        @Test
        @DisplayName("Comprehensive query of the application list - 200")
        void listApplications_success() throws Exception {
            Page<ApplicationResponse> page = new Page<>(1, 10, 1);
            page.setRecords(List.of(sampleResponse()));

            given(jobApplicationService.listApplications(any(ApplicationQueryRequest.class))).willReturn(page);

            mockMvc.perform(get("/applications")
                            .with(authentication(mockAuth(1L)))
                            .param("pageNum", "1")
                            .param("pageSize", "10")
                            .param("status", "PENDING"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.records").isArray());
        }
    }
}
