package com.example.logmonitor.project.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Document(collection = "projects")
@CompoundIndex(name = "organization_project_key_unique", def = "{'organizationId': 1, 'key': 1}", unique = true)
public class Project {

    @Id
    private String id;

    @Indexed
    private String organizationId;

    private String key;
    private String name;
    private Boolean active = true;
    private List<String> environments = new ArrayList<>();
    private RetentionPolicy retention = new RetentionPolicy();
    private Map<String, String> settings = new LinkedHashMap<>();
    private Instant createdAt;
    private Instant updatedAt;

    public Project() {
    }

    public Project(String id, String organizationId, String key, String name) {
        this.id = id;
        this.organizationId = organizationId;
        this.key = key;
        this.name = name;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isActive() { return active == null || active; }
    public void setActive(boolean active) { this.active = active; }

    public List<String> getEnvironments() {
        return environments == null ? List.of() : List.copyOf(environments);
    }

    public void setEnvironments(List<String> environments) {
        this.environments = environments == null ? new ArrayList<>() : new ArrayList<>(environments);
    }

    public RetentionPolicy getRetention() {
        return retention == null ? new RetentionPolicy() : retention;
    }

    public void setRetention(RetentionPolicy retention) {
        this.retention = retention == null ? new RetentionPolicy() : retention;
    }

    public Map<String, String> getSettings() {
        return settings == null ? Map.of() : Map.copyOf(settings);
    }

    public void setSettings(Map<String, String> settings) {
        this.settings = settings == null ? new LinkedHashMap<>() : new LinkedHashMap<>(settings);
    }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public static class RetentionPolicy {
        private int defaultDays = 7;
        private Map<String, Integer> levelOverrides = new LinkedHashMap<>();

        public RetentionPolicy() {
        }

        public RetentionPolicy(int defaultDays, Map<String, Integer> levelOverrides) {
            this.defaultDays = defaultDays;
            this.levelOverrides = levelOverrides == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(levelOverrides);
        }

        public int getDefaultDays() { return defaultDays; }
        public void setDefaultDays(int defaultDays) { this.defaultDays = defaultDays; }

        public Map<String, Integer> getLevelOverrides() {
            return levelOverrides == null ? Map.of() : Map.copyOf(levelOverrides);
        }

        public void setLevelOverrides(Map<String, Integer> levelOverrides) {
            this.levelOverrides = levelOverrides == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(levelOverrides);
        }
    }
}
