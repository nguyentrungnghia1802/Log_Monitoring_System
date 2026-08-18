# Codebase Guide

## 1. Repository layout

Recommended repository:

```text
.
|-- backend/
|   |-- src/main/java/com/example/logmonitor/
|   |-- src/main/resources/
|   \-- src/test/
|
|-- frontend/
|   |-- src/
|   \-- ...
|
|-- docs/
|   \-- project/
|
|-- docker/
|-- scripts/
|-- compose.yaml
|-- README.md
\-- AGENTS.md
```

V1 should remain one backend deployable and one frontend deployable.

---

## 2. Backend package layout

Use package-by-feature with internal layers.

```text
com.example.logmonitor
|
|-- auth/
|-- organization/
|-- project/
|-- apikey/
|-- ingestion/
|   |-- api/
|   |-- application/
|   |-- domain/
|   |-- infrastructure/
|   \-- worker/
|
|-- logquery/
|-- analytics/
|-- livetail/
|-- alerting/
|-- notification/
|-- audit/
|-- observability/
|-- common/
\-- LogMonitoringApplication.java
```

Example ingestion feature:

```text
ingestion/
  api/
    IngestionController.java
    IngestionRequest.java
  application/
    IngestionService.java
    LogEventNormalizer.java
  domain/
    LogEvent.java
    LogLevel.java
  infrastructure/
    IngestionQueue.java
  worker/
    PersistenceWorker.java
    BatchAssembler.java
```

---

## 3. Layer rules

| Layer | May do | Must not do |
| --- | --- | --- |
| API/controller | HTTP mapping, validation entry, response mapping | Mongo queries, queue algorithms |
| Application/service | Orchestrate use case, authorization context, transactions | Depend on React/HTTP response classes unnecessarily |
| Domain | Invariants, value objects, pure policy | Spring/Mongo infrastructure |
| Repository | Mongo persistence/query mapping | HTTP authorization decisions |
| Worker | Poll buffer, batch, invoke persistence/application ports | Reimplement controller policy |
| Integration adapter | Telegram/Slack transport | Own alert rule semantics |

---

## 4. Java conventions

- Java 21.
- Prefer immutable records/value objects for DTO/domain values where appropriate.
- Prefer constructor injection.
- Use explicit executors rather than generic default async pools.
- Name executor beans and thread factories.
- Do not hide blocking calls inside methods advertised as non-blocking.
- Use `java.time.Instant` for machine timestamps.
- Use UTC internally.
- Avoid `Optional` fields in persisted entities/DTOs where simple nullability is clearer.
- Keep exception taxonomy stable through application error codes.

---

## 5. Concurrency conventions

Concurrency is a core learning objective.

Required rules:

1. ingestion queue must have fixed capacity;
2. worker executor size is explicit;
3. no unbounded `LinkedBlockingQueue` without capacity;
4. no `Executors.newCachedThreadPool()` for ingestion;
5. shutdown behavior must be tested;
6. metrics must expose active threads, queue depth, rejections;
7. interruption must be handled correctly;
8. batch retries must not sleep request threads.

Prefer direct Java primitives in the ingestion core before adding abstractions that hide behavior:

```text
BlockingQueue
ThreadPoolExecutor
ScheduledExecutorService
CompletableFuture where appropriate
AtomicLong/LongAdder for counters
Semaphore where a bounded external-operation concurrency limit is useful
```

---

## 6. MongoDB conventions

- Repositories own `MongoTemplate`/Spring Data operations.
- Aggregation pipelines belong in analytics/query infrastructure, not controllers.
- Index definitions are reviewed against `04_DATABASE.md`.
- High-volume search requires project/time scope.
- Use projections for list/analytics views.
- Bulk inserts are centralized in persistence infrastructure.
- Do not use Mongo transactions for ordinary log ingestion.

---

## 7. Frontend layout

```text
frontend/src/
|-- app/
|-- pages/
|   |-- dashboard/
|   |-- logs/
|   |-- live-tail/
|   |-- alerts/
|   |-- projects/
|   \-- settings/
|-- components/
|-- features/
|-- hooks/
|-- services/
|-- store/
|-- types/
|-- utils/
\-- main.tsx
```

Rules:

- TanStack Query owns server state;
- page components orchestrate, reusable UI stays in components/features;
- log filtering state should be URL-addressable where practical;
- never store API-key raw secrets after the one-time creation screen;
- charts consume aggregate DTOs, not raw event arrays.

---

## 7.1 Java source SDK layout

The source SDK is intentionally dependency-free and lives outside the Spring
application under `sdk/log-monitoring-java-sdk/`:

