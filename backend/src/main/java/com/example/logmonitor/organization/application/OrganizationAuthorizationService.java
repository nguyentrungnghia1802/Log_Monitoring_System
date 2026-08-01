package com.example.logmonitor.organization.application;

import com.example.logmonitor.auth.application.JwtService;
import com.example.logmonitor.auth.domain.ProjectMembershipRepository;
import com.example.logmonitor.auth.domain.Role;
import com.example.logmonitor.auth.domain.User;
import com.example.logmonitor.auth.domain.UserRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

@Service
public class OrganizationAuthorizationService {

    private final UserRepository userRepository;
    private final ProjectMembershipRepository membershipRepository;

    public OrganizationAuthorizationService(
        UserRepository userRepository,
        ProjectMembershipRepository membershipRepository
    ) {
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
    }

    public AuthorizationDecision authorize(Authentication authentication, String organizationId, Permission permission) {
        Optional<User> currentUser = resolveCurrentUser(authentication, organizationId);
        if (currentUser.isEmpty()) {
            return authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                ? AuthorizationDecision.unauthenticated()
                : AuthorizationDecision.denied();
        }

        if (permission == Permission.READ || isOrganizationAdmin(currentUser.get())) {
            return AuthorizationDecision.granted();
        }
        return AuthorizationDecision.denied();
    }

    public Optional<User> resolveCurrentUser(Authentication authentication, String organizationId) {
        if (authentication == null
            || !authentication.isAuthenticated()
            || authentication instanceof AnonymousAuthenticationToken
            || !(authentication.getPrincipal() instanceof JwtService.UserPrincipal principal)) {
            return Optional.empty();
        }

        return userRepository.findById(principal.userId())
            .filter(User::isActive)
            .filter(user -> user.getOrganizationId() != null)
            .filter(user -> principal.organizationId() != null)
            .filter(user -> Objects.equals(user.getOrganizationId(), organizationId))
            .filter(user -> Objects.equals(user.getOrganizationId(), principal.organizationId()));
    }

    public boolean isOrganizationAdmin(User user) {
        if (user == null) {
            return false;
        }
        if (user.getOrganizationRole() == Role.ORGANIZATION_ADMIN) {
            return true;
        }
        // Compatibility for users created before organizationRole was persisted.
        return user.getId() != null && membershipRepository.findByUserId(user.getId()).stream()
            .anyMatch(membership -> membership.getRole() == Role.ORGANIZATION_ADMIN);
    }

    public enum Permission {
        READ,
        MANAGE
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
