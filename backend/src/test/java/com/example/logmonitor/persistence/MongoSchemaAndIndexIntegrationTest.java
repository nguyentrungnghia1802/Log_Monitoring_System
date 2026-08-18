package com.example.logmonitor.persistence;

import com.example.logmonitor.ingestion.api.IngestionRequest;
import com.example.logmonitor.ingestion.domain.LogEvent;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.mongodb.client.model.Filters.eq;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class MongoSchemaAndIndexIntegrationTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
    }

    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private LogEventPersistenceService persistenceService;

    @BeforeEach
    void clearEventsWithoutDroppingIndexes() {
        mongoTemplate.remove(new Query(), LogEventDocument.class);
    }

    @Test
    void persistsExactServerControlledLogEventSchema() {
        Instant producerTime = Instant.now().plusSeconds(86_400);
        IngestionRequest request = new IngestionRequest(
            "event-1", producerTime, "ERROR", "queue-service", "production", "QUEUE_CREATE_FAILED",
            "Failed to create queue", "trace-1", "request-1",
            new IngestionRequest.ExceptionRequest("MongoTimeoutException", "Timed out", "at QueueService.create"),
            Map.of("branchId", "BR001"), Map.of("version", "1.0.0")
        );
        LogEvent event = LogEvent.of(request, "org-1", "project-1", "api-key-1", 3600);

        persistenceService.persist(List.of(event));

        Document stored = mongoTemplate.getCollection("log_events").find(eq("event_id", "event-1")).first();
        assertNotNull(stored);
        assertEquals(producerTime.toEpochMilli(), stored.getDate("timestamp").toInstant().toEpochMilli());
        assertEquals(event.receivedAt().toEpochMilli(), stored.getDate("received_at").toInstant().toEpochMilli());
        assertNotEquals(stored.getDate("timestamp"), stored.getDate("received_at"));
        assertEquals(3600, stored.getDate("expire_at").toInstant().getEpochSecond()
            - stored.getDate("received_at").toInstant().getEpochSecond());
        assertEquals("org-1", stored.getString("organization_id"));
        assertEquals("project-1", stored.getString("project_id"));
        assertEquals("api-key-1", stored.getString("api_key_id"));
        assertEquals("QUEUE_CREATE_FAILED::Failed to create queue", stored.getString("error_fingerprint"));
        assertEquals("MongoTimeoutException", stored.get("exception", Document.class).getString("type"));
        assertEquals("BR001", stored.get("context", Document.class).getString("branchId"));
        assertEquals("1.0.0", stored.get("tags", Document.class).getString("version"));
    }

    @Test
    void storesRepeatedClientEventIdsAsSeparateServerDocuments() {
        Instant producerTime = Instant.now();
        IngestionRequest request = new IngestionRequest(
            "retry-event-1", producerTime, "WARN", "queue-service", "production", "QUEUE_RETRY",
            "Retryable queue operation", null, null, null, Map.of(), Map.of()
        );
        LogEvent first = LogEvent.of(request, "org-1", "project-1", "api-key-1", 3600);
        LogEvent second = LogEvent.of(request, "org-1", "project-1", "api-key-1", 3600);

        persistenceService.persist(List.of(first, second));

        List<Document> stored = mongoTemplate.getCollection("log_events")
            .find(eq("event_id", "retry-event-1"))
            .into(new ArrayList<>());
        assertEquals(2, stored.size());
        assertNotEquals(stored.get(0).get("_id"), stored.get(1).get("_id"));
        assertEquals("project-1", stored.get(0).getString("project_id"));
        assertEquals("project-1", stored.get(1).getString("project_id"));
    }

    @Test
    void initializesTtlCriticalCompoundAndConfigurationUniqueIndexes() {
        Map<String, Document> logIndexes = indexes("log_events");
        assertIndex(logIndexes, "ttl_expire", new Document("expire_at", 1));
        assertEquals(0L, ((Number) logIndexes.get("ttl_expire").get("expireAfterSeconds")).longValue());
        assertIndex(logIndexes, "idx_logs_proj_time", new Document("project_id", 1).append("timestamp", -1).append("_id", -1));
        assertIndex(logIndexes, "idx_logs_proj_environment_time", new Document("project_id", 1).append("environment", 1).append("timestamp", -1).append("_id", -1));
        assertIndex(logIndexes, "idx_logs_proj_service_time", new Document("project_id", 1).append("service", 1).append("timestamp", -1).append("_id", -1));
        assertIndex(logIndexes, "idx_logs_proj_level_time", new Document("project_id", 1).append("level", 1).append("timestamp", -1).append("_id", -1));
        assertIndex(logIndexes, "idx_logs_proj_trace", new Document("project_id", 1).append("trace_id", 1).append("timestamp", -1));
        assertIndex(logIndexes, "idx_logs_proj_request", new Document("project_id", 1).append("request_id", 1).append("timestamp", -1));
        assertIndex(indexes("alert_rules"), "idx_alert_rules_project_enabled", new Document("project_id", 1).append("enabled", 1));
        assertIndex(indexes("alert_occurrences"), "idx_alert_occurrences_project_triggered", new Document("project_id", 1).append("triggered_at", -1));

        assertUniqueIndex("organizations", new Document("slug", 1));
        assertUniqueIndex("users", new Document("username", 1));
        assertUniqueIndex("users", new Document("email", 1));
        assertUniqueIndex("projects", new Document("organizationId", 1).append("key", 1));
        assertUniqueIndex("project_memberships", new Document("userId", 1).append("projectId", 1));
        assertUniqueIndex("api_keys", new Document("publicId", 1));
        assertUniqueIndex("auth_sessions", new Document("refreshTokenHash", 1));
    }

    private Map<String, Document> indexes(String collectionName) {
        MongoCollection<Document> collection = mongoTemplate.getCollection(collectionName);
        List<Document> documents = collection.listIndexes().into(new ArrayList<>());
        return documents.stream().collect(java.util.stream.Collectors.toMap(
            index -> index.getString("name"), index -> index));
    }

    private void assertIndex(Map<String, Document> indexes, String name, Document expectedKeys) {
        assertTrue(indexes.containsKey(name), () -> "Missing index " + name + " from " + indexes.keySet());
        assertEquals(expectedKeys, indexes.get(name).get("key", Document.class));
    }

    private void assertUniqueIndex(String collectionName, Document expectedKeys) {
        Document index = indexes(collectionName).values().stream()
            .filter(candidate -> expectedKeys.equals(candidate.get("key", Document.class)))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Missing unique index " + expectedKeys + " on " + collectionName));
        assertEquals(Boolean.TRUE, index.getBoolean("unique"));
    }
}
