package com.example.logmonitor.project.domain;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RetentionPolicyResolver {

    public static final long DEFAULT_RETENTION_SECONDS = 7 * 24 * 3600L; // 7 days
    public static final long DEBUG_RETENTION_SECONDS = 3 * 24 * 3600L;   // 3 days
    public static final long INFO_RETENTION_SECONDS = 7 * 24 * 3600L;    // 7 days
    public static final long WARN_RETENTION_SECONDS = 14 * 24 * 3600L;   // 14 days
    public static final long ERROR_RETENTION_SECONDS = 30 * 24 * 3600L;  // 30 days

    private final Map<String, Long> customProjectDefaults = new ConcurrentHashMap<>();
    private final Map<String, Project.RetentionPolicy> customProjectPolicies = new ConcurrentHashMap<>();
    private final ProjectRepository projectRepository;

    public RetentionPolicyResolver() {
        this.projectRepository = null;
    }

    @Autowired
    public RetentionPolicyResolver(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @PostConstruct
    void loadPersistedPolicies() {
        if (projectRepository == null) {
            return;
        }
        projectRepository.findAll().forEach(project -> configureProject(
            project.getId(),
            project.getRetention().getDefaultDays(),
            project.getRetention().getLevelOverrides()
        ));
    }

    public long resolveRetentionSeconds(String projectId, String level) {
        Project.RetentionPolicy projectPolicy = customProjectPolicies.get(projectId);
        if (projectPolicy != null) {
            String normalizedLevel = level == null ? null : level.trim().toUpperCase();
            Integer overrideDays = normalizedLevel == null
                ? null
                : projectPolicy.getLevelOverrides().get(normalizedLevel);
            long days = overrideDays == null ? projectPolicy.getDefaultDays() : overrideDays;
            return days * 24 * 3600L;
        }

        if (level == null) {
            return getProjectDefault(projectId);
        }

        String normalizedLevel = level.trim().toUpperCase();
        return switch (normalizedLevel) {
            case "DEBUG" -> DEBUG_RETENTION_SECONDS;
            case "INFO" -> INFO_RETENTION_SECONDS;
            case "WARN" -> WARN_RETENTION_SECONDS;
            case "ERROR", "FATAL" -> ERROR_RETENTION_SECONDS;
            default -> getProjectDefault(projectId);
        };
    }

    public void setProjectDefaultRetention(String projectId, long seconds) {
        if (projectId != null && seconds > 0) {
            customProjectDefaults.put(projectId, seconds);
        }
    }

    public void configureProject(String projectId, int defaultDays, Map<String, Integer> levelOverrides) {
        if (projectId == null || projectId.isBlank()) {
            return;
        }
        customProjectPolicies.put(projectId, new Project.RetentionPolicy(defaultDays, levelOverrides));
    }

    public long getProjectDefault(String projectId) {
        if (projectId == null) return DEFAULT_RETENTION_SECONDS;
        return customProjectDefaults.getOrDefault(projectId, DEFAULT_RETENTION_SECONDS);
    }
}
