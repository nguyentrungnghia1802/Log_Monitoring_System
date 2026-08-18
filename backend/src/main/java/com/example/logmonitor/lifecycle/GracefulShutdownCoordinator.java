package com.example.logmonitor.lifecycle;

import com.example.logmonitor.ingestion.infrastructure.IngestionQueue;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Coordinates the application-level part of graceful shutdown.
 *
 * <p>Spring Boot's web-server shutdown prevents new socket work, but the
 * ingestion queue also needs an application gate so requests already being
 * dispatched cannot admit new in-memory events after readiness is withdrawn.
 * The context-closed event is the common boundary for SIGTERM and an explicit
 * application close in tests.</p>
 */
@Component
public class GracefulShutdownCoordinator {

    private static final Logger log = LoggerFactory.getLogger(GracefulShutdownCoordinator.class);

    private final ApplicationEventPublisher eventPublisher;
    private final IngestionQueue ingestionQueue;
    private final Counter shutdownStartedCounter;
    private final AtomicBoolean acceptingTraffic = new AtomicBoolean(true);

    public GracefulShutdownCoordinator(
        ApplicationEventPublisher eventPublisher,
        IngestionQueue ingestionQueue,
        MeterRegistry meterRegistry
    ) {
        this.eventPublisher = eventPublisher;
        this.ingestionQueue = ingestionQueue;
        this.shutdownStartedCounter = Counter.builder("application.shutdown.started")
            .description("Number of graceful shutdown sequences started")
            .register(meterRegistry);
        Gauge.builder("application.shutdown.accepting", acceptingTraffic, value -> value.get() ? 1 : 0)
            .description("Whether the application still accepts new traffic")
            .register(meterRegistry);
    }

    @EventListener
    @Order(Integer.MIN_VALUE)
    public void onContextClosed(ContextClosedEvent event) {
        beginShutdown();
    }

    public synchronized void beginShutdown() {
        if (!acceptingTraffic.compareAndSet(true, false)) {
            return;
        }

        shutdownStartedCounter.increment();
        // Readiness must be withdrawn before queue admission is closed so load
        // balancers stop routing traffic before the final producer gate.
        eventPublisher.publishEvent(new AvailabilityChangeEvent<>(this, ReadinessState.REFUSING_TRAFFIC));
        ingestionQueue.stopAccepting();
        log.info("Graceful shutdown started: readiness=REFUSING_TRAFFIC, ingestion admission=STOPPED");
    }

    public boolean isAcceptingTraffic() {
        return acceptingTraffic.get();
    }
}
