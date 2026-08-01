package com.example.logmonitor.apikey.application;

import com.example.logmonitor.apikey.domain.ApiKey;
import com.example.logmonitor.apikey.domain.ApiKeyRepository;
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
    private final ApiKeyRepository apiKeyRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiKeyService(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    public record CreateApiKeyResult(ApiKey apiKey, String rawApiKey) {}

    public CreateApiKeyResult createApiKey(String projectId, String name) {
        byte[] prefixBytes = new byte[6];
        byte[] secretBytes = new byte[24];
        secureRandom.nextBytes(prefixBytes);
        secureRandom.nextBytes(secretBytes);

        String prefix = Base64.getUrlEncoder().withoutPadding().encodeToString(prefixBytes).substring(0, 8);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);

        String rawKey = KEY_PREFIX_HEADER + prefix + "_" + secret;
        String hashedSecret = passwordEncoder.encode(secret);

        ApiKey apiKey = new ApiKey(projectId, name, prefix, hashedSecret);
        ApiKey saved = apiKeyRepository.save(apiKey);

        return new CreateApiKeyResult(saved, rawKey);
    }

    public Optional<ApiKey> validateApiKey(String rawApiKey) {
        if (rawApiKey == null || !rawApiKey.startsWith(KEY_PREFIX_HEADER)) {
            return Optional.empty();
        }

        String payload = rawApiKey.substring(KEY_PREFIX_HEADER.length());
        int underscoreIdx = payload.indexOf('_');
        if (underscoreIdx <= 0 || underscoreIdx >= payload.length() - 1) {
            return Optional.empty();
        }

        String prefix = payload.substring(0, underscoreIdx);
        String secret = payload.substring(underscoreIdx + 1);

        List<ApiKey> candidates = apiKeyRepository.findByKeyPrefix(prefix);
        for (ApiKey candidate : candidates) {
            if (!candidate.isRevoked() && passwordEncoder.matches(secret, candidate.getHashedSecret())) {
                candidate.setLastUsedAt(Instant.now());
                apiKeyRepository.save(candidate);
                return Optional.of(candidate);
            }
        }

        return Optional.empty();
    }

    public List<ApiKey> getApiKeys(String projectId) {
        return apiKeyRepository.findByProjectId(projectId);
    }

    public void revokeApiKey(String keyId) {
        apiKeyRepository.findById(keyId).ifPresent(key -> {
            key.setRevoked(true);
            apiKeyRepository.save(key);
        });
    }
}