```text
sdk/log-monitoring-java-sdk/src/main/java/com/example/logmonitor/sdk/
|-- LogEventPayload.java
|-- LogMonitoringClientConfig.java
|-- LogMonitoringClient.java
|-- LogMonitoringOperations.java
|-- LogSubmissionOutcome.java
\-- LogSubmissionResult.java
```

`LogMonitoringClient` owns a fixed-capacity `ArrayBlockingQueue`, a named
single worker, bounded batch formation, HTTP retry classification, and a
bounded close flush. `LogSubmissionResult` is callback-based because the
public `log`/`error` methods return before server admission. `202` is exposed
as `ACCEPTED_BY_SERVER_ADMISSION`, never as durable persistence.

The optional Spring Boot adapter lives under
`sdk/log-monitoring-spring-boot-starter/`. Its auto-configuration binds the
same bounded settings, supplies `LogMonitoringOperations` as either the real
client or a network-free no-op, and adds Actuator health plus Micrometer
outcome/queue signals. It is disabled by default and is registered through
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

### 7.2 LINE Smart Queue source adapter

The LINE Smart Queue Assistant is maintained in the sibling
`LINE_Smart_Queue_Assistant` repository. Its Node 20 API uses a small native
HTTP adapter under `apps/api/src/modules/log-monitoring/` rather than coupling
the TypeScript application to the Java SDK. The adapter sends the platform
batch contract to `/api/v1/ingest/logs/batch` with a project-scoped
`X-API-Key`, and remains disabled unless `LOG_MONITORING_ENABLED=true` plus an
environment-specific endpoint/key are supplied.

The source integration is fail-open and bounded: request/trace correlation is
captured through AsyncLocalStorage/OpenTelemetry, queue and batch sizes are
fixed, retries honor bounded `Retry-After`, shutdown flush is time-limited,
and context/exception values pass through the source sanitizer before
submission. The source emits stable failure events such as
`QUEUE_CREATE_FAILED`, `PAYMENT_WEBHOOK_FAILED`, `LINE_PUSH_FAILED`, and
`DATABASE_QUERY_SLOW` without including LINE user IDs, payment credentials,
API keys, tokens, or raw provider payloads. `npm run log-monitoring:verify`
performs a staging-only admission smoke test and prints no secret.

---

## 8. Error handling

Backend stable error type concept:

```java
public record ApiError(
    String code,
    String message,
    Map<String, Object> details
) {}
```

Operational exceptions should have stable codes.

Unexpected exceptions:

- generate/request-correlate an internal error ID;
- log safe diagnostics;
- return generic response;
- never return stack trace to normal clients.

---

## 9. Internal logging

The monitoring backend must not create a recursive firehose into itself in V1.

Backend internal logs:

- write structured stdout/file logs;
- include request ID;
- redact secrets;
- use sampling for noisy internal debug logs;
- do not send every internal diagnostic back into the same ingestion pipeline by default.

A later deployment may ship platform logs to a separate instance/project.

---

## 10. Adding an ingestion field

1. Update product/domain contract.
2. Confirm whether field is reserved or context.
3. Update request DTO/validation.
4. Update normalizer.
5. Update Mongo document mapping.
6. Decide whether index/search support is required.
7. Add tests.
8. Update API and database docs.

Do not add a field to an index simply because the field exists.

---

## 11. Adding a query/filter

1. Confirm user requirement.
2. Add API query parameter.
3. Enforce project/time scope.
4. Design/query index.
5. Run `explain`.
6. Add repository/integration test.
7. Add frontend filter.
8. Add load/performance regression if query can be expensive.

---

## 12. Adding an alert channel

Use a port:

```java
interface AlertNotificationSender {
    NotificationResult send(AlertNotification notification);
}
```

Adapters:

```text
MockAlertNotificationSender
TelegramAlertNotificationSender
SlackAlertNotificationSender
```

Rule evaluation must not depend directly on Telegram/Slack SDK details.

---

## 13. Files requiring extra care

- security configuration;
- API-key hashing/lookup;
- ingestion queue configuration;
- worker executor configuration;
- Mongo index migrations/init;
- alert evaluator/cooldown logic;
- redaction utility;
- graceful shutdown;
- `.env.example`;
- Docker/Compose production configuration.

---

## 14. Documentation-first rule

Before a material architecture change, review:

1. `00_PROJECT_CONTEXT.md`
2. `01_PRODUCT_REQUIREMENTS.md`
3. `02_SYSTEM_ARCHITECTURE.md`
4. relevant domain/database/API document
5. `09_ROADMAP_AND_DECISIONS.md`

A coding agent must not silently introduce Kafka, Redis, microservices, or a different database without an accepted architecture decision.
