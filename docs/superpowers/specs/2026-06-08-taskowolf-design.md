# TaskWolf — Design Spec

**Date:** 2026-06-08
**Status:** Approved

## Overview

TaskWolf ist ein Open-Source, self-hosted Projektmanagement-Tool im Stile von Jira. Es wird als einzelnes Docker-Image ausgeliefert, unterstützt H2 embedded für Entwicklung und PostgreSQL für Produktion, und ist von Anfang an so designed, dass es später horizontal auf mehrere Nodes skaliert werden kann (Projekt-Sharding).

## Tech Stack

| Layer | Technologie |
|---|---|
| Backend | Kotlin 2.x + Spring Boot 3.x |
| Frontend | React 19 + TypeScript + Vite |
| UI | shadcn/ui + Tailwind CSS 4 |
| DB (Produktion) | PostgreSQL 16 |
| DB (Entwicklung) | H2 embedded |
| DB-Migration | Flyway |
| Real-time | Spring WebSocket + STOMP |
| Auth | JWT (stateless) + OAuth2 (GitHub, Google) |
| API-Docs | OpenAPI 3 via springdoc |
| Build | Gradle mit Kotlin DSL |
| Tests | JUnit 5 + Testcontainers |
| Frontend State | React Query (server) + Zustand (UI) |
| Charts | Recharts |
| Drag & Drop | @dnd-kit |
| Deployment | Docker + docker-compose |

## Architektur

**Modularer Monolith mit internem Event Bus.**

Alle Module laufen in einem einzigen Spring Boot Prozess und einem Docker-Container. Module kommunizieren ausschließlich über Spring `ApplicationEvent`s — keine direkten Service-Aufrufe zwischen Modulen. Das hält Abhängigkeiten sauber und ermöglicht spätere Extraktion einzelner Module ohne Rewrite.

```
Browser
  ↕ REST + WebSocket (STOMP)
Spring Boot App
  ├── Module (je domain/ application/ infrastructure/ api/)
  │     issues, projects, workflows, sprints, boards,
  │     comments, notifications, attachments, automation,
  │     reports, integrations, auth
  └── core (Event Bus, Pagination, Error Handling)
  ↕
PostgreSQL / H2
```

### Skalierungspfad (Phase 6+)

Da die API stateless ist (JWT) und jede URL den Projekt-Key enthält (`/api/v1/projects/{key}/...`), kann später ein Routing Layer (nginx/Traefik) vorgeschaltet werden, der anhand des Projekt-Keys auf verschiedene Node-Instanzen verteilt. Eine kleine Registry-DB speichert die Zuordnung `projectKey → nodeId`. Das Feld `Project.nodeId` ist bereits im Datenmodell reserviert.

## Module

Jedes Modul ist nach Hexagonal Architecture aufgebaut:

```
<modul>/
  domain/        # Entities, Value Objects, Domain Events
  application/   # Services, Use Cases
  infrastructure/ # Repositories (JPA), externe Adapter
  api/           # REST Controller
```

| Modul | Verantwortung | Abhängigkeiten |
|---|---|---|
| `core` | Event Bus, Basis-Klassen, Fehlerbehandlung, Pagination | — |
| `auth` | JWT, OAuth2, Rollen, API Keys | core |
| `projects` | Projekte, Mitglieder, Projekt-Settings | core, auth |
| `issues` | Issues, Bugs, Stories, Epics, Subtasks, Prioritäten, Labels, Verlinkungen | core, projects, workflows |
| `workflows` | Status-Definitionen, Übergänge, Bedingungen | core, projects |
| `sprints` | Sprint-Lifecycle (PLANNED→ACTIVE→CLOSED), Backlog | core, projects, issues |
| `boards` | Kanban/Scrum Board Views, Drag-and-Drop Logik | issues, sprints, workflows |
| `comments` | Kommentare, @Mentions, Aktivitäts-Feed, Markdown | core, issues |
| `notifications` | In-App, E-Mail, WebSocket Push — reagiert auf Domain Events | core (Event Bus) |
| `attachments` | Datei-Upload, lokaler Storage (Phase 1), S3-kompatibel (später) | core |
| `automation` | When/If/Then Regeln, No-Code, reagiert auf Domain Events | core (Event Bus), issues |
| `reports` | Burndown, Velocity, Cycle Time, Custom Dashboards | issues, sprints |
| `integrations` | Webhook-System, GitHub/GitLab Callbacks, Slack | core (Event Bus), issues |

