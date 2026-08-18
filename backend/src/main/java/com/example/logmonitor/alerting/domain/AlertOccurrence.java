package com.example.logmonitor.alerting.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "alert_occurrences")
public class AlertOccurrence {

    @Id
    private String id;

    @Field("rule_id")
    private String ruleId;

    @Field("rule_name")
    private String ruleName;

    @Field("project_id")
    private String projectId;

    @Field("triggered_at")
    private Instant triggeredAt = Instant.now();

    @Field("window_start")
    private Instant windowStart;

    @Field("window_end")
    private Instant windowEnd;

    @Field("observed_value")
    private long observedValue;

    @Field("threshold")
    private long threshold;

    @Field("status")
    private String status = "TRIGGERED"; // TRIGGERED, ACKNOWLEDGED

    @Field("delivery_status")
    private String deliveryStatus = "PENDING"; // PENDING, DELIVERED, FAILED

    @Field("attempt_count")
    private int attemptCount = 0;

    @Field("last_attempt_at")
    private Instant lastAttemptAt;

    @Field("last_error")
    private String lastError;

    @Field("acknowledged_at")
    private Instant acknowledgedAt;

    @Field("acknowledged_by")
    private String acknowledgedBy;

    @Field("delivery_attempts")
    private List<DeliveryAttempt> deliveryAttempts = new ArrayList<>();

    public AlertOccurrence() {}

    public AlertOccurrence(String id, String ruleId, String ruleName, String projectId, Instant triggeredAt,
                           Instant windowStart, Instant windowEnd, long observedValue, long threshold,
                           String status, String deliveryStatus, int attemptCount, Instant lastAttemptAt, String lastError) {
        this.id = id;
        this.ruleId = ruleId;
        this.ruleName = ruleName;
        this.projectId = projectId;
        this.triggeredAt = triggeredAt;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.observedValue = observedValue;
        this.threshold = threshold;
        this.status = status;
        this.deliveryStatus = deliveryStatus;
        this.attemptCount = attemptCount;
        this.lastAttemptAt = lastAttemptAt;
        this.lastError = lastError;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }

    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public Instant getTriggeredAt() { return triggeredAt; }
    public void setTriggeredAt(Instant triggeredAt) { this.triggeredAt = triggeredAt; }

    public Instant getWindowStart() { return windowStart; }
    public void setWindowStart(Instant windowStart) { this.windowStart = windowStart; }

    public Instant getWindowEnd() { return windowEnd; }
    public void setWindowEnd(Instant windowEnd) { this.windowEnd = windowEnd; }

    public long getObservedValue() { return observedValue; }
    public void setObservedValue(long observedValue) { this.observedValue = observedValue; }

    public long getThreshold() { return threshold; }
    public void setThreshold(long threshold) { this.threshold = threshold; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDeliveryStatus() { return deliveryStatus; }
    public void setDeliveryStatus(String deliveryStatus) { this.deliveryStatus = deliveryStatus; }

    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }

    public Instant getLastAttemptAt() { return lastAttemptAt; }
    public void setLastAttemptAt(Instant lastAttemptAt) { this.lastAttemptAt = lastAttemptAt; }

    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }

    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(Instant acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }

    public String getAcknowledgedBy() { return acknowledgedBy; }
    public void setAcknowledgedBy(String acknowledgedBy) { this.acknowledgedBy = acknowledgedBy; }

    public List<DeliveryAttempt> getDeliveryAttempts() {
        if (deliveryAttempts == null) {
            deliveryAttempts = new ArrayList<>();
        }
        return deliveryAttempts;
    }

    public void setDeliveryAttempts(List<DeliveryAttempt> deliveryAttempts) {
        this.deliveryAttempts = deliveryAttempts == null ? new ArrayList<>() : new ArrayList<>(deliveryAttempts);
    }

    public void addDeliveryAttempt(DeliveryAttempt attempt) {
        getDeliveryAttempts().add(attempt);
    }

    public record DeliveryAttempt(
        int attemptNumber,
        String provider,
        Instant attemptedAt,
        String status,
        String errorSummary
    ) {}
}
