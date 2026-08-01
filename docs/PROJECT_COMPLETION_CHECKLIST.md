# Centralized Log Monitoring System — Master Completion Checklist

Last prepared: 2026-08-02

## 1. Purpose

This document is the master list of work required to take the **Centralized Log Monitoring System** from its current implementation state to a complete, production-ready V1, with a clearly separated optional V2 architecture-evolution path.

It is derived from the canonical specifications:

1. `00_PROJECT_CONTEXT.md`
2. `01_PRODUCT_REQUIREMENTS.md`
3. `02_SYSTEM_ARCHITECTURE.md`
4. `03_DOMAIN_AND_FLOWS.md`
5. `04_DATABASE.md`
6. `05_API.md`
7. `06_CODEBASE_GUIDE.md`
8. `07_DEVELOPMENT_AND_TESTING.md`
9. `08_DEPLOYMENT_AND_OPERATIONS.md`
10. `09_ROADMAP_AND_DECISIONS.md`

The current implementation report states that Phases 3–8 are substantially implemented, including bulk persistence, retention, search, analytics, live tail, alerts, and notification adapters. Because the canonical roadmap still describes some of these as planned work, the first required action is an implementation-to-documentation audit.

---

## 2. Definition of “Complete”

### 2.1 V1 complete

V1 is complete when the system can be deployed and operated as a secure single-instance modular monolith with:

- management authentication and project-scoped authorization;
- API-key-authenticated single and batch log ingestion;
- bounded in-memory admission and explicit backpressure;
- asynchronous batching and MongoDB bulk persistence;
- project/level retention with MongoDB TTL;
- indexed search and opaque cursor pagination;
- trace/request correlation;
- MongoDB aggregation dashboards;
- authenticated and bounded WebSocket live tail;
- threshold alert rules with cooldown;
- durable alert occurrences;
- at least one real notification adapter plus mock mode;
- audit logging for sensitive operations;
- health, readiness, Prometheus metrics, and platform health visibility;
- meaningful automated tests and repeatable load tests;
- hardened Docker deployment;
- backup and restore procedures that have been exercised;
- documented incident runbooks;
- integration with at least one real source application, preferably LINE Smart Queue Assistant.

### 2.2 V1 limitation that must remain explicit

In the default V1 architecture:

> `202 Accepted` means the event was admitted to the bounded process-memory queue. It does not mean the event is durable in MongoDB.

If the backend process crashes before the persistence worker flushes the event, that event may be lost.

V1 must not claim:

- zero log loss;
- exactly-once ingestion;
- durable admission;
- multi-instance ingestion correctness.

### 2.3 V2 complete

V2 is optional and begins only after V1 measurements justify architectural evolution.

Possible V2 goals:

- durable broker admission;
- replay;
- independent ingress and persistence scaling;
- consumer groups;
- at-least-once delivery;
- multi-instance deployment;
- coordinated alert evaluation;
- MongoDB replica-set or sharding evaluation.

Kafka, RabbitMQ, Redis, Kubernetes, Elasticsearch, OpenSearch, and microservices are not required for V1 completion.

---

## 3. Status Legend

- `[x]` Reported as implemented; must still be verified against code and tests.
- `[ ]` Not yet complete or not proven complete.
- `[~]` Partially complete or requires hardening.
- `[!]` Blocking issue for production readiness.
- `[V2]` Optional post-V1 evolution.

---

# PART A — BASELINE RE-AUDIT AND DOCUMENT CONSISTENCY

## 4. Phase A1 — Verify the Actual Repository State

### Objective

Prove which reported capabilities are actually implemented and passing.

### Tasks

- [x] Inspect the complete backend and frontend source tree.
- [x] Inspect current Git status and repository history.
- [x] Confirm whether the repository has been initialized with Git.
- [x] Verify all build scripts referenced by documentation actually exist.
- [x] Verify current Java and Spring Boot versions.
- [x] Verify current Node.js, React, Vite, and TypeScript setup.
- [x] Verify MongoDB and Testcontainers versions.
- [x] Verify all environment-property names match `.env.example` and Spring configuration.
- [~] Verify no real secret exists in source, history, fixtures, logs, or screenshots.
- [x] Map every implemented feature to its canonical requirement and API contract.

Evidence: `docs/project/FEATURE_MATRIX.md`, `backend/build.gradle`,
`frontend/package.json`, `.env.example`, `compose.yaml`, and the controller/source
tree audit on 2026-08-02. The secret scan found committed development defaults
(`example_password`, placeholder JWT secret) and the static `demo-api-key`
fallback was removed; production-secret clearance remains incomplete.

Build evidence: `backend/:build` and the multi-module `backend/assemble` pass
after configuring the Spring Boot starter module to publish a library JAR.
The aggregate `backend/build` is still blocked by Gradle being unable to delete
an open generated `sdk/log-monitoring-java-sdk/build/test-results/.../output.bin`
while running that SDK test task; no repository file was deleted to work around
the lock.

### Reported Phase 3 items to verify

- [x] `MongoTemplate.bulkOps(BulkMode.UNORDERED, LogEventDocument.class)`
- [x] max-wait batching
- [x] atomic `offerAll` batch admission
- [x] retention resolver
- [x] TTL index
- [x] compound indexes
- [x] graceful shutdown drain
- [x] Micrometer ingestion metrics

### Reported Phase 4 items to verify

- [x] bounded time-range search
- [x] filtering
- [x] opaque cursor based on `timestamp + _id`
- [x] log detail endpoint
- [x] trace/request correlation
- [x] React Log Explorer

### Reported Phase 5 items to verify

- [x] MongoDB aggregation pipelines
- [x] time-series analytics
- [x] severity distribution
- [x] top services
- [x] top fingerprints
- [x] dashboard UI

### Reported Phase 6 items to verify

- [x] WebSocket/STOMP endpoint
- [x] live-tail publishing
- [x] frontend reconnect/pause/clear behavior
- [x] bounded browser buffer

### Reported Phase 7–8 items to verify

- [x] alert-rule CRUD
- [x] threshold/window evaluation
- [x] cooldown
- [x] durable alert occurrence
- [x] acknowledgement
- [x] mock notification sender
- [x] Telegram sender
- [x] notification retry endpoint

### Exit criteria

- [x] A feature matrix exists showing `Requirement -> Implementation -> Tests -> Status`.
- [~] Every “implemented” claim is supported by source and a passing test or manual verification.
- [ ] Gaps are added to this checklist rather than hidden by documentation edits.

