# Database

## 1. Database position

MongoDB is the V1 primary data store for:

- tenant/project configuration;
- API-key metadata;
- high-volume log events;
- alert rules and occurrences;
- audit events.

The application uses MongoDB intentionally to learn document modeling, compound indexes, TTL, bulk writes, aggregation pipelines, and time-oriented workloads.

---

## 2. Collection catalog

| Collection | Purpose |
| --- | --- |
| `organizations` | Tenant settings |
| `users` | Management user identity/profile |
| `auth_sessions` | Hashed, revocable management refresh sessions |
| `project_memberships` | Project authorization and legacy organization-admin compatibility |
| `projects` | Monitored project settings |
| `api_keys` | Hashed ingestion credentials and scope |
| `log_events` | High-volume normalized event storage |
| `alert_rules` | Threshold configuration |
| `alert_occurrences` | Triggered alert history/delivery state |
| `audit_logs` | Sensitive configuration history (logical audit events) |
| `dashboard_preferences` | Optional saved filters/views |

V1 may embed project service/environment metadata in `projects` instead of separate collections where write/update patterns remain simple.

### `organizations` document

```json
{
  "_id": "org-1",
  "slug": "acme",
  "name": "Acme Platform",
  "active": true,
  "settings": {
    "timezone": "UTC"
  },
  "createdAt": "ISODate",
  "updatedAt": "ISODate"
}
```

Organization settings are bounded to 20 string entries, 64 characters per key,
and 256 characters per value. A legacy organization ID is lazily materialized
when an authenticated organization endpoint is first read, so administration
does not require a manual seed edit.

### Management user fields

`users` retains the existing unique username and organization scope and now
stores `organizationRole`, `active`, and `updatedAt`. Email and username are
unique indexed login identities. `passwordHash` is never
returned by management DTOs. `organizationId` is indexed for membership lists;
an inactive or removed user cannot authenticate into organization APIs. The
organization role is `ORGANIZATION_ADMIN`, `PROJECT_OPERATOR`, or `VIEWER`.

### `auth_sessions` document

```json
{
  "_id": "ObjectId",
  "userId": "ObjectId",
  "organizationId": "ObjectId",
  "refreshTokenHash": "SHA-256 hex",
  "createdAt": "ISODate",
  "expiresAt": "ISODate",
  "revokedAt": null
}
```

Only a hash of the random refresh token is stored. `refreshTokenHash` is unique,
`userId` is indexed, and `expiresAt` has a TTL index. Revoked records remain
until expiration to preserve replay rejection without retaining raw secrets.

---

## 3. `log_events` document

