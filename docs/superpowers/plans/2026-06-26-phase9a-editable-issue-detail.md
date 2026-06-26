# Phase 9a — Editable Issue Detail Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `IssueDetailPage` fully editable — click-to-edit title, priority, type, assignee, due date, sprint, and a TipTap rich-text description editor.

**Architecture:** Four sequential slices: (1) backend DTO + service extension with no migration, (2) frontend display wiring for new fields, (3) click-to-edit sidebar components, (4) TipTap rich-text editor for description. Each slice is independently testable and committable.

**Tech Stack:** Kotlin/Spring Boot (backend), React 19 + TypeScript + Tailwind CSS 4 + Radix UI + TipTap (frontend), MockK (backend tests), npm (frontend package manager in `frontend/` directory).

## Global Constraints

- No Flyway migration — all fields already exist in the `issues` DB table
- Backend test framework: MockK (not Mockito) — all mocks use `mockk<T>()`, stubs use `every { }`, verifications use `verify { }`
- Run backend tests from: `backend/` directory with `./gradlew test`
- Run frontend dev server from: `frontend/` directory with `npm run dev`
- Backend package root: `com.taskowolf`
- Frontend path alias `@/` maps to `frontend/src/`
- All PATCH calls send only the changed field(s); React Query prefix invalidation on `['issues', projectKey]` refreshes both list and detail views

---

## File Map

**Backend (Task 1):**
- Modify: `backend/src/main/kotlin/com/taskowolf/issues/api/dto/IssueResponse.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/issues/api/dto/UpdateIssueRequest.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/issues/application/IssueService.kt`
- Modify: `backend/src/test/kotlin/com/taskowolf/issues/IssueServiceTest.kt`

**Frontend data layer (Task 2):**
- Modify: `frontend/src/types/index.ts` — add seven fields to `Issue`
- Modify: `frontend/src/api/projects.ts` — add `members` endpoint
- Create: `frontend/src/hooks/useProjectMembers.ts`
- Modify: `frontend/src/pages/issues/IssueDetailPage.tsx` — display all fields read-only

**Frontend editing (Task 3):**
- Create: `frontend/src/components/issue/InlineEditTitle.tsx`
- Create: `frontend/src/components/issue/PrioritySelector.tsx`
- Create: `frontend/src/components/issue/TypeSelector.tsx`
- Create: `frontend/src/components/issue/AssigneeSelector.tsx`
- Create: `frontend/src/components/issue/SprintSelector.tsx`
- Create: `frontend/src/components/issue/DueDatePicker.tsx`
- Modify: `frontend/src/pages/issues/IssueDetailPage.tsx` — replace display-only with editing components

**Frontend rich text (Task 4):**
- Create: `frontend/src/components/issue/RichTextEditor.tsx`
- Modify: `frontend/src/pages/issues/IssueDetailPage.tsx` — replace static description with RichTextEditor

---

### Task 1: Backend — Extend DTOs and IssueService

**Files:**
- Modify: `backend/src/main/kotlin/com/taskowolf/issues/api/dto/IssueResponse.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/issues/api/dto/UpdateIssueRequest.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/issues/application/IssueService.kt`
- Modify: `backend/src/test/kotlin/com/taskowolf/issues/IssueServiceTest.kt`

**Interfaces:**
- Produces: `IssueResponse` with `dueDate`, `sprintId`, `sprintName`, `assigneeName`, `reporterName`, `createdAt`, `updatedAt`; `UpdateIssueRequest` with `type`, `dueDate`, `clearDueDate`, `sprintId`, `clearSprint`, `clearAssignee`

- [ ] **Step 1: Write failing tests for the new update behaviours**

Add these tests to `backend/src/test/kotlin/com/taskowolf/issues/IssueServiceTest.kt`. First add `sprintRepository` mock and update the service instantiation — the existing instantiation line `IssueService(issueRepository, projectService, workflowService, userRepository, eventPublisher)` must become the six-argument form shown below.

