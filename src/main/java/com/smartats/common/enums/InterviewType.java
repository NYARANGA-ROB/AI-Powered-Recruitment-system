package com.smartats.common.enums;

import lombok.Getter;

/**
 * Interview type enum
 */
@Getter
public enum InterviewType {

    PHONE("PHONE", "Phone interview"),
    VIDEO("VIDEO", "Video interview"),
    ONSITE("ONSITE", "Onsite interview"),
    WRITTEN_TEST("WRITTEN_TEST", "Written test");

    private final String code;
    private final String description;

    InterviewType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static InterviewType fromCode(String code) {
        if (code == null) return null;
        for (InterviewType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }

    public static String getDescriptionByCode(String code) {
        InterviewType type = fromCode(code);
        return type != null ? type.description : code;
    }
}
