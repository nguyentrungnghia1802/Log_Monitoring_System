# Development and Testing

## 1. Prerequisites

- JDK 21
- Docker Desktop / Docker Compose
- Node.js 20+ for frontend tooling
- MongoDB through Docker for standard local development
- Gradle wrapper or Maven wrapper committed to the repository

Use one build tool for backend; this specification assumes **Gradle** examples unless the repository chooses Maven before implementation.

---

## 2. Local environment

Recommended services:

```text
frontend   : 5173
backend    : 8080
mongodb    : 27017
```

The Vite development proxy defaults to `http://localhost:8080`. If that port
is already used by another local service, run the backend on an alternate port
and pass matching frontend variables:

```powershell
$env:SERVER_PORT='18080'
./gradlew bootRun

$env:VITE_BACKEND_URL='http://localhost:18080'
$env:VITE_WS_URL='ws://localhost:18080/ws-logs'
npm run dev -- --host 127.0.0.1 --port 15173
```

`VITE_BACKEND_URL` controls the `/api` and `/ws-logs` development proxy
targets. `VITE_WS_URL` controls the Live Tail STOMP connection. These are
development-only overrides; production deployments should provide the
frontend and backend through their normal configured origin.

Example:

```bash
docker compose up -d mongodb
./gradlew bootRun
cd frontend && npm install && npm run dev
```

---

## 3. Configuration

Suggested environment/application properties:

```text
SPRING_PROFILES_ACTIVE=local

MONGODB_URI=mongodb://root:example_password@localhost:27017/log_monitor?authSource=admin

JWT_SECRET=...
JWT_EXPIRATION_MS=900000
AUTH_REFRESH_TOKEN_EXPIRATION_SECONDS=604800
AUTH_LOGIN_BURST_CAPACITY=5
AUTH_LOGIN_WINDOW_SECONDS=60
INGESTION_QUEUE_CAPACITY=50000
INGESTION_WORKER_COUNT=4
INGESTION_BATCH_MAX_SIZE=500
INGESTION_BATCH_MAX_WAIT_MS=500
INGESTION_ENQUEUE_TIMEOUT_MS=5

ALERT_NOTIFICATION_MODE=mock
```

Secrets must not be committed.

---

## 4. Local seed

For a new local database, enable the idempotent local-profile bootstrap with
`LOCAL_BOOTSTRAP_ADMIN_ENABLED=true` and provide
`LOCAL_BOOTSTRAP_ADMIN_EMAIL`/`LOCAL_BOOTSTRAP_ADMIN_PASSWORD` (minimum 12
characters). It creates only a missing local organization and first
organization administrator, and does not run outside the `local` profile.

Seed only enough data to develop:

- one organization;
- one admin user;
- one project named `LINE Smart Queue Assistant`;
- one development ingestion API key;
- optional sample alert rule.

Raw API-key secret may be printed once by an explicit local seed command, never on every application startup.

---

## 5. Test strategy

| Layer | Tool | Focus |
| --- | --- | --- |
| Pure unit | JUnit 5 | normalizer, retention, fingerprints, cursor, alert policy |
| Concurrency unit/integration | JUnit 5 | bounded admission, worker drain, shutdown, races |
| Repository integration | Testcontainers MongoDB | indexes, queries, aggregation, bulk write |
| API integration | Spring Boot Test / MockMvc | auth, status codes, validation |
| WebSocket integration | Spring test client | authorization/filtering/fan-out |
| Frontend unit/component | Vitest + Testing Library | filters, dashboards, live-tail behavior |
| Browser E2E | Playwright | login, key creation, ingestion, search, alert flow |
| Load | k6 or Gatling | ingestion latency/throughput/backpressure |

---

## 6. Critical unit tests

### Ingestion

- valid level normalization;
- missing required field rejected;
- oversized HTTP body, message, exception, batch, and nested context rejected;
- credential fields and credential-shaped text are redacted before admission;
- server cannot be given tenant/project authority through payload;
- `expireAt` computed from retention;
- API key revoked path.

### Organization management

- current organization detail and settings update;
- organization member listing without password-hash leakage;
- BCrypt password hashing on management-user creation;
- role change, disable, remove, and disabled-login rejection;
- final active organization-admin protection;
- organization-scoped audit records;
- frontend loading, empty, error/retry, and destructive-action confirmation states.

