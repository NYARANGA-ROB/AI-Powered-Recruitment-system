package com.smartats.module.resume.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartats.common.exception.BusinessException;
import com.smartats.common.result.ResultCode;
import com.smartats.module.resume.dto.CandidateInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Resume AI parsing service (Zhipu AI)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeParseService {

    private final OpenAiChatModel chatModel;
    private final ObjectMapper objectMapper;

    @Value("${spring.ai.openai.chat.options.model:glm-4-flash-250414}")
    private String model;

    public CandidateInfo parseResume(String resumeContent) {
        return parseResumeInternal(resumeContent).candidateInfo();
    }

    /**
     * Parse a resume and return the candidate information plus the raw AI response (for storing rawJson)
     */
    public ParseResult parseResumeWithRaw(String resumeContent) {
        return parseResumeInternal(resumeContent);
    }

    public record ParseResult(CandidateInfo candidateInfo, String rawResponse) {}

    private ParseResult parseResumeInternal(String resumeContent) {
        log.info("Starting resume parsing with Zhipu AI: model={}, contentLength={}", model, resumeContent.length());

        // Content pre-check: avoid sending empty content to the AI
        if (resumeContent == null || resumeContent.trim().length() < 20) {
            log.error("Resume text content too short or empty, cannot parse. Actual content: [{}]", resumeContent);
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "Resume content extraction failed, possibly a scanned document or a specially encoded PDF, extracted length=" + (resumeContent == null ? 0 : resumeContent.trim().length()));
        }

        log.info("First 300 characters of the extracted resume text: [{}]",
                resumeContent.trim().substring(0, Math.min(300, resumeContent.trim().length())));

        try {
            // 1. Build the prompt (optimized for Chinese resumes)
            String prompt = buildPromptForChineseResume(resumeContent);

            // 2. Call Zhipu AI
            Prompt aiPrompt = new Prompt(new UserMessage(prompt));
            String responseContent = chatModel.call(aiPrompt).getResult().getOutput().getContent();

            log.info("Raw Zhipu AI response: {}", responseContent);

            // 3. Clean the response content (remove possible markdown code block markers)
            String cleanedResponse = cleanMarkdownCodeBlock(responseContent);
            log.info("Cleaned JSON: {}", cleanedResponse);

            // 4. Parse the JSON response
            CandidateInfo candidateInfo = objectMapper.readValue(cleanedResponse, CandidateInfo.class);

            log.info("Zhipu AI parsing succeeded: name={}, phone={}, email={}",
                    candidateInfo.getName(), candidateInfo.getPhone(), candidateInfo.getEmail());

            // Warn on empty results
            if (!StringUtils.hasText(candidateInfo.getName()) && !StringUtils.hasText(candidateInfo.getPhone())) {
                log.warn("All key fields of the AI parsing result are null; the AI response may be malformed. Raw response: {}", responseContent);
                throw new BusinessException(ResultCode.AI_SERVICE_ERROR,
                        "AI parsing returned an empty result, please check the logs. Raw AI response: " + responseContent);
            }

            return new ParseResult(candidateInfo, responseContent);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // For rate-limit (429) errors log only a warning (to avoid redundant stacks); for other errors log the full exception
            if (isRateLimitError(e)) {
                log.warn("Zhipu AI rate limited (429), retry will be handled by the MQ layer: {}", e.getMessage());
            } else {
                log.error("Zhipu AI parsing failed", e);
            }
            throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "Resume parsing failed: " + e.getMessage());
        }
    }

    private String buildPromptForChineseResume(String resumeContent) {
        return """
                You are a professional resume information extraction assistant. Please extract structured information from the following Chinese resume content and return it in JSON format.

                ## Field extraction guide

                ### Basic information
                - name: full name
                - phone: mobile number (11 digits)
                - email: email address
                - gender: gender (MALE/FEMALE)
                - age: age

                ### Education information
                - education: education level, use one of the stored values (高中/专科/本科/硕士研究生/博士研究生)
                - school: graduated school
                - major: major
                - graduationYear: graduation year (4-digit integer)

                ### Work information
                - workYears: years of experience (integer years)
                - currentCompany: current company
                - currentPosition: current position

                ### Skills and experience
                - skills: skill list (keep only technical skills)
                - workExperience: array of work experience, containing company, position, startDate, endDate, description
                - projectExperience: array of project experience, containing name, role, startDate, endDate, description, technologies
                - selfEvaluation: self evaluation

                ## Date format conversion

                - "January 2020" → "2020-01"
                - "2020.01" → "2020-01"
                - "up to now" → "Present"

                ## Notes

                1. Use null for fields that cannot be extracted
                2. Unify date formats as yyyy-MM
                3. Keep only core technologies in the skill list
                4. Sort work experience in reverse chronological order
                5. Return only JSON, without markdown code block markers
                6. Each element in the workExperience and projectExperience arrays must also be a JSON object

                ## Resume content

                %s

                Please return the extracted JSON (do not include any markdown markers):
                """.formatted(resumeContent);
    }

    /**
     * Clean markdown code block markers from the response
     */
    private String cleanMarkdownCodeBlock(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }

        content = content.trim();

        // Remove the leading ```json or ```
        if (content.startsWith("```json")) {
            content = content.substring(7);
        } else if (content.startsWith("```")) {
            content = content.substring(3);
        }

        // Remove the trailing ```
        if (content.endsWith("```")) {
            content = content.substring(0, content.length() - 3);
        }

        return content.trim();
    }

    /**
     * Determine whether this is an API rate-limit (429 Too Many Requests) error
     */
    private boolean isRateLimitError(Exception e) {
        return containsRateLimitKeyword(e.getMessage())
                || (e.getCause() != null && containsRateLimitKeyword(e.getCause().getMessage()));
    }

    private boolean containsRateLimitKeyword(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase();
        return lower.contains("429") || lower.contains("rate limit") || lower.contains("too many requests");
    }
}