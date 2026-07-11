# Sidebar Groups Collapsible (#10) + Logout Always Reachable (B3) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** In the expanded left sidebar, each section group (Admin, Account, Project, Settings) gets its own collapse toggle with per-group persistence (#10); and the Logout button stays reachable at any viewport height by making the nav list scroll internally instead of pushing the footer out of view (B3).

**Architecture:** Two frontend-only changes to the sidebar in `frontend/src/layouts/AppLayout.tsx`. B3 is a one-line-ish CSS change to `<nav>` (`min-h-0 overflow-y-auto`). #10 extracts the repeated `sectionLabel(...) + <div>` blocks into a focused `SidebarSection` component backed by a new `useSidebarSections` localStorage hook that mirrors the existing `useSidebarCollapsed` pattern.

**Tech Stack:** React + TypeScript, Tailwind CSS, `lucide-react` icons, `localStorage` for persistence. No frontend test framework — verification is `tsc --noEmit` + manual browser check.

## Global Constraints

- Frontend has **no** test framework — verification is `cd frontend && npx tsc --noEmit` (must be clean) + manual browser check. Fresh worktree checkouts lack `frontend/node_modules`; run `npm install` in `frontend/` first if `tsc` reports missing deps. Never run a docker build.
- Persistence pattern to mirror: `frontend/src/hooks/useSidebarCollapsed.ts` (localStorage key read on init via a lazy `useState` initializer; write on mutation).
- The existing whole-sidebar collapse state is `collapsed` from `useSidebarCollapsed()` — in this plan it is the **rail mode** signal. When `collapsed` is true (icon-rail), per-group collapse is disabled and all group items always render (current behavior must not regress).
- Default for #10: **all groups open** (absent localStorage entry = open).
- Groups that get a toggle: **Admin, Account, Project, Settings**. Top-level items (Dashboard, Projects, Organizations) have no section header and stay always-visible (no toggle).
- Icons come from `lucide-react` (already imported in `AppLayout.tsx`): use `ChevronDown` (open) / `ChevronRight` (collapsed) for the section toggle. `ChevronRight` is already imported; add `ChevronDown` to the import.
- Spec: `docs/superpowers/specs/2026-07-11-sidebar-groups-and-logout-design.md`.

---

### Task 1: B3 — make the nav list scroll internally so Logout stays pinned

**Files:**
- Modify: `frontend/src/layouts/AppLayout.tsx` (the `<nav>` element, currently `className="flex flex-col gap-1 flex-1"`)

**Interfaces:**
- Consumes: nothing new.
- Produces: nothing new — pure layout change. The `<aside>` shell (`h-screen overflow-hidden ... flex`) and the `mt-auto` footer block are unchanged.

- [ ] **Step 1: Apply the scroll fix to `<nav>`**

In `AppLayout.tsx`, change the nav opening tag from:

```tsx
        <nav className="flex flex-col gap-1 flex-1">
```

to:

```tsx
        <nav className="flex flex-col gap-1 flex-1 min-h-0 overflow-y-auto">
```

Rationale (do not add as a code comment): `min-h-0` lets the flex child shrink below its content height, which is what enables `overflow-y-auto` to actually scroll; the header and the `mt-auto` footer (OrgSwitcher / NotificationBell / Logout / VersionTag) stay fixed, so Logout is always reachable.

- [ ] **Step 2: Typecheck**

Run: `cd frontend && npx tsc --noEmit`
Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/layouts/AppLayout.tsx
git commit -m "fix(#B3): make sidebar nav scroll internally so Logout stays reachable"
```

- [ ] **Step 4: Manual verification (Wolfgang)**

Inside a project (so the Project + Settings groups render → tall sidebar), shrink the browser window height until the nav overflows: the nav list scrolls internally and the **Logout** button remains visible at the bottom. No double scrollbar between `<aside>` and `<main>`.

---

### Task 2: `useSidebarSections` persistence hook

**Files:**
- Create: `frontend/src/hooks/useSidebarSections.ts`

**Interfaces:**
- Consumes: `localStorage`.
- Produces:
  ```ts
  export function useSidebarSections(): {
    isOpen: (id: string) => boolean   // absent entry → true (default open)
    toggle: (id: string) => void      // flips open/closed for that id, persists
  }
  ```
  Storage key: `sidebar-sections-collapsed`; stored value is a JSON `Record<string, boolean>` where `true` means **collapsed** (so a missing key reads as open).

- [ ] **Step 1: Create the hook**

Create `frontend/src/hooks/useSidebarSections.ts` with exactly:

```ts
import { useCallback, useState } from 'react'

