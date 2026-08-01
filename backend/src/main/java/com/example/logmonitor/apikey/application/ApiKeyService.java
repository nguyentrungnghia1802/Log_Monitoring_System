package com.example.logmonitor.apikey.application;

import com.example.logmonitor.apikey.domain.ApiKey;
import com.example.logmonitor.apikey.domain.ApiKeyRepository;
import com.example.logmonitor.audit.application.AuditService;
import com.example.logmonitor.apikey.config.ApiKeyProperties;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
public class ApiKeyService {

    private static final String KEY_PREFIX_HEADER = "lm_live_";
    private static final int PUBLIC_ID_BYTES = 12;
    private static final int SECRET_BYTES = 32;
    private static final int PUBLIC_ID_LENGTH = 3 + 16; // "ak_" plus unpadded base64url for 12 bytes
    private static final int SECRET_LENGTH = 43; // unpadded base64url for 32 bytes
    private static final int MAX_RAW_KEY_LENGTH = 512;
    private final ApiKeyRepository apiKeyRepository;
    private final AuditService auditService;
    private final ApiKeyProperties properties;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiKeyService(
        ApiKeyRepository apiKeyRepository,
        AuditService auditService,
        ApiKeyProperties properties
    ) {
        this.apiKeyRepository = apiKeyRepository;
        this.auditService = auditService;
        this.properties = properties;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    public record CreateApiKeyResult(ApiKey apiKey, String rawApiKey) {}
    public record RotateApiKeyResult(ApiKey previousApiKey, ApiKey apiKey, String rawApiKey) {}

    public CreateApiKeyResult createApiKey(String projectId, String name) {
        return createApiKey(projectId, name, null, "system");
    }

    public CreateApiKeyResult createApiKey(String projectId, String name, String organizationId, String actor) {
        requireValue(projectId, "projectId");
        requireValue(name, "name");

        GeneratedSecret generated = generateSecret();
        ApiKey apiKey = new ApiKey(
            projectId,
            name.trim(),
            generated.publicId(),
            passwordEncoder.encode(generated.secret()),
            lastFour(generated.secret()),
            organizationId,
            actor
        );
        ApiKey saved = apiKeyRepository.save(apiKey);
        auditService.logAction(
            safeActor(actor),
            safeOrganization(organizationId),
            projectId,
            "CREATE",
            "API_KEY",
            saved.getId(),
            "API key created"
        );

        return new CreateApiKeyResult(saved, generated.rawKey());
    }

    public Optional<ApiKey> validateApiKey(String rawApiKey) {
        if (rawApiKey == null
            || rawApiKey.length() > MAX_RAW_KEY_LENGTH
            || !rawApiKey.startsWith(KEY_PREFIX_HEADER)) {
            return Optional.empty();
        }

        String payload = rawApiKey.substring(KEY_PREFIX_HEADER.length());
        int underscoreIdx = payload.startsWith("ak_") && payload.length() > PUBLIC_ID_LENGTH
            && payload.charAt(PUBLIC_ID_LENGTH) == '_'
            ? PUBLIC_ID_LENGTH
            : payload.indexOf('_');
        if (underscoreIdx <= 0 || underscoreIdx >= payload.length() - 1) {
            return Optional.empty();
        }

        String publicId = payload.substring(0, underscoreIdx);
        String secret = payload.substring(underscoreIdx + 1);
        if (publicId.startsWith("ak_") && secret.length() != SECRET_LENGTH) {
            return Optional.empty();
        }

        List<ApiKey> candidates = apiKeyRepository.findByPublicId(publicId)
            .map(List::of)
            .orElseGet(() -> apiKeyRepository.findByKeyPrefix(publicId));
        for (ApiKey candidate : candidates) {
            if (!candidate.isRevoked()
                && candidate.getHashedSecret() != null
                && passwordEncoder.matches(secret, candidate.getHashedSecret())) {
                updateLastUsedIfDue(candidate);
                return Optional.of(candidate);
            }
        }

        return Optional.empty();
    }

    public List<ApiKey> getApiKeys(String projectId) {
        return apiKeyRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
    }

    public Optional<ApiKey> findApiKey(String projectId, String keyId) {
        return apiKeyRepository.findByIdAndProjectId(keyId, projectId);
    }

    public Optional<RotateApiKeyResult> rotateApiKey(String projectId, String keyId, String organizationId, String actor) {
        Optional<ApiKey> existing = apiKeyRepository.findByIdAndProjectId(keyId, projectId);
        if (existing.isEmpty() || existing.get().isRevoked()) {
            return Optional.empty();
        }

        ApiKey previous = existing.get();
        Instant now = Instant.now();
        previous.setRevoked(true);
        previous.setRevokedAt(now);
        apiKeyRepository.save(previous);

        CreateApiKeyResult replacement = createApiKey(projectId, previous.getName(), organizationId, actor);
        auditService.logAction(
            safeActor(actor),
            safeOrganization(organizationId),
            projectId,
            "ROTATE",
            "API_KEY",
            previous.getId(),
            "API key rotated; replacement created"
        );
        return Optional.of(new RotateApiKeyResult(previous, replacement.apiKey(), replacement.rawApiKey()));
    }

    public boolean revokeApiKey(String projectId, String keyId, String organizationId, String actor) {
        return apiKeyRepository.findByIdAndProjectId(keyId, projectId)
            .map(key -> revoke(key, organizationId, actor))
            .orElse(false);
    }

    /** Compatibility path for existing internal callers; new callers must provide project scope. */
    public void revokeApiKey(String keyId) {
        apiKeyRepository.findById(keyId).ifPresent(key -> {
            revoke(key, key.getOrganizationId(), "system");
        });
    }

    private boolean revoke(ApiKey key, String organizationId, String actor) {
        if (!key.isRevoked()) {
            key.setRevoked(true);
            key.setRevokedAt(Instant.now());
            apiKeyRepository.save(key);
            auditService.logAction(
                safeActor(actor),
                safeOrganization(organizationId),
                key.getProjectId(),
                "REVOKE",
                "API_KEY",
                key.getId(),
                "API key revoked"
            );
        }
        return true;
    }

    private void updateLastUsedIfDue(ApiKey candidate) {
        Instant now = Instant.now();
        Instant lastUsedAt = candidate.getLastUsedAt();
        long intervalSeconds = Math.max(1, properties.getLastUsedUpdateIntervalSeconds());
        if (lastUsedAt == null || lastUsedAt.plusSeconds(intervalSeconds).isBefore(now)) {
            candidate.setLastUsedAt(now);
            apiKeyRepository.save(candidate);
        }
    }

    private GeneratedSecret generateSecret() {
        byte[] publicIdBytes = new byte[PUBLIC_ID_BYTES];
        byte[] secretBytes = new byte[SECRET_BYTES];
        secureRandom.nextBytes(publicIdBytes);
        secureRandom.nextBytes(secretBytes);
        String publicId = "ak_" + encode(publicIdBytes);
        String secret = encode(secretBytes);
        return new GeneratedSecret(publicId, secret, KEY_PREFIX_HEADER + publicId + "_" + secret);
    }

    private String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String lastFour(String secret) {
        return secret.substring(Math.max(0, secret.length() - 4));
    }

    private void requireValue(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private String safeActor(String actor) {
        return actor == null || actor.isBlank() ? "system" : actor;
    }

    private String safeOrganization(String organizationId) {
        return organizationId == null || organizationId.isBlank() ? "unknown" : organizationId;
    }

    private record GeneratedSecret(String publicId, String secret, String rawKey) {}
}
