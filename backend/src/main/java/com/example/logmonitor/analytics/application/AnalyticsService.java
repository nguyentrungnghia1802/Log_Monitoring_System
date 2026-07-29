package com.example.logmonitor.analytics.application;

import com.example.logmonitor.analytics.api.AnalyticsHistogramResponse;
import com.example.logmonitor.analytics.api.AnalyticsSummaryResponse;
import com.example.logmonitor.persistence.LogEventDocument;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class AnalyticsService {

    private final MongoTemplate mongoTemplate;

    public AnalyticsService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public AnalyticsSummaryResponse getSummary(
        String projectId,
        Instant startTime,
        Instant endTime,
        String environment,
        String service
    ) {
        Instant effectiveEndTime = endTime == null ? Instant.now() : endTime;
        Instant effectiveStartTime = startTime == null ? effectiveEndTime.minus(24, ChronoUnit.HOURS) : startTime;

        Criteria matchCriteria = Criteria.where("project_id").is(projectId)
            .and("timestamp").gte(effectiveStartTime).lte(effectiveEndTime);

        if (environment != null && !environment.isBlank()) {
            matchCriteria.and("environment").is(environment.trim());
        }
        if (service != null && !service.isBlank()) {
            matchCriteria.and("service").is(service.trim());
        }

        // 1. Level Breakdown Aggregation
        Aggregation levelAgg = Aggregation.newAggregation(
            Aggregation.match(matchCriteria),
            Aggregation.group("level").count().as("count")
        );
        AggregationResults<Document> levelResults = mongoTemplate.aggregate(levelAgg, LogEventDocument.class, Document.class);

        Map<String, Long> countByLevel = new HashMap<>();
        long totalLogs = 0;
        long errorCount = 0;
        long warnCount = 0;

        for (Document doc : levelResults.getMappedResults()) {
            String lvl = doc.getString("_id");
            long cnt = doc.getInteger("count", 0);
            if (lvl != null) {
                countByLevel.put(lvl, cnt);
                totalLogs += cnt;
                if ("ERROR".equalsIgnoreCase(lvl)) errorCount += cnt;
                if ("WARN".equalsIgnoreCase(lvl)) warnCount += cnt;
            }
        }

        double errorRatePercentage = totalLogs > 0
            ? Math.round(((double) (errorCount + warnCount) / totalLogs * 100.0) * 100.0) / 100.0
            : 0.0;

        // 2. Top Services Aggregation
        Aggregation serviceAgg = Aggregation.newAggregation(
            Aggregation.match(matchCriteria),
            Aggregation.group("service").count().as("count"),
            Aggregation.sort(Sort.Direction.DESC, "count"),
            Aggregation.limit(5)
        );
        AggregationResults<Document> serviceResults = mongoTemplate.aggregate(serviceAgg, LogEventDocument.class, Document.class);

        List<AnalyticsSummaryResponse.ServiceVolume> topServices = serviceResults.getMappedResults().stream()
            .map(doc -> new AnalyticsSummaryResponse.ServiceVolume(
                doc.getString("_id") != null ? doc.getString("_id") : "unknown",
                doc.getInteger("count", 0)
            ))
            .toList();

        // 3. Top Error Fingerprints Aggregation
        Criteria errorMatchCriteria = Criteria.where("project_id").is(projectId)
            .and("timestamp").gte(effectiveStartTime).lte(effectiveEndTime)
            .and("level").in("ERROR", "WARN");
        if (environment != null && !environment.isBlank()) {
            errorMatchCriteria.and("environment").is(environment.trim());
        }
        if (service != null && !service.isBlank()) {
            errorMatchCriteria.and("service").is(service.trim());
        }

        Aggregation errorAgg = Aggregation.newAggregation(
            Aggregation.match(errorMatchCriteria),
            Aggregation.group("error_fingerprint")
                .count().as("count")
                .first("message").as("sampleMessage"),
            Aggregation.sort(Sort.Direction.DESC, "count"),
            Aggregation.limit(10)
        );
        AggregationResults<Document> errorResults = mongoTemplate.aggregate(errorAgg, LogEventDocument.class, Document.class);

        List<AnalyticsSummaryResponse.ErrorFingerprintCount> topErrors = errorResults.getMappedResults().stream()
            .map(doc -> new AnalyticsSummaryResponse.ErrorFingerprintCount(
                doc.getString("_id") != null ? doc.getString("_id") : "UNKNOWN_ERROR",
                doc.getString("sampleMessage") != null ? doc.getString("sampleMessage") : "",
                doc.getInteger("count", 0)
            ))
            .toList();

        return new AnalyticsSummaryResponse(totalLogs, errorRatePercentage, countByLevel, topServices, topErrors);
    }

    public AnalyticsHistogramResponse getHistogram(
        String projectId,
        Instant startTime,
        Instant endTime,
        String interval,
        String environment,
        String service
    ) {
        Instant effectiveEndTime = endTime == null ? Instant.now() : endTime;
        Instant effectiveStartTime = startTime == null ? effectiveEndTime.minus(24, ChronoUnit.HOURS) : startTime;
        String effectiveInterval = (interval == null || interval.isBlank()) ? "1h" : interval;

        Criteria matchCriteria = Criteria.where("project_id").is(projectId)
            .and("timestamp").gte(effectiveStartTime).lte(effectiveEndTime);

        if (environment != null && !environment.isBlank()) {
            matchCriteria.and("environment").is(environment.trim());
        }
        if (service != null && !service.isBlank()) {
            matchCriteria.and("service").is(service.trim());
        }

        Aggregation aggregation = Aggregation.newAggregation(
            Aggregation.match(matchCriteria),
            Aggregation.project("level", "timestamp")
        );

        AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, LogEventDocument.class, Document.class);

        long bucketSizeMillis = parseIntervalToMillis(effectiveInterval);
        Map<Long, BucketCounts> bucketMap = new TreeMap<>();

        for (Document doc : results.getMappedResults()) {
            Instant ts = parseInstant(doc.get("timestamp"));
            if (ts == null) continue;

            long bucketKey = (ts.toEpochMilli() / bucketSizeMillis) * bucketSizeMillis;
            String lvl = doc.getString("level");

            BucketCounts counts = bucketMap.computeIfAbsent(bucketKey, k -> new BucketCounts());
            counts.total++;
            if ("ERROR".equalsIgnoreCase(lvl)) counts.error++;
            else if ("WARN".equalsIgnoreCase(lvl)) counts.warn++;
            else if ("INFO".equalsIgnoreCase(lvl)) counts.info++;
            else if ("DEBUG".equalsIgnoreCase(lvl)) counts.debug++;
        }

        List<AnalyticsHistogramResponse.HistogramBucket> buckets = new ArrayList<>();
        for (Map.Entry<Long, BucketCounts> entry : bucketMap.entrySet()) {
            BucketCounts c = entry.getValue();
            buckets.add(new AnalyticsHistogramResponse.HistogramBucket(
                Instant.ofEpochMilli(entry.getKey()),
                c.total,
                c.error,
                c.warn,
                c.info,
                c.debug
            ));
        }

        return new AnalyticsHistogramResponse(effectiveInterval, buckets);
    }

    private long parseIntervalToMillis(String interval) {
        return switch (interval.toLowerCase()) {
            case "1m", "1min" -> 60_000L;
            case "5m", "5min" -> 300_000L;
            case "15m", "15min" -> 900_000L;
            case "1h", "1hour" -> 3_600_000L;
            case "1d", "1day" -> 86_400_000L;
            default -> 3_600_000L;
        };
    }

    private Instant parseInstant(Object val) {
        if (val instanceof Instant inst) return inst;
        if (val instanceof java.util.Date date) return date.toInstant();
        if (val instanceof String str) {
            try { return Instant.parse(str); } catch (Exception ignored) {}
        }
        return null;
    }

    private static class BucketCounts {
        long total = 0;
        long error = 0;
        long warn = 0;
        long info = 0;
        long debug = 0;
    }
}
