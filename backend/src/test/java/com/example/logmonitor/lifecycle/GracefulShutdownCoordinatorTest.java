package com.example.logmonitor.lifecycle;

import com.example.logmonitor.ingestion.infrastructure.IngestionQueue;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.availability.AvailabilityChangeEvent;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GracefulShutdownCoordinatorTest {

    @Test
    void publishesReadinessWithdrawalBeforeClosingQueueAdmissionAndIsIdempotent() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        IngestionQueue queue = new IngestionQueue(2, meterRegistry);
        List<Object> events = new ArrayList<>();
        GracefulShutdownCoordinator coordinator = new GracefulShutdownCoordinator(
            events::add,
            queue,
            meterRegistry
        );

        coordinator.beginShutdown();
        coordinator.beginShutdown();

        assertFalse(coordinator.isAcceptingTraffic());
        assertFalse(queue.isAccepting());
        assertEquals(1.0, meterRegistry.counter("application.shutdown.started").count());
        assertEquals(0.0, meterRegistry.get("application.shutdown.accepting").gauge().value());
        AvailabilityChangeEvent<?> event = assertInstanceOf(AvailabilityChangeEvent.class, events.get(0));
        assertEquals("REFUSING_TRAFFIC", event.getState().toString());
        assertEquals(1, events.size());
    }
}
