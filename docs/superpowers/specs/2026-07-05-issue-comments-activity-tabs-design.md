# Issue Comments/Activity Tabs, Pinned Sidebar & Paginated Feeds

**Date:** 2026-07-05
**Status:** Approved (design)

## Problem

On the issue detail view (`IssueDetailContent`, shared by the full-page
`IssueDetailPage` and the `?issue=` modal `IssueDialog`):

- **Comments** and **Activity** are two separate sections stacked vertically in the
  left column. They should instead be **tabs sharing the same space**.
- The layout is capped at `max-w-5xl`. On the full-page view the metadata
  **sidebar should be pinned to the right** while the description + comments/activity
  section **fills** the remaining width.
- Both feeds render **all** items with no height limit, so long issues produce very
  tall pages. Each feed should live in a **fixed-height, internally scrollable** panel.
- Both feeds load **everything** up front. They should load only the **5 most recent**
  items, with a **"Load more"** button to fetch older ones. Comments currently have
  **no backend paging** (the endpoint returns a flat `Comment[]`); Activity is already
  paginated (`PagedModel`, default `size=50`) but the frontend ignores paging.

## Goals

1. Comments and Activity become tabs occupying one shared panel.
2. Full-page view goes full-width; sidebar pinned right (`w-80`), content flexes to fill.
   Modal keeps its capped width but inherits the same internal structure.
3. Each feed scrolls internally within a fixed max height.
4. Each feed loads the 5 most recent items; "Load more" fetches the next 5 older ones.

## Non-Goals (YAGNI)

- Real-time / websocket updates.
- Infinite auto-scroll (explicit button only).
- Splitting Activity into a "History" tab that excludes comment events — the Activity
  tab keeps the full feed including `commented` rows, as today.
- Comment search or filtering.

## Current State (reference)

- `frontend/src/components/issue/IssueDetailContent.tsx` — `grid grid-cols-3 gap-8`;
  left `col-span-2` stacks Description → `CommentThread` → `ActivityFeed` → References;
  right column is the metadata sidebar + `AttachmentPanel`.
- `frontend/src/pages/issues/IssueDetailPage.tsx` — wraps content in `max-w-5xl`.
- `frontend/src/components/issue/IssueDialog.tsx` — modal, `max-w-5xl`, renders the same
  `IssueDetailContent`.
- `frontend/src/components/comments/CommentThread.tsx` — renders all comments + add box.
- `frontend/src/components/comments/ActivityFeed.tsx` — renders `data.content` (ignores paging).
- `frontend/src/api/comments.ts` — `list` returns `Comment[]` (no paging);
  `listActivity` takes `page`/`size` and returns `Page<ActivityItem>`.
- `frontend/src/hooks/useComments.ts` — `useComments` / `useActivity` are plain `useQuery`.
- `backend/.../comments/api/CommentController.kt` — `GET /comments` returns a plain list;
  `GET /activity` returns `PagedModel<ActivityResponse>` with `page`/`size` params.
- `backend/.../comments/application/CommentService.kt` — `listComments` returns
  `List<Comment>`, filtering `deletedAt == null` **in memory** after fetching all.
- `backend/.../comments/infrastructure/CommentRepository.kt` — only `findAllByIssueId`.

## Design

### 1. Layout

- **`IssueDetailPage`**: remove `max-w-5xl`; render full-width (retain existing page
  padding). This is the only page-width change.
- **`IssueDetailContent`**: replace `grid grid-cols-3 gap-8` with a responsive flex row:
  - Wrapper: `flex flex-col lg:flex-row gap-8`.
  - Main content (Description + tabs + References): `flex-1 min-w-0`.
  - Sidebar (metadata + `AttachmentPanel`): `w-80 shrink-0` — pinned right on `lg+`,
    stacked below on small screens.
- Because `IssueDetailContent` is shared, the **modal** inherits this structure with no
  extra change; it simply renders at its capped `max-w-5xl` width (sidebar still pinned
  right, content flexes).

### 2. Tabs — new `CommentsActivityTabs` component

`frontend/src/components/comments/CommentsActivityTabs.tsx`

- Replaces the two stacked `<section>`s for Comments and Activity in `IssueDetailContent`.
- Local `useState<'comments' | 'activity'>`, default `'comments'`.
- Renders a tab bar `[ Comments | Activity ]` styled consistently with existing UI,
  then the active feed inside a shared scroll container.
- Passes through the props the feeds already need (`projectKey`, `issueKey`, `currentUserId`).

### 3. Scroll behavior

