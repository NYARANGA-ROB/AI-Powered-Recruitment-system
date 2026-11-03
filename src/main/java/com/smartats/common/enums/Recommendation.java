package com.smartats.common.enums;

import lombok.Getter;

/**
 * Interview recommendation conclusion enum
 */
@Getter
public enum Recommendation {

    STRONG_YES("STRONG_YES", "Strongly recommend"),
    YES("YES", "Recommend"),
    NEUTRAL("NEUTRAL", "Neutral"),
    NO("NO", "Do not recommend"),
    STRONG_NO("STRONG_NO", "Strongly do not recommend");

    private final String code;
    private final String description;

    Recommendation(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static Recommendation fromCode(String code) {
        if (code == null) return null;
        for (Recommendation r : values()) {
            if (r.code.equals(code)) {
                return r;
            }
        }
        return null;
    }

    public static String getDescriptionByCode(String code) {
        Recommendation r = fromCode(code);
        return r != null ? r.description : code;
    }
}
