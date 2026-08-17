package com.example.logmonitor.auth.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "auth_sessions")
public class AuthSession {

    @Id
    private String id;

    @Indexed
    private String userId;

    private String organizationId;

    @Indexed(unique = true)
    private String refreshTokenHash;

    private Instant createdAt;

    @Indexed(expireAfter = "0s")
    private Instant expiresAt;

    private Instant revokedAt;

    public AuthSession() {
    }

    public AuthSession(String userId, String organizationId, String refreshTokenHash, Instant expiresAt) {
        this.userId = userId;
        this.organizationId = organizationId;
        this.refreshTokenHash = refreshTokenHash;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt != null && expiresAt.isAfter(now);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public String getRefreshTokenHash() { return refreshTokenHash; }
    public void setRefreshTokenHash(String refreshTokenHash) { this.refreshTokenHash = refreshTokenHash; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
}
