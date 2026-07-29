package com.example.logmonitor.observability;

import com.example.logmonitor.ingestion.infrastructure.IngestionQueue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/system")
public class SystemStatusController {

    private final IngestionQueue ingestionQueue;
    private final int queueCapacity;
    private final int workerCount;
    private final int batchMaxSize;

    public SystemStatusController(
        IngestionQueue ingestionQueue,
        @Value("${ingestion.queue.capacity:50000}") int queueCapacity,
        @Value("${ingestion.workers:4}") int workerCount,
        @Value("${ingestion.batch.max-size:500}") int batchMaxSize
    ) {
        this.ingestionQueue = ingestionQueue;
        this.queueCapacity = queueCapacity;
        this.workerCount = workerCount;
        this.batchMaxSize = batchMaxSize;
    }

    @GetMapping("/ingestion-status")
    public Map<String, Object> ingestionStatus() {
        return Map.of(
            "queueDepth", ingestionQueue.size(),
            "queueCapacity", queueCapacity,
            "workerCount", workerCount,
            "batchMaxSize", batchMaxSize,
            "acceptedCount", ingestionQueue.acceptedCount(),
            "rejectedCount", ingestionQueue.rejectedCount(),
            "status", "ready"
        );
    }
}
