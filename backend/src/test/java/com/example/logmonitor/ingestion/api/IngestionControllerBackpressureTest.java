package com.example.logmonitor.ingestion.api;

import com.example.logmonitor.ingestion.application.IngestionService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IngestionControllerBackpressureTest {

    @Test
    void mapsQueueRejectionToExplicit503BackpressureResponse() {
        IngestionService ingestionService = mock(IngestionService.class);
        when(ingestionService.accept(any(), any()))
            .thenReturn(new IngestionService.AdmissionResult(
                false, 0, "request-1", "memory_queue", "Ingestion capacity is temporarily unavailable"
            ));
        IngestionController controller = new IngestionController(ingestionService);

        var response = controller.ingestLog(
            "lm_live_test_key",
            new IngestionRequest(null, null, "INFO", "test-service", "test", "TEST", "message", null, null, null, null, null)
        );

        assertEquals(503, response.getStatusCode().value());
        assertEquals("1", response.getHeaders().getFirst("Retry-After"));
        Map<String, Object> body = response.getBody();
        assertEquals(false, body.get("accepted"));
        assertEquals("INGESTION_BACKPRESSURE", ((Map<?, ?>) body.get("error")).get("code"));
    }
}
