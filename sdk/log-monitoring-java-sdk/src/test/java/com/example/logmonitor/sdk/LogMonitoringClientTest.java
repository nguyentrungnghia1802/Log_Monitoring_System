package com.example.logmonitor.sdk;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogMonitoringClientTest {

    @Test
    void injectsDefaultFieldsAndSerializesCorrelationContextAndTags() throws Exception {
        List<String> bodies = new CopyOnWriteArrayList<>();
        try (TestServer server = new TestServer((exchange, attempt) -> {
            bodies.add(readBody(exchange));
            respond(exchange, 202, "{\"requestId\":\"server-request-1\"}", null);
        })) {
            LogMonitoringClientConfig config = config(server)
                .setService("checkout-service")
                .setEnvironment("staging")
                .setBatchSize(1);

            try (LogMonitoringClient client = new LogMonitoringClient(config)) {
                assertTrue(client.log("WARN", "CHECKOUT_FAILED", "A message", "trace-123", "request-456", null, Map.of("orderId", 42)));
                assertTrue(client.flush());
            }

            assertEquals(1, bodies.size());
            String body = bodies.get(0);
            assertTrue(body.contains("\"service\":\"checkout-service\""));
            assertTrue(body.contains("\"environment\":\"staging\""));
            assertTrue(body.contains("\"traceId\":\"trace-123\""));
            assertTrue(body.contains("\"requestId\":\"request-456\""));
            assertTrue(body.contains("\"context\":{\"orderId\":42}"));
            assertTrue(body.contains("\"tags\":{\"sdk\":\"java-21\"}"));
        }
    }

    @Test
    void truncatesThrowableMessageAndStackTraceBeforeQueueing() throws Exception {
        List<String> bodies = new CopyOnWriteArrayList<>();
        try (TestServer server = new TestServer((exchange, attempt) -> {
            bodies.add(readBody(exchange));
            respond(exchange, 202, "{}", null);
        })) {
            LogMonitoringClientConfig config = config(server)
                .setBatchSize(1)
                .setMaxExceptionMessageLength(20)
                .setMaxStackTraceLength(40);

            try (LogMonitoringClient client = new LogMonitoringClient(config)) {
                assertTrue(client.error(
                    "EXCEPTION_TEST",
                    "failed",
                    new IllegalStateException("This exception message is intentionally very long")));
                assertTrue(client.flush());
            }

            String body = bodies.get(0);
            assertTrue(body.contains("\"exception\":{"));
            assertTrue(body.contains("[truncated]"));
            assertFalse(body.contains("This exception message is intentionally very long"));
        }
    }

    @Test
    void formsBoundedBatchesAndFlushesPartialBatch() throws Exception {
        List<String> bodies = new CopyOnWriteArrayList<>();
        try (TestServer server = new TestServer((exchange, attempt) -> {
            bodies.add(readBody(exchange));
            respond(exchange, 202, "{}", null);
        })) {
            LogMonitoringClientConfig config = config(server)
                .setBatchSize(2)
                .setMaxWaitMs(10_000);

            try (LogMonitoringClient client = new LogMonitoringClient(config)) {
                assertTrue(client.submit(payload("batch-1")).isQueuedLocally());
                assertTrue(client.submit(payload("batch-2")).isQueuedLocally());
                assertTrue(client.submit(payload("batch-3")).isQueuedLocally());
                assertTrue(client.flush());
            }

            assertEquals(2, bodies.size());
            assertTrue(bodies.get(0).contains("\"eventId\":\"batch-1\""));
            assertTrue(bodies.get(0).contains("\"eventId\":\"batch-2\""));
            assertTrue(bodies.get(1).contains("\"eventId\":\"batch-3\""));
        }
    }

    @Test
    void rejectsEventsWhenLocalQueueCapacityIsExhausted() throws Exception {
        CountDownLatch requestStarted = new CountDownLatch(1);
        CountDownLatch releaseRequest = new CountDownLatch(1);
        try (TestServer server = new TestServer((exchange, attempt) -> {
            requestStarted.countDown();
            releaseRequest.await(2, TimeUnit.SECONDS);
            respond(exchange, 202, "{}", null);
        })) {
            LogMonitoringClientConfig config = config(server)
                .setQueueCapacity(1)
                .setBatchSize(1)
                .setMaxWaitMs(10_000);

            try (LogMonitoringClient client = new LogMonitoringClient(config)) {
                assertTrue(client.submit(payload("in-flight")).isQueuedLocally());
                assertTrue(requestStarted.await(2, TimeUnit.SECONDS));
                assertTrue(client.submit(payload("queued")).isQueuedLocally());
                LogSubmissionResult rejected = client.submit(payload("overflow"));
                assertEquals(LogSubmissionOutcome.REJECTED_LOCAL_QUEUE, rejected.outcome());
                releaseRequest.countDown();
                assertTrue(client.flush());
            }
        }
    }

    @Test
    void treats202AsServerAdmissionAndReportsCallbackOutcome() throws Exception {
        List<LogSubmissionResult> results = new CopyOnWriteArrayList<>();
        try (TestServer server = new TestServer((exchange, attempt) -> respond(
            exchange,
            202,
            "{\"requestId\":\"server-request-202\"}",
            null
        ))) {
            LogMonitoringClientConfig config = config(server)
                .setBatchSize(1)
                .setResultListener(results::add);

            try (LogMonitoringClient client = new LogMonitoringClient(config)) {
                assertTrue(client.submit(payload("accepted" )).isQueuedLocally());
                assertTrue(client.flush());
            }

            LogSubmissionResult finalResult = finalResult(results, "accepted");
            assertEquals(LogSubmissionOutcome.ACCEPTED_BY_SERVER_ADMISSION, finalResult.outcome());
            assertEquals(202, finalResult.httpStatus());
            assertEquals("server-request-202", finalResult.serverRequestId());
            assertTrue(finalResult.message().contains("persistence is asynchronous"));
        }
    }

    @Test
    void doesNotRetry401Or403Responses() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        List<LogSubmissionResult> results = new CopyOnWriteArrayList<>();
        try (TestServer server = new TestServer((exchange, attempt) -> {
            readBody(exchange);
            int status = attempts.incrementAndGet() == 1 ? 401 : 403;
            respond(exchange, status, "{\"error\":{\"code\":\"AUTH_FAILED\",\"message\":\"denied\"}}", null);
        })) {
            LogMonitoringClientConfig config = config(server)
                .setBatchSize(1)
                .setMaxRetries(3)
                .setBackoffMs(1)
                .setJitterMs(0)
                .setResultListener(results::add);

            try (LogMonitoringClient client = new LogMonitoringClient(config)) {
                client.submit(payload("unauthorized"));
                assertTrue(client.flush());
                client.submit(payload("forbidden"));
                assertTrue(client.flush());
            }

            assertEquals(2, attempts.get());
            assertEquals(LogSubmissionOutcome.REJECTED_SERVER, finalResult(results, "unauthorized").outcome());
            assertEquals(LogSubmissionOutcome.REJECTED_SERVER, finalResult(results, "forbidden").outcome());
        }
    }

    @Test
    void retries429UsingRetryAfterBeforeAccepting() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        AtomicLong firstAttemptAt = new AtomicLong();
        AtomicLong secondAttemptAt = new AtomicLong();
        List<LogSubmissionResult> results = new CopyOnWriteArrayList<>();
        try (TestServer server = new TestServer((exchange, attempt) -> {
            readBody(exchange);
            int current = attempts.incrementAndGet();
            if (current == 1) {
                firstAttemptAt.set(System.nanoTime());
                respond(exchange, 429, "{\"error\":{\"code\":\"RATE_LIMITED\"}}", "1");
            } else {
                secondAttemptAt.set(System.nanoTime());
                respond(exchange, 202, "{}", null);
            }
        })) {
            LogMonitoringClientConfig config = config(server)
                .setBatchSize(1)
                .setMaxRetries(2)
                .setBackoffMs(0)
                .setJitterMs(0)
                .setMaxRetryAfterMs(1_500)
                .setResultListener(results::add);

            try (LogMonitoringClient client = new LogMonitoringClient(config)) {
                client.submit(payload("rate-limited"));
                assertTrue(client.flush());
            }

            assertEquals(2, attempts.get());
            assertTrue(TimeUnit.NANOSECONDS.toMillis(secondAttemptAt.get() - firstAttemptAt.get()) >= 800);
            assertEquals(LogSubmissionOutcome.ACCEPTED_BY_SERVER_ADMISSION, finalResult(results, "rate-limited").outcome());
        }
    }

    @Test
    void retries503BeforeAccepting() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        List<LogSubmissionResult> results = new CopyOnWriteArrayList<>();
        try (TestServer server = new TestServer((exchange, attempt) -> {
            readBody(exchange);
            if (attempts.incrementAndGet() == 1) {
                respond(exchange, 503, "{\"error\":{\"code\":\"TEMPORARILY_UNAVAILABLE\"}}", "0");
            } else {
                respond(exchange, 202, "{}", null);
            }
        })) {
            LogMonitoringClientConfig config = config(server)
                .setBatchSize(1)
                .setMaxRetries(2)
                .setBackoffMs(0)
                .setJitterMs(0)
                .setResultListener(results::add);

            try (LogMonitoringClient client = new LogMonitoringClient(config)) {
                client.submit(payload("temporarily-unavailable"));
                assertTrue(client.flush());
            }

            assertEquals(2, attempts.get());
            assertEquals(LogSubmissionOutcome.ACCEPTED_BY_SERVER_ADMISSION,
                finalResult(results, "temporarily-unavailable").outcome());
        }
    }

    @Test
    void retriesTimeoutAndReportsRetryExhaustion() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        List<LogSubmissionResult> results = new CopyOnWriteArrayList<>();
        try (TestServer server = new TestServer((exchange, attempt) -> {
            readBody(exchange);
            attempts.incrementAndGet();
            Thread.sleep(150);
            respond(exchange, 503, "{}", null);
        })) {
            LogMonitoringClientConfig config = config(server)
                .setBatchSize(1)
                .setRequestTimeoutMs(25)
                .setMaxRetries(2)
                .setBackoffMs(0)
                .setJitterMs(0)
                .setResultListener(results::add);

            try (LogMonitoringClient client = new LogMonitoringClient(config)) {
                client.submit(payload("timeout"));
                assertTrue(client.flush(2, TimeUnit.SECONDS));
            }

            assertEquals(2, attempts.get());
            assertEquals(LogSubmissionOutcome.RETRY_EXHAUSTED, finalResult(results, "timeout").outcome());
        }
    }

    @Test
    void closeFlushesQueuedEventsWithinBoundedPolicy() throws Exception {
        List<LogSubmissionResult> results = new CopyOnWriteArrayList<>();
        try (TestServer server = new TestServer((exchange, attempt) -> respond(exchange, 202, "{}", null))) {
            LogMonitoringClientConfig config = config(server)
                .setBatchSize(1)
                .setFlushTimeoutMs(1_000)
                .setResultListener(results::add);

            LogMonitoringClient client = new LogMonitoringClient(config);
            client.submit(payload("close-flush"));
            client.close();

            assertEquals(LogSubmissionOutcome.ACCEPTED_BY_SERVER_ADMISSION, finalResult(results, "close-flush").outcome());
        }
    }

    @Test
    void reportsDroppedPolicyAfterClose() throws Exception {
        try (TestServer server = new TestServer((exchange, attempt) -> respond(exchange, 202, "{}", null))) {
            LogMonitoringClient client = new LogMonitoringClient(config(server));
            client.close();
            LogSubmissionResult result = client.submit(payload("after-close"));
            assertEquals(LogSubmissionOutcome.DROPPED_BY_POLICY, result.outcome());
        }
    }

    @Test
    void generatedCorrelationIdsAndQueueResultAreBoundedWithoutRetainingPayloads() throws Exception {
        List<String> bodies = new CopyOnWriteArrayList<>();
        try (TestServer server = new TestServer((exchange, attempt) -> {
            bodies.add(readBody(exchange));
            respond(exchange, 202, "{}", null);
        })) {
            LogMonitoringClientConfig config = config(server)
                .setQueueCapacity(4)
                .setBatchSize(4)
                .setMaxContextEntries(2)
                .setMaxTagEntries(2);
            try (LogMonitoringClient client = new LogMonitoringClient(config)) {
                assertTrue(client.log("INFO", "DEFAULT_IDS", "message"));
                assertTrue(client.flush());
            }
            String body = bodies.get(0);
            Matcher traceMatcher = Pattern.compile("\\\"traceId\\\":\\\"([^\\\"]+)\\\"").matcher(body);
            Matcher requestMatcher = Pattern.compile("\\\"requestId\\\":\\\"([^\\\"]+)\\\"").matcher(body);
            assertTrue(traceMatcher.find());
            assertTrue(requestMatcher.find());
            assertFalse(traceMatcher.group(1).isBlank());
            assertFalse(requestMatcher.group(1).isBlank());
        }
    }

    private static LogMonitoringClientConfig config(TestServer server) {
        return new LogMonitoringClientConfig()
            .setEndpoint("http://localhost:" + server.port())
            .setApiKey("lm_live_test_secret")
            .setQueueCapacity(10)
            .setBatchSize(1)
            .setMaxWaitMs(10_000)
            .setMaxRetries(1)
            .setBackoffMs(1)
            .setJitterMs(0)
            .setRequestTimeoutMs(1_000)
            .setFlushTimeoutMs(2_000);
    }

    private static LogEventPayload payload(String eventId) {
        return new LogEventPayload(
            eventId,
            Instant.parse("2026-08-18T00:00:00Z"),
            "INFO",
            "test-service",
            "test",
            "SDK_TEST",
            "message",
            "trace-" + eventId,
            "request-" + eventId,
            null,
            Map.of("eventId", eventId),
            Map.of("suite", "sdk")
        );
    }

    private static LogSubmissionResult finalResult(List<LogSubmissionResult> results, String eventId) {
        return results.stream()
            .filter(result -> eventId.equals(result.eventId()))
            .filter(result -> result.outcome() != LogSubmissionOutcome.QUEUED_LOCALLY)
            .findFirst()
            .orElseThrow(() -> new AssertionError("No final result for " + eventId + ": " + results));
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void respond(HttpExchange exchange, int status, String body, String retryAfter) throws IOException {
        if (retryAfter != null) {
            exchange.getResponseHeaders().add("Retry-After", retryAfter);
        }
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    @FunctionalInterface
    private interface TestHandler {
        void handle(HttpExchange exchange, int attempt) throws Exception;
    }

    private static final class TestServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor = Executors.newCachedThreadPool();
        private final AtomicInteger attempts = new AtomicInteger();

        private TestServer(TestHandler handler) throws IOException {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.setExecutor(executor);
            server.createContext("/api/v1/ingest/logs/batch", exchange -> {
                try {
                    handler.handle(exchange, attempts.incrementAndGet());
                } catch (Exception exception) {
                    exchange.close();
                }
            });
            server.start();
        }

        private int port() {
            return server.getAddress().getPort();
        }

        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
