package com.example.logmonitor.analytics.api;

import com.example.logmonitor.analytics.application.AnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/summary")
    public ResponseEntity<AnalyticsSummaryResponse> getSummary(
        @PathVariable("projectId") String projectId,
        @RequestParam(value = "startTime", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
        @RequestParam(value = "endTime", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime,
        @RequestParam(value = "environment", required = false) String environment,
        @RequestParam(value = "service", required = false) String service
    ) {
        AnalyticsSummaryResponse summary = analyticsService.getSummary(projectId, startTime, endTime, environment, service);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/histogram")
    public ResponseEntity<AnalyticsHistogramResponse> getHistogram(
        @PathVariable("projectId") String projectId,
        @RequestParam(value = "startTime", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
        @RequestParam(value = "endTime", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime,
        @RequestParam(value = "interval", defaultValue = "1h") String interval,
        @RequestParam(value = "environment", required = false) String environment,
        @RequestParam(value = "service", required = false) String service
    ) {
        AnalyticsHistogramResponse histogram = analyticsService.getHistogram(projectId, startTime, endTime, interval, environment, service);
        return ResponseEntity.ok(histogram);
    }
}
