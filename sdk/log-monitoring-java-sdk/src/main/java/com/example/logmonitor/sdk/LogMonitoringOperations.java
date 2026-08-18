package com.example.logmonitor.sdk;

import java.util.Map;

/**
 * Application-facing logging contract shared by the real client and the
 * Spring Boot starter's disabled-mode implementation.
 */
public interface LogMonitoringOperations {

    boolean log(String level, String eventType, String message);

    boolean log(
        String level,
        String eventType,
        String message,
        String traceId,
        String requestId,
        LogEventPayload.ExceptionPayload exception,
        Map<String, Object> context
    );

    boolean error(String eventType, String message, Throwable throwable);

    boolean error(
        String eventType,
        String message,
        Throwable throwable,
        String traceId,
        String requestId,
        Map<String, Object> context,
        Map<String, Object> tags
    );

    LogSubmissionResult submit(LogEventPayload payload);

    boolean flush();
}
