# Module: sprints

## Purpose

Manages sprint lifecycle (PLANNED → ACTIVE → CLOSED). Owns backlog: issues not assigned to any sprint. Snapshots `plannedPoints` at sprint start and calculates `completedPoints` on close.

---

## Entities Owned

| Entity | Table | Key Fields |
|---|---|---|
| `Sprint` | `sprints` | `name` VARCHAR(255) NOT NULL, `goal` TEXT nullable, `status: SprintStatus` NOT NULL DEFAULT PLANNED, `startDate: LocalDate?`, `endDate: LocalDate?`, `plannedPoints: Int?` (snapshot at start), `completedPoints: Int?` (computed at close), `project` FK→projects NOT NULL |

`SprintStatus` enum values: `PLANNED`, `ACTIVE`, `CLOSED`.

---

## DB Schema

### `sprints` (V5)

```sql
CREATE TABLE sprints (
    id               UUID         NOT NULL PRIMARY KEY,
    name             VARCHAR(255) NOT NULL,
    goal             TEXT,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PLANNED',
    start_date       DATE,
    end_date         DATE,
    planned_points   INT,
    completed_points INT,
    project_id       UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    created_at       TIMESTAMP    NOT NULL,
    updated_at       TIMESTAMP    NOT NULL
);
```

Index: `idx_sprints_project` on `project_id`.

V5 also adds FK constraint `fk_issues_sprint` (`issues.sprint_id → sprints.id ON DELETE SET NULL`) and index `idx_issues_sprint` on `issues.sprint_id`.

---

## API Endpoints

