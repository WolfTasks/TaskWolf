# Developer Wiki Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create 22 MkDocs developer-guide pages covering all 16 backend modules and 4 frontend topics — AI-readable, dense, structured, templated.

**Architecture:** Each page follows the template defined in the design spec (`docs/superpowers/specs/2026-06-27-developer-wiki-design.md`). Content is extracted from source files, not invented. Pages live in `mkdocs/developer-guide/` and are wired into the existing MkDocs Material site via `mkdocs.yml`.

**Tech Stack:** MkDocs + Material theme (already installed via `requirements-docs.txt`), Markdown

## Global Constraints

- Template section order is fixed: Purpose → Entities Owned → DB Schema → API Endpoints → Events Emitted → Events Consumed → Key Files → Extension Points → Common Pitfalls → Example → Test Patterns
- All file paths cited in pages must be relative to repo root (e.g. `backend/src/main/kotlin/...`)
- API Endpoints table Auth column: `PUBLIC` / `USER` / `ADMIN` / `API_KEY` only
- Code snippets: Kotlin for backend, TypeScript for frontend; 10–20 lines max; no ellipsis (`...`) in runnable snippets
- No prose padding — every sentence must convey a constraint, a pattern, or a fact
- Current Flyway version: **V22** (`V22__audit_details_text.sql`); any migration references in docs must note that next migration is V23+
- `python -m mkdocs build --strict` must pass (zero warnings, zero errors) after every task
- Inter-module: modules communicate via `ApplicationEventPublisher` only — no direct cross-module `@Service` injection; enforce this in every module page
- All entities extend `AuditableEntity` (id: UUID, createdAt: Instant, updatedAt: Instant) from `core`
- Exception classes defined in `core`: `NotFoundException` → 404, `ForbiddenException` → 403, `ConflictException` → 409, `BadRequestException` → 400
- Test framework: **MockK** (not Mockito); Testcontainers for integration tests

---

## Task 1: Scaffold + mkdocs.yml + index.md + conventions.md

**Files:**
- Modify: `mkdocs.yml`
- Create: `mkdocs/developer-guide/index.md`
- Create: `mkdocs/developer-guide/conventions.md`
- Create dirs: `mkdocs/developer-guide/backend/` and `mkdocs/developer-guide/frontend/` (empty, filled by later tasks)

- [ ] **Step 1: Update mkdocs.yml — add Developer Guide nav**

In `mkdocs.yml`, replace the existing nav block with (insert `Developer Guide` between `API Reference` and `Development`):

```yaml
nav:
  - Home: index.md
  - Getting Started: getting-started.md
  - Configuration: configuration.md
  - User Guide:
      - Projects & Issues: user-guide/projects.md
      - Boards & Sprints: user-guide/boards.md
      - Automation: user-guide/automation.md
      - Dashboards: user-guide/dashboards.md
  - Admin Guide:
      - SSO / OIDC: admin-guide/sso.md
      - Organizations: admin-guide/organizations.md
      - Service Desk: admin-guide/service-desk.md
      - Audit Logs: admin-guide/audit-logs.md
  - API Reference: api.md
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
  - Development: development.md
```

- [ ] **Step 2: Create placeholder stubs for all 22 pages**

All 22 pages must exist for `mkdocs build --strict` to pass (the nav references them). Create each as a one-line stub — they will be overwritten in later tasks:

```bash
mkdir -p mkdocs/developer-guide/backend mkdocs/developer-guide/frontend

for f in core auth projects issues workflows sprints boards comments notifications attachments automation reports integrations audit organizations servicedesk; do
  echo "# Module: $f" > "mkdocs/developer-guide/backend/$f.md"
done

echo "# Frontend Overview" > mkdocs/developer-guide/frontend/overview.md
echo "# Components" > mkdocs/developer-guide/frontend/components.md
echo "# Hooks" > mkdocs/developer-guide/frontend/hooks.md
echo "# Pages" > mkdocs/developer-guide/frontend/pages.md
```

- [ ] **Step 3: Write mkdocs/developer-guide/index.md**

```markdown
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

1. Read [Conventions](conventions.md) — covers patterns all modules share.
2. Open the module page for the area you are touching.
3. Check **Extension Points** for step-by-step instructions on common tasks.
4. Check **Common Pitfalls** before writing code.
```

- [ ] **Step 4: Write mkdocs/developer-guide/conventions.md**

```markdown
# Conventions

Cross-cutting patterns that apply to every module. Module pages assume you have read this.

## Module Structure

Every module follows hexagonal architecture:

```
<module>/
  domain/         @Entity classes, value objects, *Event.kt domain events
  application/    *Service.kt — business logic, @EventListener handlers
  infrastructure/ *Repository.kt (Spring Data JPA), external adapters
  api/            *Controller.kt — REST endpoints only, no business logic
```

Rules:
- Controllers call Services only. Never call Repositories directly from Controllers.
- Services may call Repositories in the same module. Never inject a Service from another module.
- Cross-module side effects: publish an `ApplicationEvent` via `ApplicationEventPublisher`. The other module listens with `@EventListener`.
- Domain events live in `<module>/domain/` and are plain data classes (no Spring annotations).

## Base Entity

All `@Entity` classes extend `AuditableEntity` from `core`:

```kotlin
// core/domain/AuditableEntity.kt
abstract class AuditableEntity(
    @Id val id: UUID = UUID.randomUUID(),
    @CreatedDate @Column(nullable = false, updatable = false) var createdAt: Instant? = null,
    @LastModifiedDate @Column(nullable = false) var updatedAt: Instant = Instant.now()
) : Persistable<UUID>
```

Do not add `id`, `createdAt`, or `updatedAt` fields to entity subclasses — they are inherited.

## Error Handling

Use the exception classes from `core/infrastructure/GlobalExceptionHandler.kt`:

| Exception | HTTP Status | Error Code |
|-----------|------------|------------|
| `NotFoundException` | 404 | `NOT_FOUND` |
| `ForbiddenException` | 403 | `FORBIDDEN` |
| `ConflictException` | 409 | `CONFLICT` |
| `BadRequestException` | 400 | `BAD_REQUEST` |
| `IllegalArgumentException` | 400 | `BAD_REQUEST` |
| `MethodArgumentNotValidException` | 400 | `VALIDATION_ERROR` |
| `DataIntegrityViolationException` | 409 | `CONFLICT` |

Error response shape:
```json
{ "code": "NOT_FOUND", "message": "Issue not found", "details": {} }
```

Do NOT throw raw `RuntimeException` — use the typed exceptions above.

## Inter-Module Communication

```kotlin
// Publisher side (in any Service):
@Service
class IssueService(private val eventPublisher: ApplicationEventPublisher) {
    fun updateStatus(...) {
        eventPublisher.publishEvent(IssueStatusChangedEvent(issueId, newStatus))
    }
}

