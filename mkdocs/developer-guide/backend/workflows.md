# Module: workflows

## Purpose

Defines workflow statuses and the valid transitions between them for each project. Guards transitions with configurable conditions (`RequiredFieldGuard`, `RoleRestrictionGuard`). Exposes a visual canvas editor for administrators.

---

## Entities Owned

| Entity | Table | Key Fields |
|---|---|---|
| `Workflow` | `workflows` | `name` VARCHAR(255) NOT NULL, `project` FK→projects NOT NULL, `isDefault` BOOLEAN NOT NULL DEFAULT false |
| `WorkflowStatus` | `workflow_statuses` | `name` VARCHAR(100) NOT NULL, `category: StatusCategory` NOT NULL, `color` VARCHAR(7) NOT NULL (hex), `position` INT NOT NULL (ordering), `workflow` FK→workflows NOT NULL |
| `WorkflowTransition` | `workflow_transitions` | `workflow` FK→workflows NOT NULL, `fromStatus` FK→workflow_statuses nullable (null = from any status), `toStatus` FK→workflow_statuses NOT NULL, `guards` TEXT (JSON array) |
| `WorkflowStatusPosition` | `workflow_status_positions` | composite PK `(workflow_id, status_id)`, `x` INT, `y` INT (canvas editor coordinates) |

`StatusCategory` enum values: `TODO`, `IN_PROGRESS`, `DONE`.

`TransitionGuard` is a sealed class with two subtypes:

- `RequiredFieldGuard(field: String)` — blocks transition if the named issue field is blank or null.
- `RoleRestrictionGuard(roles: List<String>)` — blocks transition if the actor's project role is not in the list.

Guards are stored as a JSON array in `workflow_transitions.guards` and deserialized via Jackson `@JsonSubTypes`.

---

## DB Schema

### `workflows` (V3)

```sql
CREATE TABLE workflows (
    id          UUID         NOT NULL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    project_id  UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    is_default  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL
);
```

Index: `idx_workflows_project` on `project_id`. V3 also adds `workflow_id UUID REFERENCES workflows(id)` to the `projects` table.

### `workflow_statuses` (V3)

| Column | Type | Constraint |
|---|---|---|
| `id` | UUID | PK |
| `name` | VARCHAR(100) | NOT NULL |
| `category` | VARCHAR(20) | NOT NULL; one of `TODO`, `IN_PROGRESS`, `DONE` |
| `color` | VARCHAR(7) | NOT NULL DEFAULT `#6c8fef` |
| `position` | INT | NOT NULL DEFAULT 0 |
| `workflow_id` | UUID | FK→workflows ON DELETE CASCADE |

Index: `idx_workflow_statuses_workflow` on `workflow_id`.

### `workflow_transitions` (V3 + V10)

| Column | Type | Constraint |
|---|---|---|
| `id` | UUID | PK |
| `workflow_id` | UUID | FK→workflows ON DELETE CASCADE |
| `from_status_id` | UUID | FK→workflow_statuses ON DELETE CASCADE; nullable (null = from any status) |
| `to_status_id` | UUID | FK→workflow_statuses ON DELETE CASCADE NOT NULL |
| `guards` | TEXT | JSON array of `TransitionGuard`; column added by V10 |

### `workflow_status_positions` (V10)

| Column | Type | Constraint |
|---|---|---|
| `workflow_id` | UUID | FK→workflows ON DELETE CASCADE |
| `status_id` | UUID | FK→workflow_statuses ON DELETE CASCADE |
| `x` | INT | NOT NULL DEFAULT 0 |
| `y` | INT | NOT NULL DEFAULT 0 |

Composite PK: `(workflow_id, status_id)`.

---

## API Endpoints

### `WorkflowController` — `/api/v1/projects/{key}/workflows`

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/v1/projects/{key}/workflows` | USER | Lists all workflows for a project; statuses ordered by `position ASC` |

### `WorkflowEditorController` — `/api/v1/projects/{key}/workflow`

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/v1/projects/{key}/workflow` | USER | Returns workflow with statuses, transitions, and canvas layout in a single transaction |
| POST | `/api/v1/projects/{key}/workflow/statuses` | ADMIN | Creates a status; appended at `max(position)+1` |
| PUT | `/api/v1/projects/{key}/workflow/statuses/{sid}` | ADMIN | Updates status name, category, or color |
| DELETE | `/api/v1/projects/{key}/workflow/statuses/{sid}` | ADMIN | Deletes a status |
| POST | `/api/v1/projects/{key}/workflow/transitions` | ADMIN | Creates a transition; `fromStatusId` is nullable (any-to-target) |
| DELETE | `/api/v1/projects/{key}/workflow/transitions/{tid}` | ADMIN | Deletes a transition |
| PUT | `/api/v1/projects/{key}/workflow/transitions/{tid}/guards` | ADMIN | Replaces all guards on a transition with the supplied list |
| PUT | `/api/v1/projects/{key}/workflow/layout` | ADMIN | Replaces all canvas positions; existing positions are deleted before saving the new batch |

---

## Events Emitted

None. The workflows module emits no domain events.

---

## Events Consumed

None. No `@EventListener` annotations exist in `workflows/application/` or `workflows/domain/`.

---

## Key Files

