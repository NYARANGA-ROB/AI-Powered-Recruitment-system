package com.smartats.common.enums;

import lombok.Getter;

import java.util.Map;
import java.util.Set;

/**
 * Job application status enum
 * <p>
 * Contains the valid status transition rules (state machine)
 */
@Getter
public enum ApplicationStatus {

    PENDING("PENDING", "Pending"),
    SCREENING("SCREENING", "Screening"),
    INTERVIEW("INTERVIEW", "Interviewing"),
    OFFER("OFFER", "Offer extended"),
    REJECTED("REJECTED", "Rejected"),
    WITHDRAWN("WITHDRAWN", "Withdrawn");

    private final String code;
    private final String description;

    ApplicationStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * Map of valid status transitions
     * <p>
     * Key: current status, Value: set of allowed target statuses
     */
    private static final Map<ApplicationStatus, Set<ApplicationStatus>> VALID_TRANSITIONS = Map.of(
            PENDING,   Set.of(SCREENING, REJECTED, WITHDRAWN),
            SCREENING, Set.of(INTERVIEW, REJECTED, WITHDRAWN),
            INTERVIEW, Set.of(OFFER, REJECTED, WITHDRAWN),
            OFFER,     Set.of(WITHDRAWN),
            REJECTED,  Set.of(),
            WITHDRAWN, Set.of()
    );

    /**
     * Determine whether a transition from the current status to the target status is allowed
     */
    public boolean canTransitionTo(ApplicationStatus target) {
        Set<ApplicationStatus> allowed = VALID_TRANSITIONS.getOrDefault(this, Set.of());
        return allowed.contains(target);
    }

    /**
     * Get the set of allowed target statuses
     */
    public Set<ApplicationStatus> getAllowedTargets() {
        return VALID_TRANSITIONS.getOrDefault(this, Set.of());
    }

    /**
     * Get the enum constant by code
     */
    public static ApplicationStatus fromCode(String code) {
        if (code == null) return null;
        for (ApplicationStatus status : values()) {
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
        ApplicationStatus status = fromCode(code);
        return status != null ? status.description : code;
    }
}
