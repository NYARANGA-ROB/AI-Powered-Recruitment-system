package com.smartats.module.candidate.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartats.common.exception.BusinessException;
import com.smartats.common.result.ResultCode;
import com.smartats.config.SecurityConfig;
import com.smartats.module.auth.filter.JwtAuthenticationFilter;
import com.smartats.module.candidate.dto.SmartSearchRequest;
import com.smartats.module.candidate.dto.SmartSearchResponse;
import com.smartats.module.candidate.dto.SmartSearchResponse.MatchedCandidate;
import com.smartats.module.candidate.service.SmartSearchService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * SmartSearchController integration tests
 */
@WebMvcTest(SmartSearchController.class)
@Import(SecurityConfig.class)
@DisplayName("SmartSearchController integration tests")
class SmartSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SmartSearchService smartSearchService;

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

    private UsernamePasswordAuthenticationToken hrAuth() {
        return new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_HR"))
        );
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Normal search
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("POST /candidates/smart-search")
    class SmartSearchTests {

        @Test
        @DisplayName("Returns search results successfully")
        void shouldReturnSearchResults() throws Exception {
            // Given
            SmartSearchResponse response = new SmartSearchResponse();
            response.setQuery("Java backend developer");
            response.setTotalMatches(1);

            MatchedCandidate mc = new MatchedCandidate();
            mc.setCandidateId(1L);
            mc.setName("Zhang San");
            mc.setMatchScore(0.85);
            mc.setCurrentPosition("Senior backend engineer");
            mc.setSkills(List.of("Java", "Spring Boot"));
            response.setCandidates(List.of(mc));

            given(smartSearchService.search(any(SmartSearchRequest.class))).willReturn(response);

            SmartSearchRequest request = new SmartSearchRequest();
            request.setQuery("Java backend developer");
            request.setTopK(10);

            // When & Then
            mockMvc.perform(post("/candidates/smart-search")
                            .with(authentication(hrAuth()))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.query").value("Java backend developer"))
                    .andExpect(jsonPath("$.data.totalMatches").value(1))
                    .andExpect(jsonPath("$.data.candidates[0].candidateId").value(1))
                    .andExpect(jsonPath("$.data.candidates[0].name").value("Zhang San"))
                    .andExpect(jsonPath("$.data.candidates[0].matchScore").value(0.85));
        }

        @Test
        @DisplayName("Empty query returns 400")
        void shouldReturn400WhenQueryEmpty() throws Exception {
            SmartSearchRequest request = new SmartSearchRequest();
            request.setQuery("");

            mockMvc.perform(post("/candidates/smart-search")
                            .with(authentication(hrAuth()))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Unauthenticated returns 403")
        void shouldReturn403WhenNotAuthenticated() throws Exception {
            SmartSearchRequest request = new SmartSearchRequest();
            request.setQuery("Java development");

            mockMvc.perform(post("/candidates/smart-search")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AI service failure returns an error")
        void shouldReturnErrorWhenAiServiceFails() throws Exception {
            given(smartSearchService.search(any(SmartSearchRequest.class)))
                    .willThrow(new BusinessException(ResultCode.AI_SERVICE_ERROR, "AI vectorization service unavailable"));

            SmartSearchRequest request = new SmartSearchRequest();
            request.setQuery("Python data analysis");

            mockMvc.perform(post("/candidates/smart-search")
                            .with(authentication(hrAuth()))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())  // BusinessException is handled by GlobalExceptionHandler
                    .andExpect(jsonPath("$.code").value(ResultCode.AI_SERVICE_ERROR.getCode()));
        }

        @Test
        @DisplayName("Returns an empty list when nothing matches")
        void shouldReturnEmptyList() throws Exception {
            SmartSearchResponse emptyResponse = new SmartSearchResponse();
            emptyResponse.setQuery("blockchain engineer");
            emptyResponse.setTotalMatches(0);
            emptyResponse.setCandidates(List.of());

            given(smartSearchService.search(any(SmartSearchRequest.class))).willReturn(emptyResponse);

            SmartSearchRequest request = new SmartSearchRequest();
            request.setQuery("blockchain engineer");

            mockMvc.perform(post("/candidates/smart-search")
                            .with(authentication(hrAuth()))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalMatches").value(0))
                    .andExpect(jsonPath("$.data.candidates").isEmpty());
        }
    }
}
