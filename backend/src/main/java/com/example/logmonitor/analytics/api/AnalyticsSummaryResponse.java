package com.example.logmonitor.analytics.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AnalyticsSummaryResponse(
    long totalLogs,
    double errorRatePercentage,
    Map<String, Long> countByLevel,
    List<ServiceVolume> topServices,
    List<ErrorFingerprintCount> topErrors
) {
    public record ServiceVolume(String service, long count) {}
    public record ErrorFingerprintCount(String errorFingerprint, String sampleMessage, long count) {}
}
