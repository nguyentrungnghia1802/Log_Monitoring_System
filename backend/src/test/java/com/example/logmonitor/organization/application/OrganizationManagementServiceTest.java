package com.example.logmonitor.organization.application;

import com.example.logmonitor.audit.application.AuditService;
import com.example.logmonitor.auth.domain.ProjectMembershipRepository;
import com.example.logmonitor.auth.domain.Role;
import com.example.logmonitor.auth.domain.User;
import com.example.logmonitor.auth.domain.UserRepository;
import com.example.logmonitor.organization.domain.Organization;
import com.example.logmonitor.organization.domain.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrganizationManagementServiceTest {

    private OrganizationRepository organizationRepository;
    private UserRepository userRepository;
    private ProjectMembershipRepository membershipRepository;
    private OrganizationAuthorizationService authorizationService;
    private AuditService auditService;
    private PasswordEncoder passwordEncoder;
    private OrganizationManagementService service;

    @BeforeEach
    void setUp() {
        organizationRepository = mock(OrganizationRepository.class);
        userRepository = mock(UserRepository.class);
        membershipRepository = mock(ProjectMembershipRepository.class);
        authorizationService = mock(OrganizationAuthorizationService.class);
        auditService = mock(AuditService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new OrganizationManagementService(
            organizationRepository,
            userRepository,
            membershipRepository,
            authorizationService,
            auditService,
            passwordEncoder
        );
    }

    @Test
    void createsHashedMemberAndAuditsMembershipCreation() {
        Organization organization = new Organization("org-1", "acme", "Acme");
        User saved = new User("user-2", "new-user", "new@example.com", "encoded", "org-1");
        saved.setOrganizationRole(Role.VIEWER);
        when(organizationRepository.findById("org-1")).thenReturn(Optional.of(organization));
        when(userRepository.findByUsername("new-user")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("long-enough-password")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenReturn(saved);

        var result = service.createMember(
            "org-1", "new-user", "new@example.com", "long-enough-password", Role.VIEWER, "admin-1");

        assertEquals(Role.VIEWER, result.role());
        verify(passwordEncoder).encode("long-enough-password");
        verify(auditService).logAction(
            "admin-1", "org-1", null, "CREATE", "ORGANIZATION_MEMBERSHIP", "user-2",
            "Organization membership created");
    }

    @Test
    void refusesToRemoveTheFinalActiveOrganizationAdmin() {
        Organization organization = new Organization("org-1", "acme", "Acme");
        User admin = new User("admin-1", "admin", "admin@example.com", "hash", "org-1");
        admin.setOrganizationRole(Role.ORGANIZATION_ADMIN);
        when(organizationRepository.findById("org-1")).thenReturn(Optional.of(organization));
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(admin));
        when(userRepository.findByOrganizationIdOrderByCreatedAtAsc("org-1")).thenReturn(List.of(admin));
        when(authorizationService.isOrganizationAdmin(admin)).thenReturn(true);

        OrganizationManagementException exception = assertThrows(
            OrganizationManagementException.class,
            () -> service.updateMember("org-1", "admin-1", Role.VIEWER, true, "admin-1")
        );

        assertEquals("FINAL_ORGANIZATION_ADMIN", exception.getCode());
        verify(userRepository, never()).save(any(User.class));
    }
}
