package com.example.logmonitor.apikey.application;

import com.example.logmonitor.apikey.config.ApiKeyProperties;
import com.example.logmonitor.apikey.domain.ApiKey;
import com.example.logmonitor.apikey.domain.ApiKeyRepository;
import com.example.logmonitor.audit.application.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiKeyServiceTest {

    private ApiKeyRepository repository;
    private AuditService auditService;
    private ApiKeyProperties properties;
    private ApiKeyService service;

    @BeforeEach
    void setUp() {
        repository = mock(ApiKeyRepository.class);
        auditService = mock(AuditService.class);
        properties = new ApiKeyProperties();
        properties.setLastUsedUpdateIntervalSeconds(60);
        service = new ApiKeyService(repository, auditService, properties);
        when(repository.save(any(ApiKey.class))).thenAnswer(invocation -> {
            ApiKey key = invocation.getArgument(0);
            if (key.getId() == null) {
                key.setId("key-" + key.getPublicId());
            }
            return key;
        });
    }

    @Test
    void generatesOneTimeHighEntropySecretAndStoresOnlyItsHash() {
        ApiKeyService.CreateApiKeyResult result = service.createApiKey(
            "project-a", " Production ", "org-1", "user-1");

        ApiKey saved = result.apiKey();
        assertTrue(result.rawApiKey().startsWith("lm_live_ak_"));
        assertTrue(result.rawApiKey().length() > 50);
        assertEquals("Production", saved.getName());
        assertEquals("org-1", saved.getOrganizationId());
        assertEquals("ACTIVE", saved.getStatus());
        assertNotNull(saved.getSecretLast4());
        assertNotEquals(result.rawApiKey(), saved.getHashedSecret());
        assertFalse(saved.getHashedSecret().contains(result.rawApiKey()));
        assertFalse(saved.getHashedSecret().contains(result.rawApiKey().substring(result.rawApiKey().lastIndexOf('_') + 1)));
        verify(auditService).logAction("user-1", "org-1", "project-a", "CREATE", "API_KEY", saved.getId(), "API key created");
    }

    @Test
    void validatesCorrectSecretAndRejectsMalformedUnknownIncorrectAndRevokedKeys() {
        ApiKeyService.CreateApiKeyResult result = service.createApiKey("project-a", "key");
        ApiKey saved = result.apiKey();
        when(repository.findByPublicId(saved.getPublicId())).thenReturn(Optional.of(saved));

        assertTrue(service.validateApiKey(result.rawApiKey()).isPresent());
        assertTrue(service.validateApiKey("not-a-log-monitoring-key").isEmpty());
        assertTrue(service.validateApiKey(result.rawApiKey().replaceFirst(".$", "x")).isEmpty());

        when(repository.findByPublicId("ak_unknown")).thenReturn(Optional.empty());
        when(repository.findByKeyPrefix("ak_unknown")).thenReturn(List.of());
        assertTrue(service.validateApiKey("lm_live_ak_unknown_secret").isEmpty());

        saved.setRevoked(true);
        assertTrue(service.validateApiKey(result.rawApiKey()).isEmpty());
    }

    @Test
    void throttlesLastUsedWritesWithoutSuppressingAuthentication() {
        ApiKeyService.CreateApiKeyResult result = service.createApiKey("project-a", "key");
        ApiKey saved = result.apiKey();
        saved.setLastUsedAt(Instant.now());
        when(repository.findByPublicId(saved.getPublicId())).thenReturn(Optional.of(saved));
        clearInvocations(repository);

        assertTrue(service.validateApiKey(result.rawApiKey()).isPresent());
        verify(repository, never()).save(any(ApiKey.class));
    }

    @Test
    void rotationRevokesOldKeyBeforeIssuingReplacementInSameProject() {
        ApiKey oldKey = new ApiKey("project-a", "production", "ak_old", "$2a$10$old", "last", "org-1", "user-1");
        oldKey.setId("old-key");
        // Use a key created by the service so the replacement is guaranteed to have a valid BCrypt hash.
        ApiKeyService.CreateApiKeyResult created = service.createApiKey("project-a", "production", "org-1", "user-1");
        oldKey = created.apiKey();
        oldKey.setId("old-key");
        when(repository.findByIdAndProjectId("old-key", "project-a")).thenReturn(Optional.of(oldKey));

        Optional<ApiKeyService.RotateApiKeyResult> rotated = service.rotateApiKey(
            "project-a", "old-key", "org-1", "user-1");

        assertTrue(rotated.isPresent());
        assertTrue(oldKey.isRevoked());
        assertNotNull(oldKey.getRevokedAt());
        assertEquals("project-a", rotated.get().apiKey().getProjectId());
        assertNotEquals(oldKey.getPublicId(), rotated.get().apiKey().getPublicId());
        verify(auditService).logAction("user-1", "org-1", "project-a", "ROTATE", "API_KEY", "old-key", "API key rotated; replacement created");
    }

    @Test
    void scopedRevokeCannotTouchKeyFromAnotherProject() {
        when(repository.findByIdAndProjectId("key-1", "foreign-project")).thenReturn(Optional.empty());

        assertFalse(service.revokeApiKey("foreign-project", "key-1", "org-1", "user-1"));
        verify(repository, never()).save(any(ApiKey.class));
    }

    @Test
    void revokeAuditsMetadataWithoutIncludingTheRawSecret() {
        ApiKeyService.CreateApiKeyResult result = service.createApiKey("project-a", "key", "org-1", "user-1");
        when(repository.findByIdAndProjectId(result.apiKey().getId(), "project-a"))
            .thenReturn(Optional.of(result.apiKey()));

        assertTrue(service.revokeApiKey("project-a", result.apiKey().getId(), "org-1", "user-1"));
        verify(auditService).logAction(
            "user-1", "org-1", "project-a", "REVOKE", "API_KEY", result.apiKey().getId(), "API key revoked");
    }
}
