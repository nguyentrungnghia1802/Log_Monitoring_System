package com.example.logmonitor.analytics.application;

import com.example.logmonitor.analytics.api.AnalyticsHistogramResponse;
import com.example.logmonitor.analytics.api.AnalyticsSummaryResponse;
import com.example.logmonitor.persistence.LogEventDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AnalyticsServiceTest {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private AnalyticsService analyticsService;

    private static final String TEST_PROJECT = "test-proj-analytics";

    @BeforeEach
    void setUp() {
        mongoTemplate.dropCollection(LogEventDocument.class);

        Instant now = Instant.now();

        // Save sample events
        mongoTemplate.save(new LogEventDocument("1", now.minus(30, ChronoUnit.MINUTES), "ERROR", "order-service", "prod", "FAIL", "Timeout 1", null, null, null, null, null, now, now, "org", TEST_PROJECT, "k", "FAIL::Timeout"));
        mongoTemplate.save(new LogEventDocument("2", now.minus(25, ChronoUnit.MINUTES), "ERROR", "order-service", "prod", "FAIL", "Timeout 2", null, null, null, null, null, now, now, "org", TEST_PROJECT, "k", "FAIL::Timeout"));
        mongoTemplate.save(new LogEventDocument("3", now.minus(20, ChronoUnit.MINUTES), "WARN", "payment-service", "prod", "RETRY", "Gateway retry", null, null, null, null, null, now, now, "org", TEST_PROJECT, "k", "RETRY::Gateway"));
        mongoTemplate.save(new LogEventDocument("4", now.minus(15, ChronoUnit.MINUTES), "INFO", "order-service", "prod", "OK", "Success", null, null, null, null, null, now, now, "org", TEST_PROJECT, "k", "OK"));
        mongoTemplate.save(new LogEventDocument("5", now.minus(10, ChronoUnit.MINUTES), "INFO", "auth-service", "prod", "OK", "Login ok", null, null, null, null, null, now, now, "org", TEST_PROJECT, "k", "OK"));
    }

    @Test
    void getSummaryCalculatesTotalCountsAndErrorRate() {
        Instant now = Instant.now();
        AnalyticsSummaryResponse summary = analyticsService.getSummary(
            TEST_PROJECT, now.minus(1, ChronoUnit.HOURS), now, null, null
        );

        assertEquals(5, summary.totalLogs());
        assertEquals(60.0, summary.errorRatePercentage()); // (2 ERROR + 1 WARN) / 5 = 60%
        assertEquals(2L, summary.countByLevel().get("ERROR"));
        assertEquals(1L, summary.countByLevel().get("WARN"));
        assertEquals(2L, summary.countByLevel().get("INFO"));
        assertFalse(summary.topServices().isEmpty());
        assertFalse(summary.topErrors().isEmpty());
        assertEquals("FAIL::Timeout", summary.topErrors().get(0).errorFingerprint());
        assertEquals(2L, summary.topErrors().get(0).count());
    }

    @Test
    void getHistogramBucketsEventsByInterval() {
        Instant now = Instant.now();
        AnalyticsHistogramResponse histogram = analyticsService.getHistogram(
            TEST_PROJECT, now.minus(1, ChronoUnit.HOURS), now, "15m", null, null
        );

        assertNotNull(histogram.buckets());
        assertFalse(histogram.buckets().isEmpty());
    }
}
