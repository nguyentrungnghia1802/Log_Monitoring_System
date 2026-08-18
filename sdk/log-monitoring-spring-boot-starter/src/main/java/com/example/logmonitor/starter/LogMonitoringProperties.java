package com.example.logmonitor.starter;

import com.example.logmonitor.sdk.LogMonitoringClientConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized, bounded settings for the Java SDK.
 *
 * <p>The integration is disabled by default. Applications must opt in
 * explicitly so adding the starter to a local test cannot send events to an
 * unexpected endpoint.</p>
 */
@ConfigurationProperties(prefix = "log-monitoring.client")
public class LogMonitoringProperties {
    private boolean enabled = false;
    private String endpoint = "http://localhost:8080";
    private String apiKey;
    private String service = "spring-boot-service";
    private String environment = "production";
    private int queueCapacity = 5_000;
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

    public LogMonitoringClientConfig toClientConfig() {
        return new LogMonitoringClientConfig()
            .setEndpoint(endpoint)
            .setApiKey(apiKey)
            .setService(service)
            .setEnvironment(environment)
            .setQueueCapacity(queueCapacity)
            .setBatchSize(batchSize)
            .setMaxWaitMs(maxWaitMs)
            .setMaxRetries(maxRetries)
            .setBackoffMs(backoffMs)
            .setMaxBackoffMs(maxBackoffMs)
            .setJitterMs(jitterMs)
            .setMaxRetryAfterMs(maxRetryAfterMs)
            .setRequestTimeoutMs(requestTimeoutMs)
            .setFlushTimeoutMs(flushTimeoutMs)
            .setMaxMessageLength(maxMessageLength)
            .setMaxExceptionMessageLength(maxExceptionMessageLength)
            .setMaxStackTraceLength(maxStackTraceLength)
            .setMaxContextEntries(maxContextEntries)
            .setMaxTagEntries(maxTagEntries)
            .setMaxContextKeyLength(maxContextKeyLength)
            .setMaxContextValueLength(maxContextValueLength);
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getService() { return service; }
    public void setService(String service) { this.service = service; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public int getQueueCapacity() { return queueCapacity; }
    public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public long getMaxWaitMs() { return maxWaitMs; }
    public void setMaxWaitMs(long maxWaitMs) { this.maxWaitMs = maxWaitMs; }
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    public long getBackoffMs() { return backoffMs; }
    public void setBackoffMs(long backoffMs) { this.backoffMs = backoffMs; }
    public long getMaxBackoffMs() { return maxBackoffMs; }
    public void setMaxBackoffMs(long maxBackoffMs) { this.maxBackoffMs = maxBackoffMs; }
    public long getJitterMs() { return jitterMs; }
    public void setJitterMs(long jitterMs) { this.jitterMs = jitterMs; }
    public long getMaxRetryAfterMs() { return maxRetryAfterMs; }
    public void setMaxRetryAfterMs(long maxRetryAfterMs) { this.maxRetryAfterMs = maxRetryAfterMs; }
    public long getRequestTimeoutMs() { return requestTimeoutMs; }
    public void setRequestTimeoutMs(long requestTimeoutMs) { this.requestTimeoutMs = requestTimeoutMs; }
    public long getFlushTimeoutMs() { return flushTimeoutMs; }
    public void setFlushTimeoutMs(long flushTimeoutMs) { this.flushTimeoutMs = flushTimeoutMs; }
    public int getMaxMessageLength() { return maxMessageLength; }
    public void setMaxMessageLength(int maxMessageLength) { this.maxMessageLength = maxMessageLength; }
    public int getMaxExceptionMessageLength() { return maxExceptionMessageLength; }
    public void setMaxExceptionMessageLength(int maxExceptionMessageLength) { this.maxExceptionMessageLength = maxExceptionMessageLength; }
    public int getMaxStackTraceLength() { return maxStackTraceLength; }
    public void setMaxStackTraceLength(int maxStackTraceLength) { this.maxStackTraceLength = maxStackTraceLength; }
    public int getMaxContextEntries() { return maxContextEntries; }
    public void setMaxContextEntries(int maxContextEntries) { this.maxContextEntries = maxContextEntries; }
    public int getMaxTagEntries() { return maxTagEntries; }
    public void setMaxTagEntries(int maxTagEntries) { this.maxTagEntries = maxTagEntries; }
    public int getMaxContextKeyLength() { return maxContextKeyLength; }
    public void setMaxContextKeyLength(int maxContextKeyLength) { this.maxContextKeyLength = maxContextKeyLength; }
    public int getMaxContextValueLength() { return maxContextValueLength; }
    public void setMaxContextValueLength(int maxContextValueLength) { this.maxContextValueLength = maxContextValueLength; }
}
