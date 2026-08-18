package com.example.logmonitor.livetail.application;

import com.example.logmonitor.auth.application.JwtService;
import com.example.logmonitor.ingestion.domain.LogEvent;
import com.example.logmonitor.lifecycle.GracefulShutdownCoordinator;
import com.example.logmonitor.livetail.config.LiveTailProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LiveTailSubscriptionRegistryTest {

    private LiveTailProperties properties;
    private LiveTailSubscriptionRegistry registry;

    @BeforeEach
    void setUp() {
        properties = new LiveTailProperties();
        GracefulShutdownCoordinator shutdownCoordinator = mock(GracefulShutdownCoordinator.class);
        when(shutdownCoordinator.isAcceptingTraffic()).thenReturn(true);
        registry = new LiveTailSubscriptionRegistry(properties, new SimpleMeterRegistry(), shutdownCoordinator);
    }

    @Test
    void boundsConnectionsPerUserAndReleasesSlotOnDisconnect() {
        properties.setMaxConnectionsPerUser(1);
        JwtService.UserPrincipal principal = principal("user-1");

        assertTrue(registry.registerSession("session-1", principal, "10.0.0.1").accepted());
        var rejected = registry.registerSession("session-2", principal, "10.0.0.2");
        assertFalse(rejected.accepted());
        assertEquals(LiveTailSubscriptionRegistry.SessionRejection.USER_CONNECTION_LIMIT, rejected.rejection());

        registry.unregisterSession("session-1");
        assertTrue(registry.registerSession("session-2", principal, "10.0.0.2").accepted());
    }

    @Test
    void boundsSubscriptionsAndRejectsDuplicateIds() {
        properties.setMaxSubscriptionsPerSession(1);
        registry.registerSession("session-1", principal("user-1"), "10.0.0.1");
        var filter = LiveTailSubscriptionRegistry.filter("ERROR", "payments", "production");

        assertTrue(registry.registerSubscription("session-1", "sub-1", "project-a", filter).accepted());

        var duplicate = registry.registerSubscription("session-1", "sub-1", "project-a", filter);
        assertFalse(duplicate.accepted());
        assertEquals(
            LiveTailSubscriptionRegistry.SubscriptionRejection.DUPLICATE_SUBSCRIPTION,
            duplicate.rejection()
        );

        var limited = registry.registerSubscription("session-1", "sub-2", "project-a", filter);
        assertFalse(limited.accepted());
        assertEquals(
            LiveTailSubscriptionRegistry.SubscriptionRejection.SUBSCRIPTION_LIMIT,
            limited.rejection()
        );
    }

    @Test
    void filtersBeforeReturningProjectSubscriptions() {
        registry.registerSession("session-1", principal("user-1"), "10.0.0.1");
        registry.registerSubscription(
            "session-1",
            "sub-1",
            "project-a",
            LiveTailSubscriptionRegistry.filter("ERROR", "payments", "production")
        );

        assertEquals(1, registry.matchingSubscriptions("project-a", event("ERROR", "payments", "production")).size());
        assertEquals(0, registry.matchingSubscriptions("project-a", event("INFO", "payments", "production")).size());
        assertEquals(0, registry.matchingSubscriptions("project-a", event("ERROR", "billing", "production")).size());
        assertEquals(0, registry.matchingSubscriptions("foreign-project", event("ERROR", "payments", "production")).size());
    }

    @Test
    void disconnectEventRemovesAllSessionSubscriptions() {
        registry.registerSession("session-1", principal("user-1"), "10.0.0.1");
        registry.registerSubscription(
            "session-1",
            "sub-1",
            "project-a",
            LiveTailSubscriptionRegistry.filter(null, null, null)
        );
        Message<byte[]> message = MessageBuilder.withPayload(new byte[0]).build();

        registry.onSessionDisconnect(new SessionDisconnectEvent(this, message, "session-1", CloseStatus.NORMAL));

        assertEquals(0, registry.activeSessionCount());
        assertEquals(0, registry.activeSubscriptionCount());
    }

    @Test
    void clearsAllSessionsWhenContextCloses() {
        registry.registerSession("session-1", principal("user-1"), "10.0.0.1");
        registry.registerSubscription(
            "session-1",
            "sub-1",
            "project-a",
            LiveTailSubscriptionRegistry.filter(null, null, null)
        );

        registry.closeAllSessions();

        assertEquals(0, registry.activeSessionCount());
        assertEquals(0, registry.activeSubscriptionCount());
    }

    @Test
    void rejectsUnsafeFilterValues() {
        assertThrows(
            IllegalArgumentException.class,
            () -> LiveTailSubscriptionRegistry.filter("ERROR", "payments service", "production")
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> LiveTailSubscriptionRegistry.filter("NOTICE", null, null)
        );
    }

    private JwtService.UserPrincipal principal(String userId) {
        return new JwtService.UserPrincipal(userId, userId, "org-1");
    }

    private LogEvent event(String level, String service, String environment) {
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
            "project-a",
            "key-1",
            null
        );
    }
}
