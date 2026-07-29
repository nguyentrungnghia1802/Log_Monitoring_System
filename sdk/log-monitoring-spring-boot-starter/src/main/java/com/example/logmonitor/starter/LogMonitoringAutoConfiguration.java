package com.example.logmonitor.starter;

import com.example.logmonitor.sdk.LogMonitoringClient;
import com.example.logmonitor.sdk.LogMonitoringClientConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(LogMonitoringProperties.class)
@ConditionalOnProperty(prefix = "log-monitoring.client", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LogMonitoringAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public LogMonitoringClient logMonitoringClient(LogMonitoringProperties props) {
        LogMonitoringClientConfig config = new LogMonitoringClientConfig()
            .setEndpoint(props.getEndpoint())
            .setApiKey(props.getApiKey())
            .setService(props.getService())
            .setEnvironment(props.getEnvironment())
            .setQueueCapacity(props.getQueueCapacity())
            .setBatchSize(props.getBatchSize())
            .setMaxWaitMs(props.getMaxWaitMs());

        return new LogMonitoringClient(config);
    }
}
