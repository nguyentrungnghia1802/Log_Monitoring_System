package com.example.logmonitor.ingestion.application;

import com.example.logmonitor.common.security.RedactionProperties;
import com.example.logmonitor.common.security.SensitiveDataRedactor;
import com.example.logmonitor.ingestion.api.BatchIngestionRequest;
import com.example.logmonitor.ingestion.api.IngestionRequest;
import com.example.logmonitor.ingestion.config.IngestionLimitsProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IngestionPayloadSanitizerTest {

    private IngestionLimitsProperties limits;
    private IngestionPayloadSanitizer sanitizer;

    @BeforeEach
    void setUp() {
        limits = new IngestionLimitsProperties();
        sanitizer = new IngestionPayloadSanitizer(
            new ObjectMapper(),
            limits,
            new SensitiveDataRedactor(new RedactionProperties()),
            new SimpleMeterRegistry()
        );
    }

    @Test
    void redactsSensitiveContextTagsAndTextBeforeNormalization() {
        IngestionRequest request = request(
            "password=raw-password token=raw-token",
            new IngestionRequest.ExceptionRequest(
                "RuntimeException", "authorization=raw-auth", "Bearer raw-stack-token"),
            Map.of(
                "password", "raw-password",
                "nested", Map.of("access_token", "raw-token", "safe", "value")
            ),
            Map.of("authorization", "Bearer raw-auth")
        );

        IngestionRequest sanitized = sanitizer.sanitize(request);

        assertEquals("password=[REDACTED] token=[REDACTED]", sanitized.message());
        assertEquals("[REDACTED]", sanitized.context().get("password"));
        assertEquals("[REDACTED]", ((Map<?, ?>) sanitized.context().get("nested")).get("access_token"));
        assertEquals("[REDACTED]", sanitized.tags().get("authorization"));
        assertEquals("authorization=[REDACTED]", sanitized.exception().message());
        assertEquals("Bearer [REDACTED]", sanitized.exception().stackTrace());
    }

    @Test
    void rejectsReservedContextFields() {
        IngestionRequest request = request(
            "message",
            null,
            Map.of("project_id", "foreign-project"),
            null
        );

        IngestionValidationException exception = assertThrows(
            IngestionValidationException.class,
            () -> sanitizer.sanitize(request)
        );

        assertEquals("context contains a reserved event field", exception.getMessage());
    }

    @Test
    void rejectsDeepLargeAndLongStructuredValues() {
        limits.setMaxNestingDepth(2);
        limits.setMaxStringValueLength(10);
        limits.setMaxKeyLength(5);

        assertThrows(IngestionValidationException.class, () -> sanitizer.sanitize(request(
            "message", null,
            Map.of("outer", Map.of("inner", Map.of("deep", "value"))), null)));

        assertThrows(IngestionValidationException.class, () -> sanitizer.sanitize(request(
            "message", null, Map.of("long-key", "value"), null)));
        assertThrows(IngestionValidationException.class, () -> sanitizer.sanitize(request(
            "message", null, Map.of("safe", "value-too-long"), null)));
    }

    @Test
    void rejectsOversizedStackTraceAndBatchBeforeQueueAdmission() {
        limits.setMaxStackTraceLength(10);
        limits.setMaxBatchSize(2);

        assertThrows(IngestionValidationException.class, () -> sanitizer.sanitize(request(
            "message", new IngestionRequest.ExceptionRequest("Error", null, "12345678901"), null, null)));

        IngestionRequest event = request("message", null, null, null);
        assertThrows(IngestionValidationException.class, () -> sanitizer.sanitize(
            new BatchIngestionRequest(List.of(event, event, event))));
    }

    private IngestionRequest request(
        String message,
        IngestionRequest.ExceptionRequest exception,
        Map<String, Object> context,
        Map<String, Object> tags
    ) {
        return new IngestionRequest(
            "event-1",
            null,
            "INFO",
            "service",
            "production",
            "TEST_EVENT",
            message,
            "trace-1",
            "request-1",
            exception,
            context,
            tags
        );
    }
}
