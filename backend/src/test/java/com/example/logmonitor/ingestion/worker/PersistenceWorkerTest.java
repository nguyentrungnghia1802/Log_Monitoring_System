package com.example.logmonitor.ingestion.worker;

import com.example.logmonitor.common.security.RedactionProperties;
import com.example.logmonitor.common.security.SensitiveDataRedactor;
import com.example.logmonitor.ingestion.api.IngestionRequest;
import com.example.logmonitor.ingestion.domain.LogEvent;
import com.example.logmonitor.ingestion.infrastructure.IngestionQueue;
import com.example.logmonitor.persistence.LogEventPersistenceService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersistenceWorkerTest {

    @Test
    void workerContinuesWithNextBatchAfterPersistenceException() throws Exception {
        IngestionQueue queue = mock(IngestionQueue.class);
        LogEventPersistenceService persistenceService = mock(LogEventPersistenceService.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        LogEvent first = createEvent("first");
        LogEvent second = createEvent("second");
        CountDownLatch secondBatchPersisted = new CountDownLatch(1);

        when(queue.poll(anyLong(), eq(TimeUnit.MILLISECONDS)))
            .thenReturn(first, null, second, null)
            .thenThrow(new InterruptedException("test stop"));
        doThrow(new RuntimeException("temporary persistence failure"))
            .doAnswer(invocation -> {
                secondBatchPersisted.countDown();
                return null;
            })
            .when(persistenceService).persist(anyList());

        PersistenceWorker worker = newWorker(queue, persistenceService, meterRegistry, 1);
        worker.start();

        assertTrue(secondBatchPersisted.await(2, TimeUnit.SECONDS));
        worker.shutdown();

        verify(persistenceService, times(2)).persist(anyList());
        assertEquals(1.0, meterRegistry.counter("ingestion.worker.persistence.failed_batches").count());
        assertEquals(1.0, meterRegistry.counter("ingestion.worker.persistence.failed_events").count());
        assertEquals(2.0, meterRegistry.summary("ingestion.batch.size").count());
        assertEquals(0.0, meterRegistry.get("ingestion.worker.active").gauge().value());
    }

    @Test
    void shutdownPersistsEventsStillInQueueAndRecordsObservedDepth() throws Exception {
        IngestionQueue queue = mock(IngestionQueue.class);
        LogEventPersistenceService persistenceService = mock(LogEventPersistenceService.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        LogEvent queuedEvent = createEvent("queued-at-shutdown");

        when(queue.size()).thenReturn(1, 0);
        when(queue.poll(anyLong(), eq(TimeUnit.MILLISECONDS)))
            .thenThrow(new InterruptedException("test stop"));
        when(queue.poll()).thenReturn(queuedEvent).thenReturn((LogEvent) null);

        PersistenceWorker worker = newWorker(queue, persistenceService, meterRegistry, 1);
        worker.start();
        worker.shutdown();

        verify(persistenceService).persist(List.of(queuedEvent));
        assertEquals(1.0, meterRegistry.counter("ingestion.worker.shutdown.remaining_events").count());
        assertEquals(1.0, meterRegistry.get("ingestion.worker.shutdown.queue_depth").gauge().value());
        assertEquals(0.0, meterRegistry.get("ingestion.worker.active").gauge().value());
    }

    @Test
    void flushesPartialBatchBeforeWorkerStops() throws Exception {
        IngestionQueue queue = mock(IngestionQueue.class);
        LogEventPersistenceService persistenceService = mock(LogEventPersistenceService.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        LogEvent partialEvent = createEvent("partial-batch");
        CountDownLatch persisted = new CountDownLatch(1);

        when(queue.poll(anyLong(), eq(TimeUnit.MILLISECONDS)))
            .thenReturn(partialEvent, (LogEvent) null)
            .thenThrow(new InterruptedException("test stop"));
        doAnswer(invocation -> {
            persisted.countDown();
            return null;
        }).when(persistenceService).persist(anyList());

        PersistenceWorker worker = newWorker(queue, persistenceService, meterRegistry, 10, 10);
        worker.start();

        assertTrue(persisted.await(2, TimeUnit.SECONDS));
        worker.shutdown();

        verify(persistenceService).persist(List.of(partialEvent));
    }

    private PersistenceWorker newWorker(
        IngestionQueue queue,
        LogEventPersistenceService persistenceService,
        SimpleMeterRegistry meterRegistry,
        int batchMaxSize
    ) {
        return newWorker(queue, persistenceService, meterRegistry, batchMaxSize, 10);
    }

    private PersistenceWorker newWorker(
        IngestionQueue queue,
        LogEventPersistenceService persistenceService,
        SimpleMeterRegistry meterRegistry,
        int batchMaxSize,
        long shutdownTimeoutMs
    ) {
        return new PersistenceWorker(
            queue,
            persistenceService,
            1,
            batchMaxSize,
            10,
            shutdownTimeoutMs,
            new SensitiveDataRedactor(new RedactionProperties()),
            meterRegistry
        );
    }

    private LogEvent createEvent(String message) {
        return LogEvent.of(
            new IngestionRequest(
                "worker-test-event-" + message, Instant.now(), "INFO", "test-service", "test", "WORKER_TEST",
                message, null, null, null, null, null
            ),
            "org-test",
            "project-test",
            "api-key-test",
            3600
        );
    }
}
