# TaskWolf

**Open-source, self-hosted project management — built for teams that want full control.**

TaskWolf is a Jira-style issue tracker and agile board you run on your own infrastructure. Single Docker image, zero vendor lock-in, PostgreSQL in production and H2 in development.

[![Backend](https://img.shields.io/docker/v/taskwolf/taskowolf-backend?label=backend&logo=docker)](https://hub.docker.com/r/taskwolf/taskowolf-backend)
[![Frontend](https://img.shields.io/docker/v/taskwolf/taskowolf-frontend?label=frontend&logo=docker)](https://hub.docker.com/r/taskwolf/taskowolf-frontend)

---

## Features

### Core (Phase 1)
- Projects with custom keys (e.g. `WOLF-42`)
- Issues: Epics, Stories, Bugs, Tasks, Subtasks with priority, labels, story points, due dates, and parent–child linking
- Configurable workflows with custom statuses and transitions
- JWT auth + OAuth2 (GitHub, Google) + API keys
- Role-based access: System Admin, Project Admin, Member
- OpenAPI 3 docs (`/swagger-ui.html`)

### Agile Boards (Phase 2)
- Kanban and Scrum board views with drag-and-drop
- Sprint lifecycle: Planned → Active → Closed
- Backlog management and sprint planning
- Burndown chart

### Collaboration (Phase 3)
- Markdown comments with `@mentions`
- Real-time notifications (in-app + WebSocket push)
- File attachments
- Activity feed per issue

### Workflows & Automation (Phase 4)
- Visual workflow canvas editor
- No-code automation rules: When / If / Then
- Triggers: issue created, status changed, field updated, sprint events
- 6 action types: assign, set status, set field, add comment, create issue, send notification

### Dashboards & Reports (Phase 5)
- Burndown chart (sprint-level)
- Velocity chart (team trend)
- Cycle time analysis
- Custom dashboards with drag-and-drop widget layout
- Widget types: metrics, charts, issue tables, activity feeds

### Developer Tools & Integrations (Phase 6)
- Outgoing webhooks with HMAC signing and automatic retry
- GitHub and GitLab incoming webhooks → auto-link commits and PRs to issues
- API key authentication for CI/CD pipelines
- SSRF-safe HTTP delivery

### Enterprise (Phase 7)
- **Audit Logs** — SECURITY / WRITE / ALL levels, admin and project-scoped views, CSV + JSON export
- **SSO via OIDC** — dynamic provider registration, AES-GCM encrypted client secrets, auto-provisioning
- **Organizations** — multi-tenancy with org switcher, JWT org context, member management
- **Service Management** — service desks, SLA policies with escalation rules, incident tracking with postmortem, email-to-ticket ingestion (IMAP)

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Kotlin 2.x + Spring Boot 3.x |
| Frontend | React 19 + TypeScript + Vite |
| UI | shadcn/ui + Tailwind CSS 4 |
| Database (prod) | PostgreSQL 16 |
| Database (dev) | H2 embedded |
| Migrations | Flyway (V1–V21) |
| Real-time | Spring WebSocket + STOMP |
| Auth | JWT (stateless) + OAuth2 + OIDC |
| Build | Gradle with Kotlin DSL |
| Tests | JUnit 5 + MockK + Testcontainers |
| Frontend state | React Query + Zustand |
| Charts | Recharts |
| Drag & Drop | @dnd-kit |
| Deployment | Docker + docker-compose |

---

## Quick Start

**Requirements:** Docker and docker-compose.

```bash
git clone https://github.com/WolfTasks/TaskWolf.git
cd TaskWolf
cp .env.example .env
# Edit .env — set TW_JWT_SECRET at minimum
docker compose up -d
```

Open [http://localhost](http://localhost). The first registered user becomes System Admin.

---

## Configuration

Copy `.env.example` to `.env` and adjust:

```env
# Required
TW_JWT_SECRET=your-256-bit-secret-here

# Database (defaults to bundled PostgreSQL)
TW_DB_URL=jdbc:postgresql://db:5432/taskowolf
TW_DB_USER=taskowolf
TW_DB_PASS=changeme

# App
TW_BASE_URL=https://taskowolf.example.com
TW_STORAGE_PATH=/data/attachments

# OAuth2 (optional)
TW_OAUTH_GITHUB_ID=...
TW_OAUTH_GITHUB_SECRET=...

# IMAP email ingestion (optional, for service desk)
TW_MAIL_IMAP_ENABLED=false
TW_MAIL_IMAP_HOST=imap.example.com
TW_MAIL_IMAP_USER=support@example.com
TW_MAIL_IMAP_PASS=changeme
```

---

## Development Setup

**Backend:**
```bash
cd backend
./gradlew bootRun
# Spring Boot starts on :8080 with H2 embedded
# H2 console: http://localhost:8080/h2-console
```

**Frontend:**
```bash
cd frontend
npm install
npm run dev
# Vite dev server on :5173 with HMR
```

**Tests:**
```bash
cd backend
./gradlew test
# Integration tests use Testcontainers (Docker required)
```

---

## Architecture

**Modular Monolith with internal Spring ApplicationEvent bus.**

All modules run in a single Spring Boot process and Docker container. Modules communicate exclusively through `ApplicationEvent`s — no direct cross-module service calls. This keeps dependencies clean and allows extracting individual modules without a rewrite.

```
Browser
  ↕ REST + WebSocket (STOMP)
Spring Boot App (port 8080)
  ├── core          — Event bus, base classes, error handling, pagination
  ├── auth          — JWT, OAuth2, OIDC/SSO, roles, API keys
  ├── projects      — Projects, members, settings
  ├── issues        — Issues, links, priorities, labels
  ├── workflows     — Status definitions, transitions, guards
  ├── sprints       — Sprint lifecycle, backlog
  ├── boards        — Kanban/Scrum views, drag-and-drop logic
  ├── comments      — Comments, @mentions, activity feed, Markdown
  ├── notifications — In-app, email, WebSocket push
  ├── attachments   — File upload, local storage
  ├── automation    — When/If/Then rules, no-code engine
  ├── reports       — Burndown, velocity, cycle time, dashboards
  ├── integrations  — Webhooks, GitHub/GitLab callbacks
  ├── audit         — Audit log, configurable levels
  ├── organizations — Multi-tenancy, org context, member management
  └── servicedesk   — Service desks, SLA policies, incidents, email ingestion
  ↕
PostgreSQL 16 / H2
```

Each module follows **Hexagonal Architecture**:
```
<module>/
  domain/          # Entities, value objects, domain events
  application/     # Services, use cases
  infrastructure/  # Repositories (JPA), external adapters
  api/             # REST controllers, DTOs
```

**Scaling path:** The API is stateless (JWT) and every URL contains the project key (`/api/v1/projects/{key}/...`). A future routing layer (nginx/Traefik) can distribute by project key to separate node instances. `Project.nodeId` is already reserved in the data model.

---

## API

**Base URL:** `/api/v1`  
**Auth:** `Authorization: Bearer <accessToken>`  
**Docs:** `/swagger-ui.html` (development only)

Key endpoints:

```
POST   /auth/login                              # → accessToken + refreshToken
POST   /auth/refresh
GET    /auth/oauth/{provider}                   # GitHub, Google
POST   /auth/switch-org/{orgId}                 # Multi-tenancy org switch

GET    /projects                                # List projects
POST   /projects/{key}/issues                   # Create issue
PATCH  /projects/{key}/issues/{id}              # Update issue
POST   /projects/{key}/sprints/{id}/start       # Start sprint
GET    /projects/{key}/reports/burndown

GET    /admin/audit                             # Audit log (ADMIN)
GET    /admin/sso                               # SSO config (ADMIN)
GET    /organizations                           # Org management (ADMIN)
POST   /projects/{key}/service-desk/enable      # Enable service desk
POST   /projects/{key}/service-desk/tickets     # Submit ticket (public)
POST   /projects/{key}/incidents                # Declare incident
```

**WebSocket:**
```
WS  /ws  (STOMP)
SUB /topic/projects/{key}         # Board updates, issue changes
SUB /user/queue/notifications     # Personal notifications
```

---

## Roadmap

### Phase 8 — Release v1.0 *(planned)*

- Changelog and release notes
- User-facing documentation site
- Docker Hub publish
- Hardening of known items:
  - OIDC endpoint discovery (currently Keycloak-style paths only)
  - Organization membership guard on read endpoints
  - CSV export escaping (audit log)

---

## Project History

| Phase | Shipped | Description |
|---|---|---|
| 1 | 2026-06-08 | Core Foundation — projects, issues, workflows, auth, Docker |
| 2 | 2026-06-09 | Agile Boards — Kanban, Scrum, sprints, burndown |
| 3 | 2026-06-11 | Collaboration — comments, @mentions, notifications, attachments |
| 4 | 2026-06-14 | Workflows & Automation — custom workflows, no-code rules |
| 5 | 2026-06-21 | Dashboards & Reports — velocity, cycle time, custom dashboards |
| 6 | 2026-06-22 | Developer Tools & Integrations — webhooks, GitHub/GitLab |
| 7 | 2026-06-23 | Enterprise — SSO/OIDC, audit logs, organizations, service desk |

---

## License

MIT
