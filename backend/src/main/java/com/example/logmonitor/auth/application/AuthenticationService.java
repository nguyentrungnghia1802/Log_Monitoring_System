package com.example.logmonitor.auth.application;

import com.example.logmonitor.audit.application.AuditService;
import com.example.logmonitor.auth.config.AuthProperties;
import com.example.logmonitor.auth.domain.AuthSession;
import com.example.logmonitor.auth.domain.AuthSessionRepository;
import com.example.logmonitor.auth.domain.ProjectMembershipRepository;
import com.example.logmonitor.auth.domain.Role;
import com.example.logmonitor.auth.domain.User;
import com.example.logmonitor.auth.domain.UserRepository;
import com.example.logmonitor.common.RateLimiterService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class AuthenticationService {

    private static final int REFRESH_TOKEN_BYTES = 32;
    private static final int MAX_REFRESH_TOKEN_LENGTH = 256;
    private static final String REFRESH_TOKEN_PREFIX = "lm_refresh_";

    private final UserRepository userRepository;
    private final ProjectMembershipRepository membershipRepository;
    private final AuthSessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final RateLimiterService rateLimiterService;
    private final AuthProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();
    private final String dummyPasswordHash;

    public AuthenticationService(
        UserRepository userRepository,
        ProjectMembershipRepository membershipRepository,
        AuthSessionRepository sessionRepository,
        PasswordEncoder passwordEncoder,
        JwtService jwtService,
        AuditService auditService,
        RateLimiterService rateLimiterService,
        AuthProperties properties
    ) {
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
        this.rateLimiterService = rateLimiterService;
        this.properties = properties;
        this.dummyPasswordHash = passwordEncoder.encode("invalid-credential-timing-placeholder");
    }

    public AuthResult login(String identity, String password, String clientAddress) {
        String normalizedIdentity = normalizeIdentity(identity);
        enforceLoginRateLimit(normalizedIdentity, clientAddress);

        Optional<User> candidate = findUser(normalizedIdentity);
        String hash = candidate.map(User::getPasswordHash).orElse(dummyPasswordHash);
        boolean passwordMatches = password != null && passwordEncoder.matches(password, hash);
        if (candidate.isEmpty() || !passwordMatches || !isEligible(candidate.get())) {
            auditFailure(candidate.orElse(null), "LOGIN_FAILED", "Management login failed");
            throw unauthorized("INVALID_CREDENTIALS", "Invalid email or password");
        }

        User user = candidate.get();
        SessionIssue session = createSession(user);
        String accessToken = jwtService.generateAccessToken(
            user.getId(), user.getUsername(), user.getOrganizationId(), session.session().getId());
        auditService.logAction(user.getUsername(), user.getOrganizationId(), null,
            "LOGIN", "AUTH_SESSION", session.session().getId(), "Management login succeeded");
        return result(user, accessToken, session);
    }

    public synchronized AuthResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null
            || rawRefreshToken.isBlank()
            || rawRefreshToken.length() > MAX_REFRESH_TOKEN_LENGTH
            || !rawRefreshToken.startsWith(REFRESH_TOKEN_PREFIX)) {
            throw unauthorized("INVALID_REFRESH_TOKEN", "Refresh session is invalid or expired");
        }

        Instant now = Instant.now();
        AuthSession existing = sessionRepository.findByRefreshTokenHash(hash(rawRefreshToken))
            .filter(session -> session.isActive(now))
            .orElseThrow(() -> unauthorized("INVALID_REFRESH_TOKEN", "Refresh session is invalid or expired"));
        User user = userRepository.findById(existing.getUserId())
            .filter(this::isEligible)
            .filter(candidate -> candidate.getOrganizationId().equals(existing.getOrganizationId()))
            .orElseThrow(() -> unauthorized("INVALID_REFRESH_TOKEN", "Refresh session is invalid or expired"));

        existing.setRevokedAt(now);
        sessionRepository.save(existing);
        SessionIssue replacement = createSession(user);
        String accessToken = jwtService.generateAccessToken(
            user.getId(), user.getUsername(), user.getOrganizationId(), replacement.session().getId());
        auditService.logAction(user.getUsername(), user.getOrganizationId(), null,
            "REFRESH", "AUTH_SESSION", replacement.session().getId(), "Management session refreshed");
        return result(user, accessToken, replacement);
    }

    public void logout(String rawAccessToken) {
        JwtService.ValidatedToken token = jwtService.validateAndExtractToken(rawAccessToken)
            .orElseThrow(() -> unauthorized("INVALID_ACCESS_TOKEN", "Authentication required"));
        if (token.sessionId() == null) {
            throw unauthorized("INVALID_ACCESS_TOKEN", "Authentication required");
        }

        sessionRepository.findById(token.sessionId())
            .filter(session -> session.getUserId().equals(token.principal().userId()))
            .ifPresent(session -> {
                if (session.getRevokedAt() == null) {
                    session.setRevokedAt(Instant.now());
                    sessionRepository.save(session);
                }
            });
        auditService.logAction(token.principal().username(), token.principal().organizationId(), null,
            "LOGOUT", "AUTH_SESSION", token.sessionId(), "Management session ended");
    }

    public UserView currentUser(JwtService.UserPrincipal principal) {
        if (principal == null) {
            throw unauthorized("UNAUTHENTICATED", "Authentication required");
        }
        User user = userRepository.findById(principal.userId())
            .filter(this::isEligible)
            .filter(candidate -> candidate.getOrganizationId().equals(principal.organizationId()))
            .orElseThrow(() -> unauthorized("UNAUTHENTICATED", "Authentication required"));
        return toUserView(user);
    }

    private AuthResult result(User user, String accessToken, SessionIssue session) {
        return new AuthResult(
            accessToken,
            session.rawRefreshToken(),
            jwtService.getAccessExpirationSeconds(),
            session.session().getExpiresAt(),
            toUserView(user)
        );
    }

    private SessionIssue createSession(User user) {
        byte[] tokenBytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(tokenBytes);
        String rawToken = REFRESH_TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        Instant expiresAt = Instant.now().plusSeconds(Math.max(60, properties.getRefreshTokenExpirationSeconds()));
        AuthSession saved = sessionRepository.save(new AuthSession(
            user.getId(), user.getOrganizationId(), hash(rawToken), expiresAt));
        return new SessionIssue(saved, rawToken);
    }

    private UserView toUserView(User user) {
        List<ProjectAccessView> projectAccess = membershipRepository.findByUserId(user.getId()).stream()
            .map(membership -> new ProjectAccessView(membership.getProjectId(), membership.getRole()))
            .toList();
        return new UserView(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getOrganizationId(),
            user.getOrganizationRole(),
            projectAccess
        );
    }

    private Optional<User> findUser(String identity) {
        return userRepository.findByEmailIgnoreCase(identity)
            .or(() -> userRepository.findByUsernameIgnoreCase(identity));
    }

    private boolean isEligible(User user) {
        return user.isActive()
            && user.getId() != null
            && user.getOrganizationId() != null
            && !user.getOrganizationId().isBlank();
    }

    private String normalizeIdentity(String identity) {
        return identity == null ? "" : identity.trim().toLowerCase(Locale.ROOT);
    }

    private void enforceLoginRateLimit(String identity, String clientAddress) {
        int capacity = Math.max(1, properties.getLoginBurstCapacity());
        long windowSeconds = Math.max(1, properties.getLoginWindowSeconds());
        double refillPerSecond = capacity / (double) windowSeconds;
        String addressHash = hash(clientAddress == null ? "unknown" : clientAddress);
        String identityKey = "management-login:identity:" + hash(identity) + ":" + addressHash;
        int addressCapacity = capacity > Integer.MAX_VALUE / 5 ? Integer.MAX_VALUE : capacity * 5;
        boolean identityAllowed = rateLimiterService.tryAcquire(identityKey, capacity, refillPerSecond);
        boolean addressAllowed = rateLimiterService.tryAcquire(
            "management-login:address:" + addressHash,
            addressCapacity,
            addressCapacity / (double) windowSeconds
        );
        if (!identityAllowed || !addressAllowed) {
            auditFailure(null, "LOGIN_RATE_LIMITED", "Management login rate limit exceeded");
            throw new AuthenticationException(
                "LOGIN_RATE_LIMITED",
                AuthenticationException.Kind.RATE_LIMITED,
                "Too many login attempts; retry later"
            );
        }
    }

    private void auditFailure(User user, String action, String summary) {
        auditService.logAction(
            "anonymous",
            user == null || user.getOrganizationId() == null ? "unknown" : user.getOrganizationId(),
            null,
            action,
            "AUTH_SESSION",
            null,
            summary
        );
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private AuthenticationException unauthorized(String code, String message) {
        return new AuthenticationException(code, AuthenticationException.Kind.UNAUTHORIZED, message);
    }

    private record SessionIssue(AuthSession session, String rawRefreshToken) {
    }

    public record AuthResult(
        String accessToken,
        String refreshToken,
        long accessExpiresInSeconds,
        Instant refreshExpiresAt,
        UserView user
    ) {
    }

    public record UserView(
        String id,
        String username,
        String email,
        String organizationId,
        Role organizationRole,
        List<ProjectAccessView> projects
    ) {
    }

    public record ProjectAccessView(String projectId, Role role) {
    }
}
