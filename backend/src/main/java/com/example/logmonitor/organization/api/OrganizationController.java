package com.example.logmonitor.organization.api;

import com.example.logmonitor.auth.application.JwtService;
import com.example.logmonitor.auth.domain.Role;
import com.example.logmonitor.organization.application.OrganizationAuthorizationService;
import com.example.logmonitor.organization.application.OrganizationManagementException;
import com.example.logmonitor.organization.application.OrganizationManagementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/organizations/current")
public class OrganizationController {

    private final OrganizationAuthorizationService authorizationService;
    private final OrganizationManagementService managementService;

    public OrganizationController(
        OrganizationAuthorizationService authorizationService,
        OrganizationManagementService managementService
    ) {
        this.authorizationService = authorizationService;
        this.managementService = managementService;
    }

    public record UpdateOrganizationRequest(
        @NotBlank @Size(max = 200) String name,
        Map<String, String> settings
    ) {}

    public record CreateMemberRequest(
        @NotBlank @Size(max = 100) String username,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 12, max = 128) String password,
        @NotNull Role role
    ) {}

    public record UpdateMemberRequest(Role role, Boolean active) {}

    @GetMapping
    public ResponseEntity<?> getOrganization() {
        String organizationId = currentOrganizationId();
        ResponseEntity<?> denied = require(organizationId, OrganizationAuthorizationService.Permission.READ);
        if (denied != null) {
            return denied;
        }
        return ResponseEntity.ok(managementService.getOrganization(organizationId));
    }

    @PatchMapping
    public ResponseEntity<?> updateOrganization(@Valid @RequestBody UpdateOrganizationRequest request) {
        String organizationId = currentOrganizationId();
        ResponseEntity<?> denied = require(organizationId, OrganizationAuthorizationService.Permission.MANAGE);
        if (denied != null) {
            return denied;
        }
        return handleManagement(() -> ResponseEntity.ok(managementService.updateOrganization(
            organizationId,
            request.name(),
            request.settings(),
            currentUserId()
        )));
    }

    @GetMapping("/users")
    public ResponseEntity<?> listMembers() {
        String organizationId = currentOrganizationId();
        ResponseEntity<?> denied = require(organizationId, OrganizationAuthorizationService.Permission.READ);
        if (denied != null) {
            return denied;
        }
        return ResponseEntity.ok(managementService.listMembers(organizationId));
    }

    @PostMapping("/users")
    public ResponseEntity<?> createMember(@Valid @RequestBody CreateMemberRequest request) {
        String organizationId = currentOrganizationId();
        ResponseEntity<?> denied = require(organizationId, OrganizationAuthorizationService.Permission.MANAGE);
        if (denied != null) {
            return denied;
        }
        return handleManagement(() -> ResponseEntity.status(HttpStatus.CREATED).body(
            managementService.createMember(
                organizationId,
                request.username(),
                request.email(),
                request.password(),
                request.role(),
                currentUserId()
            )
        ));
    }

    @PatchMapping("/users/{userId}")
    public ResponseEntity<?> updateMember(
        @PathVariable String userId,
        @Valid @RequestBody UpdateMemberRequest request
    ) {
        String organizationId = currentOrganizationId();
        ResponseEntity<?> denied = require(organizationId, OrganizationAuthorizationService.Permission.MANAGE);
        if (denied != null) {
            return denied;
        }
        if (request.role() == null && request.active() == null) {
            return error(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Role or active status is required");
        }
        return handleManagement(() -> ResponseEntity.ok(managementService.updateMember(
            organizationId,
            userId,
            request.role(),
            request.active(),
            currentUserId()
        )));
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<?> removeMember(@PathVariable String userId) {
        String organizationId = currentOrganizationId();
        ResponseEntity<?> denied = require(organizationId, OrganizationAuthorizationService.Permission.MANAGE);
        if (denied != null) {
            return denied;
        }
        return handleManagement(() -> {
            managementService.removeMember(organizationId, userId, currentUserId());
            return ResponseEntity.noContent().build();
        });
    }

    private ResponseEntity<?> require(String organizationId, OrganizationAuthorizationService.Permission permission) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        var decision = authorizationService.authorize(authentication, organizationId, permission);
        if (decision.allowed()) {
            return null;
        }
        return decision.failure() == OrganizationAuthorizationService.Failure.UNAUTHENTICATED
            ? error(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication required")
            : error(HttpStatus.FORBIDDEN, "FORBIDDEN", "Organization access denied");
    }

    private <T> ResponseEntity<?> handleManagement(ManagementOperation<T> operation) {
        try {
            return operation.execute();
        } catch (OrganizationManagementException exception) {
            HttpStatus status = switch (exception.getKind()) {
                case VALIDATION -> HttpStatus.UNPROCESSABLE_ENTITY;
                case NOT_FOUND -> HttpStatus.NOT_FOUND;
                case CONFLICT -> HttpStatus.CONFLICT;
            };
            return error(status, exception.getCode(), exception.getMessage());
        }
    }

    private String currentOrganizationId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof JwtService.UserPrincipal principal) {
            return principal.organizationId();
        }
        return null;
    }

    private String currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof JwtService.UserPrincipal principal) {
            return principal.userId();
        }
        return "unknown";
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("error", Map.of("code", code, "message", message));
        return ResponseEntity.status(status).body(body);
    }

    @FunctionalInterface
    private interface ManagementOperation<T> {
        ResponseEntity<T> execute();
    }
}
