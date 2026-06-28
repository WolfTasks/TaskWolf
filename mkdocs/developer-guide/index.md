# Developer Guide

AI-readable reference for integrating new features into TaskWolf. Read [Conventions](conventions.md) first, then the module page for the area you are modifying.

## Architecture

```
Browser
  ↕ REST (JSON) + WebSocket (STOMP)
Spring Boot App (single process, single Docker container)
  ├── 16 modules — each: domain/ application/ infrastructure/ api/
  ├── core — event bus, base classes, error handling, WebSocket config
  └── Inter-module: ApplicationEventPublisher ONLY (no cross-module @Service injection)
  ↕
PostgreSQL 16 (prod) / H2 embedded (dev/test)
```

All API paths: `/api/v1/projects/{key}/...` (stateless JWT; project key enables future nginx sharding)

## Module Map

| Module | Owns |
|--------|------|
| `core` | AuditableEntity, GlobalExceptionHandler, WebSocketConfig, DomainEventPublisher |
| `auth` | Users, roles, JWT, OAuth2 (GitHub/Google), SSO/OIDC, API keys, refresh tokens |
| `projects` | Projects, project membership, project settings |
| `issues` | Issues, issue links, issue types, priorities |
| `workflows` | Workflow status definitions, transitions, guards |
| `sprints` | Sprint lifecycle (PLANNED→ACTIVE→CLOSED), backlog |
| `boards` | Kanban/Scrum board views, drag-and-drop column logic |
| `comments` | Comments, @mention parsing, activity feed |
| `notifications` | In-app, email, WebSocket push notifications |
| `attachments` | File upload, local filesystem storage |
| `automation` | When/If/Then no-code rules, AutomationEngine |
| `reports` | Burndown, velocity, cycle time, custom dashboards |
| `integrations` | API keys auth filter, webhooks, GitHub/GitLab event ingestion |
| `audit` | Audit event log, AuditService |
| `organizations` | Multi-tenancy, OrganizationContextHolder, org membership |
| `servicedesk` | SLA monitoring, service queues, email ingestion |

## How To Use This Guide

1. Read the [AI Implementation Guide](ai-guide.md) — mandatory checklist and canonical patterns before writing any code.
2. Read [Conventions](conventions.md) — covers patterns all modules share.
3. Open the module page for the area you are touching.
4. Check **Extension Points** for step-by-step instructions on common tasks.
5. Check **Common Pitfalls** before writing code.
