package com.example.logmonitor.livetail.config;

import com.example.logmonitor.auth.application.JwtService;
import com.example.logmonitor.auth.application.ProjectAuthorizationService;
import com.example.logmonitor.livetail.application.LiveTailSubscriptionRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(StompAuthChannelInterceptor.class);
    private static final Pattern DESTINATION_PATTERN = Pattern.compile(
        "^/user/queue/projects/([A-Za-z0-9._:-]{1,128})/livetail$"
    );

    private final JwtService jwtService;
    private final ProjectAuthorizationService authorizationService;
    private final LiveTailSubscriptionRegistry subscriptionRegistry;
    private final Counter authorizationFailureCounter;
    private final Counter connectionRejectionCounter;
    private final Counter subscriptionRejectionCounter;

    public StompAuthChannelInterceptor(
        JwtService jwtService,
        ProjectAuthorizationService authorizationService,
        LiveTailSubscriptionRegistry subscriptionRegistry,
        MeterRegistry meterRegistry
    ) {
        this.jwtService = jwtService;
        this.authorizationService = authorizationService;
        this.subscriptionRegistry = subscriptionRegistry;
        this.authorizationFailureCounter = Counter.builder("livetail.authorization.failures")
            .description("Rejected live-tail authentication and authorization attempts")
            .register(meterRegistry);
        this.connectionRejectionCounter = Counter.builder("livetail.connections.rejected")
            .description("Rejected live-tail WebSocket sessions")
            .register(meterRegistry);
        this.subscriptionRejectionCounter = Counter.builder("livetail.subscriptions.rejected")
            .description("Rejected live-tail subscriptions")
            .register(meterRegistry);
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (StompCommand.CONNECT.equals(command) || StompCommand.STOMP.equals(command)) {
            authenticateAndRegister(accessor);
            return message;
        }

        if (StompCommand.DISCONNECT.equals(command)) {
            subscriptionRegistry.unregisterSession(accessor.getSessionId());
            return message;
        }

        Authentication authentication = requireSessionAuthentication(accessor);
        if (StompCommand.SUBSCRIBE.equals(command)) {
            authorizeSubscription(accessor, authentication);
        } else if (StompCommand.UNSUBSCRIBE.equals(command)) {
            subscriptionRegistry.unregisterSubscription(accessor.getSessionId(), accessor.getSubscriptionId());
        } else {
            throw reject("STOMP command not allowed on the live-tail endpoint");
        }
        return message;
    }

    private void authenticateAndRegister(StompHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (isBlank(authHeader) || !authHeader.startsWith("Bearer ") || isBlank(authHeader.substring(7))) {
            throw rejectConnection("Bearer JWT is required on STOMP CONNECT");
        }

        String token = authHeader.substring(7).trim();
        JwtService.UserPrincipal principal = jwtService.validateAndExtractPrincipal(token).orElseThrow(
            () -> rejectConnection("Invalid or expired STOMP JWT")
        );
        if (isBlank(principal.organizationId())) {
            throw rejectConnection("JWT organization claim is required");
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            principal,
            null,
            List.of(() -> "ROLE_USER")
        );
        String remoteAddress = readRemoteAddress(accessor.getSessionAttributes());
        LiveTailSubscriptionRegistry.SessionRegistration registration =
            subscriptionRegistry.registerSession(sessionId, principal, remoteAddress);
        if (!registration.accepted()) {
            throw rejectConnection("Live-tail connection limit reached");
        }

        accessor.setUser(authentication);
        log.debug("Authenticated live-tail STOMP session for userId={}", principal.userId());
    }

    private void authorizeSubscription(
        StompHeaderAccessor accessor,
        Authentication authentication
    ) {
        Matcher matcher = destinationMatcher(accessor.getDestination());
        if (matcher == null) {
            throw rejectSubscription("Unsupported live-tail destination");
        }

        if (!(authentication.getPrincipal() instanceof JwtService.UserPrincipal principal)
            || !subscriptionRegistry.belongsTo(accessor.getSessionId(), principal.userId())) {
            throw rejectSubscription("STOMP session identity is not valid");
        }

        String projectId = matcher.group(1);
        ProjectAuthorizationService.AuthorizationDecision decision = authorizationService.authorize(
            authentication,
            projectId,
            ProjectAuthorizationService.Permission.READ
        );
        if (!decision.allowed()) {
            throw rejectSubscription("User is not authorized for the requested project live tail");
        }

        String subscriptionId = accessor.getSubscriptionId();
        if (isBlank(subscriptionId)) {
            throw rejectSubscription("A unique STOMP subscription id is required");
        }

        LiveTailSubscriptionRegistry.LiveTailFilter filter;
        try {
            filter = LiveTailSubscriptionRegistry.filter(
                accessor.getFirstNativeHeader("level"),
                accessor.getFirstNativeHeader("service"),
                accessor.getFirstNativeHeader("environment")
            );
        } catch (IllegalArgumentException ex) {
            throw rejectSubscription("Invalid live-tail filter");
        }

        LiveTailSubscriptionRegistry.SubscriptionRegistration registration =
            subscriptionRegistry.registerSubscription(accessor.getSessionId(), subscriptionId, projectId, filter);
        if (!registration.accepted()) {
            throw rejectSubscription("Live-tail subscription limit reached");
        }
    }

    private Authentication requireSessionAuthentication(StompHeaderAccessor accessor) {
        Principal principal = accessor.getUser();
        if (!(principal instanceof Authentication authentication)
            || !(authentication.getPrincipal() instanceof JwtService.UserPrincipal)) {
            throw reject("Authenticated STOMP session is required");
        }
        return authentication;
    }

    private Matcher destinationMatcher(String destination) {
        if (destination == null) {
            return null;
        }
        Matcher matcher = DESTINATION_PATTERN.matcher(destination);
        return matcher.matches() ? matcher : null;
    }

    private String readRemoteAddress(Map<String, Object> attributes) {
        if (attributes == null) {
            return "unknown";
        }
        Object value = attributes.get(LiveTailHandshakeInterceptor.REMOTE_ADDRESS_ATTRIBUTE);
        return value == null ? "unknown" : value.toString();
    }

    private AccessDeniedException rejectConnection(String message) {
        connectionRejectionCounter.increment();
        return reject(message);
    }

    private AccessDeniedException rejectSubscription(String message) {
        subscriptionRejectionCounter.increment();
        return reject(message);
    }

    private AccessDeniedException reject(String message) {
        authorizationFailureCounter.increment();
        log.debug("Rejected live-tail STOMP request: {}", message);
        return new AccessDeniedException(message);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
