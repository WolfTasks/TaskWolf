# Developer Wiki — Design Spec

**Date:** 2026-06-27  
**Status:** Approved  
**Audience:** AI coding assistants + human contributors

---

## Goal

Add a Developer Guide section to the existing MkDocs site that gives an AI or a new developer enough structured context to integrate a new feature into any module without reading source code first.

Primary reader: AI. Pages are dense, structured, and machine-readable — minimal prose, maximum precision.

---

## File Structure

```
mkdocs/developer-guide/
  index.md              ← entry point: architecture diagram, module map, how to use this guide
  conventions.md        ← cross-cutting: event bus, Flyway, security, testing, error handling

  backend/
    core.md
    auth.md
    projects.md
    issues.md
    workflows.md
    sprints.md
    boards.md
    comments.md
    notifications.md
    attachments.md
    automation.md
    reports.md
    integrations.md
    audit.md
    organizations.md
    servicedesk.md

  frontend/
    overview.md
    components.md
    hooks.md
    pages.md
```

22 pages total.

---

## MkDocs Nav Integration

Add a top-level `Developer Guide` section to `mkdocs.yml` nav, between `API Reference` and `Development`:

```yaml
  - Developer Guide:
      - Overview: developer-guide/index.md
      - Conventions: developer-guide/conventions.md
      - Backend:
          - core: developer-guide/backend/core.md
          - auth: developer-guide/backend/auth.md
          - projects: developer-guide/backend/projects.md
          - issues: developer-guide/backend/issues.md
          - workflows: developer-guide/backend/workflows.md
          - sprints: developer-guide/backend/sprints.md
          - boards: developer-guide/backend/boards.md
          - comments: developer-guide/backend/comments.md
          - notifications: developer-guide/backend/notifications.md
          - attachments: developer-guide/backend/attachments.md
          - automation: developer-guide/backend/automation.md
          - reports: developer-guide/backend/reports.md
          - integrations: developer-guide/backend/integrations.md
          - audit: developer-guide/backend/audit.md
          - organizations: developer-guide/backend/organizations.md
          - servicedesk: developer-guide/backend/servicedesk.md
      - Frontend:
          - Overview: developer-guide/frontend/overview.md
          - Components: developer-guide/frontend/components.md
          - Hooks: developer-guide/frontend/hooks.md
          - Pages: developer-guide/frontend/pages.md
```

No theme changes needed — Material theme handles nested nav automatically.

---

## Page Templates

### `index.md` — Developer Guide Overview

- Architecture diagram (text-based): browser → REST/WebSocket → Spring Boot modules → DB
- Module map: 16 backend modules listed with one-line purpose each
- Key cross-cutting facts: stateless JWT, project key in all URLs, ApplicationEvent bus for inter-module comms
- How to use this guide: link to `conventions.md` first, then the relevant module page

### `conventions.md` — Cross-Cutting Conventions

Sections:
1. **Module structure** — domain/ application/ infrastructure/ api/ sub-package rules
2. **Inter-module communication** — ApplicationEventPublisher only, no direct service injection across modules
3. **Flyway migrations** — current version (V22), naming convention, H2/PostgreSQL compatibility rules
4. **Security** — SecurityConfig permit-all vs authenticated paths, role annotations (@PreAuthorize), JWT claims
5. **Error handling** — GlobalExceptionHandler, standard error response shape, which exceptions map to which HTTP codes
6. **Testing** — JUnit 5 + MockK (not Mockito), Testcontainers for integration tests, where test files live
7. **API conventions** — `/api/v1/projects/{key}/...` URL structure, pagination shape, request/response naming

### Backend module pages (`backend/<module>.md`)

Each page follows this fixed template, in this order:

```
# Module: <name>

## Purpose
One sentence: what this module owns and is responsible for.

## Entities Owned
Table: entity name | key fields | notes

## DB Schema
Tables owned, key columns, FK relationships, relevant indexes.
Which Flyway migration version created each table.

## API Endpoints
| Method | Path | Auth | Description |
Auth values: PUBLIC / USER / ADMIN / API_KEY

## Events Emitted
For each event: class name, trigger condition, payload fields.

## Events Consumed
For each @EventListener: event class → action taken.

## Key Files
| File | Role |
Every significant file in the module mapped to its single responsibility.

## Extension Points
Step-by-step instructions for the 1–2 most common integration tasks:
"To add a new field: touch these files in this order."

## Common Pitfalls
Bulleted DO NOT list specific to this module.

## Example
Minimal before/after code snippet for the most common extension task.

## Test Patterns
What is unit-tested vs. integration-tested. Which fixtures/utilities are used.
Where test files live (path).
```

### Frontend pages (`frontend/<page>.md`)

Same skeleton, adapted:

- **`overview.md`**: routing (React Router v6 setup), React Query client config, Zustand store locations, Vite proxy config, env vars
- **`components.md`**: shadcn/ui import pattern, component file naming, props conventions, Tailwind usage rules
- **`hooks.md`**: custom hook naming (useX), query key conventions, mutation patterns, optimistic update pattern
- **`pages.md`**: page file location (`src/pages/`), route guard pattern, layout slots (`AppLayout`), how to add a new route

Frontend pages replace `Entities / DB Schema / Events` sections with `State shape / Query keys / Store slices`.

---

## Scope Constraints

- Pages describe the current state of the codebase — they are not aspirational.
- No tutorials, no walkthroughs beyond the `Extension Points` section.
- Code snippets are minimal (10–20 lines max) and show the pattern, not a complete implementation.
- Pages are written to be updated incrementally when a module changes — each section is self-contained.

---

## Out of Scope

- Per-endpoint request/response schema documentation (that belongs in the OpenAPI spec at `api.md`)
- User-facing feature docs (belong in `user-guide/`)
- Deployment / ops docs (belong in `admin-guide/`)
