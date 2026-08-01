# System Architecture

## 1. Architecture summary

V1 is a Java/Spring Boot **modular monolith** with one deployable backend, one React SPA, and MongoDB.

```text
Source Applications
       |
       | HTTPS + X-API-Key
       v
+------------------------+
| Spring Boot Backend    |
|                        |
| Ingestion HTTP         |
|      |                 |
|      v                 |
| Bounded Queue          |
|      |                 |
|      v                 |
| Worker Pool ----------+------> MongoDB
|      |                 |
|      +------> Alert Engine
|      +------> Live Tail Hub
|                        |
| Management REST/JWT    |
| WebSocket/STOMP        |
+-----------+------------+
            |
            v
         React SPA
```

The first architectural goal is to make asynchronous behavior explicit while keeping deployment and debugging simple.

---

## 2. Runtime boundaries

| Runtime | Technology | Responsibility |
| --- | --- | --- |
| `web` | React/Vite | Management UI, dashboards, log explorer, live tail, alert configuration |
| `api` | Java 21 / Spring Boot | HTTP API, auth, ingestion, queue, workers, analytics, alerts, WebSocket |
| `mongo` | MongoDB | Durable platform configuration, logs, alert state, audit |
| Notification provider | Telegram/Slack/mock adapter | Outbound alert delivery |
| Source applications | Any stack | Produce structured logs |

V1 does not require a broker or cache.

---

## 3. Backend modules

Recommended top-level modules:

| Module | Responsibility |
| --- | --- |
| `auth` | User login, JWT, password/security policy |
| `organization` | Tenant users and organization settings |
| `project` | Projects, environments, services, retention settings |
| `apikey` | API-key issue/hash/revoke/rotate |
| `ingestion` | Ingestion HTTP contract, normalization, admission |
| `buffer` | Bounded in-memory queue and queue metrics |
| `persistence` | Batch assembly, Mongo bulk writes, retries |
| `logquery` | Search, filters, cursor pagination, event detail |
| `analytics` | Mongo aggregation pipelines and chart responses |
| `livetail` | STOMP destinations, subscription filters, event fan-out |
| `alerting` | Rules, evaluation windows, cooldown, occurrences |
| `notification` | Telegram/Slack/mock adapters and retry |
| `audit` | Sensitive configuration traceability |
| `observability` | Metrics, health, readiness, internal diagnostics |
| `common` | Cross-cutting value types, errors, time, IDs, redaction |

Dependency direction:

```text
web/controller
    -> application/service
        -> domain
        -> repository / integration ports

repository -> MongoDB
integration adapter -> external notification provider
```

Controllers must not own domain policies. Repositories must not authorize HTTP users.

---

## 4. Ingestion thread model

V1 must separate request threads from storage workers.

```text
Tomcat request thread
  -> authenticate API key
  -> validate/normalize
  -> queue.offer(event)
  -> 202

Persistence worker thread
  -> queue.take/poll
  -> create batch
  -> MongoDB bulk write
```

### Required characteristics

- queue is bounded;
- admission uses a short bounded/non-blocking operation;
- request threads never perform MongoDB writes for normal ingestion;
- worker count is configurable;
- batch max size and max wait duration are configurable;
- worker retry is bounded;
- thread pools use explicit names and metrics;
- no default unbounded executor is allowed.

Example configuration concepts:

```text
ingestion.queue.capacity = 50_000
ingestion.workers = 4
ingestion.batch.max-size = 500
ingestion.batch.max-wait = 500ms
ingestion.enqueue.timeout = 5ms
```

Values are environment tuning parameters, not hard business constants.

---

## 5. Backpressure

Backpressure is a first-class behavior.

When the bounded queue reaches capacity:

1. the API does not allocate another unbounded buffer;
2. admission fails quickly;
3. response is `503 INGESTION_BACKPRESSURE`;
4. `Retry-After` may be provided;
5. rejection metrics increment;
6. the source application decides whether to retry, locally buffer, sample, or drop according to its own policy.

The monitoring platform must never make the monitored application block indefinitely.

---

## 6. Event lifecycle

```text
RECEIVED
   |
   v
VALIDATED
   |
   v
NORMALIZED
   |
   v
ADMITTED_TO_MEMORY_QUEUE
   |
   +--> LIVE TAIL best-effort
   |
   v
BATCHED
   |
   v
PERSISTED
   |
   +--> query/analytics truth
   +--> alert evaluation input/state
```

For V1, `ADMITTED_TO_MEMORY_QUEUE` is not durable.

Where precise alert semantics require persisted truth, alert evaluation should use persisted events or persisted counters/state rather than assuming WebSocket delivery is authoritative.

---

## 7. MongoDB strategy

Separate operational/configuration collections from high-volume event storage.

- normal collections for users/projects/API keys/alert rules/audit;
- `log_events` as the primary high-volume event collection;
- TTL index on `expireAt`;
- compound indexes designed from concrete query patterns;
- aggregation pipelines for dashboards.

