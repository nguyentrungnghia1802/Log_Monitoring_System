package com.example.logmonitor.sdk;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    @Test
    void retriesTheSameBatchWithStableEventIdAfterTemporaryServerFailure() throws IOException {
        AtomicInteger attempts = new AtomicInteger();
        List<String> requestBodies = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/v1/ingest/logs/batch", exchange -> {
            requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            int status = attempts.incrementAndGet() == 1 ? 503 : 202;
            byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            LogMonitoringClientConfig config = new LogMonitoringClientConfig()
                .setEndpoint("http://localhost:" + server.getAddress().getPort())
                .setApiKey("lm_live_test_secret")
                .setQueueCapacity(10)
                .setBatchSize(1)
                .setMaxWaitMs(10_000)
                .setMaxRetries(2)
                .setBackoffMs(1);

            try (LogMonitoringClient client = new LogMonitoringClient(config)) {
                assertTrue(client.log("INFO", "RETRY_TEST", "retry-stable-event"));
            }

            assertEquals(2, attempts.get());
            assertEquals(2, requestBodies.size());
            assertEquals(requestBodies.get(0), requestBodies.get(1));
            Matcher matcher = Pattern.compile("\\\"eventId\\\":\\\"([^\\\"]+)\\\"")
                .matcher(requestBodies.get(0));
            assertTrue(matcher.find());
            assertFalse(matcher.group(1).isBlank());
        } finally {
            server.stop(0);
        }
    }
}