Evidence: feature matrix added. The initial default multi-context test run had
one order/isolation-sensitive API-key failure; the backend application task
`./gradlew :test --no-parallel` subsequently passed all 20 tests. WebSocket and
management API coverage is still not sufficient for a full `[x]`.

---

## 5. Phase A2 — Synchronize Canonical Documentation

### Tasks

- [ ] Update `00_PROJECT_CONTEXT.md` with the actual current product state.
- [ ] Mark completed requirements in `01_PRODUCT_REQUIREMENTS.md` without weakening them.
- [ ] Update `02_SYSTEM_ARCHITECTURE.md` to match real package/runtime boundaries.
- [ ] Update `03_DOMAIN_AND_FLOWS.md` with actual alert and notification states.
- [ ] Update `04_DATABASE.md` with actual collection names, fields, indexes, and TTL configuration.
- [ ] Update `05_API.md` with every real route and actual response shape.
- [ ] Update `06_CODEBASE_GUIDE.md` to match the repository layout.
- [ ] Update `07_DEVELOPMENT_AND_TESTING.md` with real commands only.
- [ ] Update `08_DEPLOYMENT_AND_OPERATIONS.md` with the real Compose/runtime topology.
- [ ] Update `09_ROADMAP_AND_DECISIONS.md` so completed phases are not still shown as future work.
- [ ] Add new ADRs only for real architectural decisions.
- [ ] Ensure `README.md` gives a working local-development path.
- [x] Ensure `AGENT.md` points agents to the correct paths and commands.

### Exit criteria

- [~] No known contradiction remains between code, tests, configuration, and canonical docs.
- [ ] A new developer can start the system using only `README.md` and `.env.example`.

Evidence: checklist path corrected in `docs/agent/AGENT.md`; the API document
still inventories management routes not implemented in the repository and is
intentionally left as an open gap.

---

# PART B — SECURITY, AUTHORIZATION, AND TENANT ISOLATION

## 6. Phase B1 — Management Authentication

### Tasks

- [ ] Verify email/password login is implemented.
- [ ] Use a secure password hash.
- [ ] Validate inactive/disabled user behavior.
- [ ] Implement short-lived access tokens.
- [ ] Decide and document refresh/session strategy.
- [ ] Implement logout/revocation behavior.
- [ ] Prevent account enumeration in authentication errors.
- [ ] Add login rate limiting.
- [ ] Add authentication audit events where appropriate.
- [ ] Ensure frontend never stores long-lived secrets insecurely.

### Tests

- [ ] valid login;
- [ ] invalid password;
- [ ] disabled account;
- [ ] expired token;
- [ ] malformed token;
- [ ] logout/revocation;
- [ ] rate-limit behavior.

### Exit criteria

- [ ] Every management endpoint is protected except explicitly public health/login routes.
- [ ] Authentication errors expose no sensitive details.

---

## 7. Phase B2 — Organization, Project, and Role Authorization

### Required roles

At minimum, define and enforce capabilities equivalent to:

- `ORGANIZATION_ADMIN`
- `PROJECT_OPERATOR`
- `VIEWER`

### Tasks

- [~] Verify organizations exist as tenant boundaries.
- [x] Verify users are linked through memberships.
- [x] Verify project access is derived from membership.
- [x] Centralize authorization checks.
- [~] Ensure request-body `organizationId`, `projectId`, or role never grants authority.
- [ ] Restrict project creation/update/deactivation to allowed roles.
- [ ] Restrict API-key creation/rotation/revocation.
- [ ] Restrict retention changes.
- [x] Restrict alert-rule mutation.
- [x] Restrict acknowledgement and notification retry.
- [x] Return forbidden/not-found semantics without revealing foreign tenant data.
- [~] Add project-scoped repository/query methods that cannot accidentally query globally.

### Required cross-project tests

- [x] foreign-project log search;
- [x] foreign-project log detail;
- [x] foreign-project analytics;
- [x] foreign-project alert rules;
- [x] foreign-project alerts;
- [ ] foreign-project API keys;
- [x] foreign-project live-tail subscription;
- [x] viewer mutation rejection;
- [ ] operator organization-management rejection.

### Exit criteria

- [~] Every management read and write is explicitly scoped to an authorized organization/project.
- [~] Cross-project tests pass for all resource types.

Evidence: `ProjectAuthorizationService` now requires a signed JWT, current user
organization match, and current project membership; `ProjectSecurityInterceptor`
rejects unknown principals and enforces viewer read-only access. Alert rule and
occurrence repository lookups now use `(id, projectId)`, preventing nested-ID
cross-project disclosure. `Phase9SecurityTest` covers foreign log search/detail,
analytics, alert rule/occurrence detail, unauthenticated access, and system
status protection; `ProjectAuthorizationServiceTest` covers role and stale
organization decisions. Organization/project management controllers remain
incomplete; API-key management controllers and cross-project tests are covered
by the B3 evidence below. WebSocket authorization is covered by the B4
interceptor/registry/publisher tests below.
Validation: backend `./gradlew :test --no-parallel` passed all 20 tests,
`./gradlew :build --no-parallel` and multi-module `./gradlew assemble` passed;
frontend lint, typecheck, test, and build passed with existing warnings only.

---

## 8. Phase B3 — API-Key Security

### Tasks

- [x] Generate high-entropy API-key secrets.
- [x] Use a stable public lookup identifier.
- [x] Store only a secure hash of the secret.
- [x] Return the raw key only once at creation/rotation.
- [x] Never return raw secret through list/detail APIs.
- [x] Implement active/revoked lifecycle.
- [x] Implement rotation that invalidates the old key.
- [x] Scope every key to exactly the allowed project(s).
- [x] Do not support environment scope in V1; the absence is documented and project scope is exact.
- [x] Record safe `lastUsedAt`.
- [x] Rate-limit ingestion per API key.
- [x] Audit creation, rotation, and revocation.
- [x] Redact `X-API-Key` from every platform log.

### Tests

- [x] valid key;
- [x] malformed key;
- [x] unknown public identifier;
- [x] incorrect secret;
- [x] revoked key;
- [x] rotated old key;
- [x] foreign project supplied in payload is ignored as an authority;
- [x] list API never contains secret.

### Exit criteria

- [x] A leaked database does not immediately reveal usable ingestion keys.
- [x] Source applications cannot select another project through request data.

