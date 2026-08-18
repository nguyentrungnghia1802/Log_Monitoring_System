package com.example.logmonitor.observability;

import io.micrometer.core.instrument.MeterRegistry;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class MongoCommandMetricsIntegrationTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> {
            String uri = MONGO.getReplicaSetUrl();
            return uri + (uri.contains("?") ? "&" : "?")
                + "serverSelectionTimeoutMS=500&connectTimeoutMS=500";
        });
    }

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void recordsLowCardinalityCommandDurationMetrics() {
        mongoTemplate.getCollection("metrics_probe").countDocuments();

        var timers = meterRegistry.find("mongodb.command.duration").timers();
        assertFalse(timers.isEmpty(), "Mongo command duration timer must be registered");
        assertTrue(
            timers.stream().allMatch(timer -> timer.getId().getTags().stream()
                .allMatch(tag -> !"database".equals(tag.getKey()) && !"collection".equals(tag.getKey()))),
            "Mongo metrics must not include database or collection cardinality"
        );
        assertTrue(
            timers.stream().anyMatch(timer -> timer.count() > 0),
            "At least one Mongo command must have been timed"
        );
    }
}
