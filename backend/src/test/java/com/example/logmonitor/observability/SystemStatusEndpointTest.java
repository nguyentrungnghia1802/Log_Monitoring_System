package com.example.logmonitor.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc(addFilters = false)
class SystemStatusEndpointTest {

    private static MongoDBContainer mongoDBContainer;

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));
            mongoDBContainer.start();
            registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
        } else {
            registry.add("spring.data.mongodb.uri", () -> "mongodb://localhost:27017/log_monitor_test");
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Test
    void ingestionStatusEndpointShouldExposeQueueMetrics() throws Exception {
        Assumptions.assumeTrue(
            DockerClientFactory.instance().isDockerAvailable(),
            "Docker is required to run Testcontainers-based MongoDB integration tests"
        );

        mockMvc.perform(get("/api/v1/system/ingestion-status"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("queueCapacity")));
    }

    @Test
    void readinessIncludesMongoHealthWhenMongoIsAvailable() throws Exception {
        Assumptions.assumeTrue(
            DockerClientFactory.instance().isDockerAvailable(),
            "Docker is required to run Testcontainers-based MongoDB integration tests"
        );

        mockMvc.perform(get("/actuator/health/readiness"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.components.mongo.status").value("UP"));
    }

    @Test
    void platformMetersExposeHttpJvmAndProcessSignalsWithoutIdentifiers() throws Exception {
        Assumptions.assumeTrue(
            DockerClientFactory.instance().isDockerAvailable(),
            "Docker is required to run Testcontainers-based MongoDB integration tests"
        );

        var response = testRestTemplate.getForEntity("/actuator/health/readiness", String.class);
        assertEquals(200, response.getStatusCode().value());

        var httpTimers = meterRegistry.find("http.server.requests").timers();
        org.junit.jupiter.api.Assertions.assertFalse(httpTimers.isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(
            httpTimers.stream().flatMap(timer -> timer.getId().getTags().stream())
                .map(io.micrometer.core.instrument.Tag::getKey)
                .noneMatch(key -> key.toLowerCase().contains("id") || key.toLowerCase().contains("message"))
        );
        org.junit.jupiter.api.Assertions.assertNotNull(meterRegistry.find("jvm.memory.used").gauge());
        org.junit.jupiter.api.Assertions.assertNotNull(meterRegistry.find("process.cpu.usage").gauge());
    }

    @Test
    void healthDashboardRequiresAnOrganizationAdministrator() throws Exception {
        mockMvc.perform(get("/api/v1/system/health-dashboard"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }
}
