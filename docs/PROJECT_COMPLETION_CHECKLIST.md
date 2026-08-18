# Centralized Log Monitoring System — Master Completion Checklist

Last prepared: 2026-08-18

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
- [x] Verify no real secret exists in source, history, fixtures, logs, or screenshots.
- [x] Map every implemented feature to its canonical requirement and API contract.

Evidence: `docs/project/FEATURE_MATRIX.md`, `backend/build.gradle`,
`frontend/package.json`, `.env.example`, `compose.yaml`, and the controller/source
tree audit on 2026-08-02. The secret scan found committed development defaults
(`example_password`, placeholder JWT secret) and the static `demo-api-key`
fallback was removed. A 2026-08-18 all-revision high-confidence credential
pattern scan found no AWS/GitHub/OpenAI/Slack token or private-key material;
remaining password/secret matches are configuration keys, placeholders, tests,
or credential-handling code rather than deployable credentials.

Build evidence (2026-08-18): the multi-module `backend/clean test --no-parallel`
and `backend/build --no-parallel` pass, including the Java SDK, demo, and Spring
Boot starter modules. The earlier generated-test-output lock is no longer
present.

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

- [x] Verify email/password login is implemented.
- [x] Use a secure password hash.
- [x] Validate inactive/disabled user behavior.
- [x] Implement short-lived access tokens.
- [x] Decide and document refresh/session strategy.
- [x] Implement logout/revocation behavior.
- [x] Prevent account enumeration in authentication errors.
- [x] Add login rate limiting.
- [x] Add authentication audit events where appropriate.
- [x] Ensure frontend never stores long-lived secrets insecurely.

### Tests

- [x] valid login;
- [x] invalid password;
- [x] disabled account;
- [x] expired token;
- [x] malformed token;
- [x] logout/revocation;
- [x] rate-limit behavior.

### Exit criteria

- [x] Every management endpoint is protected except explicitly public health/login/refresh routes.
- [x] Authentication errors expose no sensitive details.

Evidence (2026-08-18): `AuthenticationService` implements case-insensitive
email/legacy-username login, BCrypt verification, generic invalid credential
responses, active-account checks, token-bucket login throttling, and safe audit
actions. Access JWTs default to 15 minutes. A random opaque refresh token is
stored only as a SHA-256 hash in TTL-indexed `auth_sessions` and sent as an
HttpOnly SameSite cookie; refresh rotates the session and logout revokes it,
which also invalidates its access JWT. `SecurityConfig` requires JWT auth for
all management APIs except login/refresh and public health/ingestion boundaries.
The React `AuthProvider`, protected router, login page, shared authorized HTTP
client, and Live Tail client keep access tokens only in process memory and
restore sessions through the cookie. `AuthControllerTest`, `JwtServiceTest`,
and `AuthFlow.test.tsx` cover the listed paths. Backend `:test` and frontend
lint/typecheck/test/build gates passed on this slice.

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
- [x] Restrict project creation/update/deactivation to allowed roles.
- [x] Restrict API-key creation/rotation/revocation.
- [x] Restrict retention changes.
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
- [x] foreign-project API keys;
- [x] foreign-project live-tail subscription;
- [x] viewer mutation rejection;
- [x] operator organization-management rejection.

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
organization decisions. C1 organization management and C2 project management
controllers now enforce organization-admin mutation boundaries; the C2
controller test covers operator rejection and foreign-organization project
isolation. API-key management controllers and cross-project tests are covered
by the B3 evidence below. WebSocket authorization is covered by the B4
interceptor/registry/publisher tests below.
Retention changes require current-organization `MANAGE` permission and
`ProjectControllerTest` verifies a project operator cannot mutate them.
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
- [x] management rejects a project document outside the principal's organization.

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
foreign project authorization, organization-document scope, and a payload
project selector that cannot change the key scope. The canonical multi-module
backend `clean test --no-parallel` and `build --no-parallel` gates now pass with
the SDK test task included.

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

- [x] An organization can be administered without direct MongoDB edits or seed changes.

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
`OrganizationPage.test.tsx` covers the empty state. The B1 login/session UI now
protects the console, and an explicitly enabled local-profile bootstrap creates
the first organization administrator idempotently without a MongoDB edit. See
the B1 evidence for the current validation gates.

---

## 12. Phase C2 — Project Management

### Tasks

