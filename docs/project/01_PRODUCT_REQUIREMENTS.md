# Product Requirements

## 1. Scope and terminology

The system receives structured application logs, normalizes them, buffers them asynchronously, persists them to MongoDB, exposes query/analytics interfaces, streams selected events to connected dashboards, and evaluates alert rules.

Terminology:

- **Organization**: tenant boundary owning projects and users.
- **Project**: monitored application/product boundary, for example `LINE Smart Queue Assistant`.
- **Service**: runtime component inside a project, for example `queue-service`.
- **Environment**: runtime environment such as `development`, `staging`, or `production`.
- **Log Event**: one normalized structured log record.
- **Ingestion API Key**: credential used by source applications.
- **Ingestion Queue**: bounded V1 in-memory buffer between HTTP ingestion and MongoDB persistence.
- **Alert Rule**: condition evaluated over matching events in a time window.
- **Live Tail**: near-real-time delivery of selected newly ingested logs to UI clients.

---

## 2. Actors and authorization

| Actor | Scope |
| --- | --- |
| Organization admin | Manage organization users, projects, API keys, retention, alert rules |
| Project operator | View/search logs, dashboards, alerts, manage project-scoped alert rules |
| Viewer | Read project logs/dashboards only |
| Source application | Submit logs to exactly the projects allowed by its API key |
| System worker | Flush ingestion queue, retry batches, evaluate alert state, deliver notifications |
| System operator | Deploy, monitor, back up configuration data, investigate platform incidents |

Authorization must always combine the authenticated identity with organization/project scope. Request-body project IDs are selectors, not authorization.

---

## 3. Functional requirements

### Authentication and access

| ID | Requirement | V1 |
| --- | --- | --- |
| FR-AUTH-001 | Authenticate management users using email/password and JWT-based API access | Required |
| FR-AUTH-002 | Support organization/project-scoped roles | Required |
| FR-AUTH-003 | Authenticate ingestion using API keys in `X-API-Key` | Required |
| FR-AUTH-004 | Store API-key secrets only as hashes after creation | Required |
| FR-AUTH-005 | Allow API-key rotation and revocation | Required |
| FR-AUTH-006 | Record sensitive configuration actions in audit events | Required |

### Organization administration

| ID | Requirement | V1 |
| --- | --- | --- |
| FR-ORG-001 | Read and update the current organization summary/settings | Required |
| FR-ORG-002 | List organization users and their effective roles/status | Required |
| FR-ORG-003 | Create a management user with a hashed password | Required |
| FR-ORG-004 | Change membership role and enable/disable membership | Required |
| FR-ORG-005 | Prevent removal or demotion of the final active organization administrator | Required |
| FR-ORG-006 | Audit organization settings and membership changes without storing credentials | Required |

### Organization, project, and service management

| ID | Requirement | V1 |
| --- | --- | --- |
| FR-PRJ-001 | Organization admin creates/updates/deactivates projects | Required |
| FR-PRJ-002 | Project owns a stable machine-readable key/slug | Required |
| FR-PRJ-003 | Services may be discovered from ingested logs and optionally registered explicitly | Required |
| FR-PRJ-004 | Project defines allowed environments | Required |
| FR-PRJ-005 | Project configures default retention and per-level overrides | Required |

Current implementation status (2026-08-02): the project management API and
React page implement organization-scoped project creation, stable lowercase
keys, authorized listing/detail, settings and retention updates, soft
deactivation, activity/service summaries, and safe project audit events.
Deactivated projects remain visible to authorized management users but valid
ingestion API keys receive 409 PROJECT_INACTIVE. The API-key lifecycle UI is
tracked separately in C3.

### Ingestion

| ID | Requirement | V1 |
| --- | --- | --- |
| FR-ING-001 | Accept one structured log event | Required |
| FR-ING-002 | Accept a batch of structured log events | Required |
| FR-ING-003 | Validate maximum request size and maximum batch size | Required |
| FR-ING-004 | Normalize timestamp, level, project, service, environment, trace/request IDs, message, context | Required |
| FR-ING-005 | Return `202 Accepted` only after the event is successfully admitted to the V1 ingestion queue | Required |
| FR-ING-006 | Reject queue-saturated ingestion with explicit retryable error | Required |
| FR-ING-007 | Never block indefinitely waiting for queue capacity | Required |
| FR-ING-008 | Emit ingestion metrics for accepted/rejected/invalid events | Required |
| FR-ING-009 | Support client-generated `eventId` for deduplication analysis | Required |
| FR-ING-010 | Prevent source applications from choosing another tenant/project outside API-key scope | Required |

### Persistence

