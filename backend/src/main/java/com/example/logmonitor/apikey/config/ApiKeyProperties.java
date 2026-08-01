package com.example.logmonitor.apikey.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "api-key")
public class ApiKeyProperties {

    private int requestsPerSecond = 100;
    private int burstCapacity = 200;
    private long lastUsedUpdateIntervalSeconds = 60;

    public int getRequestsPerSecond() { return requestsPerSecond; }
    public void setRequestsPerSecond(int requestsPerSecond) { this.requestsPerSecond = requestsPerSecond; }

    public int getBurstCapacity() { return burstCapacity; }
    public void setBurstCapacity(int burstCapacity) { this.burstCapacity = burstCapacity; }

    public long getLastUsedUpdateIntervalSeconds() { return lastUsedUpdateIntervalSeconds; }
    public void setLastUsedUpdateIntervalSeconds(long lastUsedUpdateIntervalSeconds) {
        this.lastUsedUpdateIntervalSeconds = lastUsedUpdateIntervalSeconds;
    }
}
