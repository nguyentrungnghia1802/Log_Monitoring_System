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
stores `organizationRole`, `active`, and `updatedAt`. `passwordHash` is never
returned by management DTOs. `organizationId` is indexed for membership lists;
an inactive or removed user cannot authenticate into organization APIs. The
organization role is `ORGANIZATION_ADMIN`, `PROJECT_OPERATOR`, or `VIEWER`.

---

## 3. `log_events` document

```json
{
  "_id": "ObjectId",
  "organizationId": "ObjectId",
  "projectId": "ObjectId",
  "apiKeyId": "ObjectId",

  "eventId": "client-optional",
  "timestamp": "2026-07-30T10:15:12.123Z",
  "receivedAt": "2026-07-30T10:15:12.175Z",
  "expireAt": "2026-08-29T10:15:12.175Z",

  "level": "ERROR",
  "service": "queue-service",
  "environment": "production",
  "eventType": "QUEUE_CREATE_FAILED",

  "message": "Failed to create queue",
  "fingerprint": "optional-normalized-error-fingerprint",

  "traceId": "optional",
  "requestId": "optional",

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
- server computes `expireAt`;
- `context` and `tags` have bounded key count, depth, key length, and serialized size;
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
  "organizationId": "ObjectId",
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
  "status": "enabled",

  "filter": {
    "environment": "production",
    "service": "payment-service",
    "levels": ["ERROR"],
    "eventTypes": ["PAYMENT_FAILED"]
  },

  "windowSeconds": 60,
  "threshold": 100,
  "cooldownSeconds": 600,

  "notificationChannels": [
    {
      "type": "telegram",
      "configurationRef": "telegram-main"
    }
  ],

  "lastTriggeredAt": null,
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
  "organizationId": "ObjectId",
  "projectId": "ObjectId",
  "ruleId": "ObjectId",

  "status": "notified",
  "triggeredAt": "ISODate",
  "windowStart": "ISODate",
  "windowEnd": "ISODate",

  "observedValue": 214,
  "threshold": 100,

  "delivery": {
    "channelType": "telegram",
    "status": "sent",
    "attemptCount": 1,
    "lastAttemptAt": "ISODate",
    "lastErrorCode": null
  },

  "acknowledgedAt": null,
  "acknowledgedBy": null
}
```

---

## 8. Index strategy

Indexes must come from query patterns, not habit.

### `log_events`

Baseline candidate indexes:

```javascript
{ projectId: 1, timestamp: -1 }
{ projectId: 1, environment: 1, timestamp: -1 }
{ projectId: 1, service: 1, timestamp: -1 }
{ projectId: 1, level: 1, timestamp: -1 }
{ projectId: 1, traceId: 1, timestamp: 1 }
{ projectId: 1, requestId: 1, timestamp: 1 }
{ expireAt: 1 } // TTL
```

Do **not** blindly create every combination. Use real query traces and `explain()` to remove/adjust redundant indexes.

Potential partial indexes may be evaluated for fields such as `traceId` if sparsity is high.

### Configuration collections

- `organizations.slug` unique;
- project key unique within organization;
- API-key `publicId` unique;
- `users.organizationId` for organization member listing;
- `audit_logs.organizationId` for organization configuration history;
- user normalized email unique;
- membership organization/user unique;
- alert rule project/status index;
- alert occurrence project/triggeredAt descending.

---

## 9. TTL

Create one TTL index on absolute expiration:

```javascript
db.log_events.createIndex(
  { expireAt: 1 },
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
