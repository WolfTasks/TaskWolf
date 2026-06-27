# Frontend Pages

## Page Structure

Pages live in `frontend/src/pages/` organised by feature domain:

```
frontend/src/pages/
  auth/                       # LoginPage, RegisterPage
  dashboard/                  # DashboardPage (global)
  projects/                   # ProjectListPage, ProjectCreatePage
  projects/settings/          # ProjectAuditPage
  projects/servicedesk/       # ServiceDeskPage, IncidentDashboardPage
  issues/                     # IssueListPage, IssueDetailPage
  board/                      # BoardPage
  backlog/                    # BacklogPage
  project-dashboard/          # ProjectDashboardPage (per-project widget canvas)
  reports/                    # ReportsPage
  notifications/              # NotificationsPage
  settings/                   # WorkflowEditorPage, ApiKeysPage, WebhooksPage, IntegrationsPage
  automation/                 # AutomationPage, AutomationRuleEditorPage
  admin/                      # AdminAutomationPage, AuditLogPage, SsoSettingsPage
  orgs/                       # OrgsPage, OrgSettingsPage
```

**File naming:** one page component per file with a `Page` suffix (`IssueListPage.tsx` exports `IssueListPage`). Named files, not `index.tsx`.

**Exports:** Named exports only — no default exports (exception: `AuditLogPage` and `ProjectAuditPage` currently use default exports; new pages must use named exports).

**Pattern inside a page:**
1. Call `useParams` to extract route parameters (e.g., `key`, `issueKey`, `rid`).
2. Call `useX` hooks for data.
3. Render child components, passing data as props.
4. Keep all loading states (`if (isLoading) return ...`) at the top of the render, before JSX.

---

## AppLayout

`frontend/src/layouts/AppLayout.tsx` provides the full authenticated shell:

| Slot | Details |
|---|---|
| Left sidebar (`<aside>`) | Fixed 56-wide (`w-56`); dark background (`bg-gray-900`); full-height flex column |
| Top-level nav | Dashboard (`/`), Projects (`/projects`), Organizations (`/orgs`) |
| Admin section | Audit Log (`/admin/audit`), Automation (`/admin/automation`); always visible |
| Project section | Visible only when the URL matches `/p/:key/*`; detected via `useMatch('/p/:key/*')` |
| Project nav links | Dashboard, Board, Backlog, Issues, Reports, Automation; conditionally adds Service Desk and Incidents when `serviceDeskConfig.enabled` is true |
| Project settings | API Keys, Webhooks, Integrations, Audit Log — shown as a sub-section inside the project section |
| Footer | `OrgSwitcher`, `NotificationBell`, Logout button |
| Main content (`<main>`) | `<Outlet />` renders the matched child route; `flex-1 overflow-auto p-8` |

`AuthLayout` (`frontend/src/layouts/AuthLayout.tsx`) is the unauthenticated shell: centered card on a dark background, no sidebar. Used by `LoginPage` and `RegisterPage`.

---

## Route Guards

The router in `frontend/src/app/router.tsx` uses a `RequireAuth` wrapper component:

```typescript
const isAuthenticated = () => !!localStorage.getItem('accessToken')

function RequireAuth({ children }: { children: React.ReactNode }) {
  return isAuthenticated() ? <>{children}</> : <Navigate to="/login" replace />
}
```

All protected routes are nested inside `<RequireAuth><AppLayout /></RequireAuth>`. Auth routes (`/login`, `/register`) are nested inside `AuthLayout` with no guard — there is no automatic redirect to `/` for already-authenticated users visiting the login page.

---

## Adding a New Route

**Step 1 — Create the page file:**

```
frontend/src/pages/<feature>/<FeatureName>Page.tsx
```

Export a named function component:

```typescript
export function FeatureNamePage() {
  return <div>...</div>
}
```

**Step 2 — Register the route in `frontend/src/app/router.tsx`:**

Import the page at the top of the file, then add an entry to the children array of the `RequireAuth` layout object:

```typescript
import { FeatureNamePage } from '@/pages/feature/FeatureNamePage'

// Inside the RequireAuth children array:
{ path: '/p/:key/feature', element: <FeatureNamePage /> },
```

**Step 3 — Add a nav link in `frontend/src/layouts/AppLayout.tsx`:**

For a project-scoped link, place it inside the `{insideProject && projectKey && (...)}` block using `subNavLinkClass`:

```typescript
<NavLink to={`/p/${projectKey}/feature`} className={subNavLinkClass}>
  Feature Name
</NavLink>
```

For a global link, add a `<NavLink>` to the top-level `<nav>` block using `navLinkClass`.

---

## Common Pitfalls

- **Do not call API functions directly in page components.** Use `useX` hooks — they provide caching, loading/error state, and automatic revalidation.
- **Do not use default exports for page components.** The router uses named imports; mixing export styles creates inconsistency and can cause hot-module replacement issues.
- **Do not fetch data inside `AppLayout`.** The service desk config query in `AppLayout` is a pre-existing exception for conditional nav rendering. New layout-level queries should be avoided; the layout should be stateless except for routing metadata.

---

## Example

`frontend/src/pages/board/BoardPage.tsx` — a representative page that composes hooks, a WebSocket side-effect, drag-and-drop, and child components:

```typescript
import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { DndContext, DragEndEvent, PointerSensor, useSensor, useSensors } from '@dnd-kit/core'
import { useBoard, useMoveIssue } from '@/hooks/useBoard'
import { useCompleteSprint } from '@/hooks/useSprints'
import { useProjectSocket } from '@/hooks/useProjectSocket'
import { BoardColumn } from '@/components/board/BoardColumn'
import { SprintHeader } from '@/components/sprint/SprintHeader'
import { CompleteSprintDialog } from '@/components/sprint/CompleteSprintDialog'

export function BoardPage() {
  const { key } = useParams<{ key: string }>()
  useProjectSocket(key!)
  const { data: board, isLoading } = useBoard(key!)
  const moveIssue = useMoveIssue(key!)
  const completeSprint = useCompleteSprint(key!)
  const [showComplete, setShowComplete] = useState(false)
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 5 } }))

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event
    if (!over) return
    const issueId = active.id as string
    const newStatusId = over.id as string
    const currentStatusId = board?.columns.find(c => c.issues.some(i => i.id === issueId))?.status.id
    if (currentStatusId !== newStatusId) moveIssue.mutate({ issueId, newStatusId })
  }

  if (isLoading) return <div className="text-gray-400">Loading...</div>
  if (!board) return <p className="text-gray-400">No active sprint.</p>

  return (
    <div>
      <SprintHeader sprint={board.sprint} onComplete={() => setShowComplete(true)} />
      <DndContext sensors={sensors} onDragEnd={handleDragEnd}>
        <div className="flex gap-4 overflow-x-auto pb-4">
          {board.columns.map(col => <BoardColumn key={col.status.id} column={col} />)}
        </div>
      </DndContext>
      {showComplete && (
        <CompleteSprintDialog
          sprintName={board.sprint.name}
          openIssueCount={0}
          loading={completeSprint.isPending}
          onCancel={() => setShowComplete(false)}
          onConfirm={() => completeSprint.mutate(board.sprint.id, { onSuccess: () => setShowComplete(false) })}
        />
      )}
    </div>
  )
}
```
