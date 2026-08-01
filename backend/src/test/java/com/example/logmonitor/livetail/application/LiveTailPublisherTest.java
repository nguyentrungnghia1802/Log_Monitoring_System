package com.example.logmonitor.livetail.application;

import com.example.logmonitor.auth.application.JwtService;
import com.example.logmonitor.ingestion.domain.LogEvent;
import com.example.logmonitor.livetail.config.LiveTailProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LiveTailPublisherTest {

    private SimpMessagingTemplate messagingTemplate;
    private LiveTailSubscriptionRegistry registry;
    private SimpleMeterRegistry meterRegistry;
    private LiveTailPublisher publisher;

    @BeforeEach
    void setUp() {
        LiveTailProperties properties = new LiveTailProperties();
        registry = new LiveTailSubscriptionRegistry(properties, new SimpleMeterRegistry());
        registry.registerSession(
            "session-1",
            new JwtService.UserPrincipal("user-1", "operator", "org-1"),
            "10.0.0.1"
        );
        registry.registerSubscription(
            "session-1",
            "sub-1",
            "project-a",
            LiveTailSubscriptionRegistry.filter("ERROR", "payments", "production")
        );
        messagingTemplate = mock(SimpMessagingTemplate.class);
        meterRegistry = new SimpleMeterRegistry();
        publisher = new LiveTailPublisher(
            messagingTemplate,
            registry,
            properties,
            meterRegistry
        );
    }

    @Test
    void sendsOnlyMatchingEventsToTheOwningSessionQueue() {
        LogEvent matching = event("project-a", "ERROR", "payments", "production");
        LogEvent filtered = event("project-a", "INFO", "payments", "production");

        publisher.publish(List.of(matching, filtered));

        verify(messagingTemplate).convertAndSendToUser(
            eq("user-1"),
            eq("/queue/projects/project-a/livetail"),
            eq(matching),
            anyMap()
        );
        assertEquals(1.0, meterRegistry.counter("livetail.events.sent").count());
        assertEquals(0.0, meterRegistry.counter("livetail.events.dropped").count());
    }

    @Test
    void countsOutboundFailureAsDroppedEvent() {
        doThrow(new MessagingException("outbound queue full"))
            .when(messagingTemplate)
            .convertAndSendToUser(anyString(), anyString(), any(), anyMap());

        publisher.publish(List.of(event("project-a", "ERROR", "payments", "production")));

        assertEquals(0.0, meterRegistry.counter("livetail.events.sent").count());
        assertEquals(1.0, meterRegistry.counter("livetail.events.dropped").count());
    }

    @Test
    void targetsSessionWhenConstructingUserDestinationHeaders() {
        publisher.publish(List.of(event("project-a", "ERROR", "payments", "production")));

        @SuppressWarnings("unchecked")
        var headersCaptor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSendToUser(
            eq("user-1"),
            eq("/queue/projects/project-a/livetail"),
            any(LogEvent.class),
            headersCaptor.capture()
        );
        assertEquals("session-1", headersCaptor.getValue().get(SimpMessageHeaderAccessor.SESSION_ID_HEADER));
    }

    private LogEvent event(String projectId, String level, String service, String environment) {
        Instant now = Instant.now();
        return new LogEvent(
            "event-1",
            now,
            level,
            service,
            environment,
            "LOG",
            "message",
            null,
            null,
            null,
            Map.of(),
            Map.of(),
            now,
            now.plusSeconds(3600),
            "org-1",
            projectId,
            "key-1",
            null
        );
    }
}