| ID | Requirement | V1 |
| --- | --- | --- |
| FR-STO-001 | Worker pool drains queue independently of HTTP request threads | Required |
| FR-STO-002 | Workers aggregate events into configurable batches | Required |
| FR-STO-003 | Persist batches using MongoDB bulk write operations | Required |
| FR-STO-004 | Retry transient write failure with bounded exponential backoff | Required |
| FR-STO-005 | Expose terminal persistence failures through metrics and operator logs | Required |
| FR-STO-006 | Apply event-specific expiration time according to retention policy | Required |

### Search and exploration

| ID | Requirement | V1 |
| --- | --- | --- |
| FR-LOG-001 | Search by project and bounded time range | Required |
| FR-LOG-002 | Filter by environment, service, level, event type, request ID, trace ID | Required |
| FR-LOG-003 | Support text filtering on normalized message with documented limitations | Required |
| FR-LOG-004 | Use cursor-based pagination for chronological log exploration | Required |
| FR-LOG-005 | Return newest-first by default | Required |
| FR-LOG-006 | Open one event detail including safe context and exception data | Required |
| FR-LOG-007 | Query exact `traceId`/`requestId` efficiently | Required |

### Analytics

| ID | Requirement | V1 |
| --- | --- | --- |
| FR-AN-001 | Return total log count by time bucket | Required |
| FR-AN-002 | Return count by severity | Required |
| FR-AN-003 | Return top services generating errors | Required |
| FR-AN-004 | Return top error fingerprints/messages within a bounded window | Required |
| FR-AN-005 | Push grouping and aggregation into MongoDB rather than loading raw result sets into Java | Required |
| FR-AN-006 | Reject or constrain unbounded expensive analytics queries | Required |

### Live Tail

| ID | Requirement | V1 |
| --- | --- | --- |
| FR-LIVE-001 | Authenticated UI client can subscribe to a project live-tail channel | Required |
| FR-LIVE-002 | Subscription supports server-side service/environment/level filters | Required |
| FR-LIVE-003 | New events are published only after successful normalization/admission | Required |
| FR-LIVE-004 | Live-tail delivery is best-effort and does not define persistence truth | Required |
| FR-LIVE-005 | Slow clients must not create unbounded server buffers | Required |

### Alerts

| ID | Requirement | V1 |
| --- | --- | --- |
| FR-ALT-001 | Project operator creates threshold rules | Required |
| FR-ALT-002 | Rule filters on project/service/environment/level/event type | Required |
| FR-ALT-003 | Rule evaluates event count over a defined time window | Required |
| FR-ALT-004 | Rule triggers only after threshold is reached | Required |
| FR-ALT-005 | Rule has cooldown/deduplication to prevent notification storms | Required |
| FR-ALT-006 | Rule state and alert occurrences are persisted | Required |
| FR-ALT-007 | Delivery adapter supports at least one real channel and one mock/dev channel | Required |
| FR-ALT-008 | Delivery failure does not remove stored alert occurrence | Required |
| FR-ALT-009 | Alert occurrence records status, trigger value, threshold, window, and delivery result | Required |

### Retention

| ID | Requirement | V1 |
| --- | --- | --- |
| FR-RET-001 | Default retention is configurable per project | Required |
| FR-RET-002 | Retention can differ by severity | Required |
| FR-RET-003 | Each event stores an absolute expiration timestamp | Required |
| FR-RET-004 | MongoDB TTL automatically removes expired events | Required |
| FR-RET-005 | UI explains that TTL cleanup is asynchronous rather than exact-to-the-second | Required |

### Platform observability

| ID | Requirement | V1 |
| --- | --- | --- |
| FR-OBS-001 | Expose liveness and readiness endpoints | Required |
| FR-OBS-002 | Expose Prometheus-compatible application metrics | Required |
| FR-OBS-003 | Track ingestion queue depth/capacity and rejection count | Required |
| FR-OBS-004 | Track batch size, worker throughput, persistence latency and failures | Required |
| FR-OBS-005 | Track HTTP latency/status and active WebSocket clients | Required |
| FR-OBS-006 | Track alert evaluation and delivery outcomes | Required |

---

## 4. Business rules

| Rule | Definition |
| --- | --- |
| BR-TENANT-001 | Every management read/write is scoped by organization. |
| BR-TENANT-002 | An ingestion API key can submit only to its configured project(s). |
| BR-ING-001 | `202 Accepted` means admitted to the V1 process-memory queue, not durably stored. |
| BR-ING-002 | When the ingestion queue is full, the API rejects quickly rather than growing memory without bound. |
| BR-ING-003 | Server receive time is stored independently from client event time. |
| BR-ING-004 | Unknown/custom context fields are stored only within configured size/depth limits. |
| BR-ING-005 | Reserved system fields cannot be overwritten through arbitrary context. |
| BR-LOG-001 | Search requires organization/project scope and a bounded time range. |
| BR-LOG-002 | Log events are append-only in normal product behavior. |
| BR-RET-001 | Expiration is computed server-side from effective project/level retention. |
| BR-RET-002 | A source application cannot extend its own retention by supplying `expireAt`. |
| BR-ALT-001 | One rule may have at most one active cooldown period at a time. |
| BR-ALT-002 | Notification retry must not create a second alert occurrence. |
| BR-LIVE-001 | Live-tail loss or disconnect never changes stored event state. |
| BR-SEC-001 | API key, JWT, authorization headers, passwords, and known secret fields must be redacted from internal platform logs. |

