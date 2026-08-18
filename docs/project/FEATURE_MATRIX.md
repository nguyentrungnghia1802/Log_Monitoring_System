# Feature Matrix — Repository Re-audit

Last reviewed: 2026-08-18

This is an evidence index for the baseline re-audit. `[x]` means the current
implementation and a relevant automated test were found. `[~]` means code
exists but the contract, security boundary, or validation is incomplete.

| Requirement | Implementation | Tests | Status |
| --- | --- | --- | --- |
| FR-ING-001/002/005/006 — single/batch admission and backpressure | `backend/src/main/java/com/example/logmonitor/ingestion/{api,application,infrastructure}` | `IngestionControllerTest`, `BatchIngestionControllerTest`, `IngestionQueueTest` | [x] |
| FR-STO-002/003/006 — batching, bulk write, retention | `PersistenceWorker`, `LogEventPersistenceService`, `RetentionPolicyResolver` | `RetentionPolicyResolverTest`, persistence/application context tests | [~] |
| FR-LOG-001/002/004/005/007 — scoped search and cursor | `LogQueryController`, `LogQueryService`, `LogEventRepository` | `LogQueryServiceTest` | [~] |
| FR-AN-001/002/003/004/005 — Mongo analytics | `AnalyticsController`, `AnalyticsService` | `AnalyticsServiceTest` | [~] |
| FR-LIVE-001/002/004 — authenticated, filtered, bounded STOMP live tail | `WebSocketConfig`, `LiveTailPublisher`, `LiveTailSubscriptionRegistry`, `StompAuthChannelInterceptor` | `StompAuthChannelInterceptorTest`, `LiveTailSubscriptionRegistryTest`, `LiveTailPublisherTest` | [x] |
| FR-ALT-001/002/003/005/006/007/008/009 — validated rules, cooldown, durable explainable occurrences, adapters and audited retry | `AlertRuleController`, `AlertController`, `AlertService`, notification adapters, alert operations UI | `AlertServiceTest`, `AlertsPage.test.tsx`, `AlertRulesPage.test.tsx`, `Phase9SecurityTest` | [x] |
| FR-AUTH-001/002 — login, revocable sessions, JWT and project membership checks | `AuthenticationService`, `JwtService`, `AuthSession`, `AuthContext`, `ProjectAuthorizationService`, `ProjectSecurityInterceptor` | `AuthControllerTest`, `JwtServiceTest`, `AuthFlow.test.tsx`, `ProjectAuthorizationServiceTest`, `Phase9SecurityTest` | [x] |
| FR-AUTH-004/005 — hashed API-key creation and revocation | `ApiKeyService`, `ApiKeyController`, `ApiKeyAuthenticationFilter` | `Phase9SecurityTest`, `ApiKeyServiceTest` | [x] |
| FR-OBS-001/002/003/004 — health, Prometheus, ingestion, and platform metrics | Actuator, `SystemStatusController`, queue/worker/persistence/Mongo/alert metrics | `SystemStatusEndpointTest`, `MongoCommandMetricsIntegrationTest`, queue/worker/persistence/alert tests | [x] |
| Cross-project nested alert isolation | `AlertRuleRepository`, `AlertOccurrenceRepository`, `AlertService` use `(id, projectId)` lookups | `Phase9SecurityTest.doesNotExposeForeignProjectNestedAlertResources` | [x] |
| Management project/API-key lifecycle | `ProjectController`, `ApiKeyController`, `ProjectManagementService`, `ApiKeysPage` | `ProjectControllerTest`, `Phase9SecurityTest`, `ApiKeysPage.test.tsx` | [~] |

## Audit findings

- `202 Accepted` remains memory-queue admission; no durability claim was added.
- The static `demo-api-key` authentication bypass was removed. Tests now create
  a real random key and validate it through the hash-backed repository path.
- The project and API-key management routes are present in the controller tree;
  frontend lint, typecheck, component tests, and production build are runnable.
- Management refresh tokens are hash-only, rotated, and revocable; access
  tokens are short-lived and kept only in frontend process memory.
- The starter module now disables executable `bootJar` generation and publishes
  a normal library JAR; this fixes the multi-module assemble failure.
- Live Tail now authenticates STOMP `CONNECT`, authorizes user destinations against
  current project membership, filters before fan-out, and bounds session,
  subscription, channel, and transport buffers. Drops and authorization failures
  are exposed through low-cardinality Micrometer metrics.
- Platform metrics now cover the documented HTTP, ingestion, queue, worker,
  persistence, Mongo command, alert, JVM, and process signals. The Prometheus
  path has an explicit reverse-proxy allowlist starting point under `ops/nginx`.
- An earlier default multi-context run exposed an order/isolation-sensitive
  API-key failure. The controlled backend application suite now passes all 20
  tests with `./gradlew :test --no-parallel`; broader test-isolation hardening
  remains useful before CI parallelization.
