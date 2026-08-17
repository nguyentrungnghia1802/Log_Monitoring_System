# Centralized Log Monitoring System

This repository contains a working centralized log monitoring platform built as a modular monolith using Java 21, Spring Boot 3.x, MongoDB, and a React + Vite frontend.

## Overview

The current baseline includes the repository foundation plus:

- Spring Boot backend with ingestion, persistence, search, analytics, alerting, management security, Actuator, WebSocket, and MongoDB;
- React management console with login/session restoration, project and organization administration, Log Explorer, analytics, Live Tail, alerts, and API-key lifecycle;
- organization/user management and organization-scoped project administration;
- project settings, retention configuration, activity/service summaries, soft deactivation, and audited management APIs;
- API-key inventory and safe create/rotate/revoke management with one-time secret display;
- email/password management login with short access JWTs, rotating HttpOnly refresh sessions, protected routes, and logout;
- Docker Compose configuration for local MongoDB;
- environment and profile configuration;
- smoke/integration tests and health endpoints.

## Requirements

- JDK 21
- Docker Desktop / Docker Compose
- Node.js 20+
- MongoDB (provided by Docker Compose for local development)

## Quick start

### 1. Clone and configure environment

Copy the environment template:

```bash
cp .env.example .env
```

For a fresh local database, set `LOCAL_BOOTSTRAP_ADMIN_ENABLED=true` and replace
`LOCAL_BOOTSTRAP_ADMIN_EMAIL` and `LOCAL_BOOTSTRAP_ADMIN_PASSWORD` (12+
characters). Export the `.env` values into your shell before starting the
backend; Docker Compose reads `.env` automatically, while Gradle does not.

### 2. Start MongoDB

```bash
docker compose up -d mongodb
```

### 3. Run backend

```bash
cd backend
./gradlew bootRun
```

### 4. Run frontend

```bash
cd frontend
npm ci
npm run dev
```

### 5. Run tests

Backend:

```bash
cd backend
MONGODB_URI='mongodb://root:example_password@localhost:27017/log_monitor_test?authSource=admin' ./gradlew :test
```

Frontend:

```bash
cd frontend
npm run test
npm run lint
npm run typecheck
npm run build
```

## Key endpoints

- Backend health: http://localhost:8080/actuator/health
- Readiness: http://localhost:8080/actuator/health/readiness
- Ingestion status: http://localhost:8080/api/v1/system/ingestion-status
- Management UI: http://localhost:5173/login

## Repository structure

- backend/: Spring Boot application
- frontend/: React + Vite application
- docs/project/: canonical product/architecture/database/API docs
- compose.yaml: local MongoDB service
# Log_Monitoring_System