Implementation note: V1 anchors the server-calculated `expireAt` to
`receivedAt`, not the producer-controlled event `timestamp`, so producer clock
skew cannot extend the configured retention period.

---

## 5. Initial event contract

Required or normalized fields:

```json
{
  "eventId": "optional-client-id",
  "timestamp": "2026-07-30T10:15:12.123Z",
  "level": "ERROR",
  "service": "queue-service",
  "environment": "production",
  "eventType": "QUEUE_CREATE_FAILED",
  "message": "Failed to create queue",
  "traceId": "trace-optional",
  "requestId": "request-optional",
  "exception": {
    "type": "MongoTimeoutException",
    "message": "Timed out",
    "stackTrace": "optional"
  },
  "context": {
    "branchId": "BR001",
    "orderId": "ORD001"
  },
  "tags": {
    "version": "1.0.0",
    "region": "ap-southeast-1"
  }
}
```

The server derives:

- organization ID;
- project ID;
- API-key ID;
- received timestamp;
- normalized level;
- retention policy;
- expiration timestamp;
- optional error fingerprint.

---

## 6. Non-functional requirements

### Performance

Initial engineering targets, to be validated by load test:

- successful ingestion enqueue: p95 <= 20 ms under the declared V1 target workload;
- management/search APIs: p95 <= 500 ms for supported indexed queries;
- live-tail event delivery: normally <= 2 seconds from ingestion admission;
- worker batch persistence must achieve substantially higher throughput than single-document writes.

These are development targets, not production guarantees until measured on declared hardware/data volume.

### Reliability

- no unbounded queues;
- no infinite retry;
- graceful shutdown attempts to drain/flush within a configurable deadline;
- readiness fails when MongoDB is unavailable;
- platform configuration and alert state are durable.

### Security

- least privilege;
- input validation;
- rate limiting;
- secure password hashing;
- hashed API keys;
- CORS policy;
- safe error envelopes;
- secret redaction;
- dependency and container scanning in CI roadmap.

### Privacy

The monitoring platform may receive user IDs, order IDs, branch IDs, request payload fragments, or stack traces. Source systems are responsible for avoiding unnecessary personal data; the monitoring platform must provide field redaction and retention controls.

Ingestion privacy baseline: source applications must not send passwords,
access/refresh tokens, API keys, cookies, private keys, payment data, or full
request/response bodies unless a documented diagnostic need exists. The backend
enforces bounded request/message/stack-trace/context sizes, redacts configured
credential fields before queue admission, rejects context keys that shadow
server-derived event fields, and applies project/level retention. Source
exception data is part of the event contract; platform operational logs must
contain only safe diagnostics and no request payload or credential values.

---

## 7. Core acceptance criteria

1. A valid API key can submit a log and receives `202` only when the queue admits it.
2. An invalid/revoked API key cannot submit logs.
3. A full ingestion queue produces a bounded rejection response and increments a rejection metric.
4. Accepted events are drained by workers and stored in MongoDB using batch writes.
5. Effective retention produces server-calculated `expireAt` values.
6. A project operator can search stored production `ERROR` events for a selected service/time range.
7. Exact request/trace lookup returns only events in the caller's authorized project.
8. Dashboard aggregation returns bucketed counts without loading all matching raw events into application memory.
9. A WebSocket subscriber receives matching events and not unrelated services when a server-side filter is active.
10. A threshold rule triggers one alert occurrence, enters cooldown, and does not spam duplicate notifications during cooldown.
11. Notification failure is visible and retryable without deleting the alert occurrence.
12. MongoDB unavailability makes readiness fail while liveness can remain healthy.
13. Graceful shutdown stops accepting new traffic, attempts a bounded queue flush, and records remaining queue depth.
14. Cross-organization access attempts return forbidden/not-found semantics without leaking foreign tenant data.
15. An organization administrator can update organization settings and manage users without direct MongoDB edits; the final active administrator cannot be removed or demoted.

---

## 8. Error semantics

- `400`: malformed protocol/business input that is not schema validation.
- `401`: missing/invalid authentication.
- `403`: authenticated but not authorized for organization/project.
- `404`: resource not found within caller scope.
- `409`: configuration/state conflict.
- `413`: payload too large.
- `422`: request validation failure.
- `429`: rate limit.
- `503 INGESTION_BACKPRESSURE`: bounded queue has no admission capacity.
- `503 DEPENDENCY_UNAVAILABLE`: required dependency prevents operation.
- `500`: unexpected failure.

Ingestion responses must clearly distinguish validation rejection from temporary backpressure.
