# Organizations Umbrella — Phase B (Frontend) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give org-admins a real UI to manage their organization's members/roles, assign projects to organizations, and make Phase A's permission inheritance visible — consuming Phase A endpoints only, no new backend.

**Architecture:** React + TanStack Query SPA. Reuse the #9 project-members patterns (`useUserSearch` autocomplete, owner/self-protected role editor, `myRole` gating). Add one new project-settings page for org assignment; uplift the existing `OrgSettingsPage`; open `OrgsPage` to non-system-admins; add an inheritance info banner to the project members page.

**Tech Stack:** TypeScript, React, react-router-dom, @tanstack/react-query, axios (`apiClient`), lucide-react icons, Tailwind CSS.

## Global Constraints

- **Base branch:** work in a git worktree branched from `worktree-org-umbrella-phase-a` (NOT `main`) — the Phase A backend is unmerged (PR #55) and only that branch serves the new endpoints. Set up via `superpowers:using-git-worktrees` at execution time.
- **No test framework on the frontend.** The per-task "test" gate is `cd frontend && npx tsc --noEmit` (must exit 0) plus the manual check listed in the task. There is no vitest/jest.
- **No backend changes.** Consume only the endpoints listed below. Do not modify anything under `backend/`.
- **Follow existing patterns verbatim:** Tailwind class style, `apiClient` wrapper, `queryKey` conventions, `@/` path alias.
- **Every code step shows the full code.** Frequent commits — one per task.

### Phase A endpoint contract (ground truth)

```
GET    /organizations/mine                      -> Organization[]        (any authed user)
GET    /organizations                           -> Organization[]        (system ADMIN)
GET    /organizations/{id}                       -> Organization          (member or system ADMIN)
GET    /organizations/{id}/members               -> OrganizationMember[]  ({user, role})
POST   /organizations/{id}/members  {userId,role}-> OrganizationMember    (org OWNER/ADMIN or system ADMIN)
PATCH  /organizations/{id}/members/{userId} {role}-> OrganizationMember   (org OWNER/ADMIN or system ADMIN)
DELETE /organizations/{id}/members/{userId}      -> 204                   (org OWNER/ADMIN or system ADMIN)
PATCH  /projects/{key}/organization  {orgId|null}-> Project               (set: proj-ADMIN & org-OWNER/ADMIN; clear: proj-ADMIN; system ADMIN both)
```

Confirmed TypeScript-relevant shapes:
```
Organization = { id: string; name: string; slug: string }
User         = { id: string; email: string; displayName: string; avatarUrl: string | null; role: 'ADMIN' | 'MEMBER' }
OrgRole      = 'OWNER' | 'ADMIN' | 'MEMBER'
OrganizationMember = { user: User; role: OrgRole }     // NEW shape (was {orgId,userId,role})
```

---

## File Structure

- `frontend/src/types/index.ts` — add `orgId` to `Project`; add `OrgRole` type.
- `frontend/src/api/projects.ts` — add `setOrganization(key, orgId)`.
- `frontend/src/api/organizations.ts` — change `OrganizationMember` shape; add `changeMemberRole`; type methods with `OrgRole`.
- `frontend/src/hooks/useOrganizations.ts` — **new**: org list + member CRUD hooks (mirror `useProjectMembers`).
- `frontend/src/pages/projects/settings/OrganizationSettingsPage.tsx` — **new**: project→org assignment.
- `frontend/src/pages/projects/settings/MembersPage.tsx` — add inheritance banner.
- `frontend/src/pages/orgs/OrgSettingsPage.tsx` — rewrite to MembersPage-level.
- `frontend/src/pages/orgs/OrgsPage.tsx` — open to non-system-admins.
- `frontend/src/app/router.tsx` — add `/p/:key/settings/organization` route.
- `frontend/src/layouts/AppLayout.tsx` — add "Organization" nav item under project Settings.

**Task order & dependencies:** Task 1 (additive project API) → Tasks 2, 3 (project-side, depend on Task 1, independent of each other) → Task 4 (org api shape change is breaking → bundles the `OrgSettingsPage` rewrite) → Task 5 (`OrgsPage`) → Task 6 (final verification). Each task leaves `tsc --noEmit` green.

---

### Task 1: Project↔Org API method + `orgId` on Project type

Additive, non-breaking. Foundation for Tasks 2 and 3.

**Files:**
- Modify: `frontend/src/types/index.ts`
- Modify: `frontend/src/api/projects.ts`

**Interfaces:**
- Consumes: existing `apiClient`, `Project`, `ProjectRole`.
- Produces: `Project.orgId: string | null`; `OrgRole` type; `projectsApi.setOrganization(key: string, orgId: string | null) => Promise<AxiosResponse<Project>>`.

- [ ] **Step 1: Add `orgId` to `Project` and the `OrgRole` type**

In `frontend/src/types/index.ts`, add `orgId` to the `Project` interface and a new `OrgRole` type near `ProjectRole`:

```typescript
export type ProjectRole = 'ADMIN' | 'MEMBER' | 'VIEWER'
export type OrgRole = 'OWNER' | 'ADMIN' | 'MEMBER'
```

Change the `Project` interface to include `orgId`:

```typescript
export interface Project {
  id: string
  key: string
  name: string
  description: string | null
  ownerId: string
  archived: boolean
  orgId: string | null
  myRole?: ProjectRole
}
```

- [ ] **Step 2: Add `setOrganization` to the projects API**

In `frontend/src/api/projects.ts`, add the method inside the `projectsApi` object (after `removeMember`):

```typescript
  setOrganization: (key: string, orgId: string | null) =>
    apiClient.patch<Project>(`/projects/${key}/organization`, { orgId }),
```

- [ ] **Step 3: Typecheck**

Run: `cd frontend && npx tsc --noEmit`
Expected: exit 0, no errors. (`orgId` is optional at call sites elsewhere because responses now simply carry it; no consumer breaks.)

- [ ] **Step 4: Commit**

```bash
git add frontend/src/types/index.ts frontend/src/api/projects.ts
git commit -m "feat(orgs): project↔org assignment API + orgId on Project type (#14)"
```

---

### Task 2: Organization project-settings page (route + nav + page)

New admin-only page to assign/unassign a project's organization.

**Files:**
- Create: `frontend/src/pages/projects/settings/OrganizationSettingsPage.tsx`
- Modify: `frontend/src/app/router.tsx`
- Modify: `frontend/src/layouts/AppLayout.tsx`

**Interfaces:**
- Consumes: `projectsApi.setOrganization` (Task 1), `Project.orgId` (Task 1), existing `organizationsApi.listMine()` / `getById()`, `useProject(key)`, `useQueryClient`.
- Produces: route `/p/:key/settings/organization`; component `OrganizationSettingsPage`.

- [ ] **Step 1: Create the page**

Create `frontend/src/pages/projects/settings/OrganizationSettingsPage.tsx`:

```tsx
import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useProject } from '@/hooks/useProjects'
import { organizationsApi } from '@/api/organizations'
import { projectsApi } from '@/api/projects'

export function OrganizationSettingsPage() {
  const { key } = useParams<{ key: string }>()
  const qc = useQueryClient()
  const { data: project, isLoading } = useProject(key!)
  const [selected, setSelected] = useState('')
  const [error, setError] = useState('')

  const { data: myOrgs = [] } = useQuery({
    queryKey: ['organizations', 'mine'],
    queryFn: () => organizationsApi.listMine().then(r => r.data),
  })

  const { data: currentOrg } = useQuery({
    queryKey: ['org', project?.orgId],
    queryFn: () => organizationsApi.getById(project!.orgId!).then(r => r.data),
    enabled: !!project?.orgId,
  })

  const setOrg = useMutation({
    mutationFn: (orgId: string | null) => projectsApi.setOrganization(key!, orgId).then(r => r.data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['projects', key] }) // useProject(key) — refreshes project.orgId + myRole
      qc.invalidateQueries({ queryKey: ['projects'] })       // project list
      setSelected(''); setError('')
    },
    onError: (e: unknown) => {
      const status = (e as { response?: { status?: number } }).response?.status
      setError(status === 403
        ? 'You must be an owner or admin of the target organization to assign this project to it.'
        : 'Could not update the organization.')
    },
  })

  if (isLoading || !project) return <div className="p-6 text-gray-400">Loading…</div>
  if (project.myRole !== 'ADMIN') {
    return <div className="p-6 text-gray-400">You don’t have permission to manage this project’s organization.</div>
  }

  return (
    <div className="p-6 space-y-6 max-w-2xl">
      <h1 className="text-2xl font-semibold">Organization</h1>

      <div className="p-4 bg-gray-900 border border-gray-800 rounded-lg space-y-1">
        <div className="text-xs text-gray-400">Current organization</div>
        <div className="text-sm text-white">
          {project.orgId ? (currentOrg?.name ?? '…') : 'Not assigned'}
        </div>
      </div>

      {error && <p className="text-sm text-red-400">{error}</p>}

      <div className="flex flex-col gap-3 p-4 bg-gray-800 rounded-lg border border-gray-700">
        <label className="block text-xs text-gray-400">Assign to organization</label>
        <div className="flex items-center gap-2">
          <select
            value={selected}
            onChange={e => { setSelected(e.target.value); setError('') }}
            className="bg-gray-700 border border-gray-600 rounded px-2 py-1.5 text-sm text-white flex-1"
          >
            <option value="">Select an organization…</option>
            {myOrgs.map(o => <option key={o.id} value={o.id}>{o.name}</option>)}
          </select>
          <button
            type="button"
            onClick={() => selected && setOrg.mutate(selected)}
            disabled={!selected || setOrg.isPending}
            className="bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white px-4 py-1.5 rounded text-sm font-medium"
          >
            Assign
          </button>
        </div>
        {project.orgId && (
          <button
            type="button"
            onClick={() => setOrg.mutate(null)}
            disabled={setOrg.isPending}
            className="self-start text-xs text-red-400 hover:text-red-300 disabled:opacity-50"
          >
            Remove from organization
          </button>
        )}
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Register the route**

In `frontend/src/app/router.tsx`, add the import near the other settings-page imports:

```typescript
import { OrganizationSettingsPage } from '@/pages/projects/settings/OrganizationSettingsPage'
```

Add the route next to the other `/p/:key/settings/*` routes (after the `members` route):

```typescript
      { path: '/p/:key/settings/organization', element: <OrganizationSettingsPage /> },
```

- [ ] **Step 3: Add the nav item**

In `frontend/src/layouts/AppLayout.tsx`, inside the project-settings `SidebarSection` (the block containing the "Members" NavItem), add an admin-gated item right after the Members item:

```tsx
                  {currentProject?.myRole === 'ADMIN' && (
                    <NavItem to={`/p/${projectKey}/settings/organization`} label="Organization" icon={Building2} collapsed={collapsed} variant="sub" />
                  )}
```

(`Building2` is already imported in this file.)

- [ ] **Step 4: Typecheck**

Run: `cd frontend && npx tsc --noEmit`
Expected: exit 0.

- [ ] **Step 5: Manual verification**

Run the stack (Phase A backend from the worktree + `cd frontend && npm run dev`). As a project ADMIN, open a project → Settings → **Organization**. Assign the project to an org you own/admin → "Current organization" updates. Remove → back to "Not assigned". Pick an org where you are only a MEMBER → inline 403 message appears.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pages/projects/settings/OrganizationSettingsPage.tsx frontend/src/app/router.tsx frontend/src/layouts/AppLayout.tsx
git commit -m "feat(orgs): project→organization assignment settings page (#14)"
```

---

### Task 3: Inheritance banner on the project Members page

Explains why org members have access even though they aren't in the explicit member list.

**Files:**
- Modify: `frontend/src/pages/projects/settings/MembersPage.tsx`

**Interfaces:**
- Consumes: `project.orgId` (Task 1), existing `organizationsApi.getById`, existing `useProject`.
- Produces: none (leaf UI).

- [ ] **Step 1: Add the org-name query and banner**

In `frontend/src/pages/projects/settings/MembersPage.tsx`, add these imports at the top:

```typescript
import { useQuery } from '@tanstack/react-query'
import { organizationsApi } from '@/api/organizations'
```

Inside the `MembersPage` component, after the existing hooks (e.g. after `const removeMember = useRemoveMember(key!)`), add:

```typescript
  const { data: org } = useQuery({
    queryKey: ['org', project?.orgId],
    queryFn: () => organizationsApi.getById(project!.orgId!).then(r => r.data),
    enabled: !!project?.orgId,
  })
```

In the returned JSX, add the banner immediately above `<AddMemberForm projectKey={key!} />`:

```tsx
      {project.orgId && (
        <div className="p-4 bg-blue-950/40 border border-blue-900 rounded-lg text-sm text-blue-200">
          This project belongs to <span className="font-medium">{org?.name ?? 'an organization'}</span>.
          Its owners and admins are admins here; its members are viewers. Those people have access
          without appearing in the list below.
        </div>
      )}
```

- [ ] **Step 2: Typecheck**

Run: `cd frontend && npx tsc --noEmit`
Expected: exit 0.

- [ ] **Step 3: Manual verification**

Open Settings → Members for a project that IS assigned to an org → banner shows the org name. Open Members for an unassigned project → no banner.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/projects/settings/MembersPage.tsx
git commit -m "feat(orgs): inheritance banner on project members page (#14)"
```

---

### Task 4: Org member API shape + hooks + `OrgSettingsPage` uplift

The `OrganizationMember` shape change is breaking (only `OrgSettingsPage` consumes it), so the API change and the page rewrite ship together to keep `tsc` green.

**Files:**
- Modify: `frontend/src/api/organizations.ts`
- Create: `frontend/src/hooks/useOrganizations.ts`
- Modify (rewrite): `frontend/src/pages/orgs/OrgSettingsPage.tsx`

**Interfaces:**
- Consumes: existing `apiClient`, `useUserSearch`, `useMe`, `User`, `OrgRole` (Task 1).
- Produces: `OrganizationMember = { user: User; role: OrgRole }`; `organizationsApi.changeMemberRole(orgId, userId, role)`; hooks `useOrgMembers`, `useAddOrgMember`, `useChangeOrgMemberRole`, `useRemoveOrgMember`.

- [ ] **Step 1: Update the organizations API client**

Rewrite `frontend/src/api/organizations.ts` to the new shape and add `changeMemberRole`:

```typescript
import { apiClient } from './client'
import type { User, OrgRole } from '@/types'

export interface Organization {
  id: string
  name: string
  slug: string
}

export interface OrganizationMember {
  user: User
  role: OrgRole
}

export interface CreateOrganizationRequest {
  name: string
  slug: string
}

export interface AddMemberRequest {
  userId: string
  role: OrgRole
}

export const organizationsApi = {
  listAll: () => apiClient.get<Organization[]>('/organizations'),
  listMine: () => apiClient.get<Organization[]>('/organizations/mine'),
  getById: (id: string) => apiClient.get<Organization>(`/organizations/${id}`),
  create: (data: CreateOrganizationRequest) =>
    apiClient.post<Organization>('/organizations', data),

  listMembers: (orgId: string) =>
    apiClient.get<OrganizationMember[]>(`/organizations/${orgId}/members`),
  addMember: (orgId: string, data: AddMemberRequest) =>
    apiClient.post<OrganizationMember>(`/organizations/${orgId}/members`, data),
  changeMemberRole: (orgId: string, userId: string, role: OrgRole) =>
    apiClient.patch<OrganizationMember>(`/organizations/${orgId}/members/${userId}`, { role }),
  removeMember: (orgId: string, userId: string) =>
    apiClient.delete(`/organizations/${orgId}/members/${userId}`),

  switchOrg: (orgId: string) =>
    apiClient.post<{ accessToken: string }>(`/auth/switch-org/${orgId}`),
}
```

- [ ] **Step 2: Create org member hooks**

Create `frontend/src/hooks/useOrganizations.ts` (mirrors `useProjectMembers`):

```typescript
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { organizationsApi, AddMemberRequest } from '@/api/organizations'
import type { OrgRole } from '@/types'

export function useOrgMembers(orgId: string) {
  return useQuery({
    queryKey: ['org-members', orgId],
    queryFn: () => organizationsApi.listMembers(orgId).then(r => r.data),
    enabled: !!orgId,
  })
}

export function useAddOrgMember(orgId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: AddMemberRequest) => organizationsApi.addMember(orgId, data).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['org-members', orgId] }),
  })
}

export function useChangeOrgMemberRole(orgId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ userId, role }: { userId: string; role: OrgRole }) =>
      organizationsApi.changeMemberRole(orgId, userId, role).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['org-members', orgId] }),
  })
}

export function useRemoveOrgMember(orgId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (userId: string) => organizationsApi.removeMember(orgId, userId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['org-members', orgId] }),
  })
}
```

- [ ] **Step 3: Rewrite `OrgSettingsPage` to MembersPage-level**

Rewrite `frontend/src/pages/orgs/OrgSettingsPage.tsx`. Member add via user search; per-row role editor; self/owner guards; viewer-role gating for read-only. `OrgRole` order for OWNER-option gating: only OWNER or system-ADMIN viewers may grant OWNER.

```tsx
import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { organizationsApi } from '@/api/organizations'
import { useOrgMembers, useAddOrgMember, useChangeOrgMemberRole, useRemoveOrgMember } from '@/hooks/useOrganizations'
import { useUserSearch } from '@/hooks/useUserSearch'
import { useMe } from '@/hooks/useAuth'
import type { OrgRole, UserSearchResult } from '@/types'

const ROLE_LABELS: Record<OrgRole, string> = { MEMBER: 'Member', ADMIN: 'Admin', OWNER: 'Owner' }

function AddOrgMemberForm({ orgId, canGrantOwner }: { orgId: string; canGrantOwner: boolean }) {
  const [input, setInput] = useState('')
  const [debounced, setDebounced] = useState('')
  const [selected, setSelected] = useState<UserSearchResult | null>(null)
  const [role, setRole] = useState<OrgRole>('MEMBER')
  const [error, setError] = useState('')

  const addMember = useAddOrgMember(orgId)
  const { data: results = [] } = useUserSearch(debounced)

  useEffect(() => {
    const t = setTimeout(() => setDebounced(input), 300)
    return () => clearTimeout(t)
  }, [input])

  const showDropdown = !selected && debounced.trim().length >= 2 && results.length > 0
  const roleOptions: OrgRole[] = canGrantOwner ? ['MEMBER', 'ADMIN', 'OWNER'] : ['MEMBER', 'ADMIN']

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
                  onClick={() => { setSelected(u); setInput('') }}
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
          onChange={e => setRole(e.target.value as OrgRole)}
          className="bg-gray-700 border border-gray-600 rounded px-2 py-1.5 text-sm text-white"
        >
          {roleOptions.map(r => <option key={r} value={r}>{ROLE_LABELS[r]}</option>)}
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

export function OrgSettingsPage() {
  const { orgId } = useParams<{ orgId: string }>()
  const { data: me } = useMe()
  const { data: org, isLoading: orgLoading } = useQuery({
    queryKey: ['org', orgId],
    queryFn: () => organizationsApi.getById(orgId!).then(r => r.data),
    enabled: !!orgId,
  })
  const { data: members = [], isLoading: membersLoading } = useOrgMembers(orgId!)
  const changeRole = useChangeOrgMemberRole(orgId!)
  const removeMember = useRemoveOrgMember(orgId!)

  const isSystemAdmin = me?.role === 'ADMIN'
  const myRole = members.find(m => m.user.id === me?.id)?.role
  const canManage = isSystemAdmin || myRole === 'OWNER' || myRole === 'ADMIN'
  const canGrantOwner = isSystemAdmin || myRole === 'OWNER'
  const roleOptions: OrgRole[] = canGrantOwner ? ['MEMBER', 'ADMIN', 'OWNER'] : ['MEMBER', 'ADMIN']

  if (orgLoading) return <div className="p-6 text-gray-400">Loading…</div>
  if (!org) return <div className="p-6 text-red-400">Organization not found.</div>

  async function handleRemove(userId: string, name: string) {
    if (!confirm(`Remove ${name} from ${org!.name}?`)) return
    await removeMember.mutateAsync(userId)
  }

  return (
    <div className="p-6 max-w-2xl mx-auto space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">{org.name} — Settings</h1>
        <p className="text-gray-400 text-sm">Slug: {org.slug}</p>
      </div>

      {canManage && <AddOrgMemberForm orgId={orgId!} canGrantOwner={canGrantOwner} />}

      <div className="flex flex-col gap-2">
        <h2 className="text-lg font-medium">Members</h2>
        {membersLoading && <p className="text-gray-400 text-sm">Loading…</p>}
        {!membersLoading && members.length === 0 && (
          <p className="text-gray-500 text-sm">No members found.</p>
        )}
        {members.map(({ user, role }) => {
          const isSelf = user.id === me?.id
          const isOwner = role === 'OWNER'
          // Only system admins may touch OWNER rows; nobody may edit their own row.
          const lockRole = isSelf || (isOwner && !isSystemAdmin) || !canManage
          const lockRemove = isSelf || (isOwner && !isSystemAdmin) || !canManage
          return (
            <div key={user.id} className="flex items-center gap-3 px-4 py-3 bg-gray-900 border border-gray-800 rounded-lg">
              <div className="min-w-0">
                <div className="text-sm text-white truncate">{user.displayName}</div>
                <div className="text-xs text-gray-500 truncate">{user.email}</div>
              </div>
              {isOwner && <span className="text-xs bg-gray-700 text-gray-300 px-2 py-0.5 rounded">Owner</span>}
              {isSelf && !isOwner && <span className="text-xs bg-gray-700 text-gray-300 px-2 py-0.5 rounded">You</span>}
              <div className="ml-auto flex items-center gap-2">
                <select
                  value={role}
                  disabled={lockRole || changeRole.isPending}
                  onChange={e => changeRole.mutate({ userId: user.id, role: e.target.value as OrgRole })}
                  className="bg-gray-700 border border-gray-600 rounded px-2 py-1 text-sm text-white disabled:opacity-50"
                >
                  {/* Ensure the current role renders even if outside the actor's grantable set. */}
                  {(roleOptions.includes(role) ? roleOptions : [role, ...roleOptions]).map(r => (
                    <option key={r} value={r}>{ROLE_LABELS[r]}</option>
                  ))}
                </select>
                {canManage && (
                  <button
                    onClick={() => handleRemove(user.id, user.displayName)}
                    disabled={lockRemove}
                    className="text-xs text-red-400 hover:text-red-300 disabled:opacity-30 px-2 py-1 rounded hover:bg-gray-700"
                  >
                    Remove
                  </button>
                )}
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
```

- [ ] **Step 4: Typecheck**

Run: `cd frontend && npx tsc --noEmit`
Expected: exit 0. (Confirms the breaking shape change is fully absorbed — `OrgSettingsPage` was the only consumer.)

- [ ] **Step 5: Manual verification**

As a non-system **org OWNER**: open `/orgs/{id}/settings` → add a member via search, change a member's role, remove a member. Verify your own row's select + Remove are disabled. As a non-system **org ADMIN**: OWNER rows' select/Remove are disabled and the add-form role dropdown has no "Owner" option. As a plain **org MEMBER**: no add form, all selects/Remove hidden/disabled (read-only).

- [ ] **Step 6: Commit**

```bash
git add frontend/src/api/organizations.ts frontend/src/hooks/useOrganizations.ts frontend/src/pages/orgs/OrgSettingsPage.tsx
git commit -m "feat(orgs): org member management uplift with role editor + guards (#14)"
```

---

### Task 5: Open `OrgsPage` to non-system-admins

"My Organizations" for everyone; system admins additionally see Create + "All Organizations".

**Files:**
- Modify: `frontend/src/pages/orgs/OrgsPage.tsx`

**Interfaces:**
- Consumes: existing `organizationsApi.listMine()` / `listAll()` / `create()`, `useMe`.
- Produces: none (leaf page).

- [ ] **Step 1: Rewrite `OrgsPage`**

Rewrite `frontend/src/pages/orgs/OrgsPage.tsx`:

```tsx
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { organizationsApi, CreateOrganizationRequest, Organization } from '@/api/organizations'
import { useMe } from '@/hooks/useAuth'

