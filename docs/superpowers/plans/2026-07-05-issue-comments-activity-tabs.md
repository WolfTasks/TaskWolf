# Issue Comments/Activity Tabs, Pinned Sidebar & Paginated Feeds — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the issue detail Comments and Activity sections into two tabs sharing one fixed-height scrollable panel, pin the metadata sidebar to the right with the content filling the rest on the full-page view, and load only the 5 most recent comments/activity items with a "Load more" button.

**Architecture:** Backend gains real pagination for the comments endpoint and flattens the activity endpoint to a standard Spring `Page` (matching the rest of the app, so the existing frontend `Page<T>` type works for both). The frontend converts the comments and activity queries to `useInfiniteQuery` (page size 5), each feed renders inside a `max-h`/`overflow-y-auto` panel with a "Load more" button, and a new `CommentsActivityTabs` component hosts them as tabs inside a flex layout whose sidebar is pinned right.

**Tech Stack:** Kotlin + Spring Boot (Spring Data JPA, JUnit5 + MockK) backend; React + TypeScript + Vite + TanStack Query v5 + Tailwind frontend.

## Global Constraints

- Spring `Page` responses are serialized flat (`content`, `totalElements`, `totalPages`, `number`) — the app convention (e.g. issues/notifications endpoints). Do NOT use `org.springframework.data.web.PagedModel`; the activity endpoint is being converted away from it.
- Page size for both feeds is fixed at **5**.
- TanStack Query is **v5** (`^5.101.1`): `useInfiniteQuery` requires `initialPageParam` and `getNextPageParam`.
- Frontend has **no test framework** (project convention): the verification gate is `npm run build` (runs `tsc && vite build`) plus manual verification. Do not add a test runner.
- Backend follows **TDD**: write/adjust the failing test first.
- Backend commands run from the `backend/` directory (that is where `gradlew` lives). Frontend commands run from `frontend/`.
- Frequent commits: one commit per task.

---

## File Structure

**Backend**
- `backend/src/main/kotlin/com/taskowolf/comments/infrastructure/CommentRepository.kt` — add paged, deleted-excluding, newest-first query.
- `backend/src/main/kotlin/com/taskowolf/comments/application/CommentService.kt` — `listComments` returns `Page<Comment>`.
- `backend/src/main/kotlin/com/taskowolf/comments/api/CommentController.kt` — paged `GET /comments`; flatten `GET /activity` to `Page`.
- `backend/src/test/kotlin/com/taskowolf/comments/CommentServiceTest.kt` — add paged `listComments` test.
- `backend/src/test/kotlin/com/taskowolf/CollaborationIntegrationTest.kt` — update comment-list JSON paths.

**Frontend**
- `frontend/src/api/comments.ts` — `list` gains `page`/`size` params + `Page<Comment>` return type.
- `frontend/src/hooks/useComments.ts` — `useComments` + `useActivity` become `useInfiniteQuery`; mutations reset/invalidate.
- `frontend/src/components/comments/CommentThread.tsx` — infinite paging, "Load more" (older) at top, scroll panel.
- `frontend/src/components/comments/ActivityFeed.tsx` — infinite paging, "Load more" at bottom, scroll panel.
- `frontend/src/components/comments/CommentsActivityTabs.tsx` — **new** tab container.
- `frontend/src/components/issue/IssueDetailContent.tsx` — flex layout, pinned sidebar, use tabs component.
- `frontend/src/pages/issues/IssueDetailPage.tsx` — drop `max-w-5xl`.

No changes needed to `frontend/src/types/index.ts`: the existing `Page<T>` (`content`, `totalElements`, `totalPages`, `number`), `Comment`, and `ActivityItem` types already match.

---

## Task 1: Backend — paginate the comments endpoint

**Files:**
- Modify: `backend/src/main/kotlin/com/taskowolf/comments/infrastructure/CommentRepository.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/comments/application/CommentService.kt:44-48`
- Modify: `backend/src/main/kotlin/com/taskowolf/comments/api/CommentController.kt:34-39`
- Test: `backend/src/test/kotlin/com/taskowolf/comments/CommentServiceTest.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/CollaborationIntegrationTest.kt:69,103`

