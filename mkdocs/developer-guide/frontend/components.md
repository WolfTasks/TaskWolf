# Frontend Components

## Component Conventions

Components live in `frontend/src/components/` organised by feature domain:

```
frontend/src/components/
  issue/          # StatusBadge, AssigneeSelector, PrioritySelector, InlineEditTitle,
  #               # RichTextEditor, DueDatePicker, TypeSelector, SprintSelector,
  #               # LabelChip, LabelSelector
  board/          # DraggableCard, BoardColumn
  sprint/         # SprintHeader, CompleteSprintDialog, CreateSprintForm
  comments/       # CommentThread, ActivityFeed
  notifications/  # NotificationBell
  workflow/       # WorkflowCanvas, StatusNode, TransitionArrow, TransitionGuardPanel
  attachments/    # AttachmentPanel
  automation/     # RuleEditor, TriggerSelector, ConditionGroupBuilder,
  #               # ConditionRow, ActionList, ActionRow
  dashboard/      # DashboardCanvas, WidgetWrapper, WidgetPalette, BurndownWidget,
  #               # VelocityWidget, CycleTimeWidget, IssueCountWidget,
  #               # IssueListWidget, IssuesByStatusWidget
  ui/             # Local shadcn/ui copies — import only from here, never from the package
  OrgSwitcher.tsx # Top-level shared component (no sub-folder)
```

**File naming:** one component per file, named identically to the file (`StatusBadge.tsx` exports `StatusBadge`).

**Props interface:** Defined as `interface Props { ... }` at the top of the file. Export the interface only when another file needs to reference it; keep it file-local otherwise.

**Exports:** Named exports only — no default exports.

**Styling:** Tailwind utility classes only. No inline `style` props except for purely dynamic numeric values (e.g., progress bar width, chart colours). Use `cn()` from `@/lib/utils` for conditional or composed class strings.

**shadcn/ui:** Always import from `@/components/ui/<name>`. Never import directly from the `shadcn` npm package.

```typescript
// frontend/src/components/issue/StatusBadge.tsx
import { cn } from '@/lib/utils'
const categoryColors = {
  TODO: 'bg-blue-900 text-blue-300',
  IN_PROGRESS: 'bg-yellow-900 text-yellow-300',
  DONE: 'bg-green-900 text-green-300',
}

interface Props {
  name: string
  category: 'TODO' | 'IN_PROGRESS' | 'DONE'
}

export function StatusBadge({ name, category }: Props) {
  return (
    <span className={cn('px-2 py-0.5 rounded text-xs font-medium', categoryColors[category])}>
      {name}
    </span>
  )
}
```

---

## State

**Presentational components** receive all data via props. They do not call `useQuery`, `useMutation`, or any data-fetching hook. The parent page or container owns the query and passes results down.

**Self-contained widget components** may call a hook when no logical parent owns that specific data. `NotificationBell` is the canonical example — it calls `useUnreadCount` directly because the bell is always rendered inside `AppLayout` without a parent page that would naturally own the count.

```typescript
// frontend/src/components/notifications/NotificationBell.tsx
import { useNavigate } from 'react-router-dom'
import { useUnreadCount } from '@/hooks/useNotifications'
export function NotificationBell() {
  const navigate = useNavigate()
  const count = useUnreadCount().data ?? 0
  return (
    <button
      onClick={() => navigate('/notifications')}
      className="relative p-1.5 text-gray-400 hover:text-white rounded"
      aria-label="Notifications"
    >
      {count > 0 && (
        <span className="absolute top-0 right-0 bg-red-500 text-white text-[10px] font-bold rounded-full px-0.5">
          {count > 99 ? '99+' : count}
        </span>
      )}
    </button>
  )
}
```

**Local UI state** (open/closed toggles, form inputs) uses React `useState` inside the component.

---

## Issue Dialog

Issues can be viewed two ways: as a full page (`/p/:key/issues/:issueKey`) or as a modal overlay on top of the current page (Board, Backlog, Issue List), opened via the `?issue=KEY` URL query parameter. Both views render the same shared content component, so there is only one implementation of the issue detail UI to maintain.

