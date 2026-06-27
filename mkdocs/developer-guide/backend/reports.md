# Module: reports

## Purpose

Provides burndown charts, velocity reports, cycle time analysis, and configurable dashboard widgets. All report endpoints are read-only (`@Transactional(readOnly = true)`). The dashboard entity is the exception — widget layout mutations use write transactions. Reports query directly from `issues`, `sprints`, `issue_activities`, and `workflow_statuses` tables; no materialized views or separate reporting tables are used.

---

## Entities Owned

| Entity | Table | Key Fields |
|---|---|---|
| `Dashboard` | `dashboard` | `projectId` UUID NOT NULL UNIQUE FK→projects ON DELETE CASCADE; one dashboard per project, auto-created on first `GET` |
| `DashboardWidget` | `dashboard_widget` | `dashboardId` UUID FK→dashboard, `type: WidgetType`, `config` TEXT? (JSON, widget-specific), `gridX`/`gridY`/`gridW`/`gridH` INT (grid layout positions) |

`WidgetType` values: `BURNDOWN`, `VELOCITY`, `CYCLE_TIME`, `ISSUE_COUNT`, `ISSUES_BY_STATUS`, `ISSUE_LIST`.

`DashboardWidget.config` is a free-form JSON string. Its schema is undefined at the backend — the frontend interprets it per widget type.

---

## DB Schema

### `dashboard`, `dashboard_widget` (V12)

```sql
CREATE TABLE dashboard (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    project_id UUID        NOT NULL UNIQUE REFERENCES projects(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE dashboard_widget (
    id           UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    dashboard_id UUID        NOT NULL REFERENCES dashboard(id) ON DELETE CASCADE,
    type         VARCHAR(40) NOT NULL,
    config       TEXT,
    grid_x       INT         NOT NULL DEFAULT 0,
    grid_y       INT         NOT NULL DEFAULT 0,
    grid_w       INT         NOT NULL DEFAULT 4,
    grid_h       INT         NOT NULL DEFAULT 4,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL
);
```

Index: `idx_dashboard_widget_dashboard` on `(dashboard_id)`.

---

## API Endpoints

### Reports (`/api/v1/projects/{key}/reports`)

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/v1/projects/{key}/reports/burndown` | USER | Burndown chart for a sprint; requires `?sprintId=<uuid>`; returns `BurndownResponse` with per-day ideal/remaining points |
| `GET` | `/api/v1/projects/{key}/reports/velocity` | USER | Velocity for all CLOSED sprints; returns `VelocityResponse` with planned vs. completed points per sprint |
| `GET` | `/api/v1/projects/{key}/reports/cycle-time` | USER | Cycle time per issue for a sprint (`?sprintId=<uuid>`); omit `sprintId` to get aggregate averages across all closed sprints |

### Dashboard (`/api/v1/projects/{key}/dashboard`)

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/v1/projects/{key}/dashboard` | USER | Get or auto-create the project dashboard; returns `DashboardResponse` with widgets |
| `PUT` | `/api/v1/projects/{key}/dashboard/layout` | ADMIN | Save widget grid positions; body is `List<LayoutItem>`; returns updated `DashboardResponse` |
| `POST` | `/api/v1/projects/{key}/dashboard/widgets` | ADMIN | Add a widget; returns 201 with `WidgetResponse` |
| `DELETE` | `/api/v1/projects/{key}/dashboard/widgets/{widgetId}` | ADMIN | Remove a widget; returns 204 |

All report endpoints enforce `projectService.requireMember()`. Dashboard mutation endpoints enforce `projectService.requireAdmin()`.

---

## Events Emitted

None. The reports module publishes no domain events.

---

## Events Consumed

None. Reports query data directly from repositories; no event listeners.

---

## Key Files