**Interfaces:**
- Consumes: `IssueService.findByKey(projectKey, issueKey, userId): Issue`; `Comment` domain (`deletedAt: Instant?`, `createdAt: Instant?`).
- Produces:
  - `CommentRepository.findByIssueIdAndDeletedAtIsNullOrderByCreatedAtDesc(issueId: UUID, pageable: Pageable): Page<Comment>`
  - `CommentService.listComments(projectKey: String, issueKey: String, userId: UUID, page: Int, size: Int): Page<Comment>`
  - `GET /api/v1/projects/{key}/issues/{issueKey}/comments?page&size` → flat `Page<CommentResponse>` (newest-first, deleted excluded, default `page=0`, `size=5`).

- [ ] **Step 1: Update the failing service test**

In `CommentServiceTest.kt`, add these imports next to the existing ones:

```kotlin
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
```

Add this test method inside the class (e.g. after `addComment` test):

```kotlin
@Test
fun `listComments returns newest-first page excluding deleted`() {
    val newest = Comment(issueId = issue.id, authorId = author.id, body = "newest")
    every { issueService.findByKey("WOLF", "WOLF-1", author.id) } returns issue
    every {
        commentRepository.findByIssueIdAndDeletedAtIsNullOrderByCreatedAtDesc(issue.id, any())
    } returns PageImpl(listOf(newest), PageRequest.of(0, 5), 1)

    val result = service.listComments("WOLF", "WOLF-1", author.id, 0, 5)

    assertEquals(1, result.totalElements)
    assertEquals("newest", result.content[0].body)
}
```

- [ ] **Step 2: Run the test to verify it fails to compile/fail**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.comments.CommentServiceTest"`
Expected: FAIL — unresolved reference `findByIssueIdAndDeletedAtIsNullOrderByCreatedAtDesc` and/or `listComments` arity mismatch (old signature takes 3 args).

- [ ] **Step 3: Add the repository query**

Replace the whole body of `CommentRepository.kt` with:

```kotlin
package com.taskowolf.comments.infrastructure

import com.taskowolf.comments.domain.Comment
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CommentRepository : JpaRepository<Comment, UUID> {
    fun findAllByIssueId(issueId: UUID): List<Comment>
    fun findByIssueIdAndDeletedAtIsNullOrderByCreatedAtDesc(
        issueId: UUID,
        pageable: Pageable
    ): Page<Comment>
}
```

- [ ] **Step 4: Update the service**

In `CommentService.kt`, add these imports:

```kotlin
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
```

Replace the existing `listComments` method (lines 44-48):

```kotlin
    @Transactional(readOnly = true)
    fun listComments(projectKey: String, issueKey: String, userId: UUID, page: Int, size: Int): Page<Comment> {
        val issue = issueService.findByKey(projectKey, issueKey, userId)
        return commentRepository.findByIssueIdAndDeletedAtIsNullOrderByCreatedAtDesc(
            issue.id, PageRequest.of(page, size)
        )
    }
```

- [ ] **Step 5: Update the controller endpoint**

In `CommentController.kt`, replace the `listComments` handler (lines 34-39):

```kotlin
    @GetMapping("/comments")
    fun listComments(
        @PathVariable key: String,
        @PathVariable issueKey: String,
        @AuthenticationPrincipal user: User,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "5") size: Int
    ) = commentService.listComments(key, issueKey, user.id, page, size).map { CommentResponse.from(it) }
```

- [ ] **Step 6: Fix the integration test's JSON paths**

In `CollaborationIntegrationTest.kt`, the comments list is now a paged object, not a bare array.

Change line 69 from:

```kotlin
            .andExpect(jsonPath("$[0].body").value("This is a test comment"))
```

to:

