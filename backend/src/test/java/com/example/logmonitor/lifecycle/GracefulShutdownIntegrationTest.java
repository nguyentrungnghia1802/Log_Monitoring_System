package com.example.logmonitor.lifecycle;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import com.mongodb.client.MongoClient;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
@Testcontainers
class GracefulShutdownIntegrationTest {

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
    private MockMvc mockMvc;

    @Autowired
    private GracefulShutdownCoordinator shutdownCoordinator;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void withdrawsReadinessAndStopsIngestionAdmissionBeforeContextDrain() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));

        shutdownCoordinator.beginShutdown();

        mockMvc.perform(get("/actuator/health/readiness"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.status").value("OUT_OF_SERVICE"));

        mockMvc.perform(post("/api/v1/ingest/logs")
                .header("X-API-Key", "lm_live_test_key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "level": "ERROR",
                      "service": "shutdown-test",
                      "environment": "test",
                      "eventType": "SHUTDOWN",
                      "message": "must not be queued"
                    }
                    """))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.accepted").value(false))
            .andExpect(jsonPath("$.error.code").value("INGESTION_SHUTTING_DOWN"));

        ConfigurableApplicationContext configurableContext = (ConfigurableApplicationContext) applicationContext;
        String mongoLifecycle = java.util.Arrays.stream(configurableContext.getBeanFactory().getBeanNamesForType(MongoClient.class))
            .map(beanName -> beanName + ":" + configurableContext.getBeanFactory().getBeanDefinition(beanName).getDestroyMethodName())
            .collect(java.util.stream.Collectors.joining(","));
        assertTrue(
            java.util.Arrays.stream(configurableContext.getBeanFactory().getBeanNamesForType(MongoClient.class))
                .anyMatch(beanName -> {
                    String destroyMethod = configurableContext.getBeanFactory()
                        .getBeanDefinition(beanName)
                        .getDestroyMethodName();
                    return "close".equals(destroyMethod) || "(inferred)".equals(destroyMethod);
                }),
            "Spring must manage MongoClient destruction: " + mongoLifecycle
        );
    }
}
