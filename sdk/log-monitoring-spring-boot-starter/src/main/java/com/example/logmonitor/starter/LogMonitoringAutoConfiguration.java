package com.example.logmonitor.starter;

import com.example.logmonitor.sdk.LogMonitoringClient;
import com.example.logmonitor.sdk.LogMonitoringOperations;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(LogMonitoringProperties.class)
public class LogMonitoringAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "log-monitoring.client", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean({LogMonitoringClient.class, LogMonitoringOperations.class})
    public LogMonitoringClient logMonitoringClient(
        LogMonitoringProperties props,
        ObjectProvider<LogMonitoringMetricsListener> metricsListenerProvider
    ) {
        LogMonitoringMetricsListener metricsListener = metricsListenerProvider.getIfAvailable();
        var config = props.toClientConfig();
        if (metricsListener != null) {
            config.setResultListener(metricsListener);
        }
        LogMonitoringClient client = new LogMonitoringClient(config);
        if (metricsListener != null) {
            metricsListener.bind(client);
        }
        return client;
    }

    @Bean
    @ConditionalOnProperty(
        prefix = "log-monitoring.client",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true
    )
    @ConditionalOnMissingBean(LogMonitoringOperations.class)
    public NoopLogMonitoringOperations noOpLogMonitoringOperations() {
        return new NoopLogMonitoringOperations();
    }

    @Bean
    @ConditionalOnMissingBean(LogMonitoringHealthIndicator.class)
    public LogMonitoringHealthIndicator logMonitoringHealthIndicator(
        LogMonitoringProperties props,
        ObjectProvider<LogMonitoringClient> clientProvider
    ) {
        return new LogMonitoringHealthIndicator(props, clientProvider);
    }

    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean
    public LogMonitoringMetricsListener logMonitoringMetricsListener(MeterRegistry registry) {
        return new LogMonitoringMetricsListener(registry);
    }
}
