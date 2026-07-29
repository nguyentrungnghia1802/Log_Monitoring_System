package com.example.logmonitor.logquery.api;

import com.example.logmonitor.logquery.application.LogQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/logs")
public class LogQueryController {

    private final LogQueryService logQueryService;

    public LogQueryController(LogQueryService logQueryService) {
        this.logQueryService = logQueryService;
    }

    @GetMapping
    public ResponseEntity<LogSearchResponse> searchLogs(
        @PathVariable("projectId") String projectId,
        @RequestParam(value = "startTime", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
        @RequestParam(value = "endTime", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime,
        @RequestParam(value = "level", required = false) String level,
        @RequestParam(value = "service", required = false) String service,
        @RequestParam(value = "environment", required = false) String environment,
        @RequestParam(value = "eventType", required = false) String eventType,
        @RequestParam(value = "traceId", required = false) String traceId,
        @RequestParam(value = "requestId", required = false) String requestId,
        @RequestParam(value = "errorFingerprint", required = false) String errorFingerprint,
        @RequestParam(value = "search", required = false) String search,
        @RequestParam(value = "cursor", required = false) String cursor,
        @RequestParam(value = "limit", defaultValue = "50") int limit
    ) {
        LogSearchResponse response = logQueryService.searchLogs(
            projectId, startTime, endTime, level, service, environment,
            eventType, traceId, requestId, errorFingerprint, search, cursor, limit
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<LogSearchResponse.LogEventResponse> getLogById(
        @PathVariable("projectId") String projectId,
        @PathVariable("id") String id
    ) {
        return logQueryService.getLogById(projectId, id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
