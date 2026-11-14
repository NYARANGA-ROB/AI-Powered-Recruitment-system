package com.smartaps.module.application.service;

import com.smartaps.infrastructure.vector.EmbeddingService;
import com.smartaps.infrastructure.vector.VectorStoreService;
import com.smartaps.module.application.mapper.JobApplicationMapper;
import com.smartaps.module.candidate.entity.Candidate;
import com.smartaps.module.candidate.mapper.CandidateMapper;
import com.smartaps.module.job.entity.Job;
import com.smartaps.module.job.mapper.JobMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class MatchScoreServiceTest {

    @Mock
    private EmbeddingService embeddingService;
    @Mock
    private VectorStoreService vectorStoreService;
    @Mock
    private JobMapper jobMapper;
    @Mock
    private CandidateMapper candidateMapper;
    @Mock
    private JobApplicationMapper jobApplicationMapper;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private MatchScoreService matchScoreService;

    @Test
    void scoresNormalizedEnglishEducationLevels() {
        Job job = new Job();
        job.setEducation("MASTER");
        Candidate candidate = new Candidate();
        candidate.setEducation("BACHELOR");

        assertThat(matchScoreService.calculateEducationScore(job, candidate, new ArrayList<>()))
                .isEqualTo(75.0);
    }

    @Test
    void unspecifiedEducationRequirementDoesNotReduceTheScore() {
        Job job = new Job();
        job.setEducation("UNSPECIFIED");
        Candidate candidate = new Candidate();
        candidate.setEducation("HIGH_SCHOOL");

        assertThat(matchScoreService.calculateEducationScore(job, candidate, new ArrayList<>()))
                .isEqualTo(100.0);
    }
}
