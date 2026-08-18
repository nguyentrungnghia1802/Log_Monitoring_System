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

---

## 9. Load test plan

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

## 10. Performance experiment discipline

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

## 11. Suggested validation commands

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

## 12. Static/security checks

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

## 13. Manual acceptance scenario with LINE Smart Queue

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

---

## 14. Definition of done

A backend feature is not done until:

- requirement is satisfied;
- tests cover success/failure;
- concurrency behavior is considered;
- metrics exist where operationally relevant;
- security/redaction is reviewed;
- docs are updated;
- performance impact is measured when high-volume paths change.

For ingestion-path changes, load/backpressure behavior is part of correctness.
