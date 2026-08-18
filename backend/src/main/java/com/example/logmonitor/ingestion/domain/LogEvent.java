package com.example.logmonitor.ingestion.domain;

import com.example.logmonitor.ingestion.api.IngestionRequest;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record LogEvent(
    String eventId,
    Instant timestamp,
    String level,
    String service,
    String environment,
    String eventType,
    String message,
    String traceId,
    String requestId,
    ExceptionDetails exception,
    Map<String, Object> context,
    Map<String, Object> tags,
    Instant receivedAt,
    Instant expireAt,
    String organizationId,
    String projectId,
    String apiKeyId,
    String errorFingerprint
) {
    public static final long DEFAULT_RETENTION_SECONDS = 7 * 24 * 3600L; // 7 days

    public static LogEvent of(IngestionRequest request, String organizationId, String projectId, String apiKeyId) {
        return of(request, organizationId, projectId, apiKeyId, DEFAULT_RETENTION_SECONDS);
    }

    public static LogEvent of(IngestionRequest request, String organizationId, String projectId, String apiKeyId, long retentionSeconds) {
        String normalizedLevel = request.level() == null ? "INFO" : request.level().trim().toUpperCase();
        String normalizedEventType = request.eventType() == null ? "LOG" : request.eventType().trim();
        String normalizedService = request.service() == null ? "unknown" : request.service().trim();
        String normalizedEnvironment = request.environment() == null ? "development" : request.environment().trim();
        String normalizedMessage = request.message() == null ? "" : request.message().trim();
        String normalizedTraceId = request.traceId() == null ? null : request.traceId().trim();
        String normalizedRequestId = request.requestId() == null ? null : request.requestId().trim();

        Map<String, Object> safeContext = request.context() == null ? Map.of() : new LinkedHashMap<>(request.context());
        Map<String, Object> safeTags = request.tags() == null ? Map.of() : new LinkedHashMap<>(request.tags());

        Instant now = Instant.now();
        Instant eventTimestamp = request.timestamp() == null ? now : request.timestamp();
        // Retention is anchored to server receipt time so a producer-controlled
        // future timestamp cannot extend storage beyond the configured policy.
        Instant expireAt = now.plusSeconds(retentionSeconds <= 0 ? DEFAULT_RETENTION_SECONDS : retentionSeconds);
        String fingerprint = normalizedMessage.isBlank() ? normalizedEventType : normalizedEventType + "::" + normalizedMessage;

        return new LogEvent(
            request.eventId() == null || request.eventId().isBlank() ? null : request.eventId().trim(),
            eventTimestamp,
            normalizedLevel,
            normalizedService,
            normalizedEnvironment,
            normalizedEventType,
            normalizedMessage,
            normalizedTraceId,
            normalizedRequestId,
            request.exception() == null ? null : ExceptionDetails.from(request.exception()),
            safeContext,
            safeTags,
            now,
            expireAt,
            organizationId,
            projectId,
            apiKeyId,
            fingerprint
        );
    }

    public record ExceptionDetails(String type, String message, String stackTrace) {
        static ExceptionDetails from(IngestionRequest.ExceptionRequest exception) {
            return new ExceptionDetails(exception.type(), exception.message(), exception.stackTrace());
        }
    }
}
