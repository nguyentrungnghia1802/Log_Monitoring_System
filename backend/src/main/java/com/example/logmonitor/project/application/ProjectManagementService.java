package com.example.logmonitor.project.application;

import com.example.logmonitor.audit.application.AuditService;
import com.example.logmonitor.auth.domain.ProjectMembership;
import com.example.logmonitor.auth.domain.ProjectMembershipRepository;
import com.example.logmonitor.auth.domain.User;
import com.example.logmonitor.organization.application.OrganizationAuthorizationService;
import com.example.logmonitor.project.domain.Project;
import com.example.logmonitor.project.domain.ProjectRepository;
import com.example.logmonitor.project.domain.RetentionPolicyResolver;
import com.example.logmonitor.project.infrastructure.ProjectActivityRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ProjectManagementService {

    private static final int MAX_KEY_LENGTH = 80;
    private static final int MAX_NAME_LENGTH = 200;
    private static final int MAX_ENVIRONMENTS = 20;
    private static final int MAX_ENVIRONMENT_LENGTH = 64;
    private static final int MAX_SETTINGS = 20;
    private static final int MAX_SETTING_KEY_LENGTH = 64;
    private static final int MAX_SETTING_VALUE_LENGTH = 256;
    private static final int MIN_RETENTION_DAYS = 1;
    private static final int MAX_RETENTION_DAYS = 3650;
    private static final Set<String> RETENTION_LEVELS = Set.of("DEBUG", "INFO", "WARN", "ERROR", "FATAL");

    private final ProjectRepository projectRepository;
    private final ProjectMembershipRepository membershipRepository;
    private final OrganizationAuthorizationService organizationAuthorizationService;
    private final ProjectActivityRepository activityRepository;
    private final RetentionPolicyResolver retentionPolicyResolver;
    private final AuditService auditService;

    public ProjectManagementService(
        ProjectRepository projectRepository,
        ProjectMembershipRepository membershipRepository,
        OrganizationAuthorizationService organizationAuthorizationService,
        ProjectActivityRepository activityRepository,
        RetentionPolicyResolver retentionPolicyResolver,
        AuditService auditService
    ) {
        this.projectRepository = projectRepository;
        this.membershipRepository = membershipRepository;
        this.organizationAuthorizationService = organizationAuthorizationService;
        this.activityRepository = activityRepository;
        this.retentionPolicyResolver = retentionPolicyResolver;
        this.auditService = auditService;
    }

    public List<ProjectView> listProjects(String organizationId, User currentUser) {
        List<Project> projects;
        if (organizationAuthorizationService.isOrganizationAdmin(currentUser)) {
            projects = projectRepository.findByOrganizationIdOrderByCreatedAtAsc(organizationId);
        } else {
            List<String> projectIds = membershipRepository.findByUserId(currentUser.getId()).stream()
                .map(ProjectMembership::getProjectId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
            projects = projectIds.isEmpty()
                ? List.of()
                : projectRepository.findByIdInAndOrganizationIdOrderByCreatedAtAsc(projectIds, organizationId);
        }
        return projects.stream().map(this::toView).toList();
    }

    public ProjectView getProject(String organizationId, String projectId) {
        return toView(findProject(organizationId, projectId));
    }

    public ProjectView createProject(
        String organizationId,
        CreateProjectCommand command,
        String actor
    ) {
        String key = validateKey(command.key());
        String name = validateName(command.name());
        List<String> environments = normalizeEnvironments(command.environments(), true);
        Map<String, String> settings = normalizeSettings(command.settings());
        Project.RetentionPolicy retention = normalizeRetention(command.retention());

        if (projectRepository.findByOrganizationIdAndKey(organizationId, key).isPresent()) {
            throw conflict("PROJECT_KEY_EXISTS", "Project key already exists in this organization");
        }

        Project project = new Project(null, organizationId, key, name);
        project.setEnvironments(environments);
        project.setSettings(settings);
        project.setRetention(retention);
        project.setActive(true);

        Project saved;
        try {
            saved = projectRepository.save(project);
        } catch (DuplicateKeyException exception) {
            throw conflict("PROJECT_KEY_EXISTS", "Project key already exists in this organization");
        }
        retentionPolicyResolver.configureProject(
            saved.getId(),
            saved.getRetention().getDefaultDays(),
            saved.getRetention().getLevelOverrides()
        );
        auditService.logAction(actor, organizationId, saved.getId(), "CREATE", "PROJECT", saved.getId(),
            "Project created");
        return toView(saved);
    }

    public ProjectView updateProject(
        String organizationId,
        String projectId,
        UpdateProjectCommand command,
        String actor
    ) {
        Project project = findProject(organizationId, projectId);
        if (command.name() != null) {
            project.setName(validateName(command.name()));
        }
        if (command.environments() != null) {
            project.setEnvironments(normalizeEnvironments(command.environments(), false));
        }
        if (command.settings() != null) {
            project.setSettings(normalizeSettings(command.settings()));
        }
        project.setUpdatedAt(Instant.now());
        Project saved = projectRepository.save(project);
        auditService.logAction(actor, organizationId, saved.getId(), "UPDATE", "PROJECT", saved.getId(),
            "Project settings updated");
        return toView(saved);
    }

    public ProjectView updateRetention(
        String organizationId,
        String projectId,
        RetentionCommand command,
        String actor
    ) {
        Project project = findProject(organizationId, projectId);
        Project.RetentionPolicy retention = normalizeRetention(command);
        project.setRetention(retention);
        project.setUpdatedAt(Instant.now());
        Project saved = projectRepository.save(project);
        retentionPolicyResolver.configureProject(
            saved.getId(),
            saved.getRetention().getDefaultDays(),
            saved.getRetention().getLevelOverrides()
        );
        auditService.logAction(actor, organizationId, saved.getId(), "UPDATE", "PROJECT_RETENTION", saved.getId(),
            "Project retention policy updated");
        return toView(saved);
    }

    public void deactivateProject(String organizationId, String projectId, String actor) {
        Project project = findProject(organizationId, projectId);
        if (!project.isActive()) {
            return;
        }
        project.setActive(false);
        project.setUpdatedAt(Instant.now());
        Project saved = projectRepository.save(project);
        auditService.logAction(actor, organizationId, saved.getId(), "DEACTIVATE", "PROJECT", saved.getId(),
            "Project deactivated; ingestion is no longer accepted");
    }

    private Project findProject(String organizationId, String projectId) {
        if (organizationId == null || organizationId.isBlank() || projectId == null || projectId.isBlank()) {
            throw notFound("PROJECT_NOT_FOUND", "Project does not exist in the organization");
        }
        return projectRepository.findByIdAndOrganizationId(projectId, organizationId)
            .orElseThrow(() -> notFound("PROJECT_NOT_FOUND", "Project does not exist in the organization"));
    }

    private ProjectView toView(Project project) {
        ProjectActivityRepository.ActivitySummary activity = activityRepository.summarize(
            project.getId(),
            Instant.now().minusSeconds(24 * 3600L)
        );
        Project.RetentionPolicy retention = project.getRetention();
        return new ProjectView(
            project.getId(),
            project.getOrganizationId(),
            project.getKey(),
            project.getName(),
            project.isActive(),
            project.getEnvironments(),
            new RetentionView(retention.getDefaultDays(), retention.getLevelOverrides()),
            project.getSettings(),
            activity.services(),
            new IngestionSummaryView(
                activity.eventsLast24Hours(),
                activity.errorEventsLast24Hours(),
                activity.lastReceivedAt()
            ),
            project.getCreatedAt(),
            project.getUpdatedAt()
        );
    }

    private String validateKey(String value) {
        if (value == null || value.isBlank()) {
            throw validation("PROJECT_KEY_REQUIRED", "Project key is required");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > MAX_KEY_LENGTH || !normalized.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw validation("INVALID_PROJECT_KEY", "Project key must use lowercase letters, numbers, and single hyphens");
        }
        return normalized;
    }

    private String validateName(String value) {
        if (value == null || value.isBlank()) {
            throw validation("PROJECT_NAME_REQUIRED", "Project name is required");
        }
        String normalized = value.trim();
        if (normalized.length() > MAX_NAME_LENGTH) {
            throw validation("PROJECT_NAME_TOO_LONG", "Project name is too long");
        }
        return normalized;
    }

    private List<String> normalizeEnvironments(List<String> values, boolean useDefaultWhenMissing) {
        if (values == null && useDefaultWhenMissing) {
            return List.of("development");
        }
        if (values == null || values.isEmpty()) {
            throw validation("ENVIRONMENTS_REQUIRED", "At least one environment is required");
        }
        if (values.size() > MAX_ENVIRONMENTS) {
            throw validation("TOO_MANY_ENVIRONMENTS", "Too many environments");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw validation("INVALID_ENVIRONMENT", "Environment names cannot be blank");
            }
            String environment = value.trim().toLowerCase(Locale.ROOT);
            if (environment.length() > MAX_ENVIRONMENT_LENGTH
                || !environment.matches("[a-z0-9]+(?:[-_.][a-z0-9]+)*")) {
                throw validation("INVALID_ENVIRONMENT", "Environment names contain unsupported characters");
            }
            normalized.add(environment);
        }
        return List.copyOf(normalized);
    }

    private Map<String, String> normalizeSettings(Map<String, String> values) {
        if (values == null) {
            return Map.of();
        }
        if (values.size() > MAX_SETTINGS) {
            throw validation("TOO_MANY_PROJECT_SETTINGS", "Too many project settings");
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        values.forEach((rawKey, rawValue) -> {
            if (rawKey == null || rawKey.isBlank() || rawKey.trim().length() > MAX_SETTING_KEY_LENGTH) {
                throw validation("INVALID_PROJECT_SETTING", "Project setting keys are invalid");
            }
            if (rawValue == null || rawValue.length() > MAX_SETTING_VALUE_LENGTH) {
                throw validation("INVALID_PROJECT_SETTING", "Project setting values are invalid");
            }
            normalized.put(rawKey.trim(), rawValue);
        });
        return Map.copyOf(normalized);
    }

    private Project.RetentionPolicy normalizeRetention(RetentionCommand command) {
        int defaultDays = command == null || command.defaultDays() == null ? 7 : command.defaultDays();
        validateRetentionDays(defaultDays);
        Map<String, Integer> normalized = new LinkedHashMap<>();
        Map<String, Integer> overrides = command == null || command.levelOverrides() == null
            ? Map.of()
            : command.levelOverrides();
        if (overrides.size() > RETENTION_LEVELS.size()) {
            throw validation("INVALID_RETENTION_POLICY", "Too many retention level overrides");
        }
        overrides.forEach((rawLevel, days) -> {
            if (rawLevel == null || !RETENTION_LEVELS.contains(rawLevel.trim().toUpperCase(Locale.ROOT))) {
                throw validation("INVALID_RETENTION_LEVEL", "Unsupported retention level");
            }
            if (days == null) {
                throw validation("INVALID_RETENTION_DAYS", "Retention days are required");
            }
            validateRetentionDays(days);
            normalized.put(rawLevel.trim().toUpperCase(Locale.ROOT), days);
        });
        return new Project.RetentionPolicy(defaultDays, normalized);
    }

    private void validateRetentionDays(int days) {
        if (days < MIN_RETENTION_DAYS || days > MAX_RETENTION_DAYS) {
            throw validation("INVALID_RETENTION_DAYS", "Retention must be between 1 and 3650 days");
        }
    }

    private ProjectManagementException validation(String code, String message) {
        return new ProjectManagementException(ProjectManagementException.Kind.VALIDATION, code, message);
    }

    private ProjectManagementException notFound(String code, String message) {
        return new ProjectManagementException(ProjectManagementException.Kind.NOT_FOUND, code, message);
    }

    private ProjectManagementException conflict(String code, String message) {
        return new ProjectManagementException(ProjectManagementException.Kind.CONFLICT, code, message);
    }

    public record CreateProjectCommand(
        String key,
        String name,
        List<String> environments,
        RetentionCommand retention,
        Map<String, String> settings
    ) {}

    public record UpdateProjectCommand(
        String name,
        List<String> environments,
        Map<String, String> settings
    ) {}

    public record RetentionCommand(
        Integer defaultDays,
        Map<String, Integer> levelOverrides
    ) {}

    public record ProjectView(
        String id,
        String organizationId,
        String key,
        String name,
        boolean active,
        List<String> environments,
        RetentionView retention,
        Map<String, String> settings,
        List<String> services,
        IngestionSummaryView recentIngestion,
        Instant createdAt,
        Instant updatedAt
    ) {}

    public record RetentionView(int defaultDays, Map<String, Integer> levelOverrides) {}

    public record IngestionSummaryView(
        long eventsLast24Hours,
        long errorEventsLast24Hours,
        Instant lastReceivedAt
    ) {}
}