const STORAGE_KEY = 'sidebar-sections-collapsed'

type CollapsedMap = Record<string, boolean>

function readStored(): CollapsedMap {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return {}
    const parsed = JSON.parse(raw)
    return parsed && typeof parsed === 'object' ? (parsed as CollapsedMap) : {}
  } catch {
    return {}
  }
}

export function useSidebarSections(): {
  isOpen: (id: string) => boolean
  toggle: (id: string) => void
} {
  const [collapsed, setCollapsed] = useState<CollapsedMap>(readStored)

  const isOpen = useCallback((id: string) => collapsed[id] !== true, [collapsed])

  const toggle = useCallback((id: string) => {
    setCollapsed(prev => {
      const next = { ...prev, [id]: !prev[id] }
      localStorage.setItem(STORAGE_KEY, JSON.stringify(next))
      return next
    })
  }, [])

  return { isOpen, toggle }
}
```

- [ ] **Step 2: Typecheck**

Run: `cd frontend && npx tsc --noEmit`
Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/hooks/useSidebarSections.ts
git commit -m "feat(#10): add useSidebarSections persistence hook"
```

---

### Task 3: `SidebarSection` component

**Files:**
- Create: `frontend/src/components/nav/SidebarSection.tsx`

**Interfaces:**
- Consumes: `useSidebarSections` from Task 2 (`isOpen(id)`, `toggle(id)`); `ChevronDown` / `ChevronRight` from `lucide-react`.
- Produces:
  ```tsx
  interface SidebarSectionProps {
    id: string          // stable persistence key, e.g. 'admin' | 'account' | 'project' | 'project-settings'
    label: string       // header text
    railMode: boolean   // true = whole sidebar collapsed (icon rail)
    children: ReactNode
  }
  export function SidebarSection(props: SidebarSectionProps): JSX.Element
  ```

- [ ] **Step 1: Create the component**

Create `frontend/src/components/nav/SidebarSection.tsx` with exactly:

```tsx
import { type ReactNode } from 'react'
import { ChevronDown, ChevronRight } from 'lucide-react'
import { useSidebarSections } from '@/hooks/useSidebarSections'

interface SidebarSectionProps {
  id: string
  label: string
  railMode: boolean
  children: ReactNode
}

export function SidebarSection({ id, label, railMode, children }: SidebarSectionProps) {
  const { isOpen, toggle } = useSidebarSections()

  // Icon-rail mode: no header, no chevron — items always visible (unchanged behavior).
  if (railMode) {
    return <div className="flex flex-col gap-1">{children}</div>
  }

  const open = isOpen(id)
  return (
    <div>
      <button
        type="button"
        onClick={() => toggle(id)}
        aria-expanded={open}
        className="w-full flex items-center gap-1 px-3 mb-1 text-xs font-semibold text-gray-500 uppercase tracking-wider hover:text-gray-300"
      >
        {open ? <ChevronDown size={12} className="shrink-0" /> : <ChevronRight size={12} className="shrink-0" />}
        <span className="truncate">{label}</span>
      </button>
      {open && <div className="flex flex-col gap-1">{children}</div>}
    </div>
  )
}
```

Note: the header keeps the existing section-label styling (`text-xs font-semibold text-gray-500 uppercase tracking-wider`) so collapsed/expanded groups look consistent with today's labels, plus a chevron and hover affordance.

- [ ] **Step 2: Typecheck**