```kotlin
// Add at top of IssueServiceTest class (alongside existing mocks):
private val sprintRepository = mockk<com.taskowolf.sprints.infrastructure.SprintRepository>()
// Replace the existing `private val service = ...` line with:
private val service = IssueService(issueRepository, projectService, workflowService, userRepository, eventPublisher, sprintRepository)

// Add these test methods inside the class:

@Test
fun `update sets type when type provided`() {
    every { projectService.requireMember("WOLF", owner.id) } returns project
    every { issueRepository.findById(issue.id) } returns java.util.Optional.of(issue)
    every { issueRepository.save(any()) } returnsArgument 0

    val updated = service.update("WOLF", issue.id,
        com.taskowolf.issues.api.dto.UpdateIssueRequest(type = com.taskowolf.issues.domain.IssueType.BUG),
        owner)

    assert(updated.type == com.taskowolf.issues.domain.IssueType.BUG)
}

@Test
fun `update sets dueDate when provided`() {
    val date = java.time.LocalDate.of(2026, 12, 31)
    every { projectService.requireMember("WOLF", owner.id) } returns project
    every { issueRepository.findById(issue.id) } returns java.util.Optional.of(issue)
    every { issueRepository.save(any()) } returnsArgument 0

    val updated = service.update("WOLF", issue.id,
        com.taskowolf.issues.api.dto.UpdateIssueRequest(dueDate = date),
        owner)

    assert(updated.dueDate == date)
}

@Test
fun `update clears dueDate when clearDueDate is true`() {
    issue.dueDate = java.time.LocalDate.of(2026, 1, 1)
    every { projectService.requireMember("WOLF", owner.id) } returns project
    every { issueRepository.findById(issue.id) } returns java.util.Optional.of(issue)
    every { issueRepository.save(any()) } returnsArgument 0

    val updated = service.update("WOLF", issue.id,
        com.taskowolf.issues.api.dto.UpdateIssueRequest(clearDueDate = true),
        owner)

    assert(updated.dueDate == null)
}

@Test
fun `update unassigns when clearAssignee is true`() {
    val assignee = User(email = "dev@test.com", displayName = "Dev")
    issue.assignee = assignee
    every { projectService.requireMember("WOLF", owner.id) } returns project
    every { issueRepository.findById(issue.id) } returns java.util.Optional.of(issue)
    every { issueRepository.save(any()) } returnsArgument 0

    val updated = service.update("WOLF", issue.id,
        com.taskowolf.issues.api.dto.UpdateIssueRequest(clearAssignee = true),
        owner)

    assert(updated.assignee == null)
}

@Test
fun `update assigns sprint when sprintId provided`() {
    val sprint = com.taskowolf.sprints.domain.Sprint(
        name = "Sprint 1", project = project,
        status = com.taskowolf.sprints.domain.SprintStatus.ACTIVE
    )
    every { projectService.requireMember("WOLF", owner.id) } returns project
    every { issueRepository.findById(issue.id) } returns java.util.Optional.of(issue)
    every { sprintRepository.findById(sprint.id) } returns java.util.Optional.of(sprint)
    every { issueRepository.save(any()) } returnsArgument 0

    val updated = service.update("WOLF", issue.id,
        com.taskowolf.issues.api.dto.UpdateIssueRequest(sprintId = sprint.id),
        owner)

    assert(updated.sprint?.id == sprint.id)
}

@Test
fun `update clears sprint when clearSprint is true`() {
    val sprint = com.taskowolf.sprints.domain.Sprint(
        name = "Sprint 1", project = project,
        status = com.taskowolf.sprints.domain.SprintStatus.ACTIVE
    )
    issue.sprint = sprint
    every { projectService.requireMember("WOLF", owner.id) } returns project
    every { issueRepository.findById(issue.id) } returns java.util.Optional.of(issue)
    every { issueRepository.save(any()) } returnsArgument 0

    val updated = service.update("WOLF", issue.id,
        com.taskowolf.issues.api.dto.UpdateIssueRequest(clearSprint = true),
        owner)

    assert(updated.sprint == null)
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```
cd backend
./gradlew test --tests "com.taskowolf.issues.IssueServiceTest"
```

Expected: compilation failure — `IssueService` does not accept six arguments yet.

- [ ] **Step 3: Replace `IssueResponse.kt` with the extended version**

```kotlin
package com.taskowolf.issues.api.dto

import com.taskowolf.integrations.api.dto.IssueRefResponse
import com.taskowolf.issues.domain.Issue
import com.taskowolf.issues.domain.IssuePriority
import com.taskowolf.issues.domain.IssueType
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class IssueResponse(
    val id: UUID,
    val key: String,
    val title: String,
    val description: String?,
    val type: IssueType,
    val priority: IssuePriority,
    val storyPoints: Int?,
    val statusId: UUID,
    val statusName: String,
    val statusCategory: String,
    val projectId: UUID,
    val assigneeId: UUID?,
    val assigneeName: String?,
    val reporterId: UUID,
    val reporterName: String,
    val parentId: UUID?,
    val dueDate: LocalDate?,
    val sprintId: UUID?,
    val sprintName: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val refs: List<IssueRefResponse> = emptyList()
) {
    companion object {
        fun from(i: Issue, refs: List<IssueRefResponse> = emptyList()) = IssueResponse(
            id = i.id,
            key = i.key,
            title = i.title,
            description = i.description,
            type = i.type,
            priority = i.priority,
            storyPoints = i.storyPoints,
            statusId = i.status.id,
            statusName = i.status.name,
            statusCategory = i.status.category.name,
            projectId = i.project.id,
            assigneeId = i.assignee?.id,
            assigneeName = i.assignee?.displayName,
            reporterId = i.reporter.id,
            reporterName = i.reporter.displayName,
            parentId = i.parent?.id,
            dueDate = i.dueDate,
            sprintId = i.sprint?.id,
            sprintName = i.sprint?.name,
            createdAt = i.createdAt ?: Instant.now(),
            updatedAt = i.updatedAt,
            refs = refs
        )
    }
}
```

- [ ] **Step 4: Replace `UpdateIssueRequest.kt` with the extended version**

```kotlin
package com.taskowolf.issues.api.dto

import com.taskowolf.issues.domain.IssuePriority
import com.taskowolf.issues.domain.IssueType
import java.time.LocalDate
import java.util.UUID

