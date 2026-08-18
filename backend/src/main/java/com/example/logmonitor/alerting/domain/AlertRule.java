package com.example.logmonitor.alerting.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

@Document(collection = "alert_rules")
@CompoundIndex(name = "idx_alert_rules_project_enabled", def = "{'project_id': 1, 'enabled': 1}")
public class AlertRule {

    @Id
    private String id;

    @Field("name")
    private String name;

    @Field("project_id")
    private String projectId;

    @Field("enabled")
    private boolean enabled = true;

    @Field("environment")
    private String environment;

    @Field("service")
    private String service;

    @Field("levels")
    private List<String> levels;

    @Field("event_types")
    private List<String> eventTypes;

    @Field("window_seconds")
    private long windowSeconds = 60;

    @Field("threshold")
    private long threshold = 10;

    @Field("cooldown_seconds")
    private long cooldownSeconds = 300;

    @Field("cooldown_until")
    private Instant cooldownUntil;

    @Field("created_at")
    private Instant createdAt = Instant.now();

    @Field("updated_at")
    private Instant updatedAt = Instant.now();

    public AlertRule() {}

    public AlertRule(String id, String name, String projectId, boolean enabled, String environment, String service,
                     List<String> levels, List<String> eventTypes, long windowSeconds, long threshold,
                     long cooldownSeconds, Instant cooldownUntil, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.projectId = projectId;
        this.enabled = enabled;
        this.environment = environment;
        this.service = service;
        this.levels = levels;
        this.eventTypes = eventTypes;
        this.windowSeconds = windowSeconds;
        this.threshold = threshold;
        this.cooldownSeconds = cooldownSeconds;
        this.cooldownUntil = cooldownUntil;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public String getService() { return service; }
    public void setService(String service) { this.service = service; }

    public List<String> getLevels() { return levels; }
    public void setLevels(List<String> levels) { this.levels = levels; }

    public List<String> getEventTypes() { return eventTypes; }
    public void setEventTypes(List<String> eventTypes) { this.eventTypes = eventTypes; }

    public long getWindowSeconds() { return windowSeconds; }
    public void setWindowSeconds(long windowSeconds) { this.windowSeconds = windowSeconds; }

    public long getThreshold() { return threshold; }
    public void setThreshold(long threshold) { this.threshold = threshold; }

    public long getCooldownSeconds() { return cooldownSeconds; }
    public void setCooldownSeconds(long cooldownSeconds) { this.cooldownSeconds = cooldownSeconds; }

    public Instant getCooldownUntil() { return cooldownUntil; }
    public void setCooldownUntil(Instant cooldownUntil) { this.cooldownUntil = cooldownUntil; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
