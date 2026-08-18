package com.example.logmonitor.starter;

import com.example.logmonitor.sdk.LogMonitoringClient;
import com.example.logmonitor.sdk.LogSubmissionOutcome;
import com.example.logmonitor.sdk.LogSubmissionResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Low-cardinality Micrometer bridge for SDK outcomes and queue health.
 */
public final class LogMonitoringMetricsListener implements Consumer<LogSubmissionResult> {
    private final MeterRegistry registry;
    private final Map<LogSubmissionOutcome, Counter> submissionCounters = new EnumMap<>(LogSubmissionOutcome.class);
    private final AtomicBoolean gaugesBound = new AtomicBoolean(false);

    public LogMonitoringMetricsListener(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
        for (LogSubmissionOutcome outcome : LogSubmissionOutcome.values()) {
            submissionCounters.put(outcome, Counter.builder("log.monitoring.sdk.submissions")
                .description("Log Monitoring SDK submission outcomes")
                .tag("outcome", outcome.name().toLowerCase())
                .register(registry));
        }
    }

    @Override
    public void accept(LogSubmissionResult result) {
        if (result != null && result.outcome() != null) {
            Counter counter = submissionCounters.get(result.outcome());
            if (counter != null) {
                counter.increment();
            }
        }
    }

    public void bind(LogMonitoringClient client) {
        Objects.requireNonNull(client, "client");
        if (!gaugesBound.compareAndSet(false, true)) {
            return;
        }
        Gauge.builder("log.monitoring.sdk.queue.depth", client, LogMonitoringClient::queuedEventCount)
            .description("Events awaiting a final SDK outcome")
            .register(registry);
        Gauge.builder("log.monitoring.sdk.queue.capacity", client, LogMonitoringClient::queueCapacity)
            .description("Configured local SDK queue capacity")
            .register(registry);
        Gauge.builder("log.monitoring.sdk.running", client, current -> current.isRunning() ? 1 : 0)
            .description("Whether the SDK worker accepts events")
            .register(registry);
    }
}