Evidence (2026-08-02): `ApiKeyService` generates a 96-bit public lookup id and
256-bit random secret, stores only BCrypt `hashedSecret`, and exposes raw material
only through create/rotate results. `ApiKeyController` maps list/revoke responses
to metadata DTOs and requires `ORGANIZATION_ADMIN`; repository operations include
`projectId`. `ApiKeyAuthenticationFilter` validates the scoped key, applies a
per-key token bucket, and never logs the header; `IngestionService` takes project
and organization from the authenticated principal, not request data. Lifecycle
actions write safe CREATE/ROTATE/REVOKE audit summaries and throttle `lastUsedAt`
writes.

Tests: `ApiKeyServiceTest` covers high entropy/hash-only storage, valid/malformed/
unknown/incorrect/revoked keys, last-used throttling, rotation, and foreign-scope
revoke; `ApiKeyAuthenticationFilterTest` covers the 429 per-key burst boundary;
`Phase9SecurityTest` covers admin CRUD, metadata-only list, old-key invalidation,
foreign project authorization, and a payload project selector that cannot change
the key scope. Full backend `test` passed 52/52 tests with no failures, and
backend `build` passed with the pre-existing locked SDK test task excluded.

---

## 9. Phase B4 — WebSocket/STOMP Security

### Blocking priority

The previous `/topic/projects/{projectId}/livetail` model was not sufficient as
an authorization boundary by itself. The implementation now uses session-targeted
user destinations and server-side authorization before a subscription is recorded.

### Tasks

- [x] Authenticate WebSocket connection or STOMP `CONNECT`.
- [x] Validate token expiry and signature.
- [x] Resolve user memberships at subscription time.
- [x] Authorize the selected project server-side.
- [x] Prevent arbitrary foreign-project topic subscription.
- [x] Validate requested service/environment/level filters.
- [x] Bound subscriptions per session.
- [x] Bound connections per user/IP where appropriate.
- [x] Bound outbound queues.
- [x] Define slow-client drop/disconnect policy.
- [x] Increment authorization-failure and dropped-event metrics.
- [x] Remove session/subscription state on disconnect.
- [x] Validate allowed WebSocket origins.

### Tests

- [x] authenticated allowed subscription;
- [x] unauthenticated rejection;
- [x] expired JWT rejection;
- [x] foreign project rejection;
- [x] viewer read allowed;
- [x] duplicate subscription limit;
- [x] disconnect cleanup;
- [x] service/environment/level filtering.

### Exit criteria

- [x] A browser cannot receive a foreign project’s events by constructing a topic name.
- [x] Slow clients cannot block ingestion or grow server memory without bound.

Evidence: `StompAuthChannelInterceptor` rejects missing/invalid JWTs, all public
project topics, unknown STOMP commands, invalid filters, and unauthorized
projects. `ProjectAuthorizationService` is consulted on every subscription;
`LiveTailSubscriptionRegistry` caps sessions/subscriptions and removes them on
`DISCONNECT` or `SessionDisconnectEvent`. `LiveTailPublisher` fans out only to
matching session-targeted user destinations. `WebSocketConfig` bounds inbound and
outbound executor queues plus transport message/send buffers, while publisher
metrics count sent/dropped events. `StompAuthChannelInterceptorTest`,
`LiveTailSubscriptionRegistryTest`, and `LiveTailPublisherTest` cover the
security, cleanup, filtering, and backpressure decisions.

---

## 10. Phase B5 — Input Limits, Redaction, and Privacy

### Tasks

- [x] Enforce maximum HTTP body size.
- [x] Enforce maximum batch count.
- [x] Enforce message length.
- [x] Enforce stack-trace length.
- [x] Enforce context serialized size.
- [x] Enforce context depth.
- [x] Enforce context/tag key count and key length.
- [x] Prevent context from overriding reserved fields.
- [x] Add configurable sensitive-field redaction.
- [x] Redact credentials from platform internal logs.
- [x] Distinguish source application stack traces from platform stack traces.
- [x] Document personal-data expectations for source applications.
- [x] Add retention/privacy guidance for IDs, payload fragments, and stack traces.

### Exit criteria

- [x] A single request cannot allocate uncontrolled memory through deeply nested or oversized data.
- [x] Known credential fields do not appear in internal logs or metrics.

Evidence (2026-08-02): `PayloadLimitFilter` rejects oversized fixed-length and
chunked ingestion bodies with `413`; `IngestionPayloadSanitizer` enforces the
batch, scalar, exception, context, tag, collection, key, depth, and serialized
size bounds before queue admission. `SensitiveDataRedactor` applies configurable
credential-key and text-pattern redaction before events reach the queue, Live
Tail, or MongoDB; persistence, worker, and Telegram diagnostics log only safe
exception type/message fields. Validation counters are bounded aggregate metrics
and do not contain request values. `SensitiveDataRedactorTest`,
`IngestionPayloadSanitizerTest`, `PayloadLimitFilterTest`,
`IngestionControllerTest`, and `BatchIngestionControllerTest` cover the
behavior. The existing `202` admission contract, bounded in-memory queue,
worker batch persistence, and documented pre-MongoDB loss window are unchanged.

---

# PART C — COMPLETE PRODUCT MANAGEMENT FEATURES

## 11. Phase C1 — Organization and User Management

### Tasks

- [x] organization detail API;
- [x] organization settings update;
- [x] list users/memberships;
- [x] invite/create management user;
- [x] change membership role;
- [x] disable/remove membership;
- [x] protect final organization-admin ownership;
- [x] audit all membership changes;
- [x] frontend organization/user management pages;
- [x] loading, empty, error, retry, and confirmation states.

### Exit criteria

- [~] An organization can be administered without direct MongoDB edits or seed changes.

Evidence (2026-08-02): `OrganizationController`,
`OrganizationManagementService`, `OrganizationAuthorizationService`, and the
`organizations` document implement current-organization detail/settings,
member listing, BCrypt-hashed user creation, role/status changes, soft removal,
and final-admin protection. Legacy users retain a project-membership fallback;
new organization roles are persisted on `users.organizationRole`. Every
successful organization or membership mutation writes an organization-scoped
audit record without passwords. `OrganizationPage` and
`organizationApi.ts` provide settings/member management with loading, empty,
error/retry, and confirmation states. `OrganizationManagementServiceTest` and
`OrganizationControllerTest` cover service and Mongo-backed HTTP behavior;
`OrganizationPage.test.tsx` covers the empty state. The remaining gap is the
management login/session bootstrap UI: the page uses an existing
`localStorage.accessToken`, so a complete first-time browser onboarding flow is
not yet claimed. Final validation: backend Gradle test produced 69 tests in
22 suites with 0 failures/errors/skips; frontend typecheck, lint, 2 Vitest
tests, and production build passed.

