# Frontend Custom Hooks

## Custom Hook Patterns

All custom hooks live in `frontend/src/hooks/`. Naming convention: `use` + noun or noun phrase (`useIssues`, `useProjectMembers`, `useCompleteSprint`).

Every hook wraps `useQuery` (read) or `useMutation` (write) from `@tanstack/react-query`, calling an API function from `frontend/src/api/`. Hooks never call `apiClient` directly — they call the typed function objects exported from the API modules.

**Query hook pattern:**

```typescript
import { useQuery } from '@tanstack/react-query'
import { issuesApi } from '@/api/issues'

export function useIssues(projectKey: string) {
  return useQuery({
    queryKey: ['issues', projectKey],
    queryFn: () => issuesApi.list(projectKey).then(r => r.data),
  })
}

export function useIssue(projectKey: string, issueKey: string) {
  return useQuery({
    queryKey: ['issues', projectKey, issueKey],
    queryFn: () => issuesApi.get(projectKey, issueKey).then(r => r.data),
  })
}
```

**Mutation hook pattern — with cache invalidation:**

```typescript
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { issuesApi } from '@/api/issues'

export function useCreateIssue(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: { title: string; type?: string; priority?: string }) =>
      issuesApi.create(projectKey, data).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['issues', projectKey] }),
  })
}
```

---

## Query Key Conventions

Query keys are always arrays. Project-scoped keys always include `projectKey` as the second element to prevent cross-project cache collisions.

| Key pattern | Hook |
|---|---|
| `['projects']` | `useProjects` |
| `['projects', key]` | `useProject` |
| `['members', projectKey]` | `useProjectMembers` |
| `['labels', projectKey]` | `useLabels`, `useCreateLabel`, `useUpdateLabel`, `useDeleteLabel` |
| `['issues', projectKey]` | `useIssues` |
| `['issues', projectKey, issueKey]` | `useIssue` |
| `['board', projectKey]` | `useBoard` |
| `['backlog', projectKey]` | `useBacklog` |
| `['sprints', projectKey]` | `useSprints` |
| `['comments', projectKey, issueKey]` | `useComments` |
| `['activity', projectKey, issueKey]` | `useActivity` |
| `['attachments', projectKey, issueKey]` | `useAttachments` |
| `['burndown', projectKey, sprintId]` | `useBurndown` |
| `['velocity', projectKey]` | `useVelocity` |
| `['cycleTimeAggregate', projectKey]` | `useCycleTimeAggregate` |
| `['notifications', page]` | `useNotifications` |
| `['notifications', 'unread-count']` | `useUnreadCount` |
| `['workflow-editor', projectKey]` | `useWorkflowEditor` |
| `['automation', projectKey]` | `useAutomationRules` |
| `['automation', 'system']` | `useSystemRules` |
| `['api-keys', projectKey]` | `useApiKeys` |
| `['webhooks', projectKey]` | `useWebhooks` |
| `['webhook-deliveries', projectKey, webhookId]` | `useWebhookDeliveries` |
| `['integrations', projectKey]` | `useProjectIntegrations` |
| `['dashboard', projectKey]` | `useProjectDashboard` |
| `['me']` | `useMe` |
| N/A (mutation-only) | `useDeleteAccount` |
| `['access-tokens']` | `useAccessTokens`, `useCreateAccessToken`, `useRevokeAccessToken` |
| `['admin-users']` | `useAdminUsers`, `useActivateUser`, `useDeactivateUser`, `useDeleteUser` |

---

## Mutation Patterns

Every mutation hook follows the same structure:

1. Acquire `queryClient` via `useQueryClient()`.
2. Call the API function in `mutationFn`.
3. Invalidate the affected query key(s) in `onSuccess`.

**Multi-key invalidation** — when a mutation affects several cached resources, invalidate all of them:

```typescript
export function useStartSprint(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (sprintId: string) => sprintsApi.start(projectKey, sprintId).then(r => r.data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['sprints', projectKey] })
      qc.invalidateQueries({ queryKey: ['board', projectKey] })
      qc.invalidateQueries({ queryKey: ['backlog', projectKey] })
    },
  })
}
```

**No invalidation needed** — `useDeleteAccount` (`frontend/src/hooks/useAccount.ts`) is a mutation with no `onSuccess` cache invalidation, because deleting your own account ends the session (the caller clears `localStorage` tokens and navigates to `/login`); there is no cached data left to keep in sync.

**Conditional enabling** — pass `enabled: !!param` to `useQuery` when a required parameter may be absent:

```typescript
export function useBurndown(projectKey: string, sprintId: string | null) {
  return useQuery({
    queryKey: ['burndown', projectKey, sprintId],
    queryFn: () => reportsApi.burndown(projectKey, sprintId!).then(r => r.data),
    enabled: !!sprintId,
  })
}
```

---

## Optimistic Update Pattern

No hooks currently use `onMutate` / `onError` / `onSettled` for optimistic updates. All mutations use invalidation-on-success, which triggers a background refetch. If you need to add an optimistic update, follow the React Query documentation pattern:

- `onMutate`: snapshot the current cache, apply the optimistic change, return the snapshot as context.
- `onError`: roll back to the snapshot using the returned context.
- `onSettled`: always re-invalidate to sync with the server.

---

## WebSocket Hook

`useProjectSocket(projectKey)` in `frontend/src/hooks/useProjectSocket.ts` is a side-effect-only hook — it returns nothing. It opens a STOMP WebSocket connection over `/ws-stomp` and calls `queryClient.invalidateQueries` when the server pushes `ISSUE_MOVED` or `SPRINT_UPDATED` events. Call it once at the top of any page that needs live board updates.

The hook authenticates by reading `localStorage.getItem('accessToken')` for the STOMP `connectHeaders`. This is the one sanctioned place outside of `apiClient` that reads the token directly.

---

## Extension Points

To add a hook for a new resource:

1. Add API functions to `frontend/src/api/<module>.ts` (or a new file importing `apiClient`).
2. Define the query key as `['resource', projectKey]` — include `projectKey` for any project-scoped data.
3. Create `frontend/src/hooks/useX.ts` exporting `useX` (query) and mutation hooks as needed.
4. Call `qc.invalidateQueries({ queryKey: [...] })` in every mutation's `onSuccess` — always pass a `queryKey` filter, never call `qc.invalidateQueries()` with no arguments.

---

## Common Pitfalls

- **Never call API functions directly in components.** All API access must go through a `useX` hook.
- **Query keys must be arrays, not strings.** Use `['issues', projectKey]`, not `'issues'` or `` `issues-${projectKey}` ``.
- **Always include `projectKey` in project-scoped query keys.** Omitting it causes cross-project cache collisions when the user switches projects without a page reload.
- **Do not call `qc.invalidateQueries()` without a `queryKey` filter.** This invalidates the entire cache and triggers unnecessary refetches across all open queries.

---

## Example: Complete Query + Mutation Pair

`frontend/src/hooks/useComments.ts` — a query hook and a mutation with multi-key invalidation:

```typescript
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { commentsApi } from '@/api/comments'

export function useComments(projectKey: string, issueKey: string) {
  return useQuery({
    queryKey: ['comments', projectKey, issueKey],
    queryFn: () => commentsApi.list(projectKey, issueKey).then(r => r.data),
  })
}

export function useAddComment(projectKey: string, issueKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: string) => commentsApi.create(projectKey, issueKey, body).then(r => r.data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['comments', projectKey, issueKey] })
      qc.invalidateQueries({ queryKey: ['activity', projectKey, issueKey] })
    },
  })
}
```

The file also exports `useDeleteComment` and `useEditComment`, which follow the same mutation pattern with identical `onSuccess` invalidation of `['comments', ...]` and `['activity', ...]`.