### Management authentication

- valid email/password login and BCrypt verification;
- generic unknown/wrong/disabled credential failures;
- short access-token expiry and malformed-token rejection;
- HttpOnly refresh-cookie rotation and old-session rejection;
- logout revocation of refresh and access credentials;
- login rate-limit response and safe authentication audit actions;
- frontend refresh bootstrap, protected routing, in-memory access token, and logout.

### Project management

- project key normalization and organization-local uniqueness;
- organization-admin project create/update/deactivate authorization;
- authorized project listing and foreign-organization 404 isolation;
- retention validation and resolver configuration;
- service discovery and 24-hour ingestion/error summaries from MongoDB;
- project mutation audit records;
- valid API-key ingestion rejection after soft deactivation;
- frontend project loading, empty, retry, editing, retention, and confirmation
  states.

### API-key management UI

- project-scoped API-key metadata list never renders the raw secret;
- create and rotate return a one-time secret screen with warning and copy
  confirmation;
- dismissing the one-time screen clears transient secret state and does not
  write the raw value to `localStorage`;
- rotate and revoke require explicit destructive-action confirmation;
- revoked status and last-used timestamp remain visible in the inventory;
- backend API-key management rejects a project document outside the principal's
  current organization.

### Queue

- admission succeeds below capacity;
- queue exactly at capacity rejects;
- concurrent producers never exceed configured capacity;
- batch drain respects max size;
- interruption exits workers cleanly.

### Alerting

- threshold below boundary does not trigger;
- threshold crossing triggers;
- cooldown prevents duplicate occurrence;
- cooldown expiry permits new trigger;
- rule filters and numeric ranges are validated and normalized;
- duplicate project-local rule names are rejected;
- delivery failure persists occurrence with sanitized attempt history;
- acknowledgement records actor/time once and writes an audit event;
- retry updates the same occurrence and writes an audit event;
- alert rule and occurrence lookups remain project-scoped.

---

## 7. MongoDB integration tests

Must use a real MongoDB container for behaviors that mocks cannot validate.

Test:

- TTL index definition;
- compound index presence;
- bulk insert;
- project/time search;
- trace/request lookup;
- cursor pagination with equal timestamps;
- aggregation buckets;
- top services/errors;
- authorization repository scope.

`LogQueryServiceTest` also covers bounded default/max ranges, rejected page
sizes, malformed cursors, literal (escaped) message search, equal-timestamp
cursor pagination after deletion, summary/detail projection separation, and
foreign-project trace isolation.

`AnalyticsServiceTest` covers bounded/default ranges, supported and rejected
intervals, automatic bucket selection, bucket-cap rejection, severity counts,
top-N limits, UTC bucket alignment, missing-fingerprint normalization, and
empty project results. The histogram assertions exercise the MongoDB
aggregation output and therefore protect the no-raw-event-loading path.

The D5 duplicate policy is covered by `MongoSchemaAndIndexIntegrationTest`,
which persists two events with the same client `eventId` and verifies two
distinct server documents, and by `LogMonitoringClientTest`, which measures a
temporary `503` followed by `202` and verifies that the SDK sends the exact
same event body/event ID on both attempts. These tests are deterministic retry
harnesses, not a claim about production traffic retry rates.

The D2 query-plan review is a real-MongoDB integration test rather than a
mocked repository check. Run it from `backend` with the root-project task path
so the filter is not incorrectly applied to SDK subprojects:

```bash
./gradlew :test --tests com.example.logmonitor.persistence.MongoQueryPlanIntegrationTest --no-parallel --info
```

The test reports the winning index, `totalDocsExamined`, `nReturned`, and
collection-scan detection for six search shapes and four analytics pipelines.
It also records paired write medians for the current `log_events` secondary
index set versus a baseline collection. The measured values are evidence for
index decisions, not a substitute for the later production-like load plan.

TTL deletion timing itself should not use fragile exact-second assertions.

---

## 8. Failure injection tests

### MongoDB outage

1. fill queue partially;
2. stop MongoDB container;
3. verify worker failures/retries;
4. verify readiness fails;
5. continue ingesting until queue capacity;
6. verify backpressure response;
7. restore MongoDB;
8. verify workers recover and queue drains where retries have not been exhausted.

