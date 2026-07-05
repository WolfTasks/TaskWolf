# Phase 3 — Sprint-Übersicht + aufgeschobene Phase-1-Minors — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a read-only Sprint-Übersicht page (`/p/:key/sprints`) grouping sprints by status, and fix the three deferred Phase-1 interaction minors on the board card and issue dialog host.

**Architecture:** Pure frontend. A new `SprintCard` presentational component and a `SprintsPage` that groups the existing `useSprints(key)` result by status (ACTIVE/PLANNED/CLOSED) and links each group to a context-appropriate page. A new route + nav entry wire it in. The three minors are surgical edits to two existing files (`DraggableCard.tsx`, `IssueDialogHost.tsx`). No backend change, no new dependency, no data-model change.

**Tech Stack:** React 19, TypeScript 6, react-router-dom 7, @tanstack/react-query 5, Tailwind 4, @dnd-kit/core 6. Existing conventions: `useParams<{ key: string }>()`, `useSprints`, `cn` from `@/lib/utils`, dark theme (`bg-gray-950`/`gray-900`/`gray-800`).

## Global Constraints

- **No frontend test framework.** The frontend has zero test files and no Vitest/RTL — this is a deliberate, standing project decision. The "test cycle" for every task below is **`npm run build`** (which runs `tsc && vite build`, i.e. a full typecheck) **plus the explicit manual verification steps in that task.** Do not add a test framework.
- **Run the build from the `frontend/` directory:** `cd frontend && npm run build`. Expected on success: `tsc` emits nothing and `vite build` ends with `✓ built in …`. Any red TypeScript error = fail.
- **Read-only Sprint-Übersicht.** No sprint actions (start/complete/edit) on the new page — those stay in Backlog/Board (spec Nicht-Ziele).
- **No backend change.** `useSprints(key)` already returns everything needed.
- **Follow existing dark-theme styling** copied from `SprintHeader.tsx` / `BacklogPage.tsx` (`gray-900` panels, `gray-800` borders, `blue-500` progress fill).
- **Reference spec:** `docs/superpowers/specs/2026-07-04-agile-ux-story-points-sprints-dialog-design.md` (Phase 3 = lines 121–147; Addendum minors section follows it).
- **Final task of the workstream:** update wiki/mkdocs docs (see Task 7) — mandatory per project convention.

---

## File Structure

**Create:**
- `frontend/src/components/sprint/SprintCard.tsx` — one read-only card for a single sprint (name, goal, date range, points metric, optional progress bar). Presentational; parent supplies an `onClick`.
- `frontend/src/pages/sprints/SprintsPage.tsx` — loads `useSprints`, groups by status, renders three sections of `SprintCard`s with context-appropriate navigation and empty-state hints.

**Modify:**
- `frontend/src/app/router.tsx` — add the `/p/:key/sprints` route.
- `frontend/src/layouts/AppLayout.tsx` — add the "Sprints" nav entry between Backlog and Issues.
- `frontend/src/components/board/DraggableCard.tsx` — Minor 1 (keyboard open) + Minor 2 (drag-guard hardening).
- `frontend/src/components/issue/IssueDialogHost.tsx` — Minor 3 (no double editor over full page).

**Docs:**
- mkdocs pages under `docs/` (frontend components/overview) — Task 7.

---

## Task 1: `SprintCard` component

**Files:**
- Create: `frontend/src/components/sprint/SprintCard.tsx`

**Interfaces:**
- Consumes: `Sprint` from `@/types` — `{ id, name, goal: string|null, status: 'PLANNED'|'ACTIVE'|'CLOSED', startDate: string|null, endDate: string|null, plannedPoints: number|null, completedPoints: number|null }`; `cn` from `@/lib/utils`.
- Produces: `export function SprintCard(props: { sprint: Sprint; onClick: () => void }): JSX.Element`. Consumed by Task 2.

- [ ] **Step 1: Write the component**

Create `frontend/src/components/sprint/SprintCard.tsx`:

```tsx
import type { Sprint } from '@/types'
import { cn } from '@/lib/utils'

interface Props {
  sprint: Sprint
  onClick: () => void
}

function formatRange(start: string | null, end: string | null): string | null {
  if (!start && !end) return null
  const fmt = (iso: string) => new Date(iso).toLocaleDateString()
  if (start && end) return `${fmt(start)} – ${fmt(end)}`
  return fmt((start ?? end)!)
}

const statusStyle: Record<Sprint['status'], string> = {
  ACTIVE: 'border-blue-500/60 bg-blue-500/5',
  PLANNED: 'border-gray-800 bg-gray-900/50',
  CLOSED: 'border-gray-800 bg-gray-900/30',
}

export function SprintCard({ sprint, onClick }: Props) {
  const range = formatRange(sprint.startDate, sprint.endDate)
  const planned = sprint.plannedPoints ?? 0
  const completed = sprint.completedPoints ?? 0
  const pct = planned > 0 ? Math.round((completed / planned) * 100) : 0

  return (
    <button
      onClick={onClick}
      className={cn(
        'w-full text-left rounded-lg border p-4 hover:border-gray-600 transition-colors',
        statusStyle[sprint.status],
      )}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h3 className="text-sm font-semibold text-white truncate">{sprint.name}</h3>
          {sprint.goal && <p className="text-xs text-gray-400 mt-0.5 line-clamp-2">{sprint.goal}</p>}
        </div>
        <span className="text-xs text-gray-500 whitespace-nowrap shrink-0">
          {completed} / {planned} pts
        </span>
      </div>
      {range && <p className="text-xs text-gray-500 mt-2">{range}</p>}
      {sprint.status === 'ACTIVE' && planned > 0 && (
        <div className="mt-2 h-1.5 bg-gray-800 rounded-full overflow-hidden">
          <div className="h-full bg-blue-500 transition-all" style={{ width: `${pct}%` }} />
        </div>
      )}
    </button>
  )
}
```

- [ ] **Step 2: Typecheck**

Run: `cd frontend && npm run build`
Expected: PASS — `tsc` clean, `vite build` ends with `✓ built in …`. (Component is not yet imported anywhere; this only proves it compiles in isolation.)

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/sprint/SprintCard.tsx
git commit -m "feat(sprints): add read-only SprintCard component"
```

---

## Task 2: `SprintsPage` — grouped overview

**Files:**
- Create: `frontend/src/pages/sprints/SprintsPage.tsx`

**Interfaces:**
- Consumes: `SprintCard` (Task 1); `useSprints` from `@/hooks/useSprints` returning `{ data: Sprint[] | undefined, isLoading: boolean }`; `useParams`, `useNavigate` from `react-router-dom`.
- Produces: `export function SprintsPage(): JSX.Element`. Consumed by Task 3 (router).

**Navigation targets (spec lines 141–146):** ACTIVE → `/p/:key/board`, PLANNED → `/p/:key/backlog`, CLOSED → `/p/:key/reports`.

- [ ] **Step 1: Write the page**

Create `frontend/src/pages/sprints/SprintsPage.tsx`:

```tsx
import { useParams, useNavigate } from 'react-router-dom'
import { useSprints } from '@/hooks/useSprints'
import { SprintCard } from '@/components/sprint/SprintCard'
import type { Sprint } from '@/types'

export function SprintsPage() {
  const { key } = useParams<{ key: string }>()
  const navigate = useNavigate()
  const { data: sprints, isLoading } = useSprints(key!)

  if (isLoading) return <div className="text-gray-400">Loading...</div>

  const all = sprints ?? []
  const active = all.filter(s => s.status === 'ACTIVE')
  const planned = all.filter(s => s.status === 'PLANNED')
  const closed = all
    .filter(s => s.status === 'CLOSED')
    .sort((a, b) => (b.endDate ?? '').localeCompare(a.endDate ?? ''))

  const go = (target: string) => () => navigate(`/p/${key}/${target}`)

  const Section = ({
    title,
    items,
    target,
    emptyHint,
  }: {
    title: string
    items: Sprint[]
    target: string
    emptyHint: string
  }) => (
    <section className="mb-8">
      <h2 className="text-lg font-semibold text-white mb-3">{title}</h2>
      {items.length === 0 ? (
        <p className="text-gray-500 text-sm">{emptyHint}</p>
      ) : (
        <div className="grid gap-3 sm:grid-cols-2">
          {items.map(s => (
            <SprintCard key={s.id} sprint={s} onClick={go(target)} />
          ))}
        </div>
      )}
    </section>
  )

  return (
    <div className="max-w-4xl">
      <h1 className="text-2xl font-bold mb-6">Sprints</h1>
      <Section title="Laufend" items={active} target="board" emptyHint="Kein laufender Sprint." />
      <Section title="Geplant" items={planned} target="backlog" emptyHint="Keine geplanten Sprints." />
      <Section title="Abgeschlossen" items={closed} target="reports" emptyHint="Noch keine abgeschlossenen Sprints." />
    </div>
  )
}
```

- [ ] **Step 2: Typecheck**

Run: `cd frontend && npm run build`
Expected: PASS — `tsc` clean, `vite build` succeeds. (Page compiles; still not routed — Task 3 wires it.)

- [ ] **Step 3: Commit**

```bash
git add frontend/src/pages/sprints/SprintsPage.tsx
git commit -m "feat(sprints): add SprintsPage grouping sprints by status"
```

---

## Task 3: Wire route + navigation entry

**Files:**
- Modify: `frontend/src/app/router.tsx`
- Modify: `frontend/src/layouts/AppLayout.tsx`

**Interfaces:**
- Consumes: `SprintsPage` (Task 2).
- Produces: reachable route `/p/:key/sprints` and a "Sprints" nav link.

- [ ] **Step 1: Add the import to the router**

In `frontend/src/app/router.tsx`, add after line 12 (`import { BacklogPage } …`):

```tsx
import { SprintsPage } from '@/pages/sprints/SprintsPage'
```

- [ ] **Step 2: Add the route**

In the same file, add directly after the `backlog` route (`{ path: '/p/:key/backlog', element: <BacklogPage /> },`):

```tsx
      { path: '/p/:key/sprints', element: <SprintsPage /> },