- [x] create project;
- [x] stable unique project key/slug;
- [x] list authorized projects;
- [x] project detail;
- [x] update name/environments/settings;
- [x] soft deactivate project;
- [x] prevent ingestion into inactive project;
- [x] retention configuration;
- [x] service discovery summary;
- [x] recent ingestion summary;
- [x] project-management frontend;
- [x] audit project changes.

### Exit criteria

- [x] A new monitored application can be onboarded through project APIs/UI.

Evidence (2026-08-02): Project, ProjectRepository,
ProjectManagementService, ProjectController, and
ProjectActivityRepository implement organization-scoped project creation,
stable key validation, authorized listing/detail, settings and retention
updates, soft deactivation, service discovery, and 24-hour activity summaries.
ApiKeyAuthenticationFilter rejects keys for an inactive project with
409 PROJECT_INACTIVE. Project mutations write safe PROJECT or
PROJECT_RETENTION audit events. ProjectManagementServiceTest and
ProjectControllerTest cover validation, role boundaries, foreign-organization
isolation, Mongo activity summaries, audit records, and inactive ingestion.
The ProjectsPage/projectApi.ts implementation includes loading, empty,
error/retry, edit, retention, and deactivation-confirmation states. Frontend
lint, typecheck, Vitest, and production build pass in the current workspace.
Backend targeted validation passed with:
backend/gradlew.bat test --tests
com.example.logmonitor.project.application.ProjectManagementServiceTest
--tests com.example.logmonitor.project.api.ProjectControllerTest --tests
com.example.logmonitor.auth.config.ApiKeyAuthenticationFilterTest -x
:sdk:log-monitoring-java-sdk:test --rerun-tasks.

---

## 13. Phase C3 — API-Key Management UI

### Tasks

- [x] list key metadata;
- [x] create key;
- [x] one-time secret display;
- [x] copy confirmation and warning;
- [x] rotate key;
- [x] revoke key;
- [x] show last-used timestamp and status;
- [x] never retain raw key in browser storage;
- [x] confirmation for destructive actions.

### Exit criteria

- [x] Project admins can complete a safe API-key lifecycle without database access.

Evidence (2026-08-02): `frontend/src/pages/apikeys/ApiKeysPage.tsx` and
`apiKeyApi.ts` implement project selection, metadata-only inventory, create,
one-time secret warning/copy/dismiss flow, rotate, revoke, status, last-used
display, retry states, and destructive-action confirmations. Raw secrets are
held only in transient component state; they are not placed in `localStorage`
or the metadata query cache. `ApiKeysPage.test.tsx` covers metadata leakage,
one-time display/copy/dismiss, browser-storage safety, and confirmation gates.
The backend Phase9 security suite passed 12/12 tests after adding the
organization-document boundary check. The full backend application test passed
77/77 tests in 24 suites. Frontend lint, typecheck, Vitest, and production build
now pass in the current workspace; the authenticated shared HTTP client attaches
the in-memory access JWT to the lifecycle calls.

---

## 14. Phase C4 — Retention Management UI

### Tasks

- [x] show project default retention;
- [x] show per-level overrides;
- [x] validate ranges;
- [x] show estimated storage implications where practical;
- [x] explain TTL cleanup is asynchronous;
- [x] audit changes;
- [x] ensure updates affect only future event `expireAt` unless a backfill feature is explicitly designed.

### Exit criteria

- [x] Retention policy is understandable and configurable without misleading users about exact deletion time.

Evidence (2026-08-18): the authenticated project administration UI displays
the stored default and DEBUG/INFO/WARN/ERROR/FATAL overrides, accepts blank
override inheritance, and enforces whole-day values from 1 through 3650 before
submission. It presents a clearly labeled event-day planning ratio rather than
a false byte estimate, explains asynchronous MongoDB TTL cleanup, and states
that updates affect future events without rewriting existing `expireAt` values.
The existing backend command validates the same ranges, updates the in-process
resolver, and writes a `PROJECT_RETENTION` audit event. Component and controller
tests cover the UI payload and operator mutation rejection.

---

## 15. Phase C5 — Alert Operations Completion

### Tasks

- [x] validate rule filter combinations;
- [x] validate window/threshold/cooldown ranges;
- [x] prevent duplicate or conflicting rules where applicable;
- [x] add occurrence detail view;
- [x] acknowledgement actor/time;
- [x] delivery status/attempt history;
- [x] audited retry;
- [x] prevent retry from creating a second occurrence;
- [x] optional resolve state only if product semantics are defined;
- [x] provider error sanitization;
- [x] alert rule and occurrence project isolation.