```json
{
  "_id": "ObjectId",
  "organization_id": "organization-id",
  "project_id": "project-id",
  "api_key_id": "api-key-id",

  "event_id": "client-optional",
  "timestamp": "2026-07-30T10:15:12.123Z",
  "received_at": "2026-07-30T10:15:12.175Z",
  "expire_at": "2026-08-29T10:15:12.175Z",

  "level": "ERROR",
  "service": "queue-service",
  "environment": "production",
  "event_type": "QUEUE_CREATE_FAILED",

  "message": "Failed to create queue",
  "error_fingerprint": "QUEUE_CREATE_FAILED::Failed to create queue",

  "trace_id": "optional",
  "request_id": "optional",

  "exception": {
    "type": "MongoTimeoutException",
    "message": "Timed out",
    "stackTrace": "..."
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

Rules:

- server sets organization/project/API-key IDs;
- server sets `receivedAt`;
- server computes `expireAt = receivedAt + effective retention`; client
  `timestamp` remains distinct and cannot extend retention;
- optional client `eventId` is stored without a uniqueness guarantee in V1;
- the current deterministic fingerprint is `eventType + "::" + message`
  (or `eventType` for a blank message) after ingestion sanitization;
- `context` and `tags` have bounded key count, depth, key length, and serialized size;
- default bounds are 50 root keys, depth 5, key length 100, 100 collection
  entries, 32 KiB context, 16 KiB tags, 4,000-character values/messages,
  4,000-character exception messages, and 16,000-character stack traces;
- log event is append-only;
- arbitrary context cannot overwrite reserved fields.

---

## 4. Project document

```json
{
  "_id": "ObjectId",
  "organizationId": "ObjectId",
  "key": "line-smart-queue",
  "name": "LINE Smart Queue Assistant",
  "active": true,

  "environments": [
    "development",
    "staging",
    "production"
  ],

  "retention": {
    "defaultDays": 7,
    "levelOverrides": {
      "DEBUG": 3,
      "WARN": 14,
      "ERROR": 30
    }
  },
  "settings": {
    "owner": "platform"
  },

  "createdAt": "ISODate",
  "updatedAt": "ISODate"
}
```

The current projects mapping uses a compound unique index on
(organizationId, key). active is a soft-deactivation flag; the project
document is retained for management reads and audit history. settings is a
bounded string map. The application validates retention days from 1 through
3650 and normalizes supported level overrides to DEBUG, INFO, WARN, ERROR, or
FATAL.

---

## 5. API-key document

```json
{
  "_id": "ObjectId",
  "projectId": "ObjectId",
  "name": "production-api",
  "publicId": "ak_01...",
  "hashedSecret": "...",
  "secretLast4": "9X2A",
  "status": "active",
  "createdBy": "ObjectId",
  "createdAt": "ISODate",
  "lastUsedAt": "ISODate",
  "revokedAt": null
}
```

Never store the raw key after creation. `publicId` is an indexed stable lookup
identifier and is not a credential. `hashedSecret` is a BCrypt password hash;
the application never serializes it through the API. `status` is derived from
the active/revoked state, and `revokedAt` is populated during revoke/rotation.
The current implementation also retains `keyPrefix` as a compatibility field
for records created by the initial schema; new records set it equal to
`publicId`. `lastUsedAt` is deliberately throttled so ingestion does not issue
a Mongo write for every accepted event.

---

## 6. Alert rule document

```json
{
  "_id": "ObjectId",
  "organizationId": "ObjectId",
  "projectId": "ObjectId",

  "name": "Payment error spike",
  "enabled": true,
  "environment": "production",
  "service": "payment-service",
  "levels": ["ERROR"],
  "eventTypes": ["PAYMENT_FAILED"],

  "windowSeconds": 60,
  "threshold": 100,
  "cooldownSeconds": 600,

  "cooldownUntil": null,

  "createdAt": "ISODate",
  "updatedAt": "ISODate"
}
```

Provider secrets must not live inside freely readable rule documents.

---

## 7. Alert occurrence document

```json
{
  "_id": "ObjectId",
  "projectId": "ObjectId",
  "ruleId": "ObjectId",
  "ruleName": "Payment error spike",

  "status": "ACKNOWLEDGED",
  "triggeredAt": "ISODate",
  "windowStart": "ISODate",
  "windowEnd": "ISODate",

  "observedValue": 214,
  "threshold": 100,

  "deliveryStatus": "DELIVERED",
  "attemptCount": 1,
  "lastAttemptAt": "ISODate",
  "lastError": null,
  "deliveryAttempts": [
    {
      "attemptNumber": 1,
      "provider": "telegram",
      "attemptedAt": "ISODate",
      "status": "DELIVERED",
      "errorSummary": null
    }
  ],

  "acknowledgedAt": null,
  "acknowledgedBy": null
}
```

Older occurrences without `deliveryAttempts` remain readable as an empty
history. Provider errors are redacted, flattened, and length-bounded before
persistence. Occurrences are always queried by `(id, projectId)`.

---

## 8. Index strategy

Indexes must come from query patterns, not habit.

### `log_events`

Baseline candidate indexes:

```javascript
{ project_id: 1, timestamp: -1, _id: -1 }
{ project_id: 1, environment: 1, timestamp: -1, _id: -1 }
{ project_id: 1, service: 1, timestamp: -1, _id: -1 }
{ project_id: 1, level: 1, timestamp: -1, _id: -1 }
{ project_id: 1, event_type: 1, timestamp: -1, _id: -1 }
{ project_id: 1, trace_id: 1, timestamp: -1 }
{ project_id: 1, request_id: 1, timestamp: -1 }
{ project_id: 1, error_fingerprint: 1, timestamp: -1 }
{ expire_at: 1 } // TTL, expireAfterSeconds: 0
```

Do **not** blindly create every combination. Use real query traces and `explain()` to remove/adjust redundant indexes.

Potential partial indexes may be evaluated for fields such as `traceId` if sparsity is high.

### D2 measured query-plan review (2026-08-18)

`MongoQueryPlanIntegrationTest` runs against MongoDB 7 with 240 synthetic events
split across two projects and calls `explain("executionStats")` for the six
representative search shapes and four analytics pipelines below. `docsExamined`
and `nReturned` are the values reported by MongoDB's execution statistics; for
aggregation, `nReturned` is the `$cursor` input count rather than the final
post-group response size.

| Query shape | Winning index | Documents examined | Documents returned | COLLSCAN |
| --- | --- | ---: | ---: | --- |
| project + recent time | `idx_logs_proj_time` | 100 | 100 | no |
| project + environment + time | `idx_logs_proj_environment_time` | 40 | 40 | no |
| project + service + time | `idx_logs_proj_service_time` | 40 | 40 | no |
| project + level + time | `idx_logs_proj_level_time` | 40 | 40 | no |
| project + trace ID | `idx_logs_proj_trace` | 14 | 14 | no |
| project + request ID | `idx_logs_proj_request` | 9 | 9 | no |
| time-series aggregation | `idx_logs_proj_time` | 120 | 120 | no |
| severity aggregation | `idx_logs_proj_time` | 120 | 120 | no |
| top service aggregation | `idx_logs_proj_time` | 120 | 120 | no |
| top fingerprint aggregation | `idx_logs_proj_level_time` | 80 | 80 | no |

The top-fingerprint plan uses the level/time index because its early match is
project + time + `level in (ERROR, WARN)`; the fingerprint field is grouped,
not used as an equality predicate. All plans remained project-scoped and
index-compatible, with no unexpected collection scan.

The same integration test measures five paired 1,000-document write samples
after one warm-up round, comparing the nine secondary indexes on `log_events`
with an otherwise unindexed baseline collection. The local MongoDB 7 run
recorded a baseline median of 33.636 ms and an indexed median of 54.446 ms
(1.619x for this sample). This is a directional local measurement, not a
production capacity claim. The indexes are retained because each supports a
measured dominant read shape; no index is removed without a production-like
workload benchmark and a follow-up plan review.

### Configuration collections

- `organizations.slug` unique;
- `projects(organizationId, key)` unique and `organizationId` indexed;
- `api_keys.publicId` unique sparse and legacy `keyPrefix` indexed;
- `users.username` unique, `email` unique sparse, and `organizationId` indexed;
- `auth_sessions.refreshTokenHash` unique, `userId` indexed, and `expiresAt` TTL;
- `project_memberships(userId, projectId)` unique;
- `audit_logs.organizationId` and `projectId` indexed;
- `alert_rules(project_id, enabled)`;
- `alert_occurrences(project_id, triggered_at desc)`.

---

## 9. TTL

Create one TTL index on absolute expiration:

```javascript
db.log_events.createIndex(
  { expire_at: 1 },
  { expireAfterSeconds: 0 }
)
```

The application computes `expireAt` from the effective project policy.

TTL cleanup is asynchronous. Product behavior must not depend on a log disappearing at the exact expiration second.

V1 should not use capped collections for the primary log store.

---

## 10. Bulk write

Persistence workers convert a batch to bulk operations:

```text
List<WriteModel<LogEventDocument>>
 -> insertOne(...)
 -> bulkWrite(models, ordered=false)
