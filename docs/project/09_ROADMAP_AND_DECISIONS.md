# Roadmap and Decisions

Last reviewed: 2026-08-18.

The B1 management-authentication and C1–C5 administration/operations slices are
implemented and validated. The browser now restores an HttpOnly refresh-cookie
session, holds the short access JWT only in memory, protects management routes,
and supports logout. A guarded local-profile bootstrap creates the first admin
without direct MongoDB edits. Alert operations now expose validated rule
configuration, explainable occurrence detail, actor/time acknowledgement,
sanitized delivery history, and audited idempotent retry.

## 1. Delivery roadmap

### Phase 0 — repository and documentation

- canonical docs 00–09;
- README and AGENTS instructions;
- Java 21/Spring Boot skeleton;
- React/Vite skeleton;
- Docker Compose with MongoDB;
- CI baseline.

### Phase 1 — project/auth foundation

- management authentication;
- organization/project model;
- API-key creation/hash/revoke;
- audit baseline;
- health/readiness/metrics baseline.

### Phase 2 — ingestion core

- structured event DTO;
- normalization/redaction;
- bounded `BlockingQueue`;
- explicit `ThreadPoolExecutor`;
- single/batch endpoints;
- `202` semantics;
- backpressure `503`;
- ingestion metrics.

### Phase 3 — persistence and retention

- Mongo event repository;
- batch assembler;
- bulk write;
- bounded retry;
- TTL;
- compound indexes;
- graceful drain;
- Mongo failure tests.

### Phase 4 — log explorer

- project/time query;
- filters;
- cursor pagination;
- event details;
- trace/request correlation;
- React log explorer.

### Phase 5 — analytics

- timeseries;
- severity;
- top services;
- top fingerprints;
- dashboard charts;
- query performance tests.

### Phase 6 — live tail

- WebSocket/STOMP;
- authenticated subscriptions;
- server-side filters;
- bounded per-client buffering;
- reconnect UI.

### Phase 7 — alerting

- alert rules;
- threshold/window engine;
- cooldown;
- alert occurrences;
- mock + Telegram or Slack adapter;
- retry and operations UI.

### Phase 8 — hardening

- load tests;
- fault injection;
- performance experiments;
- secret scanning;
- backup/restore drill;
- platform self-monitoring dashboard;
- production-style deployment.

### Phase 9 — architecture evolution

Only after V1 measurements:

- durable broker evaluation;
- Kafka/RabbitMQ proof of concept;
- consumer group;
- replay strategy;
- horizontal scaling;
- Mongo replica/sharding evaluation.

---

## 2. Technical debt register

| ID | Issue | Exit/control |
| --- | --- | --- |
| TD-001 | Accepted V1 logs can be lost on process crash before Mongo persistence | Broker ADR when durability becomes required |
| TD-002 | In-process alert evaluation is single-instance oriented | Ownership/stream-processing strategy before horizontal scale |
| TD-003 | Mongo text search is limited compared with dedicated search engines | Measure need before introducing Atlas Search/Elasticsearch/OpenSearch |
| TD-004 | Raw log schema can grow uncontrolled | Strict context size/depth/key limits |
| TD-005 | WebSocket fan-out can become expensive | Subscription limits, server-side filter, sampling/batching |
| TD-006 | Alert delivery adapters can leak provider complexity | Keep adapter boundary and sanitized errors |
| TD-007 | Index count can hurt write throughput | Explain/query telemetry and index review |
| TD-008 | Platform may ingest sensitive data | Redaction guidance, retention, role-scoped access |

---

## 3. Decision record format

Each major architectural decision uses:

```text
ADR-###
Status
Context
Decision
Consequences
Exit criteria / superseding condition when applicable
```

Do not silently reverse accepted architecture.

---

# ADR-001: Java 21 and Spring Boot modular monolith

**Status:** Accepted

**Context:** The learning objective emphasizes Java, Spring, concurrency, MongoDB, and system internals. Early microservices would add deployment complexity before the ingestion core is understood.

**Decision:** Build one Spring Boot backend with explicit feature modules and internal layering.

**Consequences:** Easier debugging/deployment; module boundaries must remain disciplined so workers/services can be extracted later.

---

# ADR-002: MongoDB as V1 primary datastore

**Status:** Accepted

**Context:** Logs are append-heavy, time-oriented, structurally flexible, and suitable for MongoDB aggregation/index/TTL learning.

**Decision:** Use MongoDB for event storage and V1 platform configuration.

**Consequences:** Index design and document size discipline are critical. Dedicated search technology is not introduced until MongoDB limitations are measured.

---

# ADR-003: Normal collection + TTL before capped/time-series specialization

**Status:** Accepted

**Context:** The project needs retention, trace/request lookups, flexible indexes, and straightforward learning/debugging.

**Decision:** Store V1 logs in a normal `log_events` collection with server-computed `expireAt` and TTL index.

**Consequences:** Simple semantics and index flexibility. MongoDB time-series collections remain an experiment/possible migration after benchmark. Capped collection is not the primary storage design.

---

# ADR-004: Bounded in-memory ingestion queue for V1

**Status:** Accepted with explicit exit criteria

**Context:** The project should first teach Java concurrency, buffering, batching, backpressure, and process failure before hiding these concerns behind Kafka.

**Decision:** Use a bounded Java queue and explicit worker executor between HTTP ingestion and MongoDB.

**Consequences:**

- fast request path;
- easy concurrency experimentation;
- admitted events may be lost on process failure;
- queue saturation produces explicit backpressure;
- the platform must never market V1 `202` as durable persistence.