Run: `cd frontend && npx tsc --noEmit`
Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/nav/SidebarSection.tsx
git commit -m "feat(#10): add SidebarSection component (per-group collapse toggle)"
```

---

### Task 4: Wire the four groups in `AppLayout` to `SidebarSection`

**Files:**
- Modify: `frontend/src/layouts/AppLayout.tsx`

**Interfaces:**
- Consumes: `SidebarSection` (Task 3). Uses the existing `collapsed` from `useSidebarCollapsed()` as `railMode`.
- Produces: nothing new. Removes the now-unused local `sectionLabel` helper.

**Context:** Today each group is written as `{sectionLabel('X')}` followed by `<div className="flex flex-col gap-1">…NavItems…</div>`. There are four such groups: **Admin** and **Account** (always present), and **Project** and **Settings** (only rendered when `insideProject && projectKey`). You are replacing each group with a `<SidebarSection>` wrapper that provides the header+chevron and holds the same `NavItem`s as children. The `SidebarSection` renders its own inner `<div className="flex flex-col gap-1">`, so drop the old inner wrapper `<div>` when converting.

- [ ] **Step 1: Add the import**

In `AppLayout.tsx`, add alongside the existing `import { NavItem } from '@/components/nav/NavItem'`:

```tsx
import { SidebarSection } from '@/components/nav/SidebarSection'
```

- [ ] **Step 2: Remove the now-unused `sectionLabel` helper**

Delete these lines from the component body:

```tsx
  const sectionLabel = (text: string) =>
    !collapsed && (
      <p className="px-3 text-xs font-semibold text-gray-500 uppercase tracking-wider mb-1">{text}</p>
    )
```

- [ ] **Step 3: Convert the Admin group**

Replace:

```tsx
          <div className="mt-4">
            {sectionLabel('Admin')}
            <div className="flex flex-col gap-1">
              <NavItem to="/admin/audit" label="Audit Log" icon={ScrollText} collapsed={collapsed} variant="sub" />
              <NavItem to="/admin/automation" label="Automation" icon={Zap} collapsed={collapsed} variant="sub" />
              {me?.role === 'ADMIN' && (
                <NavItem to="/admin/users" label="Users" icon={Users} collapsed={collapsed} variant="sub" />
              )}
            </div>
          </div>
```

with:

```tsx
          <div className="mt-4">
            <SidebarSection id="admin" label="Admin" railMode={collapsed}>
              <NavItem to="/admin/audit" label="Audit Log" icon={ScrollText} collapsed={collapsed} variant="sub" />
              <NavItem to="/admin/automation" label="Automation" icon={Zap} collapsed={collapsed} variant="sub" />
              {me?.role === 'ADMIN' && (
                <NavItem to="/admin/users" label="Users" icon={Users} collapsed={collapsed} variant="sub" />
              )}
            </SidebarSection>
          </div>
```

- [ ] **Step 4: Convert the Account group**

Replace:

```tsx
          <div className="mt-4">
            {sectionLabel('Account')}
            <div className="flex flex-col gap-1">
              <NavItem to="/settings" label="Settings" icon={Settings} collapsed={collapsed} variant="sub" />
            </div>
          </div>
```

with:

```tsx
          <div className="mt-4">
            <SidebarSection id="account" label="Account" railMode={collapsed}>
              <NavItem to="/settings" label="Settings" icon={Settings} collapsed={collapsed} variant="sub" />
            </SidebarSection>
          </div>
```

- [ ] **Step 5: Convert the Project group**

Replace:

```tsx
            <div className="mt-4">
              {sectionLabel('Project')}
              <div className="flex flex-col gap-1">
                <NavItem to={`/p/${projectKey}/dashboard`} label="Dashboard" icon={LayoutDashboard} collapsed={collapsed} variant="sub" />
                <NavItem to={`/p/${projectKey}/board`} label="Board" icon={Kanban} collapsed={collapsed} variant="sub" />
                <NavItem to={`/p/${projectKey}/backlog`} label="Backlog" icon={ListChecks} collapsed={collapsed} variant="sub" />
                <NavItem to={`/p/${projectKey}/sprints`} label="Sprints" icon={CalendarRange} collapsed={collapsed} variant="sub" />
                <NavItem to={`/p/${projectKey}/issues`} label="Issues" icon={ListTodo} collapsed={collapsed} variant="sub" />
                <NavItem to={`/p/${projectKey}/reports`} label="Reports" icon={BarChart3} collapsed={collapsed} variant="sub" />
                <NavItem to={`/p/${projectKey}/automation`} label="Automation" icon={Zap} collapsed={collapsed} variant="sub" />
                {serviceDeskConfig?.enabled && (
                  <>
                    <NavItem to={`/p/${projectKey}/service-desk`} label="Service Desk" icon={LifeBuoy} collapsed={collapsed} variant="sub" />
                    <NavItem to={`/p/${projectKey}/incidents`} label="Incidents" icon={AlertTriangle} collapsed={collapsed} variant="sub" />
                  </>
                )}
              </div>
