# Module: audit

## Purpose

Immutable audit log of all significant actions. `AuditService` is called by other modules to record events; no direct writes from outside the audit module. `SecurityAuditListener` captures authentication events (login, logout, registration); `WriteAuditListener` captures write events via Spring `@EventListener`. `AuditLevel.SECURITY` is always enabled regardless of config; `WRITE` and `ALL` are toggled via `audit_config`.

---

## Entities Owned

| Entity | Table | Key Fields |
|---|---|---|
| `AuditEvent` | `audit_events` | `timestamp` TIMESTAMPTZ, `userId` UUID? FK→users (SET NULL on delete), `userEmail` VARCHAR(255), `projectId` UUID? FK→projects (SET NULL on delete), `action: AuditAction`, `level: AuditLevel`, `resourceType` VARCHAR(50)?, `resourceId` VARCHAR(100)?, `details` TEXT? (JSON string), `ipAddress` VARCHAR(45)?, `userAgent` VARCHAR(500)? |
| `AuditConfig` | `audit_config` | `level: AuditLevel` (PK), `enabled` BOOLEAN |

`AuditAction` values: `LOGIN_SUCCESS`, `LOGIN_FAILED`, `LOGOUT`, `PASSWORD_CHANGED`, `ROLE_CHANGED`, `API_KEY_CREATED`, `API_KEY_DELETED`, `OAUTH_LOGIN`, `USER_REGISTERED`, `ISSUE_CREATED`, `ISSUE_UPDATED`, `ISSUE_DELETED`, `ISSUE_TRANSITIONED`, `COMMENT_CREATED`, `COMMENT_DELETED`, `SPRINT_STARTED`, `SPRINT_COMPLETED`, `MEMBER_ADDED`, `MEMBER_REMOVED`, `WEBHOOK_CREATED`, `WEBHOOK_DELETED`, `SLA_BREACHED`, `ISSUE_VIEWED`, `BOARD_OPENED`, `REPORT_VIEWED`.

`AuditLevel` values: `SECURITY`, `WRITE`, `ALL`.

Seed data: `audit_config` is pre-populated with `(SECURITY, true)`, `(WRITE, false)`, `(ALL, false)`.

---

## DB Schema

### `audit_events`, `audit_config` (V17 + V22)

```sql
CREATE TABLE audit_events (
    id            UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    timestamp     TIMESTAMPTZ NOT NULL DEFAULT now(),
    user_id       UUID        REFERENCES users(id) ON DELETE SET NULL,
    user_email    VARCHAR(255) NOT NULL,
    project_id    UUID        REFERENCES projects(id) ON DELETE SET NULL,
    action        VARCHAR(100) NOT NULL,
    level         VARCHAR(20)  NOT NULL,
    resource_type VARCHAR(50),
    resource_id   VARCHAR(100),
    details       JSONB,
    ip_address    VARCHAR(45),
    user_agent    VARCHAR(500)
);
-- V22: ALTER TABLE audit_events ALTER COLUMN details TYPE text;
```

V17 created `details` as `JSONB`. **V22 (`V22__audit_details_text.sql`) changed it to `TEXT`.** The `AuditEvent.details` field is `String?`; callers must serialize maps to a JSON string before passing to `AuditService.log()` and deserialize on read.

Indexes: `idx_audit_timestamp` on `(timestamp DESC)`; `idx_audit_project` on `(project_id, timestamp DESC)`; `idx_audit_user` on `(user_id, timestamp DESC)`.

V19 added `org_id UUID REFERENCES organizations(id)` to `audit_events` to support multi-tenancy scoping.

---

## API Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/v1/admin/audit` | ADMIN | List all events; query params: `page`, `size`, `from` (Instant), `to` (Instant), `userId` (UUID), `action` (String), `level` (String); paginated |
| `GET` | `/api/v1/admin/audit/export` | ADMIN | Export all events; `?format=json` (default) or `?format=csv`; CSV response includes `Content-Disposition: attachment; filename=audit.csv` |
| `GET` | `/api/v1/projects/{key}/audit` | ADMIN | List events for project; params: `page`, `size`, `from`, `to`, `action`; enforces `hasRole('ADMIN') or @projectSecurity.isProjectAdmin` |
| `GET` | `/api/v1/admin/audit/config` | ADMIN | Get current audit level toggles; returns list of `{"level": "...", "enabled": ...}` |
| `PUT` | `/api/v1/admin/audit/config` | ADMIN | Update a level toggle; body: `{"level": "WRITE", "enabled": true}` |

All endpoints on `/api/v1/admin/audit` require `@PreAuthorize("hasRole('ADMIN')")`. The project audit endpoint requires ADMIN role or project admin role.

---

## Events Emitted

None. The audit module does not publish domain events.

---

## Events Consumed

| Event | Handler | `AuditAction` logged |
|---|---|---|
| `IssueCreatedEvent` | `WriteAuditListener.onIssueCreated()` | `ISSUE_CREATED` |
| `IssueFieldChangedEvent` | `WriteAuditListener.onIssueUpdated()` | `ISSUE_UPDATED` |
| `IssueStatusChangedEvent` | `WriteAuditListener.onIssueTransitioned()` | `ISSUE_TRANSITIONED` (`actor` may be null → `userEmail = "system"`) |
| `CommentCreatedEvent` | `WriteAuditListener.onCommentCreated()` | `COMMENT_CREATED` |
| `SprintStartedEvent` | `WriteAuditListener.onSprintStarted()` | `SPRINT_STARTED` |
| `SprintCompletedEvent` | `WriteAuditListener.onSprintCompleted()` | `SPRINT_COMPLETED` |