**Exit criteria:**

Introduce a durable broker when admitted-event durability, replay, independent scaling, or multi-consumer processing is required.

---

# ADR-005: `202 Accepted` means memory-queue admission

**Status:** Accepted

**Context:** The HTTP response is returned before MongoDB persistence.

**Decision:** Return `202` only after the complete request is successfully admitted to the bounded queue. Response/documentation expose `admission=memory_queue`.

**Consequences:** Honest API semantics. Clients must decide retry/buffer/drop behavior for `503`.

---

# ADR-006: Reject rather than block on overload

**Status:** Accepted

**Context:** A monitoring tool must not destabilize monitored applications by holding connections indefinitely.

**Decision:** Use bounded enqueue wait and reject with `503 INGESTION_BACKPRESSURE` when capacity is unavailable.

**Consequences:** Possible telemetry loss under overload, but system memory remains bounded and producers can apply their own policy.

---

# ADR-007: Batch MongoDB writes

**Status:** Accepted

**Context:** One database write per event adds avoidable network/driver overhead.

**Decision:** Persistence workers assemble batches by max-size/max-wait and use Mongo bulk writes.

**Consequences:** Better throughput at the cost of small buffering latency and more complex retry/error accounting.

---

# ADR-008: REST for management/search, WebSocket/STOMP for live tail

**Status:** Accepted

**Context:** Search/configuration are request/response workloads while live tail benefits from server push.

**Decision:** Use REST `/api/v1` for normal APIs and STOMP/WebSocket for live events.

**Consequences:** Two transport models but clear responsibility boundaries. WebSocket state is best-effort and non-authoritative.

---

# ADR-009: Separate management authentication from ingestion authentication

**Status:** Accepted

**Context:** Human UI users and source applications have different security lifecycle and permissions.

**Decision:** Use JWT/session authentication for UI management and hashed API keys for ingestion.

**Consequences:** API keys can be independently scoped/rotated without user sessions.

---

# ADR-010: Alert occurrences are durable; delivery is secondary

**Status:** Accepted

**Context:** Notification providers can fail even though a threshold genuinely triggered.

**Decision:** Persist alert occurrence before/independently of final notification success.

**Consequences:** Provider outages remain visible and retryable without losing incident history.

---

# ADR-011: Structured logging is the primary ingestion contract

**Status:** Accepted

**Context:** Free-text logs alone are difficult to correlate and aggregate.

**Decision:** Require normalized top-level fields and allow bounded structured `context`/`tags`.

**Consequences:** Better filtering/analytics. Source applications should supply `traceId`, `requestId`, service, environment, and stable event types.

---

# ADR-012: Project-first query scope

**Status:** Accepted

**Context:** High-volume global scans are expensive and unsafe across tenants.

**Decision:** High-volume log APIs always resolve one authorized project and normally require a bounded time range.

**Consequences:** Efficient indexing and strict tenant isolation. Cross-project aggregate views require explicit future designs.

---

# ADR-013: No Kafka, Redis, Kubernetes, or microservices in initial V1

**Status:** Accepted

**Context:** Adding infrastructure before a concrete bottleneck reduces learning value and increases operational complexity.

**Decision:** Do not add these technologies to V1 without a superseding ADR tied to a measured requirement.

**Consequences:** V1 remains understandable end-to-end. Evolution is driven by evidence.

---

# ADR-014: V1 tolerates duplicate client event IDs

**Status:** Accepted

**Context:** HTTP admission and persistence are asynchronous. A client or
the persistence worker can retry after a timeout where the previous write may
already have succeeded. A client `eventId` alone cannot distinguish that
uncertain outcome without a uniqueness constraint or a durable idempotency
store.

**Decision:** Keep Mongo `_id` server-generated and store optional client
`eventId` without a unique `(projectId,eventId)` index. Repeated accepted
submissions are valid duplicate telemetry and remain separate immutable log
documents. The Java SDK generates one event ID before queueing and reuses it
for its bounded HTTP retries; direct clients should reuse their own event ID
when correlating retries.

**Consequences:** V1 remains write-throughput friendly and does not silently
drop a legitimate repeated event, but search/analytics can count retry
duplicates. The API and SDK must never promise exactly-once ingestion. Any
future deduplication change requires measured producer retry data plus a
benchmark of unique-index/write-cost and an explicit replacement ADR.

---

## 4. Future research questions

- Kafka vs RabbitMQ for the first durable-ingestion step?
- Should ingestion API acknowledge after broker durability or after Mongo persistence?
- Should alert evaluation consume the broker stream or query persisted aggregates?
- When does MongoDB Atlas Search/OpenSearch become justified?
- Would MongoDB time-series collections improve the dominant workload?
- What raw-log volume/SLO requires sharding?
- Should high-cardinality context fields be indexed dynamically? Default answer is no.
- Should source SDKs implement local disk spooling?
- How should sampled/dropped-event metadata be represented?
- When should metrics and traces be correlated with logs?

---

## 5. Success criteria for the learning project

The project is successful when its developer can explain and demonstrate:

- why the queue is bounded;
- what `202` really guarantees;
- what happens when MongoDB is slow/down;
- how worker count and batch size affect throughput;
- how backpressure protects memory;
- why indexes speed reads but cost writes/storage;
- how TTL retention works;
- why cursor pagination beats deep skip;
- how Mongo aggregation differs from processing all raw logs in Java;
- why WebSocket delivery is not persistence;
- how cooldown prevents alert storms;
- why Kafka is introduced only after the V1 durability limitation is understood;
- how LINE Smart Queue failures can be correlated through request/trace IDs.
