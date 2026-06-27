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
