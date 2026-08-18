package com.example.logmonitor.sdk;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Small dependency-free Java 21 client for the batch ingestion endpoint.
 *
 * <p>{@link #log(String, String, String)} and {@link #error(String, String,
 * Throwable)} report local queue admission through their boolean return value.
 * The configured callback receives the later server outcome. Applications
 * must treat {@code 202} as server admission only, never as durable storage.</p>
 */
public class LogMonitoringClient implements AutoCloseable {

    private final LogMonitoringClientConfig config;
    private final ArrayBlockingQueue<QueuedEvent> queue;
    private final HttpClient httpClient;
    private final ExecutorService executorService;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean activeWork = new AtomicBoolean(false);
    private final AtomicBoolean flushRequested = new AtomicBoolean(false);
    private final AtomicInteger pendingEvents = new AtomicInteger();
    private final AtomicReference<Thread> workerThread = new AtomicReference<>();
    private final Object flushMonitor = new Object();
    private volatile long closeDeadlineNanos = Long.MAX_VALUE;

    public LogMonitoringClient(LogMonitoringClientConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        this.config = config;
        this.config.validate();
        URI endpoint = URI.create(config.getEndpoint());
        if (endpoint.getScheme() == null || (!"http".equalsIgnoreCase(endpoint.getScheme())
            && !"https".equalsIgnoreCase(endpoint.getScheme()))) {
            throw new IllegalArgumentException("endpoint must use http or https");
        }

        this.queue = new ArrayBlockingQueue<>(config.getQueueCapacity());
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(config.getRequestTimeoutMs()))
            .build();
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "log-monitoring-sdk-worker");
            thread.setDaemon(true);
            workerThread.set(thread);
            return thread;
        });
        this.executorService.submit(this::runWorker);
    }

    public boolean log(String level, String eventType, String message) {
        return log(level, eventType, message, null, null, null, null);
    }

    /** Backward-compatible overload that supplies a trace ID and exception. */
    public boolean log(
        String level,
        String eventType,
        String message,
        String traceId,
        LogEventPayload.ExceptionPayload exception,
        Map<String, Object> context
    ) {
        return log(level, eventType, message, traceId, null, exception, context);
    }

    /**
     * Queues a structured event while preserving caller-provided correlation
     * IDs. Missing IDs are generated locally so every event remains traceable.
     */
    public boolean log(
        String level,
        String eventType,
        String message,
        String traceId,
        String requestId,
        LogEventPayload.ExceptionPayload exception,
        Map<String, Object> context
    ) {
        return submit(buildPayload(level, eventType, message, traceId, requestId, exception, context, null))
            .isQueuedLocally();
    }

    public boolean error(String eventType, String message, Throwable throwable) {
        return error(eventType, message, throwable, null, null, null, null);
    }

    public boolean error(
        String eventType,
        String message,
        Throwable throwable,
        String traceId,
        String requestId,
        Map<String, Object> context,
        Map<String, Object> tags
    ) {
        return submit(buildPayload(
            "ERROR",
            eventType,
            message,
            traceId,
            requestId,
            exceptionPayload(throwable),
            context,
            tags
        )).isQueuedLocally();
    }

    /**
     * Submit a pre-built event. The return value only describes local queue
     * admission; the callback reports the eventual server result.
     */
    public LogSubmissionResult submit(LogEventPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("payload must not be null");
        }
        LogEventPayload normalized = normalize(payload);
        if (!running.get()) {
            return publishImmediate(new LogSubmissionResult(
                normalized.eventId(),
                LogSubmissionOutcome.DROPPED_BY_POLICY,
                0,
                null,
                "CLIENT_CLOSED",
                "The SDK client is closed"
            ));
        }

        QueuedEvent queuedEvent = new QueuedEvent(normalized);
        pendingEvents.incrementAndGet();
        if (!queue.offer(queuedEvent)) {
            pendingEvents.decrementAndGet();
            return publishImmediate(new LogSubmissionResult(
                normalized.eventId(),
                LogSubmissionOutcome.REJECTED_LOCAL_QUEUE,
                0,
                null,
                "LOCAL_QUEUE_FULL",
                "The SDK bounded queue is full"
            ));
        }
        return publishImmediate(new LogSubmissionResult(
            normalized.eventId(),
            LogSubmissionOutcome.QUEUED_LOCALLY,
            0,
            null,
            null,
            "Queued locally; server admission is pending"
        ));
    }

    /** Flushes queued events using the configured bounded timeout. */
    public boolean flush() {
        return flush(config.getFlushTimeoutMs(), TimeUnit.MILLISECONDS);
    }

    /**
     * Requests immediate batch formation and waits for the queue and active
     * batch to settle. A timeout returns false; the client remains usable.
     */
    public boolean flush(long timeout, TimeUnit unit) {
        if (timeout <= 0 || unit == null) {
            throw new IllegalArgumentException("flush timeout must be positive");
        }
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        flushRequested.set(true);
        synchronized (flushMonitor) {
            while (running.get() && pendingEvents.get() > 0) {
                Thread worker = workerThread.get();
                if (!activeWork.get() && worker != null) {
                    worker.interrupt();
                }
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    return false;
                }
                try {
                    TimeUnit.NANOSECONDS.timedWait(flushMonitor, Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(50)));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            boolean flushed = pendingEvents.get() == 0;
            if (flushed) {
                flushRequested.set(false);
            }
            return flushed;
        }
    }

    @Override
    public void close() {
        if (!running.get()) {
            return;
        }

        // Wake a sleeping worker and give it the configured bounded window
        // while admission is still open. This avoids a close-time race where
        // the executor has not started its worker yet.
        flush(config.getFlushTimeoutMs(), TimeUnit.MILLISECONDS);
        if (!running.compareAndSet(true, false)) {
            return;
        }

        flushRequested.set(true);
        closeDeadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(config.getFlushTimeoutMs());
        Thread worker = workerThread.get();
        if (worker != null && !activeWork.get()) {
            worker.interrupt();
        }
        executorService.shutdown();
        try {
            long waitMs = config.getFlushTimeoutMs() + config.getRequestTimeoutMs();
            if (!executorService.awaitTermination(waitMs, TimeUnit.MILLISECONDS)) {
                executorService.shutdownNow();
                if (!executorService.awaitTermination(config.getRequestTimeoutMs(), TimeUnit.MILLISECONDS)) {
                    dropRemaining("CLIENT_CLOSE_TIMEOUT", "The bounded close flush timed out");
                }
            }
        } catch (InterruptedException exception) {
            executorService.shutdownNow();
            dropRemaining("CLIENT_CLOSE_INTERRUPTED", "The close flush was interrupted");
            Thread.currentThread().interrupt();
        } finally {
            dropRemaining("CLIENT_CLOSE_TIMEOUT", "The bounded close flush timed out");
        }
    }

    private void runWorker() {
        try {
            while (running.get() || !queue.isEmpty()) {
                if (closeDeadlineExpired()) {
                    dropRemaining("CLIENT_CLOSE_TIMEOUT", "The bounded close flush timed out");
                    break;
                }

                QueuedEvent first;
                try {
                    first = queue.poll(waitForFirstEventNanos(), TimeUnit.NANOSECONDS);
                } catch (InterruptedException exception) {
                    Thread.interrupted();
                    continue;
                }
                if (first == null) {
                    continue;
                }

                // An interrupt may only have been the flush wake-up signal
                // delivered just after poll returned. Do not carry that
                // signal into HttpClient.send(), where it would abort a real
                // request and masquerade as a transport failure.
                Thread.interrupted();
                activeWork.set(true);
                List<QueuedEvent> batch = collectBatch(first);
                BatchResult result = sendBatchWithRetry(batch);
                for (QueuedEvent event : batch) {
                    publish(new LogSubmissionResult(
                        event.payload().eventId(),
                        result.outcome(),
                        result.httpStatus(),
                        result.serverRequestId(),
                        result.errorCode(),
                        result.message()
                    ));
                    pendingEvents.decrementAndGet();
                }
                activeWork.set(false);
                if (queue.isEmpty()) {
                    flushRequested.set(false);
                }
                notifyFlushWaiters();
            }
        } finally {
            activeWork.set(false);
            notifyFlushWaiters();
        }
    }

    private List<QueuedEvent> collectBatch(QueuedEvent first) {
        List<QueuedEvent> batch = new ArrayList<>(config.getBatchSize());
        batch.add(first);
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(config.getMaxWaitMs());
        while (batch.size() < config.getBatchSize()) {
            queue.drainTo(batch, config.getBatchSize() - batch.size());
            if (batch.size() >= config.getBatchSize() || flushRequested.get()) {
                break;
            }
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                break;
            }
            try {
                QueuedEvent next = queue.poll(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(50)), TimeUnit.NANOSECONDS);
                if (next != null) {
                    batch.add(next);
                }
            } catch (InterruptedException exception) {
                Thread.interrupted();
                break;
            }
        }
        return batch;
    }

    private BatchResult sendBatchWithRetry(List<QueuedEvent> batch) {
        String jsonBody = toJsonBatch(batch);
        String url = config.getEndpoint().replaceAll("/+$", "") + "/api/v1/ingest/logs/batch";
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofMillis(config.getRequestTimeoutMs()))
            .header("Content-Type", "application/json");
        if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
            requestBuilder.header("X-API-Key", config.getApiKey());
        }
        HttpRequest request = requestBuilder.POST(HttpRequest.BodyPublishers.ofString(jsonBody)).build();

        long backoff = config.getBackoffMs();
        for (int attempt = 1; attempt <= config.getMaxRetries(); attempt++) {
            if (closeDeadlineExpired()) {
                return BatchResult.dropped("CLIENT_CLOSE_TIMEOUT", "The bounded close flush timed out");
            }
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                String body = response.body() == null ? "" : response.body();
                if (status >= 200 && status < 300) {
                    return BatchResult.accepted(status, jsonField(body, "requestId"));
                }
                if (!isRetryableStatus(status)) {
                    return BatchResult.rejected(status, jsonField(body, "code"), jsonField(body, "message"));
                }
                if (attempt == config.getMaxRetries()) {
                    return BatchResult.retryExhausted(status, jsonField(body, "code"), jsonField(body, "message"));
                }
                long retryAfterMs = retryAfterMs(response);
                long delayMs = Math.max(retryDelayMs(backoff), retryAfterMs);
                if (!sleepBeforeRetry(delayMs)) {
                    return closeDeadlineExpired()
                        ? BatchResult.dropped("CLIENT_CLOSE_TIMEOUT", "The bounded close flush timed out")
                        : BatchResult.retryExhausted(status, jsonField(body, "code"), jsonField(body, "message"));
                }
                backoff = Math.min(config.getMaxBackoffMs(), safeDouble(backoff));
            } catch (java.io.IOException exception) {
                if (attempt == config.getMaxRetries()) {
                    return BatchResult.retryExhausted(0, "TRANSPORT_FAILURE", "Ingestion request failed after retries");
                }
                if (!sleepBeforeRetry(retryDelayMs(backoff))) {
                    return closeDeadlineExpired()
                        ? BatchResult.dropped("CLIENT_CLOSE_TIMEOUT", "The bounded close flush timed out")
                        : BatchResult.retryExhausted(0, "TRANSPORT_FAILURE", "Ingestion request failed after retries");
                }
                backoff = Math.min(config.getMaxBackoffMs(), safeDouble(backoff));
            } catch (InterruptedException exception) {
                Thread.interrupted();
                return closeDeadlineExpired()
                    ? BatchResult.dropped("CLIENT_CLOSE_TIMEOUT", "The bounded close flush timed out")
                    : BatchResult.retryExhausted(0, "TRANSPORT_INTERRUPTED", "Ingestion request was interrupted");
            }
        }
        return BatchResult.retryExhausted(0, "RETRY_EXHAUSTED", "Ingestion request failed after retries");
    }

    private boolean sleepBeforeRetry(long delayMs) {
        long boundedDelay = Math.max(0, delayMs);
        long remainingNanos = closeDeadlineNanos - System.nanoTime();
        if (remainingNanos <= 0) {
            return false;
        }
        long sleepMs = Math.min(boundedDelay, TimeUnit.NANOSECONDS.toMillis(remainingNanos));
        try {
            Thread.sleep(sleepMs);
            return !closeDeadlineExpired();
        } catch (InterruptedException exception) {
            Thread.interrupted();
            return false;
        }
    }

    private long retryDelayMs(long backoff) {
        long jitter = config.getJitterMs() == 0
            ? 0
            : ThreadLocalRandom.current().nextLong(config.getJitterMs() + 1);
        return Math.min(config.getMaxBackoffMs(), safeAdd(backoff, jitter));
    }

    private long retryAfterMs(HttpResponse<?> response) {
        String retryAfter = response.headers().firstValue("Retry-After").orElse(null);
        if (retryAfter == null || retryAfter.isBlank()) {
            return 0;
        }
        try {
            long seconds = Long.parseLong(retryAfter.trim());
            return Math.min(config.getMaxRetryAfterMs(), Math.max(0, safeMultiply(seconds, 1_000)));
        } catch (NumberFormatException ignored) {
            try {
                long millis = ZonedDateTime.parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant().toEpochMilli() - System.currentTimeMillis();
                return Math.min(config.getMaxRetryAfterMs(), Math.max(0, millis));
            } catch (DateTimeParseException ignoredDate) {
                return 0;
            }
        }
    }

    private boolean isRetryableStatus(int status) {
        return status == 408 || status == 425 || status == 429 || status >= 500;
    }

    private LogEventPayload buildPayload(
        String level,
        String eventType,
        String message,
        String traceId,
        String requestId,
        LogEventPayload.ExceptionPayload exception,
        Map<String, Object> context,
        Map<String, Object> tags
    ) {
        return normalize(new LogEventPayload(
            UUID.randomUUID().toString(),
            java.time.Instant.now(),
            nonBlankOrDefault(level, "INFO"),
            config.getService(),
            config.getEnvironment(),
            nonBlankOrDefault(eventType, "GENERAL"),
            message,
            nonBlankOrDefault(traceId, UUID.randomUUID().toString()),
            nonBlankOrDefault(requestId, UUID.randomUUID().toString()),
            exception,
            context,
            tags
        ));
    }

    private LogEventPayload normalize(LogEventPayload payload) {
        Map<String, Object> boundedTags = new LinkedHashMap<>();
        boundedTags.put("sdk", "java-21");
        if (payload.tags() != null) {
            boundedTags.putAll(payload.tags());
        }
        return new LogEventPayload(
            boundText(nonBlankOrDefault(payload.eventId(), UUID.randomUUID().toString()), config.getMaxContextValueLength()),
            payload.timestamp() == null ? java.time.Instant.now() : payload.timestamp(),
            boundText(nonBlankOrDefault(payload.level(), "INFO"), config.getMaxContextValueLength()),
            boundText(nonBlankOrDefault(payload.service(), config.getService()), config.getMaxContextValueLength()),
            boundText(nonBlankOrDefault(payload.environment(), config.getEnvironment()), config.getMaxContextValueLength()),
            boundText(nonBlankOrDefault(payload.eventType(), "GENERAL"), config.getMaxContextValueLength()),
            boundText(payload.message(), config.getMaxMessageLength()),
            boundText(nonBlankOrDefault(payload.traceId(), UUID.randomUUID().toString()), config.getMaxContextValueLength()),
            boundText(nonBlankOrDefault(payload.requestId(), UUID.randomUUID().toString()), config.getMaxContextValueLength()),
            normalizeException(payload.exception()),
            boundMap(payload.context(), config.getMaxContextEntries()),
            boundMap(boundedTags, config.getMaxTagEntries())
        );
    }

    private LogEventPayload.ExceptionPayload exceptionPayload(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return normalizeException(new LogEventPayload.ExceptionPayload(
            throwable.getClass().getName(),
            throwable.getMessage(),
            writer.toString()
        ));
    }

    private LogEventPayload.ExceptionPayload normalizeException(LogEventPayload.ExceptionPayload exception) {
        if (exception == null) {
            return null;
        }
        return new LogEventPayload.ExceptionPayload(
            boundText(exception.type(), config.getMaxContextValueLength()),
            boundText(exception.message(), config.getMaxExceptionMessageLength()),
            boundText(exception.stackTrace(), config.getMaxStackTraceLength())
        );
    }

    private Map<String, Object> boundMap(Map<?, ?> source, int maxEntries) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> bounded = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (bounded.size() >= maxEntries || entry.getKey() == null) {
                break;
            }
            String key = boundText(String.valueOf(entry.getKey()), config.getMaxContextKeyLength());
            if (!key.isBlank()) {
                bounded.put(key, boundValue(entry.getValue(), 0));
            }
        }
        return bounded;
    }

    private Object boundValue(Object value, int depth) {
        if (value == null || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof CharSequence) {
            return boundText(value.toString(), config.getMaxContextValueLength());
        }
        if (depth >= 1) {
            return boundText(String.valueOf(value), config.getMaxContextValueLength());
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> nested = new LinkedHashMap<>();
            int count = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (count++ >= config.getMaxContextEntries()) {
                    break;
                }
                nested.put(boundText(String.valueOf(entry.getKey()), config.getMaxContextKeyLength()), boundValue(entry.getValue(), depth + 1));
            }
            return nested;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> bounded = new ArrayList<>();
            for (Object item : iterable) {
                if (bounded.size() >= config.getMaxContextEntries()) {
                    break;
                }
                bounded.add(boundValue(item, depth + 1));
            }
            return bounded;
        }
        return boundText(String.valueOf(value), config.getMaxContextValueLength());
    }

    private String toJsonBatch(List<QueuedEvent> batch) {
        StringBuilder json = new StringBuilder("{\"events\":[");
        for (int i = 0; i < batch.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            appendEventJson(json, batch.get(i).payload());
        }
        return json.append("]}").toString();
    }

    private void appendEventJson(StringBuilder json, LogEventPayload payload) {
        json.append('{')
            .append("\"eventId\":").append(jsonString(payload.eventId())).append(',')
            .append("\"timestamp\":").append(jsonString(payload.timestamp().toString())).append(',')
            .append("\"level\":").append(jsonString(payload.level())).append(',')
            .append("\"service\":").append(jsonString(payload.service())).append(',')
            .append("\"environment\":").append(jsonString(payload.environment())).append(',')
            .append("\"eventType\":").append(jsonString(payload.eventType())).append(',')
            .append("\"message\":").append(jsonString(payload.message())).append(',')
            .append("\"traceId\":").append(jsonString(payload.traceId())).append(',')
            .append("\"requestId\":").append(jsonString(payload.requestId())).append(',')
            .append("\"context\":");
        appendJsonValue(json, payload.context());
        json.append(',').append("\"tags\":");
        appendJsonValue(json, payload.tags());
        if (payload.exception() != null) {
            json.append(',').append("\"exception\":{")
                .append("\"type\":").append(jsonString(payload.exception().type())).append(',')
                .append("\"message\":").append(jsonString(payload.exception().message())).append(',')
                .append("\"stackTrace\":").append(jsonString(payload.exception().stackTrace()))
                .append('}');
        }
        json.append('}');
    }

    private void appendJsonValue(StringBuilder json, Object value) {
        if (value == null) {
            json.append("null");
        } else if (value instanceof String text) {
            json.append(jsonString(text));
        } else if (value instanceof Number || value instanceof Boolean) {
            json.append(value);
        } else if (value instanceof Map<?, ?> map) {
            json.append('{');
            int index = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (index++ > 0) {
                    json.append(',');
                }
                json.append(jsonString(String.valueOf(entry.getKey()))).append(':');
                appendJsonValue(json, entry.getValue());
            }
            json.append('}');
        } else if (value instanceof Iterable<?> iterable) {
            json.append('[');
            int index = 0;
            for (Object item : iterable) {
                if (index++ > 0) {
                    json.append(',');
                }
                appendJsonValue(json, item);
            }
            json.append(']');
        } else {
            json.append(jsonString(String.valueOf(value)));
        }
    }

    private String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder escaped = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.append('"').toString();
    }

    private String jsonField(String body, String field) {
        if (body == null) {
            return null;
        }
        String marker = "\"" + field + "\":";
        int start = body.indexOf(marker);
        if (start < 0) {
            marker = "\"" + field + "\": \"";
            start = body.indexOf(marker);
            if (start < 0) {
                return null;
            }
            start += marker.length();
        } else {
            start += marker.length();
            while (start < body.length() && Character.isWhitespace(body.charAt(start))) {
                start++;
            }
            if (start >= body.length() || body.charAt(start) != '"') {
                return null;
            }
            start++;
        }
        StringBuilder result = new StringBuilder();
        boolean escaped = false;
        for (int i = start; i < body.length(); i++) {
            char character = body.charAt(i);
            if (escaped) {
                result.append(character);
                escaped = false;
            } else if (character == '\\') {
                escaped = true;
            } else if (character == '"') {
                return result.toString();
            } else {
                result.append(character);
            }
        }
        return null;
    }

    private LogSubmissionResult publishImmediate(LogSubmissionResult result) {
        publish(result);
        return result;
    }

    private void publish(LogSubmissionResult result) {
        try {
            config.getResultListener().accept(result);
        } catch (RuntimeException ignored) {
            // Application callbacks must not stop ingestion or expose payloads.
        }
    }

    private void dropRemaining(String errorCode, String message) {
        QueuedEvent event;
        while ((event = queue.poll()) != null) {
            publish(new LogSubmissionResult(
                event.payload().eventId(),
                LogSubmissionOutcome.DROPPED_BY_POLICY,
                0,
                null,
                errorCode,
                message
            ));
            pendingEvents.decrementAndGet();
        }
        notifyFlushWaiters();
    }

    private void notifyFlushWaiters() {
        synchronized (flushMonitor) {
            flushMonitor.notifyAll();
        }
    }

    private long waitForFirstEventNanos() {
        if (!running.get()) {
            long remaining = closeDeadlineNanos - System.nanoTime();
            return Math.max(1, Math.min(TimeUnit.MILLISECONDS.toNanos(config.getMaxWaitMs()), remaining));
        }
        return TimeUnit.MILLISECONDS.toNanos(config.getMaxWaitMs());
    }

    private boolean closeDeadlineExpired() {
        return !running.get() && System.nanoTime() >= closeDeadlineNanos;
    }

    private String boundText(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        if (maxLength <= 15) {
            return text.substring(0, maxLength);
        }
        return text.substring(0, maxLength - 15) + "... [truncated]";
    }

    private String nonBlankOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private long safeDouble(long value) {
        return value > config.getMaxBackoffMs() / 2 ? config.getMaxBackoffMs() : value * 2;
    }

    private long safeAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private long safeMultiply(long left, long right) {
        if (left < 0 || right < 0) {
            return 0;
        }
        if (left == 0 || right == 0) {
            return 0;
        }
        if (left > Long.MAX_VALUE / right) {
            return Long.MAX_VALUE;
        }
        return left * right;
    }

    private record QueuedEvent(LogEventPayload payload) { }

    private record BatchResult(
        LogSubmissionOutcome outcome,
        int httpStatus,
        String serverRequestId,
        String errorCode,
        String message
    ) {
        static BatchResult accepted(int status, String requestId) {
            return new BatchResult(
                LogSubmissionOutcome.ACCEPTED_BY_SERVER_ADMISSION,
                status,
                requestId,
                null,
                "Server admitted the batch to its bounded in-memory queue; persistence is asynchronous"
            );
        }

        static BatchResult rejected(int status, String code, String message) {
            return new BatchResult(
                LogSubmissionOutcome.REJECTED_SERVER,
                status,
                null,
                code == null ? "SERVER_REJECTED" : code,
                message == null ? "The server rejected the batch" : message
            );
        }

        static BatchResult retryExhausted(int status, String code, String message) {
            return new BatchResult(
                LogSubmissionOutcome.RETRY_EXHAUSTED,
                status,
                null,
                code == null ? "RETRY_EXHAUSTED" : code,
                message == null ? "The batch failed after bounded retries" : message
            );
        }

        static BatchResult dropped(String code, String message) {
            return new BatchResult(LogSubmissionOutcome.DROPPED_BY_POLICY, 0, null, code, message);
        }
    }
}