// Listener side (in another module's Service):
@Service
class NotificationService {
    @EventListener
    fun onIssueStatusChanged(event: IssueStatusChangedEvent) { ... }
}
```

- Events are synchronous by default (same thread, same transaction).
- Event class names: `<Entity><Action>Event` (e.g. `IssueStatusChangedEvent`).
- Event classes live in the publishing module's `domain/` package.
- Never add a return type to `@EventListener` methods — the return value is ignored.

## Flyway Migrations

- Migration files: `backend/src/main/resources/db/migration/V{n}__{description}.sql`
- Current version: **V22** (`V22__audit_details_text.sql`)
- Next migration must be **V23**
- H2 compatibility: avoid `JSONB`, `UUID` functions, `SERIAL`; use `TEXT`, `VARCHAR(36)`, `BIGSERIAL`
- PostgreSQL-only features: use `-- H2: skip` comment and conditional logic in application code

## Security

Security is configured in `auth/infrastructure/SecurityConfig.kt`.

- Authenticated paths: all `/api/v1/**` routes require a valid JWT by default
- Permit-all paths: `/api/v1/auth/**`, `/api/v1/integrations/**/webhook`, `actuator/health`
- Role enforcement: use `@PreAuthorize("hasRole('ADMIN')")` on Controller methods, not Service methods
- API key auth: `ApiKeyAuthFilter` runs before `JwtAuthFilter`; sets a `UsernamePasswordAuthenticationToken` from the API key owner
- Do NOT add `permitAll()` entries without a security review

JWT claims available via `SecurityContextHolder`:
```kotlin
val userId = (SecurityContextHolder.getContext().authentication.principal as UserDetails).username
```

## API Conventions

- Base path: `/api/v1/projects/{key}/...` for project-scoped resources
- Global resources: `/api/v1/auth/...`, `/api/v1/admin/...`, `/api/v1/orgs/...`
- Pagination: `GET` list endpoints return `{ content: [...], totalElements, totalPages, number, size }`
- IDs: always UUID strings in JSON
- Timestamps: ISO-8601 strings (`2024-01-15T10:30:00Z`)

## Testing

- Framework: **JUnit 5 + MockK** (NOT Mockito — `mockk<T>()`, `every { }`, `verify { }`)
- Unit tests: mock all dependencies with MockK; no Spring context loaded
- Integration tests: use `@SpringBootTest` + Testcontainers PostgreSQL
- Test files: `backend/src/test/kotlin/com/taskowolf/<module>/`
- Naming: `<Subject>Test.kt` for unit tests, `<Subject>IntegrationTest.kt` for integration tests

Minimal MockK unit test pattern:
```kotlin
class IssueServiceTest {
    private val repo = mockk<IssueRepository>()
    private val service = IssueService(repo, mockk())

    @Test
    fun `returns issue by id`() {
        val issue = Issue(title = "T", projectId = UUID.randomUUID())
        every { repo.findById(issue.id) } returns Optional.of(issue)
        val result = service.getById(issue.id)
        assertThat(result.id).isEqualTo(issue.id)
    }
}
```
```

- [ ] **Step 5: Verify build**

```bash
python -m mkdocs build --strict
```

Expected: `INFO - Documentation built in X.XX seconds` with zero warnings.

- [ ] **Step 6: Commit**

```bash
git add mkdocs/developer-guide/ mkdocs.yml
git commit -m "docs(wiki): scaffold developer guide — index, conventions, stubs"
```

---

## Task 2: backend/core.md + backend/auth.md

**Files:**
- Modify: `mkdocs/developer-guide/backend/core.md` (replace stub)
- Modify: `mkdocs/developer-guide/backend/auth.md` (replace stub)

Source files to read before writing:
- `backend/src/main/kotlin/com/taskowolf/core/domain/AuditableEntity.kt`
- `backend/src/main/kotlin/com/taskowolf/core/domain/DomainEventPublisher.kt` (if exists; otherwise `WebSocketConfig.kt`)
- `backend/src/main/kotlin/com/taskowolf/core/infrastructure/GlobalExceptionHandler.kt`
- `backend/src/main/kotlin/com/taskowolf/core/infrastructure/WebSocketConfig.kt`
- `backend/src/main/kotlin/com/taskowolf/auth/domain/` — all files
- `backend/src/main/kotlin/com/taskowolf/auth/application/` — all files
- `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/SecurityConfig.kt`
- `backend/src/main/kotlin/com/taskowolf/auth/api/` — all files
- `backend/src/test/kotlin/com/taskowolf/auth/` — all files

- [ ] **Step 1: Write mkdocs/developer-guide/backend/core.md**

Apply the full template. Key things to extract:

