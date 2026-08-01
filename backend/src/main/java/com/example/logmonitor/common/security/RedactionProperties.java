package com.example.logmonitor.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "security.redaction")
public class RedactionProperties {

    private boolean enabled = true;
    private String replacement = "[REDACTED]";
    private List<String> fields = new ArrayList<>(List.of(
        "password",
        "passwd",
        "secret",
        "token",
        "access_token",
        "refresh_token",
        "api_key",
        "apikey",
        "raw_api_key",
        "authorization",
        "cookie",
        "private_key",
        "client_secret",
        "webhook_token",
        "bot_token"
    ));

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getReplacement() { return replacement; }
    public void setReplacement(String replacement) {
        this.replacement = replacement == null || replacement.isBlank() ? "[REDACTED]" : replacement;
    }

    public List<String> getFields() { return fields; }
    public void setFields(List<String> fields) {
        this.fields = fields == null ? new ArrayList<>() : new ArrayList<>(fields);
    }
}
