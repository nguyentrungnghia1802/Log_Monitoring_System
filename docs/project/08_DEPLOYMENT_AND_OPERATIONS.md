# Deployment and Operations

## 1. Environment model

| Environment | Purpose |
| --- | --- |
| Local | Development, fault injection, disposable data |
| Test/CI | Automated tests with isolated MongoDB |
| Staging | Production-like performance and failure testing |
| Production | Real monitoring workloads |

Staging and production must not share JWT secrets, API keys, notification credentials, or MongoDB data.

---

## 2. V1 deployment model

Initial production-like topology:

```text
Internet
   |
HTTPS reverse proxy
   |
   +--> React static web
   |
   +--> Spring Boot API
            |
            v
         MongoDB
```

For learning/development, Docker Compose is sufficient.

For real external operation, managed TLS, secrets, backups, resource limits, and MongoDB durability must be configured deliberately.

---

## 3. Configuration

Backend secrets:

```text
JWT_SECRET
MONGODB credentials
Telegram/Slack tokens/webhook credentials
```

Non-secret tuning:

```text
INGESTION_QUEUE_CAPACITY
INGESTION_WORKER_COUNT
INGESTION_BATCH_MAX_SIZE
INGESTION_BATCH_MAX_WAIT_MS
INGESTION_ENQUEUE_TIMEOUT_MS
INGESTION_MAX_HTTP_BODY_BYTES
INGESTION_MAX_MESSAGE_LENGTH
INGESTION_MAX_STACK_TRACE_LENGTH
INGESTION_MAX_CONTEXT_BYTES
INGESTION_MAX_CONTEXT_KEYS
INGESTION_MAX_CONTEXT_DEPTH
LOG_REDACTION_ENABLED
LOG_REDACTION_REPLACEMENT
SEARCH_MAX_RANGE_DAYS
SEARCH_MAX_PAGE_SIZE
```

API keys issued to monitored applications are application credentials and must be managed like secrets.

### Ingestion privacy operations

Keep request-body limits and redaction enabled in every environment. Review the
configured `security.redaction.fields` list when a source application introduces
a new credential or personal-data field. Do not disable redaction to debug a
production incident; reproduce with synthetic values or inspect a restricted
source-side sample instead. Monitor `ingestion.rejected.validation` and
`ingestion.rejected.payload_too_large` as count-only signals. Project retention
must be shorter than the approved diagnostic need, and raw log access remains
project-scoped.

### Organization administration operations

Organization settings and membership changes are management configuration writes,
not ingestion events. Keep the organization-management endpoints behind HTTPS
and the JWT boundary, monitor `401`/`403`/`409 FINAL_ORGANIZATION_ADMIN` responses,
and verify audit records during access reviews. Passwords are hashed at creation;
operators must not request or log temporary passwords. Removing a user also
clears project memberships and disables the user record, while retaining the
non-sensitive identity row for audit/history purposes.

---

## 4. Health model

### Liveness

Process/JVM is running.

Must not become unhealthy merely because MongoDB is temporarily unavailable.

### Readiness

Instance is safe to receive traffic.

At minimum considers:

- MongoDB connectivity;
- application startup complete;
- shutdown not in progress.

For V1, readiness may remain true during temporary ingestion queue pressure because backpressure is an explicit endpoint behavior. Severe internal worker failure may mark readiness false according to an operational threshold.

---

## 5. Resource limits

Always define:

- JVM heap/container memory;
- CPU limits where appropriate;
- queue capacity;
- worker counts;
- HTTP max body;
- Mongo connection pool;
- WebSocket connection/session limits.

A bounded queue is useful only if total memory per event is also controlled.

---

## 6. Operational dashboards

The platform needs a **platform health dashboard** separate from monitored-project dashboards.

Key panels:

```text
HTTP request rate/latency/error
Ingestion accepted/sec
Ingestion rejected/sec
Queue depth / capacity %
Worker throughput
Mongo write p95
Mongo errors
Batch size
Heap / GC
Thread pool utilization
WebSocket sessions
Live-tail drops
Alerts triggered
Notification failures
```

---

## 7. Alerting on the monitoring platform

Examples:

- queue depth > 80% for 5 minutes;
- ingestion backpressure > 0 unexpectedly;
- Mongo write failures;
- readiness unavailable;
- heap > threshold;
- persistence throughput falls below ingestion rate;
- alert-delivery provider failures.

Avoid circular dependency where the only alert path for the monitoring platform is the same failing subsystem.

A secondary external health check is recommended.

