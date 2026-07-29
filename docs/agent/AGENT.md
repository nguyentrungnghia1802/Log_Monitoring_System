# Repository Agent Instructions

These rules apply to coding agents and contributors working in the **Log Monitoring System** repository.

**File deletion safety:** Never delete any files inside or outside the repository under any circumstances. If file deletion is required to complete a task, you must explicitly ask for permission and explain the reason first, then wait for my decision in the next prompt.

## Read first

For every task, read:

1. `README.md`
2. `AGENTS.md`
3. `docs/project/00_PROJECT_CONTEXT.md`
4. The relevant source files and tests

Add the following documents according to task type:

| Task | Required context |
| --- | --- |
| Product behavior | `docs/project/01_PRODUCT_REQUIREMENTS.md`, `docs/project/03_DOMAIN_AND_FLOWS.md` |
| Architecture / concurrency | `docs/project/02_SYSTEM_ARCHITECTURE.md`, `docs/project/06_CODEBASE_GUIDE.md`, `docs/project/09_ROADMAP_AND_DECISIONS.md` |
| MongoDB / persistence | `docs/project/04_DATABASE.md`, repositories, index initialization/migrations, MongoDB integration tests |
| API / WebSocket | `docs/project/05_API.md`, controllers, request/response DTOs, validators, services, frontend clients |
| Local development/testing | `docs/project/07_DEVELOPMENT_AND_TESTING.md` |
| Deployment/operations | `docs/project/08_DEPLOYMENT_AND_OPERATIONS.md`, Docker/Compose/CI files, runtime configuration |

Do not introduce architecture based only on familiarity with another project. The Log Monitoring System specifications are authoritative for this repository.

## Sources of truth

- Product intent and requirements: `docs/project/01_PRODUCT_REQUIREMENTS.md`
- Domain states and end-to-end flows: `docs/project/03_DOMAIN_AND_FLOWS.md`
- Runtime architecture and concurrency model: `docs/project/02_SYSTEM_ARCHITECTURE.md`
- MongoDB documents, indexes, TTL, and aggregation strategy: `docs/project/04_DATABASE.md`
- REST/WebSocket contracts: `docs/project/05_API.md`
- Repository/package conventions: `docs/project/06_CODEBASE_GUIDE.md`
- Testing and performance validation: `docs/project/07_DEVELOPMENT_AND_TESTING.md`
- Deployment, health, backup, and incidents: `docs/project/08_DEPLOYMENT_AND_OPERATIONS.md`
- Current roadmap, technical debt, and accepted ADRs: `docs/project/09_ROADMAP_AND_DECISIONS.md`
- Runtime configuration: Spring configuration, frontend environment files, Docker/Compose, and `.env.example`

If code/tests and canonical documentation disagree, inspect the implementation and tests, report the conflict, and update both sides in the same change. Do not silently choose one.

## Core product rules

- The platform is multi-project and tenant-aware. Every management read/write must respect organization/project authorization.
- Source applications authenticate ingestion using API keys; management users authenticate separately.
- An ingestion API key may submit only to its configured project scope.
- Project/organization identity supplied by a client is never an authorization boundary.
- Log events are append-only in normal product behavior.
- `receivedAt`, effective retention, and `expireAt` are server-derived.
- Arbitrary `context` and `tags` are bounded by key count, depth, field length, and serialized size.
- Reserved event fields cannot be overwritten through arbitrary context.
- Search must be project-scoped and normally use a bounded time range.
- Live Tail is best-effort and never the durable source of truth.
- Alert delivery failure must not remove stored logs or the durable alert occurrence.
- Secrets, credentials, authorization headers, API keys, passwords, tokens, and configured sensitive fields must never be written into platform logs.

## V1 architecture invariants

V1 intentionally uses:

```text
Source Application
        |
        v
Spring Boot Ingestion API
        |
        v
Bounded In-Memory Queue
        |
        v
Explicit Worker Pool
        |
        v
Batch / MongoDB Bulk Write
        |
        v
MongoDB
```

Preserve this design unless an accepted ADR explicitly changes it.

### Admission semantics

`202 Accepted` means:

> the complete request was successfully admitted to the V1 bounded process-memory ingestion queue.

It does **not** mean:

- MongoDB persistence has completed;
- the event is durable across process failure;
- exactly-once delivery is guaranteed.

Never change API wording, code, tests, or documentation in a way that implies stronger durability than the implementation provides.

### Backpressure

- The ingestion queue must remain bounded.
- Do not replace it with an unbounded queue.
- Do not block request threads indefinitely waiting for capacity.
- When capacity is unavailable, reject with the documented `INGESTION_BACKPRESSURE` behavior.
- Never solve overload merely by allocating unlimited memory.
- Queue capacity, rejection count, worker throughput, and persistence latency must remain observable.

### Worker model