- **Purpose:** "Provides the shared base classes, error handling, WebSocket config, and event bus used by all other modules."
- **Entities Owned:** `AuditableEntity` (id: UUID, createdAt: Instant, updatedAt: Instant) — read `AuditableEntity.kt` to confirm all fields.
- **DB Schema:** No tables — `core` is infrastructure-only, no `@Entity` with its own table. Note this explicitly.
- **API Endpoints:** None — no `@RestController` in core. Note this explicitly.
- **Events Emitted:** None from core directly.
- **Events Consumed:** None.
- **Key Files:** Read all files in `core/` and describe each file's single responsibility.
- **Extension Points:** "To add a new global exception type: add the exception class and `@ExceptionHandler` to `GlobalExceptionHandler.kt`." Show the code pattern.
- **Common Pitfalls:**
    - DO NOT extend anything other than `AuditableEntity` for `@Entity` classes.
    - DO NOT call `eventPublisher.publishEvent()` from a `@Repository` — only from `@Service`.
    - DO NOT add business logic to `GlobalExceptionHandler`.
- **Example:** Adding a new exception type — show before/after in `GlobalExceptionHandler.kt`.
- **Test Patterns:** Read `backend/src/test/kotlin/com/taskowolf/core/` for any test files; if none, note "No tests — all behaviour is covered by module integration tests."

- [ ] **Step 2: Write mkdocs/developer-guide/backend/auth.md**

Apply the full template. Key things to extract:

- **Purpose:** "Manages user identity: JWT issuance/validation, OAuth2 (GitHub + Google), SSO/OIDC, API keys, refresh token rotation, and role-based access control."
- **Entities Owned:** Read all `.kt` files in `auth/domain/` — list each `@Entity` with its key fields.
- **DB Schema:** Read migration files `V1__create_users.sql`, `V6__create_refresh_tokens.sql`, `V13__api_keys.sql`, `V18__sso_configs.sql`. List each table with key columns and FK relationships.
- **API Endpoints:** Read all files in `auth/api/` — list every `@GetMapping`/`@PostMapping`/`@PutMapping`/`@DeleteMapping` with path, HTTP method, and auth requirement.
- **Events Emitted:** Read `auth/domain/` for `*Event.kt` files. If none, note "None — auth changes are not published as domain events."
- **Events Consumed:** Read `auth/application/` for `@EventListener` methods.
- **Key Files:** Map every significant file in `auth/` to its single responsibility.
- **Extension Points:**
    - "To add a new OAuth2 provider: configure in `SecurityConfig.kt` and add a `DbClientRegistrationRepository` entry."
    - "To add a new role: add to the role enum in `auth/domain/`, update `SecurityConfig.kt` `@PreAuthorize` expressions."
- **Common Pitfalls:**
    - DO NOT store API tokens unhashed — only API key tokens use SHA-256 hashing before DB storage. (Note: webhook secrets are an exception — stored plaintext because HMAC needs the raw value; that lives in `integrations`, not `auth`.)
    - DO NOT inject `UserRepository` from outside the `auth` module — publish an event or use the `SecurityContext` principal.
    - DO NOT add `permitAll()` entries to `SecurityConfig` without a security review.
- **Example:** Read an existing API key creation endpoint in `auth/api/` — show the 10–15 line Controller method as the example.
- **Test Patterns:** Read test files in `backend/src/test/kotlin/com/taskowolf/auth/` — describe what is unit-tested vs integration-tested.

- [ ] **Step 3: Verify build**

```bash
python -m mkdocs build --strict
```

Expected: zero warnings, zero errors.

- [ ] **Step 4: Commit**

```bash
git add mkdocs/developer-guide/backend/core.md mkdocs/developer-guide/backend/auth.md
git commit -m "docs(wiki): add core and auth module pages"
```

---

## Task 3: backend/projects.md + backend/issues.md

**Files:**
- Modify: `mkdocs/developer-guide/backend/projects.md`
- Modify: `mkdocs/developer-guide/backend/issues.md`

Source files to read:
- `backend/src/main/kotlin/com/taskowolf/projects/` — all subdirs
- `backend/src/main/kotlin/com/taskowolf/issues/` — all subdirs
- `backend/src/main/resources/db/migration/V2__create_projects.sql`
- `backend/src/main/resources/db/migration/V4__create_issues.sql`
- `backend/src/test/kotlin/com/taskowolf/projects/` — all files
- `backend/src/test/kotlin/com/taskowolf/issues/` — all files

- [ ] **Step 1: Write mkdocs/developer-guide/backend/projects.md**

Apply the full template. Key extractions:

- **Purpose:** "Manages projects (create/update/archive), project membership (invite/remove/roles), and per-project settings."
- **Entities Owned:** Read `projects/domain/` — list each `@Entity`.
- **DB Schema:** Read `V2__create_projects.sql` — list tables, key columns, FKs.
- **API Endpoints:** Read `projects/api/` — list all endpoints. Include membership endpoints (`/members`).
- **Events Emitted:** Read `projects/domain/` for `*Event.kt`.
- **Events Consumed:** Read `projects/application/` for `@EventListener`.
- **Key Files:** Map all significant files.
- **Extension Points:**
    - "To add a new project-scoped setting: add field to the settings entity, add a Flyway column migration (V23+), extend `UpdateProjectRequest` and `ProjectService.update()`."
- **Common Pitfalls:**
    - DO NOT bypass project membership checks — always verify the calling user is a member of the project before returning project data.
    - DO NOT use project `id` in URLs — use project `key` (the short identifier like `TW`).
- **Example:** Show the project membership check pattern from `ProjectService` or `ProjectController` (read the actual code).
- **Test Patterns:** From `backend/src/test/kotlin/com/taskowolf/projects/`.

- [ ] **Step 2: Write mkdocs/developer-guide/backend/issues.md**

Apply the full template. Key extractions:

