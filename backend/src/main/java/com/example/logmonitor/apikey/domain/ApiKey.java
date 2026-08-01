package com.example.logmonitor.apikey.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "api_keys")
public class ApiKey {

    @Id
    private String id;

    private String projectId;
    private String name;

    /**
     * Stable public selector embedded in the raw key. It is deliberately not
     * a secret; the secret portion is stored only as a password hash.
     */
    @Indexed(unique = true, sparse = true)
    private String publicId;

    /** Kept for backwards-compatible reads of the first API-key schema. */
    @Indexed
    private String keyPrefix;

    @JsonIgnore
    private String hashedSecret;
    private String secretLast4;
    private String organizationId;
    private String createdBy;
    private boolean revoked;
    private Instant createdAt;
    private Instant lastUsedAt;
    private Instant revokedAt;

    public ApiKey() {}

    public ApiKey(
        String projectId,
        String name,
        String publicId,
        String hashedSecret,
        String secretLast4,
        String organizationId,
        String createdBy
    ) {
        this.projectId = projectId;
        this.name = name;
        this.publicId = publicId;
        this.keyPrefix = publicId;
        this.hashedSecret = hashedSecret;
        this.secretLast4 = secretLast4;
        this.organizationId = organizationId;
        this.createdBy = createdBy;
        this.revoked = false;
        this.createdAt = Instant.now();
    }

    /** Compatibility constructor for existing callers and legacy records. */
    public ApiKey(String projectId, String name, String keyPrefix, String hashedSecret) {
        this(projectId, name, keyPrefix, hashedSecret, null, null, null);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPublicId() { return publicId != null ? publicId : keyPrefix; }
    public void setPublicId(String publicId) { this.publicId = publicId; }

    public String getKeyPrefix() { return keyPrefix; }
    public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }

    public String getHashedSecret() { return hashedSecret; }
    public void setHashedSecret(String hashedSecret) { this.hashedSecret = hashedSecret; }

    public String getSecretLast4() { return secretLast4; }
    public void setSecretLast4(String secretLast4) { this.secretLast4 = secretLast4; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public boolean isRevoked() { return revoked; }
    public void setRevoked(boolean revoked) { this.revoked = revoked; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }

    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }

    public String getStatus() { return revoked ? "REVOKED" : "ACTIVE"; }
}
