# Phase 9b — Labels Design Spec

**Date:** 2026-06-27  
**Status:** Approved

## Overview

Labels are project-scoped colored text tags that can be assigned to issues in any quantity. They are managed via a dedicated project settings page and can also be created on-the-fly inside the issue detail. Labels are visible only in the issue detail sidebar. The issue list supports filtering by label both via a toolbar dropdown and by clicking a label chip in the issue detail.

## Database

Migration V23 adds two tables:

```sql
CREATE TABLE labels (
  id          BIGSERIAL PRIMARY KEY,
  project_id  BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  name        VARCHAR(50) NOT NULL,
  color       VARCHAR(7) NOT NULL,  -- hex e.g. '#e11d48'
  UNIQUE (project_id, name)
);

CREATE TABLE issue_labels (
  issue_id    BIGINT NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
  label_id    BIGINT NOT NULL REFERENCES labels(id) ON DELETE CASCADE,
  PRIMARY KEY (issue_id, label_id)
);
```

- `color` is a hex string from a fixed 12-color palette defined in the frontend.
- `UNIQUE(project_id, name)` prevents duplicate label names within a project.
- Cascade deletes on both tables: deleting a label automatically removes all issue assignments.

## Backend API

### New: LabelController, LabelService, LabelRepository

```
GET    /api/v1/projects/{key}/labels          → List<LabelResponse>
POST   /api/v1/projects/{key}/labels          ← {name, color}
PUT    /api/v1/projects/{key}/labels/{id}     ← {name, color}
DELETE /api/v1/projects/{key}/labels/{id}
```

`LabelResponse`: `{ id: Long, name: String, color: String }`

All endpoints require project membership. DELETE cascades automatically (issue assignments are removed by the DB foreign key cascade).

### Issue Integration (extends existing classes)

- `IssueResponse` gains `labels: List<LabelResponse>` — always loaded via LEFT JOIN on `issue_labels` and `labels`.
- `UpdateIssueRequest` gains `labelIds: List<Long>` (optional field; `null` = no change, empty list = remove all labels).
- `IssueService.update()`: if `labelIds != null` → DELETE all rows in `issue_labels` WHERE `issue_id = ?`, then batch INSERT the new set.
- `IssueController.list()` gains an optional `labelId: Long` query parameter; when present, adds `INNER JOIN issue_labels il ON il.issue_id = i.id AND il.label_id = ?`.

### Audit

Label changes are logged as `ISSUE_UPDATED` events (no new event type needed).

## Frontend

### API & Hooks

New methods on `projectsApi`: `getLabels`, `createLabel(name, color)`, `updateLabel(id, name, color)`, `deleteLabel(id)`.

New hooks:
- `useLabels(projectKey)` — fetches label list, cached per project
- `useCreateLabel(projectKey)`, `useUpdateLabel(projectKey)`, `useDeleteLabel(projectKey)`

### Color Palette

Fixed set of 12 hex colors (defined as a constant, e.g. Tailwind-inspired):
`#e11d48 #f97316 #eab308 #22c55e #14b8a6 #3b82f6 #8b5cf6 #ec4899 #64748b #0ea5e9 #84cc16 #f43f5e`

### New Components

**`LabelChip`** — reusable colored pill:
```
bg-[color]/15  text-[color]  border border-[color]/30  rounded-full  px-2 py-0.5  text-xs
```
Used in `LabelSelector`, settings table, and (future) issue list rows.

**`LabelSelector`** — analogous to `AssigneeSelector`:
- Click opens a Popover with a search input and a checklist of existing project labels.
- When no label matches the search text, shows a "Create label '{name}'" option that calls `createLabel` then immediately adds the new ID to the selection.
- Closes on outside click; on close fires `patch({ labelIds: [...currentIds] })`.
- Displayed in `IssueDetailPage` sidebar as a new `SidebarField "Labels"`.

### Settings Page

Route: `/projects/:key/settings/labels`  
Nav link added to the existing project settings sidebar navigation.

- Table of existing labels: colored `LabelChip` preview, name, Edit button, Delete button.
- "New Label" button reveals an inline form: text input for name + 12-color palette picker (small colored circles, click to select).
- Edit opens the same inline form populated with current values.
- Validation: name required, max 50 chars, must be unique in project (409 from API surfaced as inline error).

### Issue List Filtering

- New "Label" single-select dropdown in `IssueListPage` toolbar (same style as Status/Priority filters). Single-select keeps the backend query simple and covers the primary use case; multi-select is deferred.
- Selecting a label adds `?labelId={id}` to the URL and passes it to the API.
- In `IssueDetailPage`, clicking a `LabelChip` navigates to the issue list with `?labelId={id}` pre-set.

## Out of Scope

- Labels on the Kanban board cards (deferred to a future phase).
- Bulk label assignment from the issue list.
- Label descriptions or icons.
- Cross-project shared labels.

## Wiki Documentation (Task 8)

After all implementation tasks are complete, the following developer wiki pages are created or updated:

**New:** `mkdocs/developer-guide/backend/labels.md` — full module page following the standard template (Purpose, Entities Owned, DB Schema, API Endpoints, Events, Key Files, Extension Points, Common Pitfalls, Example, Test Patterns).

**Updated:** `mkdocs/developer-guide/backend/issues.md` — add `labels: MutableSet<Label>` to the Issue entity row; add V23 `labels` and `issue_labels` table descriptions; extend GET list endpoint description with `labelId` filter; extend PATCH description with `labelIds`; add pitfall note about direct `LabelRepository` injection in `IssueController.get()`; add label tests to Test Patterns.

**Updated:** `mkdocs.yml` — add `labels: developer-guide/backend/labels.md` to the backend nav section after `issues`.

**Updated:** `mkdocs/developer-guide/frontend/components.md` — add `LabelChip`, `LabelSelector` to the `issue/` line in the component directory listing.

**Updated:** `mkdocs/developer-guide/frontend/hooks.md` — add `['labels', projectKey]` row to the query key table; add `useLabels`, `useCreateLabel`, `useUpdateLabel`, `useDeleteLabel` to the hook inventory.

**Updated:** `mkdocs/developer-guide/frontend/pages.md` — add `LabelsPage` to the `projects/settings/` line in the page directory listing; add Labels nav link to the AppLayout table.

Verification: `python -m mkdocs build --strict` must pass with zero warnings after all edits.
