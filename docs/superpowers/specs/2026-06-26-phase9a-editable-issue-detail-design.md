# Phase 9a — Editable Issue Detail Design

**Date:** 2026-06-26  
**Status:** Approved  
**Scope:** Make `IssueDetailPage` fully editable. No new DB tables. No Flyway migration.

---

## Context

The issue detail page (`IssueDetailPage.tsx`) is entirely read-only. Several fields already exist in the DB (`due_date`, `sprint_id`, `reporter_id`, `type`, `priority`, `assignee_id`) but are not fully exposed in the API response or editable from the UI. The assignee is shown as a truncated UUID, reporter and sprint are absent, and the description has no editor.

This phase wires everything up using fields already in the database.

---

## Approach

Four sequential slices, each independently testable:

1. **Backend** — extend DTOs and service
2. **Frontend display** — show all fields correctly (read-only)
3. **Frontend editing** — click-to-edit for sidebar fields
4. **Rich text editor** — TipTap for description

---

## Slice 1 — Backend

### No migration required

All fields already exist in the `issues` table (`due_date`, `sprint_id`, `type`, `priority`, `assignee_id`, `reporter_id`, `created_at`, `updated_at`). Changes are purely in DTOs and service logic.

### `IssueResponse.kt` additions

```kotlin
val dueDate: LocalDate?
val sprintId: UUID?
val sprintName: String?
val assigneeName: String?   // was missing; assigneeId already present
val reporterName: String    // was missing; reporterId already present
val createdAt: Instant
val updatedAt: Instant
```

`from(issue)` reads `issue.sprint?.id`, `issue.sprint?.name`, `issue.assignee?.name`, `issue.reporter.name`, `issue.createdAt`, `issue.updatedAt`.

### `UpdateIssueRequest.kt` additions

```kotlin
val dueDate: LocalDate?       // new
val type: IssueType?          // was missing despite being in DB
val sprintId: UUID?           // null = remove from sprint
```

### `IssueService.update()` additions

```kotlin
request.type?.let { issue.type = it }
request.dueDate.let { issue.dueDate = it }   // explicit null allowed (to clear)
request.sprintId.let { id ->
    issue.sprint = if (id == null) null else sprintRepository.findById(id).orElseThrow()
}
```

`SprintRepository` is already a Spring bean — just inject it into `IssueService`.

---

## Slice 2 — Frontend Display

### Type changes in `src/api/issues.ts`

Extend the `Issue` TypeScript type with:

```ts
dueDate: string | null
sprintId: string | null
sprintName: string | null
assigneeName: string | null
reporterName: string
createdAt: string
updatedAt: string
```

### `IssueDetailPage.tsx` sidebar additions (all read-only)

| Field | Change |
|---|---|
| Assignee | Show `assigneeName` instead of truncated UUID |
| Reporter | New read-only row: `reporterName` |
| Sprint | New read-only row: `sprintName` or "No sprint" |
| Due date | New read-only row: formatted date or "No due date" |
| Created | New read-only row at bottom of sidebar |
| Updated | New read-only row at bottom of sidebar |

No new hooks or components in this slice.

---

## Slice 3 — Frontend Editing (click-to-edit)

### Pattern

Every editable field follows the same interaction model:
- **Idle:** styled value with hover highlight and `cursor-pointer`
- **Active:** control renders in-place (no modal, no edit button)
- **Save:** fires immediately on selection or blur via `PATCH /projects/{key}/issues/{id}` with only the changed field
- **No Save button** anywhere in the sidebar

### New hooks

| Hook | Endpoint |
|---|---|
| `useProjectMembers(projectKey)` | `GET /api/v1/projects/{key}/members` |
| `useProjectSprints(projectKey)` | `GET /api/v1/projects/{key}/sprints` |

### New components

| Component | Behaviour |
|---|---|
| `InlineEditTitle` | Clicking the `<h1>` title replaces it with a text input. Enter or blur saves via PATCH `{ title }`. Escape cancels. |
| `PrioritySelector` | Click opens a small popover listing LOW / MEDIUM / HIGH / CRITICAL. Selection saves immediately and closes. |
| `TypeSelector` | Same pattern; options: TASK / BUG / FEATURE / STORY / EPIC. |
| `AssigneeSelector` | Click opens a searchable member list from `useProjectMembers`. Includes "Unassigned" option. |
| `SprintSelector` | Click opens list of active + upcoming sprints from `useProjectSprints`. Includes "No sprint" option. |
| `DueDatePicker` | Click opens a popover with a native `<input type="date">`. Includes a "Clear" link. |

All components accept `value`, `onChange: (newValue) => void`, and call `useUpdateIssue` internally.

### `IssueDetailPage.tsx`

Each read-only display from Slice 2 is replaced with its corresponding component. Query invalidation on success: `['issues', projectKey, issueKey]`.

---

## Slice 4 — Rich Text Editor (TipTap)

### Dependencies

```
@tiptap/react
@tiptap/starter-kit
@tiptap/extension-link
@tiptap/extension-placeholder
dompurify
@types/dompurify
```

### `RichTextEditor` component

**Props:** `value: string | null`, `onSave: (html: string) => void`

**Idle (read-only):**
- Renders `value` via `dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(value) }}`
- Hover shows a subtle "Click to edit" affordance

**Active (editing):**
- TipTap editor activates in-place, initialised from current HTML
- Minimal floating toolbar: Bold, Italic, Code, Bullet list, Ordered list, Link
- Min-height expands to `200px`

**Saving:**
- On blur: calls `editor.getHTML()`, skips PATCH if content is unchanged, otherwise calls `onSave(html)`
- Empty editor serialises to `""` (not `"<p></p>"`), stored as `null` on the backend

**Empty state:**
- TipTap placeholder extension: "Add a description…"

**Existing plain-text descriptions:** DOMPurify passes plain text through unchanged; TipTap treats it as unformatted content and can be edited normally.

### `IssueDetailPage.tsx`

The static description `<div>` is replaced with `<RichTextEditor value={issue.description} onSave={...} />`.

---

## Data flow summary

```
IssueDetailPage
  ├── useIssue(projectKey, issueKey)          → GET /projects/{key}/issues/{issueKey}
  ├── useUpdateIssue(projectKey)              → PATCH /projects/{key}/issues/{id}
  ├── useProjectMembers(projectKey)           → GET /projects/{key}/members
  └── useProjectSprints(projectKey)          → GET /projects/{key}/sprints
```

All PATCH calls send only the changed field. Query invalidation after any successful PATCH refreshes the full issue from the server.

---

## Out of scope (deferred to later phases)

- Labels
- Versions / Fix version / Affects version
- Custom fields
- Issue links UI
- Sub-tasks / parent issue display
- Status change from detail page (already handled on the board)