### Exit criteria

- [x] Operators can understand why an alert triggered and whether notification delivery succeeded.

Evidence (2026-08-18): `AlertService` normalizes bounded filter lists, validates
level/window/threshold/cooldown values, and rejects case-insensitive duplicate
rule names within a project. `AlertOccurrence` now stores first acknowledgement
actor/time plus a backward-compatible delivery-attempt history. Provider names
and errors are redacted and length-bounded before persistence, including thrown
adapter failures. Acknowledgement and retry write organization/project-scoped
audit events; retry dispatches and saves the existing occurrence only. The
React alert pages select an authorized project, expose rule filters and numeric
bounds, and show occurrence trigger window/value, acknowledgement, and attempt
history. `resolved` remains intentionally absent because no V1 resolution
semantics are defined. `AlertServiceTest`, `AlertsPage.test.tsx`,
`AlertRulesPage.test.tsx`, and the existing `Phase9SecurityTest` cover these
behaviors and project-scoped nested lookups. Validation passed with backend
`clean test` and `build` across the application, Java SDK, example, and starter;
frontend lint/typecheck, 12/12 Vitest tests, and production build also passed.

---

# PART D — DATA, QUERY, AND INDEX HARDENING

## 16. Phase D1 — Verify MongoDB Documents and Indexes

### Tasks

- [x] confirm actual `log_events` schema;
- [x] confirm `receivedAt` and client `timestamp` are distinct;
- [x] confirm server-controlled `expireAt`;
- [x] confirm `organizationId`, `projectId`, and `apiKeyId`;
- [x] confirm optional `eventId`;
- [x] confirm fingerprint strategy;
- [x] confirm exception/context/tag bounds;
- [x] verify TTL index metadata;
- [x] verify critical compound indexes;
- [x] verify unique indexes for configuration collections;
- [x] remove redundant indexes only after measurement;
- [x] add integration tests for index initialization.

### Exit criteria

- [x] The documented schema and actual Mongo collections/indexes match exactly.

Evidence (2026-08-18): `MongoSchemaAndIndexIntegrationTest` starts MongoDB 7,
persists a fully populated event through `LogEventPersistenceService`, and
asserts the raw snake-case BSON fields, distinct producer/server timestamps,
server-anchored TTL value, tenant/API-key authority fields, fingerprint, nested
exception, context, and tags. It also reads `listIndexes()` to verify the
absolute `expire_at` TTL metadata, all critical log query indexes, alert rule
and occurrence indexes, and unique configuration indexes. Missing
project/environment and project/service indexes were added. Exception details
now persist in the documented shape rather than under a compatibility `value`
wrapper. `LogEventTest` proves a future producer timestamp cannot extend
retention. D2 query-plan and write-impact evidence was completed before any
index-removal decision; all current indexes remain retained.

---

## 17. Phase D2 — Query Plan Review

### Required query plans

- [x] project + recent time;
- [x] project + environment + time;
- [x] project + service + time;
- [x] project + level + time;
- [x] project + trace ID;
- [x] project + request ID;
- [x] time-series aggregation;
- [x] severity aggregation;
- [x] top service aggregation;
- [x] top fingerprint aggregation.

### Tasks

- [x] run `explain("executionStats")`;
- [x] record index selected;
- [x] record documents examined;
- [x] record documents returned;
- [x] detect unexpected `COLLSCAN`;
- [x] measure write impact of index count;
- [x] document the final index decision.

### Exit criteria

- [x] Dominant queries are index-compatible and measured.
- [x] Index choices are based on evidence, not only documentation.

Evidence (2026-08-18): `MongoQueryPlanIntegrationTest` uses MongoDB 7 and
executes all ten required search/analytics shapes with `executionStats`. The
six search plans selected `idx_logs_proj_time`,
`idx_logs_proj_environment_time`, `idx_logs_proj_service_time`,
`idx_logs_proj_level_time`, `idx_logs_proj_trace`, and
`idx_logs_proj_request`, examining 100/40/40/40/14/9 documents respectively;
all returned the same count under the capped test query and none used
`COLLSCAN`. The four aggregation plans examined 120/120/120/80 documents;
the first three selected `idx_logs_proj_time`, while top fingerprints selected
`idx_logs_proj_level_time`, with no collection scan. The test also performed
five paired 1,000-document write rounds after warm-up: the nine-secondary-index
`log_events` collection had a 54.446 ms median versus 33.636 ms for the
baseline collection (1.619x in this local sample). Because each retained index
supports a measured dominant query and the write measurement is directional,
no index was removed; production-like load evidence is required before future
pruning.

