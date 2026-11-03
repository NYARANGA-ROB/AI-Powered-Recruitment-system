package com.smartats.module.webhook.enums;

import lombok.Getter;

/**
 * Webhook event types
 */
@Getter
public enum WebhookEventType {

    /**
     * Resume-related events
     */
    RESUME_UPLOADED("resume.uploaded", "New resume uploaded"),
    RESUME_PARSE_COMPLETED("resume.parse_completed", "Resume parsing completed"),
    RESUME_PARSE_FAILED("resume.parse_failed", "Resume parsing failed"),

    /**
     * Candidate-related events
     */
    CANDIDATE_CREATED("candidate.created", "Candidate profile created"),
    CANDIDATE_UPDATED("candidate.updated", "Candidate profile updated"),

    /**
     * Job application events
     */
    APPLICATION_SUBMITTED("application.submitted", "Job application submitted"),
    APPLICATION_STATUS_CHANGED("application.status_changed", "Application status changed"),

    /**
     * Interview-related events
     */
    INTERVIEW_SCHEDULED("interview.scheduled", "Interview scheduled"),
    INTERVIEW_COMPLETED("interview.completed", "Interview completed"),
    INTERVIEW_CANCELLED("interview.cancelled", "Interview cancelled"),

    /**
     * System events
     */
    SYSTEM_ERROR("system.error", "System error"),
    SYSTEM_MAINTENANCE("system.maintenance", "System maintenance notice");

    private final String code;
    private final String description;

    WebhookEventType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static WebhookEventType fromCode(String code) {
        for (WebhookEventType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