## Core Data Model

### Entitäten (vereinfacht)

**User** — `id`, `email`, `displayName`, `avatarUrl`, `passwordHash?`, `oauthProvider?`, `systemRole`, `createdAt`

**Project** — `id`, `key` (unique, z.B. `WOLF`), `name`, `description?`, `ownerId→User`, `workflowId→Workflow`, `archived`, `nodeId?`

**Issue** — `id`, `key` (z.B. `WOLF-42`), `title`, `description?`, `type` (EPIC/STORY/BUG/TASK/SUBTASK), `priority` (CRITICAL/HIGH/MEDIUM/LOW), `storyPoints?`, `statusId→Status`, `projectId→Project`, `assigneeId→User?`, `reporterId→User`, `sprintId→Sprint?`, `parentId→Issue?`, `dueDate?`, `createdAt`

**Workflow** — `id`, `name`, `projectId→Project` → hat viele `Status` und `WorkflowTransition`

**Status** — `id`, `name`, `category` (TODO/IN_PROGRESS/DONE), `color`, `position`, `workflowId→Workflow`

**Sprint** — `id`, `name`, `goal?`, `status` (PLANNED/ACTIVE/CLOSED), `startDate?`, `endDate?`, `projectId→Project`

**Comment** — `id`, `body` (Markdown), `issueId→Issue`, `authorId→User`, `createdAt`, `editedAt?`

**IssueLink** — `fromIssueId→Issue`, `toIssueId→Issue`, `type` (BLOCKS/RELATES_TO/DUPLICATES/CLONED_BY)

### Wichtige Invarianten

- `Issue.key` = `Project.key` + laufende Nummer (auto-increment pro Projekt)
- `Issue.parentId` ist eine Self-Reference (Epic → Story → Subtask-Hierarchie)
- `Project.nodeId` ist `null` in Phase 1, wird in Phase 6+ für Sharding befüllt
- `Status.category` bestimmt die Board-Spalten-Logik (TODO-Spalte, IN_PROGRESS-Spalte, DONE-Spalte)

## API Design

**Basis-URL:** `/api/v1`

**Prinzipien:**
- Stateless — JWT in `Authorization: Bearer <token>`, kein Server-Side Session State
- Projekt-Key immer in der URL — ermöglicht Multi-Node-Routing
- REST für CRUD, WebSocket (STOMP) für Real-time
- OpenAPI 3 Dokumentation automatisch generiert

### Key Endpoints

```
POST   /auth/login
POST   /auth/refresh
GET    /auth/oauth/{provider}

GET    /projects
POST   /projects
GET    /projects/{key}
PUT    /projects/{key}
GET    /projects/{key}/members

GET    /projects/{key}/issues
POST   /projects/{key}/issues
GET    /projects/{key}/issues/{id}
PATCH  /projects/{key}/issues/{id}
DELETE /projects/{key}/issues/{id}
POST   /projects/{key}/issues/{id}/comments
POST   /projects/{key}/issues/{id}/links

GET    /projects/{key}/board
PATCH  /projects/{key}/board/move

GET    /projects/{key}/sprints
POST   /projects/{key}/sprints
POST   /projects/{key}/sprints/{id}/start
POST   /projects/{key}/sprints/{id}/complete

GET    /projects/{key}/reports/burndown
GET    /projects/{key}/reports/velocity
```

### WebSocket

```
WS     /ws  (STOMP)
SUB    /topic/projects/{key}        # Board-Updates, Issue-Änderungen
SUB    /user/queue/notifications    # Persönliche Notifications
```

### Auth-Flow

1. `POST /auth/login` → `accessToken` (15 min) + `refreshToken` (7 Tage)
2. Jeder Request: `Authorization: Bearer <accessToken>`
3. Abgelaufen → `POST /auth/refresh` → neues Token-Paar
4. OAuth2 (GitHub/Google) → Callback → JWT → gleicher Flow ab Schritt 2

## Frontend-Struktur

**React 19 SPA, gebaut mit Vite, serviert durch nginx im Prod-Container.**

