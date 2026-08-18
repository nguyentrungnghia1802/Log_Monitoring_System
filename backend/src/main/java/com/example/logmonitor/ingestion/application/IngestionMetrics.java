package com.example.logmonitor.ingestion.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Request-level ingestion telemetry that is not owned by the bounded queue.
 *
 * <p>The queue owns event admission counters; this component records that an
 * ingestion service call reached the application boundary, including calls
 * that are later rejected by validation, backpressure, or shutdown.</p>
 */
@Component
public class IngestionMetrics {

    private final Counter receivedCounter;

    public IngestionMetrics(MeterRegistry meterRegistry) {
        this.receivedCounter = Counter.builder("ingestion.received")
            .description("Ingestion service calls received before admission")
            .register(meterRegistry);
    }

    public void recordReceived() {
        receivedCounter.increment();
    }
}
