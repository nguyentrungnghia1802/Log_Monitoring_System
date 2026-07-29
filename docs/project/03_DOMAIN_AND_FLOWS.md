# Domain and Flows

## 1. Domain model

```text
Organization
  |--< Membership >-- User
  |--< Project
  |      |--< ApiKey
  |      |--< Service
  |      |--< AlertRule
  |      |      \--< AlertOccurrence
  |      |--< LogEvent
  |      \--< DashboardPreference
  |
  \--< AuditEvent
```

Operational ingestion also uses non-persistent V1 runtime objects:

```text
IngestionEnvelope
    -> BoundedIngestionQueue
        -> PersistenceBatch
```

---

## 2. Entity responsibilities

| Entity | Responsibility |
| --- | --- |
| Organization | Tenant boundary |
| User | Management identity |
| Membership | User role inside an organization/project |
| Project | Monitored application/product |
| Service | Logical runtime component/source |
| ApiKey | Source-application credential and ingestion scope |
| LogEvent | Immutable normalized event |
| AlertRule | Persistent threshold rule |
| AlertOccurrence | One triggered incident/alert instance |
| AuditEvent | Sensitive configuration trace |
| DashboardPreference | Optional saved filters/views |

---

## 3. Log event state

The product treats persisted logs as immutable.

Runtime processing state is not persisted into the log document as a long state machine.

```text
HTTP_RECEIVED
 -> VALIDATED
 -> NORMALIZED
 -> ADMITTED
 -> BATCHED
 -> PERSISTED
```

Failures:

```text
HTTP_RECEIVED -> REJECTED_VALIDATION
NORMALIZED -> REJECTED_BACKPRESSURE
BATCHED -> RETRYING_PERSISTENCE -> PERSISTED
BATCHED -> TERMINAL_PERSISTENCE_FAILURE
```

`ADMITTED` is process-memory durability only in V1.

---

## 4. API-key lifecycle

| Current | Action | Next |
| --- | --- | --- |
| new | Create | `active` |
| `active` | Rotate | old `revoked`, new `active` |
| `active` | Revoke | `revoked` |
| `revoked` | Use for ingestion | rejected |

Raw secret material is displayed only during creation/rotation.

---

## 5. Alert rule lifecycle

| Current | Action | Next |
| --- | --- | --- |
| new | Create valid rule | `enabled` or `disabled` |
| `enabled` | Disable | `disabled` |
| `disabled` | Enable | `enabled` |
| enabled/disabled | Archive/Delete | `archived` |

Archived rules are not evaluated.

---

## 6. Alert occurrence lifecycle

Recommended values:

```text
triggered
notifying
notified
delivery_failed
acknowledged
resolved
```

V1 does not need automated incident resolution to ship. At minimum it persists trigger and delivery status.

---

## 7. Single log ingestion flow

1. Source application emits one structured event.
2. Client sends `POST /api/v1/ingest/logs` with `X-API-Key`.
3. API-key middleware resolves and validates the active key.
4. Server derives organization/project scope from the key.
5. Request validator enforces payload, depth, field length, timestamp, and level rules.
6. Normalizer creates the internal `LogEvent`.
7. Retention service computes `expireAt`.
8. `queue.offer()` attempts bounded admission.
9. If admission fails, API returns `503 INGESTION_BACKPRESSURE`.
10. If admission succeeds, API returns `202 Accepted` with server ingestion metadata.
11. Worker drains the event later.
12. Worker persists it in a bulk write.
13. Log becomes query/analytics truth.

---

## 8. Batch ingestion flow

1. Source application accumulates a local batch.
2. Client sends `POST /api/v1/ingest/logs/batch`.
3. Whole request is authenticated once.
4. Each event is validated and normalized.
5. V1 uses **all-or-reject admission for the submitted batch** unless a later ADR explicitly introduces partial acceptance.
6. Server checks the batch can be admitted without violating queue capacity policy.
7. Response is `202` with accepted count.

This rule keeps client retry behavior deterministic.

---

## 9. Backpressure flow

```text
Source App
   |
   v
Ingestion API
   |
queue has capacity?
  / \
yes  no
 |    |
202   503 INGESTION_BACKPRESSURE
      Retry-After
```

Source SDK/application policy may:

- retry with backoff;
- keep a local bounded buffer;
- sample low-severity events;
- drop debug events.

The monitoring backend must not silently claim durable acceptance.

---

## 10. Persistence worker flow

1. Worker waits for at least one event.
2. It drains up to `batch.max-size`.
3. It may wait up to `batch.max-wait` to improve batch density.
4. It builds `InsertOneModel` operations.
5. MongoDB bulk write executes.
6. Success updates throughput/latency metrics.
7. Retryable failure uses bounded exponential backoff.
8. Terminal failure records event count and safe error details.