- **Purpose:** "Manages the full lifecycle of issues (bugs, stories, tasks, epics, subtasks). Owns CRUD, priority, type, inter-issue linking, and field updates."
- **Entities Owned:** Read `issues/domain/Issue.kt`, `IssueLink.kt`, `IssueLinkType.kt`, `IssuePriority.kt`, `IssueType.kt` — list each with key fields.
- **DB Schema:** Read `V4__create_issues.sql` — tables, key columns, FKs, indexes.
- **API Endpoints:** Read `issues/api/IssueController.kt` — list all endpoints.
- **Events Emitted:** Read `issues/domain/IssueCreatedEvent.kt`, `IssueStatusChangedEvent.kt`, `IssueFieldChangedEvent.kt` — list each with payload fields.
- **Events Consumed:** Read `issues/application/IssueService.kt` for `@EventListener`.
- **Key Files:** Map every file.
- **Extension Points:**
    - "To add a new field to Issue: (1) add `@Column` field to `Issue.kt`, (2) add Flyway column migration V23+, (3) add field to `IssueResponse`, (4) add field to `UpdateIssueRequest`, (5) handle in `IssueService.update()`, (6) publish `IssueFieldChangedEvent` if the field is audit-worthy."
    - "To add a new issue type: add to `IssueType.kt` enum — no migration needed."
- **Common Pitfalls:**
    - DO NOT query issues without scoping to a project — always include `projectId` in repository queries.
    - `IssueResponse.refs[]` (issue links) is populated only on single-GET, not on list endpoints — this is intentional for performance.
    - DO NOT clear `slaStartTime` on DONE transitions from outside the `servicedesk` module — use events.
- **Example:** Show the `IssueFieldChangedEvent` publish pattern from `IssueService.update()` (read the actual code).
- **Test Patterns:** From `backend/src/test/kotlin/com/taskowolf/issues/`.

- [ ] **Step 3: Verify build**

```bash
python -m mkdocs build --strict
```

- [ ] **Step 4: Commit**

```bash
git add mkdocs/developer-guide/backend/projects.md mkdocs/developer-guide/backend/issues.md
git commit -m "docs(wiki): add projects and issues module pages"
```

---

## Task 4: backend/workflows.md + backend/sprints.md + backend/boards.md

**Files:**
- Modify: `mkdocs/developer-guide/backend/workflows.md`
- Modify: `mkdocs/developer-guide/backend/sprints.md`
- Modify: `mkdocs/developer-guide/backend/boards.md`

Source files to read:
- `backend/src/main/kotlin/com/taskowolf/workflows/` — all subdirs
- `backend/src/main/kotlin/com/taskowolf/sprints/` — all subdirs
- `backend/src/main/kotlin/com/taskowolf/boards/` — all subdirs
- `backend/src/main/resources/db/migration/V3__create_workflows.sql`
- `backend/src/main/resources/db/migration/V5__create_sprints.sql`
- `backend/src/test/kotlin/com/taskowolf/workflows/`
- `backend/src/test/kotlin/com/taskowolf/sprints/`

- [ ] **Step 1: Write mkdocs/developer-guide/backend/workflows.md**

Apply the full template. Key extractions:

- **Purpose:** "Defines workflow statuses and the valid transitions between them. Guards transitions with configurable conditions."
- **Entities Owned:** Read `workflows/domain/` — list all `@Entity` classes with key fields.
- **DB Schema:** Read `V3__create_workflows.sql`.
- **API Endpoints:** Read `workflows/api/` — list all.
- **Events Emitted:** Read `workflows/domain/` for `*Event.kt`.
- **Events Consumed:** None likely — check `workflows/application/` for `@EventListener`.
- **Key Files:** Map all files.
- **Extension Points:**
    - "To add a new transition guard type: add to the sealed class/enum of guard types, implement the condition evaluator, register in `WorkflowService` or `TransitionGuard`."
- **Common Pitfalls:**
    - DO NOT allow an issue to transition to a status not defined in its project's workflow — always validate against the workflow before applying a status change.
    - Workflow statuses are per-project — never assume global status names.
- **Example:** Show a transition guard evaluation from `WorkflowService` or `TransitionGuard` (read actual code).
- **Test Patterns:** From `backend/src/test/kotlin/com/taskowolf/workflows/`.

- [ ] **Step 2: Write mkdocs/developer-guide/backend/sprints.md**

Apply the full template. Key extractions:

- **Purpose:** "Manages sprint lifecycle (PLANNED → ACTIVE → CLOSED). Owns backlog: issues not assigned to any active sprint."
- **Entities Owned:** Read `sprints/domain/` — list `@Entity` classes.
- **DB Schema:** Read `V5__create_sprints.sql`.
- **API Endpoints:** Read `sprints/api/`.
- **Events Emitted:** Read `sprints/domain/` for `*Event.kt`.
- **Events Consumed:** Read `sprints/application/` for `@EventListener`.
- **Key Files:** Map all files.
- **Extension Points:**
    - "To add sprint metadata (e.g. sprint goal): add `@Column` field to the sprint entity, add Flyway migration, extend request/response DTOs and service method."
- **Common Pitfalls:**
    - Only one sprint can be ACTIVE per project at a time — enforce this constraint in `SprintService`.
    - DO NOT delete a sprint — close it. Deleted sprints lose issue history.
- **Example:** Show the sprint start/close state transition from `SprintService` (read actual code).
- **Test Patterns:** From `backend/src/test/kotlin/com/taskowolf/sprints/`.

- [ ] **Step 3: Write mkdocs/developer-guide/backend/boards.md**

Apply the full template. Key extractions:

- **Purpose:** "Provides Kanban and Scrum board views. Handles column ordering and drag-and-drop issue reordering by publishing events."
- **Entities Owned:** Read `boards/` — check if there are any `@Entity` classes (boards may be view-only with no own tables — note this explicitly if so).
- **DB Schema:** Read migration files referencing board columns or board config. If none, note "No owned tables — board views are derived from workflow statuses and sprint assignments."
- **API Endpoints:** Read `boards/api/`.
- **Events Emitted:** Read `boards/domain/` or `boards/events/`.
- **Events Consumed:** Read `boards/` for `@EventListener`.
- **Key Files:** Map all files.
- **Extension Points:**
    - "To add a new board column type: extend the column query in `BoardService`, add an API response field."
- **Common Pitfalls:**
    - Boards do not own status transitions — they call `IssueService` (via event or direct) which validates against the workflow.
    - DO NOT add business logic to board endpoints — boards are read-heavy views.
