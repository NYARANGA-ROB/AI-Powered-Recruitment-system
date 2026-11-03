package com.smartats.module.analytics.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Analytics update event
 * <p>
 * Published when the recruitment pipeline changes (application created / status transition / interview scheduled, etc.);
 * the SSE push manager listens for it and pushes updates to all connected dashboard clients.
 * <p>
 * Uses Spring ApplicationEvent + EventListener for loose coupling:
 * business services only publish events and do not depend on SSE components directly.
 */
@Getter
public class AnalyticsUpdateEvent extends ApplicationEvent {

    /**
     * Event type (e.g. APPLICATION_CREATED, STATUS_CHANGED, INTERVIEW_SCHEDULED)
     */
    private final String eventType;

    /**
     * Event description (short text)
     */
    private final String message;

    public AnalyticsUpdateEvent(Object source, String eventType, String message) {
        super(source);
        this.eventType = eventType;
        this.message = message;
    }
}
