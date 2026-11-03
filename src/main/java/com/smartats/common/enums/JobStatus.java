package com.smartats.common.enums;

import lombok.Getter;

/**
 * Job status enum
 */
@Getter
public enum JobStatus {

    DRAFT("DRAFT", "Draft"),
    PUBLISHED("PUBLISHED", "Published"),
    CLOSED("CLOSED", "Closed");

    private final String code;
    private final String description;

    JobStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * Get the enum constant by code
     */
    public static JobStatus fromCode(String code) {
        if (code == null) return null;
        for (JobStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }

    /**
     * Get the display description; returns the raw value if not found
     */
    public static String getDescriptionByCode(String code) {
        JobStatus status = fromCode(code);
        return status != null ? status.description : code;
    }
}
