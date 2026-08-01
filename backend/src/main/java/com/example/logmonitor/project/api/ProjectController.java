package com.example.logmonitor.project.api;

import com.example.logmonitor.auth.application.JwtService;
import com.example.logmonitor.auth.application.ProjectAuthorizationService;
import com.example.logmonitor.organization.application.OrganizationAuthorizationService;
import com.example.logmonitor.project.application.ProjectManagementException;
import com.example.logmonitor.project.application.ProjectManagementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final OrganizationAuthorizationService organizationAuthorizationService;
    private final ProjectAuthorizationService projectAuthorizationService;
    private final ProjectManagementService managementService;

    public ProjectController(
        OrganizationAuthorizationService organizationAuthorizationService,
        ProjectAuthorizationService projectAuthorizationService,
        ProjectManagementService managementService
    ) {
        this.organizationAuthorizationService = organizationAuthorizationService;
        this.projectAuthorizationService = projectAuthorizationService;
        this.managementService = managementService;
    }

    public record RetentionRequest(
        Integer defaultDays,
        Map<String, Integer> levelOverrides
    ) {}

    public record CreateProjectRequest(
        @NotBlank @Size(max = 80) String key,
        @NotBlank @Size(max = 200) String name,
        List<String> environments,
        @Valid RetentionRequest retention,
        Map<String, String> settings
    ) {}

    public record UpdateProjectRequest(
        @Size(max = 200) String name,
        List<String> environments,
        Map<String, String> settings
    ) {}

    @GetMapping
    public ResponseEntity<?> listProjects() {
        String organizationId = currentOrganizationId();
        ResponseEntity<?> denied = requireOrganization(organizationId, OrganizationAuthorizationService.Permission.READ);
        if (denied != null) {
            return denied;
        }
        return organizationAuthorizationService.resolveCurrentUser(currentAuthentication(), organizationId)
            .<ResponseEntity<?>>map(user -> ResponseEntity.ok(managementService.listProjects(organizationId, user)))
            .orElseGet(() -> error(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication required"));
    }

    @PostMapping
    public ResponseEntity<?> createProject(@Valid @RequestBody CreateProjectRequest request) {
        String organizationId = currentOrganizationId();
        ResponseEntity<?> denied = requireOrganization(organizationId, OrganizationAuthorizationService.Permission.MANAGE);
        if (denied != null) {
            return denied;
        }
        return handleManagement(() -> ResponseEntity.status(HttpStatus.CREATED).body(
            managementService.createProject(
                organizationId,
                new ProjectManagementService.CreateProjectCommand(
                    request.key(),
                    request.name(),
                    request.environments(),
                    toRetentionCommand(request.retention()),
                    request.settings()
                ),
                currentUserId()
            )
        ));
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<?> getProject(@PathVariable String projectId) {
        ResponseEntity<?> denied = requireProject(projectId, ProjectAuthorizationService.Permission.READ);
        if (denied != null) {
            return denied;
        }
        return handleManagement(() -> ResponseEntity.ok(
            managementService.getProject(currentOrganizationId(), projectId)
        ));
    }

    @PatchMapping("/{projectId}")
    public ResponseEntity<?> updateProject(
        @PathVariable String projectId,
        @Valid @RequestBody UpdateProjectRequest request
    ) {
        ResponseEntity<?> denied = requireOrganization(currentOrganizationId(), OrganizationAuthorizationService.Permission.MANAGE);
        if (denied != null) {
            return denied;
        }
        return handleManagement(() -> ResponseEntity.ok(managementService.updateProject(
            currentOrganizationId(),
            projectId,
            new ProjectManagementService.UpdateProjectCommand(request.name(), request.environments(), request.settings()),
            currentUserId()
        )));
    }

    @PutMapping("/{projectId}/retention")
    public ResponseEntity<?> updateRetention(
        @PathVariable String projectId,
        @Valid @RequestBody RetentionRequest request
    ) {
        ResponseEntity<?> denied = requireOrganization(currentOrganizationId(), OrganizationAuthorizationService.Permission.MANAGE);
        if (denied != null) {
            return denied;
        }
        return handleManagement(() -> ResponseEntity.ok(managementService.updateRetention(
            currentOrganizationId(),
            projectId,
            toRetentionCommand(request),
            currentUserId()
        )));
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<?> deactivateProject(@PathVariable String projectId) {
        ResponseEntity<?> denied = requireOrganization(currentOrganizationId(), OrganizationAuthorizationService.Permission.MANAGE);
        if (denied != null) {
            return denied;
        }
        return handleManagement(() -> {
            managementService.deactivateProject(currentOrganizationId(), projectId, currentUserId());
            return ResponseEntity.noContent().build();
        });
    }

    private ProjectManagementService.RetentionCommand toRetentionCommand(RetentionRequest request) {
        return request == null
            ? null
            : new ProjectManagementService.RetentionCommand(request.defaultDays(), request.levelOverrides());
    }

    private ResponseEntity<?> requireOrganization(
        String organizationId,
        OrganizationAuthorizationService.Permission permission
    ) {
        var decision = organizationAuthorizationService.authorize(currentAuthentication(), organizationId, permission);
        if (decision.allowed()) {
            return null;
        }
        return decision.failure() == OrganizationAuthorizationService.Failure.UNAUTHENTICATED
            ? error(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication required")
            : error(HttpStatus.FORBIDDEN, "FORBIDDEN", "Organization access denied");
    }

    private ResponseEntity<?> requireProject(String projectId, ProjectAuthorizationService.Permission permission) {
        var decision = projectAuthorizationService.authorize(currentAuthentication(), projectId, permission);
        if (decision.allowed()) {
            return null;
        }
        return decision.failure() == ProjectAuthorizationService.Failure.UNAUTHENTICATED
            ? error(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication required")
            : error(HttpStatus.FORBIDDEN, "FORBIDDEN", "Project access denied");
    }

    private Authentication currentAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private String currentOrganizationId() {
        Authentication authentication = currentAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof JwtService.UserPrincipal principal) {
            return principal.organizationId();
        }
        return null;
    }

    private String currentUserId() {
        Authentication authentication = currentAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof JwtService.UserPrincipal principal) {
            return principal.userId();
        }
        return "unknown";
    }

    private <T> ResponseEntity<?> handleManagement(ManagementOperation<T> operation) {
        try {
            return operation.execute();
        } catch (ProjectManagementException exception) {
            HttpStatus status = switch (exception.getKind()) {
                case VALIDATION -> HttpStatus.UNPROCESSABLE_ENTITY;
                case NOT_FOUND -> HttpStatus.NOT_FOUND;
                case CONFLICT -> HttpStatus.CONFLICT;
            };
            return error(status, exception.getCode(), exception.getMessage());
        }
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("error", Map.of("code", code, "message", message));
        return ResponseEntity.status(status).body(body);
    }

    @FunctionalInterface
    private interface ManagementOperation<T> {
        ResponseEntity<T> execute();
    }
}