```kotlin
            .andExpect(jsonPath("$.content[0].body").value("This is a test comment"))
```

Change line 103 from:

```kotlin
            .andExpect(jsonPath("$").isArray)
```

to:

```kotlin
            .andExpect(jsonPath("$.content").isArray)
```

- [ ] **Step 7: Run the affected tests to verify they pass**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.comments.CommentServiceTest" --tests "com.taskowolf.CollaborationIntegrationTest"`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/comments backend/src/test/kotlin/com/taskowolf/comments/CommentServiceTest.kt backend/src/test/kotlin/com/taskowolf/CollaborationIntegrationTest.kt
git commit -m "feat(comments): paginate comment list endpoint (newest-first, size 5)"
```

---

## Task 2: Backend — flatten the activity endpoint to a Spring `Page`

**Files:**
- Modify: `backend/src/main/kotlin/com/taskowolf/comments/api/CommentController.kt:59-70` (and the `PagedModel` import on line 12)

**Interfaces:**
- Consumes: `ActivityService.listActivity(issueId: UUID, page: Int, size: Int): Page<IssueActivity>` (already newest-first — sorts `createdAt` descending).
- Produces: `GET /api/v1/projects/{key}/issues/{issueKey}/activity?page&size` → flat `Page<ActivityResponse>` (default `page=0`, `size=5`).

- [ ] **Step 1: Point the existing integration assertion at the flat shape (already compatible)**

No change needed — `CollaborationIntegrationTest.kt:87-88` already asserts `jsonPath("$.content")` / `$.content[0].type`, which a flat Spring `Page` also satisfies. This step is a no-op confirmation; do not edit.

- [ ] **Step 2: Replace `PagedModel` with `Page` in the controller**

In `CommentController.kt`, remove the import on line 12:

```kotlin
import org.springframework.data.web.PagedModel
```

and add:

```kotlin
import org.springframework.data.domain.Page
```

Replace the `listActivity` handler (lines 59-70) with:

```kotlin
    @GetMapping("/activity")
    fun listActivity(
        @PathVariable key: String,
        @PathVariable issueKey: String,
        @AuthenticationPrincipal user: User,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "5") size: Int
    ): Page<ActivityResponse> {
        val issue = issueService.findByKey(key, issueKey, user.id)
        return activityService.listActivity(issue.id, page, size).map { ActivityResponse.from(it) }
    }
```

- [ ] **Step 3: Run the activity/collaboration tests to verify they pass**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.CollaborationIntegrationTest" --tests "com.taskowolf.comments.ActivityServiceTest"`
Expected: PASS (`$.content[0].type` == "COMMENT" still holds).

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/comments/api/CommentController.kt
git commit -m "refactor(activity): return flat Spring Page instead of PagedModel"
```

---

## Task 3: Frontend — comments infinite query + paginated CommentThread

**Files:**
- Modify: `frontend/src/api/comments.ts:5-6`
- Modify: `frontend/src/hooks/useComments.ts:1-16,18-47`
- Modify: `frontend/src/components/comments/CommentThread.tsx`

**Interfaces:**
- Consumes: `GET /comments?page&size` → `Page<Comment>` (newest-first). `Page<T>` = `{ content, totalElements, totalPages, number }`.
- Produces:
  - `commentsApi.list(projectKey, issueKey, page?, size?) : Promise<AxiosResponse<Page<Comment>>>`
  - `useComments(projectKey, issueKey)` returns a TanStack v5 infinite query: `data.pages: Page<Comment>[]`, `fetchNextPage`, `hasNextPage`, `isFetchingNextPage`, `isLoading`.

- [ ] **Step 1: Add paging params to the comments API call**

In `frontend/src/api/comments.ts`, replace the `list` method (lines 5-6):

```ts
  list: (projectKey: string, issueKey: string, page = 0, size = 5) =>
    apiClient.get<Page<Comment>>(`/projects/${projectKey}/issues/${issueKey}/comments`, {
      params: { page, size },
    }),
