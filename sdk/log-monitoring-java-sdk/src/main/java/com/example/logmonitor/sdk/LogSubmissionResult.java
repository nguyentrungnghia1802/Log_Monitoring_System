package com.example.logmonitor.sdk;

/**
 * Result delivered to the SDK callback. A result is per event even when the
 * transport sends a batch, so applications can count failures without
 * retaining the event payload or secret API key.
 */
public record LogSubmissionResult(
    String eventId,
    LogSubmissionOutcome outcome,
    int httpStatus,
    String serverRequestId,
    String errorCode,
    String message
) {
    public boolean isQueuedLocally() {
        return outcome == LogSubmissionOutcome.QUEUED_LOCALLY;
    }

    public boolean acceptedByServer() {
        return outcome == LogSubmissionOutcome.ACCEPTED_BY_SERVER_ADMISSION;
    }
}
