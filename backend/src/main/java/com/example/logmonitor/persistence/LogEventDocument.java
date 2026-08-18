package com.example.logmonitor.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.Map;

@Document(collection = "log_events")
@CompoundIndexes({
    @CompoundIndex(name = "idx_logs_proj_time", def = "{'project_id': 1, 'timestamp': -1, '_id': -1}"),
    @CompoundIndex(name = "idx_logs_proj_level_time", def = "{'project_id': 1, 'level': 1, 'timestamp': -1, '_id': -1}"),
    @CompoundIndex(name = "idx_logs_proj_environment_time", def = "{'project_id': 1, 'environment': 1, 'timestamp': -1, '_id': -1}"),
    @CompoundIndex(name = "idx_logs_proj_service_time", def = "{'project_id': 1, 'service': 1, 'timestamp': -1, '_id': -1}"),
    @CompoundIndex(name = "idx_logs_proj_eventtype_time", def = "{'project_id': 1, 'event_type': 1, 'timestamp': -1, '_id': -1}"),
    @CompoundIndex(name = "idx_logs_proj_trace", def = "{'project_id': 1, 'trace_id': 1, 'timestamp': -1}"),
    @CompoundIndex(name = "idx_logs_proj_request", def = "{'project_id': 1, 'request_id': 1, 'timestamp': -1}"),
    @CompoundIndex(name = "idx_logs_proj_fingerprint_time", def = "{'project_id': 1, 'error_fingerprint': 1, 'timestamp': -1}")
})
public class LogEventDocument {

    @Id
    private String id;

    @Field("event_id")
    private String eventId;

    @Field("timestamp")
    private Instant timestamp;

    @Field("level")
    private String level;

    @Field("service")
    private String service;

    @Field("environment")
    private String environment;

    @Field("event_type")
    private String eventType;

    @Field("message")
    private String message;

    @Field("trace_id")
    private String traceId;

    @Field("request_id")
    private String requestId;

    @Field("exception")
    private Map<String, Object> exception;

    @Field("context")
    private Map<String, Object> context;

    @Field("tags")
    private Map<String, Object> tags;

    @Field("received_at")
    private Instant receivedAt;

    @Indexed(name = "ttl_expire", expireAfterSeconds = 0)
    @Field("expire_at")
    private Instant expireAt;

    @Field("organization_id")
    private String organizationId;

    @Field("project_id")
    private String projectId;

    @Field("api_key_id")
    private String apiKeyId;

    @Field("error_fingerprint")
    private String errorFingerprint;

    public LogEventDocument() {
    }

    public LogEventDocument(String eventId, Instant timestamp, String level, String service, String environment,
                            String eventType, String message, String traceId, String requestId,
                            Map<String, Object> exception, Map<String, Object> context, Map<String, Object> tags,
                            Instant receivedAt, Instant expireAt, String organizationId, String projectId,
                            String apiKeyId, String errorFingerprint) {
        this.eventId = eventId;
        this.timestamp = timestamp;
        this.level = level;
        this.service = service;
        this.environment = environment;
        this.eventType = eventType;
        this.message = message;
        this.traceId = traceId;
        this.requestId = requestId;
        this.exception = exception;
        this.context = context;
        this.tags = tags;
        this.receivedAt = receivedAt;
        this.expireAt = expireAt;
        this.organizationId = organizationId;
        this.projectId = projectId;
        this.apiKeyId = apiKeyId;
        this.errorFingerprint = errorFingerprint;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getEventId() { return eventId; }
    public String getTimestamp() { return timestamp == null ? null : timestamp.toString(); }
    public String getLevel() { return level; }
    public String getService() { return service; }
    public String getEnvironment() { return environment; }
    public String getEventType() { return eventType; }
    public String getMessage() { return message; }
    public String getTraceId() { return traceId; }
    public String getRequestId() { return requestId; }
    public Map<String, Object> getException() { return exception; }
    public Map<String, Object> getContext() { return context; }
    public Map<String, Object> getTags() { return tags; }
    public Instant getReceivedAt() { return receivedAt; }
    public Instant getExpireAt() { return expireAt; }
    public String getOrganizationId() { return organizationId; }
    public String getProjectId() { return projectId; }
    public String getApiKeyId() { return apiKeyId; }
    public String getErrorFingerprint() { return errorFingerprint; }
}
