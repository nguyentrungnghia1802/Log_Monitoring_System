package com.example.logmonitor.ingestion.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;

public record IngestionRequest(
    String eventId,
    Instant timestamp,
    @NotBlank String level,
    @NotBlank String service,
    @NotBlank String environment,
    @NotBlank String eventType,
    @NotBlank @Size(max = 4000) String message,
    String traceId,
    String requestId,
    ExceptionRequest exception,
    Map<String, Object> context,
    Map<String, Object> tags
) {
    public record ExceptionRequest(String type, String message, String stackTrace) {}
}
