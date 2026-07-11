# Project Permissions — Phase B (MembersPage) + Phase C (Read-only Gating) Frontend Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the project Members management UI (list/add/change-role/remove) and enforce the read-only (`VIEWER`) role in the UI by disabling/hiding write controls, on top of the already-shipped Phase A backend.

**Architecture:** Phase A (backend, on this same branch `worktree-project-permissions-phase-a`) exposes member CRUD, `myRole` on the project GET, and `users/search`. Phase B adds a `MembersPage` under project settings plus the API-client/hooks and a one-line migration of the one existing `useProjectMembers` consumer to the new `{user, role}` shape. Phase C adds a `useProjectRole` hook that derives `canWrite` from `project.myRole` and threads it through the write surfaces (issue detail, comments, board drag, backlog, sprints, issue-list create, and the label/version/custom-field settings pages). The backend 403 remains the hard boundary; Phase C is UX only.

**Tech Stack:** React 18 + TypeScript (strict), Vite, `@tanstack/react-query` v5, `react-router-dom` data router, axios (`apiClient`, baseURL `/api/v1`), Tailwind CSS, `@dnd-kit` for the board, lucide-react icons.

## Global Constraints

- **No frontend test framework.** Verification for every task = `cd frontend && npx tsc --noEmit` must exit 0. The root `tsconfig.json` has `strict`, `noUnusedLocals`, and `noUnusedParameters` — **no unused imports/params/vars** or the check fails. Manual browser verification is Wolfgang's, out of scope here.
- **Work in the worktree** `C:\Users\Admin\IdeaProjects\TaskWolf\.claude\worktrees\project-permissions-phase-a` on branch `worktree-project-permissions-phase-a`. All commits stack on the Phase A backend commits.
- **Exact backend contracts (Phase A, verbatim — do not change the backend):**
  - `GET /api/v1/projects/{key}/members` → `ProjectMemberResponse[]` where `ProjectMemberResponse = { user: UserResponse, role: ProjectRole }` and `UserResponse = { id: string, email: string, displayName: string, avatarUrl: string | null, role: string }`. Requires membership (VIEWER may read).
  - `POST /api/v1/projects/{key}/members` body `{ userId: string, role: ProjectRole }` → `ProjectMemberResponse` (201). `409` if already a member, `404` if user unknown. Requires project admin.
  - `PATCH /api/v1/projects/{key}/members/{userId}` body `{ role: ProjectRole }` → `ProjectMemberResponse`. `403` on the owner. Requires project admin.
  - `DELETE /api/v1/projects/{key}/members/{userId}` → 204. `403` on the owner. Requires project admin.
  - `GET /api/v1/projects/{key}` → `Project` now includes `myRole: ProjectRole | null` (owner → `ADMIN`, non-member → `null`).
  - `GET /api/v1/users/search?q={q}` → `UserSearchResponse[]` where `UserSearchResponse = { id: string, email: string, displayName: string }`. Returns `[]` for `q.trim().length < 2`; max 10 active users. `403` if the caller is not a system admin and not admin of any project.