```

(`Page`, `Comment` are already imported at the top of the file.)

- [ ] **Step 2: Convert `useComments` to an infinite query and reset on mutation**

In `frontend/src/hooks/useComments.ts`, replace the import on line 1:

```ts
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query'
```

Replace the `useComments` function (lines 4-9):

```ts
export function useComments(projectKey: string, issueKey: string) {
  return useInfiniteQuery({
    queryKey: ['comments', projectKey, issueKey],
    queryFn: ({ pageParam }) =>
      commentsApi.list(projectKey, issueKey, pageParam, 5).then(r => r.data),
    initialPageParam: 0,
    getNextPageParam: (lastPage) =>
      lastPage.number + 1 < lastPage.totalPages ? lastPage.number + 1 : undefined,
  })
}
```

Update the three mutation `onSuccess` handlers so posting/deleting resets the comments infinite query (avoids offset-shift duplicates), editing just invalidates:

`useAddComment` `onSuccess`:

```ts
    onSuccess: () => {
      qc.resetQueries({ queryKey: ['comments', projectKey, issueKey] })
      qc.invalidateQueries({ queryKey: ['activity', projectKey, issueKey] })
    },
```

`useEditComment` `onSuccess` (unchanged behavior, keep invalidate):

```ts
    onSuccess: () => qc.invalidateQueries({ queryKey: ['comments', projectKey, issueKey] }),
```

`useDeleteComment` `onSuccess`:

```ts
    onSuccess: () => {
      qc.resetQueries({ queryKey: ['comments', projectKey, issueKey] })
      qc.invalidateQueries({ queryKey: ['activity', projectKey, issueKey] })
    },
```

Leave `useActivity` unchanged in this task (it becomes infinite in Task 4; `invalidateQueries`/`resetQueries` work for both query kinds).

- [ ] **Step 3: Rewrite CommentThread to consume paged data with a scroll panel and top "Load more"**

Replace the entire body of `frontend/src/components/comments/CommentThread.tsx` with:

```tsx
import { useState } from 'react'
import type { Comment } from '@/types'
import { useComments, useAddComment, useEditComment, useDeleteComment } from '@/hooks/useComments'

