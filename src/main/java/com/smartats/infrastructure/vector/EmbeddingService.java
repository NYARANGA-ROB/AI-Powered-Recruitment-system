package com.smartats.infrastructure.vector;

import com.smartats.module.candidate.entity.Candidate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.StringJoiner;

/**
 * Candidate text embedding service
 * <p>
 * Responsibilities:
 * 1. Assemble the structured candidate information into text that can be embedded
 * 2. Call the Zhipu embedding-3 model to produce a 1024-dimensional vector
 * <p>
 * Embedding text construction strategy:
 * - Concatenate name, education, school, major, skills, work experience, project experience and self evaluation
 * - Truncate to 6000 characters when the text is too long (embedding-3 supports up to 8192 tokens)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    /** Output dimension of embedding-3 */
    public static final int EMBEDDING_DIMENSION = 1024;

    /** Maximum character length of the embedding text (keeps a margin below the embedding-3 limit of 8192 tokens) */
    private static final int MAX_TEXT_LENGTH = 6000;

    /**
     * Generate the embedding vector for a candidate
     *
     * @param candidate the candidate entity
     * @return a 1024-dimensional float vector
     */
    public List<Float> generateCandidateEmbedding(Candidate candidate) {
        String text = buildCandidateText(candidate);
        return embed(text);
    }

    /**
     * Generate the embedding vector for a search query
     *
     * @param query the search query text
     * @return a 1024-dimensional float vector
     */
    public List<Float> generateQueryEmbedding(String query) {
        return embed(query);
    }

    /**
     * Build the AI summary text for a candidate (stored in candidates.ai_summary)
     *
     * @param candidate the candidate entity
     * @return the structured summary text
     */
    public String buildCandidateText(Candidate candidate) {
        StringJoiner joiner = new StringJoiner("\n");

        // Basic info
        appendIfPresent(joiner, "Name", candidate.getName());
        appendIfPresent(joiner, "Gender", candidate.getGender());

        // Education
        appendIfPresent(joiner, "Education", candidate.getEducation());
        appendIfPresent(joiner, "School", candidate.getSchool());
        appendIfPresent(joiner, "Major", candidate.getMajor());

        // Work info
        if (candidate.getWorkYears() != null) {
            joiner.add("Work Years: " + candidate.getWorkYears() + " years");
        }
        appendIfPresent(joiner, "Current Company", candidate.getCurrentCompany());
        appendIfPresent(joiner, "Current Position", candidate.getCurrentPosition());

        // Skill tags
        if (candidate.getSkills() != null && !candidate.getSkills().isEmpty()) {
            joiner.add("Skills: " + String.join(", ", candidate.getSkills()));
        }

        // Work experience — extract the company and position
        if (candidate.getWorkExperience() != null) {
            StringBuilder workExp = new StringBuilder("Work Experience: ");
            for (var exp : candidate.getWorkExperience()) {
                Object company = exp.get("company");
                Object position = exp.get("position");
                Object description = exp.get("description");
                if (company != null) workExp.append(company);
                if (position != null) workExp.append(" ").append(position);
                if (description != null) workExp.append(" — ").append(description);
                workExp.append("; ");
            }
            joiner.add(workExp.toString().trim());
        }

        // Project experience — extract the project name and description
        if (candidate.getProjectExperience() != null) {
            StringBuilder projExp = new StringBuilder("Project Experience: ");
            for (var exp : candidate.getProjectExperience()) {
                Object name = exp.get("name");
                Object description = exp.get("description");
                Object role = exp.get("role");
                if (name != null) projExp.append(name);
                if (role != null) projExp.append("(").append(role).append(")");
                if (description != null) projExp.append(" — ").append(description);
                projExp.append("; ");
            }
            joiner.add(projExp.toString().trim());
        }

        // Self evaluation
        appendIfPresent(joiner, "Self Evaluation", candidate.getSelfEvaluation());

        String text = joiner.toString();

        // Truncation guard
        if (text.length() > MAX_TEXT_LENGTH) {
            text = text.substring(0, MAX_TEXT_LENGTH);
            log.warn("Candidate embedding text is too long and has been truncated: candidateId={}, originalLength={}",
                    candidate.getId(), joiner.toString().length());
        }

        return text;
    }

    /**
     * Call the embedding model to generate the vector
     */
    private List<Float> embed(String text) {
        log.debug("Generating an embedding: textLength={}", text.length());

        EmbeddingResponse response = embeddingModel.call(
                new org.springframework.ai.embedding.EmbeddingRequest(
                        List.of(text),
                        org.springframework.ai.openai.OpenAiEmbeddingOptions.builder()
                                .build()
                )
        );

        float[] embedding = response.getResult().getOutput();
        List<Float> result = new java.util.ArrayList<>(embedding.length);
        for (float v : embedding) {
            result.add(v);
        }

        log.debug("Embedding generated: dimension={}", result.size());
        return result;
    }

    private void appendIfPresent(StringJoiner joiner, String label, String value) {
        if (value != null && !value.isBlank()) {
            joiner.add(label + ": " + value);
        }
    }
}