data class UpdateIssueRequest(
    val title: String? = null,
    val description: String? = null,
    val statusId: UUID? = null,
    val assigneeId: UUID? = null,
    val clearAssignee: Boolean = false,
    val priority: IssuePriority? = null,
    val storyPoints: Int? = null,
    val type: IssueType? = null,
    val dueDate: LocalDate? = null,
    val clearDueDate: Boolean = false,
    val sprintId: UUID? = null,
    val clearSprint: Boolean = false
)
```

- [ ] **Step 5: Update `IssueService.kt`**

Add `SprintRepository` to the constructor and new update branches. Apply these changes:

**5a — Add import and constructor parameter.** Add this import after the existing imports:
```kotlin
import com.taskowolf.sprints.infrastructure.SprintRepository
```

Change the constructor from:
```kotlin
class IssueService(
    private val issueRepository: IssueRepository,
    private val projectService: ProjectService,
    private val workflowService: WorkflowService,
    private val userRepository: UserRepository,
    private val eventPublisher: DomainEventPublisher
)
```
to:
```kotlin
class IssueService(
    private val issueRepository: IssueRepository,
    private val projectService: ProjectService,
    private val workflowService: WorkflowService,
    private val userRepository: UserRepository,
    private val eventPublisher: DomainEventPublisher,
    private val sprintRepository: SprintRepository
)
```

**5b — Replace the assigneeId block and add new blocks.** In `update()`, replace:
```kotlin
        request.assigneeId?.let { assigneeId ->
            val newAssignee = resolveAssignee(assigneeId, project)
            if (issue.assignee?.id != newAssignee.id) {
                val old = issue.assignee?.displayName
                issue.assignee = newAssignee
                eventPublisher.publish(IssueFieldChangedEvent(issue, currentUser, "assignee", old, newAssignee.displayName))
            }
        }

        request.statusId?.let { newStatusId ->
```
with:
```kotlin
        if (request.clearAssignee) {
            val old = issue.assignee?.displayName
            issue.assignee = null
            if (old != null) eventPublisher.publish(IssueFieldChangedEvent(issue, currentUser, "assignee", old, null))
        } else {
            request.assigneeId?.let { assigneeId ->
                val newAssignee = resolveAssignee(assigneeId, project)
                if (issue.assignee?.id != newAssignee.id) {
                    val old = issue.assignee?.displayName
                    issue.assignee = newAssignee
                    eventPublisher.publish(IssueFieldChangedEvent(issue, currentUser, "assignee", old, newAssignee.displayName))
                }
            }
        }

        request.type?.let { newType ->
            if (issue.type != newType) {
                val old = issue.type.name
                issue.type = newType
                eventPublisher.publish(IssueFieldChangedEvent(issue, currentUser, "type", old, newType.name))
            }
        }

        when {
            request.clearDueDate -> {
                val old = issue.dueDate?.toString()
                issue.dueDate = null
                if (old != null) eventPublisher.publish(IssueFieldChangedEvent(issue, currentUser, "dueDate", old, null))
            }
            request.dueDate != null -> {
                val old = issue.dueDate?.toString()
                if (old != request.dueDate.toString()) {
                    issue.dueDate = request.dueDate
                    eventPublisher.publish(IssueFieldChangedEvent(issue, currentUser, "dueDate", old, request.dueDate.toString()))
                }
            }
        }

        when {
            request.clearSprint -> {
                val old = issue.sprint?.name
                issue.sprint = null
                if (old != null) eventPublisher.publish(IssueFieldChangedEvent(issue, currentUser, "sprint", old, null))
            }
            request.sprintId != null -> {
                val newSprint = sprintRepository.findById(request.sprintId)
                    .filter { it.project.id == project.id }
                    .orElseThrow { NotFoundException("Sprint not found: ${request.sprintId}") }
                if (issue.sprint?.id != newSprint.id) {
                    val old = issue.sprint?.name
                    issue.sprint = newSprint
                    eventPublisher.publish(IssueFieldChangedEvent(issue, currentUser, "sprint", old, newSprint.name))
                }
            }
        }

        request.statusId?.let { newStatusId ->
```

- [ ] **Step 6: Run tests — they should now pass**

```
cd backend
./gradlew test --tests "com.taskowolf.issues.IssueServiceTest"
```

Expected: all tests in `IssueServiceTest` pass, including the six new ones.

- [ ] **Step 7: Commit**

```
git add backend/src/main/kotlin/com/taskowolf/issues/api/dto/IssueResponse.kt
git add backend/src/main/kotlin/com/taskowolf/issues/api/dto/UpdateIssueRequest.kt
git add backend/src/main/kotlin/com/taskowolf/issues/application/IssueService.kt
git add backend/src/test/kotlin/com/taskowolf/issues/IssueServiceTest.kt
git commit -m "feat(issues): extend IssueResponse and UpdateIssueRequest with editable fields"
```

---

### Task 2: Frontend — Extend Issue Type and Display All Fields

**Files:**
- Modify: `frontend/src/types/index.ts`
- Modify: `frontend/src/api/projects.ts`
- Create: `frontend/src/hooks/useProjectMembers.ts`
- Modify: `frontend/src/pages/issues/IssueDetailPage.tsx`

**Interfaces:**
- Consumes: `IssueResponse` shape from Task 1 (fields: `dueDate`, `sprintId`, `sprintName`, `assigneeName`, `reporterName`, `createdAt`, `updatedAt`)
- Produces: `Issue` TypeScript type with new fields; `useProjectMembers(projectKey)` hook returning `User[]`

- [ ] **Step 1: Add seven fields to the `Issue` interface in `frontend/src/types/index.ts`**

Replace the existing `Issue` interface:
```ts
export interface Issue {
  id: string
  key: string
  title: string
  description: string | null
  type: 'EPIC' | 'STORY' | 'BUG' | 'TASK' | 'SUBTASK'
  priority: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW'
  storyPoints: number | null
  statusId: string
  statusName: string
  statusCategory: 'TODO' | 'IN_PROGRESS' | 'DONE'
  projectId: string
  assigneeId: string | null
  reporterId: string
  parentId: string | null
  refs?: IssueRefResponse[]
}
```
with:
```ts
export interface Issue {
  id: string
  key: string
  title: string
  description: string | null
  type: 'EPIC' | 'STORY' | 'BUG' | 'TASK' | 'SUBTASK'
  priority: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW'
  storyPoints: number | null
  statusId: string
  statusName: string
  statusCategory: 'TODO' | 'IN_PROGRESS' | 'DONE'
  projectId: string
  assigneeId: string | null
  assigneeName: string | null
  reporterId: string
  reporterName: string
  parentId: string | null
  dueDate: string | null
  sprintId: string | null
  sprintName: string | null
  createdAt: string
  updatedAt: string
  refs?: IssueRefResponse[]
}
```

- [ ] **Step 2: Add `members` to `projectsApi` in `frontend/src/api/projects.ts`**

Replace the file content:
```ts
import { apiClient } from './client'
import type { Project, User } from '@/types'

export const projectsApi = {
  list: () => apiClient.get<Project[]>('/projects'),
  get: (key: string) => apiClient.get<Project>(`/projects/${key}`),
  create: (data: { key: string; name: string; description?: string }) =>
    apiClient.post<Project>('/projects', data),
  members: (key: string) => apiClient.get<User[]>(`/projects/${key}/members`),
}
```

- [ ] **Step 3: Create `frontend/src/hooks/useProjectMembers.ts`**

```ts
import { useQuery } from '@tanstack/react-query'
import { projectsApi } from '@/api/projects'

export function useProjectMembers(projectKey: string) {
  return useQuery({
    queryKey: ['members', projectKey],
    queryFn: () => projectsApi.members(projectKey).then(r => r.data),
  })
}
```

- [ ] **Step 4: Replace `IssueDetailPage.tsx` with display-only version showing all fields**

```tsx
import { useParams } from 'react-router-dom'
import { useIssue } from '@/hooks/useIssues'
import { useMe } from '@/hooks/useAuth'
import { StatusBadge } from '@/components/issue/StatusBadge'
import { CommentThread } from '@/components/comments/CommentThread'
import { ActivityFeed } from '@/components/comments/ActivityFeed'
import { AttachmentPanel } from '@/components/attachments/AttachmentPanel'

const priorityColors: Record<string, string> = {
  CRITICAL: 'text-red-400',
  HIGH: 'text-orange-400',
  MEDIUM: 'text-yellow-400',
  LOW: 'text-green-400',
}

function SidebarField({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <label className="text-xs text-gray-500 uppercase tracking-wider mb-1 block">{label}</label>
      {children}
    </div>
  )
}

export function IssueDetailPage() {
  const { key, issueKey } = useParams<{ key: string; issueKey: string }>()
  const { data: issue, isLoading } = useIssue(key!, issueKey!)
  const { data: me } = useMe()

  if (isLoading) return <div className="text-gray-400">Loading...</div>
  if (!issue) return <div className="text-red-400">Issue not found</div>

  return (
    <div className="max-w-5xl">
      {/* Header */}
      <div className="flex items-center gap-3 mb-2">
        <span className="text-sm text-gray-500 font-mono">{issue.key}</span>
        <span className="text-xs px-2 py-0.5 bg-gray-800 rounded text-gray-400">{issue.type}</span>
        <StatusBadge name={issue.statusName} category={issue.statusCategory} />
      </div>
      <h1 className="text-2xl font-bold text-white mb-6">{issue.title}</h1>

      {/* Two-column layout */}
      <div className="grid grid-cols-3 gap-8">
        {/* Left: description + comments + activity */}
        <div className="col-span-2 space-y-8">
          {/* Description */}
          <section>
            <h2 className="text-sm font-medium text-gray-400 mb-2">Description</h2>
            <div className="bg-gray-900 rounded-lg p-4 text-sm text-gray-300 min-h-24">
              {issue.description
                ? <div dangerouslySetInnerHTML={{ __html: issue.description }} />
                : <span className="text-gray-600 italic">No description</span>}
            </div>
          </section>

          {/* Comments */}
          <section>
            <CommentThread projectKey={key!} issueKey={issueKey!} currentUserId={me?.id} />
          </section>

          {/* Activity */}
          <section>
            <ActivityFeed projectKey={key!} issueKey={issueKey!} />
          </section>

          {/* References */}
          {issue.refs && issue.refs.length > 0 && (
            <div className="mt-6">
              <h3 className="text-sm font-semibold text-gray-400 uppercase tracking-wider mb-3">References</h3>
              <div className="space-y-2">
                {issue.refs.map((ref) => (
                  <a key={ref.id} href={ref.url} target="_blank" rel="noopener noreferrer"
                    className="flex items-center gap-3 p-3 bg-gray-800 rounded hover:bg-gray-700 transition-colors">
                    <span className="text-xs font-bold px-2 py-0.5 rounded bg-gray-700 text-gray-300">{ref.provider}</span>
                    <span className="text-xs px-2 py-0.5 rounded bg-gray-700 text-gray-400">{ref.refType}</span>
                    <span className="text-sm text-blue-400 truncate">{ref.title || ref.externalId}</span>
                    <span className="text-xs text-gray-500 shrink-0">
                      {ref.createdAt ? new Date(ref.createdAt).toLocaleDateString() : ''}
                    </span>
                  </a>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Right: metadata + attachments */}
        <div className="flex flex-col gap-4">
          <section className="space-y-4">
            <SidebarField label="Priority">
              <span className={`text-sm font-medium ${priorityColors[issue.priority] ?? 'text-white'}`}>
                {issue.priority}
              </span>
            </SidebarField>

            <SidebarField label="Type">
              <span className="text-sm text-gray-300">{issue.type}</span>
            </SidebarField>

            <SidebarField label="Assignee">
              <span className="text-sm text-gray-300">{issue.assigneeName ?? 'Unassigned'}</span>
            </SidebarField>

            <SidebarField label="Reporter">
              <span className="text-sm text-gray-300">{issue.reporterName}</span>
            </SidebarField>

            <SidebarField label="Sprint">
              <span className="text-sm text-gray-300">{issue.sprintName ?? 'No sprint'}</span>
            </SidebarField>

            <SidebarField label="Due Date">
              <span className="text-sm text-gray-300">
                {issue.dueDate ? new Date(issue.dueDate).toLocaleDateString() : 'No due date'}
              </span>
            </SidebarField>

            {issue.storyPoints != null && (
              <SidebarField label="Story Points">
                <span className="text-sm text-white">{issue.storyPoints}</span>
              </SidebarField>
            )}

            <SidebarField label="Created">
              <span className="text-xs text-gray-500">{new Date(issue.createdAt).toLocaleDateString()}</span>
            </SidebarField>

            <SidebarField label="Updated">
              <span className="text-xs text-gray-500">{new Date(issue.updatedAt).toLocaleDateString()}</span>
            </SidebarField>
          </section>

          {/* Attachments */}
          <section>
            <AttachmentPanel projectKey={key!} issueKey={issueKey!} currentUserId={me?.id} />
          </section>
        </div>
      </div>
    </div>
  )
}
```

- [ ] **Step 5: Start the dev server and verify display**

```
cd frontend
npm run dev
```

Navigate to an issue detail page. Verify: assignee shows name (not UUID), reporter row is visible, sprint row is visible, due date row is visible, created/updated rows appear at bottom of sidebar. No console errors.

- [ ] **Step 6: Commit**

```
git add frontend/src/types/index.ts
git add frontend/src/api/projects.ts
git add frontend/src/hooks/useProjectMembers.ts
git add frontend/src/pages/issues/IssueDetailPage.tsx
git commit -m "feat(issues): wire up display for assignee name, reporter, sprint, due date, timestamps"
```

---

### Task 3: Sidebar Click-to-Edit Components

**Files:**
- Create: `frontend/src/components/issue/InlineEditTitle.tsx`
- Create: `frontend/src/components/issue/PrioritySelector.tsx`
- Create: `frontend/src/components/issue/TypeSelector.tsx`
- Create: `frontend/src/components/issue/AssigneeSelector.tsx`
- Create: `frontend/src/components/issue/SprintSelector.tsx`
- Create: `frontend/src/components/issue/DueDatePicker.tsx`
- Modify: `frontend/src/pages/issues/IssueDetailPage.tsx`

**Interfaces:**
- Consumes: `useUpdateIssue(projectKey)` from `@/hooks/useIssues`; `useProjectMembers(projectKey)` from `@/hooks/useProjectMembers`; `useSprints(projectKey)` from `@/hooks/useSprints`; `Issue` type from `@/types`
- Produces: six reusable components, each calling `onSave` on selection/blur

- [ ] **Step 1: Create `frontend/src/components/issue/InlineEditTitle.tsx`**

```tsx
import { useState, useRef, useEffect } from 'react'

interface Props {
  value: string
  onSave: (value: string) => void
}

export function InlineEditTitle({ value, onSave }: Props) {
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState(value)
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => { if (editing) inputRef.current?.focus() }, [editing])
  useEffect(() => { setDraft(value) }, [value])

  function commit() {
    const trimmed = draft.trim()
    if (trimmed && trimmed !== value) onSave(trimmed)
    setEditing(false)
  }

  if (editing) {
    return (
      <input
        ref={inputRef}
        value={draft}
        onChange={e => setDraft(e.target.value)}
        onBlur={commit}
        onKeyDown={e => {
          if (e.key === 'Enter') commit()
          if (e.key === 'Escape') { setDraft(value); setEditing(false) }
        }}
        className="w-full text-2xl font-bold bg-transparent border-b border-blue-500 text-white outline-none mb-6"
      />
    )
  }

  return (
    <h1
      onClick={() => setEditing(true)}
      className="text-2xl font-bold text-white mb-6 cursor-pointer hover:bg-gray-800 rounded px-1 -mx-1"
    >
      {value}
    </h1>
  )
}
```

- [ ] **Step 2: Create `frontend/src/components/issue/PrioritySelector.tsx`**

```tsx
import { useState, useRef, useEffect } from 'react'

const PRIORITIES = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'] as const
type Priority = typeof PRIORITIES[number]

const colors: Record<Priority, string> = {
  CRITICAL: 'text-red-400',
  HIGH: 'text-orange-400',
  MEDIUM: 'text-yellow-400',
  LOW: 'text-green-400',
}

interface Props {
  value: Priority
  onSave: (value: Priority) => void
}

export function PrioritySelector({ value, onSave }: Props) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [])

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => setOpen(o => !o)}
        className={`text-sm font-medium ${colors[value]} cursor-pointer hover:underline`}
      >
        {value}
      </button>
      {open && (
        <div className="absolute z-50 top-6 left-0 bg-gray-800 border border-gray-700 rounded shadow-lg py-1 min-w-32">
          {PRIORITIES.map(p => (
            <button
              key={p}
              onClick={() => { onSave(p); setOpen(false) }}
              className={`w-full text-left px-3 py-1.5 text-sm ${colors[p]} hover:bg-gray-700 ${p === value ? 'font-bold' : ''}`}
            >
              {p}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
```

- [ ] **Step 3: Create `frontend/src/components/issue/TypeSelector.tsx`**

```tsx
import { useState, useRef, useEffect } from 'react'

const TYPES = ['EPIC', 'STORY', 'BUG', 'TASK', 'SUBTASK'] as const
type IssueType = typeof TYPES[number]

interface Props {
  value: IssueType
  onSave: (value: IssueType) => void
}

export function TypeSelector({ value, onSave }: Props) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [])

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => setOpen(o => !o)}
        className="text-sm text-gray-300 cursor-pointer hover:underline"
      >
        {value}
      </button>
      {open && (
        <div className="absolute z-50 top-6 left-0 bg-gray-800 border border-gray-700 rounded shadow-lg py-1 min-w-32">
          {TYPES.map(t => (
            <button
              key={t}
              onClick={() => { onSave(t); setOpen(false) }}
              className={`w-full text-left px-3 py-1.5 text-sm text-gray-300 hover:bg-gray-700 ${t === value ? 'font-bold text-white' : ''}`}
            >
              {t}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
```

- [ ] **Step 4: Create `frontend/src/components/issue/AssigneeSelector.tsx`**

```tsx
import { useState, useRef, useEffect } from 'react'
import type { User } from '@/types'

interface Props {
  value: string | null        // assigneeName
  assigneeId: string | null
  members: User[]
  onSave: (userId: string | null) => void
}

export function AssigneeSelector({ value, assigneeId, members, onSave }: Props) {
  const [open, setOpen] = useState(false)
  const [search, setSearch] = useState('')
  const ref = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) { setOpen(false); setSearch('') }
    }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [])

  useEffect(() => { if (open) inputRef.current?.focus() }, [open])

  const filtered = members.filter(m =>
    m.displayName.toLowerCase().includes(search.toLowerCase()) ||
    m.email.toLowerCase().includes(search.toLowerCase())
  )

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => setOpen(o => !o)}
        className="text-sm text-gray-300 cursor-pointer hover:underline"
      >
        {value ?? 'Unassigned'}
      </button>
      {open && (
        <div className="absolute z-50 top-6 left-0 bg-gray-800 border border-gray-700 rounded shadow-lg py-1 min-w-48">
          <div className="px-2 pb-1">
            <input
              ref={inputRef}
              value={search}
              onChange={e => setSearch(e.target.value)}
              placeholder="Search members…"
              className="w-full bg-gray-700 text-sm text-white rounded px-2 py-1 outline-none"
            />
          </div>
          {assigneeId && (
            <button
              onClick={() => { onSave(null); setOpen(false); setSearch('') }}
              className="w-full text-left px-3 py-1.5 text-sm text-gray-500 hover:bg-gray-700"
            >
              Unassign
            </button>
          )}
          {filtered.map(m => (
            <button
              key={m.id}
              onClick={() => { onSave(m.id); setOpen(false); setSearch('') }}
              className={`w-full text-left px-3 py-1.5 text-sm text-gray-300 hover:bg-gray-700 ${m.id === assigneeId ? 'font-bold text-white' : ''}`}
            >
              {m.displayName}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
```

- [ ] **Step 5: Create `frontend/src/components/issue/SprintSelector.tsx`**

```tsx
import { useState, useRef, useEffect } from 'react'
import type { Sprint } from '@/types'

interface Props {
  value: string | null        // sprintName
  sprintId: string | null
  sprints: Sprint[]
  onSave: (sprintId: string | null) => void
}

export function SprintSelector({ value, sprintId, sprints, onSave }: Props) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [])

  const activeSprints = sprints.filter(s => s.status === 'ACTIVE' || s.status === 'PLANNED')

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => setOpen(o => !o)}
        className="text-sm text-gray-300 cursor-pointer hover:underline"
      >
        {value ?? 'No sprint'}
      </button>
      {open && (
        <div className="absolute z-50 top-6 left-0 bg-gray-800 border border-gray-700 rounded shadow-lg py-1 min-w-40">
          {sprintId && (
            <button
              onClick={() => { onSave(null); setOpen(false) }}
              className="w-full text-left px-3 py-1.5 text-sm text-gray-500 hover:bg-gray-700"
            >
              No sprint
            </button>
          )}
          {activeSprints.map(s => (
            <button
              key={s.id}
              onClick={() => { onSave(s.id); setOpen(false) }}
              className={`w-full text-left px-3 py-1.5 text-sm text-gray-300 hover:bg-gray-700 ${s.id === sprintId ? 'font-bold text-white' : ''}`}
            >
              {s.name}
            </button>
          ))}
          {activeSprints.length === 0 && (
            <p className="px-3 py-2 text-xs text-gray-600">No active sprints</p>
          )}
        </div>
      )}
    </div>
  )
}
```

- [ ] **Step 6: Create `frontend/src/components/issue/DueDatePicker.tsx`**

```tsx
import { useState, useRef, useEffect } from 'react'

interface Props {
  value: string | null   // ISO date string "YYYY-MM-DD" or null
  onSave: (date: string | null) => void
}

export function DueDatePicker({ value, onSave }: Props) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [])

  const display = value ? new Date(value).toLocaleDateString() : 'No due date'

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => setOpen(o => !o)}
        className="text-sm text-gray-300 cursor-pointer hover:underline"
      >
        {display}
      </button>
      {open && (
        <div className="absolute z-50 top-6 left-0 bg-gray-800 border border-gray-700 rounded shadow-lg p-3 min-w-44">
          <input
            type="date"
            defaultValue={value ?? ''}
            onChange={e => { onSave(e.target.value || null); setOpen(false) }}
            className="bg-gray-700 text-white text-sm rounded px-2 py-1 outline-none w-full"
          />
          {value && (
            <button
              onClick={() => { onSave(null); setOpen(false) }}
              className="mt-2 text-xs text-gray-500 hover:text-red-400 w-full text-left"
            >
              Clear due date
            </button>
          )}
        </div>
      )}
    </div>
  )
}
```

- [ ] **Step 7: Replace `IssueDetailPage.tsx` with the fully interactive version**

```tsx
import { useParams } from 'react-router-dom'
import { useIssue, useUpdateIssue } from '@/hooks/useIssues'
import { useMe } from '@/hooks/useAuth'
import { useSprints } from '@/hooks/useSprints'
import { useProjectMembers } from '@/hooks/useProjectMembers'
import { StatusBadge } from '@/components/issue/StatusBadge'
import { InlineEditTitle } from '@/components/issue/InlineEditTitle'
import { PrioritySelector } from '@/components/issue/PrioritySelector'
import { TypeSelector } from '@/components/issue/TypeSelector'
import { AssigneeSelector } from '@/components/issue/AssigneeSelector'
import { SprintSelector } from '@/components/issue/SprintSelector'
import { DueDatePicker } from '@/components/issue/DueDatePicker'
import { CommentThread } from '@/components/comments/CommentThread'
import { ActivityFeed } from '@/components/comments/ActivityFeed'
import { AttachmentPanel } from '@/components/attachments/AttachmentPanel'

function SidebarField({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <label className="text-xs text-gray-500 uppercase tracking-wider mb-1 block">{label}</label>
      {children}
    </div>
  )
}

export function IssueDetailPage() {
  const { key, issueKey } = useParams<{ key: string; issueKey: string }>()
  const { data: issue, isLoading } = useIssue(key!, issueKey!)
  const { data: me } = useMe()
  const updateIssue = useUpdateIssue(key!)
  const { data: members = [] } = useProjectMembers(key!)
  const { data: sprints = [] } = useSprints(key!)

  if (isLoading) return <div className="text-gray-400">Loading...</div>
  if (!issue) return <div className="text-red-400">Issue not found</div>

  function patch(data: Record<string, unknown>) {
    updateIssue.mutate({ id: issue!.id, data })
  }

  return (
    <div className="max-w-5xl">
      {/* Header */}
      <div className="flex items-center gap-3 mb-2">
        <span className="text-sm text-gray-500 font-mono">{issue.key}</span>
        <StatusBadge name={issue.statusName} category={issue.statusCategory} />
      </div>

      <InlineEditTitle value={issue.title} onSave={title => patch({ title })} />

      {/* Two-column layout */}
      <div className="grid grid-cols-3 gap-8">
        {/* Left: description + comments + activity */}
        <div className="col-span-2 space-y-8">
          {/* Description (plain display — replaced in Task 4) */}
          <section>
            <h2 className="text-sm font-medium text-gray-400 mb-2">Description</h2>
            <div className="bg-gray-900 rounded-lg p-4 text-sm text-gray-300 min-h-24">
              {issue.description
                ? <div dangerouslySetInnerHTML={{ __html: issue.description }} />
                : <span className="text-gray-600 italic">No description</span>}
            </div>
          </section>

          <section>
            <CommentThread projectKey={key!} issueKey={issueKey!} currentUserId={me?.id} />
          </section>

          <section>
            <ActivityFeed projectKey={key!} issueKey={issueKey!} />
          </section>

          {issue.refs && issue.refs.length > 0 && (
            <div className="mt-6">
              <h3 className="text-sm font-semibold text-gray-400 uppercase tracking-wider mb-3">References</h3>
              <div className="space-y-2">
                {issue.refs.map((ref) => (
                  <a key={ref.id} href={ref.url} target="_blank" rel="noopener noreferrer"
                    className="flex items-center gap-3 p-3 bg-gray-800 rounded hover:bg-gray-700 transition-colors">
                    <span className="text-xs font-bold px-2 py-0.5 rounded bg-gray-700 text-gray-300">{ref.provider}</span>
                    <span className="text-xs px-2 py-0.5 rounded bg-gray-700 text-gray-400">{ref.refType}</span>
                    <span className="text-sm text-blue-400 truncate">{ref.title || ref.externalId}</span>
                    <span className="text-xs text-gray-500 shrink-0">
                      {ref.createdAt ? new Date(ref.createdAt).toLocaleDateString() : ''}
                    </span>
                  </a>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Right: metadata + attachments */}
        <div className="flex flex-col gap-4">
          <section className="space-y-4">
            <SidebarField label="Priority">
              <PrioritySelector
                value={issue.priority}
                onSave={priority => patch({ priority })}
              />
            </SidebarField>

            <SidebarField label="Type">
              <TypeSelector
                value={issue.type}
                onSave={type => patch({ type })}
              />
            </SidebarField>

            <SidebarField label="Assignee">
              <AssigneeSelector
                value={issue.assigneeName}
                assigneeId={issue.assigneeId}
                members={members}
                onSave={userId =>
                  userId ? patch({ assigneeId: userId }) : patch({ clearAssignee: true })
                }
              />
            </SidebarField>

            <SidebarField label="Reporter">
              <span className="text-sm text-gray-300">{issue.reporterName}</span>
            </SidebarField>

            <SidebarField label="Sprint">
              <SprintSelector
                value={issue.sprintName}
                sprintId={issue.sprintId}
                sprints={sprints}
                onSave={sprintId =>
                  sprintId ? patch({ sprintId }) : patch({ clearSprint: true })
                }
              />
            </SidebarField>

            <SidebarField label="Due Date">
              <DueDatePicker
                value={issue.dueDate}
                onSave={date =>
                  date ? patch({ dueDate: date }) : patch({ clearDueDate: true })
                }
              />
            </SidebarField>

            {issue.storyPoints != null && (
              <SidebarField label="Story Points">
                <span className="text-sm text-white">{issue.storyPoints}</span>
              </SidebarField>
            )}

            <SidebarField label="Created">
              <span className="text-xs text-gray-500">{new Date(issue.createdAt).toLocaleDateString()}</span>
            </SidebarField>

            <SidebarField label="Updated">
              <span className="text-xs text-gray-500">{new Date(issue.updatedAt).toLocaleDateString()}</span>
            </SidebarField>
          </section>

          <section>
            <AttachmentPanel projectKey={key!} issueKey={issueKey!} currentUserId={me?.id} />
          </section>
        </div>
      </div>
    </div>
  )
}
```

- [ ] **Step 8: Verify in browser**

With the dev server still running, open an issue. Verify:
- Click the title → text input appears; Enter saves, Escape cancels
- Click Priority → dropdown shows four options; selection updates immediately
- Click Type → dropdown shows five options; selection updates immediately
- Click Assignee → searchable list appears; "Unassign" option shown when assigned
- Click Sprint → dropdown shows active/planned sprints; "No sprint" option shown when assigned
- Click Due Date → date picker appears; "Clear due date" link shown when set
- Reporter row is read-only (no click affordance)
- All changes appear reflected after save (React Query invalidation refreshes the page)

- [ ] **Step 9: Commit**

```
git add frontend/src/components/issue/InlineEditTitle.tsx
git add frontend/src/components/issue/PrioritySelector.tsx
git add frontend/src/components/issue/TypeSelector.tsx
git add frontend/src/components/issue/AssigneeSelector.tsx
git add frontend/src/components/issue/SprintSelector.tsx
git add frontend/src/components/issue/DueDatePicker.tsx
git add frontend/src/pages/issues/IssueDetailPage.tsx
git commit -m "feat(issues): click-to-edit sidebar fields — priority, type, assignee, sprint, due date, title"
```

---

### Task 4: TipTap Rich Text Editor

**Files:**
- Create: `frontend/src/components/issue/RichTextEditor.tsx`
- Modify: `frontend/src/pages/issues/IssueDetailPage.tsx`

**Interfaces:**
- Consumes: `useUpdateIssue` `patch` helper from `IssueDetailPage`
- Produces: `<RichTextEditor value={string | null} onSave={(html: string | null) => void} />`

- [ ] **Step 1: Install TipTap dependencies**

```
cd frontend
npm install @tiptap/react @tiptap/starter-kit @tiptap/extension-link @tiptap/extension-placeholder dompurify
npm install --save-dev @types/dompurify
```

Expected: packages added to `package.json` and `node_modules/`.

- [ ] **Step 2: Create `frontend/src/components/issue/RichTextEditor.tsx`**

Notes on two design decisions baked into this component:
- **Editor sync:** `useEditor` sets content only on mount. A `useEffect` watches `value` and `editing` to re-sync the editor when the server returns a fresh value after save.
- **`prose` classes omitted:** `@tailwindcss/typography` is not installed; the rendered HTML is styled via the browser's built-in defaults inside a container with `text-sm text-gray-300`. This is intentional — add typography later if desired.
- **Clearing description:** sending `description: ""` (empty string) when user clears the editor. The backend's `?.let` branch runs and stores `""`, which renders the same as `null` ("No description"). This avoids adding a `clearDescription` boolean for now.

```tsx
import { useEditor, EditorContent } from '@tiptap/react'
import StarterKit from '@tiptap/starter-kit'
import Link from '@tiptap/extension-link'
import Placeholder from '@tiptap/extension-placeholder'
import DOMPurify from 'dompurify'
import { useState, useEffect } from 'react'

interface Props {
  value: string | null
  onSave: (html: string) => void
}

const EMPTY_DOC = '<p></p>'

function sanitize(html: string | null): string {
  if (!html) return ''
  return DOMPurify.sanitize(html)
}

function normalise(html: string): string {
  return html === EMPTY_DOC ? '' : html
}

export function RichTextEditor({ value, onSave }: Props) {
  const [editing, setEditing] = useState(false)

  const editor = useEditor({
    extensions: [
      StarterKit,
      Link.configure({ openOnClick: false }),
      Placeholder.configure({ placeholder: 'Add a description…' }),
    ],
    content: sanitize(value),
    editorProps: {
      attributes: {
        class: 'min-h-24 focus:outline-none text-sm text-gray-300 [&_strong]:font-bold [&_em]:italic [&_code]:bg-gray-800 [&_code]:rounded [&_code]:px-1 [&_ul]:list-disc [&_ul]:pl-4 [&_ol]:list-decimal [&_ol]:pl-4',
      },
    },
    onBlur: ({ editor }) => {
      const html = normalise(editor.getHTML())
      const current = normalise(sanitize(value))
      if (html !== current) onSave(html)
      setEditing(false)
    },
  })

  // Sync editor content when value changes from server (e.g. after a save + refetch)
  useEffect(() => {
    if (editor && !editing) {
      const current = normalise(editor.getHTML())
      const incoming = normalise(sanitize(value))
      if (current !== incoming) editor.commands.setContent(incoming)
    }
  }, [editor, value, editing])

  if (!editing) {
    return (
      <div
        onClick={() => { setEditing(true); setTimeout(() => editor?.commands.focus('end'), 0) }}
        className="bg-gray-900 rounded-lg p-4 text-sm text-gray-300 min-h-24 cursor-pointer hover:ring-1 hover:ring-gray-700"
      >
        {value
          ? <div dangerouslySetInnerHTML={{ __html: sanitize(value) }} />
          : <span className="text-gray-600 italic">Add a description…</span>}
      </div>
    )
  }

  return (
    <div className="bg-gray-900 rounded-lg ring-1 ring-blue-500">
      {/* Toolbar */}
      <div className="flex gap-1 p-2 border-b border-gray-800">
        {[
          { label: 'B', title: 'Bold', action: () => editor?.chain().focus().toggleBold().run(), active: () => editor?.isActive('bold') },
          { label: 'I', title: 'Italic', action: () => editor?.chain().focus().toggleItalic().run(), active: () => editor?.isActive('italic') },
          { label: '<>', title: 'Code', action: () => editor?.chain().focus().toggleCode().run(), active: () => editor?.isActive('code') },
          { label: '• List', title: 'Bullet list', action: () => editor?.chain().focus().toggleBulletList().run(), active: () => editor?.isActive('bulletList') },
          { label: '1. List', title: 'Ordered list', action: () => editor?.chain().focus().toggleOrderedList().run(), active: () => editor?.isActive('orderedList') },
        ].map(btn => (
          <button
            key={btn.label}
            title={btn.title}
            onMouseDown={e => { e.preventDefault(); btn.action() }}
            className={`px-2 py-0.5 text-xs rounded ${btn.active?.() ? 'bg-gray-600 text-white' : 'text-gray-400 hover:bg-gray-800'}`}
          >
            {btn.label}
          </button>
        ))}
      </div>
      <div className="p-4">
        <EditorContent editor={editor} />
      </div>
    </div>
  )
}
```

- [ ] **Step 3: Update `IssueDetailPage.tsx` — replace static description block with `RichTextEditor`**

In `IssueDetailPage.tsx`, add this import at the top with the other component imports:
```tsx
import { RichTextEditor } from '@/components/issue/RichTextEditor'
```

Replace the description `<section>` block:
```tsx
          {/* Description (plain display — replaced in Task 4) */}
          <section>
            <h2 className="text-sm font-medium text-gray-400 mb-2">Description</h2>
            <div className="bg-gray-900 rounded-lg p-4 text-sm text-gray-300 min-h-24">
              {issue.description
                ? <div dangerouslySetInnerHTML={{ __html: issue.description }} />
                : <span className="text-gray-600 italic">No description</span>}
            </div>
          </section>
```
with:
```tsx
          {/* Description */}
          <section>
            <h2 className="text-sm font-medium text-gray-400 mb-2">Description</h2>
            <RichTextEditor
              value={issue.description}
              onSave={description => patch({ description })}
            />
          </section>
```

- [ ] **Step 4: Verify in browser**

Open an issue. Verify:
- Description area shows rendered HTML (or "Add a description…" placeholder if empty)
- Clicking the description area activates TipTap editor with toolbar
- Toolbar buttons (Bold, Italic, Code, Bullet list, Ordered list) work
- Clicking outside the editor saves and returns to read-only view
- Saving an empty description sets it to empty/null (no `<p></p>` stored)
- Existing plain-text descriptions render as plain text and remain editable

- [ ] **Step 5: Commit**

```
git add frontend/src/components/issue/RichTextEditor.tsx
git add frontend/src/pages/issues/IssueDetailPage.tsx
git add frontend/package.json frontend/package-lock.json
git commit -m "feat(issues): TipTap rich text editor for issue description"
```
