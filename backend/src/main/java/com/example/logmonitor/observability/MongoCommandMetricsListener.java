package com.example.logmonitor.observability;

import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandSucceededEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Records low-cardinality MongoDB command timing and failure metrics.
 *
 * <p>Only a bounded set of command names is used as a tag. Error messages,
 * database names, request IDs, and collection names are deliberately omitted
 * because they can contain sensitive data or grow without bound.</p>
 */
public final class MongoCommandMetricsListener implements CommandListener {

    private static final Set<String> KNOWN_COMMANDS = Set.of(
        "aggregate", "buildInfo", "count", "create", "delete", "distinct", "drop",
        "endSessions", "find", "getMore", "hello", "insert", "isMaster", "listCollections",
        "listIndexes", "ping", "update"
    );

    private final MeterRegistry meterRegistry;

    public MongoCommandMetricsListener(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void commandSucceeded(CommandSucceededEvent event) {
        recordDuration(event.getCommandName(), "success", event.getElapsedTime(TimeUnit.NANOSECONDS));
    }

    @Override
    public void commandFailed(CommandFailedEvent event) {
        recordDuration(event.getCommandName(), "failure", event.getElapsedTime(TimeUnit.NANOSECONDS));
        Counter.builder("mongodb.command.errors")
            .description("MongoDB commands that failed")
            .tag("command", normalizeCommand(event.getCommandName()))
            .register(meterRegistry)
            .increment();
    }

    private void recordDuration(String commandName, String outcome, long elapsedNanos) {
        Timer.builder("mongodb.command.duration")
            .description("MongoDB command execution duration")
            .tag("command", normalizeCommand(commandName))
            .tag("outcome", outcome)
            .register(meterRegistry)
            .record(elapsedNanos, TimeUnit.NANOSECONDS);
    }

    private String normalizeCommand(String commandName) {
        return KNOWN_COMMANDS.contains(commandName) ? commandName : "other";
    }
}