- **Example:** Show the drag-and-drop endpoint or board column assembly from `BoardService` (read actual code).
- **Test Patterns:** Read `backend/src/test/kotlin/com/taskowolf/boards/` if it exists.

- [ ] **Step 4: Verify build**

```bash
python -m mkdocs build --strict
```

- [ ] **Step 5: Commit**

```bash
git add mkdocs/developer-guide/backend/workflows.md mkdocs/developer-guide/backend/sprints.md mkdocs/developer-guide/backend/boards.md
git commit -m "docs(wiki): add workflows, sprints, boards module pages"
```

---

## Task 5: backend/comments.md + backend/notifications.md + backend/attachments.md

**Files:**
- Modify: `mkdocs/developer-guide/backend/comments.md`
- Modify: `mkdocs/developer-guide/backend/notifications.md`
- Modify: `mkdocs/developer-guide/backend/attachments.md`

Source files to read:
- `backend/src/main/kotlin/com/taskowolf/comments/` — all subdirs
- `backend/src/main/kotlin/com/taskowolf/notifications/` — all subdirs
- `backend/src/main/kotlin/com/taskowolf/attachments/` — all subdirs
- `backend/src/main/resources/db/migration/V7__create_comments.sql`
- `backend/src/main/resources/db/migration/V8__create_notifications.sql`
- `backend/src/main/resources/db/migration/V9__create_attachments.sql`
- `backend/src/test/kotlin/com/taskowolf/comments/`
- `backend/src/test/kotlin/com/taskowolf/notifications/`
- `backend/src/test/kotlin/com/taskowolf/attachments/`

- [ ] **Step 1: Write mkdocs/developer-guide/backend/comments.md**

Apply the full template. Key extractions:

- **Purpose:** "Manages comments on issues, @mention parsing, and the activity feed."
- **Entities Owned:** Read `comments/domain/` — list each `@Entity` class with key fields.
- **DB Schema:** Read `V7__create_comments.sql` — tables, key columns, FKs.
- **API Endpoints:** Read `comments/api/` — list all endpoints.
- **Events Emitted:** Read `comments/domain/` for `*Event.kt` files.
- **Events Consumed:** Read `comments/application/` for `@EventListener` methods.
- **Extension Points:**
    - "To add a new mentionable entity type: extend the @mention parser in `CommentService` to recognise the new prefix, and publish the appropriate mention event."
- **Common Pitfalls:**
    - Comments are immutable after creation except by the author or an ADMIN — enforce this in `CommentService`.
    - DO NOT store raw HTML in comments — sanitize with the existing utility before persisting.
- **Example:** Show the @mention extraction and event publish from `CommentService` (read actual code).
- **Test Patterns:** From `backend/src/test/kotlin/com/taskowolf/comments/`.

- [ ] **Step 2: Write mkdocs/developer-guide/backend/notifications.md**

Apply the full template. Key extractions:

- **Purpose:** "Delivers in-app, email, and WebSocket push notifications. Reacts to domain events from other modules — it has no public REST API for creating notifications."
- **Entities/DB:** Extract from source. Note that notifications are read-only from the outside (mark-as-read is the only mutation).
- **API Endpoints:** List only — likely `GET /api/v1/notifications`, `PATCH /api/v1/notifications/{id}/read`, `PATCH /api/v1/notifications/read-all`.
- **Events Consumed:** Read `notifications/application/NotificationService.kt` — list every `@EventListener` method and which event it handles.
- **Events Emitted:** None — notifications module does not publish events.
- **Extension Points:**
    - "To trigger a new notification type: publish a domain event from the originating module. Add an `@EventListener` in `NotificationService` that maps that event to a `Notification` entity and calls `notificationRepository.save()` + WebSocket push."
- **Common Pitfalls:**
    - DO NOT call `NotificationService` directly from other modules — use events.
    - WebSocket push uses STOMP destination `/user/{userId}/queue/notifications` — do not change this path without updating the frontend.
- **Example:** Show an `@EventListener` method in `NotificationService` that creates and pushes a notification (read actual code).
- **Test Patterns:** From `backend/src/test/kotlin/com/taskowolf/notifications/`.

- [ ] **Step 3: Write mkdocs/developer-guide/backend/attachments.md**

Apply the full template. Key extractions:

- **Purpose:** "Handles file upload and download. Stores files on the local filesystem (configurable path). Designed with a storage adapter interface for future S3 swap."
- **Entities/DB/Endpoints:** Extract from source.
- **Extension Points:**
    - "To add S3 storage: implement the storage adapter interface (read `attachments/application/` for the interface name), configure the active profile."
- **Common Pitfalls:**
    - DO NOT return raw filesystem paths in API responses — return a URL path through the API.
    - File size limits are enforced in the Spring `multipart` config — do not bypass with a raw `InputStream`.
- **Example:** Show the upload endpoint and storage adapter call from `AttachmentService` or `AttachmentController` (read actual code).
- **Test Patterns:** From `backend/src/test/kotlin/com/taskowolf/attachments/`.

- [ ] **Step 4: Verify build**

```bash
python -m mkdocs build --strict
```

- [ ] **Step 5: Commit**

```bash
git add mkdocs/developer-guide/backend/comments.md mkdocs/developer-guide/backend/notifications.md mkdocs/developer-guide/backend/attachments.md
git commit -m "docs(wiki): add comments, notifications, attachments module pages"
```

---

## Task 6: backend/automation.md + backend/reports.md + backend/integrations.md

**Files:**
- Modify: `mkdocs/developer-guide/backend/automation.md`
- Modify: `mkdocs/developer-guide/backend/reports.md`
- Modify: `mkdocs/developer-guide/backend/integrations.md`

