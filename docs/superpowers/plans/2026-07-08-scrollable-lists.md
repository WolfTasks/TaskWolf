# Scrollbare Listen (DataTable) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Lange Tabellen scrollen intern in einem Container, der die Viewport-Resthöhe füllt (sticky Header, virtualisiert), über eine wiederverwendbare `DataTable`-Komponente; alle bestehenden `<table>`-Listen werden darauf migriert.

**Architecture:** Eine generische, div-basierte `DataTable<T>`-Komponente rendert Header und Zeilen über eine gemeinsame CSS-Grid-Spaltendefinition, scrollt in einem eigenen `overflow-auto`-Container mit `sticky` Header und virtualisiert die Zeilen via `@tanstack/react-virtual`. Konsumierende Seiten werden auf ein `flex flex-col h-full min-h-0`-Layout umgestellt, sodass Seiten-Header/Filter oben fix bleiben und nur die Tabelle scrollt.

**Tech Stack:** React 19, TypeScript 6, Tailwind CSS 4, `@tanstack/react-virtual` (neu), `@tanstack/react-query` (bestehend), react-router-dom 7.

## Global Constraints

- Frontend hat **kein** Test-Framework. Automatischer Gate ist der Typecheck: `cd frontend && npx tsc --noEmit` muss fehlerfrei sein. Funktionaler Gate ist **manuelle** Verifikation im Dev-Server.
- Dark-Theme-Stil der bestehenden Tabellen beibehalten: `text-sm`, Trenner `border-b border-gray-800`, Hover `hover:bg-gray-800/40`, Header-Text `text-gray-400`.
- Neue Komponente liegt unter `frontend/src/components/table/`.
- Import-Alias `@/` zeigt auf `frontend/src/` (bestehende Konvention).
- Comment-/Activity-Feeds sind **nicht** Teil dieses Zyklus.
- Commit-Nachrichten enden mit:
  `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`

---

### Task 1: `DataTable`-Komponente + Dependency

**Files:**
- Modify: `frontend/package.json` (Dependency `@tanstack/react-virtual`)
- Create: `frontend/src/components/table/DataTable.tsx`

**Interfaces:**
- Consumes: nichts (erste Aufgabe).
- Produces:
  - `interface Column<T> { key: string; header: ReactNode; cell: (row: T) => ReactNode; width?: string; align?: 'left' | 'right' | 'center' }`
  - `interface DataTableProps<T> { columns: Column<T>[]; rows: T[]; rowKey: (row: T) => string | number; empty?: ReactNode; estimateRowHeight?: number }`
  - `function DataTable<T>(props: DataTableProps<T>): JSX.Element`
  - Beide Interfaces und die Komponente werden benannt exportiert.

- [ ] **Step 1: Dependency installieren**

Run:
```bash
cd frontend && npm install @tanstack/react-virtual@^3
```
Expected: `package.json` listet `@tanstack/react-virtual` unter `dependencies`; Installation ohne Peer-Dependency-Fehler (react-virtual 3.x unterstützt React 19).

- [ ] **Step 2: Komponente schreiben**

Create `frontend/src/components/table/DataTable.tsx`:

