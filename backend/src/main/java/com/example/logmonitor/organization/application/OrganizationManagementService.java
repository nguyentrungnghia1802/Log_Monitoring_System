package com.example.logmonitor.organization.application;

import com.example.logmonitor.audit.application.AuditService;
import com.example.logmonitor.auth.domain.ProjectMembership;
import com.example.logmonitor.auth.domain.ProjectMembershipRepository;
import com.example.logmonitor.auth.domain.Role;
import com.example.logmonitor.auth.domain.User;
import com.example.logmonitor.auth.domain.UserRepository;
import com.example.logmonitor.organization.domain.Organization;
import com.example.logmonitor.organization.domain.OrganizationRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class OrganizationManagementService {

    private static final int MAX_NAME_LENGTH = 200;
    private static final int MAX_SETTINGS = 20;
    private static final int MAX_SETTING_KEY_LENGTH = 64;
    private static final int MAX_SETTING_VALUE_LENGTH = 256;
    private static final int MAX_USERNAME_LENGTH = 100;
    private static final int MAX_EMAIL_LENGTH = 254;
    private static final int MAX_PASSWORD_LENGTH = 128;
    private static final int MIN_PASSWORD_LENGTH = 12;

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final ProjectMembershipRepository membershipRepository;
    private final OrganizationAuthorizationService authorizationService;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;

    public OrganizationManagementService(
        OrganizationRepository organizationRepository,
        UserRepository userRepository,
        ProjectMembershipRepository membershipRepository,
        OrganizationAuthorizationService authorizationService,
        AuditService auditService,
        PasswordEncoder passwordEncoder
    ) {
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.passwordEncoder = passwordEncoder;
    }

    public OrganizationView getOrganization(String organizationId) {
        Organization organization = ensureOrganization(organizationId);
        return toOrganizationView(organization);
    }

    public OrganizationView updateOrganization(
        String organizationId,
        String name,
        Map<String, String> settings,
        String actor
    ) {
        Organization organization = ensureOrganization(organizationId);
        String normalizedName = validateName(name);
        organization.setName(normalizedName);
        if (settings != null) {
            organization.setSettings(validateSettings(settings));
        }
        organization.setUpdatedAt(Instant.now());
        Organization saved = organizationRepository.save(organization);
        auditService.logAction(actor, organizationId, null, "UPDATE", "ORGANIZATION", organizationId,
            "Organization settings updated");
        return toOrganizationView(saved);
    }

    public List<MemberView> listMembers(String organizationId) {
        return userRepository.findByOrganizationIdOrderByCreatedAtAsc(organizationId).stream()
            .map(this::toMemberView)
            .toList();
    }

    public MemberView createMember(
        String organizationId,
        String username,
        String email,
        String password,
        Role role,
        String actor
    ) {
        String normalizedUsername = validateUsername(username);
        String normalizedEmail = validateEmail(email);
        validatePassword(password);
        if (role == null) {
            throw validation("ROLE_REQUIRED", "Role is required");
        }
        if (userRepository.findByUsername(normalizedUsername).isPresent()) {
            throw conflict("USERNAME_EXISTS", "Username already exists");
        }
        if (userRepository.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            throw conflict("EMAIL_EXISTS", "Email already exists");
        }

        User user = new User(
            null,
            normalizedUsername,
            normalizedEmail,
            passwordEncoder.encode(password),
            organizationId
        );
        user.setOrganizationRole(role);
        user.setActive(true);
        user.setUpdatedAt(Instant.now());
        User saved = userRepository.save(user);
        auditService.logAction(actor, organizationId, null, "CREATE", "ORGANIZATION_MEMBERSHIP", saved.getId(),
            "Organization membership created");
        return toMemberView(saved);
    }

    public MemberView updateMember(
        String organizationId,
        String userId,
        Role requestedRole,
        Boolean requestedActive,
        String actor
    ) {
        User user = findMember(organizationId, userId);
        Role currentRole = effectiveRole(user);
        boolean currentActive = user.isActive();
        Role nextRole = requestedRole == null ? currentRole : requestedRole;
        boolean nextActive = requestedActive == null ? currentActive : requestedActive;

        if (nextRole == null) {
            throw validation("ROLE_REQUIRED", "Role is required");
        }
        if (authorizationService.isOrganizationAdmin(user)
            && (!nextActive || nextRole != Role.ORGANIZATION_ADMIN)
            && countActiveOrganizationAdmins(organizationId) <= 1) {
            throw conflict("FINAL_ORGANIZATION_ADMIN", "The organization must retain an active administrator");
        }

        if (currentRole == Role.ORGANIZATION_ADMIN && nextRole != Role.ORGANIZATION_ADMIN) {
            demoteLegacyProjectAdminMemberships(userId, nextRole);
        }
        user.setOrganizationRole(nextRole);
        user.setActive(nextActive);
        user.setUpdatedAt(Instant.now());
        User saved = userRepository.save(user);

        if (currentRole != nextRole) {
            auditService.logAction(actor, organizationId, null, "ROLE_CHANGED", "ORGANIZATION_MEMBERSHIP",
                userId, "Organization membership role changed");
        }
        if (currentActive != nextActive) {
            auditService.logAction(actor, organizationId, null, nextActive ? "ENABLED" : "DISABLED",
                "ORGANIZATION_MEMBERSHIP", userId, "Organization membership status changed");
        }
        return toMemberView(saved);
    }

    public void removeMember(String organizationId, String userId, String actor) {
        User user = findMember(organizationId, userId);
        if (authorizationService.isOrganizationAdmin(user) && countActiveOrganizationAdmins(organizationId) <= 1) {
            throw conflict("FINAL_ORGANIZATION_ADMIN", "The organization must retain an active administrator");
        }

        auditService.logAction(actor, organizationId, null, "REMOVED", "ORGANIZATION_MEMBERSHIP", userId,
            "Organization membership removed");
        membershipRepository.deleteAll(membershipRepository.findByUserId(userId));
        user.setOrganizationId(null);
        user.setOrganizationRole(null);
        user.setActive(false);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
    }

    private Organization ensureOrganization(String organizationId) {
        if (organizationId == null || organizationId.isBlank()) {
            throw validation("ORGANIZATION_REQUIRED", "Organization is required");
        }
        return organizationRepository.findById(organizationId).orElseGet(() -> {
            String normalizedId = organizationId.trim();
            String slug = normalizedId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
            slug = slug.replaceAll("^-|-$", "");
            if (slug.isBlank()) {
                slug = "organization-" + normalizedId.hashCode();
            }
            return organizationRepository.save(new Organization(
                normalizedId,
                slug,
                "Organization " + normalizedId
            ));
        });
    }

    private OrganizationView toOrganizationView(Organization organization) {
        return new OrganizationView(
            organization.getId(),
            organization.getSlug(),
            organization.getName(),
            organization.isActive(),
            new LinkedHashMap<>(organization.getSettings()),
            userRepository.findByOrganizationIdOrderByCreatedAtAsc(organization.getId()).size(),
            organization.getCreatedAt(),
            organization.getUpdatedAt()
        );
    }

    private MemberView toMemberView(User user) {
        return new MemberView(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            effectiveRole(user),
            user.isActive(),
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }

    private User findMember(String organizationId, String userId) {
        return userRepository.findById(userId)
            .filter(user -> organizationId.equals(user.getOrganizationId()))
            .orElseThrow(() -> new OrganizationManagementException(
                "MEMBER_NOT_FOUND",
                OrganizationManagementException.Kind.NOT_FOUND,
                "Organization member does not exist"
            ));
    }

    private Role effectiveRole(User user) {
        if (user.getOrganizationRole() != null) {
            return user.getOrganizationRole();
        }
        List<ProjectMembership> memberships = user.getId() == null
            ? List.of()
            : membershipRepository.findByUserId(user.getId());
        if (memberships.stream().anyMatch(membership -> membership.getRole() == Role.ORGANIZATION_ADMIN)) {
            return Role.ORGANIZATION_ADMIN;
        }
        if (memberships.stream().anyMatch(membership -> membership.getRole() == Role.PROJECT_OPERATOR)) {
            return Role.PROJECT_OPERATOR;
        }
        return memberships.stream().anyMatch(membership -> membership.getRole() == Role.VIEWER)
            ? Role.VIEWER
            : null;
    }

    private long countActiveOrganizationAdmins(String organizationId) {
        return userRepository.findByOrganizationIdOrderByCreatedAtAsc(organizationId).stream()
            .filter(User::isActive)
            .filter(authorizationService::isOrganizationAdmin)
            .count();
    }

    private void demoteLegacyProjectAdminMemberships(String userId, Role nextRole) {
        membershipRepository.findByUserId(userId).stream()
            .filter(membership -> membership.getRole() == Role.ORGANIZATION_ADMIN)
            .forEach(membership -> {
                membership.setRole(nextRole);
                membershipRepository.save(membership);
            });
    }

    private String validateName(String name) {
        if (name == null || name.isBlank() || name.trim().length() > MAX_NAME_LENGTH) {
            throw validation("INVALID_ORGANIZATION_NAME", "Organization name is required and must be at most 200 characters");
        }
        return name.trim();
    }

    private Map<String, String> validateSettings(Map<String, String> settings) {
        if (settings.size() > MAX_SETTINGS) {
            throw validation("INVALID_ORGANIZATION_SETTINGS", "Organization settings contain too many entries");
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        settings.forEach((key, value) -> {
            if (key == null || key.isBlank() || key.trim().length() > MAX_SETTING_KEY_LENGTH
                || value == null || value.length() > MAX_SETTING_VALUE_LENGTH) {
                throw validation("INVALID_ORGANIZATION_SETTINGS", "Organization settings contain an invalid entry");
            }
            normalized.put(key.trim(), value);
        });
        return normalized;
    }

    private String validateUsername(String username) {
        if (username == null || username.isBlank() || username.trim().length() > MAX_USERNAME_LENGTH) {
            throw validation("INVALID_USERNAME", "Username is required and must be at most 100 characters");
        }
        return username.trim();
    }

    private String validateEmail(String email) {
        if (email == null || email.isBlank() || email.trim().length() > MAX_EMAIL_LENGTH || !email.contains("@")) {
            throw validation("INVALID_EMAIL", "A valid email is required");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH || password.length() > MAX_PASSWORD_LENGTH) {
            throw validation("INVALID_PASSWORD", "Password must contain 12 to 128 characters");
        }
    }

    private OrganizationManagementException validation(String code, String message) {
        return new OrganizationManagementException(code, OrganizationManagementException.Kind.VALIDATION, message);
    }

    private OrganizationManagementException conflict(String code, String message) {
        return new OrganizationManagementException(code, OrganizationManagementException.Kind.CONFLICT, message);
    }

    public record OrganizationView(
        String id,
        String slug,
        String name,
        boolean active,
        Map<String, String> settings,
        int memberCount,
        Instant createdAt,
        Instant updatedAt
    ) {}

    public record MemberView(
        String id,
        String username,
        String email,
        Role role,
        boolean active,
        Instant createdAt,
        Instant updatedAt
    ) {}
}
