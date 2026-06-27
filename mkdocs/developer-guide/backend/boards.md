# Module: boards

## Purpose

Provides Kanban (active sprint board) and Scrum backlog views. Handles column ordering and drag-and-drop issue reordering by delegating status changes to `IssueService`, which validates against the workflow. Publishes WebSocket notifications for real-time board updates.

---

## Entities Owned

The boards module owns **no database tables**. All data is derived from workflow statuses, sprint assignments, and issues owned by other modules.

---

## DB Schema

No owned tables. Board views are derived from `workflow_statuses` (column definitions ordered by `position`), `sprints` (active sprint selection), and `issues` (issue data and status assignments).

---

## API Endpoints

### `BoardController` — `/api/v1/projects/{key}`

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/v1/projects/{key}/board` | USER | Returns the active sprint board grouped by workflow status columns; returns 204 No Content when no active sprint exists |
| PATCH | `/api/v1/projects/{key}/board/move` | USER | Moves an issue to a new status column; delegates to `IssueService.update()` which validates the transition against the workflow |
| GET | `/api/v1/projects/{key}/backlog` | USER | Returns PLANNED sprints with their issues plus unassigned backlog issues |

---

## Events Emitted

The boards module emits no domain events. `BoardEventPublisher` subscribes to domain events from other modules and forwards them as WebSocket messages on a per-project topic.

---

## Events Consumed

### `BoardEventPublisher` — WebSocket forwarding via `SimpMessagingTemplate`

| Event | Source module | WebSocket topic | Message payload |
|---|---|---|---|
| `IssueStatusChangedEvent` | issues | `/topic/projects/{projectKey}` | `{ type: "ISSUE_MOVED", issueId, newStatusId, projectKey }` |
| `SprintStartedEvent` | sprints | `/topic/projects/{projectKey}` | `{ type: "SPRINT_UPDATED", sprintId, projectKey }` |
| `SprintCompletedEvent` | sprints | `/topic/projects/{projectKey}` | `{ type: "SPRINT_UPDATED", sprintId, projectKey }` |

---

## Key Files

| File | Responsibility |
|---|---|
| `backend/src/main/kotlin/com/taskowolf/boards/application/BoardService.kt` | `getBoard()`: assembles board columns from active sprint and workflow statuses; `getBacklog()`: returns PLANNED sprint entries plus unassigned issues |
| `backend/src/main/kotlin/com/taskowolf/boards/api/BoardController.kt` | REST endpoints; `move` delegates directly to `IssueService.update()` — no transition logic in this class |
| `backend/src/main/kotlin/com/taskowolf/boards/events/BoardEventPublisher.kt` | `@EventListener` on `IssueStatusChangedEvent`, `SprintStartedEvent`, `SprintCompletedEvent`; forwards to WebSocket |
| `backend/src/main/kotlin/com/taskowolf/boards/api/dto/BoardResponse.kt` | `BoardResponse(sprint: BoardSprintSummary, columns: List<BoardColumnResponse>)`; `BoardSprintSummary` includes `daysRemaining: Long?` |
| `backend/src/main/kotlin/com/taskowolf/boards/api/dto/BacklogResponse.kt` | `BacklogResponse(sprints: List<BacklogSprintEntry>, backlogIssues: List<IssueResponse>)`; `BacklogSprintEntry` includes `totalPoints: Int` |
| `backend/src/main/kotlin/com/taskowolf/boards/api/dto/BoardMoveRequest.kt` | `BoardMoveRequest(issueId: UUID, newStatusId: UUID)` |

---

## Extension Points

**To add a new board column type (e.g. swimlane grouping by assignee):**

1. Add a second grouping pass after the status grouping in `BoardService.getBoard()`.
2. Add the new fields to `BoardColumnResponse` or introduce a separate response DTO.
3. Add a new endpoint in `BoardController` if the view warrants a distinct path.

No DB migration is required — boards derive all data from other modules.

---

## Common Pitfalls

- **Boards do not own status transitions.** `PATCH /board/move` calls `IssueService.update(key, issueId, UpdateIssueRequest(statusId = newStatusId), user)`. Transition validation is performed by `WorkflowService.validateTransition()` inside `IssueService`. Do not add transition logic to `BoardController` or `BoardService`.
- **DO NOT add business logic to board endpoints.** Board endpoints are read-heavy views. Avoid per-issue queries inside loops; always load issues in batch.
- `getBoard()` returns `null` (HTTP 204) when no active sprint exists. Callers must handle the 204 case explicitly.
- `daysRemaining` in `BoardSprintSummary` is computed as `max(0, ChronoUnit.DAYS.between(LocalDate.now(), endDate))`. It is null when `endDate` is not set on the sprint.
- `completedPoints` in `BoardSprintSummary` is computed live from current DONE issues in the sprint, not from `sprint.completedPoints` (which is only set at close time). The board reflects current state, not the final snapshot.

---

## Example

Board column assembly in `BoardService.getBoard()` — columns ordered by `position` from the workflow, each containing only sprint issues with the matching status:

```kotlin
val sprintIssues = issueRepository.findBySprintId(sprint.id)
val issuesByStatus: Map<UUID, List<Issue>> = sprintIssues.groupBy { it.status.id }
val columns = workflow.statuses.map { status ->
    BoardColumnResponse(
        status = StatusSummary(
            id = status.id,
            name = status.name,
            category = status.category.name,
            color = status.color
        ),
        issues = (issuesByStatus[status.id] ?: emptyList()).map { IssueResponse.from(it) }
    )
}
```

`workflow.statuses` is ordered by `position ASC` via `@OrderBy("position ASC")` on `Workflow.statuses`. Column order follows the workflow status order; there is no separate board column configuration.

Drag-and-drop move in `BoardController.move()` — fully delegated, no local logic:

```kotlin
@PatchMapping("/board/move")
fun move(
    @PathVariable key: String,
    @RequestBody request: BoardMoveRequest,
    @AuthenticationPrincipal user: User
) {
    issueService.update(key, request.issueId, UpdateIssueRequest(statusId = request.newStatusId), user)
}
```

---

## Test Patterns

### Unit tests (MockK, no Spring context)

| File | What is tested |
|---|---|
| `BoardServiceTest` | `getBoard()` returns null when no active sprint exists |
| `BoardServiceTest` | `getBoard()` groups issues into columns by status ID |
| `BoardServiceTest` | `getBacklog()` returns PLANNED sprints and unassigned backlog issues |

No dedicated board integration tests exist in isolation. Board endpoints are covered by `SprintLifecycleIntegrationTest` (`backend/src/test/kotlin/com/taskowolf/sprints/SprintLifecycleIntegrationTest.kt`), which exercises `GET /board` and `PATCH /board/move` as part of the full sprint lifecycle.
