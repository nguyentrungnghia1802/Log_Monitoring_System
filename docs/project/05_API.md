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
/ws
```

Authenticated subscriptions should use server-authorized destinations, for example:

```text
/user/queue/live-logs
```

Client sends a subscribe command/filter message such as:

```json
{
  "projectId": "...",
  "environment": "production",
  "services": ["queue-service"],
  "levels": ["WARN", "ERROR"]
}
```

Server creates an internal subscription object after authorization.

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

Admin/operator only. May show:

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
