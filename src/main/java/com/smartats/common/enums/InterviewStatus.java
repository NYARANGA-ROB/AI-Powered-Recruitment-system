package com.smartats.common.enums;

import lombok.Getter;

/**
 * Interview status enum
 */
@Getter
public enum InterviewStatus {

    SCHEDULED("SCHEDULED", "Scheduled"),
    COMPLETED("COMPLETED", "Completed"),
    CANCELLED("CANCELLED", "Cancelled"),
    NO_SHOW("NO_SHOW", "No-show");

    private final String code;
    private final String description;

    InterviewStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static InterviewStatus fromCode(String code) {
        if (code == null) return null;
        for (InterviewStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }

    public static String getDescriptionByCode(String code) {
        InterviewStatus status = fromCode(code);
        return status != null ? status.description : code;
    }
}
