package com.example.logmonitor.analytics.application;

import com.example.logmonitor.analytics.api.AnalyticsHistogramResponse;
import com.example.logmonitor.analytics.api.AnalyticsSummaryResponse;
import com.example.logmonitor.analytics.config.AnalyticsProperties;
import com.example.logmonitor.persistence.LogEventDocument;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnalyticsService {

    private static final int HARD_MAX_RANGE_HOURS = 24 * 31;
    private static final int HARD_MAX_BUCKETS = 2_000;
    private static final int HARD_MAX_TOP_N = 20;
    private static final int MAX_FILTER_LENGTH = 256;

    private final MongoTemplate mongoTemplate;
    private final AnalyticsProperties properties;

    public AnalyticsService(MongoTemplate mongoTemplate, AnalyticsProperties properties) {
        this.mongoTemplate = mongoTemplate;
        this.properties = properties;
    }

    public AnalyticsSummaryResponse getSummary(
        String projectId,
        Instant startTime,
        Instant endTime,
        String environment,
        String service
    ) {
        TimeRange range = resolveRange(projectId, startTime, endTime);

        Criteria matchCriteria = matchCriteria(range, environment, service);

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
            String lvl = textValue(doc.get("_id"), "UNKNOWN");
            long cnt = numberValue(doc.get("count"));
            countByLevel.put(lvl, cnt);
            totalLogs += cnt;
            if ("ERROR".equalsIgnoreCase(lvl)) errorCount += cnt;
            if ("WARN".equalsIgnoreCase(lvl)) warnCount += cnt;
        }

        double errorRatePercentage = totalLogs > 0
            ? Math.round(((double) (errorCount + warnCount) / totalLogs * 100.0) * 100.0) / 100.0
            : 0.0;

        // 2. Top Services Aggregation
        Aggregation serviceAgg = Aggregation.newAggregation(
            Aggregation.match(matchCriteria),
            Aggregation.group("service").count().as("count"),
            Aggregation.sort(Sort.Direction.DESC, "count"),
            Aggregation.limit(resolveTopLimit(properties.getTopServicesLimit()))
        );
        AggregationResults<Document> serviceResults = mongoTemplate.aggregate(serviceAgg, LogEventDocument.class, Document.class);

        List<AnalyticsSummaryResponse.ServiceVolume> topServices = serviceResults.getMappedResults().stream()
            .map(doc -> new AnalyticsSummaryResponse.ServiceVolume(
                textValue(doc.get("_id"), "unknown"),
                numberValue(doc.get("count"))
            ))
            .toList();

        // 3. Top Error Fingerprints Aggregation
        Criteria errorMatchCriteria = matchCriteria(range, environment, service)
            .and("level").in("ERROR", "WARN");

        Aggregation errorAgg = Aggregation.newAggregation(
            Aggregation.match(errorMatchCriteria),
            fingerprintGroup(),
            Aggregation.sort(Sort.Direction.DESC, "count"),
            Aggregation.limit(resolveTopLimit(properties.getTopErrorsLimit()))
        );
        AggregationResults<Document> errorResults = mongoTemplate.aggregate(errorAgg, LogEventDocument.class, Document.class);

        List<AnalyticsSummaryResponse.ErrorFingerprintCount> topErrors = errorResults.getMappedResults().stream()
            .map(doc -> new AnalyticsSummaryResponse.ErrorFingerprintCount(
                textValue(doc.get("_id"), "UNKNOWN_ERROR"),
                textValue(doc.get("sampleMessage"), ""),
                numberValue(doc.get("count"))
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
        TimeRange range = resolveRange(projectId, startTime, endTime);
        String effectiveInterval = resolveInterval(interval, range);
        IntervalDefinition intervalDefinition = intervalDefinition(effectiveInterval);

        Criteria matchCriteria = matchCriteria(range, environment, service);

        Aggregation aggregation = Aggregation.newAggregation(
            Aggregation.match(matchCriteria),
            histogramGroup(intervalDefinition),
            sortBuckets()
        );

        AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, LogEventDocument.class, Document.class);

        List<AnalyticsHistogramResponse.HistogramBucket> buckets = results.getMappedResults().stream()
            .map(doc -> {
                Instant bucket = parseInstant(doc.get("_id"));
                if (bucket == null) {
                    return null;
                }
                return new AnalyticsHistogramResponse.HistogramBucket(
                    bucket,
                    numberValue(doc.get("total")),
                    numberValue(doc.get("errorCount")),
                    numberValue(doc.get("warnCount")),
                    numberValue(doc.get("infoCount")),
                    numberValue(doc.get("debugCount"))
                );
            })
            .filter(java.util.Objects::nonNull)
            .toList();

        return new AnalyticsHistogramResponse(effectiveInterval, buckets);
    }

    private Criteria matchCriteria(TimeRange range, String environment, String service) {
        Criteria criteria = Criteria.where("project_id").is(range.projectId())
            .and("timestamp").gte(range.startTime()).lte(range.endTime());
        String normalizedEnvironment = normalizeFilter(environment, "environment");
        String normalizedService = normalizeFilter(service, "service");
        if (normalizedEnvironment != null) {
            criteria.and("environment").is(normalizedEnvironment);
        }
        if (normalizedService != null) {
            criteria.and("service").is(normalizedService);
        }
        return criteria;
    }

    private AggregationOperation fingerprintGroup() {
        return context -> new Document("$group", new Document("_id",
            new Document("$ifNull", List.of("$error_fingerprint", "UNKNOWN_ERROR")))
            .append("count", new Document("$sum", 1))
            .append("sampleMessage", new Document("$first", "$message")));
    }

    private AggregationOperation histogramGroup(IntervalDefinition interval) {
        Document dateTrunc = new Document("$dateTrunc", new Document("date", "$timestamp")
            .append("unit", interval.unit())
            .append("binSize", interval.binSize())
            .append("timezone", "UTC"));
        return context -> new Document("$group", new Document("_id", dateTrunc)
            .append("total", new Document("$sum", 1))
            .append("errorCount", conditionalCount("ERROR"))
            .append("warnCount", conditionalCount("WARN"))
            .append("infoCount", conditionalCount("INFO"))
            .append("debugCount", conditionalCount("DEBUG")));
    }

    private Document conditionalCount(String level) {
        return new Document("$sum", new Document("$cond", List.of(
            new Document("$eq", List.of("$level", level)),
            1,
            0
        )));
    }

    private AggregationOperation sortBuckets() {
        return context -> new Document("$sort", new Document("_id", 1));
    }

    private TimeRange resolveRange(String projectId, Instant startTime, Instant endTime) {
        String normalizedProjectId = normalizeFilter(projectId, "projectId");
        Instant effectiveEndTime = endTime == null ? Instant.now() : endTime;
        Instant effectiveStartTime = startTime == null
            ? effectiveEndTime.minus(resolveDefaultRangeHours(), ChronoUnit.HOURS)
            : startTime;
        if (effectiveStartTime.isAfter(effectiveEndTime)) {
            throw new AnalyticsQueryException(
                "INVALID_ANALYTICS_RANGE",
                "startTime must be before or equal to endTime",
                AnalyticsQueryException.Kind.UNPROCESSABLE_ENTITY
            );
        }
        long maxRangeHours = resolveMaxRangeHours();
        if (Duration.between(effectiveStartTime, effectiveEndTime).compareTo(Duration.ofHours(maxRangeHours)) > 0) {
            throw new AnalyticsQueryException(
                "ANALYTICS_RANGE_TOO_LARGE",
                "The requested analytics range must not exceed " + maxRangeHours + " hours",
                AnalyticsQueryException.Kind.UNPROCESSABLE_ENTITY
            );
        }
        return new TimeRange(normalizedProjectId, effectiveStartTime, effectiveEndTime);
    }

    private String resolveInterval(String requestedInterval, TimeRange range) {
        String effectiveInterval = requestedInterval == null || requestedInterval.isBlank()
            ? autoInterval(range)
            : canonicalInterval(requestedInterval);
        IntervalDefinition definition = intervalDefinition(effectiveInterval);
        long maxBuckets = Math.max(1, Math.min(properties.getMaxBuckets(), HARD_MAX_BUCKETS));
        long durationMillis = Math.max(1, Duration.between(range.startTime(), range.endTime()).toMillis());
        long bucketCount = (durationMillis + definition.millis() - 1) / definition.millis();
        if (bucketCount > maxBuckets) {
            throw new AnalyticsQueryException(
                "ANALYTICS_BUCKET_TOO_FINE",
                "The selected interval would produce more than " + maxBuckets + " buckets",
                AnalyticsQueryException.Kind.UNPROCESSABLE_ENTITY
            );
        }
        return effectiveInterval;
    }

    private String autoInterval(TimeRange range) {
        Duration duration = Duration.between(range.startTime(), range.endTime());
        if (duration.compareTo(Duration.ofHours(2)) <= 0) {
            return "5m";
        }
        if (duration.compareTo(Duration.ofHours(24)) <= 0) {
            return "1h";
        }
        return "1d";
    }

    private String canonicalInterval(String interval) {
        return switch (interval.trim().toLowerCase()) {
            case "1m", "1min" -> "1m";
            case "5m", "5min" -> "5m";
            case "15m", "15min" -> "15m";
            case "1h", "1hour" -> "1h";
            case "1d", "1day" -> "1d";
            default -> throw new AnalyticsQueryException(
                "INVALID_ANALYTICS_INTERVAL",
                "interval must be one of 1m, 5m, 15m, 1h, or 1d",
                AnalyticsQueryException.Kind.UNPROCESSABLE_ENTITY
            );
        };
    }

    private IntervalDefinition intervalDefinition(String interval) {
        return switch (interval) {
            case "1m" -> new IntervalDefinition("minute", 1, 60_000L);
            case "5m" -> new IntervalDefinition("minute", 5, 300_000L);
            case "15m" -> new IntervalDefinition("minute", 15, 900_000L);
            case "1h" -> new IntervalDefinition("hour", 1, 3_600_000L);
            case "1d" -> new IntervalDefinition("day", 1, 86_400_000L);
            default -> throw new IllegalArgumentException("Unknown canonical analytics interval: " + interval);
        };
    }

    private String normalizeFilter(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            if ("projectId".equals(fieldName)) {
                throw new AnalyticsQueryException(
                    "INVALID_PROJECT_ID",
                    "projectId is required",
                    AnalyticsQueryException.Kind.UNPROCESSABLE_ENTITY
                );
            }
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > MAX_FILTER_LENGTH) {
            throw new AnalyticsQueryException(
                "ANALYTICS_FILTER_TOO_LONG",
                fieldName + " must not exceed " + MAX_FILTER_LENGTH + " characters",
                AnalyticsQueryException.Kind.UNPROCESSABLE_ENTITY
            );
        }
        return normalized;
    }

    private int resolveDefaultRangeHours() {
        return clamp(properties.getDefaultRangeHours(), 1, resolveMaxRangeHours());
    }

    private int resolveMaxRangeHours() {
        return clamp(properties.getMaxRangeHours(), 1, HARD_MAX_RANGE_HOURS);
    }

    private int resolveTopLimit(int configured) {
        return clamp(configured, 1, HARD_MAX_TOP_N);
    }

    private int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

    private String textValue(Object value, String fallback) {
        if (value == null || value.toString().isBlank()) {
            return fallback;
        }
        return value.toString();
    }

    private long numberValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private Instant parseInstant(Object val) {
        if (val instanceof Instant inst) return inst;
        if (val instanceof java.util.Date date) return date.toInstant();
        if (val instanceof String str) {
            try { return Instant.parse(str); } catch (Exception ignored) {}
        }
        return null;
    }

    private record TimeRange(String projectId, Instant startTime, Instant endTime) { }

    private record IntervalDefinition(String unit, int binSize, long millis) { }
}
