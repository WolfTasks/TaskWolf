# B1 — Prevent Self Role-Change — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A project admin can no longer change their *own* project role; role changes must come from a different admin. Enforced server-side and reflected in the Members UI.

**Architecture:** Add a self-target guard in `ProjectService.changeMemberRole` (backend authoritative). In the frontend `MembersPage`, disable the role selector for the current user's own row. Backend is covered by a real-Postgres integration test; frontend by typecheck + manual verification.

**Tech Stack:** Kotlin / Spring Boot backend; JUnit5 + Spring MockMvc integration tests over Testcontainers Postgres (no MockK needed here — these are full HTTP-layer tests). React + TypeScript frontend (no test framework: `tsc` typecheck + manual).

## Global Constraints

- Backend integration tests extend `com.taskowolf.IntegrationTestBase` (spins up `postgres:16-alpine` via Testcontainers; **Docker must be running**).
- Error type for authorization failures: `com.taskowolf.core.infrastructure.ForbiddenException` → maps to HTTP **403** via the global exception handler (see existing `changeMemberRole` owner-guard which already 403s).
- Frontend has **no** test framework — verification is `tsc`/build + manual browser check.
- Spec: `docs/superpowers/specs/2026-07-11-project-permissions-fixes-design.md` (this plan implements the **B1** section only; B2 is a separate frontend cycle).
- Scope: role change only. Do **not** touch `removeMember` (self-removal = leaving a project, intentionally allowed).

---

### Task 1: Backend — forbid changing your own role

**Files:**
- Modify: `backend/src/main/kotlin/com/taskowolf/projects/application/ProjectService.kt` (method `changeMemberRole`, currently lines 105–113)
- Test: `backend/src/test/kotlin/com/taskowolf/projects/ProjectMemberIntegrationTest.kt` (add one `@Test`)

**Interfaces:**
- Consumes: existing `ProjectService.changeMemberRole(projectKey: String, actorId: UUID, targetUserId: UUID, role: ProjectRole): ProjectMember` and `requireAdmin(...)`.
- Produces: same signature; new behavior — throws `ForbiddenException` when `actorId == targetUserId`. No API/DTO changes; route stays `PATCH /api/v1/projects/{key}/members/{userId}`.

- [ ] **Step 1: Write the failing test**

