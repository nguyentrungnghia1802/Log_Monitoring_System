package com.example.logmonitor.auth.api;

import com.example.logmonitor.audit.application.AuditService;
import com.example.logmonitor.auth.application.JwtService;
import com.example.logmonitor.auth.domain.User;
import com.example.logmonitor.auth.domain.UserRepository;
import com.example.logmonitor.auth.domain.ProjectMembership;
import com.example.logmonitor.auth.domain.ProjectMembershipRepository;
import com.example.logmonitor.auth.domain.Role;
import com.example.logmonitor.organization.domain.Organization;
import com.example.logmonitor.organization.domain.OrganizationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final ProjectMembershipRepository membershipRepository;
    private final OrganizationRepository organizationRepository;
    private final AuditService auditService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthController(
        UserRepository userRepository,
        ProjectMembershipRepository membershipRepository,
        OrganizationRepository organizationRepository,
        JwtService jwtService,
        PasswordEncoder passwordEncoder,
        AuditService auditService
    ) {
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.organizationRepository = organizationRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    public record RegisterRequest(String username, String email, String password, String organizationId) {}
    public record LoginRequest(String username, String password) {}
    public record AuthResponse(String token, String userId, String username, String organizationId) {}

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        if (request.username() == null || request.password() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username and password required"));
        }

        if (userRepository.findByUsername(request.username()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Username already exists"));
        }

        String orgId = request.organizationId() != null ? request.organizationId() : "default-org";
        organizationRepository.findById(orgId).orElseGet(() -> organizationRepository.save(
            new Organization(orgId, orgId.toLowerCase(), "Organization " + orgId)));
        User user = new User(null, request.username(), request.email(), passwordEncoder.encode(request.password()), orgId);
        user.setOrganizationRole(Role.ORGANIZATION_ADMIN);
        User savedUser = userRepository.save(user);

        // Grant default membership for demo-project
        membershipRepository.save(new ProjectMembership(savedUser.getId(), "demo-project", Role.ORGANIZATION_ADMIN));
        auditService.logAction(
            savedUser.getUsername(),
            orgId,
            null,
            "CREATE",
            "ORGANIZATION_MEMBERSHIP",
            savedUser.getId(),
            "Organization membership created"
        );

        String token = jwtService.generateToken(savedUser.getId(), savedUser.getUsername(), savedUser.getOrganizationId());
        return ResponseEntity.status(HttpStatus.CREATED).body(new AuthResponse(token, savedUser.getId(), savedUser.getUsername(), savedUser.getOrganizationId()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        Optional<User> userOpt = userRepository.findByUsername(request.username());
        if (userOpt.isEmpty() || !passwordEncoder.matches(request.password(), userOpt.get().getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid username or password"));
        }

        User user = userOpt.get();
        if (!user.isActive() || user.getOrganizationId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid username or password"));
        }
        String token = jwtService.generateToken(user.getId(), user.getUsername(), user.getOrganizationId());
        return ResponseEntity.ok(new AuthResponse(token, user.getId(), user.getUsername(), user.getOrganizationId()));
    }
}