**`IssueDetailContent`** (`frontend/src/components/issue/IssueDetailContent.tsx`) — the shared detail content. Props: `{ projectKey: string; issueKey: string }`. Loads the issue via `useIssue(projectKey, issueKey)`, so the full page and the modal read from the same React Query cache entry (`['issues', projectKey, issueKey]`). Rendered directly by `IssueDetailPage` (the full page) and wrapped by `IssueDialog` (the modal).

**`IssueDialog`** (`frontend/src/components/issue/IssueDialog.tsx`) — a hand-rolled modal overlay (no dialog library dependency added; same pattern as `CompleteSprintDialog`). Props: `{ projectKey: string; issueKey: string; onClose: () => void }`. Wraps `IssueDetailContent` in a backdrop and panel, closes on Escape, backdrop click, or the ✕ button, and renders a "Full view" link to the full-page route as an escape hatch.

**`IssueDialogHost`** (`frontend/src/components/issue/IssueDialogHost.tsx`) — mounted once in `AppLayout`. Props: `{ projectKey: string }`. Reads the `issue` search param; when present, renders `IssueDialog` with `onClose` wired to remove only the `issue` param, leaving any other query params (e.g. list filters) untouched.

**`useOpenIssue`** (`frontend/src/hooks/useOpenIssue.ts`) — hook that returns `(issueKey: string) => void`. Calling it sets `?issue=KEY` on the current URL while preserving all other existing query params. Used by backlog rows, issue-list rows, and board cards (`DraggableCard`) to open the modal.

> `DraggableCard` waits until pointer movement is below a 5px threshold before treating a click as "open issue" — this stops a drag gesture from being misread as a click that opens the dialog.

---

## Extension Points

**To add a new shadcn/ui component:**

```bash
npx shadcn@latest add <component-name>
```

The command writes the component to `frontend/src/components/ui/`. Import it as `@/components/ui/<component-name>`. Do not copy-paste the code manually — the CLI wires up the correct variant configuration.

**To add a new domain component:**

1. Create `frontend/src/components/<feature>/<ComponentName>.tsx`.
2. Export a named function: `export function ComponentName(...) { ... }`.
3. Define props as `interface Props { ... }` at the top of the file.
4. Use `cn()` for any conditional Tailwind classes.

---

## Common Pitfalls

- **Do not call `useQuery` or API functions inside presentational components.** Fetch in the page or a designated container component and pass data via props.
- **Do not import from the shadcn npm package directly.** All UI primitives must come from `@/components/ui/`.
- **Do not use `style={{...}}` for values Tailwind can express.** Acceptable exception: `style={{ width: `${pct}%` }}` for dynamic numeric widths on progress bars.
- **Do not use default exports.** Every component file must use a named export so that refactoring tools and the router can locate the export by name.
- **`LabelChip` calls `e.stopPropagation()` in its `onClick`.** This prevents a chip click inside a clickable container (such as the `LabelSelector` trigger area or a settings list row) from firing the parent's handler. If you render `LabelChip` inside a clickable container, be aware that propagation is already stopped — attaching an additional click handler on the chip itself will work, but a handler on a parent element will not fire.
- **`LabelSelector` saves on click-outside, not on a submit button.** The `onSave` callback fires when the user clicks anywhere outside the dropdown. If you render `LabelSelector` inside a form with its own submit handler, you may receive two save events. Render it outside the `<form>` element or suppress the `onSave` call when no labels have changed.

---

## Example

`frontend/src/components/sprint/SprintHeader.tsx` — a purely presentational component that receives a typed `BoardSprintSummary` prop and an action callback:

```typescript
import type { BoardSprintSummary } from '@/types'

interface Props {
  sprint: BoardSprintSummary
  onComplete: () => void
}

export function SprintHeader({ sprint, onComplete }: Props) {
  const pct = sprint.totalPoints > 0
    ? Math.round((sprint.completedPoints / sprint.totalPoints) * 100)
    : 0
  return (
    <div className="mb-6">
      <h1 className="text-xl font-bold text-white">{sprint.name}</h1>
      {sprint.goal && <p className="text-sm text-gray-400">{sprint.goal}</p>}
      <button onClick={onComplete}>Complete Sprint</button>
      {sprint.totalPoints > 0 && <div style={{ width: `${pct}%` }} />}
    </div>
  )
}
```