---

## 8. Backup

### Configuration data

Back up regularly:

- organizations;
- users/memberships;
- projects;
- API-key metadata;
- alert rules;
- alert occurrences;
- audit history.

### Raw log events

Production policy must decide whether raw log events are backed up. TTL already defines them as temporary telemetry; backing them up may conflict with retention expectations if backups outlive active storage.

Retention and backup policies must be aligned.

---

## 9. Restore

After restore verify:

1. users can authenticate;
2. project membership is intact;
3. API keys that should remain active still validate;
4. alert rules load;
5. Mongo indexes and TTL exist;
6. ingestion works;
7. worker drains;
8. analytics works;
9. notification configuration references remain valid.

A restore drill should be tested, not assumed.

---

## 10. Graceful deployment

For a single-instance V1:

1. mark old instance not ready;
2. stop new traffic;
3. graceful drain/flush bounded queue;
4. stop process;
5. deploy new version;
6. verify Mongo/index compatibility;
7. start;
8. wait for readiness;
9. smoke ingestion and search.

A rolling multi-instance deployment is a later architecture step because process-memory queues complicate zero-loss rolling behavior.

---

## 11. Incident runbook — ingestion backpressure

Symptoms:

- `503 INGESTION_BACKPRESSURE`;
- queue depth near capacity;
- producer retries.

Check:

1. MongoDB availability/latency;
2. worker pool health;
3. persistence failure metrics;
4. batch throughput;
5. CPU/heap/GC;
6. recent deployment/config changes.

Do not simply increase queue capacity until the root bottleneck is measured.

Possible controls:

- restore MongoDB;
- fix worker failure;
- temporarily increase worker/batch capacity after validation;
- reduce producer verbosity/sampling;
- activate client local buffer;
- move toward durable broker when this becomes structural.

---

## 12. Incident runbook — MongoDB unavailable

1. confirm readiness failure;
2. inspect network/credentials/server/storage;
3. protect from restart loops;
4. observe queue depth;
5. notify application owners if ingestion rejection begins;
6. restore dependency;
7. watch drain rate;
8. inspect terminal failed batches.

Because V1 is non-durable before MongoDB, communicate potential loss window honestly.

---

## 13. Incident runbook — memory pressure

Check:

- average event size;
- queue depth;
- WebSocket client buffers;
- batch objects;
- stack trace/context limits;
- heap histogram/GC.

Controls:

- enforce payload limits;
- reduce queue capacity if events are larger than expected;
- cap live-tail buffers;
- drop/suppress excessive DEBUG traffic at producers;
- fix object retention leaks.

---

## 14. Incident runbook — alert storm

1. inspect triggered rule;
2. confirm threshold/window/cooldown;
3. temporarily disable rule if operationally necessary;
4. inspect duplicate occurrence logic;
5. confirm notification retries are not creating new occurrences;
6. adjust rule based on evidence.

All changes are audited.

---

## 15. Security operations

- rotate exposed API keys immediately;
- rotate JWT/provider credentials if leaked;
- never paste secrets into incident tickets;
- restrict actuator/prometheus at infrastructure boundary;
- keep MongoDB inaccessible from public internet;
- enable TLS for external traffic;
- retain audit records longer than raw debug logs.

---

## 16. Capacity planning

Track over time:

```text
events/sec
average event bytes
peak queue depth
Mongo writes/sec
storage bytes/day
retention days
index size
dashboard query load
concurrent live-tail clients
alerts/day
```

Approximate active raw volume:

```text
events/day × average stored bytes × effective retention
```

Then include index overhead and replica/backup policy separately.

---

## 17. Scaling path

Do not jump directly to Kubernetes.

Suggested order:

1. optimize event size;
2. batch writes;
3. tune indexes;
4. measure worker/queue;
5. scale Mongo appropriately;
6. introduce durable broker if needed;
7. separate ingestion/consumer roles if needed;
8. horizontally scale;
9. only then choose orchestration based on operational need.

---

## 18. Production readiness checklist

- HTTPS.
- Secrets outside Git.
- Strong admin password/session policy.
- API-key hashing/rotation.
- Mongo authentication and network restriction.
- Required indexes created.
- TTL verified.
- Health/readiness verified.
- Queue/backpressure metrics visible.
- Load tests passed against declared SLO/hardware.
- Backup/restore tested.
- Alert channel tested.
- Graceful shutdown tested.
- Incident runbooks exercised.
- Retention/privacy policy approved.
- No claim of durable ingestion while V1 still uses process memory.
