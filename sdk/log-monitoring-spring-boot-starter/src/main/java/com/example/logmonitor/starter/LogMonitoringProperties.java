package com.example.logmonitor.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "log-monitoring.client")
public class LogMonitoringProperties {
    private boolean enabled = true;
    private String endpoint = "http://localhost:8080";
    private String apiKey;
    private String service = "spring-boot-service";
    private String environment = "production";
    private int queueCapacity = 5000;
    private int batchSize = 100;
    private long maxWaitMs = 500;

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
}
