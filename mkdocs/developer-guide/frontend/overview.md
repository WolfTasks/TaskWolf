# Frontend Overview

## Tech Stack

| Library | Role |
|---|---|
| React 19 | UI framework |
| TypeScript 5 | Static typing |
| Vite 5 | Build tool and dev server |
| Tailwind CSS 4 | Utility-first styling (Vite plugin, no `tailwind.config.js`) |
| shadcn/ui | Headless component library — local copies in `frontend/src/components/ui/` |
| `@tanstack/react-query` v5 | Server state: all API data, caching, invalidation |
| React Router v6 | Client-side routing (`createBrowserRouter`) |
| axios | HTTP client; all requests go through `apiClient` in `frontend/src/api/client.ts` |
| `@dnd-kit` | Drag-and-drop for Kanban board columns |
| `react-grid-layout` | Resizable/draggable dashboard widgets |
| TipTap | Rich-text editor for issue descriptions |
| `@stomp/stompjs` | STOMP WebSocket client for live board updates |

---

## Dev Server

```bash
cd frontend && npm run dev   # starts on http://localhost:5173
```

Vite proxies three path prefixes to the backend at `http://localhost:8080` (`frontend/vite.config.ts`):

| Proxy path | Target | Notes |
|---|---|---|
| `/api` | `http://localhost:8080` | All REST calls |
| `/ws` | `ws://localhost:8080` | SockJS WebSocket |
| `/ws-stomp` | `ws://localhost:8080` | Native STOMP WebSocket |

---

## State Management

**Server state — React Query**

All data fetched from the backend is owned by React Query. The shared `QueryClient` is created in `frontend/src/app/queryClient.ts` with global defaults:

- `staleTime: 30_000` — data is treated as fresh for 30 seconds before a background refetch.
- `retry: 1` — failed queries retry once.

Never duplicate server data in local React state. Use `useQuery` via a custom hook in `frontend/src/hooks/`.

**Auth tokens — localStorage**

`accessToken` and `refreshToken` are stored in `localStorage`. The axios request interceptor in `frontend/src/api/client.ts` reads `localStorage.getItem('accessToken')` on every outbound request and injects `Authorization: Bearer <token>`. On a 401 response the interceptor attempts a token refresh; on failure it clears storage and redirects to `/login`.

Do not read tokens from `localStorage` in components or hooks — the `apiClient` instance handles this transparently.

**UI state — React `useState`**

Ephemeral UI state (modal open/closed, form inputs, selected tab) uses React `useState` local to the component. There are no Zustand stores in the current codebase.

---

## Query Keys

All query keys are arrays. Project-scoped keys always include `projectKey` as the second element to prevent cross-project cache collisions.

| Hook | Query Key |
|---|---|
| `useProjects` | `['projects']` |
| `useProject(key)` | `['projects', key]` |
| `useProjectMembers(key)` | `['members', key]` |
| `useIssues(key)` | `['issues', key]` |
| `useIssue(key, issueKey)` | `['issues', key, issueKey]` |
| `useBoard(key)` | `['board', key]` |
| `useBacklog(key)` | `['backlog', key]` |
| `useSprints(key)` | `['sprints', key]` |
| `useComments(key, issueKey)` | `['comments', key, issueKey]` |
| `useActivity(key, issueKey)` | `['activity', key, issueKey]` |
| `useAttachments(key, issueKey)` | `['attachments', key, issueKey]` |
| `useBurndown(key, sprintId)` | `['burndown', key, sprintId]` |
| `useVelocity(key)` | `['velocity', key]` |
| `useCycleTimeAggregate(key)` | `['cycleTimeAggregate', key]` |
| `useNotifications(page)` | `['notifications', page]` |
| `useUnreadCount` | `['notifications', 'unread-count']` |
| `useWorkflowEditor(key)` | `['workflow-editor', key]` |
| `useAutomationRules(key)` | `['automation', key]` |
| `useSystemRules` | `['automation', 'system']` |
| `useApiKeys(key)` | `['api-keys', key]` |
| `useWebhooks(key)` | `['webhooks', key]` |
| `useWebhookDeliveries(key, webhookId)` | `['webhook-deliveries', key, webhookId]` |
| `useProjectIntegrations(key)` | `['integrations', key]` |
| `useProjectDashboard(key)` | `['dashboard', key]` |
| `useMe` | `['me']` |

---

## API Layer

All API modules live in `frontend/src/api/`. Each file imports `apiClient` from `./client` and exports a plain object of typed functions returning `AxiosPromise<T>`.

| File | Resources covered |
|---|---|
| `client.ts` | Axios instance; request interceptor (token injection); response interceptor (token refresh, 401 redirect) |
| `auth.ts` | Register, login, `/auth/me`, logout |
| `projects.ts` | Project CRUD, project members list |
| `issues.ts` | Issue list (paginated), get, create, update (PATCH) |
| `sprints.ts` | Sprint CRUD, start, complete, assign/unassign issues to sprint |
| `board.ts` | Board view, backlog view, move issue to status |
| `comments.ts` | Comment list, activity feed, create, edit, delete |
| `notifications.ts` | Notifications list (paginated), unread count, mark read |
| `attachments.ts` | Attachment list, upload, delete |
| `reports.ts` | Burndown chart data, velocity chart data |
| `workflowEditor.ts` | Workflow status and transition CRUD, canvas layout save |
| `automation.ts` | Project automation rule CRUD, system automation rule CRUD |
| `audit.ts` | Global audit log, project-scoped audit log |
| `organizations.ts` | Organization CRUD |
| `servicedesk.ts` | Service desk config, incident management |
| `sso.ts` | SSO configuration management, public SSO provider list |