```tsx
import { useRef } from 'react'
import type { ReactNode } from 'react'
import { useVirtualizer } from '@tanstack/react-virtual'

export interface Column<T> {
  key: string
  header: ReactNode
  cell: (row: T) => ReactNode
  width?: string // CSS-Grid-Track, z.B. '1fr' | '180px'. Default '1fr'.
  align?: 'left' | 'right' | 'center'
}

export interface DataTableProps<T> {
  columns: Column<T>[]
  rows: T[]
  rowKey: (row: T) => string | number
  empty?: ReactNode
  estimateRowHeight?: number
}

const ALIGN: Record<'left' | 'right' | 'center', string> = {
  left: 'text-left',
  right: 'text-right',
  center: 'text-center',
}

export function DataTable<T>({
  columns,
  rows,
  rowKey,
  empty = 'Keine Einträge',
  estimateRowHeight = 44,
}: DataTableProps<T>) {
  const parentRef = useRef<HTMLDivElement>(null)
  const gridTemplate = columns.map(c => c.width ?? '1fr').join(' ')

  const virtualizer = useVirtualizer({
    count: rows.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => estimateRowHeight,
    overscan: 10,
  })

  return (
    <div ref={parentRef} className="flex-1 min-h-0 overflow-auto">
      <div
        className="sticky top-0 z-10 grid bg-gray-900 text-gray-400 border-b border-gray-700"
        style={{ gridTemplateColumns: gridTemplate }}
      >
        {columns.map(col => (
          <div
            key={col.key}
            className={`px-2 py-2 text-sm font-medium ${ALIGN[col.align ?? 'left']}`}
          >
            {col.header}
          </div>
        ))}
      </div>

      {rows.length === 0 ? (
        <div className="py-4 text-sm text-gray-400">{empty}</div>
      ) : (
        <div style={{ height: virtualizer.getTotalSize(), position: 'relative' }}>
          {virtualizer.getVirtualItems().map(vr => {
            const row = rows[vr.index]
            return (
              <div
                key={rowKey(row)}
                data-index={vr.index}
                ref={virtualizer.measureElement}
                className="absolute left-0 w-full grid items-center text-sm border-b border-gray-800 hover:bg-gray-800/40"
                style={{
                  gridTemplateColumns: gridTemplate,
                  transform: `translateY(${vr.start}px)`,
                }}
              >
                {columns.map(col => (
                  <div
                    key={col.key}
                    className={`px-2 py-3 ${ALIGN[col.align ?? 'left']}`}
                  >
                    {col.cell(row)}
                  </div>
                ))}
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
```

Hinweise für den Umsetzer:
- Header und Zeilen teilen `gridTemplate` → Spalten sind synchron.
- `sticky top-0` wirkt innerhalb des scrollenden `parentRef`-Containers.
- Virtualisierte Zeilen sind `absolute` im höhengebenden Spacer (`position: relative`, Höhe = `getTotalSize()`); `measureElement` + `data-index` erlauben dynamische Zeilenhöhen.

- [ ] **Step 3: Typecheck**

Run:
```bash
cd frontend && npx tsc --noEmit
```
Expected: PASS (keine Fehler). Die generische Komponente ist noch ungenutzt, muss aber sauber kompilieren.

- [ ] **Step 4: Commit**

```bash
git add frontend/package.json frontend/package-lock.json frontend/src/components/table/DataTable.tsx
git commit -m "feat(frontend): add reusable virtualized DataTable component

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: `AuditLogPage` migrieren (Referenz + manuelle Verifikation)

**Files:**
- Modify: `frontend/src/pages/admin/AuditLogPage.tsx`

**Interfaces:**
- Consumes: `DataTable`, `Column` aus `@/components/table/DataTable`.
- Produces: nichts für spätere Tasks (Muster dient als Vorlage).

- [ ] **Step 1: Seite auf `DataTable` + Flex-Höhe umstellen**

Ersetze den kompletten Inhalt von `frontend/src/pages/admin/AuditLogPage.tsx`:

```tsx
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { auditApi, AuditEvent } from '../../api/audit'
import { DataTable, type Column } from '@/components/table/DataTable'

const LEVEL_COLOR: Record<string, string> = {
  SECURITY: 'bg-red-900/40 text-red-400',
  WRITE: 'bg-blue-900/40 text-blue-400',
  ALL: 'bg-gray-700 text-gray-300',
}

const columns: Column<AuditEvent>[] = [
  {
    key: 'time',
    header: 'Time',
    width: '180px',
    cell: e => <span className="text-gray-400">{new Date(e.timestamp).toLocaleString()}</span>,
  },
  { key: 'user', header: 'User', cell: e => e.userEmail },
  {
    key: 'action',
    header: 'Action',
    cell: e => <span className="font-mono text-xs">{e.action}</span>,
  },
  {
    key: 'level',
    header: 'Level',
    width: '120px',
    cell: e => (
      <span className={`px-2 py-1 rounded text-xs font-medium ${LEVEL_COLOR[e.level]}`}>
        {e.level}
      </span>
    ),
  },
  {
    key: 'resource',
    header: 'Resource',
    cell: e => <span className="text-gray-400">{e.resourceType} {e.resourceId}</span>,
  },
]