const emptyForm: CreateOrganizationRequest = { name: '', slug: '' }

function OrgList({ orgs, empty }: { orgs: Organization[]; empty: string }) {
  if (orgs.length === 0) return <p className="text-gray-500 text-sm">{empty}</p>
  return (
    <>
      {orgs.map(org => (
        <div key={org.id} className="bg-gray-900 border border-gray-800 rounded-lg p-4 flex items-center justify-between">
          <div>
            <div className="font-medium text-sm">{org.name}</div>
            <div className="text-xs text-gray-400">{org.slug}</div>
          </div>
          <a href={`/orgs/${org.id}/settings`} className="text-blue-400 hover:text-blue-300 text-sm">Settings</a>
        </div>
      ))}
    </>
  )
}

export function OrgsPage() {
  const queryClient = useQueryClient()
  const [form, setForm] = useState<CreateOrganizationRequest>(emptyForm)
  const [formError, setFormError] = useState('')
  const { data: me } = useMe()
  const isAdmin = me?.role === 'ADMIN'

  const { data: myOrgs = [], isLoading: mineLoading } = useQuery({
    queryKey: ['organizations', 'mine'],
    queryFn: () => organizationsApi.listMine().then(r => r.data),
  })

  const { data: allOrgs = [], isLoading: allLoading } = useQuery({
    queryKey: ['organizations'],
    queryFn: () => organizationsApi.listAll().then(r => r.data),
    enabled: isAdmin,
  })

  const createMutation = useMutation({
    mutationFn: (data: CreateOrganizationRequest) => organizationsApi.create(data).then(r => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['organizations'] })
      queryClient.invalidateQueries({ queryKey: ['organizations', 'mine'] })
      setForm(emptyForm); setFormError('')
    },
    onError: () => setFormError('Failed to create organization.'),
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    setFormError('')
    if (!form.name || !form.slug) { setFormError('Name and slug are required.'); return }
    createMutation.mutate(form)
  }

  return (
    <div className="p-6 max-w-3xl mx-auto space-y-8">
      <h1 className="text-2xl font-semibold">Organizations</h1>

      <section className="space-y-3">
        <h2 className="text-lg font-medium">My Organizations</h2>
        {mineLoading && <p className="text-gray-400 text-sm">Loading…</p>}
        {!mineLoading && <OrgList orgs={myOrgs} empty="You are not a member of any organization yet." />}
      </section>

      {isAdmin && (
        <>
          <section className="space-y-4">
            <h2 className="text-lg font-medium">Create Organization</h2>
            <form onSubmit={handleSubmit} className="space-y-3">
              {formError && <p className="text-red-400 text-sm">{formError}</p>}
              <div className="flex flex-col gap-1">
                <label className="text-sm text-gray-400">Name</label>
                <input
                  type="text"
                  value={form.name}
                  onChange={e => setForm(f => ({ ...f, name: e.target.value }))}
                  placeholder="e.g. Acme Corp"
                  className="bg-gray-800 border border-gray-700 rounded px-3 py-2 text-white text-sm"
                />
              </div>
              <div className="flex flex-col gap-1">
                <label className="text-sm text-gray-400">Slug</label>
                <input
                  type="text"
                  value={form.slug}
                  onChange={e => setForm(f => ({ ...f, slug: e.target.value.toLowerCase().replace(/[^a-z0-9-]/g, '-') }))}
                  placeholder="e.g. acme-corp"
                  className="bg-gray-800 border border-gray-700 rounded px-3 py-2 text-white text-sm"
                />
              </div>
              <button
                type="submit"
                disabled={createMutation.isPending}
                className="bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white rounded px-4 py-2 text-sm font-medium"
              >
                {createMutation.isPending ? 'Creating…' : 'Create Organization'}
              </button>
            </form>
          </section>

          <section className="space-y-3">
            <h2 className="text-lg font-medium">All Organizations</h2>
            {allLoading && <p className="text-gray-400 text-sm">Loading…</p>}
            {!allLoading && <OrgList orgs={allOrgs} empty="No organizations yet." />}
          </section>
        </>
      )}
    </div>
  )
}
```

- [ ] **Step 2: Typecheck**

Run: `cd frontend && npx tsc --noEmit`
Expected: exit 0.

- [ ] **Step 3: Manual verification**

As a **non-system-admin** who is a member of at least one org: open `/orgs` → "My Organizations" lists them, no Create form, no "All Organizations". Click Settings → reaches `OrgSettingsPage`. As a **system admin**: all three sections present; creating an org refreshes both lists.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/orgs/OrgsPage.tsx
git commit -m "feat(orgs): OrgsPage shows my orgs for all users, admin sees all + create (#14)"
```