const formatTime = (iso: string) => {
  const d = new Date(iso)
  return d.toLocaleDateString() + ' ' + d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

interface Props {
  projectKey: string
  issueKey: string
  currentUserId?: string
}

export function CommentThread({ projectKey, issueKey, currentUserId }: Props) {
  const { data, isLoading, fetchNextPage, hasNextPage, isFetchingNextPage } = useComments(projectKey, issueKey)
  const addComment = useAddComment(projectKey, issueKey)
  const editComment = useEditComment(projectKey, issueKey)
  const deleteComment = useDeleteComment(projectKey, issueKey)

  const [newBody, setNewBody] = useState('')
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editBody, setEditBody] = useState('')

  // Pages arrive newest-first; flatten to oldest -> newest for chat-style display.
  const comments: Comment[] = (data?.pages ?? [])
    .slice()
    .reverse()
    .flatMap(p => p.content.slice().reverse())

  const handleAdd = async () => {
    if (!newBody.trim()) return
    await addComment.mutateAsync(newBody.trim())
    setNewBody('')
  }

  const handleEdit = async (commentId: string) => {
    if (!editBody.trim()) return
    await editComment.mutateAsync({ commentId, body: editBody.trim() })
    setEditingId(null)
  }

  const handleDelete = (commentId: string) => {
    if (confirm('Delete this comment?')) {
      deleteComment.mutate(commentId)
    }
  }

  if (isLoading) return <div className="text-gray-500 text-sm">Loading comments...</div>

  return (
    <div className="space-y-4">
      <div className="max-h-[26rem] overflow-y-auto space-y-4 pr-1">
        {hasNextPage && (
          <button
            onClick={() => fetchNextPage()}
            disabled={isFetchingNextPage}
            className="text-xs text-indigo-400 hover:text-indigo-300 disabled:opacity-50"
          >
            {isFetchingNextPage ? 'Loading...' : 'Load older comments'}
          </button>
        )}

        {comments.length === 0 && (
          <p className="text-gray-600 text-sm italic">No comments yet</p>
        )}

        {comments.map((comment: Comment) => (
          <div key={comment.id} className="bg-gray-900 rounded-lg p-3">
            {editingId === comment.id ? (
              <div className="space-y-2">
                <textarea
                  className="w-full bg-gray-800 text-sm text-white rounded p-2 resize-none min-h-16 border border-gray-700 focus:outline-none focus:border-indigo-500"
                  value={editBody}
                  onChange={e => setEditBody(e.target.value)}
                  rows={3}
                />
                <div className="flex gap-2">
                  <button
                    onClick={() => handleEdit(comment.id)}
                    className="px-3 py-1 bg-indigo-600 hover:bg-indigo-500 text-white text-xs rounded"
                  >
                    Save
                  </button>
                  <button
                    onClick={() => setEditingId(null)}
                    className="px-3 py-1 bg-gray-700 hover:bg-gray-600 text-white text-xs rounded"
                  >
                    Cancel
                  </button>
                </div>
              </div>
            ) : (
              <>
                <p className="text-sm text-gray-300 whitespace-pre-wrap">
                  {comment.deleted ? <span className="italic text-gray-600">Comment deleted</span> : comment.body}
                </p>
                <div className="flex items-center justify-between mt-2">
                  <span className="text-xs text-gray-600">
                    {formatTime(comment.createdAt)}
                    {comment.editedAt && ' (edited)'}
                  </span>
                  {!comment.deleted && currentUserId === comment.authorId && (
                    <div className="flex gap-1">
                      <button
                        onClick={() => { setEditingId(comment.id); setEditBody(comment.body ?? '') }}
                        className="text-xs text-gray-500 hover:text-indigo-400 px-2 py-0.5 rounded"
                      >
                        Edit
                      </button>
                      <button
                        onClick={() => handleDelete(comment.id)}
                        className="text-xs text-gray-500 hover:text-red-400 px-2 py-0.5 rounded"
                      >
                        Delete
                      </button>
                    </div>
                  )}
                </div>
              </>
            )}
          </div>
        ))}
      </div>

      <div className="mt-4 space-y-2">
        <textarea
          placeholder="Add a comment..."
          className="w-full bg-gray-900 text-sm text-white rounded-lg p-3 resize-none min-h-20 border border-gray-800 focus:outline-none focus:border-indigo-500"
          value={newBody}
          onChange={e => setNewBody(e.target.value)}
          rows={3}
        />
        <button
          onClick={handleAdd}
          disabled={!newBody.trim() || addComment.isPending}
          className="px-4 py-1.5 bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 text-white text-sm rounded"
        >
          {addComment.isPending ? 'Posting...' : 'Post Comment'}
        </button>
      </div>
    </div>
  )
}
```

Note: the old `<h3>Comments</h3>` header is intentionally removed here — Task 5's tab bar provides the label.

- [ ] **Step 4: Typecheck / build to verify it compiles**

Run: `cd frontend && npm run build`
Expected: PASS (no TypeScript errors).

- [ ] **Step 5: Manual verification**

Start the app, open an issue with 6+ comments. Confirm: only the 5 newest show (oldest of the five at top, newest at bottom); a "Load older comments" button appears at the top and loads 5 more older comments above; the list scrolls internally past ~26rem; posting a comment shows it at the bottom.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/api/comments.ts frontend/src/hooks/useComments.ts frontend/src/components/comments/CommentThread.tsx
git commit -m "feat(comments): paginate comment thread (last 5 + load older, scroll panel)"
```

---

## Task 4: Frontend — activity infinite query + paginated ActivityFeed

**Files:**
- Modify: `frontend/src/hooks/useComments.ts:11-16` (the `useActivity` hook)
- Modify: `frontend/src/components/comments/ActivityFeed.tsx`