A future time-series collection may be evaluated after validating query compatibility and operational trade-offs. V1 documentation must not depend on capped collections.

---

## 8. Live-tail architecture

```text
Admitted Event
    |
    v
LiveTailPublisher
    |
    v
Subscription Registry
    |
    +--> session A: user=U1, project=P1, level=ERROR
    +--> session B: user=U2, project=P1, service=payment
                    |
                    v
              /user/queue/projects/P1/livetail
```

Rules:

- STOMP `CONNECT` requires a signed, non-expired JWT;
- subscription destinations are user destinations, not public project topics;
- `ProjectAuthorizationService` checks the current organization and membership
  when each subscription is created;
- server filters before fan-out using bounded level/service/environment values;
- connection, subscription, inbound/outbound channel, and transport buffers are
  bounded;
- a saturated outbound channel drops the live event and transport limits allow
  a slow client to be disconnected without blocking persistence;
- disconnect and `SessionDisconnectEvent` both remove session state;
- WebSocket disconnect is normal and has no effect on persisted logs.

---

## 9. Alert engine architecture

V1 threshold rules are deterministic.

Example:

```text
project = smart-queue
service = queue-service
environment = production
level = ERROR
window = 1 minute
threshold = 50
cooldown = 10 minutes
```

Recommended flow:

```text
Persisted/eligible event
      |
      v
Rule matcher
      |
      v
Window counter/state
      |
 threshold crossed?
      |
      v
AlertOccurrence persisted
      |
      v
Notification adapter
```

Rule configuration and alert occurrence are durable.

V1 can begin with in-process evaluation. Horizontal scaling requires a later ownership strategy to avoid duplicate evaluation.

---

## 10. Security architecture

### Management users

- email/password;
- password hash;
- short-lived access JWT;
- authorization by organization/project membership through the centralized
  `ProjectAuthorizationService`;
- every project-scoped resource query carries the authorized `projectId`,
  including nested alert rule and occurrence IDs;
- viewer role is read-only;
- admin operations audited.

### Ingestion clients

API key format:

```text
lm_<public-id>_<secret>
```

Storage:

- public key ID or prefix stored for lookup;
- secret portion hashed with a password/key derivation strategy appropriate for random high-entropy secrets;
- raw key returned only at creation/rotation;
- revocation checked before admission.

### Sensitive logging policy

The monitoring platform must redact at minimum:

- `Authorization`;
- `X-API-Key`;
- cookies;
- passwords;
- access/refresh tokens;
- known private keys/secrets;
- configurable context fields.

---

## 11. Failure boundaries

### MongoDB unavailable

- `/ready` fails;
- management writes fail safely;
- ingestion queue can absorb only up to its fixed capacity;
- once capacity is exhausted, ingestion rejects;
- no unlimited retry accumulation.

### Worker crash/exception

- worker catches per-batch failure;
- retries transient failures with bounds;
- terminal failure emits critical metric/log;
- executor remains alive unless the process is intentionally terminated.

### Backend process crash

- queue contents may be lost in V1;
- persisted MongoDB events remain;
- restart starts with empty in-memory buffer.

This is an accepted V1 trade-off.

### Notification provider failure

- stored alert occurrence remains;
- delivery state records failure/retry;
- provider failure never deletes source logs.

---

## 12. Graceful shutdown

Shutdown sequence:

1. readiness becomes false;
2. stop accepting new ingestion;
3. stop WebSocket admission/subscriptions cleanly;
4. stop ingestion queue producers;
5. allow workers to drain for configured timeout;
6. flush partial batch if possible;
7. record final queue depth and batch outcome;
8. close MongoDB/client resources;
9. exit.

A bounded shutdown deadline is mandatory.

---

## 13. Horizontal-scale exit criteria

Do not add Kafka, Redis, or extra services without an explicit reason.

Introduce a durable broker when one or more are true:

- admitted-event durability is required;
- one JVM cannot safely buffer peak traffic;
- persistence workers need independent scaling;
- replay is required;
- multiple independent consumers need event history before MongoDB.

Introduce multiple backend instances only after:

- alert evaluation ownership is coordinated;
- WebSocket session routing is understood;
- API-key cache/state is shareable or database-backed;
- ingestion durability strategy is decided.

---

## 14. Self-observability

Required metric families:

```text
http.server.*
ingestion.received
ingestion.accepted
ingestion.rejected.validation
ingestion.rejected.backpressure
ingestion.queue.depth
ingestion.queue.capacity
ingestion.worker.active
ingestion.batch.size
ingestion.persistence.duration
ingestion.persistence.failures
mongodb.command.duration
livetail.sessions.active
livetail.subscriptions.active
livetail.events.sent
livetail.events.dropped
livetail.authorization.failures
alert.evaluations
alert.triggered
alert.delivery.success
alert.delivery.failure
jvm.*
process.*
```

The monitoring system must be diagnosable without relying only on itself.
