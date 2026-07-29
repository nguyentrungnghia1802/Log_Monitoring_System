package com.example.logmonitor.ingestion.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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

    @Value("${ingestion.api-key:demo-api-key}")
    private String apiKey;

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
}
