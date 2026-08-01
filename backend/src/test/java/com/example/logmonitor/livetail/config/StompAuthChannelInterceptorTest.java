package com.example.logmonitor.livetail.config;

import com.example.logmonitor.auth.application.JwtService;
import com.example.logmonitor.auth.application.ProjectAuthorizationService;
import com.example.logmonitor.livetail.application.LiveTailSubscriptionRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StompAuthChannelInterceptorTest {

    private JwtService jwtService;
    private ProjectAuthorizationService authorizationService;
    private LiveTailSubscriptionRegistry registry;
    private StompAuthChannelInterceptor interceptor;
    private MessageChannel channel;
    private JwtService.UserPrincipal principal;
    private LiveTailProperties properties;

    @BeforeEach
    void setUp() {
        jwtService = mock(JwtService.class);
        authorizationService = mock(ProjectAuthorizationService.class);
        properties = new LiveTailProperties();
        properties.setMaxSubscriptionsPerSession(1);
        registry = new LiveTailSubscriptionRegistry(properties, new SimpleMeterRegistry());
        interceptor = new StompAuthChannelInterceptor(
            jwtService,
            authorizationService,
            registry,
            new SimpleMeterRegistry()
        );
        channel = (message, timeout) -> true;
        principal = new JwtService.UserPrincipal("user-1", "operator", "org-1");
        when(jwtService.validateAndExtractPrincipal("valid-token")).thenReturn(Optional.of(principal));
        when(authorizationService.authorize(
            any(),
            eq("project-a"),
            eq(ProjectAuthorizationService.Permission.READ)
        )).thenReturn(new ProjectAuthorizationService.AuthorizationDecision(true, ProjectAuthorizationService.Failure.NONE));
    }

    @Test
    void rejectsConnectWithoutBearerToken() {
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(connect("session-1", null), channel));
        assertEquals(0, registry.activeSessionCount());
    }

    @Test
    void rejectsInvalidOrExpiredJwt() {
        when(jwtService.validateAndExtractPrincipal("expired-token")).thenReturn(Optional.empty());

        assertThrows(
            AccessDeniedException.class,
            () -> interceptor.preSend(connect("session-1", "expired-token"), channel)
        );
        assertEquals(0, registry.activeSessionCount());
    }

    @Test
    void rejectsPublicProjectTopicEvenForAuthenticatedSession() {
        authenticate("session-1");

        assertThrows(
            AccessDeniedException.class,
            () -> interceptor.preSend(
                subscribe("session-1", "sub-1", "/topic/projects/project-a/livetail", null, null, null),
                channel
            )
        );
        assertEquals(0, registry.subscriptionCount("session-1"));
    }

    @Test
    void rejectsForeignProjectAndAllowsViewerReadWhenAuthorizationDecidesSo() {
        authenticate("session-1");
        when(authorizationService.authorize(
            any(),
            eq("foreign-project"),
            eq(ProjectAuthorizationService.Permission.READ)
        )).thenReturn(new ProjectAuthorizationService.AuthorizationDecision(false, ProjectAuthorizationService.Failure.FORBIDDEN));

        assertThrows(
            AccessDeniedException.class,
            () -> interceptor.preSend(
                subscribe("session-1", "sub-1", "/user/queue/projects/foreign-project/livetail", null, null, null),
                channel
            )
        );

        interceptor.preSend(
            subscribe("session-1", "sub-2", "/user/queue/projects/project-a/livetail", "ERROR", "payments", "prod"),
            channel
        );
        assertEquals(1, registry.subscriptionCount("session-1"));
    }

    @Test
    void enforcesDuplicateSubscriptionLimitAndCleansUpOnDisconnect() {
        authenticate("session-1");
        interceptor.preSend(
            subscribe("session-1", "sub-1", "/user/queue/projects/project-a/livetail", null, null, null),
            channel
        );

        assertThrows(
            AccessDeniedException.class,
            () -> interceptor.preSend(
                subscribe("session-1", "sub-2", "/user/queue/projects/project-a/livetail", null, null, null),
                channel
            )
        );

        StompHeaderAccessor disconnect = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        disconnect.setSessionId("session-1");
        interceptor.preSend(message(disconnect), channel);
        assertEquals(0, registry.activeSessionCount());
        assertEquals(0, registry.activeSubscriptionCount());
    }

    @Test
    void rejectsUnsafeFilters() {
        authenticate("session-1");

        assertThrows(
            AccessDeniedException.class,
            () -> interceptor.preSend(
                subscribe("session-1", "sub-1", "/user/queue/projects/project-a/livetail", "NOTICE", null, null),
                channel
            )
        );
        assertEquals(0, registry.subscriptionCount("session-1"));
    }

    private void authenticate(String sessionId) {
        interceptor.preSend(connect(sessionId, "valid-token"), channel);
    }

    private Message<?> connect(String sessionId, String token) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId(sessionId);
        if (token != null) {
            accessor.addNativeHeader("Authorization", "Bearer " + token);
        }
        accessor.setSessionAttributes(Map.of(LiveTailHandshakeInterceptor.REMOTE_ADDRESS_ATTRIBUTE, "10.0.0.1"));
        return message(accessor);
    }

    private Message<?> subscribe(
        String sessionId,
        String subscriptionId,
        String destination,
        String level,
        String service,
        String environment
    ) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId(sessionId);
        accessor.setUser(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        accessor.setSubscriptionId(subscriptionId);
        accessor.setDestination(destination);
        if (level != null) accessor.addNativeHeader("level", level);
        if (service != null) accessor.addNativeHeader("service", service);
        if (environment != null) accessor.addNativeHeader("environment", environment);
        return message(accessor);
    }

    private Message<?> message(StompHeaderAccessor accessor) {
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
