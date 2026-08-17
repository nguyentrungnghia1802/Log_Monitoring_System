package com.example.logmonitor.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "local-bootstrap.admin")
public class LocalAdminBootstrapProperties {
    private boolean enabled;
    private String organizationId = "local-org";
    private String organizationSlug = "local";
    private String organizationName = "Local Development";
    private String username = "local-admin";
    private String email;
    private String password;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
    public String getOrganizationSlug() { return organizationSlug; }
    public void setOrganizationSlug(String organizationSlug) { this.organizationSlug = organizationSlug; }
    public String getOrganizationName() { return organizationName; }
    public void setOrganizationName(String organizationName) { this.organizationName = organizationName; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