---

## 18. Phase D3 — Search Behavior Hardening

### Tasks

- [x] enforce required/default time range;
- [x] enforce maximum range;
- [x] enforce maximum page size;
- [x] validate opaque cursor;
- [x] test equal timestamps;
- [x] test deletion/expiry between pages;
- [x] ensure newest-first ordering is deterministic;
- [x] return summary projection for list;
- [x] return full detail only from detail endpoint;
- [x] document text-search limitations;
- [x] prevent regex/query abuse;
- [x] keep exact trace/request lookup project-scoped.

### Exit criteria

- [x] Search remains predictable with large collections and malformed client input.

Evidence (2026-08-18): `LogQueryService` now defaults omitted bounds to one
hour, enforces a configurable 168-hour maximum (with a 31-day hard cap), and
rejects invalid ranges and page sizes with stable error codes. Limits default
to 50 and reject values above the configured/server maximum instead of silently
clamping. Cursors are strictly decoded and malformed values return
`400 INVALID_CURSOR`; the seek predicate remains
`timestamp DESC, _id DESC`, so equal timestamps are deterministic and a
deleted/expired prior-page record does not break the next page. List queries
use an explicit summary projection, while detail queries retain full event
fields and both paths remain project-scoped. Message search is bounded and
escapes input as a literal case-insensitive pattern, preventing user regex
execution. `LogQueryServiceTest` covers these behaviors, including a foreign
project sharing the same trace ID. The React Log Explorer now fetches the
project-scoped detail endpoint only after an operator selects a summary row.

---

## 19. Phase D4 — Analytics Hardening

### Tasks

- [x] validate bucket selection;
- [x] enforce maximum analytics range;
- [x] auto-select bucket where appropriate;
- [x] enforce top-N caps;
- [x] ensure early `$match`;
- [x] avoid raw event loading into Java;
- [x] verify empty result behavior;
- [x] verify timezone semantics use UTC internally;
- [x] verify top fingerprint treatment of missing fingerprints;
- [x] add performance regression tests.

### Exit criteria

- [x] Dashboard queries remain bounded and use MongoDB aggregation effectively.

Evidence: `AnalyticsService` validates the project/time range, supported
intervals, bucket count, and configured top-N limits before executing
project-scoped aggregations. Histogram grouping uses `$dateTrunc` in UTC
after an early `$match`; only grouped buckets are mapped in Java. Missing
fingerprints become `UNKNOWN_ERROR`. `AnalyticsServiceTest` covers these
guards, empty results, UTC alignment, and the D4 regression set. The D2
`MongoQueryPlanIntegrationTest` provides the real-MongoDB `executionStats`
regression evidence for representative analytics pipelines.

---

## 20. Phase D5 — Event Duplicate Policy

### Tasks

- [x] decide whether duplicate telemetry is tolerated in V1;
- [x] document `eventId` semantics;
- [x] measure producer retry behavior;
- [x] decide whether `(projectId, eventId)` uniqueness is needed;
- [x] avoid adding a unique index without benchmark;
- [x] ensure API never claims exactly-once behavior.

### Exit criteria

- [x] Duplicate behavior is explicit and consistent for SDK and direct API clients.

Evidence (2026-08-18): V1 accepts repeated client `eventId` values as separate
immutable log documents; Mongo `_id` remains server-generated and no unique
`(project_id,event_id)` index is added. `05_API.md`, `04_DATABASE.md`,
`03_DOMAIN_AND_FLOWS.md`, and ADR-014 document the same rule for single,
batch, direct-client, and SDK ingestion. `MongoSchemaAndIndexIntegrationTest`
verifies duplicate storage, while `LogMonitoringClientTest` measures the
bounded `503` to `202` retry harness and verifies a stable event ID/body.
The result is an explicit at-least-once/duplicate-tolerant boundary, never an
exactly-once claim.

---

# PART E — RELIABILITY, FAILURE HANDLING, AND SELF-OBSERVABILITY

## 21. Phase E1 — Persistence Failure Injection

### Scenarios

