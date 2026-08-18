package com.example.logmonitor.persistence;

import com.mongodb.ExplainVerbosity;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gte;
import static com.mongodb.client.model.Filters.lte;
import static com.mongodb.client.model.Sorts.orderBy;
import static com.mongodb.client.model.Sorts.descending;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Evidence tests for the representative query shapes used by log search and analytics.
 *
 * <p>The test deliberately uses the MongoDB query planner rather than a mocked repository.
 * It protects the project-scoped compound indexes from silently regressing to a collection
 * scan as the document model and analytics pipelines evolve.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class MongoQueryPlanIntegrationTest {

    private static final Instant DATA_START = Instant.parse("2026-08-18T00:00:00Z");
    private static final int SEEDED_EVENT_COUNT = 240;

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
    }

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void clearEventsWithoutDroppingIndexes() {
        events().deleteMany(new Document());
    }

    @Test
    void representativeFindQueriesUseProjectScopedIndexesWithoutCollectionScans() {
        seedEvents();

        Date from = dateAt(60);
        Date to = dateAt(220);
        Bson projectAndTime = and(
            eq("project_id", "project-a"),
            gte("timestamp", from),
            lte("timestamp", to)
        );

        List<PlanEvidence> evidence = List.of(
            explainFind(
                "project + recent time",
                projectAndTime,
                orderBy(descending("timestamp"), descending("_id"))
            ),
            explainFind(
                "project + environment + time",
                and(projectAndTime, eq("environment", "production")),
                orderBy(descending("timestamp"), descending("_id"))
            ),
            explainFind(
                "project + service + time",
                and(projectAndTime, eq("service", "queue-service")),
                orderBy(descending("timestamp"), descending("_id"))
            ),
            explainFind(
                "project + level + time",
                and(projectAndTime, eq("level", "ERROR")),
                orderBy(descending("timestamp"), descending("_id"))
            ),
            explainFind(
                "project + trace ID",
                and(projectAndTime, eq("trace_id", "trace-3")),
                orderBy(descending("timestamp"), descending("_id"))
            ),
            explainFind(
                "project + request ID",
                and(projectAndTime, eq("request_id", "request-7")),
                orderBy(descending("timestamp"), descending("_id"))
            )
        );

        evidence.forEach(this::assertIndexedPlan);
    }

    @Test
    void representativeAnalyticsUseProjectTimeIndexWithoutCollectionScans() {
        seedEvents();

        Date from = dateAt(60);
        Date to = dateAt(220);
        Document projectTime = new Document("project_id", "project-a")
            .append("timestamp", new Document("$gte", from).append("$lte", to));
        Document errorProjectTime = new Document("project_id", "project-a")
            .append("timestamp", new Document("$gte", from).append("$lte", to))
            .append("level", new Document("$in", List.of("ERROR", "WARN")));

        List<PlanEvidence> evidence = List.of(
            explainAggregate(
                "time-series aggregation",
                List.of(
                    new Document("$match", projectTime),
                    new Document("$group", new Document("_id", new Document("$dateTrunc", new Document("date", "$timestamp")
                        .append("unit", "minute")
                        .append("binSize", 15)
                        .append("timezone", "UTC")))
                        .append("total", new Document("$sum", 1))
                        .append("errorCount", new Document("$sum", new Document("$cond", List.of(
                            new Document("$eq", List.of("$level", "ERROR")), 1, 0))))
                    )
                )
            ),
            explainAggregate(
                "severity aggregation",
                List.of(
                    new Document("$match", projectTime),
                    new Document("$group", new Document("_id", "$level").append("count", new Document("$sum", 1)))
                )
            ),
            explainAggregate(
                "top service aggregation",
                List.of(
                    new Document("$match", projectTime),
                    new Document("$group", new Document("_id", "$service").append("count", new Document("$sum", 1))),
                    new Document("$sort", new Document("count", -1)),
                    new Document("$limit", 5)
                )
            ),
            explainAggregate(
                "top fingerprint aggregation",
                List.of(
                    new Document("$match", errorProjectTime),
                    new Document("$group", new Document("_id", "$error_fingerprint")
                        .append("count", new Document("$sum", 1))
                        .append("sampleMessage", new Document("$first", "$message"))),
                    new Document("$sort", new Document("count", -1)),
                    new Document("$limit", 10)
                )
            )
        );

        evidence.forEach(this::assertIndexedPlan);
    }

    @Test
    void recordsIndexedWriteSampleAgainstUnindexedBaseline() {
        MongoCollection<Document> baseline = mongoTemplate.getDb().getCollection("d2_write_baseline");
        baseline.drop();

        List<Long> baselineSamples = new ArrayList<>();
        List<Long> indexedSamples = new ArrayList<>();
        for (int round = 0; round < 6; round++) {
            List<Document> sample = writeSample(1_000, "round-" + round);
            boolean baselineFirst = round % 2 == 0;
            long baselineNanos;
            long indexedNanos;
            if (baselineFirst) {
                baselineNanos = timedInsert(baseline, sample);
                indexedNanos = timedInsert(events(), sample);
            } else {
                indexedNanos = timedInsert(events(), sample);
                baselineNanos = timedInsert(baseline, sample);
            }
            if (round > 0) {
                baselineSamples.add(baselineNanos);
                indexedSamples.add(indexedNanos);
            }
            baseline.deleteMany(new Document());
            events().deleteMany(new Document());
        }

        long totalIndexCount = events().listIndexes().into(new ArrayList<>()).size();
        long secondaryIndexCount = totalIndexCount - 1;
        assertTrue(secondaryIndexCount > 0, "The write sample must include secondary indexes");
        long baselineMedianNanos = median(baselineSamples);
        long indexedMedianNanos = median(indexedSamples);
        assertTrue(baselineMedianNanos > 0);
        assertTrue(indexedMedianNanos > 0);

        double indexedToBaseline = (double) indexedMedianNanos / baselineMedianNanos;
        System.out.printf(
            "[D2-WRITE] totalIndexes=%d secondaryIndexes=%d documents=%d baselineMedianMs=%.3f indexedMedianMs=%.3f ratio=%.3f%n",
            totalIndexCount,
            secondaryIndexCount,
            1_000,
            baselineMedianNanos / 1_000_000.0,
            indexedMedianNanos / 1_000_000.0,
            indexedToBaseline
        );

        baseline.drop();
    }

    private PlanEvidence explainFind(String label, Bson filter, Bson sort) {
        Document explain = events().find(filter)
            .sort(sort)
            .limit(100)
            .explain(ExplainVerbosity.EXECUTION_STATS);
        return evidence(label, explain);
    }

    private PlanEvidence explainAggregate(String label, List<Document> pipeline) {
        Document explain = events().aggregate(pipeline)
            .explain(ExplainVerbosity.EXECUTION_STATS);
        return evidence(label, explain);
    }

    private PlanEvidence evidence(String label, Document explain) {
        Object winningPlan = firstValue(explain, "winningPlan");
        String indexName = stringValue(firstValue(winningPlan, "indexName"));
        long documentsExamined = numberValue(firstValue(explain, "totalDocsExamined"));
        long documentsReturned = numberValue(firstValue(explain, "nReturned"));
        boolean collectionScan = containsStage(explain, "COLLSCAN");
        PlanEvidence result = new PlanEvidence(label, indexName, documentsExamined, documentsReturned, collectionScan);
        System.out.printf(
            "[D2-PLAN] query=%s index=%s docsExamined=%d docsReturned=%d collscan=%s%n",
            result.label(),
            result.indexName(),
            result.documentsExamined(),
            result.documentsReturned(),
            result.collectionScan()
        );
        return result;
    }

    private void assertIndexedPlan(PlanEvidence evidence) {
        assertFalse(evidence.collectionScan(), () -> evidence.label() + " unexpectedly used COLLSCAN");
        assertNotNull(evidence.indexName(), () -> evidence.label() + " did not report a winning index");
        assertTrue(evidence.indexName().startsWith("idx_logs_"),
            () -> evidence.label() + " selected an unexpected index: " + evidence.indexName());
        assertTrue(evidence.documentsExamined() >= 0);
        assertTrue(evidence.documentsExamined() <= SEEDED_EVENT_COUNT,
            () -> evidence.label() + " examined more documents than the seeded collection");
        assertTrue(evidence.documentsReturned() >= 0);
    }

    private void seedEvents() {
        List<Document> events = new ArrayList<>(SEEDED_EVENT_COUNT);
        String[] levels = {"INFO", "WARN", "ERROR", "DEBUG"};
        for (int i = 0; i < SEEDED_EVENT_COUNT; i++) {
            String projectId = i % 4 == 0 ? "project-b" : "project-a";
            String service = i % 3 == 0 ? "queue-service" : "worker-service";
            String environment = i % 2 == 0 ? "production" : "staging";
            String level = levels[i % levels.length];
            events.add(new Document("event_id", "d2-event-" + i)
                .append("timestamp", dateAt(i))
                .append("level", level)
                .append("service", service)
                .append("environment", environment)
                .append("event_type", "QUEUE_CREATE_FAILED")
                .append("message", "synthetic query-plan event " + i)
                .append("trace_id", "trace-" + (i % 12))
                .append("request_id", "request-" + (i % 18))
                .append("error_fingerprint", "fingerprint-" + (i % 8))
                .append("received_at", dateAt(i))
                .append("expire_at", Date.from(DATA_START.plusSeconds(86_400L * 365)))
                .append("organization_id", "org-1")
                .append("project_id", projectId)
                .append("api_key_id", "api-key-d2"));
        }
        events().insertMany(events);
    }

    private long timedInsert(MongoCollection<Document> collection, List<Document> sample) {
        long start = System.nanoTime();
        collection.insertMany(sample);
        return System.nanoTime() - start;
    }

    private long median(List<Long> values) {
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Long::compareTo);
        return sorted.get(sorted.size() / 2);
    }

    private List<Document> writeSample(int size, String round) {
        List<Document> sample = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            sample.add(new Document("event_id", "d2-write-" + round + "-" + i)
                .append("timestamp", dateAt(i))
                .append("level", "INFO")
                .append("service", "write-benchmark")
                .append("environment", "test")
                .append("project_id", "project-a")
                .append("received_at", dateAt(i))
                .append("expire_at", Date.from(DATA_START.plusSeconds(86_400L * 365))));
        }
        return sample;
    }

    private MongoCollection<Document> events() {
        return mongoTemplate.getCollection("log_events");
    }

    private Date dateAt(int minutes) {
        return Date.from(DATA_START.plusSeconds(minutes * 60L));
    }

    private Object firstValue(Object current, String key) {
        if (current instanceof Document document) {
            if (document.containsKey(key)) {
                return document.get(key);
            }
            for (Map.Entry<String, Object> entry : document.entrySet()) {
                Object found = firstValue(entry.getValue(), key);
                if (found != null) {
                    return found;
                }
            }
        } else if (current instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                Object found = firstValue(item, key);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private boolean containsStage(Object current, String stage) {
        if (current instanceof Document document) {
            Object value = document.get("stage");
            if (stage.equals(value)) {
                return true;
            }
            return document.values().stream().anyMatch(valueToInspect -> containsStage(valueToInspect, stage));
        }
        if (current instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (containsStage(item, stage)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String stringValue(Object value) {
        return value instanceof String string ? string : null;
    }

    private long numberValue(Object value) {
        return value instanceof Number number ? number.longValue() : -1L;
    }

    private record PlanEvidence(
        String label,
        String indexName,
        long documentsExamined,
        long documentsReturned,
        boolean collectionScan
    ) { }
}
