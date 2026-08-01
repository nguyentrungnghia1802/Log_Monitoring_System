# API

## 1. API principles

- Versioned base path: `/api/v1`
- JSON REST for management/search/analytics/ingestion.
- STOMP/WebSocket for live tail.
- JWT for management UI.
- `X-API-Key` for ingestion.
- Standard response/error envelopes.
- Stable machine-readable error codes.
- Tenant/project scope is derived from authenticated identity.

Current implementation note (2026-08-02): management routes are being delivered
incrementally. The implemented project-scoped route families are logs, analytics,
alert rules, and alert occurrences. Organization/project/API-key management routes
listed below remain planned until their controllers and tests exist.

---

## 2. Management response envelope

Success:

```json
{
  "success": true,
  "data": {}
}
```

Error:

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Request validation failed",
    "details": {}
  }
}
```

Ingestion can use a leaner high-throughput response while keeping stable error codes.

---

## 3. Ingestion response

Accepted:

```json
{
  "accepted": true,
  "acceptedCount": 1,
  "requestId": "req-...",
  "admission": "memory_queue"
}
```

Important semantic:

`202 Accepted` means admitted to the V1 bounded process-memory queue. It does not mean the event has already been persisted to MongoDB.

Backpressure:

```json
{
  "accepted": false,
  "error": {
    "code": "INGESTION_BACKPRESSURE",
    "message": "Ingestion capacity is temporarily unavailable"
  }
}
```

Status: `503`.

---

## 4. Authentication

| Method | Path | Access | Purpose |
| --- | --- | --- | --- |
| POST | `/api/v1/auth/login` | Public | Management user login |
| POST | `/api/v1/auth/refresh` | Refresh/session boundary | Renew access session |
| POST | `/api/v1/auth/logout` | Authenticated | End session |
| GET | `/api/v1/auth/me` | Authenticated | Current user and memberships |

The current backend implementation exposes `POST /api/v1/auth/register` and
`POST /api/v1/auth/login`. Refresh, logout, and `/me` remain planned; a missing
or invalid management token is rejected with `401`.

## 4.1 Project authorization

Every request under `/api/v1/projects/{projectId}/**` requires a valid JWT,
the current user's organization claim to match the stored user record, and a
current membership for the selected project. `VIEWER` can read but cannot
mutate project resources. A project ID supplied in a URL or request body is
never sufficient to grant access.

Nested resource lookups (for example `ruleId`, `alertId`, and log IDs) include
the URL project scope in the repository query. A resource that exists in a
different project is returned as `404` within the caller's authorized scope;
the project-level denial itself is `403`.

---

## 5. Organizations and users

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/v1/organizations/current` | Current organization summary |
| GET | `/api/v1/organizations/current/users` | List memberships/users |
| POST | `/api/v1/organizations/current/users` | Invite/create management user |
| PATCH | `/api/v1/organizations/current/users/:userId` | Change role/status |

V1 may simplify onboarding and start from one seeded organization admin, but authorization boundaries must exist from the beginning.

---

## 6. Projects

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/v1/projects` | List authorized projects |
| POST | `/api/v1/projects` | Create project |
| GET | `/api/v1/projects/:projectId` | Project detail |
| PATCH | `/api/v1/projects/:projectId` | Update settings |
| DELETE | `/api/v1/projects/:projectId` | Soft deactivate |
| PUT | `/api/v1/projects/:projectId/retention` | Replace retention policy |

Project response includes:

- key/name;
- active state;
- environments;
- retention policy;
- recent ingestion summary.

---

## 7. API keys

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/v1/projects/:projectId/api-keys` | List key metadata |
| POST | `/api/v1/projects/:projectId/api-keys` | Create key and return raw secret once |
| POST | `/api/v1/projects/:projectId/api-keys/:keyId/rotate` | Revoke old and create new secret |
| DELETE | `/api/v1/projects/:projectId/api-keys/:keyId` | Revoke key |

Raw key value appears only in create/rotate success responses.

---

## 8. Ingestion endpoints

### Single event

```http
POST /api/v1/ingest/logs
X-API-Key: lm_...
Content-Type: application/json
```

Body:

```json
{
  "eventId": "optional",
  "timestamp": "2026-07-30T10:15:12.123Z",
  "level": "ERROR",
  "service": "queue-service",
  "environment": "production",
  "eventType": "QUEUE_CREATE_FAILED",
  "message": "Failed to create queue",
  "traceId": "T1",
  "requestId": "R1",
  "exception": {
    "type": "MongoTimeoutException",
    "message": "Timed out",
    "stackTrace": "..."
  },
  "context": {
    "branchId": "BR001"
  },
  "tags": {
    "version": "1.0.0"
  }
}
```

### Batch

```http
POST /api/v1/ingest/logs/batch
```

Body:

```json
{
  "events": [
    {},
    {}
  ]
}
```

V1 batch semantics: all validated/normalized events are admitted together or the request is rejected.

---

## 9. Log search

### List/search

```http
GET /api/v1/projects/:projectId/logs
```

Query parameters:

```text
from=<ISO instant>
to=<ISO instant>
environment=production
service=queue-service
level=ERROR
eventType=QUEUE_CREATE_FAILED
traceId=T1
requestId=R1
q=timeout
cursor=<opaque>
limit=100
```

Rules:

- `from/to` required for ordinary browsing;
- max supported range is configurable;
- `limit` has server cap;
- cursor is opaque;
- newest-first default.

Response:

```json
{
  "success": true,
  "data": {
    "items": [],
    "nextCursor": "..."
  }
}
```

### Detail

```http
GET /api/v1/projects/:projectId/logs/:logId
```

---

## 10. Analytics

The current backend exposes `/analytics/summary` and `/analytics/histogram`.
The more granular paths below are the target contract for a later compatible
split and are not yet implemented.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/v1/projects/:projectId/analytics/timeseries` | Counts by time bucket |
| GET | `/api/v1/projects/:projectId/analytics/severity` | Distribution by level |
| GET | `/api/v1/projects/:projectId/analytics/services` | Top services |
| GET | `/api/v1/projects/:projectId/analytics/errors` | Top error fingerprints |

Common query:

```text
from
to
environment
service
bucket=minute|hour|day
```

Bucket may be server-selected when omitted.

---

## 11. Alert rules

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/v1/projects/:projectId/alert-rules` | List |
| POST | `/api/v1/projects/:projectId/alert-rules` | Create |
| GET | `/api/v1/projects/:projectId/alert-rules/:ruleId` | Detail |
| PATCH | `/api/v1/projects/:projectId/alert-rules/:ruleId` | Update |
| POST | `/api/v1/projects/:projectId/alert-rules/:ruleId/enable` | Enable |
| POST | `/api/v1/projects/:projectId/alert-rules/:ruleId/disable` | Disable |
| DELETE | `/api/v1/projects/:projectId/alert-rules/:ruleId` | Archive |

Create example:

```json
{
  "name": "Queue error spike",
  "filter": {
    "environment": "production",
    "service": "queue-service",
    "levels": ["ERROR"],
    "eventTypes": ["QUEUE_CREATE_FAILED"]
  },
  "windowSeconds": 60,
  "threshold": 50,
  "cooldownSeconds": 600,
  "notificationChannels": ["telegram-main"]
}
```

---

## 12. Alert occurrences

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/v1/projects/:projectId/alerts` | Trigger history |
| GET | `/api/v1/projects/:projectId/alerts/:alertId` | Detail |
| POST | `/api/v1/projects/:projectId/alerts/:alertId/acknowledge` | Acknowledge |
| POST | `/api/v1/projects/:projectId/alerts/:alertId/retry-notification` | Explicit audited retry where allowed |

---

## 13. WebSocket / STOMP

Endpoint:

```text
/ws-logs
```

Spring exposes SockJS and native WebSocket variants at `/ws-logs`. The
connection must send a signed JWT in the STOMP `CONNECT` command:

```text
Authorization: Bearer <management-access-token>
```

The only live-tail subscription destination is a user destination:

```text
/user/queue/projects/{projectId}/livetail
```

The backend resolves the session user, re-checks current organization and
project membership, and then records the subscription. A project ID in the
destination is a selector only; it never grants access. The following optional
STOMP native headers are validated and applied server-side:

```text
level: ERROR
service: queue-service
environment: production
```

`level` accepts `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`, or `FATAL`.
Service/environment filters are bounded exact-match values. Each session and
user/IP has a configured connection/subscription limit. The outbound executor
and WebSocket send buffer are bounded; saturated sends are counted as dropped
live-tail events and a client that exceeds transport send limits is disconnected.

The React page keeps a bounded 200-event browser history. It reads the JWT from
`localStorage['log-monitoring.access-token']`; no token is placed in the WebSocket
URL.

Do not expose a destination where the browser can subscribe to an arbitrary project ID without server validation.

---

## 14. Health and metrics

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/actuator/health/liveness` | Process liveness |
| GET | `/actuator/health/readiness` | Dependency/readiness |
| GET | `/actuator/prometheus` | Prometheus metrics; protected at infrastructure edge |

Optional custom operational endpoint:

```http
GET /api/v1/system/ingestion-status
```

Authenticated management user only in the current implementation; role-specific
operator/admin enforcement remains planned. May show:

```json
{
  "queueDepth": 1200,
  "queueCapacity": 50000,
  "workerCount": 4,
  "lastSuccessfulBatchAt": "...",
  "persistenceFailuresLast5m": 0
}
```

---

## 15. Validation limits

All values are configurable but must have server-side caps:

- max event message length;
- max stack trace length;
- max context serialized bytes;
- max context depth;
- max tag count;
- max batch event count;
- max HTTP request bytes;
- max search range;
- max page size;
- max WebSocket subscriptions per user/session.

---

## 16. Error codes

Recommended stable codes:

```text
VALIDATION_ERROR
PAYLOAD_TOO_LARGE
UNAUTHENTICATED
FORBIDDEN
PROJECT_NOT_FOUND
API_KEY_INVALID
API_KEY_REVOKED
INGESTION_BACKPRESSURE
DEPENDENCY_UNAVAILABLE
RATE_LIMITED
SEARCH_RANGE_TOO_LARGE
ALERT_RULE_CONFLICT
INTERNAL_ERROR
```

Clients branch on `code`, not diagnostic message text.

---

## 17. API versioning

Backward-compatible additions remain under `/api/v1`.

Breaking changes to:

- ingestion event semantics;
- authentication;
- event field meaning;
- pagination cursor;
- alert evaluation contract;

require an explicit migration/versioning plan.
