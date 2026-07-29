package com.example.logmonitor.logquery.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record LogSearchResponse(
    List<LogEventResponse> events,
    String nextCursor,
    boolean hasMore
) {
    public record LogEventResponse(
        String id,
        String eventId,
        Instant timestamp,
        String level,
        String service,
        String environment,
        String eventType,
        String message,
        String traceId,
        String requestId,
        Map<String, Object> exception,
        Map<String, Object> context,
        Map<String, Object> tags,
        Instant receivedAt,
        Instant expireAt,
        String organizationId,
        String projectId,
        String apiKeyId,
        String errorFingerprint
    ) {}
}