`SecurityAuditListener` is not an `@EventListener`; it is called directly by `AuthService` and OAuth handlers. Methods: `onLoginSuccess()`, `onLoginFailed()`, `onLogout()`, `onRegister()`, `onOAuthLogin()`. All log at `AuditLevel.SECURITY`.

---

## Key Files

- `backend/src/main/kotlin/com/taskowolf/audit/domain/AuditEvent.kt`
- `backend/src/main/kotlin/com/taskowolf/audit/domain/AuditAction.kt`
- `backend/src/main/kotlin/com/taskowolf/audit/domain/AuditLevel.kt`
- `backend/src/main/kotlin/com/taskowolf/audit/domain/AuditConfig.kt`
- `backend/src/main/kotlin/com/taskowolf/audit/application/AuditService.kt`
- `backend/src/main/kotlin/com/taskowolf/audit/application/SecurityAuditListener.kt`
- `backend/src/main/kotlin/com/taskowolf/audit/application/WriteAuditListener.kt`
- `backend/src/main/kotlin/com/taskowolf/audit/api/AuditController.kt`
- `backend/src/main/kotlin/com/taskowolf/audit/infrastructure/AuditEventRepository.kt`
- `backend/src/main/kotlin/com/taskowolf/audit/infrastructure/AuditConfigRepository.kt`
- `backend/src/main/resources/db/migration/V17__audit.sql`
- `backend/src/main/resources/db/migration/V22__audit_details_text.sql`

---

## Extension Points

- **To audit a new action:** inject `AuditService` into the relevant module's Service and call `auditService.log(level, action, userEmail, ...)`. Do NOT call `AuditEventRepository` directly from outside the audit module.
- **To add a new `AuditAction`:** add the value to the `AuditAction` enum; no migration is required (stored as VARCHAR).
- **CSV export formula-injection protection:** `AuditController.escapeCsvCell()` prefixes cells starting with `=`, `+`, `-`, `@`, tab, or carriage-return with a single quote. Apply this to any new exported fields.

---

## Common Pitfalls

- **`details` is TEXT, not JSONB.** V22 changed the column type. The JPA entity maps `details` as `String?`. Pass a serialized JSON string (e.g., via `ObjectMapper.writeValueAsString(map)`) when recording structured details; deserialize manually on read. JSONB operators and PostgreSQL `->` path queries do not work on this column.
- **Audit records are immutable.** There are no `UPDATE` or `DELETE` operations on `audit_events`. Never add them. `AuditEventRepository` extends `JpaRepository` but only `findFiltered` and `findByProject` query methods are used; `save()` is called once at insert time.
- **`GET /audit` is ADMIN-only.** Never relax the `@PreAuthorize("hasRole('ADMIN')")` on admin audit endpoints. The project-scoped endpoint requires project-admin or system admin.
- **`AuditService.log()` is `@Async`.** It runs on a separate thread pool. Logs are not available within the same transaction that triggered the event; do not query for audit records in tests immediately after calling `log()` without flushing the async executor.
- **`SECURITY` level cannot be disabled.** `AuditService.isEnabled()` short-circuits to `true` for `AuditLevel.SECURITY` regardless of `audit_config`. Do not attempt to disable security-level events via the config API.

---

## Example

Calling `AuditService.log()` with a serialized `details` payload, as done in `SlaMonitorJob` when an SLA is breached:

```kotlin
fun recordBreach(issue: Issue, policy: SlaPolicy) {
    // breach-detection logic (elapsed check) runs before this call
    val details = objectMapper.writeValueAsString(
        mapOf("resolutionMinutes" to policy.resolutionMinutes)
    )
    auditService.log(
        level = AuditLevel.WRITE,
        action = AuditAction.SLA_BREACHED,
        userEmail = "system",
        projectId = issue.project.id,
        resourceType = "ISSUE",
        resourceId = issue.id.toString(),
        details = details
    )
}
```

`userEmail = "system"` is the convention for machine-initiated events where no human actor exists. The `details` column is `TEXT` (see V22), so always serialize structured data to a JSON string via `ObjectMapper` before passing it — JSONB operators will not work on this column.

---

## Test Patterns

- **`AuditServiceTest`** — pure unit test with MockK. Verifies: `SECURITY` events are saved regardless of the `enabled` flag in config; `WRITE` events are skipped when disabled; `WRITE` events are saved when enabled.
- **`SecurityAuditListenerTest`** — pure unit test with MockK; mocks `AuditService`. Verifies `onLoginSuccess()` and `onLoginFailed()` pass the correct `AuditLevel` and `AuditAction` to `auditService.log()`.
- **`WriteAuditListenerTest`** — pure unit test with MockK; mocks `AuditService` as `relaxed`. Verifies each `@EventListener` method calls `auditService.log()` with the correct level, action, and `resourceType`. Verifies `IssueStatusChangedEvent` with `actor = null` produces `userEmail = "system"`.
- **`AuditEventRepositoryTest`** — `@DataJpaTest` against H2 (`DB_CLOSE_DELAY=-1`, Flyway disabled, DDL via `ddl-auto=create-drop`). H2 INIT script maps `JSONB` type to `TEXT` for compatibility with V17 schema. Verifies `findFiltered()` returns only matching events when filtered by `action`.
- **`AuditCsvEscapeTest`** — pure unit test. Verifies `AuditController.escapeCsvCell()` prefixes formula-injection characters (`=`, `+`, `-`, `@`, `\t`, `\r`) with `'`; passes safe values including `user@example.com` through unchanged (leading `u` is not a trigger character).
