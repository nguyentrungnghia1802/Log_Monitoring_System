# Centralized Log Monitoring System

This repository contains the initial foundation for a centralized log monitoring platform built as a modular monolith using Java 21, Spring Boot 3.x, MongoDB, and a React + Vite frontend.

## Overview

Phase 0 focuses on repository and project foundation:

- backend skeleton with Spring Boot, security, validation, Actuator, WebSocket, and MongoDB support;
- frontend skeleton with React, TypeScript, Vite, TanStack Query, Router, and Tailwind CSS;
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
./gradlew test
```

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