export default function AuditLogPage() {
  const [action, setAction] = useState('')
  const [level, setLevel] = useState('')
  const { data } = useQuery({
    queryKey: ['audit', action, level],
    queryFn: () =>
      auditApi.listAll({
        ...(action && { action }),
        ...(level && { level }),
      }),
  })

  const handleExport = async (format: 'csv' | 'json') => {
    const res = await auditApi.exportAudit(format)
    const url = URL.createObjectURL(res.data)
    const a = document.createElement('a')
    a.href = url
    a.download = `audit.${format}`
    a.click()
  }

  return (
    <div className="flex flex-col h-full min-h-0 space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Audit Log</h1>
        <div className="flex gap-2">
          <button
            onClick={() => handleExport('csv')}
            className="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded text-sm font-medium"
          >
            Export CSV
          </button>
          <button
            onClick={() => handleExport('json')}
            className="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded text-sm font-medium"
          >
            Export JSON
          </button>
        </div>
      </div>
      <div className="flex gap-2">
        <input
          className="border rounded px-2 py-1 text-sm"
          placeholder="Filter action…"
          value={action}
          onChange={e => setAction(e.target.value)}
        />
        <select
          className="border rounded px-2 py-1 text-sm"
          value={level}
          onChange={e => setLevel(e.target.value)}
        >
          <option value="">All levels</option>
          <option value="SECURITY">Security</option>
          <option value="WRITE">Write</option>
          <option value="ALL">All</option>
        </select>
      </div>
      <DataTable
        columns={columns}
        rows={data?.content ?? []}
        rowKey={e => e.id}
        empty="No audit events"
      />
    </div>
  )
}
```

- [ ] **Step 2: Typecheck**

Run:
```bash
cd frontend && npx tsc --noEmit
```
Expected: PASS.

- [ ] **Step 3: Manuelle Verifikation (Referenz — sorgfältig)**

Run:
```bash
cd frontend && npm run dev
```
Öffne `/admin/audit`. Prüfe:
- Die Liste scrollt **intern**; die Seite selbst scrollt vertikal **nicht** mit.
- Der Header (Time/User/Action/Level/Resource) bleibt beim Scrollen sichtbar (sticky).
- Header- und Zeilen-Spalten sind ausgerichtet.
- **Kein** horizontaler Seiten-Scroll, **kein** doppelter Scrollbalken (falls doch: prüfen, ob `<main>` in `layouts/AppLayout.tsx:134` mit dem inneren Scroll kollidiert — der Seiten-Wrapper `h-full min-h-0` sollte exakt die `<main>`-Höhe füllen).
- Filter (action / level) und Export CSV/JSON funktionieren wie zuvor.
- Container füllt die Resthöhe auf großem **und** kleinem Fenster.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/admin/AuditLogPage.tsx
git commit -m "refactor(frontend): migrate AuditLogPage to scrollable DataTable

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: `ProjectAuditPage` migrieren

**Files:**
- Modify: `frontend/src/pages/projects/settings/ProjectAuditPage.tsx`

**Interfaces:**
- Consumes: `DataTable`, `Column` aus `@/components/table/DataTable`.

- [ ] **Step 1: Seite umstellen**

Ersetze den kompletten Inhalt von `frontend/src/pages/projects/settings/ProjectAuditPage.tsx`:

```tsx
import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { auditApi, AuditEvent } from '../../../api/audit'
import { DataTable, type Column } from '@/components/table/DataTable'

const LEVEL_COLOR: Record<string, string> = {
  SECURITY: 'bg-red-900/40 text-red-400',
  WRITE: 'bg-blue-900/40 text-blue-400',
  ALL: 'bg-gray-700 text-gray-300',
}

const columns: Column<AuditEvent>[] = [
  {
    key: 'time',
    header: 'Time',
    width: '180px',
    cell: e => <span className="text-gray-400">{new Date(e.timestamp).toLocaleString()}</span>,
  },
  { key: 'user', header: 'User', cell: e => e.userEmail },
  {
    key: 'action',
    header: 'Action',
    cell: e => <span className="font-mono text-xs">{e.action}</span>,
  },
  {
    key: 'level',
    header: 'Level',
    width: '120px',
    cell: e => (
      <span className={`px-2 py-1 rounded text-xs font-medium ${LEVEL_COLOR[e.level]}`}>
        {e.level}
      </span>
    ),
  },
  {
    key: 'resource',
    header: 'Resource',
    cell: e => <span className="text-gray-400">{e.resourceType} {e.resourceId}</span>,
  },
]

