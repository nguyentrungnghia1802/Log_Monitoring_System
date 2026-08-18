package com.example.logmonitor.logquery.application;

import com.example.logmonitor.logquery.api.LogSearchResponse;
import com.example.logmonitor.persistence.LogEventDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
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

        LogEventDocument foreignProjectEvent = new LogEventDocument(
            "foreign-event", now.minus(5, ChronoUnit.MINUTES), "ERROR", "order-service", "prod",
            "ORDER_FAILED", "Foreign project event", "t-100", "r-foreign", null,
            null, null, now, now.plusSeconds(3600), "org-2", "foreign-project", "key-2", "ORDER_FAILED::Foreign project event"
        );
        mongoTemplate.save(foreignProjectEvent);
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

    @Test
    void defaultsToBoundedRangeAndRejectsOversizedPageAndRange() {
        LogSearchResponse defaultRange = logQueryService.searchLogs(
            TEST_PROJECT, null, null, null, null, null, null, null, null, null, null, null, null
        );

        assertEquals(3, defaultRange.events().size());

        LogQueryException oversizedPage = assertThrows(LogQueryException.class, () -> logQueryService.searchLogs(
            TEST_PROJECT, Instant.now().minus(1, ChronoUnit.HOURS), Instant.now(),
            null, null, null, null, null, null, null, null, null, 201
        ));
        assertEquals("SEARCH_PAGE_SIZE_TOO_LARGE", oversizedPage.getCode());

        LogQueryException oversizedRange = assertThrows(LogQueryException.class, () -> logQueryService.searchLogs(
            TEST_PROJECT, Instant.now().minus(169, ChronoUnit.HOURS), Instant.now(),
            null, null, null, null, null, null, null, null, null, 10
        ));
        assertEquals("SEARCH_RANGE_TOO_LARGE", oversizedRange.getCode());
    }

    @Test
    void rejectsMalformedCursorAndTreatsSearchAsLiteralText() {
        LogQueryException invalidCursor = assertThrows(LogQueryException.class, () -> logQueryService.searchLogs(
            TEST_PROJECT, Instant.now().minus(1, ChronoUnit.HOURS), Instant.now(),
            null, null, null, null, null, null, null, null, "not-a-cursor", 10
        ));
        assertEquals("INVALID_CURSOR", invalidCursor.getCode());

        LogSearchResponse literalRegex = logQueryService.searchLogs(
            TEST_PROJECT, Instant.now().minus(1, ChronoUnit.HOURS), Instant.now(),
            null, null, null, null, null, null, null, ".*", null, 10
        );
        assertTrue(literalRegex.events().isEmpty());

        LogSearchResponse literalText = logQueryService.searchLogs(
            TEST_PROJECT, Instant.now().minus(1, ChronoUnit.HOURS), Instant.now(),
            null, null, null, null, null, null, null, "timeout", null, 10
        );
        assertEquals(1, literalText.events().size());
    }

    @Test
    void keepsEqualTimestampPaginationDeterministicWhenFirstDocumentIsDeleted() {
        Instant sameTimestamp = Instant.now().minus(2, ChronoUnit.MINUTES);
        LogEventDocument first = new LogEventDocument(
            "same-1", sameTimestamp, "ERROR", "pagination-service", "prod", "SAME_TIMESTAMP",
            "first", "trace-same", "request-same-1", null, null, null, sameTimestamp, sameTimestamp.plusSeconds(3600),
            "org-1", TEST_PROJECT, "key-1", "SAME_TIMESTAMP::first"
        );
        LogEventDocument second = new LogEventDocument(
            "same-2", sameTimestamp, "ERROR", "pagination-service", "prod", "SAME_TIMESTAMP",
            "second", "trace-same", "request-same-2", null, null, null, sameTimestamp, sameTimestamp.plusSeconds(3600),
            "org-1", TEST_PROJECT, "key-1", "SAME_TIMESTAMP::second"
        );
        mongoTemplate.save(first);
        mongoTemplate.save(second);

        Instant from = sameTimestamp.minusSeconds(1);
        Instant to = sameTimestamp.plusSeconds(1);
        LogSearchResponse page1 = logQueryService.searchLogs(
            TEST_PROJECT, from, to, null, null, null, "SAME_TIMESTAMP", null, null, null, null, null, 1
        );
        assertEquals(1, page1.events().size());
        assertTrue(page1.hasMore());

        mongoTemplate.remove(Query.query(Criteria.where("_id").is(page1.events().get(0).id())), LogEventDocument.class);

        LogSearchResponse page2 = logQueryService.searchLogs(
            TEST_PROJECT, from, to, null, null, null, "SAME_TIMESTAMP", null, null, null, null, page1.nextCursor(), 1
        );
        assertEquals(1, page2.events().size());
        assertFalse(page2.hasMore());
        assertNotEquals(page1.events().get(0).id(), page2.events().get(0).id());
        assertEquals(sameTimestamp.toEpochMilli(), page2.events().get(0).timestamp().toEpochMilli());
    }

    @Test
    void returnsSummaryProjectionAndKeepsDetailProjectScoped() {
        LogSearchResponse response = logQueryService.searchLogs(
            TEST_PROJECT, Instant.now().minus(1, ChronoUnit.HOURS), Instant.now(),
            "ERROR", null, null, null, null, null, null, null, null, 10
        );

        assertEquals(1, response.events().size());
        assertEquals("test-proj-query", response.events().get(0).projectId());
        assertEquals("Order timeout occurred", response.events().get(0).message());

        String eventId = response.events().get(0).id();
        assertTrue(logQueryService.getLogById(TEST_PROJECT, eventId).isPresent());
        assertTrue(logQueryService.getLogById("foreign-project", eventId).isEmpty());
    }
}