**Interfaces:**
- Consumes: `commentsApi.listActivity(projectKey, issueKey, page?, size?) : Promise<AxiosResponse<Page<ActivityItem>>>` (already exists with `page`/`size` params).
- Produces: `useActivity(projectKey, issueKey)` returns a TanStack v5 infinite query: `data.pages: Page<ActivityItem>[]`, `fetchNextPage`, `hasNextPage`, `isFetchingNextPage`, `isLoading`.

- [ ] **Step 1: Convert `useActivity` to an infinite query**

In `frontend/src/hooks/useComments.ts`, replace the `useActivity` function (lines 11-16):

```ts
export function useActivity(projectKey: string, issueKey: string) {
  return useInfiniteQuery({
    queryKey: ['activity', projectKey, issueKey],
    queryFn: ({ pageParam }) =>
      commentsApi.listActivity(projectKey, issueKey, pageParam, 5).then(r => r.data),
    initialPageParam: 0,
    getNextPageParam: (lastPage) =>
      lastPage.number + 1 < lastPage.totalPages ? lastPage.number + 1 : undefined,
  })
}
```

(`useInfiniteQuery` is already imported from Task 3.)

- [ ] **Step 2: Rewrite ActivityFeed to consume paged data with a scroll panel and bottom "Load more"**

Replace the entire body of `frontend/src/components/comments/ActivityFeed.tsx` with:

```tsx
import type { ActivityItem } from '@/types'
import { useActivity } from '@/hooks/useComments'

const formatTime = (iso: string) => {
  const d = new Date(iso)
  return d.toLocaleDateString() + ' ' + d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

function describeActivity(item: ActivityItem): string {
  switch (item.type) {
    case 'COMMENT': return 'commented'
    case 'STATUS_CHANGED': return `changed status from "${item.oldValue}" to "${item.newValue}"`
    case 'ASSIGNED': return `assigned to ${item.newValue}`
    case 'UNASSIGNED': return `unassigned ${item.oldValue}`
    case 'PRIORITY_CHANGED': return `changed priority from ${item.oldValue} to ${item.newValue}`
    case 'TITLE_CHANGED': return `renamed to "${item.newValue}"`
    case 'DESCRIPTION_CHANGED': return 'updated description'
    case 'STORY_POINTS_CHANGED': return `changed story points from ${item.oldValue ?? '—'} to ${item.newValue}`
    case 'DUE_DATE_CHANGED': return `changed due date from ${item.oldValue ?? '—'} to ${item.newValue ?? '—'}`
    case 'SPRINT_CHANGED': return `moved to sprint ${item.newValue ?? '(backlog)'}`
    case 'ATTACHMENT_ADDED': return `added attachment: ${item.newValue}`
    case 'ATTACHMENT_REMOVED': return `removed attachment: ${item.oldValue}`
    default: return (item.type as string).toLowerCase().replace(/_/g, ' ')
  }
}

interface Props {
  projectKey: string
  issueKey: string
}

export function ActivityFeed({ projectKey, issueKey }: Props) {
  const { data, isLoading, fetchNextPage, hasNextPage, isFetchingNextPage } = useActivity(projectKey, issueKey)

  // Pages arrive newest-first; keep that order for the activity log.
  const items: ActivityItem[] = (data?.pages ?? []).flatMap(p => p.content)

  if (isLoading) return <div className="text-gray-500 text-sm">Loading activity...</div>

  if (items.length === 0) {
    return <p className="text-gray-600 text-sm italic">No activity yet</p>
  }

  return (
    <div className="max-h-[26rem] overflow-y-auto space-y-2 pr-1">
      {items.map((item: ActivityItem) => (
        <div key={item.id} className="flex gap-2 items-start text-sm">
          <div className="w-1.5 h-1.5 rounded-full bg-gray-600 mt-1.5 flex-shrink-0" />
          <div>
            <span className="text-gray-300">{describeActivity(item)}</span>
            <span className="text-gray-600 ml-2 text-xs">
              {formatTime(item.createdAt)}
            </span>
          </div>
        </div>
      ))}

      {hasNextPage && (
        <button
          onClick={() => fetchNextPage()}
          disabled={isFetchingNextPage}
          className="text-xs text-indigo-400 hover:text-indigo-300 disabled:opacity-50"
        >
          {isFetchingNextPage ? 'Loading...' : 'Load more'}
        </button>
      )}
    </div>
  )
}
```