Source files to read:
- `backend/src/main/kotlin/com/taskowolf/automation/` — all subdirs
- `backend/src/main/kotlin/com/taskowolf/reports/` — all subdirs
- `backend/src/main/kotlin/com/taskowolf/integrations/` — all subdirs
- `backend/src/main/resources/db/migration/V11__automation.sql`
- `backend/src/main/resources/db/migration/V12__create_dashboard_tables.sql`
- `backend/src/main/resources/db/migration/V13__api_keys.sql`
- `backend/src/main/resources/db/migration/V14__webhooks.sql`
- `backend/src/main/resources/db/migration/V15__integrations.sql`
- `backend/src/main/resources/db/migration/V16__webhook_secrets_plaintext.sql`
- `backend/src/test/kotlin/com/taskowolf/automation/`
- `backend/src/test/kotlin/com/taskowolf/reports/`
- `backend/src/test/kotlin/com/taskowolf/integrations/`

- [ ] **Step 1: Write mkdocs/developer-guide/backend/automation.md**

Apply the full template. Key extractions:

- **Purpose:** "Executes no-code When/If/Then rules. `AutomationEngine` listens to all domain events and evaluates rules whose trigger matches."
- **Entities/DB/Endpoints:** Extract from source. Note the visual canvas editor (read `automation/domain/` for the rule/condition data model).
- **Events Consumed:** Read `automation/application/AutomationEngine.kt` (or equivalent) — list every `@EventListener` and what it does.
- **Extension Points:**
    - "To add a new trigger event type: (1) add to the trigger enum/sealed class, (2) add an `@EventListener` in `AutomationEngine` for the new event, (3) map event fields to the condition evaluator context."
    - "To add a new action type: add to the action enum/sealed class, implement execution in `AutomationEngine.executeAction()`."
- **Common Pitfalls:**
    - Automation rules run synchronously on the event thread — do not perform slow operations (HTTP calls, file I/O) inside rule execution.
    - AND/OR condition evaluation: read the evaluator to understand precedence — do not assume left-to-right short-circuit.
- **Example:** Show an action execution branch from `AutomationEngine` (read actual code — pick the shortest action type).
- **Test Patterns:** From `backend/src/test/kotlin/com/taskowolf/automation/`.

- [ ] **Step 2: Write mkdocs/developer-guide/backend/reports.md**

Apply the full template. Key extractions:

- **Purpose:** "Provides burndown charts, velocity reports, cycle time analysis, and custom dashboard widgets. Read-only — no mutations."
- **Entities/DB:** Extract from `V12__create_dashboard_tables.sql` and `reports/domain/`.
- **API Endpoints:** Read `reports/api/` — list all. Mark all as read-only (`GET` only).
- **Events Emitted/Consumed:** Likely none — reports query data directly.
- **Extension Points:**
    - "To add a new chart type: add a `GET` endpoint in `ReportController`, add a query method in `ReportService` reading from existing tables. No new DB tables needed for derived metrics."
    - "To add a new dashboard widget: add to the widget type enum, implement the data query in `ReportService`."
- **Common Pitfalls:**
    - Reports query across `issues`, `sprints`, and `workflows` tables — scope all queries to a project to avoid cross-project data leaks.
    - DO NOT add `@Transactional` with write access to report methods — they are read-only.
- **Example:** Show a burndown chart query from `ReportService` (read actual code — the date-bucketed issue count query).
- **Test Patterns:** From `backend/src/test/kotlin/com/taskowolf/reports/`.

- [ ] **Step 3: Write mkdocs/developer-guide/backend/integrations.md**

Apply the full template. Key extractions:

- **Purpose:** "Manages outgoing webhooks, incoming GitHub/GitLab event ingestion, and API key authentication. Owns the `ApiKeyAuthFilter` which runs before `JwtAuthFilter`."
- **Entities/DB:** Read `V13__api_keys.sql`, `V14__webhooks.sql`, `V15__integrations.sql`, `V16__webhook_secrets_plaintext.sql`.
- **API Endpoints:** Read `integrations/api/` — list all. Note: incoming webhook paths (`/api/v1/integrations/github/*/webhook`, `/api/v1/integrations/gitlab/*/webhook`) are `permit-all` in `SecurityConfig`.
- **Events Consumed:** Read `integrations/application/` — list `@EventListener` methods that fire outgoing webhooks.
- **Extension Points:**
    - "To add a new outgoing webhook event type: (1) add `@EventListener` in the webhook dispatcher, (2) map the domain event to the webhook payload shape, (3) add the event type to the webhook event enum."
    - "To add a new incoming provider (e.g. Bitbucket): add a new `@RestController` at `/api/v1/integrations/bitbucket/{repoId}/webhook`, verify HMAC signature with the stored secret, publish a domain event."
- **Common Pitfalls:**
    - Webhook secrets are stored **plaintext** (not hashed) because HMAC verification needs the raw value. This is intentional. Do NOT hash them.
    - API key tokens (`tw_*`) ARE hashed (SHA-256) — only the hash is stored, never the raw token.
    - `SsrfValidator` silently accepts unresolvable DNS hostnames — it only blocks private IP ranges.
    - The `ApiKeyAuthFilter` position in the filter chain: it runs **before** `JwtAuthFilter` using `JwtAuthFilter::class.java` as the anchor position.
- **Example:** Show the HMAC webhook signature verification from the GitHub webhook controller (read actual code).
- **Test Patterns:** From `backend/src/test/kotlin/com/taskowolf/integrations/`.

- [ ] **Step 4: Verify build**

```bash
python -m mkdocs build --strict
```

- [ ] **Step 5: Commit**

```bash
git add mkdocs/developer-guide/backend/automation.md mkdocs/developer-guide/backend/reports.md mkdocs/developer-guide/backend/integrations.md
git commit -m "docs(wiki): add automation, reports, integrations module pages"
```

---

## Task 7: backend/audit.md + backend/organizations.md + backend/servicedesk.md

**Files:**
- Modify: `mkdocs/developer-guide/backend/audit.md`
- Modify: `mkdocs/developer-guide/backend/organizations.md`
- Modify: `mkdocs/developer-guide/backend/servicedesk.md`

