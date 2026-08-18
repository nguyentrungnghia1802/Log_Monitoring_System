package com.example.logmonitor.starter;

import com.example.logmonitor.sdk.LogEventPayload;
import com.example.logmonitor.sdk.LogMonitoringOperations;
import com.example.logmonitor.sdk.LogSubmissionOutcome;
import com.example.logmonitor.sdk.LogSubmissionResult;

import java.util.Map;

/**
 * Safe local-test implementation used when the starter is not explicitly
 * enabled. It never starts a worker and never performs network I/O.
 */
public final class NoopLogMonitoringOperations implements LogMonitoringOperations {

    @Override
    public boolean log(String level, String eventType, String message) {
        return false;
    }

    @Override
    public boolean log(
        String level,
        String eventType,
        String message,
        String traceId,
        String requestId,
        LogEventPayload.ExceptionPayload exception,
        Map<String, Object> context
    ) {
        return false;
    }

    @Override
    public boolean error(String eventType, String message, Throwable throwable) {
        return false;
    }

    @Override
    public boolean error(
        String eventType,
        String message,
        Throwable throwable,
        String traceId,
        String requestId,
        Map<String, Object> context,
        Map<String, Object> tags
    ) {
        return false;
    }

    @Override
    public LogSubmissionResult submit(LogEventPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("payload must not be null");
        }
        return new LogSubmissionResult(
            payload.eventId(),
            LogSubmissionOutcome.DROPPED_BY_POLICY,
            0,
            null,
            "CLIENT_DISABLED",
            "Log monitoring is disabled; no network request was made"
        );
    }

    @Override
    public boolean flush() {
        return true;
    }
}
