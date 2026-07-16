# i18n Rollout — Session 7 (`dashboard` namespace) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Localize the S7 `dashboard` slice — the project-list home (`DashboardPage`), the per-project widget dashboard (`ProjectDashboardPage`), its chrome (`WidgetWrapper`, `WidgetPalette`), and all six widgets — into a new `dashboard` namespace (`en`/`de`), including the scanner-blind const-map labels (`WIDGET_TITLES`, `WIDGET_OPTIONS`, `FILTER_LABELS`, `CATEGORY_LABEL`) and recharts legend/axis/tooltip labels, then flip S7 to ✅ in the master-spec coverage matrix.

**Architecture:** Thin execution plan against the locked pattern in `docs/superpowers/specs/2026-07-13-i18n-full-rollout-design.md` (Anhang — Migrations-Checkliste). One new `dashboard` namespace covers the whole slice. Scanner-blind labels held in module-level `const` maps are localized at their JSX render site (S2 enum-label precedent), not by translating the map values. `DashboardCanvas.tsx` renders only layout/data and is string-free — it stays out of scope and out of the allowlist.

**Tech Stack:** react-i18next (`useTranslation`), recharts (`name`/`label`/`formatter` props for chart series), the Session-0 scanner/parity scripts, Node 20, Vite/tsc build.

## Global Constraints

- **Namespace = `dashboard`.** In each file use `const { t } = useTranslation('dashboard')`; reference shared terms with the `common:` prefix (`t('common:loading')`, `t('common:save')`, `t('common:saving')`, `t('common:cancel')`). Unprefixed keys resolve against `dashboard`.
- **en/de stay key-identical** (parity check is authoritative).
- **Scanner-blind const-map labels are DoD-required.** `WIDGET_TITLES` (ProjectDashboardPage), `WIDGET_OPTIONS[].label` + `ISSUE_LIST_FILTERS[].label` (WidgetPalette), `FILTER_LABELS` (IssueListWidget) and `CATEGORY_LABEL` (IssuesByStatusWidget) hold user-facing English in module-level objects. The scanner does NOT flag them (object-property values, not JSX text/attributes), which is why several of these files are in the allowlist only for their *other* JSX strings. Localize each at the render site via `t(...)` keyed on the raw enum — do **not** translate the map values in place. Leave the enum keys and any `*_COLOR` className maps untouched (they key off the raw enum).
- **recharts series/axis/tooltip labels are user-facing.** Keep every `dataKey` unchanged (it must match the chart-data object key), and add a `name={t(...)}` prop to each `<Line>`/`<Bar>` so the Legend/Tooltip show localized text. Localize the `label={{ value: … }}` axis caption and the `Tooltip` `formatter` label string too. The numeric unit suffix (`` `${v}h` ``) stays literal.
- **No string-concatenation of translated fragments.** The `+`/`✕` glyphs stay as literal JSX next to `{t(...)}` — they are markers, not translated fragments.
- **`DashboardCanvas.tsx` is string-free — leave it untouched** and do not add it to the allowlist.
- **Remove all 10 S7 files from `frontend/scripts/i18n-allowlist.json`** as they are localized: `DashboardPage`, `ProjectDashboardPage`, `WidgetPalette`, `WidgetWrapper`, `BurndownWidget`, `VelocityWidget`, `CycleTimeWidget`, `IssueCountWidget`, `IssuesByStatusWidget`, `IssueListWidget`. **Net for S7: 10 files removed (45 → 35).**
- **Done-per-slice (master spec Abschnitt 5):** `npm run test:i18n && npm run lint:i18n && npm run build` all green; manual DE/EN browser check; matrix row flipped to ✅.
- All paths relative to repo root `C:\Users\Admin\IdeaProjects\TaskWolf`. Work in an isolated worktree branched from `origin/main` (which must contain merged S0–S6; if S4–S6 are not yet merged, rebase this work on top of them so the allowlist starts at 45).

---

### Task 1: Create the `dashboard` namespace + register it

**Files:**
- Create: `frontend/src/i18n/locales/en/dashboard.json`
- Create: `frontend/src/i18n/locales/de/dashboard.json`
- Modify: `frontend/src/i18n/index.ts`

**Interfaces:**
- Produces: the `dashboard` namespace keys consumed by Tasks 2–4.

- [ ] **Step 1: Create `frontend/src/i18n/locales/en/dashboard.json`**

