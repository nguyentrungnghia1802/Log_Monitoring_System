package com.example.logmonitor.project.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RetentionPolicyResolverTest {

    private RetentionPolicyResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new RetentionPolicyResolver();
    }

    @Test
    void resolvesCorrectLevelBasedOverrides() {
        assertEquals(3 * 24 * 3600L, resolver.resolveRetentionSeconds("proj-1", "DEBUG"));
        assertEquals(7 * 24 * 3600L, resolver.resolveRetentionSeconds("proj-1", "INFO"));
        assertEquals(14 * 24 * 3600L, resolver.resolveRetentionSeconds("proj-1", "WARN"));
        assertEquals(30 * 24 * 3600L, resolver.resolveRetentionSeconds("proj-1", "ERROR"));
        assertEquals(30 * 24 * 3600L, resolver.resolveRetentionSeconds("proj-1", "FATAL"));
    }

    @Test
    void resolvesProjectDefaultOverrideWhenLevelIsUnknown() {
        resolver.setProjectDefaultRetention("proj-custom", 10 * 24 * 3600L);
        assertEquals(10 * 24 * 3600L, resolver.resolveRetentionSeconds("proj-custom", "UNKNOWN"));
        assertEquals(7 * 24 * 3600L, resolver.resolveRetentionSeconds("proj-other", "UNKNOWN"));
    }
}