---

## 12. Phase C2 — Project Management

### Tasks

- [ ] create project;
- [ ] stable unique project key/slug;
- [ ] list authorized projects;
- [ ] project detail;
- [ ] update name/environments/settings;
- [ ] soft deactivate project;
- [ ] prevent ingestion into inactive project;
- [ ] retention configuration;
- [ ] service discovery summary;
- [ ] recent ingestion summary;
- [ ] project-management frontend;
- [ ] audit project changes.

### Exit criteria

- [ ] A new monitored application can be onboarded entirely through supported APIs/UI.

---

## 13. Phase C3 — API-Key Management UI

### Tasks

- [ ] list key metadata;
- [ ] create key;
- [ ] one-time secret display;
- [ ] copy confirmation and warning;
- [ ] rotate key;
- [ ] revoke key;
- [ ] show last-used timestamp and status;
- [ ] never retain raw key in browser storage;
- [ ] confirmation for destructive actions.

### Exit criteria

- [ ] Project admins can complete a safe API-key lifecycle without database access.

---

## 14. Phase C4 — Retention Management UI

### Tasks

- [ ] show project default retention;
- [ ] show per-level overrides;
- [ ] validate ranges;
- [ ] show estimated storage implications where practical;
- [ ] explain TTL cleanup is asynchronous;
- [ ] audit changes;
- [ ] ensure updates affect only future event `expireAt` unless a backfill feature is explicitly designed.

### Exit criteria

- [ ] Retention policy is understandable and configurable without misleading users about exact deletion time.

---

## 15. Phase C5 — Alert Operations Completion

### Tasks

- [ ] validate rule filter combinations;
- [ ] validate window/threshold/cooldown ranges;
- [ ] prevent duplicate or conflicting rules where applicable;
- [ ] add occurrence detail view;
- [ ] acknowledgement actor/time;
- [ ] delivery status/attempt history;
- [ ] audited retry;
- [ ] prevent retry from creating a second occurrence;
- [ ] optional resolve state only if product semantics are defined;
- [ ] provider error sanitization;
- [ ] alert rule and occurrence project isolation.

### Exit criteria

- [ ] Operators can understand why an alert triggered and whether notification delivery succeeded.

---

# PART D — DATA, QUERY, AND INDEX HARDENING

## 16. Phase D1 — Verify MongoDB Documents and Indexes

### Tasks

- [ ] confirm actual `log_events` schema;
- [ ] confirm `receivedAt` and client `timestamp` are distinct;
- [ ] confirm server-controlled `expireAt`;
- [ ] confirm `organizationId`, `projectId`, and `apiKeyId`;
- [ ] confirm optional `eventId`;
- [ ] confirm fingerprint strategy;
- [ ] confirm exception/context/tag bounds;
- [ ] verify TTL index metadata;
- [ ] verify critical compound indexes;
- [ ] verify unique indexes for configuration collections;
- [ ] remove redundant indexes only after measurement;
- [ ] add integration tests for index initialization.

### Exit criteria

- [ ] The documented schema and actual Mongo collections/indexes match exactly.

---

## 17. Phase D2 — Query Plan Review

### Required query plans

- [ ] project + recent time;
- [ ] project + environment + time;
- [ ] project + service + time;
- [ ] project + level + time;
- [ ] project + trace ID;
- [ ] project + request ID;
- [ ] time-series aggregation;
- [ ] severity aggregation;
- [ ] top service aggregation;
- [ ] top fingerprint aggregation.

### Tasks

- [ ] run `explain("executionStats")`;
- [ ] record index selected;
- [ ] record documents examined;
- [ ] record documents returned;
- [ ] detect unexpected `COLLSCAN`;
- [ ] measure write impact of index count;
- [ ] document the final index decision.

### Exit criteria

- [ ] Dominant queries are index-compatible and measured.
- [ ] Index choices are based on evidence, not only documentation.

---

## 18. Phase D3 — Search Behavior Hardening

### Tasks

- [ ] enforce required/default time range;
- [ ] enforce maximum range;
- [ ] enforce maximum page size;
- [ ] validate opaque cursor;
- [ ] test equal timestamps;
- [ ] test deletion/expiry between pages;
- [ ] ensure newest-first ordering is deterministic;
- [ ] return summary projection for list;
- [ ] return full detail only from detail endpoint;
- [ ] document text-search limitations;
- [ ] prevent regex/query abuse;
- [ ] keep exact trace/request lookup project-scoped.

### Exit criteria

- [ ] Search remains predictable with large collections and malformed client input.

---

## 19. Phase D4 — Analytics Hardening

### Tasks

- [ ] validate bucket selection;
- [ ] enforce maximum analytics range;
- [ ] auto-select bucket where appropriate;
- [ ] enforce top-N caps;
- [ ] ensure early `$match`;
- [ ] avoid raw event loading into Java;
- [ ] verify empty result behavior;
- [ ] verify timezone semantics use UTC internally;
- [ ] verify top fingerprint treatment of missing fingerprints;
- [ ] add performance regression tests.

### Exit criteria

- [ ] Dashboard queries remain bounded and use MongoDB aggregation effectively.

---

## 20. Phase D5 — Event Duplicate Policy

### Tasks

- [ ] decide whether duplicate telemetry is tolerated in V1;
- [ ] document `eventId` semantics;
- [ ] measure producer retry behavior;
- [ ] decide whether `(projectId, eventId)` uniqueness is needed;
- [ ] avoid adding a unique index without benchmark;
- [ ] ensure API never claims exactly-once behavior.

### Exit criteria

- [ ] Duplicate behavior is explicit and consistent for SDK and direct API clients.

---

# PART E — RELIABILITY, FAILURE HANDLING, AND SELF-OBSERVABILITY

## 21. Phase E1 — Persistence Failure Injection

### Scenarios

- [ ] temporary MongoDB connection failure;
- [ ] prolonged MongoDB outage;
- [ ] bulk-write transient failure;
- [ ] retry success;
- [ ] retry exhaustion;
- [ ] worker exception;
- [ ] process shutdown with queued events;
- [ ] queue saturation;
- [ ] recovery after MongoDB returns.

### Verify

- [ ] retries are bounded;
- [ ] request threads never sleep for persistence retry;
- [ ] worker remains alive where safe;
- [ ] queue stays bounded;
- [ ] backpressure returns `503`;
- [ ] terminal failure metrics increment;
- [ ] payloads/secrets are not dumped;
- [ ] readiness behavior matches the documented model;
- [ ] remaining queue depth is recorded during shutdown.