- HTTP request threads do not perform normal MongoDB log persistence.
- Persistence workers drain the ingestion queue independently.
- Worker count is explicit and configurable.
- Batch maximum size and maximum wait time are explicit and configurable.
- Retries are bounded.
- Thread pools must be explicitly configured and named.
- Handle interruption and graceful shutdown correctly.
- Do not use `Executors.newCachedThreadPool()` or another unbounded executor for ingestion.
- Do not introduce hidden asynchronous behavior through default framework executors.

### Graceful shutdown

A shutdown must attempt, within a configured deadline, to:

1. mark the instance not ready;
2. stop accepting new ingestion;
3. stop producers;
4. drain queued events;
5. flush partial persistence batches;
6. record remaining queue depth/failures;
7. close resources;
8. exit.

Never implement infinite shutdown waiting.

## Architecture boundaries

- Controllers translate HTTP input/output.
- Request DTOs and validators define transport contracts.
- Application services orchestrate use cases.
- Domain types own pure invariants and policies.
- Queue/buffer infrastructure owns admission mechanics.
- Workers own queue draining and batch orchestration.
- Repositories own MongoDB persistence and mapping.
- Analytics infrastructure owns MongoDB aggregation pipelines.
- Integration adapters hide Telegram, Slack, or other external notification transports.
- React components must not contain backend business rules.
- Repositories must not perform HTTP authorization decisions.
- Controllers must not directly implement MongoDB queries or queue algorithms.

Preserve the modular-monolith structure unless an accepted ADR changes it.

## MongoDB rules

- MongoDB is the V1 primary datastore.
- `log_events` is append-oriented.
- Use server-calculated `expireAt` with a TTL index for retention.
- Do not use a capped collection as the primary log store.
- MongoDB time-series collections are an evaluation item, not a default rewrite.
- Ordinary log ingestion must not use multi-document transactions.
- Use MongoDB bulk writes for persistence batches.
- Prefer projections for log list and analytics queries.
- Push aggregation into MongoDB rather than loading large raw datasets into Java.
- High-volume queries must include authorized project scope.
- Ordinary browsing must use a bounded time range.
- Use cursor pagination rather than deep `skip`.
- Design indexes from actual query patterns.
- Review representative queries with `explain()` when index/query behavior changes.
- Do not create indexes for every context/tag field.
- Keep TTL and critical index definitions covered by integration tests.

## Database/schema changes

When changing MongoDB document structure or indexes:

1. update `docs/project/04_DATABASE.md`;
2. update document mappings;
3. update index initialization/migration logic;
4. consider backward compatibility with existing documents;
5. update repository/integration tests;
6. run representative query/aggregation checks;
7. document operational migration steps if existing production data requires backfill.

Never assume that editing a Java document class automatically migrates existing MongoDB data.

## API rules

- Keep the `/api/v1` prefix unless a deliberate versioning decision is recorded.
- Keep stable machine-readable error codes.
- Apply authentication, authorization, validation, payload limits, rate limiting, and project scope as appropriate.
- Do not expose stack traces, secrets, raw provider errors, or cross-tenant records.
- Update backend tests, frontend clients/types, and `docs/project/05_API.md` together when contracts change.

### Ingestion API

- Authenticate using `X-API-Key`.
- Validate/reject oversized requests before expensive processing.
- Normalize input before queue admission.
- Server derives organization/project/API-key identity.
- Single-event ingestion returns `202` only after successful queue admission.
- V1 batch ingestion is all-or-reject; do not silently partially admit a batch.
- Queue saturation returns the documented temporary backpressure response.
- Never advertise exactly-once semantics.

### Search API

- Require authorized project scope.
- Require bounded time ranges for normal log browsing.
- Enforce server-side page/range limits.
- Use opaque cursor pagination.
- Exact trace/request lookup must still enforce project scope.

### WebSocket / Live Tail

- Authenticate connections/subscriptions.
- Authorize project scope server-side.
- Filter server-side before fan-out.
- Bound per-session buffering/subscription counts.
- A slow/disconnected client must not block ingestion or persistence.
- Live Tail is not persistence truth.

## Alerting rules

- Alert rules are project-scoped and durable.
- Alert occurrences are persisted independently of final notification success.
- Threshold/window/cooldown behavior must be deterministic and tested.
- Cooldown prevents notification storms.
- Notification retries must not create duplicate alert occurrences.
- Provider-specific logic belongs behind adapters.
- External notification failure must never roll back or delete source log events.
- Horizontal scaling of alert evaluation requires an explicit ownership/coordination design before multiple evaluators are introduced.

## Security

- Never commit `.env`, JWT secrets, API keys, passwords, provider tokens, webhook credentials, or production data.
- Raw ingestion API-key secrets are returned only when created/rotated and are not stored afterward.
- Store API-key secrets as secure hashes.
- Hash management-user passwords using an approved password hashing algorithm.
- Never log `Authorization`, `X-API-Key`, cookies, passwords, access/refresh tokens, or private keys.
- Redact configured sensitive fields from log context.
- Validate maximum payload size, message length, stack-trace length, context size/depth, tag count, and batch size.
- Restrict MongoDB from the public internet in production.
- Protect Actuator/Prometheus endpoints at the infrastructure/security boundary.
- Use HTTPS for external production traffic.
- Do not put production/customer log data into tests or fixtures.

