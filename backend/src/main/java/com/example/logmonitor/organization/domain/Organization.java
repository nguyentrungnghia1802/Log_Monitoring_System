package com.example.logmonitor.organization.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Document(collection = "organizations")
public class Organization {

    @Id
    private String id;

    @Indexed(unique = true)
    private String slug;

    private String name;
    private Boolean active = true;
    private Map<String, String> settings = new LinkedHashMap<>();
    private Instant createdAt;
    private Instant updatedAt;

    public Organization() {}

    public Organization(String id, String slug, String name) {
        this.id = id;
        this.slug = slug;
        this.name = name;
        this.active = true;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isActive() { return active == null || active; }
    public void setActive(boolean active) { this.active = active; }

    public Map<String, String> getSettings() {
        if (settings == null) {
            settings = new LinkedHashMap<>();
        }
        return settings;
    }

    public void setSettings(Map<String, String> settings) {
        this.settings = settings == null ? new LinkedHashMap<>() : new LinkedHashMap<>(settings);
    }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
