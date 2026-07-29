package com.example.logmonitor.logquery.application;

import com.example.logmonitor.logquery.api.LogSearchResponse;
import com.example.logmonitor.persistence.LogEventDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class LogQueryServiceTest {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private LogQueryService logQueryService;

    private static final String TEST_PROJECT = "test-proj-query";

    @BeforeEach
    void setUp() {
        mongoTemplate.dropCollection(LogEventDocument.class);

        Instant now = Instant.now();

        LogEventDocument doc1 = new LogEventDocument(
            "e-1", now.minus(30, ChronoUnit.MINUTES), "ERROR", "order-service", "prod",
            "ORDER_FAILED", "Order timeout occurred", "t-100", "r-100", null,
            Map.of("user", 1), Map.of("tag", "val"), now, now.plusSeconds(3600), "org-1", TEST_PROJECT, "key-1", "ORDER_FAILED::Order timeout occurred"
        );

        LogEventDocument doc2 = new LogEventDocument(
            "e-2", now.minus(20, ChronoUnit.MINUTES), "WARN", "payment-service", "prod",
            "PAYMENT_RETRY", "Retrying payment gateway", "t-101", "r-101", null,
            null, null, now, now.plusSeconds(3600), "org-1", TEST_PROJECT, "key-1", "PAYMENT_RETRY::Retrying payment gateway"
        );

        LogEventDocument doc3 = new LogEventDocument(
            "e-3", now.minus(10, ChronoUnit.MINUTES), "INFO", "order-service", "prod",
            "ORDER_CREATED", "Order 42 created successfully", "t-100", "r-102", null,
            null, null, now, now.plusSeconds(3600), "org-1", TEST_PROJECT, "key-1", "ORDER_CREATED"
        );

        mongoTemplate.save(doc1);
        mongoTemplate.save(doc2);
        mongoTemplate.save(doc3);
    }

    @Test
    void searchLogsWithTimeRangeAndLevelFilter() {
        Instant now = Instant.now();
        LogSearchResponse response = logQueryService.searchLogs(
            TEST_PROJECT,
            now.minus(1, ChronoUnit.HOURS),
            now,
            "ERROR",
            null, null, null, null, null, null, null, null, 10
        );

        assertEquals(1, response.events().size());
        assertEquals("ORDER_FAILED", response.events().get(0).eventType());
        assertEquals("ERROR", response.events().get(0).level());
    }

    @Test
    void searchLogsWithTraceIdCorrelation() {
        Instant now = Instant.now();
        LogSearchResponse response = logQueryService.searchLogs(
            TEST_PROJECT,
            now.minus(1, ChronoUnit.HOURS),
            now,
            null, null, null, null, "t-100", null, null, null, null, 10
        );

        assertEquals(2, response.events().size());
    }

    @Test
    void paginationWithLimitAndCursor() {
        Instant now = Instant.now();
        LogSearchResponse page1 = logQueryService.searchLogs(
            TEST_PROJECT,
            now.minus(1, ChronoUnit.HOURS),
            now,
            null, null, null, null, null, null, null, null, null, 2
        );

        assertEquals(2, page1.events().size());
        assertTrue(page1.hasMore());
        assertNotNull(page1.nextCursor());

        LogSearchResponse page2 = logQueryService.searchLogs(
            TEST_PROJECT,
            now.minus(1, ChronoUnit.HOURS),
            now,
            null, null, null, null, null, null, null, null, page1.nextCursor(), 2
        );

        assertEquals(1, page2.events().size());
        assertFalse(page2.hasMore());
    }
}