```

- [ ] **Step 3: Add the nav entry**

In `frontend/src/layouts/AppLayout.tsx`, add a `NavLink` between the Backlog and Issues links (currently lines 60–61):

```tsx
                <NavLink to={`/p/${projectKey}/backlog`} className={subNavLinkClass}>Backlog</NavLink>
                <NavLink to={`/p/${projectKey}/sprints`} className={subNavLinkClass}>Sprints</NavLink>
                <NavLink to={`/p/${projectKey}/issues`} className={subNavLinkClass}>Issues</NavLink>
```

- [ ] **Step 4: Typecheck**

Run: `cd frontend && npm run build`
Expected: PASS.

- [ ] **Step 5: Manual verification**

Run `cd frontend && npm run dev`, open a project. Verify:
- A "Sprints" entry appears in the Project nav between Backlog and Issues.
- Clicking it loads `/p/<key>/sprints` showing three sections (Laufend / Geplant / Abgeschlossen).
- Empty groups show their hint text; non-empty groups show cards.
- Clicking a card in Laufend → Board; Geplant → Backlog; Abgeschlossen → Reports.
- The active-sprint card shows a progress bar.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/router.tsx frontend/src/layouts/AppLayout.tsx
git commit -m "feat(sprints): add /sprints route and nav entry"
```

---

## Task 4: Minor 1 & 2 — board card keyboard open + drag-guard hardening

**Files:**
- Modify: `frontend/src/components/board/DraggableCard.tsx`

**Context (why):**
- **Minor 1:** the card is a focusable `role="button"` div (dnd-kit `attributes`), but Enter/Space on a div does not fire `onClick`, and the current `onClick` bails when `downPos` is null (no pointer). The board `DndContext` uses **only** a `PointerSensor` (no `KeyboardSensor`), so Enter/Space are free to repurpose for opening.
- **Minor 2:** today's guard is net displacement `hypot(now − down) < 5`, so a drag out-and-back within 5px both drops **and** opens. Replace with "did a real drag actually happen" tracked via a ref that flips when `isDragging` becomes true.

**Interfaces:**
- Consumes: `useDraggable` (`{ attributes, listeners, setNodeRef, transform, isDragging }`), `useOpenIssue()`, `Issue`.
- Produces: unchanged public surface — still `export function DraggableCard({ issue }: { issue: Issue })`.

- [ ] **Step 1: Rewrite the component with both fixes**

Replace the entire body of `frontend/src/components/board/DraggableCard.tsx` with:

```tsx
import { useEffect, useRef } from 'react'
import { useDraggable } from '@dnd-kit/core'
import { CSS } from '@dnd-kit/utilities'
import { cn } from '@/lib/utils'
import { useOpenIssue } from '@/hooks/useOpenIssue'
import type { Issue } from '@/types'

const priorityColor: Record<string, string> = {
  CRITICAL: 'text-red-400',
  HIGH: 'text-orange-400',
  MEDIUM: 'text-yellow-400',
  LOW: 'text-green-400',
}

interface Props { issue: Issue }

export function DraggableCard({ issue }: Props) {
  const { attributes, listeners, setNodeRef, transform, isDragging } = useDraggable({ id: issue.id })
  const openIssue = useOpenIssue()
  // Tracks whether a real drag occurred during the current pointer gesture.
  const dragged = useRef(false)

  // Flip `dragged` the moment dnd-kit reports an active drag; it stays true
  // until the next pointerdown resets it, so an out-and-back gesture still counts.
  useEffect(() => {
    if (isDragging) dragged.current = true
  }, [isDragging])

  return (
    <div
      ref={setNodeRef}
      style={{ transform: CSS.Translate.toString(transform) }}
      {...attributes}
      {...listeners}
      onPointerDownCapture={() => { dragged.current = false }}
      onClick={() => {
        if (!dragged.current) openIssue(issue.key)
      }}
      onKeyDown={e => {
        if (isDragging) return
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault()
          openIssue(issue.key)
        }
      }}
      className={cn(
        'bg-gray-900 border border-gray-800 rounded-lg p-3 cursor-grab active:cursor-grabbing select-none',
        isDragging && 'opacity-50 border-blue-500 z-50'
      )}
    >
      <div className="text-xs text-gray-500 font-mono mb-1">{issue.key}</div>
      <div className="text-sm text-white mb-2 line-clamp-2">{issue.title}</div>
      <div className="flex items-center gap-2">
        <span className={cn('text-xs font-medium', priorityColor[issue.priority] ?? 'text-gray-400')}>
          {issue.priority}
        </span>
        {issue.storyPoints != null && (
          <span className="ml-auto text-xs bg-gray-800 text-gray-400 px-1.5 py-0.5 rounded font-mono">
            {issue.storyPoints}
          </span>
        )}
      </div>
    </div>
  )
}
```

Note: the `downPos` ref and `Math.hypot` distance check are intentionally removed — the drag decision now comes from dnd-kit's own `isDragging`, which is authoritative and immune to the net-displacement bug.

- [ ] **Step 2: Typecheck**

Run: `cd frontend && npm run build`
Expected: PASS.

- [ ] **Step 3: Manual verification**

`cd frontend && npm run dev`, open a Board with at least one card:
- **Click open:** a plain click on a card opens the issue modal (`?issue=` in URL).
- **Drag no-open:** press-drag a card to another column and drop → the modal does **not** open; the move persists.
- **Out-and-back no-open (Minor 2):** press a card, drag it a few px away and back near the start, release → modal does **not** open.
- **Keyboard open (Minor 1):** Tab until a card is focused (visible focus ring), press Enter → modal opens; close it, focus a card, press Space → modal opens and the page does not scroll.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/board/DraggableCard.tsx
git commit -m "fix(board): keyboard-open cards and harden drag-vs-click guard"
```

---

## Task 5: Minor 3 — no double editor over the full page

**Files:**
- Modify: `frontend/src/components/issue/IssueDialogHost.tsx`

**Context (why):** `IssueDialogHost` is mounted globally in `AppLayout`. A manual `?issue=KEY` while already on `/p/:key/issues/:issueKey` stacks the modal over the identical full-page editor. Suppress the modal when the current route is that issue's own full-page route.

**Interfaces:**
- Consumes: `useSearchParams`, `useMatch` from `react-router-dom`; `IssueDialog`.
- Produces: unchanged public surface — `export function IssueDialogHost({ projectKey }: { projectKey: string })`.

- [ ] **Step 1: Add the full-page-route guard**

Replace the contents of `frontend/src/components/issue/IssueDialogHost.tsx` with:

```tsx
import { useCallback } from 'react'
import { useSearchParams, useMatch } from 'react-router-dom'
import { IssueDialog } from '@/components/issue/IssueDialog'

interface Props { projectKey: string }