```

Unordered bulk writes are preferred for throughput where one invalid/duplicate event must not block all unrelated inserts. Exact behavior for duplicate `eventId` is governed by the idempotency decision below.

---

## 11. Event ID and duplicate strategy

V1 does not promise exactly-once ingestion.

Recommended behavior:

- Mongo `_id` remains server-generated;
- optional client `eventId` is stored;
- a unique index on `(projectId,eventId)` is **not required by default**, because retries and producer behavior must be measured first;
- exact duplicate suppression can be added per project/SDK contract later.

The API must not advertise exactly-once semantics.

---

## 12. Aggregation patterns

### Errors per minute

```javascript
[
  {
    $match: {
      projectId: ObjectId("..."),
      level: "ERROR",
      timestamp: { $gte: start, $lt: end }
    }
  },
  {
    $group: {
      _id: {
        $dateTrunc: {
          date: "$timestamp",
          unit: "minute"
        }
      },
      count: { $sum: 1 }
    }
  },
  { $sort: { "_id": 1 } }
]
```

### Severity distribution

```text
$match -> $group by level -> $sort
```

### Top failing services

```text
$match level ERROR -> $group by service -> $sort count desc -> $limit
```

### Top fingerprints

```text
$match -> $group by fingerprint -> count -> top N
```

---

## 13. Query rules

Every high-volume log query must:

1. include `projectId`;
2. include a bounded time range unless doing an exact selective lookup such as trace/request ID;
3. apply `$match` as early as possible;
4. avoid returning full stack traces when chart/list views need only summaries;
5. use cursor pagination, not deep skip-based pagination.

---

## 14. Cursor pagination

Recommended cursor fields:

```text
timestamp + _id
```

Newest-first:

```text
timestamp DESC, _id DESC
```

The cursor encodes both values so records with equal timestamps remain deterministic.

Avoid deep:

```text
skip(500000)
```

for log exploration.

---

## 15. Time-series collection evaluation

MongoDB time-series collections are a future evaluation item, not a mandatory V1 choice.

Before migration, validate:

- supported query/index patterns;
- update/delete constraints relevant to the chosen MongoDB version;
- TTL/retention behavior;
- operational tooling;
- trace/request lookup characteristics;
- aggregation performance with actual data.

The baseline normal collection keeps the learning model explicit and flexible.

---

## 16. Transactions

Log ingestion itself should not use multi-document transactions.

Transactions may be used only where configuration writes require atomic multi-document consistency, for example:

- API-key rotation metadata + audit event;
- membership change + audit event;
- alert configuration + secret reference.

Prefer single-document atomicity where document design allows it.

The current C1 service persists the user/organization document and then writes
the audit record using the existing V1 audit service. A production deployment
that requires atomic membership-plus-audit commit should enable a MongoDB
replica set and wrap those two writes in a transaction; this remains an
explicit hardening item rather than an implicit durability claim.

---

## 17. Data sensitivity

Potentially sensitive fields:

- user emails;
- source IP/user agent;
- trace/request IDs;
- application context values;
- stack traces;
- user/customer/order identifiers;
- provider delivery configuration.

Controls:

- never store raw API keys;
- redact known secrets before queue admission and persistence;
- do not persist context/tag keys that shadow reserved event fields;
- keep notification credentials in secret configuration;
- restrict raw log access by project;
- apply the effective project/level TTL retention policy;
- keep source exception data distinct from platform operational diagnostics;
- avoid production data in test fixtures.

The ingestion sanitizer bounds message/stack-trace lengths, context/tag key
counts, nesting depth, and serialized bytes before a `LogEventDocument` is
created. Redacted values are persisted as `[REDACTED]`; the original value is
not recoverable from the MongoDB document. Retention is not a substitute for
source minimization: source applications should omit credentials and
unnecessary personal data before sending telemetry.

---

## 18. Backup and recovery position

Configuration and alert history require durable backup.

Raw log backup requirements may differ because logs are high-volume and retained for limited periods. Production policy must explicitly decide whether raw logs are backed up or treated as replaceable telemetry.

At minimum, back up:

- organizations;
- users/memberships;
- projects;
- API-key metadata;
- alert rules/occurrences;
- audit events.

---

## 19. Database acceptance checks

- TTL index exists.
- Critical compound indexes exist.
- `explain("executionStats")` is reviewed for representative search and aggregation queries.
- Worker bulk write succeeds with expected batch sizes.
- Large time-range analytics are bounded/rejected.
- Cross-project queries cannot be issued by repository methods without project scope.