- [x] temporary MongoDB connection failure;
- [x] prolonged MongoDB outage;
- [x] bulk-write transient failure;
- [x] retry success;
- [x] retry exhaustion;
- [x] worker exception;
- [x] process shutdown with queued events;
- [x] queue saturation;
- [x] recovery after MongoDB returns.

### Verify

- [x] retries are bounded;
- [x] request threads never sleep for persistence retry;
- [x] worker remains alive where safe;
- [x] queue stays bounded;
- [x] backpressure returns `503`;
- [x] terminal failure metrics increment;
- [x] payloads/secrets are not dumped;
- [x] readiness behavior matches the documented model;
- [x] remaining queue depth is recorded during shutdown.

### Exit criteria

- [x] Failure behavior is proven through automated or reproducible tests, not assumed.

Evidence (2026-08-18): `LogEventPersistenceServiceTest` proves transient
recovery and three-attempt retry exhaustion while checking failure counters and
redaction. `PersistenceWorkerTest` proves worker continuity after an exception
and records queue depth/events at shutdown. `IngestionQueueTest` proves the
concurrent all-or-reject admission boundary, and
`IngestionControllerBackpressureTest` proves `503` plus `Retry-After: 1`.
`MongoSchemaAndIndexIntegrationTest` uses a real MongoDB container to prove
bounded outage behavior and recovery after restart. The
`MongoHealthReadinessIntegrationTest` proves the configured readiness group
changes from `UP/200` to `DOWN/503` when MongoDB stops. The request path only
queues events; persistence retries run in the worker/service path, and logs
record safe exception metadata without payloads or credentials.

---

## 22. Phase E2 — Graceful Shutdown and Deployment Drain

### Tasks

- [x] readiness becomes unavailable before shutdown;
- [x] new ingestion is rejected/stopped;
- [x] queue producers stop;
- [x] workers drain;
- [x] partial batch flushes;
- [x] shutdown has a configured deadline;
- [x] unfinished event count is recorded;
- [x] WebSocket sessions close cleanly;
- [x] notification operations stop safely;
- [x] MongoDB resources close.

### Exit criteria

- [x] SIGTERM (or the platform-equivalent JVM shutdown path) is tested.
- [x] A deployment does not hang indefinitely.

Evidence (2026-08-18): `GracefulShutdownCoordinator` publishes
`ReadinessState.REFUSING_TRAFFIC` before closing `IngestionQueue` admission.
`GracefulShutdownIntegrationTest` proves readiness `UP/200` to
`OUT_OF_SERVICE/503`, rejects new ingestion with
`INGESTION_SHUTTING_DOWN`, and verifies Spring closes the managed Mongo client.
`PersistenceWorkerTest` proves partial-batch flush and bounded worker shutdown;
`ingestion.worker.shutdown.queue_depth` and
`ingestion.worker.shutdown.unfinished_events` record drain state. The live-tail
registry clears sessions/subscriptions on context close, while `AlertService`
does not start a new provider call after shutdown and Telegram calls have a
bounded HTTP timeout. `server.shutdown=graceful`, `SHUTDOWN_TIMEOUT`, and
`SHUTDOWN_TIMEOUT_MS` provide deployment deadlines. The process smoke script
passes on Windows using the test-profile JVM shutdown trigger; its POSIX path
sends `SIGTERM` and checks the same shutdown markers.

---

## 23. Phase E3 — Platform Metrics Completion

### Required metric areas

- [x] HTTP rate/latency/status;
- [x] ingestion received;
- [x] ingestion accepted;
- [x] validation rejected;
- [x] backpressure rejected;
- [x] queue depth/capacity;
- [x] worker active;
- [x] batch size;
- [x] persistence duration;
- [x] persistence retries;
- [x] persistence failures;
- [x] failed events;
- [x] MongoDB command duration/errors;
- [x] live-tail sessions/subscriptions/sent/dropped;
- [x] WebSocket authorization failures;
- [x] alert evaluations;
- [x] alerts triggered;
- [x] notification success/failure/retry;
- [x] JVM memory/GC/threads;
- [x] process CPU.

### Rules

- [x] no high-cardinality labels;
- [x] no IDs/messages/trace IDs as tags;
- [x] metrics endpoint protected at infrastructure edge;
- [x] metric names documented.

### Exit criteria

- [x] The platform can diagnose its own queue, worker, MongoDB, WebSocket, and alert health.