### `SprintController` — `/api/v1/projects/{key}/sprints`

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/v1/projects/{key}/sprints` | USER | Lists all sprints for the project in creation order |
| POST | `/api/v1/projects/{key}/sprints` | USER | Creates a sprint in PLANNED state |
| PATCH | `/api/v1/projects/{key}/sprints/{sprintId}` | USER | Updates name, goal, startDate, endDate; date changes rejected if sprint is not PLANNED |
| POST | `/api/v1/projects/{key}/sprints/{sprintId}/start` | USER | Transitions PLANNED→ACTIVE; snapshots `plannedPoints`; rejects if ACTIVE sprint already exists |
| POST | `/api/v1/projects/{key}/sprints/{sprintId}/complete` | USER | Transitions ACTIVE→CLOSED; moves non-DONE issues to backlog; returns `movedToBacklogCount` |
| PUT | `/api/v1/projects/{key}/sprints/{sprintId}/issues/{issueId}` | USER | Assigns an issue to this sprint; rejected if sprint is CLOSED |
| DELETE | `/api/v1/projects/{key}/sprints/{sprintId}/issues/{issueId}` | USER | Removes an issue from this sprint |

---

## Events Emitted

### `SprintStartedEvent`

Published by `SprintService.start()` after transitioning to ACTIVE.

| Field | Type | Description |
|---|---|---|
| `sprint` | `Sprint` | The sprint entity after status change |
| `actorEmail` | `String` | Email of the user who triggered the start |
| `actorId` | `UUID` | ID of the user who triggered the start |

### `SprintCompletedEvent`

Published by `SprintService.complete()` after transitioning to CLOSED.

| Field | Type | Description |
|---|---|---|
| `sprint` | `Sprint` | The sprint entity after status change |
| `movedToBacklogCount` | `Int` | Number of non-DONE issues moved back to backlog |
| `actorEmail` | `String` | Email of the user who completed the sprint |
| `actorId` | `UUID` | ID of the user who completed the sprint |

---

## Events Consumed

None. No `@EventListener` annotations exist in `sprints/application/SprintService.kt`.

---

## Key Files

| File | Responsibility |
|---|---|
| `backend/src/main/kotlin/com/taskowolf/sprints/domain/Sprint.kt` | `@Entity`; all lifecycle fields (`status`, `startDate`, `plannedPoints`, `completedPoints`) are mutable `var` |
| `backend/src/main/kotlin/com/taskowolf/sprints/domain/SprintStatus.kt` | Enum `{ PLANNED, ACTIVE, CLOSED }` |
| `backend/src/main/kotlin/com/taskowolf/sprints/domain/events/SprintStartedEvent.kt` | Data class emitted on sprint start |
| `backend/src/main/kotlin/com/taskowolf/sprints/domain/events/SprintCompletedEvent.kt` | Data class emitted on sprint close; includes `movedToBacklogCount` |
| `backend/src/main/kotlin/com/taskowolf/sprints/application/SprintService.kt` | All business logic: lifecycle transitions, issue assignment, backlog management |
| `backend/src/main/kotlin/com/taskowolf/sprints/api/SprintController.kt` | REST endpoints mapping directly to `SprintService` |
| `backend/src/main/kotlin/com/taskowolf/sprints/api/dto/CreateSprintRequest.kt` | `name` required; `goal`, `startDate`, `endDate` optional |
| `backend/src/main/kotlin/com/taskowolf/sprints/api/dto/UpdateSprintRequest.kt` | All fields nullable; partial update |
| `backend/src/main/kotlin/com/taskowolf/sprints/api/dto/SprintResponse.kt` | Full sprint DTO |
| `backend/src/main/kotlin/com/taskowolf/sprints/api/dto/SprintCompleteResponse.kt` | Wraps `SprintResponse` with `movedToBacklogCount: Int` |
| `backend/src/main/kotlin/com/taskowolf/sprints/infrastructure/SprintRepository.kt` | `findByProjectId`, `findByProjectIdAndStatus`, `existsByProjectIdAndStatus` |

---

## Extension Points

**To add sprint metadata (e.g. a retrospective URL or velocity target):**

1. Add `@Column var newField: T` to `backend/src/main/kotlin/com/taskowolf/sprints/domain/Sprint.kt`.
2. Add a Flyway migration (V23+) that ALTERs the `sprints` table to add the column.
3. Add the field to `SprintResponse` in `backend/src/main/kotlin/com/taskowolf/sprints/api/dto/SprintResponse.kt`.
4. Add the field (nullable) to `UpdateSprintRequest` and handle it in `SprintService.update()`.

---

## Common Pitfalls

- **Only one sprint can be ACTIVE per project at a time.** `SprintService.start()` calls `sprintRepository.existsByProjectIdAndStatus(project.id, SprintStatus.ACTIVE)` and throws `ConflictException` if true. Never bypass this check.
- **DO NOT delete a sprint.** Close it via `POST /{sprintId}/complete`. Deleting a sprint sets `sprint_id` to NULL on all its issues (ON DELETE SET NULL) and permanently loses sprint association history.
- `plannedPoints` is a snapshot taken at start time via `issueRepository.sumStoryPointsBySprintId(sprint.id)`. Adding or removing issues after start does not update `plannedPoints`.
- `completedPoints` is computed at close time from issues whose `status.category == DONE`. It is not kept in sync during the sprint.
- Sprint dates (`startDate`, `endDate`) can only be changed while status is `PLANNED`. `update()` throws `ConflictException` if dates are provided for a non-PLANNED sprint.
- Issues cannot be assigned to a CLOSED sprint; `assignIssue()` throws `ConflictException` when sprint status is `CLOSED`.

---

## Example

Sprint start transition in `SprintService.start()`:

```kotlin
fun start(projectKey: String, sprintId: UUID, actor: User): Sprint {
    val project = projectService.requireMember(projectKey, actor.id)
    val sprint = requireSprint(sprintId, project.id)
    if (sprint.status != SprintStatus.PLANNED) throw ConflictException("Sprint is not in PLANNED state")
    if (sprintRepository.existsByProjectIdAndStatus(project.id, SprintStatus.ACTIVE))
        throw ConflictException("Project already has an active sprint")
    sprint.status = SprintStatus.ACTIVE
    if (sprint.startDate == null) sprint.startDate = LocalDate.now()
    sprint.plannedPoints = issueRepository.sumStoryPointsBySprintId(sprint.id).toInt()
    val saved = sprintRepository.save(sprint)
    eventPublisher.publish(SprintStartedEvent(saved, actorEmail = actor.email, actorId = actor.id))
    return saved
}
```

Sprint close in `SprintService.complete()` — non-DONE issues nulled back to backlog, `completedPoints` captured:

```kotlin
fun complete(projectKey: String, sprintId: UUID, actor: User): SprintCompleteResult {
    val project = projectService.requireMember(projectKey, actor.id)
    val sprint = requireSprint(sprintId, project.id)
    if (sprint.status != SprintStatus.ACTIVE) throw ConflictException("Sprint is not ACTIVE")
    val allIssues = issueRepository.findBySprintId(sprint.id)
    val openIssues = allIssues.filter { it.status.category != StatusCategory.DONE }
    openIssues.forEach { it.sprint = null }
    issueRepository.saveAll(openIssues)
    sprint.completedPoints = allIssues
        .filter { it.status.category == StatusCategory.DONE }.sumOf { it.storyPoints ?: 0 }
    sprint.status = SprintStatus.CLOSED
    val saved = sprintRepository.save(sprint)
    eventPublisher.publish(SprintCompletedEvent(saved, openIssues.size, actor.email, actor.id))
    return SprintCompleteResult(saved, openIssues.size)
}
```

---

## Test Patterns

### Unit tests (MockK, no Spring context)

| File | What is tested |
|---|---|
| `SprintServiceTest` | `start` throws `ConflictException` when an ACTIVE sprint already exists for the project |
| `SprintServiceTest` | `start` sets `status` to ACTIVE and snapshots `plannedPoints` from `sumStoryPointsBySprintId` |
| `SprintServiceTest` | `complete` moves non-DONE issues to backlog (`sprint = null`) and returns correct `movedToBacklogCount` |
| `SprintServiceTest` | `create` persists sprint with the correct project reference |

### Integration tests (Spring Boot Test + MockMvc + real DB, extends `IntegrationTestBase`)

| File | What is tested |
|---|---|
| `SprintLifecycleIntegrationTest` | Full lifecycle: create sprint, assign two issues (8 total points), start (assert `plannedPoints=8`), move one issue to DONE via board, complete (assert `movedToBacklogCount=1`, `status=CLOSED`), verify backlog contains the open issue, verify velocity report has one entry with `completedPoints=5` |
