package com.example.logmonitor.apikey.api;

import com.example.logmonitor.apikey.application.ApiKeyService;
import com.example.logmonitor.apikey.domain.ApiKey;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.example.logmonitor.auth.application.JwtService;
import com.example.logmonitor.auth.application.ProjectAuthorizationService;
import com.example.logmonitor.auth.application.ProjectAuthorizationService.AuthorizationDecision;
import com.example.logmonitor.auth.application.ProjectAuthorizationService.Failure;
import com.example.logmonitor.auth.application.ProjectAuthorizationService.Permission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/api-keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;
    private final ProjectAuthorizationService authorizationService;

    public ApiKeyController(
        ApiKeyService apiKeyService,
        ProjectAuthorizationService authorizationService
    ) {
        this.apiKeyService = apiKeyService;
        this.authorizationService = authorizationService;
    }

    public record CreateApiKeyRequest(
        @NotBlank @Size(max = 100) String name
    ) {}

    /**
     * The raw value is intentionally nullable and is populated only on the
     * create/rotate response. List and revoke responses never contain it.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ApiKeyResponse(
        String id,
        String projectId,
        String name,
        String publicId,
        String secretLast4,
        String status,
        Instant createdAt,
        Instant lastUsedAt,
        Instant revokedAt,
        String rawApiKey
    ) {
        static ApiKeyResponse metadata(ApiKey key) {
            return from(key, null);
        }

        static ApiKeyResponse withRaw(ApiKey key, String rawApiKey) {
            return from(key, rawApiKey);
        }

        private static ApiKeyResponse from(ApiKey key, String rawApiKey) {
            return new ApiKeyResponse(
                key.getId(),
                key.getProjectId(),
                key.getName(),
                key.getPublicId(),
                key.getSecretLast4(),
                key.getStatus(),
                key.getCreatedAt(),
                key.getLastUsedAt(),
                key.getRevokedAt(),
                rawApiKey
            );
        }
    }

    @GetMapping
    public ResponseEntity<?> list(@PathVariable String projectId) {
        ResponseEntity<?> denied = requireApiKeyManagement(projectId);
        if (denied != null) {
            return denied;
        }
        List<ApiKeyResponse> response = apiKeyService.getApiKeys(projectId).stream()
            .map(ApiKeyResponse::metadata)
            .toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<?> create(
        @PathVariable String projectId,
        @Valid @RequestBody CreateApiKeyRequest request
    ) {
        ResponseEntity<?> denied = requireApiKeyManagement(projectId);
        if (denied != null) {
            return denied;
        }
        JwtService.UserPrincipal principal = currentPrincipal();
        if (principal == null) {
            return unauthorized();
        }

        ApiKeyService.CreateApiKeyResult result = apiKeyService.createApiKey(
            projectId,
            request.name(),
            principal.organizationId(),
            principal.userId()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiKeyResponse.withRaw(result.apiKey(), result.rawApiKey()));
    }

    @PostMapping("/{keyId}/rotate")
    public ResponseEntity<?> rotate(
        @PathVariable String projectId,
        @PathVariable String keyId
    ) {
        ResponseEntity<?> denied = requireApiKeyManagement(projectId);
        if (denied != null) {
            return denied;
        }
        JwtService.UserPrincipal principal = currentPrincipal();
        if (principal == null) {
            return unauthorized();
        }

        return apiKeyService.rotateApiKey(projectId, keyId, principal.organizationId(), principal.userId())
            .<ResponseEntity<?>>map(result -> ResponseEntity.ok(
                ApiKeyResponse.withRaw(result.apiKey(), result.rawApiKey())))
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "code", "API_KEY_NOT_FOUND",
                "message", "API key does not exist or is already revoked"
            )));
    }

    @DeleteMapping("/{keyId}")
    public ResponseEntity<?> revoke(
        @PathVariable String projectId,
        @PathVariable String keyId
    ) {
        ResponseEntity<?> denied = requireApiKeyManagement(projectId);
        if (denied != null) {
            return denied;
        }
        JwtService.UserPrincipal principal = currentPrincipal();
        if (principal == null) {
            return unauthorized();
        }

        boolean revoked = apiKeyService.revokeApiKey(
            projectId,
            keyId,
            principal.organizationId(),
            principal.userId()
        );
        return revoked
            ? ResponseEntity.noContent().build()
            : ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "code", "API_KEY_NOT_FOUND",
                "message", "API key does not exist"
            ));
    }

    private ResponseEntity<?> requireApiKeyManagement(String projectId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        AuthorizationDecision decision = authorizationService.authorize(
            authentication,
            projectId,
            Permission.MANAGE_API_KEYS
        );
        if (decision.allowed()) {
            return null;
        }
        if (decision.failure() == Failure.UNAUTHENTICATED) {
            return unauthorized();
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
            "code", "FORBIDDEN",
            "message", "API key management requires organization administrator access"
        ));
    }

    private JwtService.UserPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof JwtService.UserPrincipal principal) {
            return principal;
        }
        return null;
    }

    private ResponseEntity<Map<String, String>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
            "code", "UNAUTHENTICATED",
            "message", "Authentication required"
        ));
    }
}