### Exit criteria

- [ ] Failure behavior is proven through automated or reproducible tests, not assumed.

---

## 22. Phase E2 — Graceful Shutdown and Deployment Drain

### Tasks

- [ ] readiness becomes unavailable before shutdown;
- [ ] new ingestion is rejected/stopped;
- [ ] queue producers stop;
- [ ] workers drain;
- [ ] partial batch flushes;
- [ ] shutdown has a configured deadline;
- [ ] unfinished event count is recorded;
- [ ] WebSocket sessions close cleanly;
- [ ] notification operations stop safely;
- [ ] MongoDB resources close.

### Exit criteria

- [ ] SIGTERM behavior is tested.
- [ ] A deployment does not hang indefinitely.

---

## 23. Phase E3 — Platform Metrics Completion

### Required metric areas

- [ ] HTTP rate/latency/status;
- [ ] ingestion received;
- [ ] ingestion accepted;
- [ ] validation rejected;
- [ ] backpressure rejected;
- [ ] queue depth/capacity;
- [ ] worker active;
- [ ] batch size;
- [ ] persistence duration;
- [ ] persistence retries;
- [ ] persistence failures;
- [ ] failed events;
- [ ] MongoDB command duration/errors;
- [x] live-tail sessions/subscriptions/sent/dropped;
- [x] WebSocket authorization failures;
- [ ] alert evaluations;
- [ ] alerts triggered;
- [ ] notification success/failure/retry;
- [ ] JVM memory/GC/threads;
- [ ] process CPU.

### Rules

- [ ] no high-cardinality labels;
- [ ] no IDs/messages/trace IDs as tags;
- [ ] metrics endpoint protected at infrastructure edge;
- [ ] metric names documented.

### Exit criteria

- [ ] The platform can diagnose its own queue, worker, MongoDB, WebSocket, and alert health.

---

## 24. Phase E4 — Platform Health Dashboard

### Tasks

Create an operator-only page showing:

- [ ] ingestion accepted/sec;
- [ ] ingestion rejected/sec;
- [ ] queue depth/capacity percentage;
- [ ] worker activity;
- [ ] batch size;
- [ ] MongoDB persistence latency;
- [ ] persistence failures;
- [ ] heap/GC summary;
- [ ] active WebSocket sessions;
- [ ] live-tail drops;
- [ ] alert delivery failures;
- [ ] readiness/dependency state.

### Exit criteria

- [ ] Platform operators do not need to inspect raw Prometheus output for common incidents.

---

## 25. Phase E5 — Platform External Monitoring

### Tasks

- [ ] configure an external uptime/readiness check;
- [ ] define alerts for API unavailability;
- [ ] define queue-depth alert;
- [ ] define persistence-failure alert;
- [ ] define backpressure alert;
- [ ] define MongoDB unavailable alert;
- [ ] define heap/GC alert;
- [ ] define alert-delivery-failure alert;
- [ ] avoid relying only on the monitored platform to alert about itself.

### Exit criteria

- [ ] A total platform failure can still be detected externally.

---

# PART F — JAVA SOURCE SDK AND REAL APPLICATION INTEGRATION

## 26. Phase F1 — Java SDK

### Public responsibilities

- [ ] configure endpoint;
- [ ] configure API key;
- [ ] configure service/environment;
- [ ] send structured event;
- [ ] convert `Throwable` safely;
- [ ] include trace/request IDs;
- [ ] include bounded context/tags;
- [ ] batch events;
- [ ] bounded local queue;
- [ ] bounded retry with exponential backoff and jitter;
- [ ] handle `Retry-After`;
- [ ] distinguish retryable and non-retryable HTTP responses;
- [ ] expose dropped/failed metrics or callbacks;
- [ ] bounded `close()`/flush;
- [ ] never claim durable server persistence after `202`.

### SDK result semantics

At minimum:

- [ ] accepted by server admission;
- [ ] rejected by local SDK queue;
- [ ] rejected by server validation/auth;
- [ ] retry exhausted;
- [ ] dropped according to policy.

### Tests

- [ ] default field injection;
- [ ] exception truncation;
- [ ] batch formation;
- [ ] local queue capacity;
- [ ] 202 handling;
- [ ] 401/403 non-retry;
- [ ] 429/503 retry;
- [ ] timeout retry;
- [ ] shutdown flush;
- [ ] no unbounded memory.

### Exit criteria

- [ ] A Java application can integrate without writing custom HTTP/batching/retry code.

---

## 27. Phase F2 — Spring Boot SDK Integration

### Optional but recommended

- [ ] configuration properties;
- [ ] auto-configuration;
- [ ] conditional enablement;
- [ ] bean lifecycle shutdown;
- [ ] health/metric hooks;
- [ ] safe disabled/no-op mode for local tests;
- [ ] example YAML configuration.

### Exit criteria

- [ ] A Spring Boot application can integrate using configuration and dependency injection.

---

## 28. Phase F3 — LINE Smart Queue Assistant Integration

### Tasks

- [ ] create a dedicated Log Monitoring project;
- [ ] create separate API keys for local/staging/production;
- [ ] add SDK dependency/configuration;
- [ ] propagate `traceId` and `requestId`;
- [ ] instrument high-value failures;
- [ ] avoid logging sensitive LINE/payment credentials;
- [ ] verify events appear in Log Explorer;
- [ ] verify trace correlation;
- [ ] verify dashboard counts;
- [ ] verify Live Tail;
- [ ] configure a low test threshold alert;
- [ ] verify notification;
- [ ] verify cooldown;
- [ ] verify API-key revocation/rotation procedure.

### Recommended event types

- [ ] `AUTH_LOGIN_FAILED`
- [ ] `ORDER_CREATE_FAILED`
- [ ] `PAYMENT_WEBHOOK_FAILED`
- [ ] `QUEUE_CREATE_FAILED`
- [ ] `QUEUE_TRANSITION_CONFLICT`
- [ ] `LINE_PUSH_FAILED`
- [ ] `EMAIL_DELIVERY_FAILED`
- [ ] `DATABASE_QUERY_SLOW`
- [ ] `SCHEDULER_JOB_FAILED`

### Exit criteria

- [ ] LINE Smart Queue is a real end-to-end consumer of the monitoring platform.

---

# PART G — AUTOMATED TESTING AND QUALITY GATES

## 29. Phase G1 — Backend Test Completion

### Unit tests

