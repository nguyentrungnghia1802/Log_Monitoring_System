package com.example.logmonitor.logquery.application;

import com.example.logmonitor.logquery.api.LogSearchResponse;
import com.example.logmonitor.logquery.config.LogQueryProperties;
import com.example.logmonitor.persistence.LogEventDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class LogQueryService {

    private static final int HARD_MAX_PAGE_SIZE = 200;
    private static final int HARD_MAX_RANGE_HOURS = 24 * 31;
    private static final int HARD_MAX_SEARCH_LENGTH = 128;
    private static final int HARD_MAX_FILTER_LENGTH = 256;
    private static final int MAX_CURSOR_LENGTH = 512;

    private final MongoTemplate mongoTemplate;
    private final LogQueryProperties properties;

    public LogQueryService(MongoTemplate mongoTemplate, LogQueryProperties properties) {
        this.mongoTemplate = mongoTemplate;
        this.properties = properties;
    }

    public LogSearchResponse searchLogs(
        String projectId,
        Instant startTime,
        Instant endTime,
        String level,
        String service,
        String environment,
        String eventType,
        String traceId,
        String requestId,
        String errorFingerprint,
        String search,
        String cursor,
        Integer limit
    ) {
        int pageLimit = resolvePageLimit(limit);
        Instant effectiveEndTime = endTime == null ? Instant.now() : endTime;
        Instant effectiveStartTime = startTime == null
            ? effectiveEndTime.minus(resolveDefaultRangeHours(), ChronoUnit.HOURS)
            : startTime;
        validateTimeRange(effectiveStartTime, effectiveEndTime);

        String normalizedProjectId = normalizeFilter(projectId, "projectId");

        Criteria criteria = Criteria.where("project_id").is(normalizedProjectId)
            .and("timestamp").gte(effectiveStartTime).lte(effectiveEndTime);

        String normalizedLevel = normalizeFilter(level, "level");
        if (normalizedLevel != null) {
            criteria.and("level").is(normalizedLevel.toUpperCase());
        }
        String normalizedService = normalizeFilter(service, "service");
        if (normalizedService != null) {
            criteria.and("service").is(normalizedService);
        }
        String normalizedEnvironment = normalizeFilter(environment, "environment");
        if (normalizedEnvironment != null) {
            criteria.and("environment").is(normalizedEnvironment);
        }
        String normalizedEventType = normalizeFilter(eventType, "eventType");
        if (normalizedEventType != null) {
            criteria.and("event_type").is(normalizedEventType);
        }
        String normalizedTraceId = normalizeFilter(traceId, "traceId");
        if (normalizedTraceId != null) {
            criteria.and("trace_id").is(normalizedTraceId);
        }
        String normalizedRequestId = normalizeFilter(requestId, "requestId");
        if (normalizedRequestId != null) {
            criteria.and("request_id").is(normalizedRequestId);
        }
        String normalizedFingerprint = normalizeFilter(errorFingerprint, "errorFingerprint");
        if (normalizedFingerprint != null) {
            criteria.and("error_fingerprint").is(normalizedFingerprint);
        }
        String normalizedSearch = normalizeSearch(search);
        if (normalizedSearch != null) {
            criteria.and("message").regex(Pattern.quote(normalizedSearch), "i");
        }

        if (cursor != null && !cursor.isBlank()) {
            CursorData cursorData = decodeCursor(cursor);
            Criteria cursorCriteria = new Criteria().orOperator(
                Criteria.where("timestamp").lt(cursorData.timestamp()),
                Criteria.where("timestamp").is(cursorData.timestamp()).and("_id").lt(cursorData.id())
            );
            criteria.andOperator(cursorCriteria);
        }

        Query query = Query.query(criteria)
            .with(Sort.by(Sort.Order.desc("timestamp"), Sort.Order.desc("_id")))
            .limit(pageLimit + 1);
        query.fields()
            .include("event_id")
            .include("timestamp")
            .include("level")
            .include("service")
            .include("environment")
            .include("event_type")
            .include("message")
            .include("trace_id")
            .include("request_id")
            .include("project_id")
            .include("error_fingerprint");

        List<LogEventDocument> documents = mongoTemplate.find(query, LogEventDocument.class);

        boolean hasMore = documents.size() > pageLimit;
        List<LogEventDocument> pageDocs = hasMore ? documents.subList(0, pageLimit) : documents;

        List<LogSearchResponse.LogEventSummary> events = pageDocs.stream()
            .map(this::toSummaryResponse)
            .toList();

        String nextCursor = null;
        if (hasMore && !pageDocs.isEmpty()) {
            LogEventDocument lastDoc = pageDocs.get(pageDocs.size() - 1);
            Instant ts = parseInstant(lastDoc.getTimestamp());
            if (ts != null) {
                nextCursor = encodeCursor(ts, lastDoc.getId());
            }
        }

        return new LogSearchResponse(events, nextCursor, hasMore);
    }

    private int resolvePageLimit(Integer requestedLimit) {
        int maxPageSize = clamp(properties.getMaxPageSize(), 1, HARD_MAX_PAGE_SIZE);
        int defaultPageSize = clamp(properties.getDefaultPageSize(), 1, maxPageSize);
        int effectiveLimit = requestedLimit == null ? defaultPageSize : requestedLimit;
        if (effectiveLimit < 1) {
            throw new LogQueryException(
                "INVALID_PAGE_SIZE",
                "limit must be at least 1",
                LogQueryException.Kind.UNPROCESSABLE_ENTITY
            );
        }
        if (effectiveLimit > maxPageSize) {
            throw new LogQueryException(
                "SEARCH_PAGE_SIZE_TOO_LARGE",
                "limit must not exceed " + maxPageSize,
                LogQueryException.Kind.UNPROCESSABLE_ENTITY
            );
        }
        return effectiveLimit;
    }

    private void validateTimeRange(Instant startTime, Instant endTime) {
        if (startTime.isAfter(endTime)) {
            throw new LogQueryException(
                "INVALID_TIME_RANGE",
                "startTime must be before or equal to endTime",
                LogQueryException.Kind.UNPROCESSABLE_ENTITY
            );
        }
        long maxRangeHours = resolveMaxRangeHours();
        if (Duration.between(startTime, endTime).compareTo(Duration.ofHours(maxRangeHours)) > 0) {
            throw new LogQueryException(
                "SEARCH_RANGE_TOO_LARGE",
                "The requested time range must not exceed " + maxRangeHours + " hours",
                LogQueryException.Kind.UNPROCESSABLE_ENTITY
            );
        }
    }

    private String normalizeFilter(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            if ("projectId".equals(fieldName)) {
                throw new LogQueryException(
                    "INVALID_PROJECT_ID",
                    "projectId is required",
                    LogQueryException.Kind.UNPROCESSABLE_ENTITY
                );
            }
            return null;
        }
        String normalized = value.trim();
        int maxLength = clamp(properties.getMaxFilterLength(), 1, HARD_MAX_FILTER_LENGTH);
        if (normalized.length() > maxLength) {
            throw new LogQueryException(
                "SEARCH_FILTER_TOO_LONG",
                fieldName + " must not exceed " + maxLength + " characters",
                LogQueryException.Kind.UNPROCESSABLE_ENTITY
            );
        }
        return normalized;
    }

    private String normalizeSearch(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        int maxLength = clamp(properties.getMaxSearchLength(), 1, HARD_MAX_SEARCH_LENGTH);
        if (normalized.length() > maxLength) {
            throw new LogQueryException(
                "SEARCH_QUERY_TOO_LONG",
                "search must not exceed " + maxLength + " characters",
                LogQueryException.Kind.UNPROCESSABLE_ENTITY
            );
        }
        return normalized;
    }

    private int resolveDefaultRangeHours() {
        return clamp(properties.getDefaultRangeHours(), 1, (int) resolveMaxRangeHours());
    }

    private int resolveMaxRangeHours() {
        return clamp(properties.getMaxRangeHours(), 1, HARD_MAX_RANGE_HOURS);
    }

    private int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

    public Optional<LogSearchResponse.LogEventResponse> getLogById(String projectId, String id) {
        Query query = Query.query(Criteria.where("_id").is(id).and("project_id").is(projectId));
        LogEventDocument document = mongoTemplate.findOne(query, LogEventDocument.class);
        return Optional.ofNullable(document).map(this::toResponse);
    }

    private LogSearchResponse.LogEventSummary toSummaryResponse(LogEventDocument doc) {
        return new LogSearchResponse.LogEventSummary(
            doc.getId(),
            doc.getEventId(),
            parseInstant(doc.getTimestamp()),
            doc.getLevel(),
            doc.getService(),
            doc.getEnvironment(),
            doc.getEventType(),
            doc.getMessage(),
            doc.getTraceId(),
            doc.getRequestId(),
            doc.getProjectId(),
            doc.getErrorFingerprint()
        );
    }

    private LogSearchResponse.LogEventResponse toResponse(LogEventDocument doc) {
        return new LogSearchResponse.LogEventResponse(
            doc.getId(),
            doc.getEventId(),
            parseInstant(doc.getTimestamp()),
            doc.getLevel(),
            doc.getService(),
            doc.getEnvironment(),
            doc.getEventType(),
            doc.getMessage(),
            doc.getTraceId(),
            doc.getRequestId(),
            doc.getException(),
            doc.getContext(),
            doc.getTags(),
            doc.getReceivedAt(),
            doc.getExpireAt(),
            doc.getOrganizationId(),
            doc.getProjectId(),
            doc.getApiKeyId(),
            doc.getErrorFingerprint()
        );
    }

    private Instant parseInstant(String tsStr) {
        if (tsStr == null) return null;
        try {
            return Instant.parse(tsStr);
        } catch (Exception ex) {
            return null;
        }
    }

    private String encodeCursor(Instant timestamp, String id) {
        String raw = timestamp.toEpochMilli() + ":" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private CursorData decodeCursor(String cursorStr) {
        if (cursorStr.length() > MAX_CURSOR_LENGTH || !cursorStr.matches("[A-Za-z0-9_-]+")) {
            throw invalidCursor();
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursorStr);
            String raw = new String(decoded, StandardCharsets.UTF_8);
            String[] parts = raw.split(":", -1);
            if (parts.length == 2 && !parts[1].isBlank() && parts[1].length() <= 256) {
                long millis = Long.parseLong(parts[0]);
                return new CursorData(Instant.ofEpochMilli(millis), parts[1]);
            }
        } catch (IllegalArgumentException | DateTimeException ignored) {
            // Fall through to one stable client-facing error.
        }
        throw invalidCursor();
    }

    private LogQueryException invalidCursor() {
        return new LogQueryException(
            "INVALID_CURSOR",
            "cursor is malformed or expired",
            LogQueryException.Kind.BAD_REQUEST
        );
    }

    private record CursorData(Instant timestamp, String id) {}
}