Evidence (2026-08-18): Spring Boot HTTP metrics and JVM/process binders are
verified through the random-port `SystemStatusEndpointTest`. Ingestion queue
metrics now use the documented `ingestion.*` names, including received,
accepted, validation, backpressure, shutdown rejection, depth, capacity,
worker activity, and batch size. `LogEventPersistenceService` exposes
`ingestion.persistence.duration`, retries, failures, and failed event counts.
`MongoCommandMetricsListener` records bounded `mongodb.command.duration` and
`mongodb.command.errors` tags without database, collection, IDs, or messages.
Alert evaluation, trigger, delivery success/failure, and retry counters are
covered by `AlertServiceTest`; Mongo command wiring is covered by
`MongoCommandMetricsIntegrationTest`. The edge allowlist example is
`ops/nginx/actuator-metrics.conf`; production deployments must replace its
private-network examples with the Prometheus scraper allowlist.

---

## 24. Phase E4 — Platform Health Dashboard

### Tasks

Create an operator-only page showing:

- [x] ingestion accepted/sec;
- [x] ingestion rejected/sec;
- [x] queue depth/capacity percentage;
- [x] worker activity;
- [x] batch size;
- [x] MongoDB persistence latency;
- [x] persistence failures;
- [x] heap/GC summary;
- [x] active WebSocket sessions;
- [x] live-tail drops;
- [x] alert delivery failures;
- [x] readiness/dependency state.

### Exit criteria

- [x] Platform operators do not need to inspect raw Prometheus output for common incidents.

Evidence (2026-08-18): `GET /api/v1/system/health-dashboard` returns a
low-cardinality operator snapshot for cumulative ingestion/persistence/live-tail/
alert counters, queue and worker gauges, batch/persistence summaries, JVM and
CPU signals, and readiness dependency statuses. The endpoint verifies the
current user is an active `ORGANIZATION_ADMIN`; the frontend `PlatformHealthPage`
is hidden from non-operators, polls every five seconds, and derives accepted /
rejected rates from consecutive snapshots. `SystemStatusControllerTest`,
`OperatorRoute.test.tsx`, and `PlatformHealthPage.test.tsx` cover the boundary
and dashboard rendering. Frontend gates (`lint`, `typecheck`, `test`, `build`)
pass.

---

## 25. Phase E5 — Platform External Monitoring

### Tasks

- [x] configure an external uptime/readiness check;
- [x] define alerts for API unavailability;
- [x] define queue-depth alert;
- [x] define persistence-failure alert;
- [x] define backpressure alert;
- [x] define MongoDB unavailable alert;
- [x] define heap/GC alert;
- [x] define alert-delivery-failure alert;
- [x] avoid relying only on the monitored platform to alert about itself.

### Exit criteria

- [x] A total platform failure can still be detected externally.

Evidence (2026-08-18): `.github/workflows/external-platform-readiness.yml`
executes the independent `check-readiness.ps1` probe every five minutes when
the repository variable `PLATFORM_READINESS_URL` is configured. The Prometheus
baseline in `ops/monitoring/prometheus/scrape-config.example.yml` combines
backend metrics with an independent Blackbox Exporter readiness target, and
`ops/monitoring/prometheus/platform-alerts.yml` covers API/readiness outage,
queue pressure, backpressure, persistence/Mongo failures, heap/GC pressure, and
alert-delivery failures. The GitHub workflow and Prometheus/Blackbox deployment
must remain outside the backend failure boundary; setting the repository
variable and deploying these monitoring components are environment actions
that cannot be performed from this local checkout.

---

# PART F — JAVA SOURCE SDK AND REAL APPLICATION INTEGRATION

## 26. Phase F1 — Java SDK

### Public responsibilities

- [x] configure endpoint;
- [x] configure API key;
- [x] configure service/environment;
- [x] send structured event;
- [x] convert `Throwable` safely;
- [x] include trace/request IDs;
- [x] include bounded context/tags;
- [x] batch events;
- [x] bounded local queue;
- [x] bounded retry with exponential backoff and jitter;
- [x] handle `Retry-After`;
- [x] distinguish retryable and non-retryable HTTP responses;
- [x] expose dropped/failed metrics or callbacks;
- [x] bounded `close()`/flush;
- [x] never claim durable server persistence after `202`.

### SDK result semantics

At minimum:

- [x] accepted by server admission;
- [x] rejected by local SDK queue;
- [x] rejected by server validation/auth;
- [x] retry exhausted;
- [x] dropped according to policy.

### Tests