Source files to read:
- `backend/src/main/kotlin/com/taskowolf/audit/` — all subdirs
- `backend/src/main/kotlin/com/taskowolf/organizations/` — all subdirs
- `backend/src/main/kotlin/com/taskowolf/servicedesk/` — all subdirs
- `backend/src/main/resources/db/migration/V17__audit.sql`
- `backend/src/main/resources/db/migration/V18__sso_configs.sql`
- `backend/src/main/resources/db/migration/V19__organizations.sql`
- `backend/src/main/resources/db/migration/V20__servicedesk.sql`
- `backend/src/test/kotlin/com/taskowolf/audit/`
- `backend/src/test/kotlin/com/taskowolf/organizations/`
- `backend/src/test/kotlin/com/taskowolf/servicedesk/`

- [ ] **Step 1: Write mkdocs/developer-guide/backend/audit.md**

Apply the full template. Key extractions:

- **Purpose:** "Immutable audit log of all significant actions. `AuditService` is called by other modules to record events; no direct writes from outside."
- **Entities/DB:** Read `V17__audit.sql`. Note the `details` column type — it was changed from `jsonb` to `text` in V22 (`V22__audit_details_text.sql`). The `details` field stores JSON serialized as a text string.
- **API Endpoints:** Read `audit/api/` — admin-only read endpoints.
- **Events Consumed:** Read `audit/application/` for `@EventListener`.
- **Extension Points:**
    - "To audit a new action: inject `AuditService` into the relevant module's Service, call `auditService.record(actor, action, entityType, entityId, details)`. Do NOT call `AuditRepository` directly."
- **Common Pitfalls:**
    - The `details` column is `TEXT` (not `JSONB`) as of V22 — serialize maps to JSON string before saving, deserialize on read.
    - Audit records are immutable — never update or delete them.
    - `GET /audit` is ADMIN-only — never relax this.
- **Example:** Show an `AuditService.record()` call from any module's Service (find one in the codebase and show it).
- **Test Patterns:** From `backend/src/test/kotlin/com/taskowolf/audit/`.

- [ ] **Step 2: Write mkdocs/developer-guide/backend/organizations.md**

Apply the full template. Key extractions:

- **Purpose:** "Multi-tenancy layer. Each project belongs to an organization. `OrganizationContextHolder` makes the current org available via `ThreadLocal` from the JWT `orgId` claim."
- **Entities/DB:** Read `V19__organizations.sql`, `V18__sso_configs.sql` (SSO is per-org). List tables and key columns.
- **API Endpoints:** Read `organizations/api/` — note known gap: `GET /orgs/{id}` lacks membership check (deferred from Phase 7).
- **Events Consumed/Emitted:** Extract from source.
- **Extension Points:**
    - "To scope a new resource to an organization: add an `organizationId` FK column (migration V23+), filter all queries by `OrganizationContextHolder.getCurrentOrgId()`."
- **Common Pitfalls:**
    - `OrganizationContextHolder` uses `ThreadLocal` — it is cleared per request by the org filter. Never cache the org ID outside the request thread.
    - SSO secrets (OIDC client secrets) are AES-GCM encrypted at rest — never store them plaintext.
    - Known gap: `GET /orgs/{id}` does not check org membership — do not rely on it as a security gate.
- **Example:** Show `OrganizationContextHolder.getCurrentOrgId()` usage from any Service (find one in the codebase).
- **Test Patterns:** From `backend/src/test/kotlin/com/taskowolf/organizations/`.

- [ ] **Step 3: Write mkdocs/developer-guide/backend/servicedesk.md**

Apply the full template. Key extractions:

- **Purpose:** "Service Desk / ITSM layer. Manages SLA definitions, SLA monitoring (`SlaMonitorJob` runs on a `@Scheduled` timer), service queues, and email ingestion."
- **Entities/DB:** Read `V20__servicedesk.sql`.
- **API Endpoints:** Read `servicedesk/api/`.
- **Events Consumed/Emitted:** Extract from source.
- **Extension Points:**
    - "To add a new SLA metric: add a column to the SLA table (migration V23+), compute it in `SlaMonitorJob`, expose it in the SLA response DTO."
- **Common Pitfalls:**
    - SLA breach detection: `SlaMonitorJob` is `@Scheduled` — it is NOT event-driven. SLA breach emails fire on the next poll after breach, not instantly.
    - SLA `slaStartTime` is cleared when an issue moves to DONE — this is intentional (idempotency workaround); see Phase 7 notes for context.
    - Email ingestion (`EmailIngestionService`) is guarded by `@ConditionalOnProperty` — it only starts if the mail property is configured.
- **Example:** Show `SlaMonitorJob` or the breach detection logic (read actual code — the `@Scheduled` method body).
- **Test Patterns:** From `backend/src/test/kotlin/com/taskowolf/servicedesk/`.

- [ ] **Step 4: Verify build**

```bash
python -m mkdocs build --strict
```

- [ ] **Step 5: Commit**

```bash
git add mkdocs/developer-guide/backend/audit.md mkdocs/developer-guide/backend/organizations.md mkdocs/developer-guide/backend/servicedesk.md
git commit -m "docs(wiki): add audit, organizations, servicedesk module pages"
```

---

## Task 8: frontend/overview.md + frontend/components.md + frontend/hooks.md + frontend/pages.md

**Files:**
- Modify: `mkdocs/developer-guide/frontend/overview.md`
- Modify: `mkdocs/developer-guide/frontend/components.md`
- Modify: `mkdocs/developer-guide/frontend/hooks.md`
- Modify: `mkdocs/developer-guide/frontend/pages.md`

Source files to read:
- `frontend/src/app/` — Zustand stores
- `frontend/src/api/` — all API client files
- `frontend/src/hooks/` — all custom hooks
- `frontend/src/types/index.ts` — all TypeScript types
- `frontend/src/pages/` — representative page files (at least 2)
- `frontend/src/components/` — representative components (at least 2)
- `frontend/src/layouts/` — AppLayout
- `frontend/vite.config.ts` — proxy config
- `frontend/src/lib/` — utility functions

- [ ] **Step 1: Write mkdocs/developer-guide/frontend/overview.md**

