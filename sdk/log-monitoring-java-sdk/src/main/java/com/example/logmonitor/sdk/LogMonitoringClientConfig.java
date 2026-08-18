package com.example.logmonitor.sdk;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Bounded configuration for the Java source SDK.
 *
 * <p>All limits are deliberately local to the SDK. A successful HTTP 202 is
 * still only server admission to the platform's bounded in-memory queue; it
 * is never reported as durable MongoDB persistence.</p>
 */
public class LogMonitoringClientConfig {
    private String endpoint = "http://localhost:8080";
    private String apiKey;
    private String service = "java-application";
    private String environment = "production";
    private int queueCapacity = 5000;
    private int batchSize = 100;
    private long maxWaitMs = 500;
    private int maxRetries = 3;
    private long backoffMs = 500;
    private long maxBackoffMs = 30_000;
    private long jitterMs = 100;
    private long maxRetryAfterMs = 30_000;
    private long requestTimeoutMs = 2_000;
    private long flushTimeoutMs = 5_000;
    private int maxMessageLength = 4_000;
    private int maxExceptionMessageLength = 1_000;
    private int maxStackTraceLength = 4_000;
    private int maxContextEntries = 32;
    private int maxTagEntries = 32;
    private int maxContextKeyLength = 64;
    private int maxContextValueLength = 512;
    private Consumer<LogSubmissionResult> resultListener = result -> { };

    public String getEndpoint() { return endpoint; }

    public LogMonitoringClientConfig setEndpoint(String endpoint) {
        this.endpoint = requireText(endpoint, "endpoint");
        return this;
    }

    public String getApiKey() { return apiKey; }

    public LogMonitoringClientConfig setApiKey(String apiKey) {
        this.apiKey = apiKey;
        return this;
    }

    public String getService() { return service; }

    public LogMonitoringClientConfig setService(String service) {
        this.service = requireText(service, "service");
        return this;
    }

    public String getEnvironment() { return environment; }

    public LogMonitoringClientConfig setEnvironment(String environment) {
        this.environment = requireText(environment, "environment");
        return this;
    }

    public int getQueueCapacity() { return queueCapacity; }

    public LogMonitoringClientConfig setQueueCapacity(int queueCapacity) {
        this.queueCapacity = requirePositive(queueCapacity, "queueCapacity");
        return this;
    }

    public int getBatchSize() { return batchSize; }

    public LogMonitoringClientConfig setBatchSize(int batchSize) {
        this.batchSize = requirePositive(batchSize, "batchSize");
        return this;
    }

    public long getMaxWaitMs() { return maxWaitMs; }

    public LogMonitoringClientConfig setMaxWaitMs(long maxWaitMs) {
        this.maxWaitMs = requirePositive(maxWaitMs, "maxWaitMs");
        return this;
    }

    /** Maximum number of HTTP attempts for one batch, including the first. */
    public int getMaxRetries() { return maxRetries; }

    public LogMonitoringClientConfig setMaxRetries(int maxRetries) {
        this.maxRetries = requirePositive(maxRetries, "maxRetries");
        return this;
    }

    public long getBackoffMs() { return backoffMs; }

    public LogMonitoringClientConfig setBackoffMs(long backoffMs) {
        this.backoffMs = requireNonNegative(backoffMs, "backoffMs");
        return this;
    }

    public long getMaxBackoffMs() { return maxBackoffMs; }

    public LogMonitoringClientConfig setMaxBackoffMs(long maxBackoffMs) {
        this.maxBackoffMs = requireNonNegative(maxBackoffMs, "maxBackoffMs");
        return this;
    }

    public long getJitterMs() { return jitterMs; }

    public LogMonitoringClientConfig setJitterMs(long jitterMs) {
        this.jitterMs = requireNonNegative(jitterMs, "jitterMs");
        return this;
    }

    public long getMaxRetryAfterMs() { return maxRetryAfterMs; }

    public LogMonitoringClientConfig setMaxRetryAfterMs(long maxRetryAfterMs) {
        this.maxRetryAfterMs = requireNonNegative(maxRetryAfterMs, "maxRetryAfterMs");
        return this;
    }

    public long getRequestTimeoutMs() { return requestTimeoutMs; }

    public LogMonitoringClientConfig setRequestTimeoutMs(long requestTimeoutMs) {
        this.requestTimeoutMs = requirePositive(requestTimeoutMs, "requestTimeoutMs");
        return this;
    }

    public long getFlushTimeoutMs() { return flushTimeoutMs; }

    public LogMonitoringClientConfig setFlushTimeoutMs(long flushTimeoutMs) {
        this.flushTimeoutMs = requirePositive(flushTimeoutMs, "flushTimeoutMs");
        return this;
    }

    public int getMaxMessageLength() { return maxMessageLength; }

    public LogMonitoringClientConfig setMaxMessageLength(int maxMessageLength) {
        this.maxMessageLength = requirePositive(maxMessageLength, "maxMessageLength");
        return this;
    }

    public int getMaxExceptionMessageLength() { return maxExceptionMessageLength; }

    public LogMonitoringClientConfig setMaxExceptionMessageLength(int maxExceptionMessageLength) {
        this.maxExceptionMessageLength = requirePositive(maxExceptionMessageLength, "maxExceptionMessageLength");
        return this;
    }

    public int getMaxStackTraceLength() { return maxStackTraceLength; }

    public LogMonitoringClientConfig setMaxStackTraceLength(int maxStackTraceLength) {
        this.maxStackTraceLength = requirePositive(maxStackTraceLength, "maxStackTraceLength");
        return this;
    }

    public int getMaxContextEntries() { return maxContextEntries; }

    public LogMonitoringClientConfig setMaxContextEntries(int maxContextEntries) {
        this.maxContextEntries = requirePositive(maxContextEntries, "maxContextEntries");
        return this;
    }

    public int getMaxTagEntries() { return maxTagEntries; }

    public LogMonitoringClientConfig setMaxTagEntries(int maxTagEntries) {
        this.maxTagEntries = requirePositive(maxTagEntries, "maxTagEntries");
        return this;
    }

    public int getMaxContextKeyLength() { return maxContextKeyLength; }

    public LogMonitoringClientConfig setMaxContextKeyLength(int maxContextKeyLength) {
        this.maxContextKeyLength = requirePositive(maxContextKeyLength, "maxContextKeyLength");
        return this;
    }

    public int getMaxContextValueLength() { return maxContextValueLength; }

    public LogMonitoringClientConfig setMaxContextValueLength(int maxContextValueLength) {
        this.maxContextValueLength = requirePositive(maxContextValueLength, "maxContextValueLength");
        return this;
    }

    public Consumer<LogSubmissionResult> getResultListener() { return resultListener; }

    public LogMonitoringClientConfig setResultListener(Consumer<LogSubmissionResult> resultListener) {
        this.resultListener = Objects.requireNonNull(resultListener, "resultListener");
        return this;
    }

    void validate() {
        requireText(endpoint, "endpoint");
        requireText(service, "service");
        requireText(environment, "environment");
        if (maxBackoffMs < backoffMs) {
            throw new IllegalArgumentException("maxBackoffMs must be >= backoffMs");
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static long requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static long requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }
}
