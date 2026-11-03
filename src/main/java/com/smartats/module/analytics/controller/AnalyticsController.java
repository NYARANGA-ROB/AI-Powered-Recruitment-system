package com.smartats.module.analytics.controller;

import com.smartats.common.annotation.AuditLog;
import com.smartats.common.result.Result;
import com.smartats.module.analytics.dto.AnalyticsQueryRequest;
import com.smartats.module.analytics.dto.RecruitmentOverviewDTO;
import com.smartats.module.analytics.service.AnalyticsService;
import com.smartats.module.analytics.service.SseEmitterManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Recruitment analytics controller
 * <p>
 * Provides two kinds of endpoints:
 * <ul>
 *   <li>REST API: fetches the recruitment funnel, core metrics and trend data</li>
 *   <li>SSE stream: pushes pipeline change events to the frontend dashboard in real time</li>
 * </ul>
 *
 * <p>
 * SSE usage notes (frontend integration):
 * <pre>
 * const es = new EventSource('/api/v1/analytics/stream');
 * es.addEventListener('analytics_update', (e) => {
 *     const data = JSON.parse(e.data);
 *     // Refetch the latest data once an update event arrives
 *     fetchOverview();
 * });
 * es.addEventListener('connected', (e) => {
 *     console.log('SSE connected', JSON.parse(e.data));
 * });
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
@Tag(name = "Recruitment Analytics", description = "Recruitment funnel, core metrics, trend analysis, real-time SSE push")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final SseEmitterManager sseEmitterManager;

    /**
     * Get recruitment overview data
     * <p>
     * Includes funnel distribution, core metrics (offer rate / average match score / time-to-hire) and monthly trends.
     * Supports filtering by job ID and date range.
     * Results are cached for 5 minutes.
     */
    @GetMapping("/overview")
    @Operation(summary = "Recruitment overview", description = "Fetches the recruitment funnel, core metrics and monthly trends (5-minute cache)")
    @AuditLog(module = "ANALYTICS", operation = "QUERY_OVERVIEW", description = "Query recruitment overview data")
    public Result<RecruitmentOverviewDTO> getOverview(AnalyticsQueryRequest request) {
        if (request == null) {
            request = new AnalyticsQueryRequest();
        }
        RecruitmentOverviewDTO overview = analyticsService.getOverview(request);
        return Result.success(overview);
    }

    /**
     * SSE real-time push endpoint
     * <p>
     * Establishes a long-lived Server-Sent Events connection; when the recruitment pipeline changes:
     * <ul>
     *   <li>New application created -> publishes an APPLICATION_CREATED event</li>
     *   <li>Status transition -> publishes a STATUS_CHANGED event</li>
     *   <li>Interview scheduled -> publishes an INTERVIEW_SCHEDULED event</li>
     * </ul>
     * After receiving an event, clients can refetch the /overview endpoint for the latest data.
     * <p>
     * Connection timeout: 30 minutes; a heartbeat keeps the connection alive every 30 seconds.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "SSE real-time push", description = "Establishes a long-lived connection to receive recruitment pipeline change notifications in real time")
    public SseEmitter stream() {
        log.info("New SSE subscription request");
        return sseEmitterManager.createEmitter();
    }

    /**
     * Get the current number of SSE connections (for operations monitoring)
     */
    @GetMapping("/sse-status")
    @Operation(summary = "SSE connection status", description = "Shows the number of currently active SSE connections")
    public Result<Integer> sseStatus() {
        return Result.success(sseEmitterManager.getActiveCount());
    }
}
