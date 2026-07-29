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
class BatchIngestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Value("${ingestion.api-key:demo-api-key}")
    private String apiKey;

    @Test
    void acceptsBatchOfLogEventsWithApiKey() throws Exception {
        mockMvc.perform(post("/api/v1/ingest/logs/batch")
                .header("X-API-Key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "events": [
                            {
                              "timestamp": "2026-07-30T10:15:12.123Z",
                              "level": "ERROR",
                              "service": "queue-service",
                              "environment": "production",
                              "eventType": "QUEUE_CREATE_FAILED",
                              "message": "Failed to create queue"
                            },
                            {
                              "timestamp": "2026-07-30T10:15:13.123Z",
                              "level": "WARN",
                              "service": "queue-service",
                              "environment": "production",
                              "eventType": "QUEUE_RETRY",
                              "message": "Retrying queue operation"
                            }
                          ]
                        }
                        """))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.accepted").value(true))
            .andExpect(jsonPath("$.acceptedCount").value(2));
    }
}
