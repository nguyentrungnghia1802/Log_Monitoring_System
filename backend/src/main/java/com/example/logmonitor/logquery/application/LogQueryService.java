package com.example.logmonitor.logquery.application;

import com.example.logmonitor.logquery.api.LogSearchResponse;
import com.example.logmonitor.persistence.LogEventDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
public class LogQueryService {

    private final MongoTemplate mongoTemplate;

    public LogQueryService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
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
        int limit
    ) {
        int pageLimit = Math.min(Math.max(limit, 1), 200);
        Instant effectiveEndTime = endTime == null ? Instant.now() : endTime;
        Instant effectiveStartTime = startTime == null ? effectiveEndTime.minus(1, ChronoUnit.HOURS) : startTime;

        Criteria criteria = Criteria.where("project_id").is(projectId)
            .and("timestamp").gte(effectiveStartTime).lte(effectiveEndTime);

        if (level != null && !level.isBlank()) {
            criteria.and("level").is(level.trim().toUpperCase());
        }
        if (service != null && !service.isBlank()) {
            criteria.and("service").is(service.trim());
        }
        if (environment != null && !environment.isBlank()) {
            criteria.and("environment").is(environment.trim());
        }
        if (eventType != null && !eventType.isBlank()) {
            criteria.and("event_type").is(eventType.trim());
        }
        if (traceId != null && !traceId.isBlank()) {
            criteria.and("trace_id").is(traceId.trim());
        }
        if (requestId != null && !requestId.isBlank()) {
            criteria.and("request_id").is(requestId.trim());
        }
        if (errorFingerprint != null && !errorFingerprint.isBlank()) {
            criteria.and("error_fingerprint").is(errorFingerprint.trim());
        }
        if (search != null && !search.isBlank()) {
            criteria.and("message").regex(search.trim(), "i");
        }

        if (cursor != null && !cursor.isBlank()) {
            CursorData cursorData = decodeCursor(cursor);
            if (cursorData != null) {
                Criteria cursorCriteria = new Criteria().orOperator(
                    Criteria.where("timestamp").lt(cursorData.timestamp()),
                    Criteria.where("timestamp").is(cursorData.timestamp()).and("_id").lt(cursorData.id())
                );
                criteria.andOperator(cursorCriteria);
            }
        }

        Query query = Query.query(criteria)
            .with(Sort.by(Sort.Order.desc("timestamp"), Sort.Order.desc("_id")))
            .limit(pageLimit + 1);

        List<LogEventDocument> documents = mongoTemplate.find(query, LogEventDocument.class);

        boolean hasMore = documents.size() > pageLimit;
        List<LogEventDocument> pageDocs = hasMore ? documents.subList(0, pageLimit) : documents;

        List<LogSearchResponse.LogEventResponse> events = pageDocs.stream()
            .map(this::toResponse)
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

    public Optional<LogSearchResponse.LogEventResponse> getLogById(String projectId, String id) {
        Query query = Query.query(Criteria.where("_id").is(id).and("project_id").is(projectId));
        LogEventDocument document = mongoTemplate.findOne(query, LogEventDocument.class);
        return Optional.ofNullable(document).map(this::toResponse);
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
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursorStr);
            String raw = new String(decoded, StandardCharsets.UTF_8);
            String[] parts = raw.split(":", 2);
            if (parts.length == 2) {
                long millis = Long.parseLong(parts[0]);
                return new CursorData(Instant.ofEpochMilli(millis), parts[1]);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private record CursorData(Instant timestamp, String id) {}
}
