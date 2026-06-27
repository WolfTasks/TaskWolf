# Module: labels

## Purpose

Manages project-scoped colored labels. Labels can be assigned to issues in any quantity (many-to-many) and used to filter the issue list. Label CRUD is performed by project members via a dedicated settings page.

---

## Entities Owned

| Entity | Table | Key Fields |
|---|---|---|
| `Label` | `labels` | `name` VARCHAR(50) NOT NULL, `color` VARCHAR(7) NOT NULL (hex), `project` FK→projects NOT NULL; UNIQUE (project_id, name) |

The `issue_labels` join table is owned by `Issue.labels` (`@JoinTable` on `Issue`), not by `Label`.

---

## DB Schema

### `labels` (V23)

```sql
CREATE TABLE labels (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    project_id UUID        NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name       VARCHAR(50) NOT NULL,
    color      VARCHAR(7)  NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (project_id, name)
);
```

### `issue_labels` (V23)

Join table owned by `Issue.labels`. Cascade-deletes when either the issue or label is deleted.

| Column | Type | Constraint |
|---|---|---|
| `issue_id` | UUID | FK→issues ON DELETE CASCADE |
| `label_id` | UUID | FK→labels ON DELETE CASCADE |

Primary key: `(issue_id, label_id)`.

---

## API Endpoints

### `LabelController` — `/api/v1/projects/{key}/labels`

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/v1/projects/{key}/labels` | USER | Lists all labels for the project |
| POST | `/api/v1/projects/{key}/labels` | USER | Creates a label; 409 if name already exists in project |
| PUT | `/api/v1/projects/{key}/labels/{id}` | USER | Replaces name and color; 409 on name conflict |
| DELETE | `/api/v1/projects/{key}/labels/{id}` | USER | Deletes label; `issue_labels` rows removed by DB cascade |

All endpoints require project membership (`ProjectService.requireMember()`).

---

## Events Emitted

None. Label assignment changes on issues are recorded as `IssueFieldChangedEvent` by `IssueService.update()` in the issues module.

---

## Events Consumed

None.

---

## Key Files

| File | Purpose |
|---|---|
| `backend/src/main/resources/db/migration/V23__labels.sql` | Creates `labels` and `issue_labels` tables |
| `backend/src/main/kotlin/com/taskowolf/labels/domain/Label.kt` | JPA entity |
| `backend/src/main/kotlin/com/taskowolf/labels/infrastructure/LabelRepository.kt` | `findByProjectId`, `existsByProjectIdAndName`, `findByIssueId` (native SQL) |
| `backend/src/main/kotlin/com/taskowolf/labels/application/LabelService.kt` | CRUD logic |
| `backend/src/main/kotlin/com/taskowolf/labels/api/LabelController.kt` | REST controller |
| `backend/src/main/kotlin/com/taskowolf/labels/api/dto/LabelRequest.kt` | `{name, color}` for POST/PUT |
| `backend/src/main/kotlin/com/taskowolf/labels/api/dto/LabelResponse.kt` | `{id, name, color}` |
| `backend/src/test/kotlin/com/taskowolf/labels/LabelServiceTest.kt` | Unit tests |

---

## Extension Points

The color palette (`PALETTE`) is defined as a constant in `frontend/src/components/issue/LabelSelector.tsx`. The backend accepts any valid hex string — the palette is only enforced client-side.

---

## Common Pitfalls

- **`IssueController.get()` injects `LabelRepository` directly.** This is the only deliberate cross-module repository injection in the codebase. It exists because `findByIssueId` uses native SQL on `issue_labels`, a join table whose `@JoinTable` is declared on `Issue`. Do not replicate this pattern elsewhere.
- **`null` vs empty list on `UpdateIssueRequest.labelIds`.** `null` = no change to labels; `[]` = remove all labels. The service checks `request.labelIds != null` before touching the label set.
- **`@ManyToMany(fetch=LAZY)` on `Issue.labels`.** Do not access `issue.labels` outside a transaction. `IssueController.get()` fetches labels explicitly via `LabelRepository.findByIssueId()` to avoid lazy-loading surprises.

---

## Example

```kotlin
// Create a label then assign it to an issue
val label = labelService.create("WOLF", LabelRequest("bug", "#e11d48"), actor)
issueService.update("WOLF", issueId, UpdateIssueRequest(labelIds = listOf(label.id)), actor)
```

---

## Test Patterns

| File | What is tested |
|---|---|
| `LabelServiceTest` | `list` returns labels for the correct project |
| `LabelServiceTest` | `create` saves a new label |
| `LabelServiceTest` | `create` throws `ConflictException` when name already exists in project |
| `LabelServiceTest` | `update` changes name and color |
| `LabelServiceTest` | `delete` removes the label |
| `LabelServiceTest` | `delete` throws `NotFoundException` when label belongs to a different project |
| `IssueServiceTest` | `update` sets labels when `labelIds` is a non-empty list |
| `IssueServiceTest` | `update` clears labels when `labelIds` is an empty list |