Note: the old `<h3>Activity</h3>` header is intentionally removed — Task 5's tab bar provides the label.

- [ ] **Step 3: Typecheck / build to verify it compiles**

Run: `cd frontend && npm run build`
Expected: PASS.

- [ ] **Step 4: Manual verification**

Open an issue with 6+ activity events. Confirm: only the 5 newest show (newest at top); a "Load more" button at the bottom loads 5 older items; the list scrolls internally.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/hooks/useComments.ts frontend/src/components/comments/ActivityFeed.tsx
git commit -m "feat(activity): paginate activity feed (last 5 + load more, scroll panel)"
```

---

## Task 5: Frontend — Comments/Activity tabs + pinned-sidebar layout

**Files:**
- Create: `frontend/src/components/comments/CommentsActivityTabs.tsx`
- Modify: `frontend/src/components/issue/IssueDetailContent.tsx:21-22,64-105`
- Modify: `frontend/src/pages/issues/IssueDetailPage.tsx:7`

**Interfaces:**
- Consumes: `CommentThread(projectKey, issueKey, currentUserId?)`, `ActivityFeed(projectKey, issueKey)`.
- Produces: `CommentsActivityTabs(projectKey: string, issueKey: string, currentUserId?: string)` — a tabbed panel rendering one feed at a time.

- [ ] **Step 1: Create the tabs component**

Create `frontend/src/components/comments/CommentsActivityTabs.tsx`:

```tsx
import { useState } from 'react'
import { CommentThread } from './CommentThread'
import { ActivityFeed } from './ActivityFeed'

interface Props {
  projectKey: string
  issueKey: string
  currentUserId?: string
}

export function CommentsActivityTabs({ projectKey, issueKey, currentUserId }: Props) {
  const [tab, setTab] = useState<'comments' | 'activity'>('comments')

  return (
    <div>
      <div className="flex gap-1 border-b border-gray-800 mb-4">
        {(['comments', 'activity'] as const).map(t => (
          <button
            key={t}
            onClick={() => setTab(t)}
            className={`px-4 py-2 text-sm font-medium border-b-2 -mb-px transition-colors ${
              tab === t
                ? 'border-indigo-500 text-white'
                : 'border-transparent text-gray-400 hover:text-gray-200'
            }`}
          >
            {t === 'comments' ? 'Comments' : 'Activity'}
          </button>
        ))}
      </div>

      {tab === 'comments' ? (
        <CommentThread projectKey={projectKey} issueKey={issueKey} currentUserId={currentUserId} />
      ) : (
        <ActivityFeed projectKey={projectKey} issueKey={issueKey} />
      )}
    </div>
  )
}
```

- [ ] **Step 2: Wire the tabs into IssueDetailContent and switch to the pinned-sidebar flex layout**

In `frontend/src/components/issue/IssueDetailContent.tsx`:

Replace the two feed imports (lines 21-22):

```tsx
import { CommentThread } from '@/components/comments/CommentThread'
import { ActivityFeed } from '@/components/comments/ActivityFeed'
```

with:

```tsx
import { CommentsActivityTabs } from '@/components/comments/CommentsActivityTabs'
```

Change the layout wrapper (line 65) from:

```tsx
      <div className="grid grid-cols-3 gap-8">
```

to:

```tsx
      <div className="flex flex-col lg:flex-row gap-8">
```

Change the left column opening tag (line 67) from:

```tsx
        <div className="col-span-2 space-y-8">
```

to:

```tsx
        <div className="flex-1 min-w-0 space-y-8">
