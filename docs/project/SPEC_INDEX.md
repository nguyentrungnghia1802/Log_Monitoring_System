# Log Monitoring System — Specification Index

Read in this order:

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

## Implementation rule

Before coding a task, read:

- `00_PROJECT_CONTEXT.md`
- `01_PRODUCT_REQUIREMENTS.md`
- `02_SYSTEM_ARCHITECTURE.md`
- the task-specific canonical document;
- `09_ROADMAP_AND_DECISIONS.md` for architecture changes.

The baseline intentionally starts with:

```text
Java 21
Spring Boot
MongoDB
React
bounded in-memory ingestion queue
batch persistence workers
REST + WebSocket/STOMP
```

Do not introduce Kafka, Redis, Kubernetes, microservices, or another primary datastore without an explicit superseding ADR based on a measured need.
