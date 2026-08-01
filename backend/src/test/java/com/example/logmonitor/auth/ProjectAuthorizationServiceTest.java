package com.example.logmonitor.auth;

import com.example.logmonitor.auth.application.JwtService;
import com.example.logmonitor.auth.application.ProjectAuthorizationService;
import com.example.logmonitor.auth.domain.ProjectMembership;
import com.example.logmonitor.auth.domain.ProjectMembershipRepository;
import com.example.logmonitor.auth.domain.Role;
import com.example.logmonitor.auth.domain.User;
import com.example.logmonitor.auth.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectAuthorizationServiceTest {

    private ProjectMembershipRepository membershipRepository;
    private UserRepository userRepository;
    private ProjectAuthorizationService authorizationService;

    @BeforeEach
    void setUp() {
        membershipRepository = mock(ProjectMembershipRepository.class);
        userRepository = mock(UserRepository.class);
        authorizationService = new ProjectAuthorizationService(membershipRepository, userRepository);
    }

    @Test
    void allowsMemberWithCurrentOrganizationToReadAndWrite() {
        JwtService.UserPrincipal principal = new JwtService.UserPrincipal("user-1", "operator", "org-1");
        User user = new User("user-1", "operator", "operator@example.com", "hash", "org-1");
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserIdAndProjectId("user-1", "project-a"))
            .thenReturn(Optional.of(new ProjectMembership("user-1", "project-a", Role.PROJECT_OPERATOR)));
        UsernamePasswordAuthenticationToken authentication = authenticationFor(principal);

        assertTrue(authorizationService.authorize(authentication, "project-a", ProjectAuthorizationService.Permission.READ).allowed());
        assertTrue(authorizationService.authorize(authentication, "project-a", ProjectAuthorizationService.Permission.WRITE).allowed());
    }

    @Test
    void deniesViewerWriteAndStaleOrganizationClaim() {
        JwtService.UserPrincipal viewer = new JwtService.UserPrincipal("user-1", "viewer", "org-1");
        User user = new User("user-1", "viewer", "viewer@example.com", "hash", "org-1");
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserIdAndProjectId("user-1", "project-a"))
            .thenReturn(Optional.of(new ProjectMembership("user-1", "project-a", Role.VIEWER)));

        var viewerWrite = authorizationService.authorize(
            authenticationFor(viewer), "project-a", ProjectAuthorizationService.Permission.WRITE);
        assertEquals(ProjectAuthorizationService.Failure.FORBIDDEN, viewerWrite.failure());

        JwtService.UserPrincipal stale = new JwtService.UserPrincipal("user-1", "viewer", "other-org");
        var staleDecision = authorizationService.authorize(
            authenticationFor(stale), "project-a", ProjectAuthorizationService.Permission.READ);
        assertEquals(ProjectAuthorizationService.Failure.FORBIDDEN, staleDecision.failure());
    }

    @Test
    void restrictsApiKeyManagementToOrganizationAdmins() {
        JwtService.UserPrincipal principal = new JwtService.UserPrincipal("admin-1", "admin", "org-1");
        User user = new User("admin-1", "admin", "admin@example.com", "hash", "org-1");
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserIdAndProjectId("admin-1", "project-a"))
            .thenReturn(Optional.of(new ProjectMembership("admin-1", "project-a", Role.ORGANIZATION_ADMIN)));

        var decision = authorizationService.authorize(
            authenticationFor(principal), "project-a", ProjectAuthorizationService.Permission.MANAGE_API_KEYS);

        assertTrue(decision.allowed());
    }

    @Test
    void rejectsNonManagementPrincipal() {
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken("api-key", null, List.of());

        var decision = authorizationService.authorize(
            authentication, "project-a", ProjectAuthorizationService.Permission.READ);

        assertEquals(ProjectAuthorizationService.Failure.FORBIDDEN, decision.failure());
    }

    private UsernamePasswordAuthenticationToken authenticationFor(JwtService.UserPrincipal principal) {
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }
}
