package com.example.logmonitor.sdk;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class LogMonitoringClient implements AutoCloseable {

    private final LogMonitoringClientConfig config;
    private final ArrayBlockingQueue<LogEventPayload> queue;
    private final HttpClient httpClient;
    private final ScheduledExecutorService executorService;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public LogMonitoringClient(LogMonitoringClientConfig config) {
        this.config = config;
        this.queue = new ArrayBlockingQueue<>(config.getQueueCapacity());
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(1))
            .build();

        this.executorService = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "log-monitoring-sdk-worker");
            t.setDaemon(true);
            return t;
        });

        this.executorService.scheduleWithFixedDelay(this::drainQueue, config.getMaxWaitMs(), config.getMaxWaitMs(), TimeUnit.MILLISECONDS);
    }

    public boolean log(String level, String eventType, String message) {
        return log(level, eventType, message, null, null, null);
    }

    public boolean error(String eventType, String message, Throwable throwable) {
        LogEventPayload.ExceptionPayload exPayload = null;
        if (throwable != null) {
            StringWriter sw = new StringWriter();
            throwable.printStackTrace(new PrintWriter(sw));
            exPayload = new LogEventPayload.ExceptionPayload(
                throwable.getClass().getName(),
                sanitizeText(throwable.getMessage(), 1000),
                sanitizeText(sw.toString(), 4000)
            );
        }
        return log("ERROR", eventType, message, null, exPayload, null);
    }

    public boolean log(String level, String eventType, String message, String traceId, LogEventPayload.ExceptionPayload exception, Map<String, Object> context) {
        if (!running.get()) {
            return false;
        }

        // Generate identity before queueing so all HTTP retries resend the same
        // eventId. The server still treats repeated accepted submissions as
        // separate log documents in V1.
        LogEventPayload payload = new LogEventPayload(
            UUID.randomUUID().toString(),
            Instant.now(),
            level != null ? level : "INFO",
            config.getService(),
            config.getEnvironment(),
            eventType != null ? eventType : "GENERAL",
            sanitizeText(message, 4000),
            traceId,
            UUID.randomUUID().toString(),
            exception,
            context,
            Map.of("sdk", "java-21")
        );

        return queue.offer(payload);
    }

    private void drainQueue() {
        if (queue.isEmpty()) return;

        List<LogEventPayload> batch = new ArrayList<>();
        queue.drainTo(batch, config.getBatchSize());
        if (batch.isEmpty()) return;

        sendBatchWithRetry(batch);
    }

    private void sendBatchWithRetry(List<LogEventPayload> batch) {
        String jsonBody = toJsonBatch(batch);
        String url = config.getEndpoint().replaceAll("/+$", "") + "/api/v1/ingest/logs/batch";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .header("X-API-Key", config.getApiKey() != null ? config.getApiKey() : "")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();

        int attempt = 0;
        long backoff = config.getBackoffMs();

        while (attempt < config.getMaxRetries()) {
            attempt++;
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();

                if (status >= 200 && status < 300) {
                    return; // Success
                }

                // Fail fast without retry for client auth errors
                if (status == 401 || status == 403) {
                    System.err.println("[LogMonitoringSDK] Authentication failed (HTTP " + status + "). Dropping batch.");
                    return;
                }

                System.err.println("[LogMonitoringSDK] Ingestion failed (HTTP " + status + "). Attempt " + attempt + "/" + config.getMaxRetries());
            } catch (Exception ex) {
                System.err.println("[LogMonitoringSDK] Ingestion exception: " + ex.getMessage() + ". Attempt " + attempt + "/" + config.getMaxRetries());
            }

            try {
                Thread.sleep(backoff);
                backoff *= 2;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private String sanitizeText(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() > maxLength) {
            return text.substring(0, maxLength) + "... [truncated]";
        }
        return text;
    }

    private String toJsonBatch(List<LogEventPayload> batch) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"events\":[");
        for (int i = 0; i < batch.size(); i++) {
            if (i > 0) sb.append(",");
            LogEventPayload p = batch.get(i);
            sb.append("{")
                .append("\"eventId\":\"").append(escapeJson(p.eventId())).append("\",")
                .append("\"timestamp\":\"").append(p.timestamp().toString()).append("\",")
                .append("\"level\":\"").append(escapeJson(p.level())).append("\",")
                .append("\"service\":\"").append(escapeJson(p.service())).append("\",")
                .append("\"environment\":\"").append(escapeJson(p.environment())).append("\",")
                .append("\"eventType\":\"").append(escapeJson(p.eventType())).append("\",")
                .append("\"message\":\"").append(escapeJson(p.message())).append("\"");

            if (p.exception() != null) {
                sb.append(",\"exception\":{")
                    .append("\"type\":\"").append(escapeJson(p.exception().type())).append("\",")
                    .append("\"message\":\"").append(escapeJson(p.exception().message())).append("\",")
                    .append("\"stackTrace\":\"").append(escapeJson(p.exception().stackTrace())).append("\"}");
            }
            sb.append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    @Override
    public void close() {
        if (running.compareAndSet(true, false)) {
            drainQueue();
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
            }
        }
    }
}