---

### Task 6: Final verification pass (whole-feature)

No code — confirm the full feature end-to-end against the spec's §5 matrix.

**Files:** none.

- [ ] **Step 1: Full typecheck**

Run: `cd frontend && npx tsc --noEmit`
Expected: exit 0.

- [ ] **Step 2: Manual matrix** (stack running: Phase A backend worktree + `npm run dev`)

Verify each row:
1. Non-system org-admin: `/orgs` → My Organizations → open settings → add member (search), change role, remove; self/owner guards visible.
2. Plain org member: `OrgSettingsPage` is read-only (no add form, disabled controls).
3. Project ADMIN: Settings → Organization → assign then remove an org.
4. Assign to an org where you are not org-admin → inline 403 message.
5. Inherited VIEWER (member of an org, on one of its projects, not an explicit project member): opens the project, sees it read-only (write affordances disabled), and the Members page shows the inheritance banner.
6. System ADMIN: `/orgs` shows Create + All Organizations; org create refreshes lists.

- [ ] **Step 3: Confirm no backend drift**

Run: `git status` — only files under `frontend/` and `docs/` changed; nothing under `backend/`.

- [ ] **Step 4: Final commit (if any docs/wiki updates were made)**

```bash
git add -A
git commit -m "docs(orgs): phase B wiki — org management + permission inheritance (#14)"
```

---

## Wiki Docs (final task)

Per project convention, add user-facing wiki docs describing organization management and what org membership means for project permissions (owners/admins → project admins, members → viewers). Only touch `ai-guide.md` if patterns/Flyway change (they don't here). Fold this into Task 6's final commit or a dedicated docs commit.
```
