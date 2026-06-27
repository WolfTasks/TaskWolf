# Module: issues

## Purpose

Manages the full lifecycle of issues (bugs, stories, tasks, epics, subtasks). Owns CRUD, priority, type, inter-issue linking, and field updates.

---

## Entities Owned

| Entity | Table | Key Fields |
|---|---|---|
| `Issue` | `issues` | `key` VARCHAR(20) UNIQUE (e.g. `TW-5`), `keyNumber` INT, `title` VARCHAR(500) NOT NULL, `description` TEXT, `type: IssueType`, `priority: IssuePriority`, `storyPoints: Int?`, `status` FK→workflow_statuses NOT NULL, `project` FK→projects NOT NULL, `assignee` FK→users nullable, `reporter` FK→users NOT NULL, `sprint` FK→sprints nullable, `parent` FK→issues nullable (self-referential), `dueDate: LocalDate?`, `slaStartTime: Instant?`, `labels: MutableSet<Label>` (@ManyToMany LAZY, join table `issue_labels`, owned by Issue) |
| `IssueLink` | `issue_links` | `fromIssue` FK→issues NOT NULL, `toIssue` FK→issues NOT NULL, `linkType: IssueLinkType` NOT NULL; UNIQUE (from_issue_id, to_issue_id, link_type) |

`IssueType` enum values: `EPIC`, `STORY`, `BUG`, `TASK`, `SUBTASK`.

`IssuePriority` enum values: `CRITICAL`, `HIGH`, `MEDIUM`, `LOW`.

`IssueLinkType` enum values: `BLOCKS`, `BLOCKED_BY`, `RELATES_TO`, `DUPLICATES`, `CLONED_BY`.

---

## DB Schema

### `issues` (V4)

```sql
CREATE TABLE issues (
    id           UUID         NOT NULL PRIMARY KEY,
    "key"        VARCHAR(20)  NOT NULL UNIQUE,
    key_number   INT          NOT NULL,
    title        VARCHAR(500) NOT NULL,
    description  TEXT,
    type         VARCHAR(20)  NOT NULL DEFAULT 'TASK',
    priority     VARCHAR(20)  NOT NULL DEFAULT 'MEDIUM',
    story_points INT,
    status_id    UUID         NOT NULL REFERENCES workflow_statuses(id),
    project_id   UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    assignee_id  UUID         REFERENCES users(id),
    reporter_id  UUID         NOT NULL REFERENCES users(id),
    sprint_id    UUID,
    parent_id    UUID         REFERENCES issues(id),
    due_date     DATE,
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL
);
```

`sprint_id` has no FK constraint in V4 (added by the sprints module migration).

Indexes: `idx_issues_project` on `project_id`, `idx_issues_assignee` on `assignee_id`, `idx_issues_status` on `status_id`, `idx_issues_parent` on `parent_id`.

### `issue_links` (V4)

| Column | Type | Constraint |
|---|---|---|
| `id` | UUID | PK |
| `from_issue_id` | UUID | FK→issues ON DELETE CASCADE |
| `to_issue_id` | UUID | FK→issues ON DELETE CASCADE |
| `link_type` | VARCHAR(30) | NOT NULL |

Unique constraint: `(from_issue_id, to_issue_id, link_type)`.
Indexes: `idx_issue_links_from` on `from_issue_id`, `idx_issue_links_to` on `to_issue_id`.

### `labels` and `issue_labels` (V23)

See the [labels module](labels.md) for the full schema. `issue_labels` is the join table declared via `@JoinTable` on `Issue.labels`.

---

## API Endpoints

