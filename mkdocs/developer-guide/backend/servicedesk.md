# Module: servicedesk

## Purpose

Service Desk / ITSM layer. Manages SLA definitions, SLA monitoring (`SlaMonitorJob` runs on a `@Scheduled` 60-second timer), service queues (one `ServiceDesk` per project), email ingestion (`EmailIngestionService`), and incidents. External users can submit tickets without authentication via the portal endpoint. SLA timing starts when an issue moves to `IN_PROGRESS` and stops when it moves to `DONE`.

---

## Entities Owned

| Entity | Table | Key Fields |
|---|---|---|
| `ServiceDesk` | `service_desks` | `projectId` UUID FK→projects (CASCADE), `emailAddress` VARCHAR(255)?, `enabled` BOOLEAN, extends `AuditableEntity` |
| `SlaPolicy` | `sla_policies` | `serviceDeskId` UUID FK→service_desks (CASCADE), `name` VARCHAR(100), `priority: IssuePriority`, `responseMinutes` INT, `resolutionMinutes` INT, extends `AuditableEntity` |
| `EscalationRule` | `escalation_rules` | `slaPolicyId` UUID FK→sla_policies (CASCADE), `escalateAfterMinutes` INT, `assigneeId` UUID? FK→users, `notifyUserIds` UUID[] (PostgreSQL array), extends `AuditableEntity` |
| `Incident` | `incidents` | `issueId` UUID FK→issues (CASCADE), `severity: IncidentSeverity`, `onCallAssigneeId` UUID? FK→users, `postmortemBody` TEXT?, `resolvedAt` TIMESTAMPTZ?, extends `AuditableEntity` |

`IncidentSeverity` values: `P1`, `P2`, `P3`, `P4`.

V20 also added `sla_start_time TIMESTAMPTZ` to the `issues` table. This column is set by `SlaEventListener` and polled by `SlaMonitorJob`.

---

## DB Schema

### `service_desks`, `sla_policies`, `escalation_rules`, `incidents` (V20)

```sql
ALTER TABLE issues ADD COLUMN sla_start_time TIMESTAMPTZ;

CREATE TABLE service_desks (
    id            UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    project_id    UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    email_address VARCHAR(255),
    enabled       BOOLEAN      NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL
);
CREATE TABLE sla_policies (
    id                 UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    service_desk_id    UUID         NOT NULL REFERENCES service_desks(id) ON DELETE CASCADE,
    name               VARCHAR(100) NOT NULL,
    priority           VARCHAR(20)  NOT NULL,
    response_minutes   INT          NOT NULL,
    resolution_minutes INT          NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL
);
```

`escalation_rules.notify_user_ids` is a PostgreSQL UUID array (`UUID[]`), mapped with `@JdbcTypeCode(SqlTypes.ARRAY)`. H2 does not support array types natively — service-desk repository tests must use PostgreSQL or mock the repository.

---

## API Endpoints