The E1 automated failure-injection evidence covers the bounded and observable
failure boundary. `LogEventPersistenceServiceTest` verifies retry success,
retry exhaustion after three attempts, terminal-failure counters, and redacted
exception logging. `PersistenceWorkerTest` verifies that a worker continues
with the next batch after a persistence exception and records shutdown drain
metrics. `IngestionQueueTest` exercises concurrent single-event and all-or-
reject batch admission, while `IngestionControllerBackpressureTest` verifies
the `503` response and `Retry-After: 1` header. The real-MongoDB
`MongoSchemaAndIndexIntegrationTest` stops and restarts MongoDB and verifies
that persistence recovers with the restarted container. Finally,
`MongoHealthReadinessIntegrationTest` verifies readiness transitions from
`UP/200` to `DOWN/503` when MongoDB stops. These tests do not log event
payloads, credentials, or secrets.

### Notification outage

- provider mock throws;
- occurrence remains persisted;
- retry state increments;
- source logs remain unaffected.

### Process shutdown

- queue has events;
- send SIGTERM;
- readiness flips;
- producer admission stops;
- worker drains within timeout;
- application exits.

E2 coverage is provided by `GracefulShutdownCoordinatorTest`,
`GracefulShutdownIntegrationTest`, `PersistenceWorkerTest`,
`LiveTailSubscriptionRegistryTest`, and `AlertServiceTest`. The integration
test verifies readiness changes from `UP/200` to `OUT_OF_SERVICE/503`, new
ingestion returns `503 INGESTION_SHUTTING_DOWN`, and Spring closes the managed
Mongo client. Worker tests cover partial-batch flushing, queue-depth metrics,
and the configured deadline. Notification tests verify that no new provider
call starts after shutdown. Live-tail state is cleared before the Spring
WebSocket transport stops.

Build a boot jar and run the reproducible process smoke test from `backend`:

```powershell
./gradlew bootJar --no-parallel
./scripts/verify-graceful-shutdown.ps1
```

The script waits for readiness, invokes the test-profile shutdown trigger on
Windows (the portable JVM equivalent of the signal path), and checks the
coordinator and worker drain markers. On POSIX it sends `kill -TERM` to the
boot process. The shutdown trigger is exposed only by `application-test.yml`;
it must never be enabled in a production profile.

## 9. Platform metrics

