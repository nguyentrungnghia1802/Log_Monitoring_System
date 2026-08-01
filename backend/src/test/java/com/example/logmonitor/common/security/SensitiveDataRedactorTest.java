package com.example.logmonitor.common.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensitiveDataRedactorTest {

    @Test
    void redactsConfiguredKeysAndCredentialPatternsWithoutLoggingValues() {
        RedactionProperties properties = new RedactionProperties();
        SensitiveDataRedactor redactor = new SensitiveDataRedactor(properties);

        assertTrue(redactor.isSensitiveKey("access-token"));
        assertEquals("password=[REDACTED] token: [REDACTED]", redactor.redactText(
            "password=secret-value token: bearer-value"));
        assertEquals("Bearer [REDACTED]", redactor.redactText("Bearer jwt-value"));
        assertFalse(redactor.redactText("normal message").contains("REDACTED"));
    }

    @Test
    void supportsAConfiguredFieldSet() {
        RedactionProperties properties = new RedactionProperties();
        properties.setFields(List.of("customer_ssn"));
        SensitiveDataRedactor redactor = new SensitiveDataRedactor(properties);

        assertTrue(redactor.isSensitiveKey("customer-ssn"));
        assertFalse(redactor.isSensitiveKey("password"));
    }
}