export default function ProjectAuditPage() {
  const { key } = useParams<{ key: string }>()
  const projectKey = key!

  const [action, setAction] = useState('')
  const { data } = useQuery({
    queryKey: ['project-audit', projectKey, action],
    queryFn: () =>
      auditApi.listForProject(projectKey, {
        ...(action && { action }),
      }),
  })

  return (
    <div className="flex flex-col h-full min-h-0 space-y-4">
      <h1 className="text-2xl font-semibold">Audit Log</h1>
      <div className="flex gap-2">
        <input
          className="border rounded px-2 py-1 text-sm"
          placeholder="Filter action…"
          value={action}
          onChange={e => setAction(e.target.value)}
        />
      </div>
      <DataTable
        columns={columns}
        rows={data?.content ?? []}
        rowKey={e => e.id}
        empty="No audit events"
      />
    </div>
  )
}
```

- [ ] **Step 2: Typecheck**

Run:
```bash
cd frontend && npx tsc --noEmit
```
Expected: PASS.

- [ ] **Step 3: Manuelle Kurz-Verifikation**

Im Dev-Server `/p/<KEY>/settings/audit` öffnen: interner Scroll, sticky Header, Filter funktioniert, kein Seiten-/Doppel-Scroll.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/projects/settings/ProjectAuditPage.tsx
git commit -m "refactor(frontend): migrate ProjectAuditPage to scrollable DataTable

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 4: `AdminUsersPage` migrieren

**Files:**
- Modify: `frontend/src/pages/admin/AdminUsersPage.tsx`

**Interfaces:**
- Consumes: `DataTable`, `Column` aus `@/components/table/DataTable`.

- [ ] **Step 1: Seite umstellen**

Ersetze den kompletten Inhalt von `frontend/src/pages/admin/AdminUsersPage.tsx`:

```tsx
import {
  useAdminUsers, useActivateUser, useDeactivateUser, useDeleteUser,
} from '@/hooks/useAdminUsers'
import { DataTable, type Column } from '@/components/table/DataTable'

