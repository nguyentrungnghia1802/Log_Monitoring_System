package com.example.logmonitor.sdk;

/** The lifecycle outcome reported for one SDK event. */
public enum LogSubmissionOutcome {
    /** The event is accepted by the local bounded SDK queue. */
    QUEUED_LOCALLY,
    /** The server returned 202 and admitted the event to its bounded queue. */
    ACCEPTED_BY_SERVER_ADMISSION,
    /** The local bounded queue was full. */
    REJECTED_LOCAL_QUEUE,
    /** The server returned a non-retryable validation/auth/client response. */
    REJECTED_SERVER,
    /** All retry attempts for a retryable response or transport error failed. */
    RETRY_EXHAUSTED,
    /** The SDK dropped an event because a local shutdown/flush policy expired. */
    DROPPED_BY_POLICY
}
