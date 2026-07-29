package com.example.logmonitor.sdk;

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

    public LogMonitoringClientConfig() {}

    public String getEndpoint() { return endpoint; }
    public LogMonitoringClientConfig setEndpoint(String endpoint) { this.endpoint = endpoint; return this; }

    public String getApiKey() { return apiKey; }
    public LogMonitoringClientConfig setApiKey(String apiKey) { this.apiKey = apiKey; return this; }

    public String getService() { return service; }
    public LogMonitoringClientConfig setService(String service) { this.service = service; return this; }

    public String getEnvironment() { return environment; }
    public LogMonitoringClientConfig setEnvironment(String environment) { this.environment = environment; return this; }

    public int getQueueCapacity() { return queueCapacity; }
    public LogMonitoringClientConfig setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; return this; }

    public int getBatchSize() { return batchSize; }
    public LogMonitoringClientConfig setBatchSize(int batchSize) { this.batchSize = batchSize; return this; }

    public long getMaxWaitMs() { return maxWaitMs; }
    public LogMonitoringClientConfig setMaxWaitMs(long maxWaitMs) { this.maxWaitMs = maxWaitMs; return this; }

    public int getMaxRetries() { return maxRetries; }
    public LogMonitoringClientConfig setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; return this; }

    public long getBackoffMs() { return backoffMs; }
    public LogMonitoringClientConfig setBackoffMs(long backoffMs) { this.backoffMs = backoffMs; return this; }
}