export function IssueDialogHost({ projectKey }: Props) {
  const [searchParams, setSearchParams] = useSearchParams()
  const issueKey = searchParams.get('issue')
  // When we are already on the issue's own full-page route, ignore ?issue= for
  // the *same* issue so the modal never stacks a second editor over the page.
  const fullPageMatch = useMatch('/p/:key/issues/:issueKey')

  const close = useCallback(() => {
    setSearchParams(prev => {
      const next = new URLSearchParams(prev)
      next.delete('issue')
      return next
    })
  }, [setSearchParams])

  if (!issueKey) return null
  if (fullPageMatch?.params.issueKey === issueKey) return null
  return <IssueDialog projectKey={projectKey} issueKey={issueKey} onClose={close} />
}
```

Note: the guard is scoped to the *same* key (`=== issueKey`), so a modal for a *different* issue can still be opened while viewing a full-page issue.

- [ ] **Step 2: Typecheck**

Run: `cd frontend && npm run build`
Expected: PASS.

- [ ] **Step 3: Manual verification**

`cd frontend && npm run dev`:
- Navigate to a full-page issue `/p/<key>/issues/<ISSUE-KEY>`. Append `?issue=<ISSUE-KEY>` (same key) to the URL → **no** modal appears (no double editor); the full page renders normally.
- On that same full page, append `?issue=<OTHER-KEY>` (a different issue) → the modal **does** open over the page.
- From a Board/Backlog page, opening any issue via click still shows the modal as before.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/issue/IssueDialogHost.tsx
git commit -m "fix(issue-dialog): suppress modal over the same issue's full page"
```

---

## Task 6: Full regression build

**Files:** none (verification only)

- [ ] **Step 1: Clean build**

Run: `cd frontend && npm run build`
Expected: PASS — `tsc` clean, `vite build` ends with `✓ built in …`.

- [ ] **Step 2: Manual smoke of the whole workstream**

`cd frontend && npm run dev`:
- Sprints page reachable from nav; three groups; context links work; active progress bar shows.
- Board: click-open, drag-no-open, out-and-back-no-open, keyboard-open all behave.
- Full-page issue + `?issue=` same key → no double editor; different key → modal.
- Story-Points editor and issue modal (Phases 1–2) still work — no regressions.

No commit (verification only).

---

## Task 7: Documentation (mandatory final task)

**Files:**
- Modify: the frontend docs pages under `docs/` (e.g. `docs/frontend/components.md` / `docs/frontend/overview.md` — locate the existing "components" and "pages/routes" listings and follow their format).

**Interfaces:** none (docs only).

- [ ] **Step 1: Locate the docs pages**

Run: `ls docs/frontend` (and open the file that lists components/pages). If the exact filenames differ, use the ones that enumerate frontend components and routes.

- [ ] **Step 2: Document the new surface**

Add, in the existing style of those pages:
- **Sprints page** — route `/p/:key/sprints`, read-only overview grouping sprints into Laufend/Geplant/Abgeschlossen, each group linking to Board/Backlog/Reports.
- **`SprintCard`** — presentational card (name, goal, date range, points, progress bar for active sprints).
- **Board-card interaction note** — cards open the issue modal on click or Enter/Space (keyboard-accessible); a drag never opens the modal.
- **`IssueDialogHost` note** — suppresses the modal when already on the same issue's full-page route (no double editor).

- [ ] **Step 3: Commit**

```bash
git add docs/
git commit -m "docs(frontend): document Sprints page, SprintCard and board/dialog interaction fixes"
```

---

## Self-Review

**Spec coverage (Phase 3, lines 121–147):**
- New route `/p/:key/sprints` → Task 3. ✅
- "Sprints" nav entry between Backlog and Issues → Task 3. ✅
- Grouping ACTIVE/PLANNED/CLOSED client-side via `useSprints`, CLOSED chronologically descending → Task 2 (`.sort` on `endDate`). ✅
- `SprintCard`: name, goal, date range, points metric, progress bar for ACTIVE, read-only → Task 1. ✅
- Context navigation ACTIVE→board / PLANNED→backlog / CLOSED→reports → Task 2 (`go(target)`). ✅
- Empty groups show a hint instead of vanishing → Task 2 (`emptyHint`). ✅
- No backend change → confirmed; no backend task. ✅

**Spec coverage (Addendum minors):**
- Minor 1 keyboard open (Enter/Space, `!isDragging`, preventDefault) → Task 4. ✅
- Minor 2 drag-guard via "drag actually happened" ref → Task 4. ✅
- Minor 3 double-editor guard via `useMatch` same-key check → Task 5. ✅
- Testing = build + manual (no framework) → Global Constraints + every task. ✅
- Wiki/mkdocs docs as final task → Task 7. ✅

**Placeholder scan:** No TBD/TODO; every code step shows full code; every verify step gives an exact command and expected result.

**Type consistency:** `SprintCard({ sprint, onClick })` defined in Task 1 and consumed exactly so in Task 2. `SprintsPage` (no props) defined in Task 2, imported/routed in Task 3. `DraggableCard({ issue })` and `IssueDialogHost({ projectKey })` public signatures unchanged. `Sprint` fields used match `types/index.ts`.
