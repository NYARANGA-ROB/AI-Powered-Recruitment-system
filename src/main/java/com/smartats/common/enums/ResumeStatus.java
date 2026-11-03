package com.smartats.common.enums;

import lombok.Getter;

/**
 * Resume parsing status enum
 */
@Getter
public enum ResumeStatus {

    PARSING("PARSING", "Parsing"),
    COMPLETED("COMPLETED", "Completed"),
    FAILED("FAILED", "Parsing failed");

    private final String code;
    private final String description;

    ResumeStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static ResumeStatus fromCode(String code) {
        if (code == null) return null;
        for (ResumeStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }

    public static String getDescriptionByCode(String code) {
        ResumeStatus status = fromCode(code);
        return status != null ? status.description : code;
    }
}
