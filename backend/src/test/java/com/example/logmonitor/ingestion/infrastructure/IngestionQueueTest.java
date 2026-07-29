package com.example.logmonitor.ingestion.infrastructure;

import com.example.logmonitor.ingestion.api.IngestionRequest;
import com.example.logmonitor.ingestion.domain.LogEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

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

    private LogEvent createEvent(String message) {
        IngestionRequest request = new IngestionRequest(
            null, null, "INFO", "service", "dev", "LOG", message, null, null, null, null, null
        );
        return LogEvent.of(request, "org", "proj", "key");
    }
}
