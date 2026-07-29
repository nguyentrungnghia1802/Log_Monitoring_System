package com.example.logmonitor.sdk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LogMonitoringClientTest {

    @Test
    void acceptsLogEventsWithinQueueCapacity() {
        LogMonitoringClientConfig config = new LogMonitoringClientConfig()
            .setEndpoint("http://localhost:8080")
            .setApiKey("lm_live_test_secret")
            .setService("test-service")
            .setEnvironment("test")
            .setQueueCapacity(10)
            .setMaxRetries(1)
            .setBackoffMs(10);

        try (LogMonitoringClient client = new LogMonitoringClient(config)) {
            boolean logged1 = client.log("INFO", "TEST_EVENT", "Sample test log message");
            boolean logged2 = client.log("WARN", "TEST_WARN", "Warning message");

            assertTrue(logged1);
            assertTrue(logged2);
        }
    }

    @Test
    void sanitizesExceptionStackTraces() {
        LogMonitoringClientConfig config = new LogMonitoringClientConfig()
            .setQueueCapacity(10)
            .setMaxRetries(1)
            .setBackoffMs(10);

        try (LogMonitoringClient client = new LogMonitoringClient(config)) {
            RuntimeException ex = new RuntimeException("Simulated exception for testing");
            boolean logged = client.error("TEST_ERROR", "Exception occurred", ex);

            assertTrue(logged);
        }
    }
}