- **`ProjectRole` values:** `'ADMIN' | 'MEMBER' | 'VIEWER'`. UI labels: `VIEWER` → "Read-only", `MEMBER` → "Read & Write", `ADMIN` → "Admin".
- **`canWrite` semantics:** a user may write iff `myRole` is `MEMBER` or `ADMIN` (i.e. `myRole != null && myRole !== 'VIEWER'`). The owner always resolves to `ADMIN` server-side.
- **Loading-state defaults (important for no flicker):** while `myRole` is `undefined` (project query not yet loaded), `canWrite` defaults to **`true`** (never flash-disable a normal user's controls; the backend still guards), but `isAdmin` defaults to **`false`** (never flash an admin-only link/page).

---

## File Structure

**Phase B**
- Modify `frontend/src/types/index.ts` — add `ProjectRole`, `ProjectMember`, `UserSearchResult`; add `myRole?` to `Project`.
- Modify `frontend/src/api/projects.ts` — `members()` returns `ProjectMember[]`; add `addMember`/`updateMemberRole`/`removeMember`.
- Create `frontend/src/api/users.ts` — `usersApi.search(q)`.
- Modify `frontend/src/hooks/useProjectMembers.ts` — typed to `ProjectMember[]`; add `useAddMember`/`useUpdateMemberRole`/`useRemoveMember`.
- Create `frontend/src/hooks/useUserSearch.ts` — debounced-input-friendly search query.
- Modify `frontend/src/components/issue/IssueDetailContent.tsx:114` — pass `members.map(m => m.user)` to `AssigneeSelector` (consumer migration for the new shape).
- Create `frontend/src/pages/projects/settings/MembersPage.tsx` — the management UI.
- Modify `frontend/src/app/router.tsx` — add `/p/:key/settings/members` route.
- Modify `frontend/src/layouts/AppLayout.tsx` — add the "Members" settings nav link, gated on admin.

**Phase C**
- Create `frontend/src/hooks/useProjectRole.ts` — derives `{ myRole, isAdmin, canWrite }`.
- Modify settings pages `LabelsPage.tsx`, `VersionsPage.tsx`, `CustomFieldsPage.tsx` — hide create/edit/delete when `!canWrite`.
- Modify `frontend/src/components/issue/IssueDetailContent.tsx` — thread `canWrite` to inline editors, attachments, and comments.
- Modify `frontend/src/components/comments/CommentThread.tsx` (+ `CommentsActivityTabs.tsx`) — `readOnly` prop hides the add-comment box.
- Modify `frontend/src/pages/board/BoardPage.tsx` + `frontend/src/components/board/DraggableCard.tsx` — disable drag when `!canWrite`.
- Modify `frontend/src/pages/backlog/BacklogPage.tsx` + the sprints page under `frontend/src/pages/sprints/` — hide sprint/assign write controls.
- Modify `frontend/src/pages/issues/IssueListPage.tsx` — hide the create control.

---

## PHASE B — Members management UI

### Task B1: Types, API client, hooks, and consumer migration

This task changes the `members` payload shape, so it must also migrate the one consumer to keep `tsc` green.

**Files:**
- Modify: `frontend/src/types/index.ts:1-16`
- Modify: `frontend/src/api/projects.ts` (whole file)
- Create: `frontend/src/api/users.ts`
- Modify: `frontend/src/hooks/useProjectMembers.ts` (whole file)
- Create: `frontend/src/hooks/useUserSearch.ts`
- Modify: `frontend/src/components/issue/IssueDetailContent.tsx:114`

**Interfaces produced (later tasks rely on these exact names):**
- `type ProjectRole = 'ADMIN' | 'MEMBER' | 'VIEWER'`
- `interface ProjectMember { user: User; role: ProjectRole }`
- `interface UserSearchResult { id: string; email: string; displayName: string }`
- `Project.myRole?: ProjectRole`
- `projectsApi.members(key) => Promise<AxiosResponse<ProjectMember[]>>`
- `projectsApi.addMember(key, { userId, role }) => AxiosResponse<ProjectMember>`
- `projectsApi.updateMemberRole(key, userId, { role }) => AxiosResponse<ProjectMember>`
- `projectsApi.removeMember(key, userId) => AxiosResponse<void>`
- `usersApi.search(q) => AxiosResponse<UserSearchResult[]>`
- Hooks: `useProjectMembers(key)` (data `ProjectMember[]`), `useAddMember(key)`, `useUpdateMemberRole(key)`, `useRemoveMember(key)`, `useUserSearch(q)`

- [ ] **Step 1: Add the types**

In `frontend/src/types/index.ts`, replace the `User`/`Project` block (lines 1-16) with:

```ts
export type ProjectRole = 'ADMIN' | 'MEMBER' | 'VIEWER'

export interface User {
  id: string
  email: string
  displayName: string
  avatarUrl: string | null
  role: 'ADMIN' | 'MEMBER'
}

export interface Project {
  id: string
  key: string
  name: string
  description: string | null
  ownerId: string
  archived: boolean
  myRole?: ProjectRole
}

export interface ProjectMember {
  user: User
  role: ProjectRole
}

export interface UserSearchResult {
  id: string
  email: string
  displayName: string
}
```

- [ ] **Step 2: Rewrite the projects API client**

Replace the whole of `frontend/src/api/projects.ts`:

```ts
import { apiClient } from './client'
import type { Project, ProjectMember, ProjectRole } from '@/types'

export const projectsApi = {
  list: () => apiClient.get<Project[]>('/projects'),
  get: (key: string) => apiClient.get<Project>(`/projects/${key}`),
  create: (data: { key: string; name: string; description?: string }) =>
    apiClient.post<Project>('/projects', data),
  members: (key: string) => apiClient.get<ProjectMember[]>(`/projects/${key}/members`),
  addMember: (key: string, data: { userId: string; role: ProjectRole }) =>
    apiClient.post<ProjectMember>(`/projects/${key}/members`, data),
  updateMemberRole: (key: string, userId: string, data: { role: ProjectRole }) =>
    apiClient.patch<ProjectMember>(`/projects/${key}/members/${userId}`, data),
  removeMember: (key: string, userId: string) =>
    apiClient.delete(`/projects/${key}/members/${userId}`),
}
```

- [ ] **Step 3: Create the users API client**

Create `frontend/src/api/users.ts`:

```ts
import { apiClient } from './client'
import type { UserSearchResult } from '@/types'

export const usersApi = {
  search: (q: string) =>
    apiClient.get<UserSearchResult[]>('/users/search', { params: { q } }),
}
```

- [ ] **Step 4: Extend the members hooks**

Replace the whole of `frontend/src/hooks/useProjectMembers.ts`:

```ts
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { projectsApi } from '@/api/projects'
import type { ProjectRole } from '@/types'

export function useProjectMembers(projectKey: string) {
  return useQuery({
    queryKey: ['members', projectKey],
    queryFn: () => projectsApi.members(projectKey).then(r => r.data),
  })
}

export function useAddMember(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: { userId: string; role: ProjectRole }) =>
      projectsApi.addMember(projectKey, data).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['members', projectKey] }),
  })
}

export function useUpdateMemberRole(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ userId, role }: { userId: string; role: ProjectRole }) =>
      projectsApi.updateMemberRole(projectKey, userId, { role }).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['members', projectKey] }),
  })
}

export function useRemoveMember(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (userId: string) => projectsApi.removeMember(projectKey, userId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['members', projectKey] }),
  })
}
```

- [ ] **Step 5: Create the user-search hook**

Create `frontend/src/hooks/useUserSearch.ts`. The caller passes an already-debounced query; we only hit the network at ≥2 chars (mirrors the backend min length):

```ts
import { useQuery } from '@tanstack/react-query'
import { usersApi } from '@/api/users'

export function useUserSearch(query: string) {
  const q = query.trim()
  return useQuery({
    queryKey: ['user-search', q],
    queryFn: () => usersApi.search(q).then(r => r.data),
    enabled: q.length >= 2,
  })
}
```

- [ ] **Step 6: Migrate the one members consumer**

In `frontend/src/components/issue/IssueDetailContent.tsx`, `members` is now `ProjectMember[]` but `AssigneeSelector` expects `User[]`. At line 114 change:

```tsx
                members={members}
```

to:

```tsx
                members={members.map(m => m.user)}
```

- [ ] **Step 7: Typecheck**

Run: `cd frontend && npx tsc --noEmit`
Expected: exits 0, no errors.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/types/index.ts frontend/src/api/projects.ts frontend/src/api/users.ts frontend/src/hooks/useProjectMembers.ts frontend/src/hooks/useUserSearch.ts frontend/src/components/issue/IssueDetailContent.tsx
git commit -m "feat(members): frontend types, API client, hooks + consumer migration (#9)"
```

---

### Task B2: MembersPage component

**Files:**
- Create: `frontend/src/pages/projects/settings/MembersPage.tsx`

**Interfaces consumed:** `useProject` from `@/hooks/useProjects`; `useProjectMembers`, `useAddMember`, `useUpdateMemberRole`, `useRemoveMember` from `@/hooks/useProjectMembers`; `useUserSearch` from `@/hooks/useUserSearch`; types `ProjectRole`, `ProjectMember`, `UserSearchResult`.

**Interfaces produced:** `export function MembersPage()` (default-less named export, matching `LabelsPage`).

- [ ] **Step 1: Create the page**

Create `frontend/src/pages/projects/settings/MembersPage.tsx`. Models the styling of `LabelsPage`. Owner rows (matched by `member.user.id === project.ownerId`) show an "Owner" badge with role-select and remove disabled. A non-admin who reaches the route directly gets a permission notice (the sidebar link is admin-gated in B3, but the route is still reachable). The add form debounces the search input 300 ms into `debouncedQuery`, feeds `useUserSearch`, shows matches in a dropdown, and posts on submit; 409 → "already a member", other errors → generic.

```tsx
import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { useProject } from '@/hooks/useProjects'
import {
  useProjectMembers,
  useAddMember,
  useUpdateMemberRole,
  useRemoveMember,
} from '@/hooks/useProjectMembers'
import { useUserSearch } from '@/hooks/useUserSearch'
import type { ProjectRole, UserSearchResult } from '@/types'

const ROLE_LABELS: Record<ProjectRole, string> = {
  VIEWER: 'Read-only',
  MEMBER: 'Read & Write',
  ADMIN: 'Admin',
}
const ROLE_OPTIONS: ProjectRole[] = ['VIEWER', 'MEMBER', 'ADMIN']

function AddMemberForm({ projectKey }: { projectKey: string }) {
  const [input, setInput] = useState('')
  const [debounced, setDebounced] = useState('')
  const [selected, setSelected] = useState<UserSearchResult | null>(null)
  const [role, setRole] = useState<ProjectRole>('MEMBER')
  const [error, setError] = useState('')

  const addMember = useAddMember(projectKey)
  const { data: results = [] } = useUserSearch(debounced)

  useEffect(() => {
    const t = setTimeout(() => setDebounced(input), 300)
    return () => clearTimeout(t)
  }, [input])

  const showDropdown = !selected && debounced.trim().length >= 2 && results.length > 0

  async function handleAdd() {
    if (!selected) return
    try {
      await addMember.mutateAsync({ userId: selected.id, role })
      setInput(''); setDebounced(''); setSelected(null); setRole('MEMBER'); setError('')
    } catch (e: unknown) {
      const status = (e as { response?: { status?: number } }).response?.status
      setError(status === 409 ? 'This user is already a member.' : 'Could not add member.')
    }
  }

  return (
    <div className="flex flex-col gap-3 p-4 bg-gray-800 rounded-lg border border-gray-700">
      <div className="relative">
        <label className="block text-xs text-gray-400 mb-1">Add member</label>
        <input
          value={selected ? `${selected.displayName} (${selected.email})` : input}
          onChange={e => { setSelected(null); setInput(e.target.value); setError('') }}
          placeholder="Search by name or email…"
          className="w-full bg-gray-700 border border-gray-600 rounded px-3 py-1.5 text-sm text-white outline-none focus:border-blue-500"
        />
        {showDropdown && (
          <ul className="absolute z-10 mt-1 w-full bg-gray-800 border border-gray-600 rounded shadow-lg max-h-56 overflow-auto">
            {results.map(u => (
              <li key={u.id}>
                <button
                  type="button"
                  onClick={() => { setSelected(u); setInput(''); }}
                  className="w-full text-left px-3 py-2 text-sm text-white hover:bg-gray-700"
                >
                  <span className="font-medium">{u.displayName}</span>
                  <span className="text-gray-400 ml-2">{u.email}</span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
      <div className="flex items-center gap-2">
        <select
          value={role}
          onChange={e => setRole(e.target.value as ProjectRole)}
          className="bg-gray-700 border border-gray-600 rounded px-2 py-1.5 text-sm text-white"
        >
          {ROLE_OPTIONS.map(r => <option key={r} value={r}>{ROLE_LABELS[r]}</option>)}
        </select>
        <button
          type="button"
          onClick={handleAdd}
          disabled={!selected || addMember.isPending}
          className="bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white px-4 py-1.5 rounded text-sm font-medium"
        >
          Add
        </button>
      </div>
      {error && <p className="text-xs text-red-400">{error}</p>}
    </div>
  )
}

export function MembersPage() {
  const { key } = useParams<{ key: string }>()
  const { data: project } = useProject(key!)
  const { data: members = [], isLoading } = useProjectMembers(key!)
  const updateRole = useUpdateMemberRole(key!)
  const removeMember = useRemoveMember(key!)

  if (isLoading || !project) return <div className="text-gray-400 p-6">Loading…</div>

  if (project.myRole !== 'ADMIN') {
    return <div className="p-6 text-gray-400">You don’t have permission to manage members for this project.</div>
  }

  async function handleRemove(userId: string, name: string) {
    if (!confirm(`Remove ${name} from this project?`)) return
    await removeMember.mutateAsync(userId)
  }

  return (
    <div className="p-6 space-y-6 max-w-2xl">
      <h1 className="text-2xl font-semibold">Members</h1>

      <AddMemberForm projectKey={key!} />

      <div className="flex flex-col gap-2">
        {members.map(({ user, role }) => {
          const isOwner = user.id === project.ownerId
          return (
            <div key={user.id} className="flex items-center gap-3 px-4 py-3 bg-gray-900 border border-gray-800 rounded-lg">
              <div className="min-w-0">
                <div className="text-sm text-white truncate">{user.displayName}</div>
                <div className="text-xs text-gray-500 truncate">{user.email}</div>
              </div>
              {isOwner && (
                <span className="text-xs bg-gray-700 text-gray-300 px-2 py-0.5 rounded">Owner</span>
              )}
              <div className="ml-auto flex items-center gap-2">
                <select
                  value={role}
                  disabled={isOwner || updateRole.isPending}
                  onChange={e => updateRole.mutate({ userId: user.id, role: e.target.value as ProjectRole })}
                  className="bg-gray-700 border border-gray-600 rounded px-2 py-1 text-sm text-white disabled:opacity-50"
                >
                  {ROLE_OPTIONS.map(r => <option key={r} value={r}>{ROLE_LABELS[r]}</option>)}
                </select>
                <button
                  onClick={() => handleRemove(user.id, user.displayName)}
                  disabled={isOwner}
                  className="text-xs text-red-400 hover:text-red-300 disabled:opacity-30 disabled:hover:text-red-400 px-2 py-1 rounded hover:bg-gray-700"
                >
                  Remove
                </button>
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Typecheck**

Run: `cd frontend && npx tsc --noEmit`
Expected: exits 0.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/pages/projects/settings/MembersPage.tsx
git commit -m "feat(members): MembersPage with list, role change, remove, add-autocomplete (#9)"
```

---

### Task B3: Route + admin-gated sidebar link

**Files:**
- Modify: `frontend/src/app/router.tsx:36` (imports) and `:93` (route list)
- Modify: `frontend/src/layouts/AppLayout.tsx`

- [ ] **Step 1: Register the route**

In `frontend/src/app/router.tsx`, add the import next to the other settings-page imports (after line 36, the `CustomFieldsPage` import):

```ts
import { MembersPage } from '@/pages/projects/settings/MembersPage'
```

And add the route immediately after the `custom-fields` route (line 93):

```tsx
      { path: '/p/:key/settings/members', element: <MembersPage /> },
```

- [ ] **Step 2: Add the admin-gated nav link**

In `frontend/src/layouts/AppLayout.tsx`:

1. Add `UserCog` to the `lucide-react` import list (line 3-8 block), e.g. append `, UserCog` after `Settings`.
2. Fetch the project role for gating. After the `serviceDeskConfig` query (around line 33), add:

```tsx
  const { data: currentProject } = useQuery({
    queryKey: ['projects', projectKey],
    queryFn: () => projectsApi.get(projectKey!).then(r => r.data),
    enabled: !!projectKey,
  })
```

and add the import at the top: `import { projectsApi } from '@/api/projects'`.

3. In the project **Settings** section, add the Members link as the **first** item (before "API Keys", after line 108 `<div className="flex flex-col gap-1">`), gated on admin — defaulting to hidden while the role is still loading:

```tsx
                  {currentProject?.myRole === 'ADMIN' && (
                    <NavItem to={`/p/${projectKey}/settings/members`} label="Members" icon={UserCog} collapsed={collapsed} variant="sub" />
                  )}
```

- [ ] **Step 3: Typecheck**

Run: `cd frontend && npx tsc --noEmit`
Expected: exits 0.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/router.tsx frontend/src/layouts/AppLayout.tsx
git commit -m "feat(members): route + admin-gated Members sidebar link (#9)"
```

---

## PHASE C — Read-only (VIEWER) UI gating

**The canonical gating pattern** (used by every Phase C task): call `const { canWrite } = useProjectRole(key)` in the page/component that owns the project key, then thread the boolean down. Hide destructive/creative controls (`{canWrite && <button…/>}`) or disable interactive inputs (`disabled={!canWrite || …}`). Never gate read affordances. `canWrite` defaults to `true` while the project query loads, so no control flashes disabled.

### Task C1: `useProjectRole` hook

**Files:**
- Create: `frontend/src/hooks/useProjectRole.ts`

**Interfaces produced:** `useProjectRole(key: string) => { myRole: ProjectRole | undefined; isAdmin: boolean; canWrite: boolean }`.

- [ ] **Step 1: Create the hook**

```ts
import { useProject } from '@/hooks/useProjects'

export function useProjectRole(key: string) {
  const { data: project } = useProject(key)
  const myRole = project?.myRole
  return {
    myRole,
    // Admin-only affordances stay hidden until we positively know the role.
    isAdmin: myRole === 'ADMIN',
    // Write affordances stay enabled until we positively know it's VIEWER
    // (avoids flash-disabling; the backend 403 is the hard boundary).
    canWrite: myRole == null ? true : myRole !== 'VIEWER',
  }
}
```

- [ ] **Step 2: Typecheck**

Run: `cd frontend && npx tsc --noEmit`
Expected: exits 0.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/hooks/useProjectRole.ts
git commit -m "feat(permissions): useProjectRole hook deriving canWrite from myRole (#9)"
```

---

### Task C2: Gate the label/version/custom-field settings pages

These three pages let a `MEMBER`+ manage project content; a `VIEWER` must see them read-only. Hide the create/edit/delete affordances when `!canWrite`.

**Files:**
- Modify: `frontend/src/pages/projects/settings/LabelsPage.tsx`
- Modify: `frontend/src/pages/projects/settings/VersionsPage.tsx`
- Modify: `frontend/src/pages/projects/settings/CustomFieldsPage.tsx`

- [ ] **Step 1: Gate LabelsPage**

In `LabelsPage.tsx`, add `import { useProjectRole } from '@/hooks/useProjectRole'`, and in the component body add `const { canWrite } = useProjectRole(key!)`. Then:
- Wrap the "+ New Label" button (line 120-127) so it renders only when `canWrite`: change `{!showCreate && (` to `{canWrite && !showCreate && (`.
- Wrap the per-row Edit and Delete buttons (line 149-162 `<div className="ml-auto flex gap-2">…</div>`) in `{canWrite && ( … )}`.

- [ ] **Step 2: Gate VersionsPage and CustomFieldsPage the same way**

Open each file, add the `useProjectRole(key!)` call, and gate every create/edit/delete/reorder control (any `<button>` or drag handle that triggers a `useCreate*`/`useUpdate*`/`useDelete*`/`useReorder*` mutation) behind `canWrite` — hide with `{canWrite && …}` for buttons, or `disabled={!canWrite}` for inline inputs/selects. Leave all read/display markup untouched. (Read the file first; apply the canonical pattern to the mutation controls you find.)

- [ ] **Step 3: Typecheck**

Run: `cd frontend && npx tsc --noEmit`
Expected: exits 0. (Watch for `noUnusedLocals` if a page doesn't end up using `canWrite` — every listed page has create controls, so it will.)

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/projects/settings/LabelsPage.tsx frontend/src/pages/projects/settings/VersionsPage.tsx frontend/src/pages/projects/settings/CustomFieldsPage.tsx
git commit -m "feat(permissions): gate label/version/custom-field settings on canWrite (#9)"
```

---

### Task C3: Gate comment box (CommentThread)

**Files:**
- Modify: `frontend/src/components/comments/CommentThread.tsx`
- Modify: `frontend/src/components/comments/CommentsActivityTabs.tsx`

**Interfaces produced:** `CommentThread` gains an optional prop `readOnly?: boolean`.

- [ ] **Step 1: Add the `readOnly` prop and hide the composer**

In `CommentThread.tsx` extend `Props` (line 10-14) with `readOnly?: boolean`, destructure it (line 16) as `{ projectKey, issueKey, currentUserId, readOnly }`, and wrap the add-comment composer block (line 131-146, the `<div className="mt-4 space-y-2">…</div>`) in `{!readOnly && ( … )}`. Leave the author-gated per-comment Edit/Delete as-is (a VIEWER can't have authored any comment).

- [ ] **Step 2: Thread the prop from CommentsActivityTabs**

Read `CommentsActivityTabs.tsx`. It renders `CommentThread` and already receives `projectKey`. Add a `readOnly?: boolean` prop to its `Props`, destructure it, and forward `readOnly={readOnly}` to `<CommentThread … />`. (If it computes anything from role itself, don't — keep it a pass-through; IssueDetailContent supplies the value in C4.)

- [ ] **Step 3: Typecheck**

Run: `cd frontend && npx tsc --noEmit`
Expected: exits 0.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/comments/CommentThread.tsx frontend/src/components/comments/CommentsActivityTabs.tsx
git commit -m "feat(permissions): hide comment composer for read-only members (#9)"
```

---

### Task C4: Gate issue detail (inline editors, attachments, comments)

**Files:**
- Modify: `frontend/src/components/issue/IssueDetailContent.tsx`
- Modify (as needed): the inline editor components under `frontend/src/components/issue/` and `frontend/src/components/attachments/AttachmentPanel.tsx`

- [ ] **Step 1: Derive `canWrite` and make `patch` a no-op when read-only**

In `IssueDetailContent.tsx`, add `import { useProjectRole } from '@/hooks/useProjectRole'` and `const { canWrite } = useProjectRole(projectKey)`. Guard the mutation entry point (line 49-51):

```tsx
  function patch(data: Record<string, unknown>) {
    if (!canWrite) return
    updateIssue.mutate({ id: issue!.id, data })
  }
```

- [ ] **Step 2: Disable the write surfaces**

- Forward read-only to comments/attachments: pass `readOnly={!canWrite}` to the `CommentsActivityTabs` (which forwards to `CommentThread` per C3) and to `AttachmentPanel` (read `AttachmentPanel.tsx`; add a `readOnly?: boolean` prop that hides the upload input and the per-attachment delete buttons — same canonical pattern).
- Disable the inline editors: pass `disabled={!canWrite}` to each editable control rendered here — `InlineEditTitle`, `PrioritySelector`, `TypeSelector`, `AssigneeSelector`, `SprintSelector`, `DueDatePicker`, `LabelSelector`, `VersionSelector`, `StoryPointsSelector`, `RichTextEditor` (description), and `CustomFieldInput`. For any of these components that don't yet accept `disabled`, open the component and add an optional `disabled?: boolean` prop that (a) disables its trigger button/input (`disabled` attribute or guarding the click/open handler) and (b) makes its `onSave`/`onChange` a no-op when set. Keep the displayed value visible — only the editing affordance is suppressed.

- [ ] **Step 3: Typecheck**

Run: `cd frontend && npx tsc --noEmit`
Expected: exits 0.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/issue frontend/src/components/attachments
git commit -m "feat(permissions): gate issue-detail editors, attachments, comments on canWrite (#9)"
```

---

### Task C5: Gate the board (disable drag)

**Files:**
- Modify: `frontend/src/pages/board/BoardPage.tsx`
- Modify: `frontend/src/components/board/BoardColumn.tsx`
- Modify: `frontend/src/components/board/DraggableCard.tsx`

- [ ] **Step 1: Guard the drop handler and thread `canWrite` to cards**

In `BoardPage.tsx` add `import { useProjectRole } from '@/hooks/useProjectRole'` and `const { canWrite } = useProjectRole(key!)` (the page already has the project `key` from `useParams`; confirm the variable name when reading). In `handleDragEnd` (around line 27) early-return when `!canWrite` before `moveIssue.mutate(...)`. Pass `canWrite` down to `<BoardColumn … canWrite={canWrite} />`.

- [ ] **Step 2: Thread through BoardColumn to DraggableCard**

In `BoardColumn.tsx` add a `canWrite: boolean` prop to its `Props`, destructure it, and forward `<DraggableCard key={issue.id} issue={issue} canWrite={canWrite} />` (line 28).

- [ ] **Step 3: Make the card non-draggable when read-only**

In `DraggableCard.tsx` add `canWrite: boolean` to `Props` and destructure it. Only spread the drag props when writable, and drop the grab cursor otherwise. Change the wrapper so that when `!canWrite` the `{...attributes} {...listeners}` are omitted and the class no longer shows `cursor-grab active:cursor-grabbing` (opening the issue via click/Enter still works):

```tsx
      {...(canWrite ? attributes : {})}
      {...(canWrite ? listeners : {})}
```

and change the className grab-cursor segment to be conditional, e.g. replace `'… cursor-grab active:cursor-grabbing select-none'` with `cn('… select-none', canWrite && 'cursor-grab active:cursor-grabbing')`.

- [ ] **Step 4: Typecheck**

Run: `cd frontend && npx tsc --noEmit`
Expected: exits 0.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/board/BoardPage.tsx frontend/src/components/board/BoardColumn.tsx frontend/src/components/board/DraggableCard.tsx
git commit -m "feat(permissions): disable board drag for read-only members (#9)"
```

---

### Task C6: Gate backlog and sprints write controls

**Files:**
- Modify: `frontend/src/pages/backlog/BacklogPage.tsx`
- Modify: the sprints page under `frontend/src/pages/sprints/` (locate with the router entry `/p/:key/sprints`)

- [ ] **Step 1: Gate BacklogPage**

In `BacklogPage.tsx` add `import { useProjectRole } from '@/hooks/useProjectRole'` and `const { canWrite } = useProjectRole(key!)`. Then gate on `canWrite`:
- "+ New Sprint" button (line 50-56): change `{!showCreateForm && (` → `{canWrite && !showCreateForm && (`.
- "Start Sprint" button (line 77-83): wrap in `{canWrite && ( … )}`.
- The per-issue "Add to sprint" `<select>` (line 122-133) and the unassign `✕` button (line 93-99): only build those `action` nodes when `canWrite` — pass `action={canWrite ? (<…/>) : null}` (the `IssueRow` already renders `null` actions cleanly).

- [ ] **Step 2: Gate the sprints page**

Read the sprints page component. Add `useProjectRole(key!)` and gate every control that triggers a create/update/start/complete/assign/unassign mutation behind `canWrite` (hide buttons, `disabled` on inputs), using the canonical pattern. Leave read/display markup untouched.

- [ ] **Step 3: Typecheck**

Run: `cd frontend && npx tsc --noEmit`
Expected: exits 0.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/backlog/BacklogPage.tsx frontend/src/pages/sprints
git commit -m "feat(permissions): gate backlog and sprint write controls on canWrite (#9)"
```

---

### Task C7: Gate the issue-list create control

**Files:**
- Modify: `frontend/src/pages/issues/IssueListPage.tsx`

- [ ] **Step 1: Gate creation**

Read `IssueListPage.tsx` (it holds `useCreateIssue(key!)` at line 31). Add `import { useProjectRole } from '@/hooks/useProjectRole'` and `const { canWrite } = useProjectRole(key!)`. Hide the "new issue" creation control(s) (button and/or inline create form) behind `{canWrite && …}`. Leave the row-open buttons and all filters/read UI untouched.

- [ ] **Step 2: Typecheck**

Run: `cd frontend && npx tsc --noEmit`
Expected: exits 0.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/pages/issues/IssueListPage.tsx
git commit -m "feat(permissions): hide issue creation for read-only members (#9)"
```

---

## Self-Review notes (traceability to the spec)

- **MembersPage** (list, role select, add-autocomplete, remove, owner protection): Tasks B1-B3. Owner row protection via `ownerId` compare (spec "Owner-Zeile: Rolle/Remove disabled, Badge Owner").
- **New GET members `{user, role}` shape + consumer migration:** Task B1 (backing-out the old `User[]` consumer).
- **`users/search` autocomplete (debounced, ≥2 chars, 409 handling):** Tasks B1 + B2. Backend already returns `{ id, email, displayName }` — note the spec draft said `username`; the actual DTO is `displayName`, used here.
- **Route + sidebar link, admin-only:** Task B3 (`myRole === 'ADMIN'`).
- **Read-only gating across issue/board/backlog/sprints/labels/versions/custom-fields/comments:** Tasks C1-C7 via `useProjectRole` → `canWrite`. Backend 403 remains the hard boundary (spec "Das Backend-403 bleibt die harte Grenze").
- **Not gated (correctly):** author-gated comment edit/delete; system/project-admin surfaces (workflow editor, webhooks, integrations, api-keys, audit) which stay admin-gated on the backend and are out of the read/write role's scope.
- **Loading flicker:** `canWrite` defaults true, `isAdmin`/admin-link defaults false (Task C1 + B3) — documented rationale.

> **Note on Phase C granularity:** Phase C gating is a uniform, low-risk UX pattern applied across many read-once-then-gate surfaces. Tasks C2/C4/C6/C7 name the exact controls but instruct the implementer to read each target file and apply the canonical `canWrite` pattern to the mutation controls found, because the precise JSX differs per file. C3 and C5 (drag) carry full concrete edits as the reference pattern.
