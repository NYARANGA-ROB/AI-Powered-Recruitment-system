package com.smartats.module.analytics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartats.module.analytics.event.AnalyticsUpdateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SSE long-connection manager
 * <p>
 * Core responsibilities:
 * <ul>
 *   <li>Manages all connected SseEmitter instances (thread-safe CopyOnWriteArrayList)</li>
 *   <li>Listens for {@link AnalyticsUpdateEvent} and pushes updates to all connected clients</li>
 *   <li>Periodic heartbeat (every 30 seconds) to keep proxies/firewalls from dropping idle connections</li>
 *   <li>Automatically removes dead connections on errors</li>
 * </ul>
 *
 * <p>
 * Enterprise best practices:
 * <ul>
 *   <li>CopyOnWriteArrayList provides concurrency safety (read-heavy, write-light scenarios)</li>
 *   <li>The heartbeat mechanism stays compatible with idle timeouts of reverse proxies such as Nginx/ALB</li>
 *   <li>Decoupled from the business layer via Spring ApplicationEvent</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseEmitterManager {

    /**
     * SSE timeout: 30 minutes
     */
    private static final long SSE_TIMEOUT = 30 * 60 * 1000L;

    /**
     * All active SSE connections
     */
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    private final ObjectMapper objectMapper;

    /**
     * Create and register a new SSE connection
     *
     * @return an SseEmitter with lifecycle callbacks bound
     */
    public SseEmitter createEmitter() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        emitter.onCompletion(() -> {
            emitters.remove(emitter);
            log.debug("SSE connection closed normally, active connections: {}", emitters.size());
        });

        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            log.debug("SSE connection timed out, active connections: {}", emitters.size());
        });

        emitter.onError(ex -> {
            emitters.remove(emitter);
            log.debug("SSE connection failed: {}, active connections: {}", ex.getMessage(), emitters.size());
        });

        emitters.add(emitter);
        log.info("New SSE connection established, active connections: {}", emitters.size());

        // Send the initial connection-success event
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("message", "SSE connection established", "timeout", SSE_TIMEOUT)));
        } catch (IOException e) {
            emitters.remove(emitter);
            log.warn("Failed to send initial event, removing connection");
        }

        return emitter;
    }

    /**
     * Listen for analytics update events and push them to all connected clients
     * <p>
     * Executed asynchronously with @Async to avoid blocking business threads
     */
    @Async("asyncExecutor")
    @EventListener
    public void onAnalyticsUpdate(AnalyticsUpdateEvent event) {
        if (emitters.isEmpty()) {
            return;
        }

        Map<String, Object> payload = Map.of(
                "eventType", event.getEventType(),
                "message", event.getMessage(),
                "timestamp", System.currentTimeMillis()
        );

        log.info("Pushing SSE event: {} -> {} clients", event.getEventType(), emitters.size());
        broadcast("analytics_update", payload);
    }

    /**
     * Heartbeat: sends an empty comment frame every 30 seconds
     * <p>
     * Prevents reverse proxies such as Nginx and ALB from closing connections due to idle timeouts.
     * In the SSE specification, lines starting with a colon are comment frames; clients ignore them while the connection stays alive.
     */
    @Scheduled(fixedRate = 30_000)
    public void heartbeat() {
        if (emitters.isEmpty()) {
            return;
        }

        log.debug("Sending SSE heartbeat, active connections: {}", emitters.size());
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (Exception e) {
                emitters.remove(emitter);
                log.debug("Heartbeat failed, removing dead connection");
            }
        }
    }

    /**
     * Broadcast an event to all connections
     */
    private void broadcast(String eventName, Object data) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(objectMapper.writeValueAsString(data)));
            } catch (Exception e) {
                emitters.remove(emitter);
                log.debug("Broadcast failed, removing dead connection: {}", e.getMessage());
            }
        }
    }

    /**
     * Get the current number of active connections (for monitoring)
     */
    public int getActiveCount() {
        return emitters.size();
    }
}