```

Replace the two stacked feed sections (lines 76-82):

```tsx
          <section>
            <CommentThread projectKey={projectKey} issueKey={issueKey} currentUserId={me?.id} />
          </section>

          <section>
            <ActivityFeed projectKey={projectKey} issueKey={issueKey} />
          </section>
```

with a single tabbed section:

```tsx
          <section>
            <CommentsActivityTabs projectKey={projectKey} issueKey={issueKey} currentUserId={me?.id} />
          </section>
```

Change the right column opening tag (line 105) from:

```tsx
        <div className="flex flex-col gap-4">
```

to:

```tsx
        <div className="w-full lg:w-80 shrink-0 flex flex-col gap-4">
```

- [ ] **Step 3: Make the full-page view full-width**

In `frontend/src/pages/issues/IssueDetailPage.tsx`, change line 7 from:

```tsx
    <div className="max-w-5xl">
```

to:

```tsx
    <div>
```

- [ ] **Step 4: Typecheck / build to verify it compiles**

Run: `cd frontend && npm run build`
Expected: PASS.

- [ ] **Step 5: Manual verification**

- Full-page issue view: content area fills the width, metadata sidebar is pinned to the right at a fixed width, attachments stay under the sidebar.
- The Comments/Activity tab bar switches between the two feeds in the same space; each scrolls internally and has its "Load more".
- Open the same issue as a modal via `?issue=<KEY>`: same tab/scroll behavior, sidebar pinned right within the capped modal width.
- Narrow the window: the sidebar drops below the content (stacked) instead of overflowing.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/comments/CommentsActivityTabs.tsx frontend/src/components/issue/IssueDetailContent.tsx frontend/src/pages/issues/IssueDetailPage.tsx
git commit -m "feat(issue): tabbed Comments/Activity panel + pinned sidebar full-width layout"
```

---

## Self-Review

**Spec coverage:**
- Tabs sharing one space → Task 5 (`CommentsActivityTabs`). ✓
- Full-page full-width, sidebar pinned right, content fills → Task 5 (`IssueDetailPage` drops `max-w-5xl`; `IssueDetailContent` flex with `flex-1` content + `w-80 shrink-0` sidebar). Modal inherits via shared component. ✓
- Internal scrollbars on each feed → Tasks 3 & 4 (`max-h-[26rem] overflow-y-auto`). ✓
- Load only last 5 + "Load more" → backend paging Tasks 1 (comments) & 2 (activity already paged, flattened); frontend infinite queries Tasks 3 & 4. ✓
- Comments oldest→newest with "Load more" at top; Activity newest-first with "Load more" at bottom → Tasks 3 & 4. ✓
- Deleted-comment exclusion + correct page counts → Task 1 (query-level filter). ✓
- Backend TDD; frontend typecheck+manual → reflected in each task's steps. ✓

**Placeholder scan:** No TBD/TODO/"handle edge cases"; every code step shows full code. ✓

**Type consistency:**
- `findByIssueIdAndDeletedAtIsNullOrderByCreatedAtDesc(issueId, pageable): Page<Comment>` — defined Task 1, used Task 1 service + test. ✓
- `listComments(projectKey, issueKey, userId, page, size): Page<Comment>` — Task 1 across service, controller, test. ✓
- `commentsApi.list(projectKey, issueKey, page?, size?)` returns `Page<Comment>` — Task 3 api + hook. ✓
- `useComments`/`useActivity` infinite-query shape (`data.pages`, `fetchNextPage`, `hasNextPage`, `isFetchingNextPage`) — produced Tasks 3/4, consumed by their components. ✓
- `getNextPageParam` uses `lastPage.number`/`lastPage.totalPages` — matches the flat `Page<T>` type and the flat Spring `Page` JSON from Tasks 1/2. ✓
- `CommentsActivityTabs(projectKey, issueKey, currentUserId?)` — defined Task 5, used in `IssueDetailContent`. ✓
