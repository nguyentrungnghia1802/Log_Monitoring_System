package com.example.logmonitor.persistence;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.logmonitor.common.security.RedactionProperties;
import com.example.logmonitor.common.security.SensitiveDataRedactor;
import com.example.logmonitor.ingestion.api.IngestionRequest;
import com.example.logmonitor.ingestion.domain.LogEvent;
import com.example.logmonitor.livetail.application.LiveTailPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LogEventPersistenceServiceTest {

    private MongoTemplate mongoTemplate;
    private BulkOperations bulkOperations;
    private LiveTailPublisher liveTailPublisher;
    private SimpleMeterRegistry meterRegistry;
    private LogEventPersistenceService persistenceService;
    private List<LogEvent> events;

    @BeforeEach
    void setUp() {
        mongoTemplate = mock(MongoTemplate.class);
        bulkOperations = mock(BulkOperations.class);
        liveTailPublisher = mock(LiveTailPublisher.class);
        meterRegistry = new SimpleMeterRegistry();
        persistenceService = new LogEventPersistenceService(
            mongoTemplate,
            liveTailPublisher,
            meterRegistry,
            new SensitiveDataRedactor(new RedactionProperties())
        );
        events = List.of(createEvent("persistence-retry"));
    }

    @Test
    void retriesTransientMongoFailureAndPublishesAfterOneSuccessfulWrite() {
        when(mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, LogEventDocument.class))
            .thenThrow(new RuntimeException("temporary Mongo outage"))
            .thenReturn(bulkOperations);
        when(bulkOperations.insert(anyList())).thenReturn(bulkOperations);
        when(bulkOperations.execute()).thenReturn(null);

        persistenceService.persist(events);

        verify(mongoTemplate, times(2)).bulkOps(BulkOperations.BulkMode.UNORDERED, LogEventDocument.class);
        verify(bulkOperations).insert(anyList());
        verify(bulkOperations).execute();
        verify(liveTailPublisher).publish(events);
        assertEquals(1.0, meterRegistry.counter("ingestion.persistence.events.saved").count());
        assertEquals(0.0, meterRegistry.counter("ingestion.persistence.events.failed").count());
        assertEquals(1.0, meterRegistry.counter("ingestion.persistence.retries").count());
        assertEquals(1L, meterRegistry.timer("ingestion.persistence.duration").count());
    }

    @Test
    void stopsAfterBoundedRetriesAndRecordsFailedEventsWithoutPublishing() {
        RuntimeException failure = new RuntimeException("password=should-not-be-logged");
        when(mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, LogEventDocument.class))
            .thenThrow(failure);

        Logger logger = (Logger) LoggerFactory.getLogger(LogEventPersistenceService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThrows(RuntimeException.class, () -> persistenceService.persist(events));

            String messages = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
            assertTrue(messages.contains("password=[REDACTED]"));
            assertFalse(messages.contains("password=should-not-be-logged"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        verify(mongoTemplate, times(3)).bulkOps(BulkOperations.BulkMode.UNORDERED, LogEventDocument.class);
        verify(liveTailPublisher, never()).publish(events);
        assertEquals(0.0, meterRegistry.counter("ingestion.persistence.events.saved").count());
        assertEquals(1.0, meterRegistry.counter("ingestion.persistence.events.failed").count());
        assertEquals(2.0, meterRegistry.counter("ingestion.persistence.retries").count());
        assertEquals(1.0, meterRegistry.counter("ingestion.persistence.failures").count());
        assertEquals(1L, meterRegistry.timer("ingestion.persistence.duration").count());
    }

    private LogEvent createEvent(String message) {
        return LogEvent.of(
            new IngestionRequest(
                "failure-test-event", null, "ERROR", "test-service", "test", "TEST_FAILURE",
                message, null, null, null, null, null
            ),
            "org-test",
            "project-test",
            "api-key-test",
            3600
        );
    }
}
