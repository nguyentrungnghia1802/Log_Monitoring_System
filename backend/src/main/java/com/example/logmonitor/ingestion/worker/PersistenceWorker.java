package com.example.logmonitor.ingestion.worker;

import com.example.logmonitor.common.security.SensitiveDataRedactor;
import com.example.logmonitor.ingestion.domain.LogEvent;
import com.example.logmonitor.ingestion.infrastructure.IngestionQueue;
import com.example.logmonitor.persistence.LogEventPersistenceService;
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

@Component
public class PersistenceWorker {

    private static final Logger log = LoggerFactory.getLogger(PersistenceWorker.class);

    private final IngestionQueue ingestionQueue;
    private final LogEventPersistenceService persistenceService;
    private final int workerCount;
    private final int batchMaxSize;
    private final long maxWaitMs;
    private final SensitiveDataRedactor redactor;
    private volatile boolean running = true;
    private ExecutorService executorService;

    public PersistenceWorker(
        IngestionQueue ingestionQueue,
        LogEventPersistenceService persistenceService,
        @Value("${ingestion.workers:4}") int workerCount,
        @Value("${ingestion.batch.max-size:500}") int batchMaxSize,
        @Value("${ingestion.batch.max-wait-ms:500}") long maxWaitMs,
        SensitiveDataRedactor redactor
    ) {
        this.ingestionQueue = ingestionQueue;
        this.persistenceService = persistenceService;
        this.workerCount = workerCount;
        this.batchMaxSize = batchMaxSize;
        this.maxWaitMs = maxWaitMs;
        this.redactor = redactor;
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
        log.info("Initiating PersistenceWorker graceful shutdown...");
        running = false;

        if (executorService != null) {
            executorService.shutdown();
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        }

        // Graceful drain remaining events in queue
        drainRemainingEvents();
    }

    private void drainRemainingEvents() {
        List<LogEvent> remaining = new ArrayList<>();
        LogEvent event;
        while ((event = ingestionQueue.poll()) != null) {
            remaining.add(event);
            if (remaining.size() >= batchMaxSize) {
                tryPersist(remaining);
                remaining.clear();
            }
        }
        if (!remaining.isEmpty()) {
            tryPersist(remaining);
        }
        log.info("Completed graceful drain of ingestion queue");
    }

    private void runLoop() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                LogEvent first = ingestionQueue.poll(maxWaitMs, TimeUnit.MILLISECONDS);
                if (first == null) {
                    continue;
                }

                List<LogEvent> batch = new ArrayList<>();
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

                tryPersist(batch);
            } catch (InterruptedException ex) {
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
            log.error(
                "Failed to persist batch of size {}: type={} message={}",
                batch.size(),
                ex.getClass().getSimpleName(),
                redactor.redactText(ex.getMessage())
            );
        }
    }
}