```

with (note: the outer `<div className="mt-4">` opening tag stays; only the `sectionLabel` + inner wrapper `<div>` are replaced by `<SidebarSection>`):

```tsx
            <div className="mt-4">
              <SidebarSection id="project" label="Project" railMode={collapsed}>
                <NavItem to={`/p/${projectKey}/dashboard`} label="Dashboard" icon={LayoutDashboard} collapsed={collapsed} variant="sub" />
                <NavItem to={`/p/${projectKey}/board`} label="Board" icon={Kanban} collapsed={collapsed} variant="sub" />
                <NavItem to={`/p/${projectKey}/backlog`} label="Backlog" icon={ListChecks} collapsed={collapsed} variant="sub" />
                <NavItem to={`/p/${projectKey}/sprints`} label="Sprints" icon={CalendarRange} collapsed={collapsed} variant="sub" />
                <NavItem to={`/p/${projectKey}/issues`} label="Issues" icon={ListTodo} collapsed={collapsed} variant="sub" />
                <NavItem to={`/p/${projectKey}/reports`} label="Reports" icon={BarChart3} collapsed={collapsed} variant="sub" />
                <NavItem to={`/p/${projectKey}/automation`} label="Automation" icon={Zap} collapsed={collapsed} variant="sub" />
                {serviceDeskConfig?.enabled && (
                  <>
                    <NavItem to={`/p/${projectKey}/service-desk`} label="Service Desk" icon={LifeBuoy} collapsed={collapsed} variant="sub" />
                    <NavItem to={`/p/${projectKey}/incidents`} label="Incidents" icon={AlertTriangle} collapsed={collapsed} variant="sub" />
                  </>
                )}
              </SidebarSection>
```

- [ ] **Step 6: Convert the Settings group**

Replace:

```tsx
              <div className="mt-4">
                {sectionLabel('Settings')}
                <div className="flex flex-col gap-1">
                  {currentProject?.myRole === 'ADMIN' && (
                    <NavItem to={`/p/${projectKey}/settings/members`} label="Members" icon={UserCog} collapsed={collapsed} variant="sub" />
                  )}
                  <NavItem to={`/p/${projectKey}/settings/api-keys`} label="API Keys" icon={KeySquare} collapsed={collapsed} variant="sub" />
                  <NavItem to={`/p/${projectKey}/settings/webhooks`} label="Webhooks" icon={Webhook} collapsed={collapsed} variant="sub" />
                  <NavItem to={`/p/${projectKey}/settings/integrations`} label="Integrations" icon={Plug} collapsed={collapsed} variant="sub" />
                  <NavItem to={`/p/${projectKey}/settings/audit`} label="Audit Log" icon={ScrollText} collapsed={collapsed} variant="sub" />
                  <NavItem to={`/p/${projectKey}/settings/labels`} label="Labels" icon={Tags} collapsed={collapsed} variant="sub" />
                  <NavItem to={`/p/${projectKey}/settings/versions`} label="Versions" icon={Milestone} collapsed={collapsed} variant="sub" />
                  <NavItem to={`/p/${projectKey}/settings/custom-fields`} label="Custom Fields" icon={SlidersHorizontal} collapsed={collapsed} variant="sub" />
                </div>
              </div>
