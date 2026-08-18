# Log Monitoring Java SDK

The source SDK is a dependency-free Java 21 client for
`POST /api/v1/ingest/logs/batch`. It owns bounded local batching, HTTP retry,
and shutdown flushing so an application does not need to reimplement those
policies.

## Minimal integration

```java
List<LogSubmissionResult> results = new CopyOnWriteArrayList<>();
LogMonitoringClientConfig config = new LogMonitoringClientConfig()
    .setEndpoint("https://logs.example.com")
    .setApiKey(System.getenv("LOG_MONITORING_API_KEY"))
    .setService("checkout-service")
    .setEnvironment("production")
    .setResultListener(results::add);

try (LogMonitoringClient client = new LogMonitoringClient(config)) {
    client.log(
        "ERROR",
        "CHECKOUT_FAILED",
        "Checkout request failed",
        traceId,
        requestId,
        null,
        Map.of("orderId", orderId)
    );
    client.error("PAYMENT_FAILED", "Payment provider failed", exception,
        traceId, requestId, Map.of("orderId", orderId), Map.of("provider", "stripe"));
    client.flush();
}
```

`log`/`error` return `true` only when the event enters the SDK's bounded
local queue. The result listener first receives `QUEUED_LOCALLY`, then a final
per-event outcome such as `ACCEPTED_BY_SERVER_ADMISSION`, `REJECTED_SERVER`,
`RETRY_EXHAUSTED`, or `DROPPED_BY_POLICY`. A `202` response means admission to
the platform's bounded process-memory queue; it does not claim durable MongoDB
persistence.

## Safety and retry policy

- The queue, batch size, event message, exception, context, and tags are all
  bounded by `LogMonitoringClientConfig`.
- Missing trace/request IDs are generated. Caller-provided IDs are preserved.
- `401`, `403`, validation errors, and other non-retryable client responses are
  reported without retry. `408`, `425`, `429`, and `5xx` responses, plus
  transport timeouts, use bounded exponential backoff with jitter.
- Integer or RFC 1123 `Retry-After` values are honored up to the configured
  cap. Duplicate delivery remains possible because the server intentionally
  has at-least-once semantics.
- `flush()` and `close()` use a bounded timeout. Events left after that policy
  expires receive `DROPPED_BY_POLICY`; no payload or API key is logged by the
  client.

Run the SDK tests from `backend`:

```powershell
./gradlew :sdk:log-monitoring-java-sdk:test --no-parallel
```
