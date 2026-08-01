package com.example.logmonitor.auth.application;

import com.example.logmonitor.auth.domain.ProjectMembershipRepository;
import com.example.logmonitor.auth.domain.Role;
import com.example.logmonitor.auth.domain.User;
import com.example.logmonitor.auth.domain.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

/**
 * Centralizes the management-user to project authorization decision.
 *
 * <p>Project IDs from a URL are selectors only. A signed JWT and a current
 * membership are both required; the organization claim is checked against the
 * current user record so a stale token cannot cross a tenant boundary.</p>
 */
@Service
public class ProjectAuthorizationService {

    private final ProjectMembershipRepository membershipRepository;
    private final UserRepository userRepository;

    public ProjectAuthorizationService(
        ProjectMembershipRepository membershipRepository,
        UserRepository userRepository
    ) {
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
    }

    public AuthorizationDecision authorize(Authentication authentication, String projectId, Permission permission) {
        if (authentication == null
            || !authentication.isAuthenticated()
            || authentication instanceof AnonymousAuthenticationToken) {
            return AuthorizationDecision.unauthenticated();
        }

        if (!(authentication.getPrincipal() instanceof JwtService.UserPrincipal principal)) {
            return AuthorizationDecision.denied();
        }

        Optional<User> user = userRepository.findById(principal.userId());
        if (user.isEmpty()
            || user.get().getOrganizationId() == null
            || !user.get().isActive()
            || principal.organizationId() == null
            || !Objects.equals(user.get().getOrganizationId(), principal.organizationId())) {
            return AuthorizationDecision.denied();
        }

        if (user.get().getOrganizationRole() == Role.ORGANIZATION_ADMIN) {
            return AuthorizationDecision.granted();
        }

        return membershipRepository.findByUserIdAndProjectId(principal.userId(), projectId)
            .map(membership -> isAllowed(membership.getRole(), permission)
                ? AuthorizationDecision.granted()
                : AuthorizationDecision.denied())
            .orElseGet(AuthorizationDecision::denied);
    }

    private boolean isAllowed(Role role, Permission permission) {
        if (role == null) {
            return false;
        }
        return switch (permission) {
            case READ -> true;
            case WRITE -> role != Role.VIEWER;
            case MANAGE_API_KEYS -> role == Role.ORGANIZATION_ADMIN;
        };
    }

    public enum Permission {
        READ,
        WRITE,
        MANAGE_API_KEYS
    }

    public record AuthorizationDecision(boolean allowed, Failure failure) {
        static AuthorizationDecision granted() {
            return new AuthorizationDecision(true, Failure.NONE);
        }

        static AuthorizationDecision unauthenticated() {
            return new AuthorizationDecision(false, Failure.UNAUTHENTICATED);
        }

        static AuthorizationDecision denied() {
            return new AuthorizationDecision(false, Failure.FORBIDDEN);
        }
    }

    public enum Failure {
        NONE,
        UNAUTHENTICATED,
        FORBIDDEN
    }
}