- The **list region** of each feed (comment cards / activity rows) is wrapped in a
  container with `max-h-[26rem] overflow-y-auto`, so it scrolls internally instead of
  growing the page. (`26rem` is a starting value; tunable.)
- For Comments, the **add-comment box stays outside/below** the scroll container so it is
  always visible while the list scrolls.
- The tab bar stays above the scroll container.

### 4. Pagination — 5 most recent + "Load more" (react-query `useInfiniteQuery`, `size=5`)

**Comments (requires backend change):**

- `CommentRepository`: add
  `findByIssueIdAndDeletedAtIsNullOrderByCreatedAtDesc(issueId: UUID, pageable: Pageable): Page<Comment>`.
  Excluding deleted comments **and** ordering newest-first is done **in the query** — this
  is required so page counts / `totalElements` stay correct (filtering after paging would
  corrupt them).
- `CommentService.listComments(projectKey, issueKey, userId, page, size): Page<Comment>` —
  delegates to the new repository method (still validates issue access via
  `issueService.findByKey`).
- `CommentController` `GET /comments`: accept `@RequestParam page` (default `0`) and
  `size` (default `5`), return `PagedModel<CommentResponse>` — mirrors the existing
  `/activity` endpoint shape so the frontend reads both the same way.
- **Frontend display:** page 0 = the 5 newest comments. Comments render
  **oldest → newest** (each fetched page is reversed for display). The **"Load more"**
  button sits at the **top** of the list and prepends the next 5 older comments. The
  add-comment box remains at the bottom. After posting a comment, invalidate the comments
  infinite query so page 0 refetches and the new comment appears at the bottom.

**Activity (frontend-only, plus an order-by check):**

- Switch `useActivity` to `useInfiniteQuery` with `size=5`, newest-first. The **"Load more"**
  button sits at the **bottom** and appends the next 5 older items.
- Confirm `IssueActivityRepository` orders by `createdAt DESC`; add the ordering if it is
  not already guaranteed.

**"Load more" visibility:** shown while `loadedCount < totalElements`, read from the paged
response. Note: Spring's `PagedModel` serializes totals under a nested `page` object
(`{ content, page: { size, number, totalElements, totalPages } }`), which differs from the
current flat `Page<T>` frontend type (only `.content` is read today). The implementation
must read `totalElements` from whatever shape the endpoints actually return; align the
frontend `Page`/response typing accordingly for both comments and activity so `hasMore`
can be computed reliably.

### 5. API & hooks (frontend)

- `frontend/src/api/comments.ts`: `list` gains `page`/`size` params and returns the paged
  comment response type (aligned with `listActivity`).
- `frontend/src/hooks/useComments.ts`: `useComments` and `useActivity` become
  `useInfiniteQuery` (page param, `size=5`, `getNextPageParam` from totals). Comment
  mutations (`useAddComment`, `useEditComment`, `useDeleteComment`) keep invalidating the
  `comments` and `activity` query keys.

## Affected Files

**Backend**
- `comments/infrastructure/CommentRepository.kt` — new paged, deleted-excluding, ordered query.
- `comments/application/CommentService.kt` — `listComments` returns `Page<Comment>`.
- `comments/api/CommentController.kt` — paged `GET /comments`.
- `comments/infrastructure/IssueActivityRepository.kt` — verify/add `createdAt DESC` ordering.

**Frontend**
- `pages/issues/IssueDetailPage.tsx` — drop `max-w-5xl`.
- `components/issue/IssueDetailContent.tsx` — flex layout, pinned sidebar, use tabs component.
- `components/comments/CommentsActivityTabs.tsx` — **new** tab container + scroll panel.
- `components/comments/CommentThread.tsx` — infinite paging, "Load more" at top, scroll panel.
- `components/comments/ActivityFeed.tsx` — infinite paging, "Load more" at bottom, scroll panel.
- `api/comments.ts`, `hooks/useComments.ts`, `types/index.ts` — paged types + infinite queries.

## Testing

- **Backend (TDD):** repository/service/controller tests —
  - page 0 returns the 5 newest comments;
  - page 1 returns the next-older set;
  - deleted comments are excluded;
  - ordering is newest-first from the query.
  - Update `CommentServiceTest` and any integration test (e.g. `CollaborationIntegrationTest`)
    that assumed `listComments` returns a `List`.
- **Frontend:** no test framework in the project (per convention) → TypeScript typecheck +
  manual verification (tabs switch, scroll works, "Load more" fetches next 5 for both feeds,
  posting a comment shows it, layout pinned right on full-page and in modal).

## Open Values (tunable during implementation)

- Scroll max height (`max-h-[26rem]` starting point).
- Page size fixed at 5 per the requirement.