The backend uses Spring Boot Actuator/Micrometer for `http.server.requests`,
`jvm.*`, and `process.*`. Application-specific names are deliberately
low-cardinality and documented in `docs/project/02_SYSTEM_ARCHITECTURE.md`:

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
ingestion.persistence.retries
ingestion.persistence.failures
mongodb.command.duration
mongodb.command.errors
alert.evaluations
alert.triggered
alert.delivery.success
alert.delivery.failure
alert.delivery.retry
```

`MongoCommandMetricsListener` bounds command tags to a known command set and
does not tag database names, collections, event IDs, trace IDs, or messages.
`SystemStatusEndpointTest` verifies real HTTP, JVM, and process meters;
`MongoCommandMetricsIntegrationTest` verifies command timing against a real
MongoDB container. The Prometheus endpoint must be restricted at the reverse
proxy using `ops/nginx/actuator-metrics.conf`, with the example private ranges
replaced by the actual scraper allowlist.

External monitoring configuration is kept outside the application runtime:
the scheduled GitHub workflow probes `PLATFORM_READINESS_URL`, while the
Prometheus example uses an independent Blackbox Exporter target. This split
must be exercised in staging with a deliberately stopped backend before
production rollout.

---

## 10. Java SDK integration

The Java source SDK is tested independently with an in-process HTTP server:

```powershell
./gradlew :sdk:log-monitoring-java-sdk:test --no-parallel
```

`LogMonitoringClientTest` covers default service/environment and generated
correlation fields, exception/stack truncation, context/tag serialization,
batch formation and partial flush, local queue capacity, `202` admission,
non-retryable `401/403`, retryable `429/503`, `Retry-After`, transport timeout,
retry exhaustion, bounded shutdown flush, and post-close policy drops.

The callback contract separates immediate `QUEUED_LOCALLY` from the eventual
server result. A successful `202` only means that the platform accepted the
batch into its bounded process-memory queue; integration tests must not assert
durable persistence at that point. The duplicate-event policy remains
at-least-once and tolerant of repeated `eventId` values.

### 10.1 Spring Boot starter

Run the starter context tests with:

```powershell
./gradlew :sdk:log-monitoring-spring-boot-starter:test --no-parallel
```

`LogMonitoringAutoConfigurationTest` proves that the starter is safe when
added to a local application: the default is a network-free no-op bean. An
explicit `log-monitoring.client.enabled=true` creates the bounded client,
closes it with the application context, and exposes queue health/metrics.
Configuration and the complete YAML example are maintained in
`sdk/log-monitoring-spring-boot-starter/README.md`.

---

## 11. Load test plan

Load tests are a core deliverable, not an afterthought.

### Test A — baseline single ingestion

Measure:

- requests/sec;
- p50/p95/p99 enqueue response;
- queue depth;
- worker throughput;
- Mongo batch latency;
- CPU;
- heap;
- GC.

### Test B — batch ingestion

Compare:

```text
1000 HTTP calls x 1 event
vs
1 HTTP call x 1000 events
```

Use equivalent event volume and record total overhead.

### Test C — Mongo slowdown

Artificially reduce persistence capacity and verify graceful backpressure.

### Test D — query under ingestion load

Run dashboard/search traffic while ingestion continues and observe interference.

---

## 12. Performance experiment discipline

Every optimization should record:

```text
Hypothesis
Baseline
Change
Measurement
Result
Decision
```

Examples:

- batch size 100 vs 500 vs 1000;
- worker count 2 vs 4 vs 8;
- index A vs index B;
- raw search projection vs full document;
- message compression only if network profile justifies it.

Avoid tuning by intuition only.

---

## 13. Suggested validation commands

Backend:

```bash
./gradlew clean test
./gradlew integrationTest
./gradlew bootJar
```

Frontend:

```bash
npm run lint
npm run typecheck
npm run test
npm run build
```

E2E/load scripts should be standardized later in repository scripts.

---

## 14. Static/security checks

Recommended CI roadmap:

- Spotless or equivalent formatting;
- Checkstyle/PMD/SpotBugs where useful;
- dependency vulnerability scan;
- secret scanning;
- frontend lint/typecheck;
- container scan;
- test coverage thresholds only for meaningful critical modules.

Coverage percentage must not replace behavioral test quality.

---

## 15. Manual acceptance scenario with LINE Smart Queue

1. Start monitoring platform.
2. Create project/API key.
3. Configure LINE Smart Queue to send structured logs.
4. Trigger a normal queue creation.
5. Confirm event appears in search.
6. Trigger `QUEUE_CREATE_FAILED`.
7. Confirm trace/request correlation.
8. Open Live Tail and see matching error.
9. Configure rule `QUEUE_CREATE_FAILED >= N/min`.
10. Generate threshold traffic.
11. Confirm one alert occurrence and notification.
12. Confirm cooldown suppresses duplicates.
13. Verify retention policy is visible.

The source-side implementation is in the sibling `LINE Smart Queue Assistant`
repository. After a project-scoped staging key has been provisioned, configure
its environment without committing the secret:

```text
LOG_MONITORING_ENABLED=true
LOG_MONITORING_ENDPOINT=https://<monitoring-host>
LOG_MONITORING_API_KEY=<staging-project-key>
LOG_MONITORING_ENVIRONMENT=staging
```

Run the source adapter's admission smoke test from that repository:

```bash
npm run log-monitoring:verify
```

The command requires a real staging project/key and only proves `202` server
admission. It does not claim MongoDB durability. The remaining Log Explorer,
Live Tail, dashboard, alert notification/cooldown, and key rotation checks
must be performed against an owner-provisioned running platform; without that
external state they remain `BLOCKED_EXTERNAL` in the master checklist.

For the G3 browser acceptance path, use a real browser against the local UI
and record each completed step in `docs/PROJECT_COMPLETION_CHECKLIST.md`.
The backend health endpoint and an HTTP `200` from the Vite page are only
startup checks; they do not count as browser E2E evidence.

---

## 16. Definition of done

A backend feature is not done until:

- requirement is satisfied;
- tests cover success/failure;
- concurrency behavior is considered;
- metrics exist where operationally relevant;
- security/redaction is reviewed;
- docs are updated;
- performance impact is measured when high-volume paths change.

For ingestion-path changes, load/backpressure behavior is part of correctness.