- [ ] event normalization;
- [ ] retention resolution;
- [ ] fingerprinting;
- [ ] cursor encode/decode;
- [ ] alert threshold boundary;
- [ ] cooldown;
- [ ] notification retry policy;
- [ ] redaction;
- [ ] role/capability decisions.

### Concurrency tests

- [ ] bounded single admission;
- [ ] atomic batch admission;
- [ ] concurrent batch producers;
- [ ] max-size flush;
- [ ] max-wait flush;
- [ ] interruption;
- [ ] retry exhaustion;
- [ ] shutdown drain.

### MongoDB integration tests

- [ ] TTL index metadata;
- [ ] compound indexes;
- [ ] bulk write;
- [ ] cursor pagination;
- [ ] search filters;
- [ ] trace/request query;
- [ ] analytics pipelines;
- [ ] alert-rule/occurrence persistence;
- [ ] project isolation.

### API tests

- [ ] success/error envelopes;
- [ ] stable error codes;
- [ ] auth;
- [ ] roles;
- [ ] API-key lifecycle;
- [ ] ingestion validation;
- [ ] search limits;
- [ ] alert operations.

### Exit criteria

- [ ] A clean backend test run passes using isolated MongoDB.

---

## 30. Phase G2 — Frontend Test Completion

### Tasks

- [ ] authentication state;
- [ ] protected routes;
- [ ] project selector;
- [ ] log filters;
- [ ] cursor load-more;
- [ ] detail drawer;
- [ ] empty/error/retry states;
- [ ] dashboard charts;
- [ ] live-tail connection states;
- [ ] pause/resume/clear;
- [ ] bounded browser buffer;
- [ ] alert rule forms;
- [ ] acknowledgement/retry;
- [ ] role-based action visibility;
- [ ] API-key one-time secret screen.

### Exit criteria

- [ ] Critical UI behaviors are tested beyond build/typecheck.

---

## 31. Phase G3 — Browser E2E

### Required path

- [ ] login;
- [ ] create/select project;
- [ ] create API key;
- [ ] ingest single event;
- [ ] ingest batch;
- [ ] view event in search;
- [ ] open detail;
- [ ] search trace ID;
- [ ] view dashboard;
- [ ] connect Live Tail;
- [ ] create alert rule;
- [ ] generate threshold;
- [ ] verify occurrence;
- [ ] acknowledge;
- [ ] retry failed notification;
- [ ] revoke key and verify ingestion rejection.

### Cross-tenant E2E

- [ ] user from organization A cannot read organization B;
- [ ] user cannot subscribe to foreign Live Tail;
- [ ] viewer cannot mutate settings.

### Exit criteria

- [ ] The main operator journey passes in a real browser.

---

## 32. Phase G4 — Static and Supply-Chain Checks

### Tasks

- [ ] Java formatting;
- [ ] frontend formatting;
- [ ] Java static analysis;
- [ ] frontend lint;
- [ ] typecheck;
- [ ] dependency vulnerability scanning;
- [ ] secret scanning;
- [ ] container/image scanning;
- [ ] license review where needed;
- [ ] fail CI on high-severity actionable findings;
- [ ] document narrow exceptions.

### Exit criteria

- [ ] CI blocks known secret leaks and critical dependency problems.

---

# PART H — PERFORMANCE AND CAPACITY VALIDATION

## 33. Phase H1 — Reproducible Load-Test Suite

### Scenarios

- [ ] single-event steady load;
- [ ] batch-event steady load;
- [ ] burst load;
- [ ] queue saturation;
- [ ] MongoDB slowdown;
- [ ] search under ingestion;
- [ ] analytics under ingestion;
- [ ] live-tail fan-out;
- [ ] alert evaluation under event spikes.

### Record for every run

- [ ] hardware/container limits;
- [ ] JVM options;
- [ ] MongoDB configuration;
- [ ] queue capacity;
- [ ] worker count;
- [ ] batch max size;
- [ ] batch max wait;
- [ ] dataset/event size;
- [ ] duration;
- [ ] command;
- [ ] result.

### Exit criteria

- [ ] Load tests are repeatable through repository scripts.

---

## 34. Phase H2 — Baseline Performance Results

### Required measurements

- [ ] ingestion p50;
- [ ] ingestion p95;
- [ ] ingestion p99;
- [ ] accepted/sec;
- [ ] rejected/sec;
- [ ] maximum stable throughput;
- [ ] queue depth over time;
- [ ] worker throughput;
- [ ] MongoDB persistence duration;
- [ ] average batch size;
- [ ] CPU;
- [ ] heap;
- [ ] GC;
- [ ] search p95;
- [ ] analytics p95;
- [ ] live-tail delivery delay;
- [ ] live-tail dropped events.

### Compare

- [ ] 1000 single requests vs equivalent batch ingestion;
- [ ] batch sizes 100/500/1000 where practical;
- [ ] worker counts 2/4/8 where practical;
- [ ] index alternatives where needed.

### Exit criteria

- [ ] Claims such as `p95 <= 20 ms` are either demonstrated for a declared environment or clearly marked as unproven targets.

---

## 35. Phase H3 — Capacity Plan

### Tasks

- [ ] estimate average stored event size;
- [ ] estimate events/day;
- [ ] estimate storage/day;
- [ ] calculate active retention volume;
- [ ] estimate index size;
- [ ] define MongoDB disk threshold alerts;
- [ ] define queue-capacity rationale;
- [ ] define worker/batch tuning guidance;
- [ ] define expected concurrent Live Tail clients;
- [ ] define alert volume expectations.

### Exit criteria

- [ ] Production sizing is based on expected workload rather than defaults.

---

# PART I — DEPLOYMENT, CI/CD, BACKUP, AND OPERATIONS

## 36. Phase I1 — Docker Hardening

### Tasks

- [ ] multi-stage backend image;
- [ ] production frontend image;
- [ ] non-root execution where practical;
- [ ] `.dockerignore`;
- [ ] health checks;
- [ ] JVM container-aware memory configuration;
- [ ] explicit CPU/memory limits in production-like deployment;
- [ ] persistent MongoDB volume for development baseline;
- [ ] MongoDB authentication;
- [ ] avoid public MongoDB exposure in production;
- [ ] internal network isolation;
- [ ] no secrets baked into images;
- [ ] immutable image tags for release.

### Exit criteria

- [ ] Production-like Compose starts without source-development dependencies or exposed MongoDB.

---

## 37. Phase I2 — Reverse Proxy, HTTPS, and Network Security

### Tasks

