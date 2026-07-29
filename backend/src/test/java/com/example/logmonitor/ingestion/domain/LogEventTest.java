package com.example.logmonitor.ingestion.domain;

import com.example.logmonitor.ingestion.api.IngestionRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LogEventTest {

    @Test
    void normalizesFieldsAndComputesDefaultSevenDayRetention() {
        Instant now = Instant.parse("2026-07-30T10:00:00Z");
        IngestionRequest request = new IngestionRequest(
            null,
            now,
            " error ",
            " order-service ",
            " staging ",
            " ORDER_FAILED ",
            " Payment timeout ",
            " trace-123 ",
            " req-456 ",
            new IngestionRequest.ExceptionRequest("TimeoutException", "Connection timed out", "at com.example.OrderService.pay()"),
            Map.of("userId", 42),
            Map.of("env", "staging")
        );

        LogEvent event = LogEvent.of(request, "org-1", "proj-1", "key-1");

        assertEquals("ERROR", event.level());
        assertEquals("order-service", event.service());
        assertEquals("staging", event.environment());
        assertEquals("ORDER_FAILED", event.eventType());
        assertEquals("Payment timeout", event.message());
        assertEquals("ORDER_FAILED::Payment timeout", event.errorFingerprint());
        assertEquals(now.plusSeconds(7 * 24 * 3600L), event.expireAt());
    }

    @Test
    void supportsCustomRetentionSeconds() {
        Instant now = Instant.parse("2026-07-30T10:00:00Z");
        IngestionRequest request = new IngestionRequest(
            "event-1",
            now,
            "INFO",
            "auth-service",
            "prod",
            "USER_LOGIN",
            "User logged in",
            null,
            null,
            null,
            null,
            null
        );

        long retentionSeconds = 30 * 24 * 3600L; // 30 days
        LogEvent event = LogEvent.of(request, "org-1", "proj-1", "key-1", retentionSeconds);

        assertEquals(now.plusSeconds(retentionSeconds), event.expireAt());
    }
}