```json
{
  "projects": {
    "title": "Projects",
    "new": "New Project"
  },
  "title": "Dashboard",
  "loading": "Loading dashboard…",
  "empty": {
    "text": "No widgets yet.",
    "cta": "Add your first widget"
  },
  "actions": {
    "edit": "Edit",
    "addWidget": "Add Widget"
  },
  "error": {
    "adminRequired": "Admin role required to modify the dashboard.",
    "widgetFailed": "Failed to load widget"
  },
  "widget": {
    "unknown": "Unknown widget type: {{type}}",
    "removeAria": "Remove {{title}}",
    "title": {
      "BURNDOWN": "Burndown",
      "VELOCITY": "Velocity",
      "CYCLE_TIME": "Cycle Time",
      "ISSUE_COUNT": "Open Issues",
      "ISSUES_BY_STATUS": "Issues by Status",
      "ISSUE_LIST": "Issue List"
    },
    "burndown": {
      "empty": "No burndown data.",
      "ideal": "Ideal",
      "actual": "Actual"
    },
    "velocity": {
      "empty": "No completed sprints yet.",
      "planned": "Planned",
      "completed": "Completed"
    },
    "cycleTime": {
      "empty": "No cycle time data yet.",
      "axisHours": "Hours",
      "tooltipLabel": "Avg Cycle Time"
    },
    "issueList": {
      "empty": "No issues."
    }
  },
  "palette": {
    "title": "Add Widget",
    "options": {
      "BURNDOWN": "Burndown Chart",
      "VELOCITY": "Velocity Chart",
      "CYCLE_TIME": "Cycle Time Chart",
      "ISSUE_COUNT": "Open Issue Count",
      "ISSUES_BY_STATUS": "Issues by Status",
      "ISSUE_LIST": "Issue List"
    },
    "filters": {
      "MY_OPEN": "My Open Issues",
      "RECENTLY_UPDATED": "Recently Updated",
      "OVERDUE": "Overdue"
    }
  },
  "byStatus": {
    "TODO": "To Do",
    "IN_PROGRESS": "In Progress",
    "DONE": "Done",
    "OTHER": "Other",
    "showing": "Showing first {{loaded}} of {{total}}"
  }
}
```

- [ ] **Step 2: Create `frontend/src/i18n/locales/de/dashboard.json`** (same keys, German copy)

```json
{
  "projects": {
    "title": "Projekte",
    "new": "Neues Projekt"
  },
  "title": "Dashboard",
  "loading": "Dashboard wird geladen…",
  "empty": {
    "text": "Noch keine Widgets.",
    "cta": "Erstes Widget hinzufügen"
  },
  "actions": {
    "edit": "Bearbeiten",
    "addWidget": "Widget hinzufügen"
  },
  "error": {
    "adminRequired": "Admin-Rolle erforderlich, um das Dashboard zu ändern.",
    "widgetFailed": "Widget konnte nicht geladen werden"
  },
  "widget": {
    "unknown": "Unbekannter Widget-Typ: {{type}}",
    "removeAria": "{{title}} entfernen",
    "title": {
      "BURNDOWN": "Burndown",
      "VELOCITY": "Velocity",
      "CYCLE_TIME": "Durchlaufzeit",
      "ISSUE_COUNT": "Offene Vorgänge",
      "ISSUES_BY_STATUS": "Vorgänge nach Status",
      "ISSUE_LIST": "Vorgangsliste"
    },
    "burndown": {
      "empty": "Keine Burndown-Daten.",
      "ideal": "Ideal",
      "actual": "Ist"
    },
    "velocity": {
      "empty": "Noch keine abgeschlossenen Sprints.",
      "planned": "Geplant",
      "completed": "Abgeschlossen"
    },
    "cycleTime": {
      "empty": "Noch keine Durchlaufzeit-Daten.",
      "axisHours": "Stunden",
      "tooltipLabel": "Ø Durchlaufzeit"
    },
    "issueList": {
      "empty": "Keine Vorgänge."
    }
  },
  "palette": {
    "title": "Widget hinzufügen",
    "options": {
      "BURNDOWN": "Burndown-Diagramm",
      "VELOCITY": "Velocity-Diagramm",
      "CYCLE_TIME": "Durchlaufzeit-Diagramm",
      "ISSUE_COUNT": "Anzahl offener Vorgänge",
      "ISSUES_BY_STATUS": "Vorgänge nach Status",
      "ISSUE_LIST": "Vorgangsliste"
    },
    "filters": {
      "MY_OPEN": "Meine offenen Vorgänge",
      "RECENTLY_UPDATED": "Kürzlich aktualisiert",
      "OVERDUE": "Überfällig"
    }
  },
  "byStatus": {
    "TODO": "Zu erledigen",
    "IN_PROGRESS": "In Arbeit",
    "DONE": "Erledigt",
    "OTHER": "Sonstige",
    "showing": "Zeige erste {{loaded}} von {{total}}"
  }
}
```