- [x] default field injection;
- [x] exception truncation;
- [x] batch formation;
- [x] local queue capacity;
- [x] 202 handling;
- [x] 401/403 non-retry;
- [x] 429/503 retry;
- [x] timeout retry;
- [x] shutdown flush;
- [x] no unbounded memory.

### Exit criteria

- [x] A Java application can integrate without writing custom HTTP/batching/retry code.

Evidence (2026-08-18): `LogMonitoringClient` now provides endpoint/API-key and
service/environment configuration, structured `LogEventPayload` submission,
safe throwable conversion, generated-or-propagated trace/request IDs, bounded
context/tags, fixed-capacity batching, bounded exponential backoff with jitter,
integer/RFC 1123 `Retry-After` handling, retry classification, and a callback
result contract. `LogSubmissionOutcome` distinguishes local queue admission,
server admission, server rejection, retry exhaustion, and policy drops. The
client explicitly describes `202` as admission to the server's bounded
process-memory queue rather than durable persistence. `LogMonitoringClientTest`
covers 12 in-process HTTP scenarios, and
`sdk/log-monitoring-java-sdk/README.md` documents the consumer integration.

---

## 27. Phase F2 — Spring Boot SDK Integration

### Optional but recommended

- [x] configuration properties;
- [x] auto-configuration;
- [x] conditional enablement;
- [x] bean lifecycle shutdown;
- [x] health/metric hooks;
- [x] safe disabled/no-op mode for local tests;
- [x] example YAML configuration.

### Exit criteria

- [x] A Spring Boot application can integrate using configuration and dependency injection.

Evidence (2026-08-18): `LogMonitoringProperties` maps every bounded
`LogMonitoringClientConfig` setting and defaults `enabled=false`. The starter
publishes `LogMonitoringAutoConfiguration` through Boot's imports metadata,
creates a closing-aware `LogMonitoringClient` only when explicitly enabled,
and otherwise provides `LogMonitoringOperations` through a network-free
`NoopLogMonitoringOperations`. `LogMonitoringHealthIndicator` reports safe
enabled/no-op and queue details, while `LogMonitoringMetricsListener` exposes
low-cardinality outcome counters and queue gauges when Micrometer is present.
`LogMonitoringAutoConfigurationTest` covers default no-op behavior, enabled
property binding, health/metrics, context shutdown, and all SDK bound mappings;
`sdk/log-monitoring-spring-boot-starter/README.md` contains the complete YAML
example and the `202` server-admission limitation.

---

## 28. Phase F3 — LINE Smart Queue Assistant Integration

### Tasks

- [ ] create a dedicated Log Monitoring project;
- [~] create separate API keys for local/staging/production;
- [x] add SDK dependency/configuration;
- [x] propagate `traceId` and `requestId`;
- [x] instrument high-value failures;
- [x] avoid logging sensitive LINE/payment credentials;
- [ ] verify events appear in Log Explorer;
- [ ] verify trace correlation;
- [ ] verify dashboard counts;
- [ ] verify Live Tail;
- [ ] configure a low test threshold alert;
- [ ] verify notification;
- [ ] verify cooldown;
- [ ] verify API-key revocation/rotation procedure.

### Recommended event types

- [x] `AUTH_LOGIN_FAILED`
- [x] `ORDER_CREATE_FAILED`
- [x] `PAYMENT_WEBHOOK_FAILED`
- [x] `QUEUE_CREATE_FAILED`
- [x] `QUEUE_TRANSITION_CONFLICT`
- [x] `LINE_PUSH_FAILED`
- [x] `EMAIL_DELIVERY_FAILED`
- [x] `DATABASE_QUERY_SLOW`
- [x] `SCHEDULER_JOB_FAILED`

### Exit criteria

- [~] LINE Smart Queue is a real end-to-end consumer of the monitoring platform.

Evidence (2026-08-18): LINE Smart Queue Assistant `main` commit
`cfe947ed68f27ee215b0b3738c1182951b08c65c` contains a native Node 20 source
adapter under `apps/api/src/modules/log-monitoring/`. It is disabled by
default, uses the batch ingestion contract with `X-API-Key`, propagates the
request/trace context, bounds queue/batch/retry/flush behavior, sanitizes
context and exceptions, and emits all nine recommended failure event types
from auth, queue, staff, orders, payments, LINE, email, scheduler, and DB
paths. The target repository passed 116 API suites/709 tests, 11 frontend
files/17 tests, lint, typecheck, format check, and production build. The
staging smoke command is `npm run log-monitoring:verify` from that repository.

