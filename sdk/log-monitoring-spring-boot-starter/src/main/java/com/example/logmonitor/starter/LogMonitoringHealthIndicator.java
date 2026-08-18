package com.example.logmonitor.starter;

import com.example.logmonitor.sdk.LogMonitoringClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Reports SDK readiness without exposing endpoint, service, or API-key data.
 */
public final class LogMonitoringHealthIndicator implements HealthIndicator {
    private final LogMonitoringProperties properties;
    private final ObjectProvider<LogMonitoringClient> clientProvider;

    public LogMonitoringHealthIndicator(
        LogMonitoringProperties properties,
        ObjectProvider<LogMonitoringClient> clientProvider
    ) {
        this.properties = properties;
        this.clientProvider = clientProvider;
    }

    @Override
    public Health health() {
        if (!properties.isEnabled()) {
            return Health.up()
                .withDetail("enabled", false)
                .withDetail("mode", "no-op")
                .build();
        }

        LogMonitoringClient client = clientProvider.getIfAvailable();
        if (client == null || !client.isRunning()) {
            return Health.down()
                .withDetail("enabled", true)
                .withDetail("reason", "client-not-running")
                .build();
        }

        return Health.up()
            .withDetail("enabled", true)
            .withDetail("queueDepth", client.queuedEventCount())
            .withDetail("queueCapacity", client.queueCapacity())
            .build();
    }
}