## Performance and concurrency rules

Performance is part of correctness for ingestion-path work.

When modifying ingestion, queue, worker, persistence, search, or analytics behavior, consider:

- throughput;
- p50/p95/p99 latency;
- queue depth;
- rejection rate;
- batch size;
- worker utilization;
- MongoDB write/query latency;
- CPU;
- heap;
- GC;
- WebSocket fan-out cost.

Do not optimize based only on intuition.

For meaningful performance changes, record:

```text
Hypothesis
Baseline
Change
Measurement
Result
Decision
```

Do not add Kafka, RabbitMQ, Redis, Kubernetes, microservices, Elasticsearch/OpenSearch, or another primary datastore merely as an optimization guess.

## Architecture evolution

The following are intentionally **not V1 defaults**:

- Kafka;
- RabbitMQ;
- Redis;
- Kubernetes;
- microservices;
- Elasticsearch/OpenSearch;
- MongoDB sharding;
- distributed alert workers.

Before introducing one, read `docs/project/09_ROADMAP_AND_DECISIONS.md` and create/supersede an ADR with a measured requirement.

A durable broker becomes justified when one or more are true:

- accepted-event durability across backend failure is required;
- replay is required;
- one JVM cannot safely buffer peak traffic;
- ingestion and persistence need independent scaling;
- multiple independent consumers need the event stream.

Do not silently “improve” V1 by bypassing this learning path.

## Observability

Changes to operationally important code must expose enough signals to diagnose it.

Preserve or add metrics for relevant paths:

```text
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
livetail.sessions
livetail.dropped
alert.evaluations
alert.triggered
alert.delivery.success
alert.delivery.failure
```

Use Spring Boot Actuator/Micrometer conventions where practical.

Metrics and internal logs must not expose sensitive event content or credentials.

## Required testing

Run the smallest relevant checks during development.

Before handoff, run the repository's canonical equivalents of:

```bash
./gradlew clean test
./gradlew build
```

For frontend changes, also run the canonical equivalents of:

```bash
npm run lint
npm run typecheck
npm run test
npm run build
```

For MongoDB behavior, use integration tests against real MongoDB, preferably Testcontainers.

For ingestion/concurrency changes, include relevant tests for:

- queue capacity;
- concurrent producers;
- batch boundaries;
- retry limits;
- worker interruption;
- graceful shutdown;
- MongoDB outage/recovery;
- backpressure.

For query/index changes:

- run integration tests;
- inspect representative query plans where appropriate;
- validate cursor pagination;
- validate project isolation.

If a required check cannot be run, state exactly which check and why.

## Load testing

Ingestion performance work should use k6, Gatling, or the repository-standard load tool.

At minimum, major ingestion changes should preserve tests/benchmarks for:

1. single-event ingestion;
2. batch ingestion;
3. MongoDB slowdown/backpressure;
4. search/analytics while ingestion is active.

Never raise queue capacity, worker count, or batch size as a performance fix without measuring the effect.

## Definition of done

A change is complete only when:

- behavior matches canonical requirements;
- tenant/project authorization is enforced;
- ingestion durability semantics remain honest;
- bounded-resource guarantees are preserved;
- relevant tests pass;
- concurrency/failure behavior is covered where applicable;
- MongoDB indexes/query impact is considered;
- observability is sufficient and does not expose sensitive data;
- frontend loading/empty/error states are handled where relevant;
- affected canonical documents are updated;
- unrelated user changes remain untouched.

## Branch and docs workflow

For new feature or fix work:

1. inspect the current Git status before editing;
2. do not overwrite or discard unrelated user work;
3. create/use a task branch where the repository workflow requires it;
4. implement the smallest coherent change;
5. add/update tests;
6. run relevant validation;
7. update canonical documentation to reflect the actual implementation;
8. review the diff for accidental secrets, generated artifacts, or unrelated changes.

Suggested branch names:

- `feat/<short-name>`
- `fix/<short-name>`
- `perf/<short-name>`
- `chore/<short-name>`

## Git safety

- Never run destructive Git commands against unrelated work.
- Never use `git reset --hard`, destructive checkout, force push, or broad file deletion unless explicitly requested and safe.
- Do not amend or rewrite user commits unless explicitly requested.
- Do not merge or push automatically unless the user explicitly asks or repository workflow explicitly authorizes the agent to do so.
- Never commit `.env`, secrets, database dumps, generated load-test output, or production logs.
- Before committing, inspect staged changes.

## Final handoff

Report:

1. what changed;
2. important architecture/concurrency decisions;
3. tests/checks run and their result;
4. tests/checks not run and why;
5. documentation updated;
6. remaining risks or follow-up work.

For ingestion changes, explicitly mention whether the change affects:

- `202` admission semantics;
- queue capacity/backpressure;
- worker/batch behavior;
- durability/loss window;
- MongoDB write behavior;
- performance characteristics.

Do not describe V1 as durable until an accepted architecture change makes admission durable.
