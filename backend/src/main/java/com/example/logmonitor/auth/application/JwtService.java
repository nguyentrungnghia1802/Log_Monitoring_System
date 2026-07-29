package com.example.logmonitor.auth.application;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtService(
        @Value("${jwt.secret:SuperSecretJwtSigningKeyWithMinimum256BitsLengthForHmacSha256!}") String secret,
        @Value("${jwt.expiration-ms:86400000}") long expirationMs
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(String userId, String username, String organizationId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
            .subject(userId)
            .claim("username", username)
            .claim("organizationId", organizationId)
            .issuedAt(now)
            .expiration(expiry)
            .signWith(secretKey)
            .compact();
    }

    public Optional<UserPrincipal> validateAndExtractPrincipal(String token) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

            String userId = claims.getSubject();
            String username = claims.get("username", String.class);
            String organizationId = claims.get("organizationId", String.class);

            if (userId == null || username == null) {
                return Optional.empty();
            }

            return Optional.of(new UserPrincipal(userId, username, organizationId));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    public record UserPrincipal(String userId, String username, String organizationId) {}
}