```
frontend/src/
  app/           # router.tsx, store.ts
  layouts/       # AppLayout.tsx (Sidebar + Nav), AuthLayout.tsx
  pages/
    auth/        # LoginPage
    dashboard/   # Übersicht aller Projekte
    projects/    # ProjectList, ProjectCreate
    board/       # BoardPage (Kanban/Scrum)
    backlog/     # BacklogPage
    sprints/     # SprintsPage
    issues/      # IssueDetail
    reports/     # ReportsPage (Burndown, Velocity)
    settings/    # ProjectSettings (Workflow, Members)
  components/
    board/       # Column, Card, Swimlane
    issue/       # IssueCard, IssueForm, IssueDetail
    sprint/      # SprintHeader, SprintPlanning
    ui/          # shadcn Komponenten
  hooks/
    useIssues.ts
    useWebSocket.ts
  api/
    client.ts    # axios instance mit JWT interceptor
  lib/
    utils.ts
```

**Routing:**

```
/login                    → LoginPage
/                         → Dashboard (🔒 auth)
/projects                 → ProjectList (🔒 auth)
/p/:key                   → ProjectLayout (🔒 member)
/p/:key/board             → BoardPage
/p/:key/backlog           → BacklogPage
/p/:key/issues/:id        → IssueDetail
/p/:key/sprints           → SprintsPage
/p/:key/reports           → ReportsPage
/p/:key/settings          → ProjectSettings (🔑 admin)
```

## Deployment

### Repository-Struktur

```
taskowolf/
  backend/          # Spring Boot (Kotlin)
    src/
    build.gradle.kts
    Dockerfile
  frontend/         # React + Vite
    src/
    package.json
    Dockerfile
  docker/
    nginx.conf
    init.sql
  docker-compose.yml          # Produktion
  docker-compose.dev.yml      # Entwicklung (H2, kein nginx)
  .env.example
  docs/
```

### Modi

**Entwicklung** (`docker-compose.dev.yml` oder `gradle bootRun`):
- Spring Boot mit H2 embedded (`application-dev.yml`)
- Kein PostgreSQL-Container nötig
- H2 Console unter `/h2-console`
- Frontend via `npm run dev` mit HMR

**Produktion** (`docker compose up -d`):
- nginx (Port 80/443) → Static Frontend + API Proxy zu Spring Boot
- Spring Boot (Port 8080, intern)
- PostgreSQL 16 (Port 5432, intern)
- Flyway-Migrationen laufen automatisch beim Start

### Konfiguration (.env)

```env
TW_DB_URL=jdbc:postgresql://db:5432/taskowolf
TW_DB_USER=taskowolf
TW_DB_PASS=changeme
TW_JWT_SECRET=your-256-bit-secret
TW_OAUTH_GITHUB_ID=...
TW_OAUTH_GITHUB_SECRET=...
TW_BASE_URL=https://taskowolf.example.com
TW_PROFILE=prod
TW_STORAGE_PATH=/data/attachments
# Optional: externe DB statt eingebetteter PostgreSQL
TW_EXTERNAL_DB_URL=jdbc:postgresql://external-host:5432/taskowolf
```

## Implementierungs-Phasen

| Phase | Inhalt | Voraussetzung |
|---|---|---|
| **1** | Core Foundation — Projekte, Issues, Workflows, Auth, Docker-Setup | — |
| **2** | Agile Boards — Kanban, Scrum, Sprints, Backlog, Story Points, Burndown | Phase 1 |
| **3** | Collaboration — Kommentare, @Mentions, Notifications, Attachments | Phase 1 |
| **4** | Workflows & Automation — Custom Workflows, No-Code Regeln, Trigger | Phase 1, 2 |
| **5** | Dashboards & Reports — Velocity, Cycle Time, Custom Dashboards | Phase 1, 2 |
| **6** | Developer-Tools & Integrationen — GitHub/GitLab, Webhooks, CI/CD | Phase 1 |
| **7** | Enterprise — SSO, Audit Logs, Multi-Tenancy, Service Management | Phase 1–4 |

Jede Phase ist eine eigenständige Implementierungs-Session mit eigenem Plan.

## Nicht im Scope (bewusste Entscheidungen)

- **Kafka / RabbitMQ** — Spring ApplicationEvents reichen für Phase 1–5
- **Redis** — erst ab Phase 6+ für Cross-Node-Caching nachrüstbar
- **Kubernetes** — docker-compose ist ausreichend für self-hosted OSS
- **3.000+ Integrationen** — Phase 6 liefert ein Webhook-System; Community kann weitere Integrationen beisteuern
