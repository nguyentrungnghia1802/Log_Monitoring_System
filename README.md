# Centralized Log Monitoring System

This repository contains the initial foundation for a centralized log monitoring platform built as a modular monolith using Java 21, Spring Boot 3.x, MongoDB, and a React + Vite frontend.

## Overview

The current baseline includes the repository foundation plus:

- backend skeleton with Spring Boot, security, validation, Actuator, WebSocket, and MongoDB support;
- frontend skeleton with React, TypeScript, Vite, TanStack Query, Router, and Tailwind CSS;
- organization/user management and organization-scoped project administration;
- project settings, retention configuration, activity/service summaries, soft deactivation, and audited management APIs;
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

Adjust values as needed before running the stack.

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
npm install
npm run dev
```

### 5. Run tests

Backend:

```bash
cd backend
./gradlew test -x :sdk:log-monitoring-java-sdk:test
```

On the current Windows development workspace, the SDK test task can be
blocked by a persistent lock on its generated test-results output file; the
application test suite is run with that one task excluded until the lock is
resolved without deleting the file.

Frontend:

```bash
cd frontend
npm run test
npm run typecheck
npm run build
```

## Key endpoints

- Backend health: http://localhost:8080/actuator/health
- Readiness: http://localhost:8080/actuator/health/readiness
- Ingestion status: http://localhost:8080/api/v1/system/ingestion-status

## Repository structure

- backend/: Spring Boot application
- frontend/: React + Vite application
- docs/project/: canonical product/architecture/database/API docs
- compose.yaml: local MongoDB service
# Log_Monitoring_System
