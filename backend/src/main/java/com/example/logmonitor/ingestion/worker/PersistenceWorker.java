package com.example.logmonitor.ingestion.worker;

import com.example.logmonitor.common.security.SensitiveDataRedactor;
import com.example.logmonitor.ingestion.domain.LogEvent;
import com.example.logmonitor.ingestion.infrastructure.IngestionQueue;
import com.example.logmonitor.persistence.LogEventPersistenceService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class PersistenceWorker {

    private static final Logger log = LoggerFactory.getLogger(PersistenceWorker.class);

    private final IngestionQueue ingestionQueue;
    private final LogEventPersistenceService persistenceService;
    private final int workerCount;
    private final int batchMaxSize;
    private final long maxWaitMs;
    private final SensitiveDataRedactor redactor;
    private final Counter failedBatchCounter;
    private final Counter failedEventCounter;
    private final Counter shutdownRemainingEventCounter;
    private final Counter shutdownUnfinishedEventCounter;
    private final AtomicInteger shutdownQueueDepth = new AtomicInteger();
    private final AtomicBoolean shutdownStarted = new AtomicBoolean();
    private final long shutdownTimeoutMs;
    private volatile boolean running = true;
    private ExecutorService executorService;

    public PersistenceWorker(
        IngestionQueue ingestionQueue,
        LogEventPersistenceService persistenceService,
        @Value("${ingestion.workers:4}") int workerCount,
        @Value("${ingestion.batch.max-size:500}") int batchMaxSize,
        @Value("${ingestion.batch.max-wait-ms:500}") long maxWaitMs,
        @Value("${shutdown.timeout-ms:5000}") long shutdownTimeoutMs,
        SensitiveDataRedactor redactor,
        MeterRegistry meterRegistry
    ) {
        this.ingestionQueue = ingestionQueue;
        this.persistenceService = persistenceService;
        this.workerCount = workerCount;
        this.batchMaxSize = batchMaxSize;
        this.maxWaitMs = maxWaitMs;
        this.shutdownTimeoutMs = Math.max(1, shutdownTimeoutMs);
        this.redactor = redactor;
        this.failedBatchCounter = Counter.builder("ingestion.worker.persistence.failed_batches")
            .description("Batches rejected after persistence retry exhaustion")
            .register(meterRegistry);
        this.failedEventCounter = Counter.builder("ingestion.worker.persistence.failed_events")
            .description("Events in batches rejected after persistence retry exhaustion")
            .register(meterRegistry);
        this.shutdownRemainingEventCounter = Counter.builder("ingestion.worker.shutdown.remaining_events")
            .description("Events observed in the ingestion queue at worker shutdown")
            .register(meterRegistry);
        this.shutdownUnfinishedEventCounter = Counter.builder("ingestion.worker.shutdown.unfinished_events")
            .description("Events still queued after the graceful shutdown deadline")
            .register(meterRegistry);
        Gauge.builder("ingestion.worker.shutdown.queue_depth", shutdownQueueDepth, AtomicInteger::get)
            .description("Queue depth observed when graceful worker shutdown began")
            .register(meterRegistry);
    }

    @PostConstruct
    public void start() {
        executorService = Executors.newFixedThreadPool(workerCount, runnable -> {
            Thread thread = new Thread(runnable, "ingestion-worker-");
            thread.setDaemon(false);
            return thread;
        });

        for (int i = 0; i < workerCount; i++) {
            executorService.submit(this::runLoop);
        }
    }

    @PreDestroy
    public void shutdown() throws InterruptedException {
        if (!shutdownStarted.compareAndSet(false, true)) {
            return;
        }
        log.info("Initiating PersistenceWorker graceful shutdown...");
        int queuedAtShutdown = ingestionQueue.size();
        shutdownQueueDepth.set(queuedAtShutdown);
        if (queuedAtShutdown > 0) {
            shutdownRemainingEventCounter.increment(queuedAtShutdown);
        }
        running = false;
        long shutdownDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(shutdownTimeoutMs);

        if (executorService != null) {
            executorService.shutdown();
            long remainingNanos = shutdownDeadline - System.nanoTime();
            if (remainingNanos > 0 && !executorService.awaitTermination(remainingNanos, TimeUnit.NANOSECONDS)) {
                log.warn("Persistence workers exceeded shutdown deadline of {} ms; interrupting workers", shutdownTimeoutMs);
                executorService.shutdownNow();
            }
        }

        // Graceful drain remaining events in queue
        int drainedEvents = drainRemainingEvents(shutdownDeadline);
        int remainingQueueDepth = ingestionQueue.size();
        if (remainingQueueDepth > 0) {
            shutdownUnfinishedEventCounter.increment(remainingQueueDepth);
        }
        log.info(
            "Completed graceful drain of ingestion queue: queuedAtShutdown={} drainedEvents={} remainingQueueDepth={}",
            queuedAtShutdown,
            drainedEvents,
            remainingQueueDepth
        );
    }

    private int drainRemainingEvents(long shutdownDeadline) {
        List<LogEvent> remaining = new ArrayList<>();
        int drainedEvents = 0;
        LogEvent event;
        while (System.nanoTime() < shutdownDeadline && (event = ingestionQueue.poll()) != null) {
            remaining.add(event);
            if (remaining.size() >= batchMaxSize) {
                tryPersist(List.copyOf(remaining));
                drainedEvents += remaining.size();
                remaining.clear();
            }
        }
        if (!remaining.isEmpty()) {
            if (System.nanoTime() < shutdownDeadline) {
                tryPersist(List.copyOf(remaining));
                drainedEvents += remaining.size();
            } else {
                log.warn("Shutdown deadline reached before flushing partial batch of {} events", remaining.size());
            }
        }
        return drainedEvents;
    }

    private void runLoop() {
        List<LogEvent> batch = new ArrayList<>();
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                LogEvent first = ingestionQueue.poll(maxWaitMs, TimeUnit.MILLISECONDS);
                if (first == null) {
                    continue;
                }

                batch.clear();
                batch.add(first);
                long deadline = System.currentTimeMillis() + maxWaitMs;

                while (batch.size() < batchMaxSize) {
                    long remainingWait = deadline - System.currentTimeMillis();
                    if (remainingWait <= 0) {
                        break;
                    }
                    LogEvent next = ingestionQueue.poll(remainingWait, TimeUnit.MILLISECONDS);
                    if (next == null) {
                        break;
                    }
                    batch.add(next);
                }

                tryPersist(List.copyOf(batch));
                batch.clear();
            } catch (InterruptedException ex) {
                if (!batch.isEmpty()) {
                    tryPersist(List.copyOf(batch));
                    batch.clear();
                }
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ex) {
                log.error(
                    "Unexpected error in worker loop: type={} message={}",
                    ex.getClass().getSimpleName(),
                    redactor.redactText(ex.getMessage())
                );
            }
        }
    }

    private void tryPersist(List<LogEvent> batch) {
        if (batch.isEmpty()) {
            return;
        }
        try {
            persistenceService.persist(batch);
            log.debug("Persisted worker batch size={}", batch.size());
        } catch (Exception ex) {
            failedBatchCounter.increment();
            failedEventCounter.increment(batch.size());
            log.error(
                "Failed to persist batch of size {}: type={} message={}",
                batch.size(),
                ex.getClass().getSimpleName(),
                redactor.redactText(ex.getMessage())
            );
        }
    }
}
