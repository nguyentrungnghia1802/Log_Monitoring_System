package com.example.logmonitor.auth.config;

import com.example.logmonitor.audit.application.AuditService;
import com.example.logmonitor.auth.domain.Role;
import com.example.logmonitor.auth.domain.User;
import com.example.logmonitor.auth.domain.UserRepository;
import com.example.logmonitor.organization.domain.Organization;
import com.example.logmonitor.organization.domain.OrganizationRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@Profile("local")
public class LocalAdminBootstrap implements ApplicationRunner {

    private final LocalAdminBootstrapProperties properties;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public LocalAdminBootstrap(
        LocalAdminBootstrapProperties properties,
        OrganizationRepository organizationRepository,
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        AuditService auditService
    ) {
        this.properties = properties;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) return;
        validate();
        String email = properties.getEmail().trim().toLowerCase(Locale.ROOT);
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) return;

        organizationRepository.findById(properties.getOrganizationId()).orElseGet(() ->
            organizationRepository.save(new Organization(
                properties.getOrganizationId(),
                properties.getOrganizationSlug(),
                properties.getOrganizationName()
            ))
        );
        User admin = new User(
            null,
            properties.getUsername().trim().toLowerCase(Locale.ROOT),
            email,
            passwordEncoder.encode(properties.getPassword()),
            properties.getOrganizationId()
        );
        admin.setOrganizationRole(Role.ORGANIZATION_ADMIN);
        admin = userRepository.save(admin);
        auditService.logAction(admin.getUsername(), admin.getOrganizationId(), null,
            "BOOTSTRAP", "USER", admin.getId(), "Local development administrator created");
    }

    private void validate() {
        if (blank(properties.getOrganizationId())
            || blank(properties.getOrganizationSlug())
            || blank(properties.getOrganizationName())
            || blank(properties.getUsername())
            || blank(properties.getEmail())
            || !properties.getEmail().contains("@")
            || blank(properties.getPassword())
            || properties.getPassword().length() < 12) {
            throw new IllegalStateException(
                "Enabled local admin bootstrap requires organization, username, valid email, and a password of at least 12 characters"
            );
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