- [ ] configure domain;
- [ ] terminate HTTPS;
- [ ] redirect or reject insecure HTTP;
- [ ] proxy frontend/API/WebSocket correctly;
- [ ] validate WebSocket upgrade headers;
- [ ] configure CORS for the intended origin;
- [ ] configure security headers;
- [ ] protect Actuator/Prometheus;
- [ ] rate-limit at application and/or edge;
- [ ] restrict MongoDB network access.

### Exit criteria

- [ ] The deployed platform is accessed through HTTPS and MongoDB is not publicly reachable.

---

## 38. Phase I3 — Secrets and Environment Management

### Tasks

- [ ] complete `.env.example`;
- [ ] separate local/staging/production values;
- [ ] use secret manager or protected server environment;
- [ ] rotate any previously exposed credentials;
- [ ] document API-key rotation;
- [ ] document JWT rotation impact;
- [ ] document Telegram/Slack credential rotation;
- [ ] prevent frontend `VITE_*` secrets;
- [ ] add CI secret scanning.

### Exit criteria

- [ ] No production secret is stored in Git or frontend bundles.

---

## 39. Phase I4 — CI Pipeline

### Required gates

- [ ] backend formatting/static checks;
- [ ] backend unit/integration tests;
- [ ] frontend lint;
- [ ] frontend typecheck;
- [ ] frontend tests;
- [ ] frontend build;
- [ ] E2E critical flow;
- [ ] dependency audit;
- [ ] secret scan;
- [ ] container build;
- [ ] container scan;
- [ ] documentation/contract checks where practical.

### Exit criteria

- [ ] A failing critical test or security check prevents release.

---

## 40. Phase I5 — CD and Release Process

### Tasks

- [ ] immutable version/tag strategy;
- [ ] build from reviewed commit;
- [ ] staging deployment;
- [ ] smoke test;
- [ ] manual production approval;
- [ ] production deployment;
- [ ] readiness verification;
- [ ] rollback procedure;
- [ ] changelog/release notes;
- [ ] preserve backward compatibility during deployment.

### V1-specific caution

Because the default queue is process memory:

- [ ] deployment drain must be explicit;
- [ ] rolling deployment cannot be described as zero-loss;
- [ ] stop accepting traffic before shutdown;
- [ ] observe remaining queue depth.

### Exit criteria

- [ ] A release can be deployed and rolled back using documented commands.

---

## 41. Phase I6 — Backup and Restore

### Policy decisions

- [ ] decide whether raw logs are backed up;
- [ ] align backup retention with log retention/privacy;
- [ ] always back up configuration and alert state.

### Minimum backup scope

- [ ] organizations;
- [ ] users;
- [ ] memberships;
- [ ] projects;
- [ ] API-key metadata;
- [ ] alert rules;
- [ ] alert occurrences;
- [ ] audit events.

### Restore drill

- [ ] run `mongodump`;
- [ ] restore into isolated database;
- [ ] verify indexes;
- [ ] verify TTL;
- [ ] verify login;
- [ ] verify project access;
- [ ] verify API keys;
- [ ] verify ingestion;
- [ ] verify search/analytics;
- [ ] verify alerts.

### Exit criteria

- [ ] A successful restore has been performed and documented.

---

## 42. Phase I7 — Incident Runbooks and Operations Drill

### Required runbooks

- [ ] API unavailable;
- [ ] MongoDB unavailable;
- [ ] queue saturation/backpressure;
- [ ] memory pressure;
- [ ] worker failure;
- [ ] high persistence latency;
- [ ] Live Tail drops;
- [ ] alert storm;
- [ ] notification provider failure;
- [ ] leaked API key;
- [ ] leaked JWT/provider secret;
- [ ] disk/storage pressure;
- [ ] bad deployment/rollback.

### Required drills

- [ ] simulate MongoDB outage;
- [ ] simulate queue saturation;
- [ ] simulate notification failure;
- [ ] simulate SIGTERM deployment;
- [ ] rotate an API key;
- [ ] restore a backup;
- [ ] verify external alerting.

### Exit criteria

- [ ] Operators have proven procedures for the most likely incidents.

---

# PART J — FINAL V1 ACCEPTANCE

## 43. Functional Acceptance

- [ ] organization/user management works;
- [ ] project lifecycle works;
- [ ] API-key lifecycle works;
- [ ] single ingestion works;
- [ ] batch ingestion is all-or-reject;
- [ ] backpressure is explicit;
- [ ] bulk persistence works;
- [ ] retention and TTL work;
- [ ] search works;
- [ ] trace/request correlation works;
- [ ] analytics works;
- [ ] authenticated Live Tail works;
- [ ] alert rule lifecycle works;
- [ ] threshold and cooldown work;
- [ ] durable occurrence works;
- [ ] real notification works;
- [ ] notification retry is idempotent;
- [ ] audit trail exists;
- [ ] LINE Smart Queue integration works.

---

## 44. Security Acceptance

- [ ] all management APIs authenticated;
- [ ] project/organization authorization enforced;
- [ ] foreign-project tests pass;
- [ ] API keys hashed;
- [ ] raw keys shown once;
- [ ] revoked keys rejected;
- [x] WebSocket subscriptions authorized;
- [x] input limits enforced;
- [x] sensitive fields redacted;
- [ ] HTTPS enabled;
- [ ] MongoDB not public;
- [ ] metrics protected;
- [ ] secret scanning passes.

---

## 45. Reliability Acceptance

- [ ] bounded server queue;
- [ ] bounded SDK queue;
- [ ] bounded worker retries;
- [ ] bounded notification retries;
- [x] bounded WebSocket buffers;
- [ ] bounded browser Live Tail history;
- [ ] graceful shutdown tested;
- [ ] MongoDB outage tested;
- [ ] queue recovery tested;
- [ ] readiness/liveness correct;
- [ ] external platform health monitoring active.

---

## 46. Performance Acceptance

- [ ] declared test environment;
- [ ] repeatable load scripts;
- [ ] ingestion p95 measured;
- [ ] search p95 measured;
- [ ] analytics p95 measured;
- [ ] Live Tail latency measured;
- [ ] no unbounded memory under overload;
- [ ] query plans reviewed;
- [ ] capacity plan documented;
- [ ] SLOs accepted or revised based on evidence.

---

## 47. Testing Acceptance

- [ ] backend unit tests pass;
- [ ] MongoDB Testcontainers tests pass;
- [ ] concurrency tests pass;
- [ ] API authorization tests pass;
- [x] WebSocket authorization tests pass;
- [ ] frontend tests pass;
- [ ] browser E2E passes;
- [ ] load smoke passes;
- [ ] CI clean run passes.