- [ ] **Step 3: Register the namespace in `frontend/src/i18n/index.ts`**

Add the imports after the last existing locale import (the S6 `sprints` pair, or `comments` if S4–S6 are not yet merged):

```ts
import enDashboard from './locales/en/dashboard.json'
import deDashboard from './locales/de/dashboard.json'
```

In the `resources` object, add `dashboard` to both languages (append to the existing key lists):

```ts
      en: { /* …existing… */ dashboard: enDashboard },
      de: { /* …existing… */ dashboard: deDashboard },
```

And append `'dashboard'` to the `ns` array:

```ts
    ns: [/* …existing… */ 'dashboard'],
```

- [ ] **Step 4: Verify parity + build** (no component change yet — all 10 files still allowlisted, scanner stays green; parity + build is this task's gate)

Run: `cd frontend && npm run check:i18n && npm run build`
Expected: `i18n-parity: OK — en/de namespaces and keys match.` then a successful `tsc && vite build`.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/i18n/locales/en/dashboard.json frontend/src/i18n/locales/de/dashboard.json frontend/src/i18n/index.ts
git commit -m "feat(i18n): add dashboard namespace (en/de)"
```

---

### Task 2: Localize DashboardPage (project list)

**Files:**
- Modify: `frontend/src/pages/dashboard/DashboardPage.tsx`
- Modify: `frontend/scripts/i18n-allowlist.json`

**Interfaces:**
- Consumes: `dashboard:projects.*` (Task 1); `common:loading` (existing).

- [ ] **Step 1: Add the hook import**

Add after `import { useProjects } from '@/hooks/useProjects'` (line 2):

```ts
import { useTranslation } from 'react-i18next'
```

- [ ] **Step 2: Add the hook + localize loading, heading, CTA**

Replace the body from `const { data: projects, isLoading } = useProjects()` through the `</div>` closing the header flex row:

```tsx
  const { data: projects, isLoading } = useProjects()
  const { t } = useTranslation('dashboard')
  if (isLoading) return <div className="text-gray-400">{t('common:loading')}</div>

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">{t('projects.title')}</h1>
        <Link to="/projects/new"
          className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded text-sm font-medium">
          {t('projects.new')}
        </Link>
      </div>
```

(Leave the `{p.key}`, `{p.name}`, `{p.description}` renders — they are data.)

- [ ] **Step 3: Remove DashboardPage from the allowlist**

In `frontend/scripts/i18n-allowlist.json`, delete this line:

```json
  "src/pages/dashboard/DashboardPage.tsx",
```

- [ ] **Step 4: Verify scanner + build**

Run: `cd frontend && npm run scan:i18n && npm run build`
Expected: `i18n-scan: OK — 0 hardcoded strings outside the allowlist (44 file(s) still allowlisted).` and a successful build.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/dashboard/DashboardPage.tsx frontend/scripts/i18n-allowlist.json
git commit -m "feat(i18n): localize dashboard project-list page"
```

---

### Task 3: Localize ProjectDashboardPage + WidgetWrapper + WidgetPalette

**Files:**
- Modify: `frontend/src/pages/project-dashboard/ProjectDashboardPage.tsx`
- Modify: `frontend/src/components/dashboard/WidgetWrapper.tsx`
- Modify: `frontend/src/components/dashboard/WidgetPalette.tsx`
- Modify: `frontend/scripts/i18n-allowlist.json`

**Interfaces:**
- Consumes: `dashboard:title`, `dashboard:loading`, `dashboard:empty.*`, `dashboard:actions.*`, `dashboard:error.*`, `dashboard:widget.*`, `dashboard:palette.*` (Task 1); `common:save`, `common:saving`, `common:cancel` (existing).

- [ ] **Step 1: `ProjectDashboardPage.tsx` — add the hook import**

Add after `import { WidgetPalette } from '@/components/dashboard/WidgetPalette'` (line 7):

```ts
import { useTranslation } from 'react-i18next'
```

- [ ] **Step 2: `ProjectDashboardPage.tsx` — delete the `WIDGET_TITLES` const map**

Remove the whole block (lines 17–24):

```ts
const WIDGET_TITLES: Record<string, string> = {
  BURNDOWN:         'Burndown',
  VELOCITY:         'Velocity',
  CYCLE_TIME:       'Cycle Time',
  ISSUE_COUNT:      'Open Issues',
  ISSUES_BY_STATUS: 'Issues by Status',
  ISSUE_LIST:       'Issue List',
}
```

- [ ] **Step 3: `ProjectDashboardPage.tsx` — add the hook + localize `renderWidget`**

Add as the first line of the `ProjectDashboardPage` body (before `const { key } = useParams…`):

```ts
  const { t } = useTranslation('dashboard')
```

In `renderWidget`, replace the unknown-type fallback:

```tsx
        default:                 return <p className="text-gray-500 text-xs">Unknown widget type: {widget.type}</p>
```

with:

```tsx
        default:                 return <p className="text-gray-500 text-xs">{t('widget.unknown', { type: widget.type })}</p>
```

Replace the `WidgetWrapper` `title` prop and the `onRemove` error string:

```tsx
      <WidgetWrapper
        title={WIDGET_TITLES[widget.type] ?? widget.type}
        widgetId={widget.id}
        editMode={editMode}
        onRemove={id => removeWidget.mutate(id, { onError: () => setMutationError('Admin role required to modify the dashboard.') })}
      >
```

with:

```tsx
      <WidgetWrapper
        title={t(`widget.title.${widget.type}`, { defaultValue: widget.type })}
        widgetId={widget.id}
        editMode={editMode}
        onRemove={id => removeWidget.mutate(id, { onError: () => setMutationError(t('error.adminRequired')) })}
      >
```

- [ ] **Step 4: `ProjectDashboardPage.tsx` — localize the remaining `handleSave` error + JSX**

In `handleSave`, replace `setMutationError('Admin role required to modify the dashboard.')` with `setMutationError(t('error.adminRequired'))`.

Replace the loading fallback:

```tsx
  if (isLoading) return <p className="text-gray-500 text-sm">Loading dashboard...</p>
```

with:

```tsx
  if (isLoading) return <p className="text-gray-500 text-sm">{t('loading')}</p>
```

Replace the header + buttons region (heading through the Edit button):

```tsx
        <h1 className="text-2xl font-bold">Dashboard</h1>
```

with `{t('title')}`; then the edit-mode buttons:

```tsx
                + Add Widget
```
→ `+ {t('actions.addWidget')}`

```tsx
                {saveLayout.isPending ? 'Saving...' : 'Save'}
```
→ `{saveLayout.isPending ? t('common:saving') : t('common:save')}`

```tsx
                Cancel
```
→ `{t('common:cancel')}`

```tsx
              Edit
```
→ `{t('actions.edit')}`

- [ ] **Step 5: `ProjectDashboardPage.tsx` — localize the empty state + palette error**

Replace the empty-state text + CTA:

```tsx
          <p className="text-gray-400 mb-3">No widgets yet.</p>
          <button
            onClick={() => setEditMode(true)}
            className="px-4 py-2 text-sm bg-indigo-600 hover:bg-indigo-500 text-white rounded"
          >
            Add your first widget
          </button>
```

with:

```tsx
          <p className="text-gray-400 mb-3">{t('empty.text')}</p>
          <button
            onClick={() => setEditMode(true)}
            className="px-4 py-2 text-sm bg-indigo-600 hover:bg-indigo-500 text-white rounded"
          >
            {t('empty.cta')}
          </button>
```

And in the `WidgetPalette` `onAdd` handler, replace the last `setMutationError('Admin role required to modify the dashboard.')` with `setMutationError(t('error.adminRequired'))`.

(Leave the `✕` dismiss glyph literal.)

- [ ] **Step 6: `WidgetWrapper.tsx` — localize the error boundary + remove aria-label**

Add at the top, after `import React from 'react'` (line 1):

```ts
import { useTranslation } from 'react-i18next'
```

The `ErrorBoundary` is a class component (no hooks). Localize its fallback by passing a message prop from the functional `WidgetWrapper`. Change the boundary to accept a `message`:

```tsx
class ErrorBoundary extends React.Component<{ children: React.ReactNode; message: string }, State> {
  state: State = { hasError: false }
  static getDerivedStateFromError() { return { hasError: true } }
  componentDidCatch(error: Error, info: React.ErrorInfo) {
    console.error('Widget render error:', error, info.componentStack)
  }
  render() {
    if (this.state.hasError)
      return <div className="flex items-center justify-center h-full text-red-400 text-sm">{this.props.message}</div>
    return this.props.children
  }
}
```

(The `console.error('Widget render error:', …)` string is a developer log, not user-facing — leave it; the scanner does not check call arguments.)

In `WidgetWrapper`, add the hook and localize the remove `aria-label`, and pass the boundary message:

```tsx
export function WidgetWrapper({ title, widgetId, editMode, onRemove, children }: Props) {
  const { t } = useTranslation('dashboard')
  return (
    <div className="bg-gray-900 border border-gray-800 rounded-lg flex flex-col h-full overflow-hidden">
      <div className={`flex items-center justify-between px-4 py-2 border-b border-gray-800 shrink-0 ${editMode ? 'drag-handle cursor-grab active:cursor-grabbing' : ''}`}>
        <span className="text-sm font-semibold text-gray-300">{title}</span>
        {editMode && (
          <button
            onClick={() => onRemove(widgetId)}
            aria-label={t('widget.removeAria', { title })}
            className="text-gray-500 hover:text-red-400 text-xs ml-2"
          >
            ✕
          </button>
        )}
      </div>
      <div className="flex-1 min-h-0 p-3">
        <ErrorBoundary message={t('error.widgetFailed')}>{children}</ErrorBoundary>
      </div>
    </div>
  )
}
```

(`{title}` is already localized upstream in Task 3 Step 3 — render it as data.)

- [ ] **Step 7: `WidgetPalette.tsx` — localize labels via the render site**

Add after `import { useState } from 'react'` (line 1):

```ts
import { useTranslation } from 'react-i18next'
```

Keep `WIDGET_OPTIONS` and `ISSUE_LIST_FILTERS` as structural data but drop their English `label` fields (they become `dashboard:palette.*` keys). Replace both const arrays:

```ts
const WIDGET_OPTIONS = [
  { type: 'BURNDOWN',         defaultW: 6, defaultH: 5 },
  { type: 'VELOCITY',         defaultW: 6, defaultH: 5 },
  { type: 'CYCLE_TIME',       defaultW: 6, defaultH: 5 },
  { type: 'ISSUE_COUNT',      defaultW: 3, defaultH: 3 },
  { type: 'ISSUES_BY_STATUS', defaultW: 6, defaultH: 3 },
  { type: 'ISSUE_LIST',       defaultW: 4, defaultH: 6 },
]

const ISSUE_LIST_FILTERS = ['MY_OPEN', 'RECENTLY_UPDATED', 'OVERDUE']
```

Add the hook as the first line of the `WidgetPalette` body (before `const [issueFilter, …]`):

```ts
  const { t } = useTranslation('dashboard')
```

Localize the header title, the close `aria-label`, the filter `<option>`s, and the widget button label:

```tsx
          <h2 className="text-lg font-semibold">{t('palette.title')}</h2>
          <button onClick={onClose} aria-label={t('common:close')} className="text-gray-500 hover:text-white">✕</button>
```

```tsx
                  {ISSUE_LIST_FILTERS.map(f => (
                    <option key={f} value={f}>{t(`palette.filters.${f}`)}</option>
                  ))}
```

```tsx
                {t(`palette.options.${opt.type}`)}
```

(The `<button>` previously rendered `{opt.label}`; it now renders the keyed translation. Leave the `onAdd`/`JSON.stringify` config logic unchanged.)

- [ ] **Step 8: Remove the three files from the allowlist**

In `frontend/scripts/i18n-allowlist.json`, delete these lines:

```json
  "src/components/dashboard/WidgetPalette.tsx",
  "src/components/dashboard/WidgetWrapper.tsx",
  "src/pages/project-dashboard/ProjectDashboardPage.tsx",
```

- [ ] **Step 9: Verify scanner + build**

Run: `cd frontend && npm run scan:i18n && npm run build`
Expected: `i18n-scan: OK — 0 hardcoded strings outside the allowlist (41 file(s) still allowlisted).` and a successful build. If the scanner flags a leftover string, localize it with an existing `dashboard:*`/`common:*` key before proceeding.

- [ ] **Step 10: Commit**

```bash
git add frontend/src/pages/project-dashboard/ProjectDashboardPage.tsx frontend/src/components/dashboard/WidgetWrapper.tsx frontend/src/components/dashboard/WidgetPalette.tsx frontend/scripts/i18n-allowlist.json
git commit -m "feat(i18n): localize project dashboard page, widget wrapper + palette"
```

---

### Task 4: Localize the six widgets

**Files:**
- Modify: `frontend/src/components/dashboard/BurndownWidget.tsx`
- Modify: `frontend/src/components/dashboard/VelocityWidget.tsx`
- Modify: `frontend/src/components/dashboard/CycleTimeWidget.tsx`
- Modify: `frontend/src/components/dashboard/IssueCountWidget.tsx`
- Modify: `frontend/src/components/dashboard/IssuesByStatusWidget.tsx`
- Modify: `frontend/src/components/dashboard/IssueListWidget.tsx`
- Modify: `frontend/scripts/i18n-allowlist.json`

**Interfaces:**
- Consumes: `dashboard:widget.burndown.*`, `dashboard:widget.velocity.*`, `dashboard:widget.cycleTime.*`, `dashboard:widget.title.ISSUE_COUNT`, `dashboard:widget.issueList.empty`, `dashboard:palette.filters.*`, `dashboard:byStatus.*` (Task 1); `common:loading` (existing).

- [ ] **Step 1: `BurndownWidget.tsx` — hook + empty text + chart series names**

Add after `import { LineChart, … } from 'recharts'` (line 4):

```ts
import { useTranslation } from 'react-i18next'
```

Add as the first line of the `BurndownWidget` body (before `const { data: sprints } = useSprints…`):

```ts
  const { t } = useTranslation('dashboard')
```

Replace the empty text:

```tsx
        ? <p className="text-gray-500 text-xs">No burndown data.</p>
```
→ `? <p className="text-gray-500 text-xs">{t('widget.burndown.empty')}</p>`

Add `name` props to the two `<Line>`s (keep the `dataKey` values unchanged):

```tsx
              <Line type="monotone" dataKey="Ideal" name={t('widget.burndown.ideal')} stroke="#6b7280" strokeDasharray="5 5" dot={false} />
              <Line type="monotone" dataKey="Actual" name={t('widget.burndown.actual')} stroke="#3b82f6" strokeWidth={2} dot={false} />
```

- [ ] **Step 2: `VelocityWidget.tsx` — hook + empty text + chart series names**

Add after `import { BarChart, … } from 'recharts'` (line 2):

```ts
import { useTranslation } from 'react-i18next'
```

Add as the first line of the body:

```ts
  const { t } = useTranslation('dashboard')
```

Replace the empty text:

```tsx
  if (chartData.length === 0) return <p className="text-gray-500 text-xs">No completed sprints yet.</p>
```
→ `if (chartData.length === 0) return <p className="text-gray-500 text-xs">{t('widget.velocity.empty')}</p>`

Add `name` to the two `<Bar>`s:

```tsx
        <Bar dataKey="Planned" name={t('widget.velocity.planned')} fill="#374151" radius={[3, 3, 0, 0]} />
        <Bar dataKey="Completed" name={t('widget.velocity.completed')} fill="#3b82f6" radius={[3, 3, 0, 0]} />
```

- [ ] **Step 3: `CycleTimeWidget.tsx` — hook + empty text + axis + tooltip label**

Add after `import { BarChart, … } from 'recharts'` (line 2):

```ts
import { useTranslation } from 'react-i18next'
```

Add as the first line of the body:

```ts
  const { t } = useTranslation('dashboard')
```

Replace the empty text:

```tsx
  if (chartData.length === 0) return <p className="text-gray-500 text-xs">No cycle time data yet.</p>
```
→ `if (chartData.length === 0) return <p className="text-gray-500 text-xs">{t('widget.cycleTime.empty')}</p>`

Replace the axis label and tooltip formatter:

```tsx
        <YAxis stroke="#6b7280" tick={{ fontSize: 10 }} label={{ value: 'Hours', angle: -90, position: 'insideLeft', fill: '#6b7280', fontSize: 10 }} />
        <Tooltip
          contentStyle={{ backgroundColor: '#1f2937', border: '1px solid #374151', borderRadius: '6px' }}
          formatter={(v) => [`${v}h`, 'Avg Cycle Time']}
        />
```

with:

```tsx
        <YAxis stroke="#6b7280" tick={{ fontSize: 10 }} label={{ value: t('widget.cycleTime.axisHours'), angle: -90, position: 'insideLeft', fill: '#6b7280', fontSize: 10 }} />
        <Tooltip
          contentStyle={{ backgroundColor: '#1f2937', border: '1px solid #374151', borderRadius: '6px' }}
          formatter={(v) => [`${v}h`, t('widget.cycleTime.tooltipLabel')]}
        />
```

(Keep `dataKey="Hours"` unchanged — it is the data key, not display text.)

- [ ] **Step 4: `IssueCountWidget.tsx` — hook + reuse the ISSUE_COUNT title**

Add after `import { useIssues } from '@/hooks/useIssues'` (line 1):

```ts
import { useTranslation } from 'react-i18next'
```

Replace the body:

```tsx
export function IssueCountWidget({ projectKey }: Props) {
  const { data } = useIssues(projectKey)
  const total = data?.totalElements ?? 0

  return (
    <div className="flex flex-col items-center justify-center h-full gap-1">
      <span className="text-4xl font-bold text-white">{total}</span>
      <span className="text-xs text-gray-400">Open Issues</span>
    </div>
  )
}
```

with:

```tsx
export function IssueCountWidget({ projectKey }: Props) {
  const { data } = useIssues(projectKey)
  const total = data?.totalElements ?? 0
  const { t } = useTranslation('dashboard')

  return (
    <div className="flex flex-col items-center justify-center h-full gap-1">
      <span className="text-4xl font-bold text-white">{total}</span>
      <span className="text-xs text-gray-400">{t('widget.title.ISSUE_COUNT')}</span>
    </div>
  )
}
```

- [ ] **Step 5: `IssuesByStatusWidget.tsx` — hook + category labels + truncation text**

Add after `import { useIssues } from '@/hooks/useIssues'` (line 1):

```ts
import { useTranslation } from 'react-i18next'
```

Delete the `CATEGORY_LABEL` const map (lines 7–11); keep `CATEGORY_COLOR` (it keys off the raw enum for styling).

Add as the first line of the `IssuesByStatusWidget` body (before `const { data } = useIssues…`):

```ts
  const { t } = useTranslation('dashboard')
```

Replace the three category labels and the "Other" label:

```tsx
            <span className="text-xs text-gray-500">{CATEGORY_LABEL[cat]}</span>
```
→ `<span className="text-xs text-gray-500">{t(`byStatus.${cat}`)}</span>`

```tsx
            <span className="text-xs text-gray-500">Other</span>
```
→ `<span className="text-xs text-gray-500">{t('byStatus.OTHER')}</span>`

Replace the truncation text:

```tsx
        <p className="text-xs text-gray-600 text-center mt-2">Showing first {loaded} of {total}</p>
```
→ `<p className="text-xs text-gray-600 text-center mt-2">{t('byStatus.showing', { loaded, total })}</p>`

- [ ] **Step 6: `IssueListWidget.tsx` — hook + filter label + loading/empty**

Add after `import type { Issue, Page } from '@/types'` (line 4):

```ts
import { useTranslation } from 'react-i18next'
```

Delete the `FILTER_LABELS` const map (lines 13–17); reuse the shared `dashboard:palette.filters.*` keys instead.

Add as the first line of the `IssueListWidget` body (before `let parsed…`):

```ts
  const { t } = useTranslation('dashboard')
```

Replace the filter caption, loading, and empty texts:

```tsx
      <p className="text-xs font-semibold text-gray-400 mb-1">{FILTER_LABELS[filter]}</p>
      {isLoading && <p className="text-gray-500 text-xs">Loading...</p>}
      {!isLoading && issues.length === 0 && <p className="text-gray-500 text-xs">No issues.</p>}
```

with:

```tsx
      <p className="text-xs font-semibold text-gray-400 mb-1">{t(`palette.filters.${filter}`)}</p>
      {isLoading && <p className="text-gray-500 text-xs">{t('common:loading')}</p>}
      {!isLoading && issues.length === 0 && <p className="text-gray-500 text-xs">{t('widget.issueList.empty')}</p>}
```

(Leave `{issue.key}` and `{issue.title}` — data.)

- [ ] **Step 7: Remove the six widget files from the allowlist**

In `frontend/scripts/i18n-allowlist.json`, delete these lines:

```json
  "src/components/dashboard/BurndownWidget.tsx",
  "src/components/dashboard/CycleTimeWidget.tsx",
  "src/components/dashboard/IssueCountWidget.tsx",
  "src/components/dashboard/IssueListWidget.tsx",
  "src/components/dashboard/IssuesByStatusWidget.tsx",
  "src/components/dashboard/VelocityWidget.tsx",
```

- [ ] **Step 8: Verify scanner + build**

Run: `cd frontend && npm run scan:i18n && npm run build`
Expected: `i18n-scan: OK — 0 hardcoded strings outside the allowlist (35 file(s) still allowlisted).` and a successful build.

- [ ] **Step 9: Commit**

```bash
git add frontend/src/components/dashboard/BurndownWidget.tsx frontend/src/components/dashboard/VelocityWidget.tsx frontend/src/components/dashboard/CycleTimeWidget.tsx frontend/src/components/dashboard/IssueCountWidget.tsx frontend/src/components/dashboard/IssuesByStatusWidget.tsx frontend/src/components/dashboard/IssueListWidget.tsx frontend/scripts/i18n-allowlist.json
git commit -m "feat(i18n): localize dashboard widgets (burndown, velocity, cycle-time, counts, list)"
```

---

### Task 5: Finalize S7 — full gate + flip coverage matrix

**Files:**
- Modify: `docs/superpowers/specs/2026-07-13-i18n-full-rollout-design.md`

- [ ] **Step 1: Run the full slice gate**

Run: `cd frontend && npm run test:i18n && npm run lint:i18n && npm run build`
Expected: scanner self-tests pass, `i18n-scan: OK — 0 hardcoded strings outside the allowlist (35 file(s) still allowlisted).`, `i18n-parity: OK — en/de namespaces and keys match.`, and a successful build. All 10 S7 files must be absent from `frontend/scripts/i18n-allowlist.json` (45 baseline − 10 = 35 entries).

- [ ] **Step 2: Manual DE/EN browser check**

Start the dev server (`cd frontend && npm run dev`), switch language via Settings → Profile, and confirm: the projects home ("Projects"/"New Project"), the per-project dashboard header + Edit/Save/Cancel/Add-Widget buttons, the empty state, the "Add Widget" palette (option labels + issue-list filter), each widget's title bar, the widget empty states, the burndown/velocity legends, the cycle-time axis + tooltip, the issues-by-status category labels and "Showing first N of M" text all switch between English and German with no raw keys and no layout breakage from longer German strings.

- [ ] **Step 3: Flip S7 to ✅ in the coverage matrix**

In `docs/superpowers/specs/2026-07-13-i18n-full-rollout-design.md`, replace the S7 matrix row:

```
| S7 | `dashboard` | ⬜ | DashboardPage, ProjectDashboardPage, DashboardCanvas, WidgetPalette, WidgetWrapper, Burndown/IssueCount/IssueList/IssuesByStatus/Velocity/CycleTime-Widget |
```

with:

```
| S7 | `dashboard` | ✅ | DashboardPage + ProjectDashboardPage + WidgetWrapper/WidgetPalette + all six widgets localized (new `dashboard` ns); scanner-blind WIDGET_TITLES/WIDGET_OPTIONS/FILTER_LABELS/CATEGORY_LABEL localized at render site; recharts legend/axis/tooltip labels via `name`/`label`; DashboardCanvas already string-free |
```

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/specs/2026-07-13-i18n-full-rollout-design.md
git commit -m "docs(i18n): mark S7 dashboard slice complete in coverage matrix"
```

---

## Self-Review

**Spec coverage** (against master-spec Abschnitt 5 DoD + Anhang checklist):
- New `dashboard` namespace en+de, registered in `index.ts` → Task 1. ✅
- Every user-facing string in all 10 files via `t()` → Tasks 2–4. ✅
- Scanner-blind const-map labels (WIDGET_TITLES, WIDGET_OPTIONS, ISSUE_LIST_FILTERS, FILTER_LABELS, CATEGORY_LABEL) localized at render site → Tasks 3–4. ✅
- recharts series/axis/tooltip labels localized via `name`/`label`/`formatter` → Task 4. ✅
- Interpolation via variables only (`widget.unknown` {{type}}, `byStatus.showing` {{loaded}}/{{total}}, `widget.removeAria` {{title}}) — no fragment concat. ✅
- `common:*` reused for loading/save/saving/cancel/close (no duplicate `dashboard` keys). ✅
- `DashboardCanvas` correctly untouched (string-free, not allowlisted). ✅
- All 10 files removed from allowlist (45 → 35) → Tasks 2–4. ✅
- Full gate + manual DE/EN + matrix flip → Task 5. ✅

**Placeholder scan:** No TBD/TODO; every step has exact old/new code and exact commands with expected output.

**Type/key consistency:** Keys used in Tasks 2–4 (`projects.*`, `title`, `loading`, `empty.*`, `actions.*`, `error.*`, `widget.unknown`, `widget.removeAria`, `widget.title.*`, `widget.burndown.*`, `widget.velocity.*`, `widget.cycleTime.*`, `widget.issueList.empty`, `palette.title`, `palette.options.*`, `palette.filters.*`, `byStatus.*`) are all defined in Task 1's en+de JSON. `common:loading/save/saving/cancel/close` are pre-existing. The dynamic keys `widget.title.${widget.type}`, `palette.options.${opt.type}`, `palette.filters.${f}`/`${filter}`, `byStatus.${cat}` cover exactly the enum members present in each map. `WidgetWrapper.ErrorBoundary` gets a `message` prop (class component, no hook) fed from the functional wrapper's `t('error.widgetFailed')`. Allowlist arithmetic: 45 → 44 (Task 2) → 41 (Task 3) → 35 (Task 4).