- `backend/src/main/kotlin/com/taskowolf/reports/domain/Dashboard.kt`
- `backend/src/main/kotlin/com/taskowolf/reports/domain/DashboardWidget.kt`
- `backend/src/main/kotlin/com/taskowolf/reports/domain/WidgetType.kt`
- `backend/src/main/kotlin/com/taskowolf/reports/application/ReportsService.kt`
- `backend/src/main/kotlin/com/taskowolf/reports/application/DashboardService.kt`
- `backend/src/main/kotlin/com/taskowolf/reports/api/ReportsController.kt`
- `backend/src/main/kotlin/com/taskowolf/reports/api/DashboardController.kt`
- `backend/src/main/resources/db/migration/V12__create_dashboard_tables.sql`

---

## Extension Points

- **Add a new chart type:** Add a `GET` endpoint in `ReportsController`, add a query method in `ReportsService` annotated `@Transactional(readOnly = true)`. No new DB tables are needed for derived metrics — compute from existing `issues`, `sprints`, and `issue_activities` tables.
- **Add a new dashboard widget type:** Add the value to `WidgetType`. Implement data-fetch logic client-side via an existing report endpoint, or add a dedicated query method in `ReportsService`. The `config` JSON field is available for widget-specific parameters.
- **Sprint burndown fallback:** If `sprint.plannedPoints` is null, `getBurndown()` sums `storyPoints` from all issues assigned to the sprint at query time. The ideal line uses the same sum as the starting value.

---

## Common Pitfalls

- All report queries must be scoped to a project. `getBurndown()` and `getCycleTime()` verify that the requested sprint belongs to the requested project — omitting this check causes cross-project data leaks.
- Do NOT annotate report methods with `@Transactional` without `readOnly = true`. Dashboard mutation methods correctly use write transactions; adding write access to report queries is unnecessary and raises risk.
- Cycle time assumes one workflow per project. `statusRepository.findByWorkflowProjectId()` builds a `name → category` map via `associate`. If two workflow statuses share the same name, one mapping is silently dropped — a known limitation documented in source comments.
- `getCycleTime()` uses the FIRST `IN_PROGRESS` and FIRST `DONE` activity timestamps. Re-opened issues return a non-null cycle time only if the first `DONE` timestamp is strictly after the first `IN_PROGRESS` timestamp (`doneAt.isAfter(inProgressAt)`).
- `DashboardService.getDashboard()` auto-creates a `Dashboard` row inside a write transaction. Do not call it from a read-only context.

---

## Example

Burndown day calculation from `ReportsService.getBurndown()` — the per-day remaining-points logic:

```kotlin
var date = startDate
while (!date.isAfter(endDate)) {
    val dayIndex = ChronoUnit.DAYS.between(startDate, date).toInt()
    val idealPoints = (plannedPoints * (sprintLengthDays - dayIndex).toDouble()
        / sprintLengthDays).toInt()
    val remainingPoints = if (date.isAfter(today)) {
        openIssuePoints
    } else {
        val endOfDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        issues.filter { issue ->
            !(issue.status.category == StatusCategory.DONE
                && issue.updatedAt.isBefore(endOfDay))
        }.sumOf { it.storyPoints ?: 0 }
    }
    days.add(BurndownDay(date, idealPoints, remainingPoints))
    date = date.plusDays(1)
}
```

Future dates use the current open-issue point total as a constant. Past dates recompute remaining points by filtering out issues that were in `DONE` category before end-of-day. The ideal line is a linear interpolation from `plannedPoints` to 0.

---

## Test Patterns

- **`ReportsServiceCycleTimeTest`** — pure unit test with MockK; constructs real `WorkflowStatus` objects with `StatusCategory` values. Injects `IssueActivity` timestamps by reflectively setting `createdAt` on the `AuditableEntity` superclass field. Verifies: correct cycle time for a single issue; null cycle time for issues that never reached `IN_PROGRESS`; null cycle time for issues never marked done; correct average across multiple issues; empty list when sprint has no issues.
- **`DashboardServiceTest`** — pure unit test with MockK. Verifies: `getDashboard()` auto-creates a `Dashboard` when none exists; `addWidget()` requires admin role; `saveLayout()` updates grid positions; `saveLayout()` throws `NotFoundException` when the widget belongs to a different dashboard; `getDashboard()` is accessible to any project member.
- **`DashboardControllerTest`** — MockMvc slice test; verifies request routing and HTTP status codes for each endpoint.