V1 may lose a terminally failed batch unless a later durable failure store is introduced. This must be measurable.

---

## 11. Search flow

1. Authenticated user selects organization/project.
2. UI sends a bounded time range and optional filters.
3. API derives authorized project scope from user membership.
4. Query service builds an index-compatible MongoDB query.
5. Cursor pagination reads one page.
6. Response returns events plus next cursor.
7. Event details are opened by ID within the same project scope.

No endpoint may provide unbounded “download all logs” behavior in V1.

---

## 12. Trace/request correlation flow

For a LINE Smart Queue failure, events may be:

```text
order-service       traceId=T1  ORDER_CREATED
payment-service     traceId=T1  PAYMENT_SUCCESS
webhook-service     traceId=T1  WEBHOOK_RECEIVED
queue-service       traceId=T1  QUEUE_CREATE_FAILED
```

Searching `traceId=T1` returns the chronological cross-service sequence for that authorized project.

The monitoring system does not generate distributed trace spans; it correlates identifiers supplied by source applications.

---

## 13. Analytics flow

```text
React
 -> GET /analytics/timeseries
 -> AnalyticsService
 -> Mongo Aggregation Pipeline
    $match
    $group/$dateTrunc
    $sort
 -> compact bucket DTO
 -> chart
```

Analytics queries must:

- require project scope;
- require bounded range;
- match early;
- project only needed fields;
- use bucket sizes appropriate for the requested range.

---

## 14. Live-tail flow

1. User connects over authenticated WebSocket/STOMP.
2. User subscribes with project and filter parameters.
3. Server verifies project permission.
4. New admitted events are evaluated against active subscriptions.
5. Matching event summaries are delivered.
6. UI can open a persisted event after storage catches up.
7. Disconnect removes subscription state.

Live tail is intentionally not a durable queue.

---

## 15. Alert evaluation flow

Example rule:

```json
{
  "name": "Queue creation failures",
  "service": "queue-service",
  "environment": "production",
  "level": "ERROR",
  "eventType": "QUEUE_CREATE_FAILED",
  "windowSeconds": 60,
  "threshold": 50,
  "cooldownSeconds": 600
}
```

Flow:

1. Eligible event is persisted or included in an authoritative evaluation path.
2. Matcher finds enabled rules.
3. Rule engine updates/query-counts the rule window.
4. Threshold is evaluated.
5. If below threshold, nothing is triggered.
6. If threshold is crossed and rule is not in cooldown, create `AlertOccurrence`.
7. Persist cooldown state.
8. Send through notification adapter.
9. Persist delivery result.
10. Repeated events during cooldown update metrics but do not create duplicate notification storms.

---

## 16. Retention flow

1. Project resolves retention policy.
2. Level override may replace default duration.
3. Server computes `expireAt` during normalization.
4. Event is stored with `expireAt`.
5. MongoDB TTL background cleanup eventually removes expired events.

Example policy:

```text
TRACE  1 day
DEBUG  3 days
INFO   7 days
WARN   14 days
ERROR  30 days
FATAL  90 days
```

Actual values remain configurable.

---

## 17. Failure flows

### Invalid API key

- return `401`;
- do not disclose project existence;
- increment authentication failure metric.

### Payload too large

- return `413`;
- do not enqueue partial data.

### Invalid batch

- return `422`;
- V1 does not partially admit the batch.

### Queue saturated

- return `503 INGESTION_BACKPRESSURE`;
- no event from the rejected request is considered accepted.

### MongoDB unavailable

- readiness `503`;
- workers retry within configured bounds;
- queue depth increases up to capacity;
- new ingestion is rejected once capacity is exhausted.

### WebSocket failure

- persisted flow continues;
- affected client reconnects and queries durable history.

### Alert provider failure

- alert occurrence remains;
- delivery state records failure;
- retry is bounded.

### Process crash

- in-memory admitted-but-unpersisted logs may be lost;
- on restart, queue begins empty;
- this is an accepted V1 limitation and a durable-broker exit criterion.

---

## 18. LINE Smart Queue example

Recommended structured events include:

```text
AUTH_LOGIN_FAILED
ORDER_CREATE_FAILED
PAYMENT_WEBHOOK_FAILED
QUEUE_CREATE_FAILED
QUEUE_TRANSITION_CONFLICT
LINE_PUSH_FAILED
EMAIL_DELIVERY_FAILED
DATABASE_QUERY_SLOW
SCHEDULER_JOB_FAILED
```

Useful correlation fields:

```text
traceId
requestId
organizationId
branchId
queueId
orderId
ticketId
paymentTransactionId
jobName
```

Sensitive credentials and raw payment/LINE secrets must never be included.