Add this test to `ProjectMemberIntegrationTest.kt` (it reuses the file's existing helpers `registerAndGetToken`, `myId`, `createProject`):

```kotlin
    @Test
    fun `a non-owner admin cannot change their own role but can change others`() {
        val ownerToken = registerAndGetToken("self-owner@test.com")
        val adminToken = registerAndGetToken("self-admin@test.com")
        val otherToken = registerAndGetToken("self-other@test.com")
        val adminId = myId(adminToken)
        val otherId = myId(otherToken)
        createProject(ownerToken, "SELF")

        // owner promotes adminToken's user to ADMIN, and adds a third MEMBER
        mockMvc.perform(
            post("/api/v1/projects/SELF/members").header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId":"$adminId","role":"ADMIN"}""")
        ).andExpect(status().isCreated)
        mockMvc.perform(
            post("/api/v1/projects/SELF/members").header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId":"$otherId","role":"MEMBER"}""")
        ).andExpect(status().isCreated)

        // the admin tries to change THEIR OWN role → 403
        mockMvc.perform(
            patch("/api/v1/projects/SELF/members/$adminId").header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"role":"MEMBER"}""")
        ).andExpect(status().isForbidden)

        // positive control: the admin changes ANOTHER member's role → 200
        mockMvc.perform(
            patch("/api/v1/projects/SELF/members/$otherId").header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"role":"ADMIN"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.role").value("ADMIN"))
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.projects.ProjectMemberIntegrationTest"`
Expected: FAIL — the self-change PATCH returns **200** (current code allows it) instead of the asserted 403.

- [ ] **Step 3: Add the self-target guard**

In `ProjectService.changeMemberRole`, insert the self-check immediately after `requireAdmin` and before the owner-guard. The method becomes:

```kotlin
    @Transactional
    fun changeMemberRole(projectKey: String, actorId: UUID, targetUserId: UUID, role: ProjectRole): ProjectMember {
        val project = requireAdmin(projectKey, actorId)
        if (actorId == targetUserId) throw ForbiddenException("You cannot change your own role")
        if (project.owner.id == targetUserId) throw ForbiddenException("Cannot change the project owner's role")
        val member = memberRepository.findByProjectIdAndUserId(project.id, targetUserId)
            ?: throw NotFoundException("Member not found")
        member.role = role
        return memberRepository.save(member)
    }
```

(`ForbiddenException` is already imported in this file — it's used by `requireMember`/`requireAdmin`.)

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.projects.ProjectMemberIntegrationTest"`
Expected: PASS (all tests in the class, including the existing `admin can add, change role, list and remove a member`).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/projects/application/ProjectService.kt \
        backend/src/test/kotlin/com/taskowolf/projects/ProjectMemberIntegrationTest.kt
git commit -m "fix(#B1): forbid changing your own project role (self privilege change)"
```

---

### Task 2: Frontend — disable the own-row role selector in Members

**Files:**
- Modify: `frontend/src/pages/projects/settings/MembersPage.tsx`

**Interfaces:**
- Consumes: `useMe` from `@/hooks/useAuth` (returns a query whose `data` (`me`) has `id: string`); existing `members` items shaped `{ user: { id, displayName, email }, role }`; existing `project.ownerId`.
- Produces: no exported API change — purely local rendering behavior.

- [ ] **Step 1: Import the current-user hook**

At the top of `MembersPage.tsx`, add the import alongside the existing hook imports:

```tsx
import { useMe } from '@/hooks/useAuth'
```

- [ ] **Step 2: Read the current user in the `MembersPage` component**

Inside `export function MembersPage()`, after the existing `const { data: project } = useProject(key!)` line, add:

```tsx
  const { data: me } = useMe()
```

- [ ] **Step 3: Compute `isSelf` and gate the role selector + add a "You" hint**

In the `members.map(({ user, role }) => { ... })` body, replace the current `const isOwner = ...` line and the `<select>`/badge markup so it reads:

```tsx
          const isOwner = user.id === project.ownerId
          const isSelf = user.id === me?.id
          return (
            <div key={user.id} className="flex items-center gap-3 px-4 py-3 bg-gray-900 border border-gray-800 rounded-lg">
              <div className="min-w-0">
                <div className="text-sm text-white truncate">{user.displayName}</div>
                <div className="text-xs text-gray-500 truncate">{user.email}</div>
              </div>
              {isOwner && (
                <span className="text-xs bg-gray-700 text-gray-300 px-2 py-0.5 rounded">Owner</span>
              )}
              {isSelf && !isOwner && (
                <span className="text-xs bg-gray-700 text-gray-300 px-2 py-0.5 rounded">You</span>
              )}
              <div className="ml-auto flex items-center gap-2">
                <select
                  value={role}
                  disabled={isOwner || isSelf || updateRole.isPending}
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
```

(Only two functional changes vs. today: the `You` badge and `isSelf` added to the `<select>`'s `disabled`. The `Remove` button is intentionally left enabled for self — leaving a project stays allowed per B1 scope.)

- [ ] **Step 4: Typecheck**

Run: `cd frontend && npx tsc --noEmit`
Expected: no errors.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/projects/settings/MembersPage.tsx
git commit -m "fix(#B1): disable own-row role selector on Members page"
```

- [ ] **Step 6: Manual verification (Wolfgang)**

- As a project admin (non-owner), open a project's **Members** page: your own row shows a **You** badge and its role dropdown is disabled; other members' dropdowns remain editable and changing them works.
- Confirm the API rejects a crafted self-change (defense-in-depth): the backend test in Task 1 already asserts 403.

---

## Self-Review

**1. Spec coverage (B1 section):**
- Backend `actorId == targetUserId` → `ForbiddenException` (403): Task 1, Step 3 ✓
- Backend test: non-owner admin self-change → 403, plus positive control (change another member → 200): Task 1, Step 1 ✓
- Frontend own-row `<select>` disabled via `user.id === me.id`, "You" hint: Task 2 ✓
- Self-removal untouched: `removeMember` not modified; `Remove` button left enabled for self ✓

**2. Placeholder scan:** No TBD/TODO; all code shown in full. ✓

**3. Type consistency:** `changeMemberRole(projectKey, actorId, targetUserId, role)` signature unchanged; `useMe()` → `me.id: string` matches `user.id: string` and `project.ownerId: string` comparisons; route `PATCH /api/v1/projects/{key}/members/{userId}` matches `ProjectMemberController`. ✓