Manual platform acceptance is `BLOCKED_EXTERNAL`: this workspace has no
owner-provisioned Log Monitoring project/API keys or running backend/provider
endpoint, so Log Explorer, trace correlation, dashboard/Live Tail delivery,
alert notification/cooldown, and key rotation cannot be truthfully marked as
verified. The source configuration is prepared for one project-scoped key per
environment without committing any secret.

---

# PART G — AUTOMATED TESTING AND QUALITY GATES

## 29. Phase G1 — Backend Test Completion

### Unit tests

- [x] event normalization;
- [x] retention resolution;
- [x] fingerprinting;
- [x] cursor encode/decode;
- [x] alert threshold boundary;
- [x] cooldown;
- [x] notification retry policy;
- [x] redaction;
- [x] role/capability decisions.

### Concurrency tests

- [x] bounded single admission;
- [x] atomic batch admission;
- [x] concurrent batch producers;
- [x] max-size flush;
- [x] max-wait flush;
- [x] interruption;
- [x] retry exhaustion;
- [x] shutdown drain.

### MongoDB integration tests

- [x] TTL index metadata;
- [x] compound indexes;
- [x] bulk write;
- [x] cursor pagination;
- [x] search filters;
- [x] trace/request query;
- [x] analytics pipelines;
- [x] alert-rule/occurrence persistence;
- [x] project isolation.

### API tests

- [x] success/error envelopes;
- [x] stable error codes;
- [x] auth;
- [x] roles;
- [x] API-key lifecycle;
- [x] ingestion validation;
- [x] search limits;
- [x] alert operations.

### Exit criteria

- [x] A clean backend test run passes using isolated MongoDB.

Evidence (2026-08-18): from `backend`,
`$env:SPRING_PROFILES_ACTIVE='test'; $env:MONGODB_URI='mongodb://root:example_password@localhost:27017/log_monitor_test?authSource=admin'; ./gradlew clean test --no-parallel` completed `BUILD SUCCESSFUL`. The run includes the Testcontainers MongoDB schema/index, query-plan, health/readiness, shutdown, API, alert, persistence, and SDK module suites. Boundary tests now explicitly cover exact alert thresholds, below-threshold suppression, cooldown expiry, and interruption-safe persistence retry backoff.

---

## 30. Phase G2 — Frontend Test Completion

### Tasks

- [x] authentication state;
- [x] protected routes;
- [x] project selector;
- [x] log filters;
- [x] cursor load-more;
- [x] detail drawer;
- [x] empty/error/retry states;
- [x] dashboard charts;
- [x] live-tail connection states;
- [x] pause/resume/clear;
- [x] bounded browser buffer;
- [x] alert rule forms;
- [x] acknowledgement/retry;
- [x] role-based action visibility;
- [x] API-key one-time secret screen.

### Exit criteria

- [x] Critical UI behaviors are tested beyond build/typecheck.

Evidence (2026-08-18): frontend Vitest now covers Log Explorer cursor
pagination, filter/detail, empty/error states, and Live Tail connection,
pause/resume buffering, filtering, clearing, and bounded history behavior.
The full frontend run passed 11 files/17 tests; lint, typecheck, and Vite
production build also passed.

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

- [!] The main operator journey passes in a real browser.

Evidence (2026-08-18): the G3 local-run prerequisite is now explicit. The
Vite development proxy accepts `VITE_BACKEND_URL` for REST and WebSocket
forwarding, and Live Tail accepts `VITE_WS_URL`; this permits the UI to run on
`15173` against a backend on `18080` when the default `8080` port is occupied.
The backend health endpoint returned `200` and the Vite page returned `200`.
Frontend validation passed with 11 test files/17 tests, lint, typecheck, and
production build.
The backend `./gradlew test --no-parallel` and `./gradlew build --no-parallel`
also passed; an initial clean run exposed a transient SDK retry-test timing
failure, and the isolated test followed by the full rerun passed.

The real-browser portion is `BLOCKED_EXTERNAL`: the available browser runtime
failed before opening or interacting with the page on repeated attempts with
`failed to write kernel assets: The system cannot find the path specified.
(os error 3)`. Consequently no G3 required-path or cross-tenant checkbox is
marked complete, and the runtime error must be resolved before claiming the
operator journey or tenant-isolation E2E evidence.

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