export function AdminUsersPage() {
  const { data: users = [], isLoading } = useAdminUsers()
  const activate = useActivateUser()
  const deactivate = useDeactivateUser()
  const del = useDeleteUser()

  function onError(e: any) {
    alert(e.response?.data?.message || 'Action failed')
  }

  const columns: Column<(typeof users)[number]>[] = [
    { key: 'email', header: 'Email', cell: u => u.email },
    { key: 'name', header: 'Name', cell: u => u.displayName },
    { key: 'role', header: 'Role', width: '120px', cell: u => <span className="text-gray-400">{u.systemRole}</span> },
    {
      key: 'status',
      header: 'Status',
      width: '120px',
      cell: u => (
        <span className={`px-2 py-0.5 rounded text-xs ${
          u.active ? 'bg-green-900/50 text-green-300' : 'bg-gray-700 text-gray-400'
        }`}>
          {u.active ? 'Active' : 'Inactive'}
        </span>
      ),
    },
    {
      key: 'actions',
      header: '',
      width: '200px',
      align: 'right',
      cell: u => (
        <div className="flex gap-2 justify-end">
          {u.active ? (
            <button
              onClick={() => deactivate.mutate(u.id, { onError })}
              className="px-3 py-1 bg-gray-700 hover:bg-gray-600 rounded text-xs"
            >
              Deactivate
            </button>
          ) : (
            <button
              onClick={() => activate.mutate(u.id, { onError })}
              className="px-3 py-1 bg-green-900/40 hover:bg-green-800 text-green-300 rounded text-xs"
            >
              Activate
            </button>
          )}
          <button
            onClick={() => {
              if (confirm(`Delete ${u.email}? This anonymizes the account.`)) {
                del.mutate(u.id, { onError })
              }
            }}
            className="px-3 py-1 bg-red-900/40 hover:bg-red-800 text-red-300 rounded text-xs"
          >
            Delete
          </button>
        </div>
      ),
    },
  ]

  if (isLoading) return <div className="text-gray-400">Loading…</div>

  return (
    <div className="flex flex-col h-full min-h-0 max-w-3xl">
      <h1 className="text-2xl font-bold mb-6">Users</h1>
      <DataTable
        columns={columns}
        rows={users}
        rowKey={u => u.id}
        empty="No users"
      />
    </div>
  )
}
```

Hinweis: `columns` wird hier innerhalb der Komponente definiert, weil die `cell`-Renderer die Mutations-Hooks (`activate`/`deactivate`/`del`) referenzieren.

- [ ] **Step 2: Typecheck**

Run:
```bash
cd frontend && npx tsc --noEmit
```
Expected: PASS.

- [ ] **Step 3: Manuelle Kurz-Verifikation**

`/admin/users` (als ADMIN) öffnen: interner Scroll, sticky Header, Activate/Deactivate/Delete-Buttons funktionieren.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/admin/AdminUsersPage.tsx
git commit -m "refactor(frontend): migrate AdminUsersPage to scrollable DataTable

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 5: `AccessTokensPage` migrieren

**Files:**
- Modify: `frontend/src/pages/settings/AccessTokensPage.tsx`

**Interfaces:**
- Consumes: `DataTable`, `Column` aus `@/components/table/DataTable`.

- [ ] **Step 1: Nur den Tabellen-Block + Wrapper umstellen**

In `frontend/src/pages/settings/AccessTokensPage.tsx`:

1. Import ergänzen (nach den bestehenden Imports oben):

```tsx
import { DataTable, type Column } from '@/components/table/DataTable'
```

2. Spalten-Definition **innerhalb** der Komponente (vor dem `return`) einfügen — nach `handleCopy`:

```tsx
  const columns: Column<(typeof tokens)[number]>[] = [
    { key: 'prefix', header: 'Prefix', width: '140px', cell: t => <code className="text-green-400">{t.tokenPrefix}…</code> },
    { key: 'name', header: 'Name', cell: t => t.name },
    {
      key: 'scope',
      header: 'Scope',
      width: '140px',
      cell: t => (
        <span className={`px-2 py-0.5 rounded text-xs ${
          t.scope === 'READ_ONLY' ? 'bg-gray-700 text-gray-300' : 'bg-indigo-900/50 text-indigo-300'
        }`}>
          {t.scope === 'READ_ONLY' ? 'Read-only' : 'Read & Write'}
        </span>
      ),
    },
    { key: 'lastUsed', header: 'Last Used', width: '120px', cell: t => <span className="text-gray-400">{t.lastUsedAt ? new Date(t.lastUsedAt).toLocaleDateString() : 'Never'}</span> },
    { key: 'expires', header: 'Expires', width: '120px', cell: t => <span className="text-gray-400">{t.expiresAt ? new Date(t.expiresAt).toLocaleDateString() : 'Never'}</span> },
    {
      key: 'actions',
      header: '',
      width: '100px',
      align: 'right',
      cell: t => (
        <button
          onClick={() => revokeToken.mutate(t.id, {
            onError: (e: any) => alert(e.response?.data?.message || 'Failed to revoke token'),
          })}
          className="px-3 py-1 bg-red-900/40 hover:bg-red-800 text-red-400 hover:text-red-300 rounded text-xs"
        >
          Revoke
        </button>
      ),
    },
  ]
```

3. Den äußeren Wrapper `<div className="max-w-2xl">` ersetzen durch:

```tsx
    <div className="flex flex-col h-full min-h-0 max-w-2xl">
```

4. Den bestehenden `{tokens.length === 0 ? (...) : (<table>...</table>)}`-Block (das gesamte `<table>`-Markup) ersetzen durch:

```tsx
      <DataTable
        columns={columns}
        rows={tokens}
        rowKey={t => t.id}
        empty="No tokens yet."
      />
