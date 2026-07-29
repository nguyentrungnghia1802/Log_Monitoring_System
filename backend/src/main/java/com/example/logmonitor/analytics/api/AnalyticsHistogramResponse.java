package com.example.logmonitor.analytics.api;

import java.time.Instant;
import java.util.List;

public record AnalyticsHistogramResponse(
    String interval,
    List<HistogramBucket> buckets
) {
    public record HistogramBucket(
        Instant timestamp,
        long total,
        long errorCount,
        long warnCount,
        long infoCount,
        long debugCount
    ) {}
}
