# Frontend Pages

## Page Structure

Pages live in `frontend/src/pages/` organised by feature domain:

```
frontend/src/pages/
  auth/                       # LoginPage, RegisterPage
  dashboard/                  # DashboardPage (global)
  projects/                   # ProjectListPage, ProjectCreatePage
  projects/settings/          # ProjectAuditPage, LabelsPage
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
| Project settings | API Keys, Webhooks, Integrations, Audit Log, Labels (/p/:key/settings/labels) — shown as a sub-section inside the project section |
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

`frontend/src/pages/notifications/NotificationsPage.tsx` — a minimal page showing the standard hook-call + loading guard + render pattern:

```typescript
// frontend/src/pages/notifications/NotificationsPage.tsx
import { useNotifications, useMarkRead } from '@/hooks/useNotifications'
export function NotificationsPage() {
  const { data, isLoading } = useNotifications()
  const markRead = useMarkRead()
  const notifications = data?.content ?? []
  if (isLoading) return <div className="text-gray-500">Loading...</div>
  return (
    <div className="max-w-2xl">
      <h1 className="text-2xl font-bold text-white mb-6">Notifications</h1>
      {notifications.map(n => (
        <div key={n.id} onClick={() => !n.read && markRead.mutate(n.id)}>
          <p className="text-sm font-medium">{n.title}</p>
        </div>
      ))}
    </div>
  )
}
```
