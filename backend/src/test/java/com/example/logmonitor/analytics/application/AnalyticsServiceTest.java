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
        assertTrue(summary.topServices().size() <= 5);
        assertTrue(summary.topErrors().size() <= 10);
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
        assertEquals("15m", histogram.interval());
        assertEquals(5L, histogram.buckets().stream().mapToLong(AnalyticsHistogramResponse.HistogramBucket::total).sum());
        assertEquals(2L, histogram.buckets().stream().mapToLong(AnalyticsHistogramResponse.HistogramBucket::errorCount).sum());
        assertEquals(1L, histogram.buckets().stream().mapToLong(AnalyticsHistogramResponse.HistogramBucket::warnCount).sum());
        assertTrue(histogram.buckets().stream().allMatch(bucket -> bucket.timestamp().toEpochMilli() % 900_000L == 0));
    }

    @Test
    void autoSelectsSafeBucketAndRejectsInvalidAnalyticsRequests() {
        AnalyticsHistogramResponse autoHistogram = analyticsService.getHistogram(
            TEST_PROJECT, Instant.now().minus(3, ChronoUnit.HOURS), Instant.now(), null, null, null
        );
        assertEquals("1h", autoHistogram.interval());

        AnalyticsQueryException invalidInterval = assertThrows(AnalyticsQueryException.class, () -> analyticsService.getHistogram(
            TEST_PROJECT, Instant.now().minus(1, ChronoUnit.HOURS), Instant.now(), "8h", null, null
        ));
        assertEquals("INVALID_ANALYTICS_INTERVAL", invalidInterval.getCode());

        AnalyticsQueryException bucketTooFine = assertThrows(AnalyticsQueryException.class, () -> analyticsService.getHistogram(
            TEST_PROJECT, Instant.now().minus(24, ChronoUnit.HOURS), Instant.now(), "1m", null, null
        ));
        assertEquals("ANALYTICS_BUCKET_TOO_FINE", bucketTooFine.getCode());

        AnalyticsQueryException oversizedRange = assertThrows(AnalyticsQueryException.class, () -> analyticsService.getSummary(
            TEST_PROJECT, Instant.now().minus(169, ChronoUnit.HOURS), Instant.now(), null, null
        ));
        assertEquals("ANALYTICS_RANGE_TOO_LARGE", oversizedRange.getCode());
    }

    @Test
    void keepsEmptyResultsBoundedAndNormalizesMissingFingerprint() {
        Instant now = Instant.now();
        mongoTemplate.save(new LogEventDocument(
            "missing-fingerprint", now.minus(5, ChronoUnit.MINUTES), "ERROR", "order-service", "prod",
            "FAIL", "Missing fingerprint", null, null, null, null, null, now, now, "org", TEST_PROJECT, "k", null
        ));

        AnalyticsSummaryResponse summary = analyticsService.getSummary(
            TEST_PROJECT, now.minus(1, ChronoUnit.HOURS), now, null, null
        );
        assertEquals(6, summary.totalLogs());
        assertTrue(summary.topErrors().stream().anyMatch(error ->
            "UNKNOWN_ERROR".equals(error.errorFingerprint()) && error.count() == 1));

        AnalyticsSummaryResponse emptySummary = analyticsService.getSummary(
            "empty-project", now.minus(1, ChronoUnit.HOURS), now, null, null
        );
        AnalyticsHistogramResponse emptyHistogram = analyticsService.getHistogram(
            "empty-project", now.minus(1, ChronoUnit.HOURS), now, null, null, null
        );
        assertEquals(0, emptySummary.totalLogs());
        assertTrue(emptySummary.countByLevel().isEmpty());
        assertTrue(emptySummary.topServices().isEmpty());
        assertTrue(emptySummary.topErrors().isEmpty());
        assertTrue(emptyHistogram.buckets().isEmpty());
    }
}
