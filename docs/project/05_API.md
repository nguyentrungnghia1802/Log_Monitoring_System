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
incrementally. Logs, analytics, alert rules, alert occurrences, API-key lifecycle,
the current-organization/user-management route family, and project management
now have controllers and tests. The API-key management UI is available at
`/api-keys`; the dedicated retention UI remains planned in C4.

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
current membership for the selected project unless the user is an active global
`ORGANIZATION_ADMIN`. `VIEWER` can read but cannot mutate project resources. A
project ID supplied in a URL or request body is never sufficient to grant access.

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
| DELETE | `/api/v1/organizations/current/users/:userId` | Remove organization membership |

`GET` organization routes require an active JWT user whose current organization
matches the stored user record. Settings and membership mutations additionally
require `ORGANIZATION_ADMIN`. The current organization document is created from
the authenticated organization ID when a legacy installation has not materialized
it yet.

User creation requires a unique username, valid email, 12–128 character
password, and one of `ORGANIZATION_ADMIN`, `PROJECT_OPERATOR`, or `VIEWER`.
Passwords are BCrypt-hashed and are never returned. `PATCH` changes role and/or
active state; `DELETE` disables the user, clears organization membership, and
removes project memberships. The final active organization administrator cannot
be demoted, disabled, or removed (`409 FINAL_ORGANIZATION_ADMIN`). Successful
settings and membership mutations create safe organization-scoped audit records.
List and detail responses expose metadata only, never `passwordHash`.

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

The implemented project response also includes id, organizationId, bounded
string settings, discovered services, recentIngestion.eventsLast24Hours,
recentIngestion.errorEventsLast24Hours, and recentIngestion.lastReceivedAt.
The service list is discovered from persisted events for the project; the
counts are limited to the previous 24 hours.

POST accepts key, name, environments, optional retention, and optional bounded
settings. The key is normalized to a lowercase slug and is immutable after
creation. PATCH updates name, environments, and settings. PUT /retention
replaces the default days and supported level overrides. DELETE performs a
soft deactivation and is idempotent. All project mutation responses are
audited with static summaries that contain no credentials.
Invalid input returns 422, duplicate keys return 409 PROJECT_KEY_EXISTS, and
a project outside the current organization returns 404 PROJECT_NOT_FOUND.
Valid ingestion keys for a deactivated project return 409 PROJECT_INACTIVE.

---

## 7. API keys

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/v1/projects/:projectId/api-keys` | List key metadata |
| POST | `/api/v1/projects/:projectId/api-keys` | Create key and return raw secret once |
| POST | `/api/v1/projects/:projectId/api-keys/:keyId/rotate` | Revoke old and create new secret |
| DELETE | `/api/v1/projects/:projectId/api-keys/:keyId` | Revoke key |

Raw key value appears only in create/rotate success responses.

API-key management requires an authenticated `ORGANIZATION_ADMIN` membership for
the selected project. List responses expose metadata only: `id`, `projectId`,
`name`, stable `publicId`, `secretLast4`, lifecycle status, and timestamps. They
never expose the raw key or its password hash.

The controller resolves the selected project by both its document id and the
principal's current organization before allowing list, create, rotate, or
revoke. A global organization admin therefore cannot use a valid token to
manage a project document from another organization; the API returns
`PROJECT_NOT_FOUND` for that out-of-scope selector. The UI keeps the raw value
only in transient component state for the one-time screen and copy action; it
does not write the value to browser storage or the metadata query cache.

The raw format is `lm_live_<publicId>_<secret>`. The public id is a lookup
selector, while the secret is generated from 32 random bytes and stored only as
a BCrypt hash. Rotation revokes the old key before issuing a replacement; the
old value immediately returns `401`. Create, rotate, and revoke write safe audit
events without including the raw header value.

Ingestion derives `organizationId`, `projectId`, and `apiKeyId` from the
authenticated `X-API-Key`. Any project-like value supplied as extra request data
is not an authority. Valid ingestion requests are token-bucket limited per key;
defaults are `100` requests/second with a `200` request burst and can be changed
with `API_KEY_REQUESTS_PER_SECOND` and `API_KEY_BURST_CAPACITY`. `lastUsedAt` is
persisted at most once per configured interval (`API_KEY_LAST_USED_UPDATE_INTERVAL_SECONDS`,
default `60`) to avoid a database write on every event.

---

## 8. Ingestion endpoints

### Single event

```http
POST /api/v1/ingest/logs
X-API-Key: lm_live_ak_..._...
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

The ingestion API applies a streaming HTTP body guard before JSON parsing. The
current defaults are configurable through environment variables, but each value
has a server-side hard cap:

- HTTP body: `1 MiB` (`INGESTION_MAX_HTTP_BODY_BYTES`);
- batch: `500` events (`INGESTION_BATCH_MAX_SIZE`);
- message: `4,000` characters (`INGESTION_MAX_MESSAGE_LENGTH`);
- exception message: `4,000` characters;
- source stack trace: `16,000` characters (`INGESTION_MAX_STACK_TRACE_LENGTH`);
- scalar event IDs/service/environment/event type: `256` characters;
- context serialized size: `32 KiB` and tags serialized size: `16 KiB`;
- context/tag root keys: `50` each;
- nested map/collection entries: `100` per node;
- context nesting depth: `5`;
- context key length: `100` characters;
- context string value length: `4,000` characters;
- max search range;
- max page size;
- max WebSocket subscriptions per user/session.

An oversized HTTP body returns `413 PAYLOAD_TOO_LARGE`. A well-formed request
that violates an ingestion limit or reserved-field rule returns `422
VALIDATION_ERROR`; malformed JSON returns `400 MALFORMED_REQUEST`. Batch
validation and redaction complete before any event is offered to the queue, so a
rejected batch cannot be partially admitted.

### 15.1 Redaction and privacy

Context and tags are copied into a bounded normalized structure before queue
admission. Keys configured under `security.redaction.fields` are replaced with
`[REDACTED]` by default, including password, token, API-key, authorization,
cookie, private-key, and webhook/bot-token names. Credential-like `key=value`
patterns and bearer tokens in message/exception text are also redacted. The
redaction replacement and field set are configurable, while core credential
patterns remain protected when redaction is enabled.

Reserved event fields such as `projectId`, `organizationId`, `apiKeyId`,
`receivedAt`, and `expireAt` cannot be supplied through context or tags. A
source application's extra top-level project selector is not an authority; the
authenticated API key still determines the stored project.

Source applications should avoid sending passwords, access tokens, payment
data, full request/response bodies, or unnecessary personal data. Use opaque
trace/request IDs where possible, keep customer/order identifiers to the
minimum needed for diagnosis, and configure project retention to the shortest
useful period. Source exception fields are stored as log event data; platform
operational errors are logged separately without request payloads or credential
values.

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
RATE_LIMITED
MALFORMED_REQUEST
INGESTION_BACKPRESSURE
DEPENDENCY_UNAVAILABLE
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