### Service desk (`/api/v1/projects/{key}/service-desk`)

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/v1/projects/{key}/service-desk/enable` | USER | Enable (or re-enable) service desk for project; body: `{"emailAddress":"..."}` (nullable) |
| `GET` | `/api/v1/projects/{key}/service-desk` | USER | Get service desk config for project |
| `POST` | `/api/v1/projects/{key}/service-desk/tickets` | PUBLIC | Submit a ticket without authentication; body: `{"title":"...","description":"...","senderEmail":"..."}` |
| `GET` | `/api/v1/projects/{key}/service-desk/tickets` | USER | List tickets for project; paged (`page`, `size`) |
| `POST` | `/api/v1/projects/{key}/service-desk/sla-policies` | USER | Add SLA policy; body: `{"name":"...","priority":"HIGH","responseMinutes":30,"resolutionMinutes":240}` |
| `GET` | `/api/v1/projects/{key}/service-desk/sla-policies` | USER | List SLA policies for project's service desk |
| `DELETE` | `/api/v1/projects/{key}/service-desk/sla-policies/{id}` | USER | Delete SLA policy; returns 204 |
| `POST` | `/api/v1/projects/{key}/service-desk/sla-policies/{id}/escalation-rules` | USER | Add escalation rule; body: `{"escalateAfterMinutes":60,"assigneeId":null,"notifyUserIds":["<uuid>"]}` |

Write endpoints (`enable`, `addSlaPolicy`, `deleteSlaPolicy`, `addEscalationRule`) require `@projectSecurity.isProjectAdmin(#key, authentication)`. The ticket submission endpoint (`POST /tickets`) has no `@PreAuthorize` — it is explicitly permit-all.

### Incidents (`/api/v1/projects/{key}/incidents`)

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/v1/projects/{key}/incidents` | USER | Declare incident; body: `{"issueId":"...","severity":"P1","onCallAssigneeId":null,"notifyUserIds":["<uuid>"]}` |
| `GET` | `/api/v1/projects/{key}/incidents` | USER | List incidents for project |
| `PATCH` | `/api/v1/projects/{key}/incidents/{id}` | USER | Resolve incident; body: `{"postmortemBody":"..."}` (nullable); returns 204 |

---

## Events Emitted

None. `SlaMonitorJob` sends notifications via `NotificationService.createDirect()` (type `SLA_BREACHED`) and calls `AuditService.log()` (action `SLA_BREACHED`), but does not publish Spring application events.

---

## Events Consumed

| Event | Handler | Effect |
|---|---|---|
| `IssueStatusChangedEvent` | `SlaEventListener.onStatusChanged()` | Sets `issue.slaStartTime = Instant.now()` when status category moves to `IN_PROGRESS` and `slaStartTime` is null; clears `slaStartTime = null` when category moves to `DONE` |

`SlaEventListener` is `@Transactional` — the update to `slaStartTime` commits in the same transaction as the status change.

---

## Key Files

- `backend/src/main/kotlin/com/taskowolf/servicedesk/domain/ServiceDesk.kt`
- `backend/src/main/kotlin/com/taskowolf/servicedesk/domain/SlaPolicy.kt`
- `backend/src/main/kotlin/com/taskowolf/servicedesk/domain/EscalationRule.kt`
- `backend/src/main/kotlin/com/taskowolf/servicedesk/domain/Incident.kt`
- `backend/src/main/kotlin/com/taskowolf/servicedesk/domain/IncidentSeverity.kt`
- `backend/src/main/kotlin/com/taskowolf/servicedesk/application/SlaMonitorJob.kt`
- `backend/src/main/kotlin/com/taskowolf/servicedesk/application/SlaEventListener.kt`
- `backend/src/main/kotlin/com/taskowolf/servicedesk/application/EmailIngestionService.kt`
- `backend/src/main/kotlin/com/taskowolf/servicedesk/application/ServiceDeskService.kt`
- `backend/src/main/kotlin/com/taskowolf/servicedesk/application/IncidentService.kt`
- `backend/src/main/kotlin/com/taskowolf/servicedesk/api/ServiceDeskController.kt`
- `backend/src/main/kotlin/com/taskowolf/servicedesk/api/IncidentController.kt`
- `backend/src/main/resources/db/migration/V20__servicedesk.sql`

---

## Extension Points

- **To add a new SLA metric:** add a column to `sla_policies` (migration V23+), compute it in `SlaMonitorJob.run()`, and expose it in `SlaPolicyResponse`.
- **To add a new escalation action:** add a branch in `SlaMonitorJob.run()` inside the escalation rules loop; `EscalationRule` already carries `assigneeId` for reassignment.
- **To add a new incident severity:** add the value to `IncidentSeverity`; no migration required (stored as VARCHAR).

---

## Common Pitfalls

- **`SlaMonitorJob` is `@Scheduled`, not event-driven.** It runs every 60 seconds (`fixedDelay = 60_000`). SLA breach notifications and audit events fire on the next poll after the breach time, not instantly. Do not assume real-time SLA alerts.
- **`slaStartTime` is cleared to `null` when an issue moves to `DONE`.** This is intentional: it is an idempotency workaround from Phase 7 so that re-opening and re-closing an issue does not accumulate multiple breach notifications. `SlaMonitorJob` skips issues where `slaStartTime IS NULL`. If an issue is re-opened after DONE, `slaStartTime` is re-set only when it next enters `IN_PROGRESS`.
- **`EmailIngestionService` is guarded by `@ConditionalOnProperty`.** It only starts if `taskowolf.mail.imap.enabled=true` is set in the application properties. Without this property the bean (and its `imapFlow` integration flow) is not registered — no startup failure, but no email ingestion either.
- **`IncidentService.resolve()` creates a system comment with `authorId = null`.** A null `authorId` is legal because V21 made `comments.author_id` nullable. The system user ID constant is `IncidentService.SYSTEM_USER_ID = null`. Any downstream code that assumes comments always have an author must handle this case.
- **`POST /tickets` is unauthenticated.** This endpoint is permit-all by design. Ensure the project key is not guessable if projects should not be publicly ticketable.

---

## Example

`SlaMonitorJob.run()` — the `@Scheduled` method that detects breaches and fires escalations:

```kotlin
@Scheduled(fixedDelay = 60_000)
@Transactional
fun run() {
    val now = Instant.now()
    // outer loops: iterate issues, resolve service desk and matching SLA policy per issue
    val elapsed = Duration.between(issue.slaStartTime, now).toMinutes()
    if (elapsed >= policy.resolutionMinutes) {
        notificationService.createDirect(
            userId = uid, type = NotificationType.SLA_BREACHED,
            title = "SLA Breached: ${issue.key}",
            body = "Issue ${issue.key} exceeded ${policy.resolutionMinutes} minutes.",
            link = "/issues/${issue.key}"
        )
        auditService.log(
            level = AuditLevel.WRITE, action = AuditAction.SLA_BREACHED,
            userEmail = "system", projectId = issue.project.id,
            resourceType = "ISSUE", resourceId = issue.id.toString()
        )
    }
}
```

The full method wraps this in three nested `forEach` loops: one over `issueRepository.findBySlaStartTimeIsNotNull()`, one over `escalationRuleRepo.findBySlaPolicyId(policy.id)`, and one over `rule.notifyUserIds`. A null guard (`if (issue.slaStartTime == null) return@forEach`) defends against a race where `SlaEventListener` clears `slaStartTime` between the repository query and the loop body.

---

## Test Patterns

- **`SlaMonitorJobTest`** — pure unit test with MockK. Builds real `Issue`, `Project`, `ServiceDesk`, `SlaPolicy`, and `EscalationRule` domain objects. Verifies: no notification fired when elapsed time is within SLA; `notificationService.createDirect()` and `auditService.log()` called exactly once when elapsed exceeds `resolutionMinutes`; job silently skips when no `ServiceDesk` or no matching `SlaPolicy` is found; handles empty issue list without throwing.
- **`ServiceDeskServiceTest`** — pure unit test with MockK. Verifies: `enable()` creates a new `ServiceDesk` when none exists; re-enables and updates `emailAddress` on an existing disabled desk; `addSlaPolicy()` persists all fields correctly; `addEscalationRule()` persists `notifyUserIds` array.
- **`IncidentServiceTest`** — pure unit test with MockK. Verifies: `create()` saves incident and calls `notificationService.createDirect()` once per `notifyUserId`; `create()` with empty `notifyUserIds` sends no notifications; `resolve()` with `postmortemBody` sets `resolvedAt`, stores the body, and saves a system comment whose body contains "Postmortem"; `resolve()` with null body sets `resolvedAt` but does not save a comment.