```

with:

```tsx
              <div className="mt-4">
                <SidebarSection id="project-settings" label="Settings" railMode={collapsed}>
                  {currentProject?.myRole === 'ADMIN' && (
                    <NavItem to={`/p/${projectKey}/settings/members`} label="Members" icon={UserCog} collapsed={collapsed} variant="sub" />
                  )}
                  <NavItem to={`/p/${projectKey}/settings/api-keys`} label="API Keys" icon={KeySquare} collapsed={collapsed} variant="sub" />
                  <NavItem to={`/p/${projectKey}/settings/webhooks`} label="Webhooks" icon={Webhook} collapsed={collapsed} variant="sub" />
                  <NavItem to={`/p/${projectKey}/settings/integrations`} label="Integrations" icon={Plug} collapsed={collapsed} variant="sub" />
                  <NavItem to={`/p/${projectKey}/settings/audit`} label="Audit Log" icon={ScrollText} collapsed={collapsed} variant="sub" />
                  <NavItem to={`/p/${projectKey}/settings/labels`} label="Labels" icon={Tags} collapsed={collapsed} variant="sub" />
                  <NavItem to={`/p/${projectKey}/settings/versions`} label="Versions" icon={Milestone} collapsed={collapsed} variant="sub" />
                  <NavItem to={`/p/${projectKey}/settings/custom-fields`} label="Custom Fields" icon={SlidersHorizontal} collapsed={collapsed} variant="sub" />
                </SidebarSection>
              </div>
```

- [ ] **Step 7: Typecheck**

Run: `cd frontend && npx tsc --noEmit`
Expected: no errors. Note: the deleted `sectionLabel` helper referenced no `lucide-react` icon (only a `<p>`), so removing it orphans no imports. `AppLayout`'s existing `ChevronLeft`/`ChevronRight` imports stay used by the whole-sidebar rail toggle. (`ChevronDown` lives only in `SidebarSection`, not `AppLayout`.)

- [ ] **Step 8: Commit**

```bash
git add frontend/src/layouts/AppLayout.tsx
git commit -m "feat(#10): wire sidebar groups to collapsible SidebarSection"
```

- [ ] **Step 9: Manual verification (Wolfgang)**

- Expanded sidebar: each of Admin / Account / Project / Settings shows a chevron; clicking collapses/expands just that group's items. Default (fresh localStorage) = all open.
- Reload the page → each group's open/closed state persists.
- Collapse the whole sidebar (icon-rail): no chevrons/headers, all icons visible, no regression.
- Combined with B3: collapse groups and/or shrink the viewport — Logout stays reachable, nav scrolls internally.

---

## Self-Review

**1. Spec coverage** (`2026-07-11-sidebar-groups-and-logout-design.md`):
- B3 nav internally scrollable (`min-h-0 overflow-y-auto`), footer pinned: Task 1 ✓
- #10 `SidebarSection` component (id/label/railMode/children; rail mode = children only, no header): Task 3 ✓
- #10 `useSidebarSections` hook, key `sidebar-sections-collapsed`, `Record<string, boolean>`, absent = open, default all open: Task 2 ✓
- #10 toggles on Admin/Account/Project/Settings; top-level items not collapsible: Task 4 (only the four groups converted; Dashboard/Projects/Organizations left as bare `NavItem`s) ✓
- Chevron `ChevronDown`/`ChevronRight`, `aria-expanded`: Task 3 ✓
- Rail mode disables feature / groups always visible: Task 3 (`if (railMode)` branch) + Task 4 (`railMode={collapsed}`) ✓
- Refactor removes repeated `sectionLabel + <div>`: Task 4, Step 2 (helper deleted) ✓

**2. Placeholder scan:** No TBD/TODO; every code step shows complete code. ✓

**3. Type consistency:** `useSidebarSections()` returns `{ isOpen(id): boolean, toggle(id): void }` (Task 2) — consumed exactly that way in `SidebarSection` (Task 3). `SidebarSectionProps { id, label, railMode, children }` (Task 3) — used with those exact props in Task 4. `railMode={collapsed}` uses the existing `collapsed: boolean` from `useSidebarCollapsed()`. Storage key string `sidebar-sections-collapsed` is identical in hook and spec. ✓