| File | Responsibility |
|---|---|
| `backend/src/main/kotlin/com/taskowolf/workflows/domain/Workflow.kt` | `@Entity`; owns `statuses` and `transitions` as `@OneToMany` cascaded collections ordered by `position ASC` |
| `backend/src/main/kotlin/com/taskowolf/workflows/domain/WorkflowStatus.kt` | `@Entity` for a single status node; `name`, `category`, `color`, `position` are mutable |
| `backend/src/main/kotlin/com/taskowolf/workflows/domain/WorkflowTransition.kt` | `@Entity` for a directed edge; `fromStatus` null means transition from any status |
| `backend/src/main/kotlin/com/taskowolf/workflows/domain/TransitionGuard.kt` | Sealed class with subtypes `RequiredFieldGuard`, `RoleRestrictionGuard`; Jackson polymorphism via `@JsonSubTypes` |
| `backend/src/main/kotlin/com/taskowolf/workflows/domain/StatusCategory.kt` | Enum `{ TODO, IN_PROGRESS, DONE }` |
| `backend/src/main/kotlin/com/taskowolf/workflows/domain/WorkflowStatusPosition.kt` | `@Entity` with composite `@EmbeddedId`; stores `(x, y)` for the canvas editor |
| `backend/src/main/kotlin/com/taskowolf/workflows/application/WorkflowService.kt` | All logic: `createDefault`, `validateTransition`, status CRUD, transition CRUD, layout save |
| `backend/src/main/kotlin/com/taskowolf/workflows/api/WorkflowController.kt` | Read-only listing; returns `WorkflowResponse` with `List<StatusResponse>` |
| `backend/src/main/kotlin/com/taskowolf/workflows/api/WorkflowEditorController.kt` | Admin editor endpoints; returns `WorkflowEditorResponse` including layout |
| `backend/src/main/kotlin/com/taskowolf/workflows/infrastructure/WorkflowRepository.kt` | `findByProjectId(projectId)` |
| `backend/src/main/kotlin/com/taskowolf/workflows/infrastructure/WorkflowTransitionRepository.kt` | `existsByWorkflowId`, `findByWorkflowIdAndFromStatusIdAndToStatusId` (used by `validateTransition`) |
| `backend/src/main/kotlin/com/taskowolf/workflows/infrastructure/WorkflowStatusRepository.kt` | Standard `JpaRepository<WorkflowStatus, UUID>` |
| `backend/src/main/kotlin/com/taskowolf/workflows/infrastructure/WorkflowStatusPositionRepository.kt` | `findByIdWorkflowId`, `deleteByWorkflowId` |

---

## Extension Points

**To add a new transition guard type:**

1. Add a new `data class` extending `TransitionGuard` in `backend/src/main/kotlin/com/taskowolf/workflows/domain/TransitionGuard.kt`.
2. Register it in the `@JsonSubTypes` annotation on `TransitionGuard` with a unique `name` string.
3. Add a `when` branch for the new guard type in `WorkflowService.validateTransition()`.

No DB migration is required — guards are stored as JSON TEXT in `workflow_transitions.guards`.

---

## Common Pitfalls

- **DO NOT** allow an issue to transition to a status not defined in its project's workflow. Always call `WorkflowService.validateTransition()` before applying a status change. Transitions are silently allowed only when the workflow has zero configured transitions (new projects without any transition rows).
- Workflow statuses are per-project. Never reference status names (`"To Do"`, `"Done"`) directly in code — always look up by `statusId` scoped to the project's workflow.
- `fromStatus` being null on a `WorkflowTransition` means "from any status to this target." Do not treat a null `fromStatus` as a misconfigured transition.
- `getDefaultStatus()` returns the lowest-`position` status with category `TODO`. If no TODO status exists, `NotFoundException` is thrown on issue creation.
- `WorkflowEditorController` uses `requireAdmin`; `WorkflowController` uses `requireMember`. All mutation endpoints require ADMIN.
- The canvas layout (`workflow_status_positions`) is fully replaced on every `PUT /layout` call. Sending a partial list silently drops omitted positions.

---

## Example

Guard evaluation in `WorkflowService.validateTransition()`:

```kotlin
for (guard in guards) {
    when (guard) {
        is RequiredFieldGuard -> {
            val value = issueMap[guard.field]
            if (value.isNullOrBlank())
                throw BadRequestException(
                    "Transition blocked: field '${guard.field}' is required"
                )
        }
        is RoleRestrictionGuard -> {
            val member = projectMemberRepository
                .findByProjectIdAndUserId(issue.project.id, actor.id)
            val userRole = member?.role?.name ?: "NONE"
            if (userRole !in guard.roles)
                throw BadRequestException(
                    "Transition blocked: role '$userRole' not permitted"
                )
        }
    }
}
```

`issueMap` is built from scalar fields of the issue: `title`, `description`, `assigneeId`, `storyPoints`, `dueDate`. Only those field names are checkable by `RequiredFieldGuard`.

---

## Test Patterns

### Unit tests (MockK, no Spring context)

| File | What is tested |
|---|---|
| `WorkflowTransitionGuardTest` | Passes with no-op when `project.workflow` is null |
| `WorkflowTransitionGuardTest` | `BadRequestException` when no matching transition row exists in the repository |
| `WorkflowTransitionGuardTest` | Passes when transition exists but `guards` column is null |
| `WorkflowTransitionGuardTest` | `BadRequestException` when `RequiredFieldGuard` field is blank or null on the issue |
| `WorkflowTransitionGuardTest` | Passes when `RequiredFieldGuard` field is present and non-blank |
