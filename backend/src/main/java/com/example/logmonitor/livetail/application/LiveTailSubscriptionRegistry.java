package com.example.logmonitor.livetail.application;

import com.example.logmonitor.auth.application.JwtService;
import com.example.logmonitor.ingestion.domain.LogEvent;
import com.example.logmonitor.livetail.config.LiveTailProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class LiveTailSubscriptionRegistry {

    private static final Set<String> ALLOWED_LEVELS = Set.of(
        "TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL"
    );
    private static final Pattern FILTER_VALUE_PATTERN = Pattern.compile("^[A-Za-z0-9._:/-]{1,64}$");

    private final LiveTailProperties properties;
    private final Object monitor = new Object();
    private final Map<String, SessionState> sessions = new HashMap<>();
    private final Map<String, Integer> connectionsByUser = new HashMap<>();
    private final Map<String, Integer> connectionsByIp = new HashMap<>();

    public LiveTailSubscriptionRegistry(LiveTailProperties properties, MeterRegistry meterRegistry) {
        this.properties = properties;

        Gauge.builder("livetail.sessions.active", this, LiveTailSubscriptionRegistry::activeSessionCount)
            .description("Active authenticated live-tail WebSocket sessions")
            .register(meterRegistry);
        Gauge.builder("livetail.subscriptions.active", this, LiveTailSubscriptionRegistry::activeSubscriptionCount)
            .description("Active authorized live-tail subscriptions")
            .register(meterRegistry);
    }

    public SessionRegistration registerSession(
        String sessionId,
        JwtService.UserPrincipal principal,
        String remoteAddress
    ) {
        if (isBlank(sessionId) || principal == null || isBlank(principal.userId())) {
            return SessionRegistration.reject(SessionRejection.INVALID_SESSION);
        }

        String ip = isBlank(remoteAddress) ? "unknown" : remoteAddress;
        synchronized (monitor) {
            if (sessions.containsKey(sessionId)) {
                return SessionRegistration.reject(SessionRejection.DUPLICATE_SESSION);
            }
            if (connectionsByUser.getOrDefault(principal.userId(), 0) >= properties.getMaxConnectionsPerUser()) {
                return SessionRegistration.reject(SessionRejection.USER_CONNECTION_LIMIT);
            }
            if (connectionsByIp.getOrDefault(ip, 0) >= properties.getMaxConnectionsPerIp()) {
                return SessionRegistration.reject(SessionRejection.IP_CONNECTION_LIMIT);
            }

            sessions.put(sessionId, new SessionState(sessionId, principal.userId(), ip));
            increment(connectionsByUser, principal.userId());
            increment(connectionsByIp, ip);
            return SessionRegistration.allow();
        }
    }

    public void unregisterSession(String sessionId) {
        if (isBlank(sessionId)) {
            return;
        }
        synchronized (monitor) {
            SessionState removed = sessions.remove(sessionId);
            if (removed == null) {
                return;
            }
            decrement(connectionsByUser, removed.userId);
            decrement(connectionsByIp, removed.remoteAddress);
        }
    }

    public boolean belongsTo(String sessionId, String userId) {
        synchronized (monitor) {
            SessionState state = sessions.get(sessionId);
            return state != null && Objects.equals(state.userId, userId);
        }
    }

    public SubscriptionRegistration registerSubscription(
        String sessionId,
        String subscriptionId,
        String projectId,
        LiveTailFilter filter
    ) {
        if (isBlank(sessionId) || isBlank(subscriptionId) || isBlank(projectId) || filter == null) {
            return SubscriptionRegistration.reject(SubscriptionRejection.INVALID_SUBSCRIPTION);
        }

        synchronized (monitor) {
            SessionState state = sessions.get(sessionId);
            if (state == null) {
                return SubscriptionRegistration.reject(SubscriptionRejection.UNKNOWN_SESSION);
            }
            if (state.subscriptions.containsKey(subscriptionId)) {
                return SubscriptionRegistration.reject(SubscriptionRejection.DUPLICATE_SUBSCRIPTION);
            }
            if (state.subscriptions.size() >= properties.getMaxSubscriptionsPerSession()) {
                return SubscriptionRegistration.reject(SubscriptionRejection.SUBSCRIPTION_LIMIT);
            }

            state.subscriptions.put(
                subscriptionId,
                new Subscription(sessionId, state.userId, subscriptionId, projectId, filter)
            );
            return SubscriptionRegistration.allow();
        }
    }

    public void unregisterSubscription(String sessionId, String subscriptionId) {
        if (isBlank(sessionId) || isBlank(subscriptionId)) {
            return;
        }
        synchronized (monitor) {
            SessionState state = sessions.get(sessionId);
            if (state != null) {
                state.subscriptions.remove(subscriptionId);
            }
        }
    }

    public List<Subscription> matchingSubscriptions(String projectId, LogEvent event) {
        if (isBlank(projectId) || event == null) {
            return List.of();
        }
        synchronized (monitor) {
            return sessions.values().stream()
                .flatMap(state -> state.subscriptions.values().stream())
                .filter(subscription -> subscription.projectId().equals(projectId))
                .filter(subscription -> subscription.filter().matches(event))
                .toList();
        }
    }

    public int activeSessionCount() {
        synchronized (monitor) {
            return sessions.size();
        }
    }

    public int activeSubscriptionCount() {
        synchronized (monitor) {
            return sessions.values().stream()
                .mapToInt(state -> state.subscriptions.size())
                .sum();
        }
    }

    public int subscriptionCount(String sessionId) {
        synchronized (monitor) {
            SessionState state = sessions.get(sessionId);
            return state == null ? 0 : state.subscriptions.size();
        }
    }

    @EventListener
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        if (event != null) {
            unregisterSession(event.getSessionId());
        }
    }

    public static LiveTailFilter filter(String level, String service, String environment) {
        return new LiveTailFilter(level, service, environment);
    }

    private static void increment(Map<String, Integer> counts, String key) {
        counts.merge(key, 1, Integer::sum);
    }

    private static void decrement(Map<String, Integer> counts, String key) {
        counts.computeIfPresent(key, (ignored, count) -> count <= 1 ? null : count - 1);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static final class SessionState {
        private final String sessionId;
        private final String userId;
        private final String remoteAddress;
        private final Map<String, Subscription> subscriptions = new HashMap<>();

        private SessionState(String sessionId, String userId, String remoteAddress) {
            this.sessionId = sessionId;
            this.userId = userId;
            this.remoteAddress = remoteAddress;
        }
    }

    public record Subscription(
        String sessionId,
        String userId,
        String subscriptionId,
        String projectId,
        LiveTailFilter filter
    ) { }

    public record LiveTailFilter(String level, String service, String environment) {
        public LiveTailFilter {
            level = normalizeLevel(level);
            service = normalizeFilterValue("service", service);
            environment = normalizeFilterValue("environment", environment);
        }

        public boolean matches(LogEvent event) {
            return event != null
                && matches(level, event.level())
                && matches(service, event.service())
                && matches(environment, event.environment());
        }

        private static boolean matches(String filter, String actual) {
            return filter == null || (actual != null && filter.equalsIgnoreCase(actual));
        }

        private static String normalizeLevel(String value) {
            if (isBlank(value)) {
                return null;
            }
            String normalized = value.trim().toUpperCase();
            if (!ALLOWED_LEVELS.contains(normalized)) {
                throw new IllegalArgumentException("Unsupported live-tail level filter");
            }
            return normalized;
        }

        private static String normalizeFilterValue(String name, String value) {
            if (isBlank(value)) {
                return null;
            }
            String normalized = value.trim();
            if (!FILTER_VALUE_PATTERN.matcher(normalized).matches()) {
                throw new IllegalArgumentException("Invalid live-tail " + name + " filter");
            }
            return normalized;
        }
    }

    public enum SessionRejection {
        INVALID_SESSION,
        DUPLICATE_SESSION,
        USER_CONNECTION_LIMIT,
        IP_CONNECTION_LIMIT
    }

    public record SessionRegistration(boolean accepted, SessionRejection rejection) {
        private static SessionRegistration allow() {
            return new SessionRegistration(true, null);
        }

        private static SessionRegistration reject(SessionRejection rejection) {
            return new SessionRegistration(false, rejection);
        }
    }

    public enum SubscriptionRejection {
        INVALID_SUBSCRIPTION,
        UNKNOWN_SESSION,
        DUPLICATE_SUBSCRIPTION,
        SUBSCRIPTION_LIMIT
    }

    public record SubscriptionRegistration(boolean accepted, SubscriptionRejection rejection) {
        private static SubscriptionRegistration allow() {
            return new SubscriptionRegistration(true, null);
        }

        private static SubscriptionRegistration reject(SubscriptionRejection rejection) {
            return new SubscriptionRegistration(false, rejection);
        }
    }
}
