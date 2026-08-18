package com.example.logmonitor.ingestion.infrastructure;

import com.example.logmonitor.ingestion.api.IngestionRequest;
import com.example.logmonitor.ingestion.domain.LogEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class IngestionQueueTest {

    @Test
    void atomicOfferAllRejectsWhenCapacityExceeded() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        IngestionQueue queue = new IngestionQueue(2, registry);

        LogEvent e1 = createEvent("msg1");
        LogEvent e2 = createEvent("msg2");
        LogEvent e3 = createEvent("msg3");

        assertTrue(queue.offerAll(List.of(e1, e2)));
        assertEquals(2, queue.size());
        assertEquals(0, queue.remainingCapacity());

        // Batch of 1 should be rejected atomically because queue is full
        assertFalse(queue.offerAll(List.of(e3)));
        assertEquals(2, queue.size());
        assertEquals(1, queue.rejectedCount());
    }

    @Test
    void concurrentSingleAndBatchAdmissionNeverPartiallyAddsTheBatch() throws Exception {
        IngestionQueue queue = new IngestionQueue(2, new SimpleMeterRegistry());
        LogEvent first = createEvent("batch-1");
        LogEvent second = createEvent("batch-2");
        LogEvent single = createEvent("single");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<Boolean> batchResult = executor.submit(() -> {
            start.await();
            return queue.offerAll(List.of(first, second));
        });
        Future<Boolean> singleResult = executor.submit(() -> {
            start.await();
            return queue.offer(single);
        });
        start.countDown();

        boolean batchAccepted = batchResult.get(2, TimeUnit.SECONDS);
        boolean singleAccepted = singleResult.get(2, TimeUnit.SECONDS);
        executor.shutdownNow();

        if (batchAccepted) {
            assertFalse(singleAccepted);
            assertEquals(2, queue.size());
            assertEquals(first, queue.poll());
            assertEquals(second, queue.poll());
        } else if (singleAccepted) {
            assertEquals(1, queue.size());
            assertEquals(single, queue.poll());
        } else {
            assertEquals(0, queue.size());
        }
    }

    @Test
    void rejectsSingleAndBatchAdmissionAfterShutdown() {
        IngestionQueue queue = new IngestionQueue(2, new SimpleMeterRegistry());
        queue.stopAccepting();

        assertFalse(queue.isAccepting());
        assertFalse(queue.offer(createEvent("after-shutdown-single")));
        assertFalse(queue.offerAll(List.of(createEvent("after-shutdown-batch"))));
        assertEquals(0, queue.size());
        assertEquals(2, queue.rejectedCount());
    }

    private LogEvent createEvent(String message) {
        IngestionRequest request = new IngestionRequest(
            null, null, "INFO", "service", "dev", "LOG", message, null, null, null, null, null
        );
        return LogEvent.of(request, "org", "proj", "key");
    }
}