---

## 48. Operations Acceptance

- [ ] production-like Docker deployment works;
- [ ] HTTPS works;
- [ ] health/readiness work;
- [ ] Prometheus metrics work;
- [ ] platform health dashboard works;
- [ ] secrets managed outside Git;
- [ ] backup succeeds;
- [ ] restore succeeds;
- [ ] deployment drain succeeds;
- [ ] rollback procedure tested;
- [ ] incident runbooks reviewed.

---

## 49. Documentation Acceptance

- [ ] README matches real commands;
- [ ] AGENT instructions match real repository;
- [ ] docs 00–09 match implementation;
- [ ] API contracts match routes;
- [ ] database docs match collections/indexes;
- [ ] deployment docs match actual topology;
- [ ] limitations are explicit;
- [ ] no claim of exactly-once or zero-log-loss;
- [ ] V1/V2 boundary is clear;
- [ ] changelog/release notes prepared.

---

## 50. V1 Release Gate

V1 can be declared complete only when all blocking items below are complete:

- [!] tenant/project authorization;
- [!] WebSocket subscription authorization;
- [!] API-key hashing, rotation, and revocation;
- [!] input limits and redaction;
- [!] critical MongoDB indexes and TTL verified;
- [!] failure injection and graceful shutdown;
- [!] repeatable load test with actual measurements;
- [!] HTTPS and production secret handling;
- [!] backup and restore drill;
- [!] CI quality gates;
- [!] at least one real source integration;
- [!] canonical documentation synchronized.

Recommended release label:

```text
v1.0.0 — Single-instance production-ready baseline
```

Release notes must include:

> Ingestion admission uses a bounded in-memory queue. Events accepted with HTTP 202 may be lost if the backend process fails before MongoDB persistence completes.

---

# PART K — OPTIONAL V2 ARCHITECTURE EVOLUTION

## 51. V2 Entry Criteria

Do not begin durable broker migration merely because Kafka is popular.

At least one measured requirement should exist:

- [V2] admitted-event loss is unacceptable;
- [V2] replay is required;
- [V2] one JVM cannot buffer peak traffic safely;
- [V2] ingress and persistence must scale independently;
- [V2] multiple consumers require the event stream;
- [V2] multi-instance deployment is required.

---

## 52. V2 Broker Evaluation

### Tasks

- [V2] write a superseding/experimental ADR;
- [V2] compare Kafka and RabbitMQ against actual requirements;
- [V2] define broker acknowledgement semantics;
- [V2] define topic/queue partitioning;
- [V2] define ordering guarantees;
- [V2] define retry and dead-letter behavior;
- [V2] define offset/ack after Mongo persistence;
- [V2] define duplicate behavior;
- [V2] define replay;
- [V2] define schema/version compatibility;
- [V2] define operational ownership and cost.

### Exit criteria

- [V2] Broker selection is evidence-based and documented.

---

## 53. V2 Kafka/RabbitMQ Proof of Concept

### Tasks

- [V2] optional development profile;
- [V2] broker not required for normal V1 startup;
- [V2] producer from ingestion API;
- [V2] consumer group;
- [V2] reuse MongoDB bulk persistence;
- [V2] commit/ack only after persistence;
- [V2] test crash after Mongo write but before offset commit;
- [V2] document at-least-once semantics;
- [V2] test backlog and recovery;
- [V2] measure latency and throughput;
- [V2] compare operational complexity with V1.

### Exit criteria

- [V2] PoC proves a real benefit before replacing V1 default flow.

---

## 54. V2 Multi-Instance Readiness

### Tasks

- [V2] coordinate alert evaluation ownership;
- [V2] design WebSocket session routing/fan-out;
- [V2] share/reload API-key state safely;
- [V2] define notification worker ownership;
- [V2] remove assumptions of process-local singleton state;
- [V2] deploy MongoDB replica set;
- [V2] evaluate sharding only with measured volume;
- [V2] run rolling deployment tests.

### Exit criteria

- [V2] Multiple instances do not duplicate alert/notification work or lose authorization consistency.

---

# PART L — RECOMMENDED EXECUTION ORDER

## 55. Immediate next work

Execute in this order:

1. **Repository and documentation re-audit**
2. **Tenant/project authorization**
3. **WebSocket/STOMP authorization**
4. **API-key lifecycle and secret handling**
5. **Input limits, redaction, and privacy**
6. **Organization/project/API-key management UI**
7. **MongoDB query-plan and index verification**
8. **Failure injection and graceful shutdown validation**
9. **Platform metrics and health dashboard**
10. **Java SDK**
11. **LINE Smart Queue integration**
12. **Backend/frontend/E2E security tests**
13. **Load testing and capacity plan**
14. **Docker/HTTPS/secrets hardening**
15. **CI/CD**
16. **Backup/restore drill**
17. **Incident drills**
18. **Final V1 acceptance and release**
19. **Only then evaluate V2 broker architecture**

---

## 56. Suggested Milestones

### Milestone M1 — Security Baseline

- authorization;
- API keys;
- WebSocket security;
- input limits;
- audit.

### Milestone M2 — Product Administration

- organization users;
- projects;
- API-key UI;
- retention UI;
- alert operations.

### Milestone M3 — Reliability Evidence

- failure injection;
- graceful shutdown;
- self-observability;
- query plans;
- load tests.

### Milestone M4 — Real Integration

- Java SDK;
- LINE Smart Queue integration;
- end-to-end alert demo.

### Milestone M5 — Production Operations

- Docker hardening;
- HTTPS;
- secrets;
- CI/CD;
- backup/restore;
- runbooks.

### Milestone M6 — V1.0 Release

- all release gates;
- synchronized docs;
- tested deployment;
- explicit limitations.

### Milestone M7 — Optional V2

- broker ADR;
- PoC;
- at-least-once semantics;
- multi-instance evaluation.

---

## 57. Final Completion Statement Template

Use this only after all V1 release gates pass:

```text
Centralized Log Monitoring System V1 is complete as a secure,
single-instance production-ready baseline.

It supports authenticated structured-log ingestion, bounded backpressure,
MongoDB bulk persistence and retention, indexed search, trace correlation,
analytics, authenticated Live Tail, threshold alerts, notification delivery,
auditability, observability, tested deployment, backup/restore, and a real
source-application integration.

Known limitation: HTTP 202 confirms admission to the bounded in-memory queue,
not durable persistence. Unexpected backend failure before MongoDB flush can
lose admitted events. Durable broker admission remains a V2 evolution.
```
