package com.example.logmonitor.starter;

import com.example.logmonitor.sdk.LogEventPayload;
import com.example.logmonitor.sdk.LogMonitoringClient;
import com.example.logmonitor.sdk.LogMonitoringOperations;
import com.example.logmonitor.sdk.LogSubmissionOutcome;
import com.example.logmonitor.sdk.LogSubmissionResult;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class LogMonitoringAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(LogMonitoringAutoConfiguration.class));

    @Test
    void defaultsToNoOpWithoutStartingAWorkerOrMakingNetworkCalls() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(LogMonitoringClient.class);
            assertThat(context).hasSingleBean(NoopLogMonitoringOperations.class);
            assertThat(context).hasSingleBean(LogMonitoringOperations.class);

            LogMonitoringOperations operations = context.getBean(LogMonitoringOperations.class);
            LogSubmissionResult result = operations.submit(new LogEventPayload(
                "disabled-event",
                Instant.now(),
                "INFO",
                "service",
                "test",
                "TEST",
                "message",
                "trace",
                "request",
                null,
                Map.of(),
                Map.of()
            ));
            assertThat(result.outcome()).isEqualTo(LogSubmissionOutcome.DROPPED_BY_POLICY);
            assertThat(result.errorCode()).isEqualTo("CLIENT_DISABLED");

            Health health = context.getBean(HealthIndicator.class).health();
            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails()).containsEntry("mode", "no-op");
        });
    }

    @Test
    void createsConfiguredClientHealthAndMetricsWhenExplicitlyEnabled() {
        AtomicReference<LogMonitoringClient> clientReference = new AtomicReference<>();

        contextRunner
            .withPropertyValues(
                "log-monitoring.client.enabled=true",
                "log-monitoring.client.endpoint=http://localhost:1",
                "log-monitoring.client.api-key=test-key",
                "log-monitoring.client.service=checkout",
                "log-monitoring.client.environment=test",
                "log-monitoring.client.queue-capacity=321",
                "log-monitoring.client.batch-size=7",
                "log-monitoring.client.max-retries=2",
                "log-monitoring.client.max-backoff-ms=2000"
            )
            .withUserConfiguration(MeterRegistryConfiguration.class)
            .run(context -> {
                LogMonitoringClient client = context.getBean(LogMonitoringClient.class);
                clientReference.set(client);
                assertThat(context.getBean(LogMonitoringOperations.class)).isSameAs(client);
                assertThat(context).doesNotHaveBean(NoopLogMonitoringOperations.class);

                LogMonitoringProperties properties = context.getBean(LogMonitoringProperties.class);
                assertThat(properties.isEnabled()).isTrue();
                assertThat(properties.getQueueCapacity()).isEqualTo(321);
                assertThat(properties.getBatchSize()).isEqualTo(7);
                assertThat(properties.getMaxRetries()).isEqualTo(2);

                Health health = context.getBean(HealthIndicator.class).health();
                assertThat(health.getStatus()).isEqualTo(Status.UP);
                assertThat(health.getDetails()).containsEntry("queueCapacity", 321);

                MeterRegistry registry = context.getBean(MeterRegistry.class);
                assertThat(registry.find("log.monitoring.sdk.queue.capacity").gauge()).isNotNull();
                assertThat(registry.find("log.monitoring.sdk.queue.capacity").gauge().value()).isEqualTo(321.0);
                assertThat(registry.find("log.monitoring.sdk.running").gauge()).isNotNull();
                assertThat(registry.find("log.monitoring.sdk.submissions")
                    .tag("outcome", "queued_locally").counter()).isNotNull();
            });

        assertThat(clientReference.get()).isNotNull();
        assertThat(clientReference.get().isRunning()).isFalse();
    }

    @Test
    void mapsAllSdkBoundsToClientConfiguration() {
        LogMonitoringProperties properties = new LogMonitoringProperties();
        properties.setEndpoint("https://logs.example.test");
        properties.setService("billing");
        properties.setEnvironment("staging");
        properties.setMaxRetries(4);
        properties.setBackoffMs(25);
        properties.setMaxBackoffMs(250);
        properties.setJitterMs(5);
        properties.setMaxRetryAfterMs(600);
        properties.setRequestTimeoutMs(700);
        properties.setFlushTimeoutMs(800);
        properties.setMaxMessageLength(90);
        properties.setMaxExceptionMessageLength(91);
        properties.setMaxStackTraceLength(92);
        properties.setMaxContextEntries(9);
        properties.setMaxTagEntries(8);
        properties.setMaxContextKeyLength(7);
        properties.setMaxContextValueLength(6);

        assertThat(properties.toClientConfig().getEndpoint()).isEqualTo("https://logs.example.test");
        assertThat(properties.toClientConfig().getService()).isEqualTo("billing");
        assertThat(properties.toClientConfig().getEnvironment()).isEqualTo("staging");
        assertThat(properties.toClientConfig().getMaxRetries()).isEqualTo(4);
        assertThat(properties.toClientConfig().getBackoffMs()).isEqualTo(25);
        assertThat(properties.toClientConfig().getMaxBackoffMs()).isEqualTo(250);
        assertThat(properties.toClientConfig().getJitterMs()).isEqualTo(5);
        assertThat(properties.toClientConfig().getMaxRetryAfterMs()).isEqualTo(600);
        assertThat(properties.toClientConfig().getRequestTimeoutMs()).isEqualTo(700);
        assertThat(properties.toClientConfig().getFlushTimeoutMs()).isEqualTo(800);
        assertThat(properties.toClientConfig().getMaxMessageLength()).isEqualTo(90);
        assertThat(properties.toClientConfig().getMaxExceptionMessageLength()).isEqualTo(91);
        assertThat(properties.toClientConfig().getMaxStackTraceLength()).isEqualTo(92);
        assertThat(properties.toClientConfig().getMaxContextEntries()).isEqualTo(9);
        assertThat(properties.toClientConfig().getMaxTagEntries()).isEqualTo(8);
        assertThat(properties.toClientConfig().getMaxContextKeyLength()).isEqualTo(7);
        assertThat(properties.toClientConfig().getMaxContextValueLength()).isEqualTo(6);
    }

    @Configuration(proxyBeanMethods = false)
    static class MeterRegistryConfiguration {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
