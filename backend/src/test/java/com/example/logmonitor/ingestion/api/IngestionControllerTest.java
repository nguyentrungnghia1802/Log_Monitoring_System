package com.example.logmonitor.ingestion.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class IngestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.example.logmonitor.apikey.application.ApiKeyService apiKeyService;

    private String apiKey;

    @BeforeEach
    void setUpApiKey() {
        apiKey = apiKeyService.createApiKey("demo-project", "test-ingestion").rawApiKey();
    }

    @Test
    void acceptsSingleLogEventWithApiKey() throws Exception {
        mockMvc.perform(post("/api/v1/ingest/logs")
                .header("X-API-Key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "timestamp": "2026-07-30T10:15:12.123Z",
                          "level": "ERROR",
                          "service": "queue-service",
                          "environment": "production",
                          "eventType": "QUEUE_CREATE_FAILED",
                          "message": "Failed to create queue"
                        }
                        """))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.accepted").value(true))
            .andExpect(jsonPath("$.acceptedCount").value(1));
    }

    @Test
    void rejectsMessageThatExceedsTheConfiguredValidationLimit() throws Exception {
        String oversizedMessage = "x".repeat(4001);

        mockMvc.perform(post("/api/v1/ingest/logs")
                .header("X-API-Key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "level": "ERROR",
                      "service": "queue-service",
                      "environment": "production",
                      "eventType": "OVERSIZED",
                      "message": "%s"
                    }
                    """.formatted(oversizedMessage)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.accepted").value(false))
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsContextThatAttemptsToOverrideReservedEventFields() throws Exception {
        mockMvc.perform(post("/api/v1/ingest/logs")
                .header("X-API-Key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "level": "ERROR",
                      "service": "queue-service",
                      "environment": "production",
                      "eventType": "RESERVED_FIELD",
                      "message": "should not be admitted",
                      "context": {"projectId": "foreign-project"}
                    }
                    """))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.accepted").value(false))
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.message").value("context contains a reserved event field"));
    }
}
