# Project Context

Last reviewed: 2026-07-30.

## 1. Project

**Centralized Log Monitoring System** is a centralized log ingestion, search, analytics, live-tail, and alerting platform for applications and services.

The first real consumer is expected to be **LINE Smart Queue Assistant**, but the platform must remain application-agnostic so additional projects can send logs through the same ingestion contract.

The project is intentionally designed as a learning-oriented production-style system. Its primary goals are to deepen:

- Java 21 and Spring Boot engineering;
- concurrency, asynchronous processing, batching, and backpressure;
- MongoDB document modeling, indexing, TTL, aggregation, and time-series workloads;
- system architecture, failure handling, observability, and performance engineering.

The system is **not** intended to recreate the full feature set of Datadog, Sentry, Elasticsearch, or Grafana Loki.

---

## 2. Problem

Application logs are commonly fragmented across local files, container stdout, CI logs, and individual servers. This causes several operational problems:

- errors are discovered late;
- developers must SSH into machines or inspect multiple sources;
- related events across services are difficult to correlate;
- traffic spikes can produce too much data for manual inspection;
- recurring failures are hard to quantify;
- system operators lack a common dashboard and alerting mechanism.

The platform centralizes structured logs and provides one place to answer:

- What failed?
- Which service/environment/instance failed?
- When did the problem begin?
- How often is it happening?
- Which request, trace, user, branch, order, or resource was affected?
- Did the error rate cross an alert threshold?
- Is the monitoring platform itself healthy?

---

## 3. Target users

| Actor | Need |
| --- | --- |
| Developer | Search logs, inspect stack traces/context, correlate events, live-tail development/staging traffic |
| System operator | Monitor ingestion/storage health, inspect service errors, acknowledge operational incidents |
| Project administrator | Register projects/services, issue/revoke API keys, configure retention and alert rules |
| Read-only viewer | Inspect dashboards and logs without configuration privileges |
| Source application | Submit structured log events reliably with minimal application impact |
| Background worker | Persist batches, evaluate rules, deliver alerts, perform cleanup/recovery workflows |

---

## 4. Product goals

1. Accept structured logs from multiple independent applications through authenticated ingestion APIs.
2. Keep the log-producing application fast and isolated from monitoring-system slowdowns.
3. Buffer and batch writes before MongoDB persistence.
4. Support high-value filtering, search, aggregation, and time-based dashboards.
5. Stream selected logs to connected clients in near real time.
6. Evaluate threshold-based alert rules and send notifications through pluggable channels.
7. Apply configurable retention automatically.
8. Make queue pressure, dropped/rejected events, worker failures, latency, storage, and alert delivery observable.
9. Preserve a clear evolution path from an in-memory V1 queue to a durable broker when measured requirements justify it.

---

## 5. Initial technical baseline

- Java 21
- Spring Boot 3.x
- Spring Security
- REST API
- Spring WebSocket with STOMP for live-tail
- MongoDB
- React + Vite
- Tailwind CSS
- TanStack Query
- Recharts or Chart.js
- Docker / Docker Compose
- JUnit 5, Testcontainers, Spring Boot Test
- k6 or Gatling for load testing

### V1 architecture position

V1 is a **modular monolith** containing API, application services, ingestion buffer, persistence workers, alert engine, and WebSocket delivery.

The ingestion buffer is intentionally in memory and bounded.

This creates an explicit learning boundary:

> a request accepted into process memory is not durable across process failure.

The system must expose this limitation in documentation, metrics, and operational behavior. A durable broker is a later architectural step, not hidden behind the V1 design.

---

## 6. V1 scope

### Included

- user authentication for the management UI;
- organizations/projects/services;
- API-key-based ingestion;
- single-event and batch ingestion;
- request validation and payload limits;
- bounded ingestion queue;
- worker pool and MongoDB bulk writes;
- log search/filter/pagination;
- aggregation endpoints for dashboard charts;
- TTL-based retention;
- live-tail subscriptions;
- threshold alert rules;
- alert delivery adapters;
- audit events for sensitive configuration;
- health/readiness/metrics;
- Dockerized local environment;
- automated unit/integration/API tests;
- basic load tests and measurable SLO targets.

### Explicitly not required in V1