---

## Types

All shared TypeScript types are defined in `frontend/src/types/index.ts`.

| Type | Description |
|---|---|
| `User` | Authenticated user: id, email, displayName, avatarUrl, role (`ADMIN` or `MEMBER`) |
| `Project` | Project record: id, key, name, description, ownerId, archived flag |
| `WorkflowStatus` | Status definition: id, name, category (`TODO`/`IN_PROGRESS`/`DONE`), color, position |
| `Issue` | Full issue record: key, title, type, priority, storyPoints, status, assignee, reporter, parentId, dueDate, sprintId, refs |
| `IssueRefResponse` | External issue reference (GitHub PR / GitLab MR): provider, refType, externalId, title, url |
| `AuthResponse` | Login/register response containing accessToken and refreshToken |
| `Page<T>` | Spring Page wrapper: content array, totalElements, totalPages, page number |
| `Sprint` | Sprint record: id, name, goal, status (`PLANNED`/`ACTIVE`/`CLOSED`), dates, planned/completed points |
| `BoardSprintSummary` | Sprint summary for the board header: daysRemaining, totalPoints, completedPoints |
| `BoardColumn` | One board column: status metadata + list of issues |
| `BoardResponse` | Full board payload: active sprint summary + columns array |
| `BacklogSprintEntry` | One sprint in the backlog view: sprint + issue list + totalPoints |
| `BacklogResponse` | Full backlog payload: sprint entries + unscheduled backlog issues |
| `BurndownDay` | Single burndown data point: date, idealPoints, remainingPoints |
| `BurndownResponse` | Burndown chart payload: sprintId + days array |
| `VelocityEntry` | Single sprint velocity: sprintId, sprintName, plannedPoints, completedPoints |
| `VelocityResponse` | Velocity chart payload: entries array |
| `Comment` | Comment record: id, issueId, authorId, body, editedAt, deleted flag |
| `ActivityType` | Union of all audit event type strings that appear in the issue activity feed |
| `ActivityItem` | Activity feed entry: actorId, type, oldValue, newValue |
| `Notification` | Notification record: type (`COMMENT_MENTION`/`ISSUE_ASSIGNED`/`AUTOMATION`), title, body, link, read flag |
| `Attachment` | Attachment record: id, issueId, uploaderId, filename, contentType, size |
| `TransitionGuard` | Workflow guard rule: type (`REQUIRED_FIELD`/`ROLE_RESTRICTION`), optional field and roles |
| `WorkflowTransition` | Transition edge: id, fromStatusId (null = any source), toStatusId, guards (JSON string) |
| `StatusPosition` | Canvas layout coordinate: statusId + x/y |
| `WorkflowEditorData` | Full workflow editor payload: id, name, statuses, transitions, layout |
| `TriggerType` | Union of automation trigger event name strings |
| `ConditionType` | Union of automation condition field names |
| `ActionType` | Union of automation action type strings |
| `GroupLogic` | `'AND'` or `'OR'` |
| `RuleCondition` | Single automation condition: type, operator, params record |
| `RuleConditionGroup` | Recursive condition group: logic, conditions, childGroups |
| `RuleAction` | Single automation action: position, type, params record |
| `AutomationRule` | Persisted automation rule: id, name, triggerType, scope, enabled |
| `AutomationRuleDraft` | Create/update payload for automation rules |

---

## Extension Points

**To add a new API resource:**

1. Add a typed function to the relevant `frontend/src/api/<module>.ts`, or create a new file that imports `apiClient`.
2. Define a query key constant as an array: `['resource', projectKey]`.
3. Create `frontend/src/hooks/useX.ts` exporting `useX` (query) and mutation hooks (`useCreateX`, `useUpdateX`, `useDeleteX`) as needed.

**To add a new shared type:**

Add the interface or type alias to `frontend/src/types/index.ts` and import it as `import type { X } from '@/types'`.

---

## Common Pitfalls

- **Never fetch data in components.** Call `useX` hooks from the page or container component; pass data as props to presentational children.
- **Never use `useState` for server data.** Data returned by any API endpoint belongs in React Query (`useQuery`), not `useState`.
- **Never read auth tokens from `localStorage` in components or hooks.** The `apiClient` interceptor handles token injection. Bypassing it defeats the automatic refresh logic.
- **Never omit `projectKey` from project-scoped query keys.** Omitting it causes cross-project cache collisions when the user switches projects without a page reload.

---

## Example: Minimal Hook + Component Pair

```typescript
// frontend/src/hooks/useProjects.ts
import { useQuery } from '@tanstack/react-query'
import { projectsApi } from '@/api/projects'

export function useProjects() {
  return useQuery({
    queryKey: ['projects'],
    queryFn: () => projectsApi.list().then(r => r.data),
  })
}
```

```typescript
// frontend/src/pages/projects/ProjectListPage.tsx
import { useProjects } from '@/hooks/useProjects'

export function ProjectListPage() {
  const { data: projects = [], isLoading } = useProjects()
  if (isLoading) return <div className="text-gray-400">Loading...</div>
  return (
    <ul className="flex flex-col gap-2">
      {projects.map(p => (
        <li key={p.id} className="text-white text-sm">{p.name}</li>
      ))}
    </ul>
  )
}
```