Template adapted for frontend — replace Entities/DB Schema/Events with State Shape/Query Keys/Store Slices.

Sections to include:

**Tech Stack:**
- React 19, TypeScript, Vite, Tailwind CSS 4, shadcn/ui
- React Query (server state), Zustand (UI state)
- @dnd-kit (drag-and-drop), react-grid-layout (dashboards)
- TipTap (rich text editing)

**Dev server:**
```bash
cd frontend && npm run dev   # starts on http://localhost:5173
```
API calls proxy to `http://localhost:8080` — read `vite.config.ts` for exact proxy rules.

**State management:**
- Read `frontend/src/app/` — list each Zustand store, its slice name, and what state it holds.
- React Query is used for all server data. Never duplicate server data in Zustand.
- Rule: Zustand is for pure UI state (modals open/closed, selected board column, etc.).

**Query Keys:**
Read `frontend/src/hooks/` — extract all `queryKey` arrays used in `useQuery` calls. List them in a table: `Hook | Query Key Pattern`.

**API layer:**
Read `frontend/src/api/` — list each API module file and the resources it covers.

**Types:**
Read `frontend/src/types/index.ts` — list all exported types/interfaces with a one-line description.

**Extension Points:**
- "To add a new API resource: add a function to the relevant `frontend/src/api/<module>.ts` file, add a query key constant, create a `useX` hook in `frontend/src/hooks/`."

**Common Pitfalls:**
- DO NOT fetch data in components — always use a `useX` hook.
- DO NOT use `useState` for data that comes from the server — use React Query.
- Auth token is stored in Zustand + localStorage — read from the auth store, never from `localStorage` directly.

**Example:** Show a minimal hook + component pair (find a simple one in `frontend/src/hooks/` and its corresponding usage in a component).

- [ ] **Step 2: Write mkdocs/developer-guide/frontend/components.md**

Sections:

**Component conventions:**
- Read 2–3 components from `frontend/src/components/` — extract the file structure pattern (props interface, component function, exports).
- shadcn/ui components: always import from `@/components/ui/` (the local copy), never directly from the shadcn package.
- Tailwind: class-only styling, no inline `style` props except for dynamic values (e.g. chart colors).
- Component files: one component per file, named identically to the file (e.g. `IssueCard.tsx` exports `IssueCard`).

**State:**
- Read component props patterns — document how components receive data (always via props from a hook call in the parent, never by calling hooks themselves if they are "dumb" presentational components).

**Extension Points:**
- "To add a new shadcn/ui component: run `npx shadcn@latest add <component>` to add it to `frontend/src/components/ui/`. Do not copy-paste the code manually."

**Common Pitfalls:**
Read 2–3 components and note any patterns that appear consistently (e.g. how `cn()` is used for conditional classes, how `forwardRef` is used). List what NOT to do based on what you see.

**Example:** Show the structure of a representative domain component (e.g. an IssueCard or LabelChip) from `frontend/src/components/issue/` or `frontend/src/components/`.

- [ ] **Step 3: Write mkdocs/developer-guide/frontend/hooks.md**

Sections:

**Custom hook patterns:**
- Naming: all hooks start with `use` followed by a noun (e.g. `useIssues`, `useProjectMembers`).
- Read 3 hooks from `frontend/src/hooks/` — show the pattern: `useQuery` or `useMutation` wrapping an API call, with a consistent `queryKey`.

**Query key conventions:**
- Extract from reading hooks — list the key patterns per resource.

**Mutation patterns:**
- Read a `useMutation` hook — show the pattern including `onSuccess` cache invalidation via `queryClient.invalidateQueries`.

**Optimistic update pattern:**
- If any hook uses `onMutate` / `onError` / `onSettled`, show that pattern.

**Extension Points:**
- "To add a hook for a new resource: (1) add API function in `frontend/src/api/<module>.ts`, (2) define query key as `[resource, params]`, (3) create `useX.ts` in `frontend/src/hooks/` exporting `useX` and `useCreateX` / `useUpdateX` as needed."

**Common Pitfalls:**
- DO NOT call API functions directly in components — always via a hook.
- Query keys must be arrays, not strings — `['issues', projectKey]` not `'issues'`.
- Always include `projectKey` in query keys for project-scoped resources to prevent cross-project cache collisions.

**Example:** Show a complete `useX` hook (query + mutation pair) from `frontend/src/hooks/` (read actual code — pick one that is 20–30 lines).

- [ ] **Step 4: Write mkdocs/developer-guide/frontend/pages.md**

Sections:

**Page structure:**
- Read `frontend/src/pages/` — document the directory conventions (feature-based subdirs, `index.tsx` vs named files).
- Read `frontend/src/app/` or `frontend/src/main.tsx` — extract the React Router v6 setup and route tree.

**AppLayout:**
- Read `frontend/src/layouts/` — describe what `AppLayout` provides (sidebar, header, content slot) and how pages slot into it.

**Route guards:**
- Read the auth route guard pattern — show how protected routes check for an authenticated user.

**Adding a new route:**
- Step-by-step:
    1. Create `frontend/src/pages/<feature>/index.tsx`
    2. Add a `<Route path="..." element={<FeaturePage />} />` in the router config (show the exact file to edit)
    3. Add a nav link in `AppLayout` or the relevant nav component (show the exact file)

**Common Pitfalls:**
- Read existing page files and note what patterns are consistent — document the top 3 "DO NOT" rules based on what you see.

**Example:** Show the structure of a simple page file (read one from `frontend/src/pages/` — pick one that is ≤50 lines).

- [ ] **Step 5: Verify build**

```bash
python -m mkdocs build --strict
```

Expected: zero warnings, zero errors, all 22 pages present in the built site.

Also verify the frontend TypeScript build still passes (no doc changes should break it, but confirm):
```bash
cd frontend && npm run build
```

Expected: `✓ built in X.XXs`

- [ ] **Step 6: Commit**

```bash
git add mkdocs/developer-guide/frontend/
git commit -m "docs(wiki): add frontend overview, components, hooks, pages"
```