```

Wichtig: Der Create-Form-Block, die Token-Anzeige-Box und der Header (`flex items-center justify-between mb-6`) bleiben **unverändert** oberhalb der `DataTable`. Sie sind fixe Flex-Kinder; die `DataTable` (`flex-1`) füllt den Rest.

- [ ] **Step 2: Typecheck**

Run:
```bash
cd frontend && npx tsc --noEmit
```
Expected: PASS.

- [ ] **Step 3: Manuelle Kurz-Verifikation**

`/settings/tokens` öffnen: Token-Liste scrollt intern, sticky Header, Create/Revoke funktionieren, Create-Box schiebt die Liste nicht aus dem sichtbaren Bereich.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/settings/AccessTokensPage.tsx
git commit -m "refactor(frontend): migrate AccessTokensPage to scrollable DataTable

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 6: `ApiKeysPage` migrieren

**Files:**
- Modify: `frontend/src/pages/settings/ApiKeysPage.tsx`

**Interfaces:**
- Consumes: `DataTable`, `Column` aus `@/components/table/DataTable`.

- [ ] **Step 1: Tabellen-Block + Wrapper umstellen**

In `frontend/src/pages/settings/ApiKeysPage.tsx`:

1. Import ergänzen:

```tsx
import { DataTable, type Column } from '@/components/table/DataTable'
```

2. Spalten-Definition **innerhalb** der Komponente (vor dem `return`), nach `handleCopy`:

```tsx
  const columns: Column<(typeof keys)[number]>[] = [
    { key: 'prefix', header: 'Prefix', width: '140px', cell: k => <code className="text-green-400">{k.keyPrefix}…</code> },
    { key: 'name', header: 'Name', cell: k => k.name },
    { key: 'lastUsed', header: 'Last Used', width: '120px', cell: k => <span className="text-gray-400">{k.lastUsedAt ? new Date(k.lastUsedAt).toLocaleDateString() : 'Never'}</span> },
    { key: 'expires', header: 'Expires', width: '120px', cell: k => <span className="text-gray-400">{k.expiresAt ? new Date(k.expiresAt).toLocaleDateString() : 'Never'}</span> },
    {
      key: 'actions',
      header: '',
      width: '100px',
      align: 'right',
      cell: k => (
        <button
          onClick={() => revokeKey.mutate(k.id, {
            onError: (e: any) => alert(e.response?.data?.message || 'Failed to revoke key'),
          })}
          className="px-3 py-1 bg-red-900/40 hover:bg-red-800 text-red-400 hover:text-red-300 rounded text-xs"
        >
          Revoke
        </button>
      ),
    },
  ]
```

3. Den äußeren Wrapper `<div className="max-w-2xl">` ersetzen durch:

```tsx
    <div className="flex flex-col h-full min-h-0 max-w-2xl">
```

4. Den bestehenden `{keys.length === 0 ? (...) : (<table>...</table>)}`-Block ersetzen durch:

```tsx
      <DataTable
        columns={columns}
        rows={keys}
        rowKey={k => k.id}
        empty="No API keys yet."
      />
```

Header, Create-Form und die Key-Anzeige-Box bleiben unverändert oberhalb der `DataTable`.

- [ ] **Step 2: Typecheck**

Run:
```bash
cd frontend && npx tsc --noEmit
```
Expected: PASS.

- [ ] **Step 3: Manuelle Kurz-Verifikation**

`/p/<KEY>/settings/api-keys` öffnen: Liste scrollt intern, sticky Header, Create/Revoke funktionieren.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/settings/ApiKeysPage.tsx
git commit -m "refactor(frontend): migrate ApiKeysPage to scrollable DataTable

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Abschluss / Definition of Done

- [ ] `npx tsc --noEmit` grün.
- [ ] Alle fünf Seiten (Audit, Project-Audit, Users, Tokens, API-Keys) scrollen intern mit sticky Header, ohne Seiten-/Doppel-Scroll.
- [ ] Kein `<table>`-Markup mehr in den fünf migrierten Seiten (`grep -rl "<table" frontend/src/pages` liefert keine dieser Dateien).
- [ ] Comment-/Activity-Feeds bleiben unverändert.
- [ ] Backlog-Overview-Status für #7 nach Merge auf „Ausgeliefert" aktualisieren.

## Self-Review-Ergebnis (gegen Spec)

- **Spec-Abdeckung:** DataTable-Komponente (Task 1), sticky Header + Viewport-Resthöhe + Virtualisierung (Task 1), alle fünf `<table>`-Seiten migriert (Tasks 2–6), `@tanstack/react-virtual`-Dependency (Task 1), Feeds ausgeschlossen (Constraints + DoD). ✅
- **Platzhalter:** keine — jeder Code-Step enthält vollständigen Code.
- **Typ-Konsistenz:** `Column<T>` / `DataTableProps<T>` in Task 1 definiert, in Tasks 2–6 unverändert konsumiert; `type Column`-Import konsistent.