### `IssueController` — `/api/v1/projects/{key}/issues`

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/v1/projects/{key}/issues` | USER | Lists issues paginated (`page`, `size`); optional `assigneeMe=true`, `sort=updatedAt`, `overdue=true`, `labelId=<UUID>` (single-label filter); `refs[]` is empty on list; `labels[]` is empty on list |
| POST | `/api/v1/projects/{key}/issues` | USER | Creates an issue; key is auto-generated as `{PROJECT_KEY}-{nextNumber}`; publishes `IssueCreatedEvent` |
| GET | `/api/v1/projects/{key}/issues/{issueKey}` | USER | Returns single issue by `issueKey` (e.g. `TW-5`); `refs[]` is populated from `IssueRefRepository` |
| PATCH | `/api/v1/projects/{key}/issues/{id}` | USER | Partial update by issue UUID; `labelIds: List<UUID>?` replaces the full label set (null = no change, [] = remove all); publishes `IssueFieldChangedEvent` or `IssueStatusChangedEvent` per changed field |
| DELETE | `/api/v1/projects/{key}/issues/{id}` | USER | Deletes issue by UUID; verifies issue belongs to the project |

`{key}` in all paths is the project key (e.g. `TW`). The GET single endpoint uses `{issueKey}` (e.g. `TW-5`) while update and delete use the issue UUID `{id}`.

---

## Events Emitted

### `IssueCreatedEvent`

Published by `IssueService.create()` after saving a new issue.

| Field | Type | Description |
|---|---|---|
| `issue` | `Issue` | The newly created issue entity |
| `actorEmail` | `String` | Email of the user or sender (for email-ingested tickets) |
| `actorId` | `UUID` | ID of the reporter |

### `IssueStatusChangedEvent`

Published by `IssueService.update()` when `statusId` changes and `WorkflowService.validateTransition()` passes.

| Field | Type | Description |
|---|---|---|
| `issue` | `Issue` | The issue after the status change |
| `oldStatus` | `WorkflowStatus` | Previous status entity |
| `newStatus` | `WorkflowStatus` | New status entity |
| `actor` | `User?` | The user who triggered the change; nullable for programmatic transitions |

### `IssueFieldChangedEvent`

Published by `IssueService.update()` once per changed field (title, description, priority, storyPoints, assignee, type, dueDate, sprint).

| Field | Type | Description |
|---|---|---|
| `issue` | `Issue` | The issue being modified |
| `actor` | `User` | The authenticated user performing the update |
| `field` | `String` | Field name as a string (e.g. `"title"`, `"priority"`, `"assignee"`) |
| `oldValue` | `String?` | Previous value serialized to string; null if previously unset |
| `newValue` | `String?` | New value serialized to string; null when clearing a field |

---

## Events Consumed

None. No `@EventListener` annotations exist in `issues/application/IssueService.kt`.

---

## Key Files

| File | Responsibility |
|---|---|
| `backend/src/main/kotlin/com/taskowolf/issues/domain/Issue.kt` | `@Entity` for `issues`; all mutable fields use `var`; `key`, `keyNumber`, `project`, `reporter` are `val` (set on create, never changed) |
| `backend/src/main/kotlin/com/taskowolf/issues/domain/IssueLink.kt` | `@Entity` for `issue_links`; all fields are `val` (links are immutable after creation) |
| `backend/src/main/kotlin/com/taskowolf/issues/domain/IssueLinkType.kt` | Enum `{ BLOCKS, BLOCKED_BY, RELATES_TO, DUPLICATES, CLONED_BY }` |
| `backend/src/main/kotlin/com/taskowolf/issues/domain/IssuePriority.kt` | Enum `{ CRITICAL, HIGH, MEDIUM, LOW }` |
| `backend/src/main/kotlin/com/taskowolf/issues/domain/IssueType.kt` | Enum `{ EPIC, STORY, BUG, TASK, SUBTASK }` |
| `backend/src/main/kotlin/com/taskowolf/issues/domain/events/IssueCreatedEvent.kt` | Data class published on issue creation |
| `backend/src/main/kotlin/com/taskowolf/issues/domain/events/IssueStatusChangedEvent.kt` | Data class published on status transition |
| `backend/src/main/kotlin/com/taskowolf/issues/domain/events/IssueFieldChangedEvent.kt` | Data class published per changed field; `field` is a plain string name |
| `backend/src/main/kotlin/com/taskowolf/issues/api/IssueController.kt` | REST endpoints; fetches `IssueRefRepository` on single-GET only |
| `backend/src/main/kotlin/com/taskowolf/issues/api/dto/CreateIssueRequest.kt` | `title` required; defaults `type=TASK`, `priority=MEDIUM`; optional `assigneeId`, `parentId`, `storyPoints` |
| `backend/src/main/kotlin/com/taskowolf/issues/api/dto/UpdateIssueRequest.kt` | All fields nullable; `clearAssignee`, `clearDueDate`, `clearSprint` booleans used to explicitly null out fields |
| `backend/src/main/kotlin/com/taskowolf/issues/api/dto/IssueResponse.kt` | Serializes all issue fields; `refs: List<IssueRefResponse>` defaults to `emptyList()` on list endpoints |
| `backend/src/main/kotlin/com/taskowolf/issues/application/IssueService.kt` | `create`, `update`, `findByProject`, `findByKey`, `delete`, `createTicketFromEmail`; enforces project scope on all queries |
| `backend/src/main/kotlin/com/taskowolf/issues/infrastructure/IssueRepository.kt` | Custom queries: `maxKeyNumberByProject`, `findOverdueByProjectId`, `findOverdueByProjectIdAndAssigneeId`, `sumStoryPointsBySprintId` |
| `backend/src/main/kotlin/com/taskowolf/issues/infrastructure/IssueLinkRepository.kt` | Queries for `IssueLink` records by `fromIssue` or `toIssue` |

---

## Extension Points

**To add a new field to `Issue`:**

1. Add `@Column var newField: T` to `backend/src/main/kotlin/com/taskowolf/issues/domain/Issue.kt`.
2. Add a Flyway column migration V23+ that alters `issues` to add the column.
3. Add the field to `IssueResponse` in `backend/src/main/kotlin/com/taskowolf/issues/api/dto/IssueResponse.kt`.
4. Add the field (nullable, with a clear boolean if needed) to `UpdateIssueRequest`.
5. Handle the field in `IssueService.update()` — follow the existing pattern: check old ≠ new, mutate, publish `IssueFieldChangedEvent` if audit-worthy.
6. Add to `CreateIssueRequest` if it should be settable on creation.

**To add a new issue type:**

Add the new value to `IssueType.kt`. No migration is needed — `type` is stored as `VARCHAR` with `EnumType.STRING`.

---

## Common Pitfalls

- **DO NOT** query issues without scoping to a project. Always include `projectId` in repository queries. `IssueService` enforces this by calling `projectService.requireMember(projectKey, userId)` before every operation.
- `IssueResponse.refs[]` (issue links from the `integrations` module) is populated only on single-GET (`GET /issues/{issueKey}`), not on list endpoints. This is intentional for performance. Do not add `refs` population to list paths.
- **DO NOT** clear `slaStartTime` on DONE transitions from inside this module. `slaStartTime` is managed by the `servicedesk` module. Trigger SLA stop via `IssueStatusChangedEvent` listeners, not direct mutation.
- When assigning an issue, `resolveAssignee()` checks that the assignee is a member of the project. Passing a non-member's UUID as `assigneeId` returns 404 to avoid leaking user existence.
- Parent issues are validated to belong to the same project. A parent from a different project throws `NotFoundException`.
- Status transitions are validated by `WorkflowService.validateTransition()`. The new status must belong to the project's assigned workflow; a cross-workflow status throws `ForbiddenException`.
- When `overdue=true` in list queries, results are always ordered by `dueDate ASC` regardless of the `sort` parameter — the overdue JPQL query hardcodes `ORDER BY dueDate ASC`.
- **`LabelRepository` is injected in two cross-module locations.** `IssueController.get()` injects it to call `findByIssueId` (native SQL on `issue_labels`) for the single-issue GET — the list endpoint does not populate labels. `IssueService.update()` injects it to call `findAllById` when resolving a new label set on PATCH. Both are deliberate exceptions to the no-cross-module-injection rule; see the [labels module](labels.md) for full context.
- **Labels from a different project passed in `labelIds` are silently dropped, not rejected.** `IssueService.update()` filters the resolved labels by `it.project.id == project.id` before assignment. The call returns 200 with only the valid labels applied — no error is raised for the invalid IDs.

---

## Example

`IssueFieldChangedEvent` publish pattern in `IssueService.update()` — only publishes when the value actually changes:

Service update method — guard on value change before mutating and publishing:

```kotlin
// IssueService.kt — title field update
request.title?.let { newTitle ->
    if (issue.title != newTitle) {
        val old = issue.title
        issue.title = newTitle
        eventPublisher.publish(
            IssueFieldChangedEvent(issue, currentUser, "title", old, newTitle)
        )
    }
}
```

Clearable fields use an explicit boolean flag rather than null-ambiguity:

```kotlin
// Same pattern for clearable fields (assignee, dueDate, sprint)
if (request.clearAssignee) {
    val old = issue.assignee?.displayName
    issue.assignee = null
    if (old != null) {
        eventPublisher.publish(
            IssueFieldChangedEvent(issue, currentUser, "assignee", old, null)
        )
    }
}
```

Every field follows the same guard (`old != new`) to avoid spurious audit events on no-op updates.

---

## Test Patterns

### Unit tests (MockK, no Spring context)

| File | What is tested |
|---|---|
| `IssueServiceTest` | Key numbering: `maxKeyNumberByProject` result + 1 becomes the new `keyNumber` and is reflected in `key` |
| `IssueServiceTest` | `EPIC` type is set correctly when provided in `CreateIssueRequest` |
| `IssueServiceTest` | Missing workflow throws `NotFoundException` on create |
| `IssueServiceTest` | `findByKey` with a key from a different project throws `NotFoundException` |
| `IssueServiceTest` | Non-member assignee throws `NotFoundException` on create |
| `IssueServiceTest` | Parent from a different project throws `NotFoundException` on create |
| `IssueServiceTest` | `update` publishes `IssueFieldChangedEvent` with correct `field`, `oldValue`, `newValue`, `actor` when title changes |
| `IssueServiceTest` | `update` publishes `IssueStatusChangedEvent` with `actor` set when status changes |
| `IssueServiceTest` | `overdue=true` calls `findOverdueByProjectId`; `overdue=true, assigneeMe=true` calls `findOverdueByProjectIdAndAssigneeId`; neither calls general list query |
| `IssueServiceTest` | `clearAssignee=true` sets `assignee` to null; `clearDueDate=true` sets `dueDate` to null; `clearSprint=true` sets `sprint` to null |
| `IssueServiceTest` | `sprintId` provided assigns sprint scoped to the project |
| `IssueServiceTest` | `update` sets labels when `labelIds` is a non-empty list |
| `IssueServiceTest` | `update` clears labels when `labelIds` is an empty list |
| `IssueServiceTest` | `update` silently drops labels from other projects |

### Integration tests (Spring Boot Test + MockMvc + real DB, extend `IntegrationTestBase`)

| File | What is tested |
|---|---|
| `ProjectAndIssueIntegrationTest` | First issue key is `{PREFIX}-1`, second is `{PREFIX}-2`; `statusCategory` of new issue is `TODO` |
