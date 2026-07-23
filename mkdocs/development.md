# Development

## Prerequisites

- JDK 21 (Temurin recommended)
- Node.js 20+
- Docker (for Testcontainers)

## Clone and Build

```bash
git clone https://github.com/WolfTasks/TaskWolf.git
cd TaskWolf
```

### Backend

```bash
cd backend
./gradlew bootRun
```

The backend starts on `http://localhost:8080` with the `dev` profile (H2 in-memory database, Swagger UI enabled).

### Frontend

```bash
cd frontend
npm install
npm run dev
```

The frontend dev server starts on `http://localhost:5173` and proxies API calls to `localhost:8080`.

## Running Tests

### Backend

```bash
cd backend
./gradlew test
```

Tests use H2 for unit tests and Testcontainers (PostgreSQL) for integration tests.

**Windows note:** Docker Desktop must be running with TCP socket enabled (`tcp://localhost:2375`).

### Frontend

```bash
cd frontend
npm run build   # TypeScript + Vite build check
```

## Database Migrations

Flyway migrations live in `backend/src/main/resources/db/migration/`. The current version is **V21**. New migrations must follow the naming convention `V{n}__{description}.sql`.

## Project Structure

```
TaskWolf/
├── backend/          # Kotlin / Spring Boot
│   └── src/
│       ├── main/kotlin/com/taskowolf/
│       │   ├── auth/         # JWT, OAuth2, SSO, API keys
│       │   ├── audit/        # Audit logs
│       │   ├── organizations/# Multi-tenancy
│       │   ├── servicedesk/  # SLA, incidents
│       │   ├── projects/     # Projects, workflows
│       │   ├── issues/       # Issue lifecycle
│       │   ├── sprints/      # Sprint management
│       │   ├── boards/       # Kanban/Scrum
│       │   ├── automation/   # No-code rules
│       │   ├── reports/      # Velocity, cycle time
│       │   └── integrations/ # Webhooks, GitHub, GitLab
│       └── resources/
│           └── db/migration/ # Flyway SQL migrations V1–V21
└── frontend/         # React 19 / TypeScript / Vite
```

## Architecture

TaskWolf is a **modular monolith**. Modules communicate via Spring `ApplicationEvent`s — no direct service-to-service calls across module boundaries. Each module owns its domain, application, infrastructure, and API layers.
