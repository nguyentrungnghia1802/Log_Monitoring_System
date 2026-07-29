package com.example.logmonitor.sdk;

import java.time.Instant;
import java.util.Map;

public record LogEventPayload(
    String eventId,
    Instant timestamp,
    String level,
    String service,
    String environment,
    String eventType,
    String message,
    String traceId,
    String requestId,
    ExceptionPayload exception,
    Map<String, Object> context,
    Map<String, Object> tags
) {
    public record ExceptionPayload(String type, String message, String stackTrace) {}
}
