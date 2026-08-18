package com.example.logmonitor.persistence;

import com.example.logmonitor.ingestion.domain.LogEvent;
import com.example.logmonitor.common.security.SensitiveDataRedactor;
import com.example.logmonitor.livetail.application.LiveTailPublisher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class LogEventPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(LogEventPersistenceService.class);
    private static final int MAX_RETRIES = 3;

    private final MongoTemplate mongoTemplate;
    private final LiveTailPublisher liveTailPublisher;
    private final Timer persistenceTimer;
    private final Counter persistedEventsCounter;
    private final Counter persistenceFailedCounter;
    private final Counter persistenceRetryCounter;
    private final Counter persistenceFailureCounter;
    private final SensitiveDataRedactor redactor;

    public LogEventPersistenceService(
        MongoTemplate mongoTemplate,
        LiveTailPublisher liveTailPublisher,
        MeterRegistry registry,
        SensitiveDataRedactor redactor
    ) {
        this.mongoTemplate = mongoTemplate;
        this.liveTailPublisher = liveTailPublisher;
        this.redactor = redactor;
        this.persistenceTimer = Timer.builder("ingestion.persistence.duration")
            .description("Time taken to bulk write log events to MongoDB")
            .register(registry);
        this.persistedEventsCounter = Counter.builder("ingestion.persistence.events.saved")
            .description("Total log events successfully written to MongoDB")
            .register(registry);
        this.persistenceFailedCounter = Counter.builder("ingestion.persistence.events.failed")
            .description("Total log events failed during persistence")
            .register(registry);
        this.persistenceRetryCounter = Counter.builder("ingestion.persistence.retries")
            .description("Retry attempts made after transient persistence failures")
            .register(registry);
        this.persistenceFailureCounter = Counter.builder("ingestion.persistence.failures")
            .description("Persistence batches that exhausted all retry attempts")
            .register(registry);
    }

    public void persist(List<LogEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }

        List<LogEventDocument> documents = events.stream()
            .map(this::toDocument)
            .toList();

        persistenceTimer.record(() -> {
            int attempt = 0;
            long backoffMs = 100;
            while (attempt < MAX_RETRIES) {
                try {
                    BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, LogEventDocument.class);
                    bulkOps.insert(documents);
                    bulkOps.execute();
                    persistedEventsCounter.increment(documents.size());
                    liveTailPublisher.publish(events);
                    return;
                } catch (Exception ex) {
                    attempt++;
                    log.warn(
                        "Mongo bulk insert attempt {}/{} failed: type={} message={}",
                        attempt,
                        MAX_RETRIES,
                        ex.getClass().getSimpleName(),
                        redactor.redactText(ex.getMessage())
                    );
                    if (attempt >= MAX_RETRIES) {
                        persistenceFailedCounter.increment(documents.size());
                        persistenceFailureCounter.increment();
                        log.error(
                            "Exhausted retries persisting batch of {} events: type={} message={}",
                            documents.size(),
                            ex.getClass().getSimpleName(),
                            redactor.redactText(ex.getMessage())
                        );
                        throw ex;
                    }
                    persistenceRetryCounter.increment();
                    try {
                        Thread.sleep(backoffMs);
                        backoffMs *= 2;
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during persistence retry backoff", ie);
                    }
                }
            }
        });
    }

    private LogEventDocument toDocument(LogEvent event) {
        return new LogEventDocument(
            event.eventId(),
            event.timestamp(),
            event.level(),
            event.service(),
            event.environment(),
            event.eventType(),
            event.message(),
            event.traceId(),
            event.requestId(),
            toSafeMap(event.exception()),
            event.context(),
            event.tags(),
            event.receivedAt(),
            event.expireAt(),
            event.organizationId(),
            event.projectId(),
            event.apiKeyId(),
            event.errorFingerprint()
        );
    }

    private Map<String, Object> toSafeMap(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                .collect(Collectors.toMap(
                    entry -> String.valueOf(entry.getKey()),
                    entry -> entry.getValue(),
                    (left, right) -> right,
                    java.util.LinkedHashMap::new));
        }
        if (value instanceof LogEvent.ExceptionDetails exception) {
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("type", exception.type());
            result.put("message", exception.message());
            result.put("stackTrace", exception.stackTrace());
            return result;
        }
        return Map.of("value", value);
    }
}
