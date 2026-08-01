package com.example.logmonitor.project.application;

import com.example.logmonitor.audit.application.AuditService;
import com.example.logmonitor.auth.domain.ProjectMembership;
import com.example.logmonitor.auth.domain.ProjectMembershipRepository;
import com.example.logmonitor.auth.domain.Role;
import com.example.logmonitor.auth.domain.User;
import com.example.logmonitor.organization.application.OrganizationAuthorizationService;
import com.example.logmonitor.project.domain.Project;
import com.example.logmonitor.project.domain.ProjectRepository;
import com.example.logmonitor.project.domain.RetentionPolicyResolver;
import com.example.logmonitor.project.infrastructure.ProjectActivityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectManagementServiceTest {

    private ProjectRepository projectRepository;
    private ProjectMembershipRepository membershipRepository;
    private OrganizationAuthorizationService organizationAuthorizationService;
    private ProjectActivityRepository activityRepository;
    private RetentionPolicyResolver retentionPolicyResolver;
    private AuditService auditService;
    private ProjectManagementService service;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        membershipRepository = mock(ProjectMembershipRepository.class);
        organizationAuthorizationService = mock(OrganizationAuthorizationService.class);
        activityRepository = mock(ProjectActivityRepository.class);
        retentionPolicyResolver = mock(RetentionPolicyResolver.class);
        auditService = mock(AuditService.class);
        service = new ProjectManagementService(
            projectRepository,
            membershipRepository,
            organizationAuthorizationService,
            activityRepository,
            retentionPolicyResolver,
            auditService
        );
        when(activityRepository.summarize(any(), any()))
            .thenReturn(new ProjectActivityRepository.ActivitySummary(List.of(), 0, 0, null));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project project = invocation.getArgument(0);
            if (project.getId() == null) {
                project.setId("project-created");
            }
            return project;
        });
    }

    @Test
    void createsSluggedProjectAndConfiguresPersistedRetention() {
        when(projectRepository.findByOrganizationIdAndKey("org-1", "line-smart-queue"))
            .thenReturn(Optional.empty());

        ProjectManagementService.ProjectView result = service.createProject(
            "org-1",
            new ProjectManagementService.CreateProjectCommand(
                " Line-Smart-Queue ",
                " LINE Smart Queue ",
                List.of("Development", "production"),
                new ProjectManagementService.RetentionCommand(14, Map.of("error", 30)),
                Map.of("owner", "platform")
            ),
            "admin-1"
        );

        assertEquals("line-smart-queue", result.key());
        assertEquals("LINE Smart Queue", result.name());
        assertEquals(List.of("development", "production"), result.environments());
        assertEquals(14, result.retention().defaultDays());
        assertEquals(30, result.retention().levelOverrides().get("ERROR"));
        verify(retentionPolicyResolver).configureProject("project-created", 14, Map.of("ERROR", 30));
        verify(auditService).logAction(
            "admin-1", "org-1", "project-created", "CREATE", "PROJECT", "project-created", "Project created");
    }

    @Test
    void rejectsDuplicateKeyAndInvalidRetention() {
        Project existing = new Project("project-1", "org-1", "existing", "Existing");
        when(projectRepository.findByOrganizationIdAndKey("org-1", "existing"))
            .thenReturn(Optional.of(existing));

        ProjectManagementException duplicate = assertThrows(ProjectManagementException.class, () -> service.createProject(
            "org-1",
            new ProjectManagementService.CreateProjectCommand(
                "existing", "Another", List.of("production"), null, null),
            "admin-1"
        ));
        assertEquals("PROJECT_KEY_EXISTS", duplicate.getCode());

        when(projectRepository.findByOrganizationIdAndKey("org-1", "new-project"))
            .thenReturn(Optional.empty());
        ProjectManagementException invalidRetention = assertThrows(ProjectManagementException.class, () -> service.createProject(
            "org-1",
            new ProjectManagementService.CreateProjectCommand(
                "new-project", "New", List.of("production"),
                new ProjectManagementService.RetentionCommand(0, Map.of()), null),
            "admin-1"
        ));
        assertEquals("INVALID_RETENTION_DAYS", invalidRetention.getCode());
    }

    @Test
    void listsOnlyProjectsFromAuthorizedMembershipForNonAdmin() {
        User user = new User("user-1", "operator", "operator@example.com", "hash", "org-1");
        Project project = new Project("project-1", "org-1", "project-one", "Project One");
        when(organizationAuthorizationService.isOrganizationAdmin(user)).thenReturn(false);
        when(membershipRepository.findByUserId("user-1"))
            .thenReturn(List.of(new ProjectMembership("user-1", "project-1", Role.PROJECT_OPERATOR)));
        when(projectRepository.findByIdInAndOrganizationIdOrderByCreatedAtAsc(List.of("project-1"), "org-1"))
            .thenReturn(List.of(project));

        List<ProjectManagementService.ProjectView> projects = service.listProjects("org-1", user);

        assertEquals(1, projects.size());
        assertEquals("project-one", projects.get(0).key());
        assertTrue(projects.get(0).active());
    }
}
