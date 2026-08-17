package com.example.logmonitor.auth.application;

import com.example.logmonitor.auth.domain.AuthSessionRepository;
import com.example.logmonitor.auth.domain.User;
import com.example.logmonitor.auth.domain.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expirationMs;
    private final UserRepository userRepository;
    private final AuthSessionRepository sessionRepository;

    public JwtService(
        @Value("${jwt.secret:SuperSecretJwtSigningKeyWithMinimum256BitsLengthForHmacSha256!}") String secret,
        @Value("${jwt.expiration-ms:900000}") long expirationMs,
        UserRepository userRepository,
        AuthSessionRepository sessionRepository
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
    }

    /** Compatibility helper for tests and internal fixtures that do not model a browser session. */
    public String generateToken(String userId, String username, String organizationId) {
        return generateAccessToken(userId, username, organizationId, null);
    }

    public String generateAccessToken(String userId, String username, String organizationId, String sessionId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        var builder = Jwts.builder()
            .subject(userId)
            .id(UUID.randomUUID().toString())
            .claim("username", username)
            .claim("organizationId", organizationId)
            .claim("type", "access")
            .issuedAt(now)
            .expiration(expiry);
        if (sessionId != null) {
            builder.claim("sessionId", sessionId);
        }
        return builder.signWith(secretKey).compact();
    }

    public Optional<UserPrincipal> validateAndExtractPrincipal(String token) {
        return validateAndExtractToken(token).map(ValidatedToken::principal);
    }

    public Optional<ValidatedToken> validateAndExtractToken(String token) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

            String userId = claims.getSubject();
            String organizationId = claims.get("organizationId", String.class);
            String tokenType = claims.get("type", String.class);
            String sessionId = claims.get("sessionId", String.class);
            String jwtId = claims.getId();
            Date expiration = claims.getExpiration();

            if (userId == null
                || organizationId == null
                || jwtId == null
                || expiration == null
                || (tokenType != null && !"access".equals(tokenType))) {
                return Optional.empty();
            }

            Optional<User> user = userRepository.findById(userId)
                .filter(User::isActive)
                .filter(candidate -> candidate.getOrganizationId() != null)
                .filter(candidate -> Objects.equals(candidate.getOrganizationId(), organizationId));
            if (user.isEmpty() || !isSessionActive(sessionId, user.get(), Instant.now())) {
                return Optional.empty();
            }

            UserPrincipal principal = new UserPrincipal(
                user.get().getId(), user.get().getUsername(), user.get().getOrganizationId());
            return Optional.of(new ValidatedToken(
                principal, jwtId, sessionId, expiration.toInstant()));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    public long getAccessExpirationSeconds() {
        return Math.max(1, expirationMs / 1000);
    }

    private boolean isSessionActive(String sessionId, User user, Instant now) {
        if (sessionId == null) {
            return true;
        }
        return sessionRepository.findById(sessionId)
            .filter(session -> session.isActive(now))
            .filter(session -> Objects.equals(session.getUserId(), user.getId()))
            .filter(session -> Objects.equals(session.getOrganizationId(), user.getOrganizationId()))
            .isPresent();
    }

    public record ValidatedToken(
        UserPrincipal principal,
        String jwtId,
        String sessionId,
        Instant expiresAt
    ) {
    }

    public record UserPrincipal(String userId, String username, String organizationId) implements Principal {
        @Override
        public String getName() {
            return userId;
        }
    }
}