- Kafka/RabbitMQ;
- Redis;
- Kubernetes;
- microservices;
- distributed tracing backend;
- full-text search engine;
- machine learning anomaly detection;
- multi-region deployment;
- Elasticsearch compatibility;
- exactly-once ingestion guarantee.

---

## 7. Core quality attributes

### Reliability

- management/business configuration writes must be durable;
- accepted V1 ingestion is best-effort after enqueue to process memory;
- queue saturation must never create unbounded memory growth;
- failed MongoDB writes must be retried in a bounded manner;
- alert delivery failure must not corrupt stored logs.

### Performance

- ingestion controller performs only authentication, validation, normalization, and enqueue work;
- storage workers use batches;
- search and dashboard queries must be index-aware;
- expensive aggregations must use bounded time ranges.

### Security

- UI users authenticate separately from source applications;
- ingestion API keys are stored hashed and shown only at creation/rotation time;
- projects are tenant-scoped;
- secrets and sensitive application fields are never logged by the monitoring platform itself.

### Operability

The monitoring system must monitor itself through:

- ingestion accepted/rejected counts;
- queue depth/capacity;
- worker throughput;
- batch size;
- MongoDB latency/errors;
- WebSocket client count;
- alert evaluation/delivery results;
- JVM/memory/thread-pool metrics.

---

## 8. Architecture evolution

### Stage 1 — V1

```text
Applications
    |
    v
Ingestion API
    |
    v
Bounded In-Memory Queue
    |
    v
Worker Pool
    |
    v
MongoDB
```

### Stage 2 — durable ingestion

Triggered when one or more exit criteria are met:

- log loss during process restart is unacceptable;
- sustained ingestion exceeds a single application's safe buffering capacity;
- ingestion and persistence must scale independently;
- replay is required;
- multiple consumers need the same event stream.

Target direction:

```text
Applications -> Ingestion API -> Durable Broker -> Consumer Group -> MongoDB
```

### Stage 3 — horizontal/distributed platform

Possible additions after measurement:

- multiple ingestion instances;
- Kafka partitions/consumer groups;
- MongoDB replica set/sharding;
- dedicated alert worker;
- Redis only where a measured use case exists;
- tracing/metrics correlation.

---

## 9. Main risks

| Risk | Control |
| --- | --- |
| V1 accepted logs can be lost on process crash | Explicit semantics, metric visibility, later durable-broker ADR |
| Queue overload causes memory pressure | Bounded queue, rejection policy, payload limits, rate limiting |
| MongoDB storage grows quickly | TTL, retention policy, capacity monitoring |
| Bad indexes make dashboard queries expensive | Query budgets, explain plans, load tests |
| Alert storms spam notification channels | cooldown, deduplication, max notification rate |
| Sensitive data leaks into logs | redaction policy, deny-list fields, application guidance |
| Monitoring platform recursively logs too much | internal logging policy and sampling |
| WebSocket fan-out overloads server | server-side subscription filters, per-session limits, batching/sampling |

---

## 10. Documentation map

| Document | Canonical responsibility |
| --- | --- |
| `00_PROJECT_CONTEXT.md` | Product purpose, scope, baseline, high-level constraints |
| `01_PRODUCT_REQUIREMENTS.md` | Actors, functional/non-functional requirements, acceptance criteria |
| `02_SYSTEM_ARCHITECTURE.md` | Runtime architecture, module boundaries, concurrency and dependencies |
| `03_DOMAIN_AND_FLOWS.md` | Domain model, state machines, end-to-end flows, failure flows |
| `04_DATABASE.md` | MongoDB collections, documents, indexes, retention, aggregation strategy |
| `05_API.md` | HTTP/WebSocket contracts and endpoint inventory |
| `06_CODEBASE_GUIDE.md` | Repository structure, package/layer rules, placement conventions |
| `07_DEVELOPMENT_AND_TESTING.md` | Local setup, test strategy, commands, performance validation |
| `08_DEPLOYMENT_AND_OPERATIONS.md` | Environments, deployment, observability, backup, incident handling |
| `09_ROADMAP_AND_DECISIONS.md` | Delivery roadmap, technical debt, ADRs, architectural exit criteria |

These documents are the canonical design baseline. Code changes that materially alter behavior or architecture must update the affected document in the same change.
