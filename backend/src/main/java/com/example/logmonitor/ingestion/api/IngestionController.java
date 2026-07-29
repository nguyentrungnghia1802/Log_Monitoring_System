package com.example.logmonitor.ingestion.api;

import com.example.logmonitor.ingestion.application.IngestionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ingest")
public class IngestionController {

    private final IngestionService ingestionService;

    public IngestionController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/logs")
    public ResponseEntity<Map<String, Object>> ingestLog(
        @RequestHeader(value = "X-API-Key", required = false) String apiKey,
        @Valid @RequestBody IngestionRequest request
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "accepted", false,
                "error", Map.of("code", "UNAUTHORIZED", "message", "Missing X-API-Key")
            ));
        }

        var result = ingestionService.accept(request, apiKey);
        if (result.accepted()) {
            return ResponseEntity.accepted().body(Map.of(
                "accepted", true,
                "acceptedCount", result.acceptedCount(),
                "requestId", result.requestId(),
                "admission", result.admission()
            ));
        }

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
            "accepted", false,
            "error", Map.of("code", "INGESTION_BACKPRESSURE", "message", result.message())
        ));
    }

    @PostMapping("/logs/batch")
    public ResponseEntity<Map<String, Object>> ingestBatch(
        @RequestHeader(value = "X-API-Key", required = false) String apiKey,
        @Valid @RequestBody BatchIngestionRequest request
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "accepted", false,
                "error", Map.of("code", "UNAUTHORIZED", "message", "Missing X-API-Key")
            ));
        }

        var result = ingestionService.acceptBatch(request, apiKey);
        if (result.accepted()) {
            return ResponseEntity.accepted().body(Map.of(
                "accepted", true,
                "acceptedCount", result.acceptedCount(),
                "requestId", result.requestId(),
                "admission", result.admission()
            ));
        }

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
            "accepted", false,
            "error", Map.of("code", "INGESTION_BACKPRESSURE", "message", result.message())
        ));
    }
}
