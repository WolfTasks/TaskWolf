# React Router v8 Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** react-router von 7.18.1 auf 8.3.0 heben (behebt `GHSA-qwww-vcr4-c8h2` echt) und Route-basiertes Lazy-Code-Splitting einführen.

**Architecture:** Zwei getrennte PRs im selben Worktree. PR 1 = reine Security-Migration (Paket-Swap, 42 Import-Umstellungen, `RouterProvider`→`react-router/dom`, CI-Node 20→22, drei Interim-Ausnahmen entfernen). PR 2 = Lazy-Splitting per `React.lazy` + `<Suspense>` um den `<Outlet/>` jedes Layouts. Wir bleiben Data-/Library-Mode (`createBrowserRouter` + `RouterProvider`), element-basiert, ohne Loader/Framework-Features.

**Tech Stack:** React 19.2.7, react-router 8.3.0, TypeScript, Vite 8, TailwindCSS, Vitest gibt es NICHT (Frontend hat kein Test-Framework).

## Global Constraints

- **Node ≥ 22.22** in CI (`.github/workflows/ci.yml` `setup-node`), sonst bricht `vite build` unter v8-Engines. Muss mit dem Paket-Bump im selben PR landen.
- **React ≥ 19.2.7** — Repo ist exakt `19.2.7` (peerDep react-router@8.3.0). Nicht downgraden.
- **`react-router` `^8.3.0`**; `react-router-dom` gibt es in v8 NICHT mehr — vollständig entfernen (auch aus `package-lock.json`).
- **`RouterProvider` kommt aus `react-router/dom`**, alle anderen Symbole aus `react-router`.
- **Kein** Loader/Action/Data-Router-Umbau — Routen bleiben element-basiert (`element: <Page/>`).
- **Kein Frontend-Test-Framework** → Verifikation je Task = `npx tsc --noEmit` + `npm run build` + gezielte `grep`/`npm audit`-Checks; finaler Gate = manueller DE/EN-Browser-Smoke.
- Code-Stil: **single quotes**, benannte Exports Standard (4 Ausnahmen sind default-Exports, s.u.).
- Alle `cd`/Befehle laufen aus `frontend/`, sofern nicht anders angegeben.

---

## PR 1 — Security-Migration

### Task A0: Worktree anlegen

**Files:** keine (Setup)

- [ ] **Step 1: Isolierten Worktree erstellen**

Verwende das `superpowers:using-git-worktrees`-Skill (oder nativ) für einen Worktree von `origin/main`. Branch-Name: `worktree-react-router-v8`. Alle folgenden Tasks laufen darin.

- [ ] **Step 2: Baseline verifizieren**

Run (aus `frontend/`): `npm ci && npx tsc --noEmit`
Expected: PASS (grüne Baseline vor jeder Änderung).

---

### Task A1: react-router-dom → react-router@8.3.0 im Manifest (Rot)

**Files:**
- Modify: `frontend/package.json` (dependencies)
- Modify: `frontend/package-lock.json` (via npm install)

**Interfaces:**
- Produces: Paket `react-router@^8.3.0` installiert, `react-router-dom` entfernt. Danach brechen alle 42 Import-Stellen (react-router-dom nicht mehr auflösbar) — das ist der beabsichtigte „rote" Zustand für Task A2.

- [ ] **Step 1: Paket tauschen**

Run (aus `frontend/`):
```bash
npm uninstall react-router-dom
npm install react-router@^8.3.0
```

- [ ] **Step 2: Lockfile-Sauberkeit verifizieren**

Run: `grep -c "node_modules/react-router-dom" package-lock.json`
Expected: `0` (react-router-dom vollständig raus). Zusätzlich:
Run: `node -e "console.log(require('./node_modules/react-router/package.json').version)"`
Expected: `8.3.0` (oder höher innerhalb ^8.3.0).

- [ ] **Step 3: Roten Zustand bestätigen**

Run: `npx tsc --noEmit`
Expected: FAIL mit vielen `Cannot find module 'react-router-dom'`-Fehlern (42 Dateien). Das beweist, dass A2 nötig ist.

- [ ] **Step 4: Commit**

```bash
git add package.json package-lock.json
git commit -m "build(deps): react-router-dom -> react-router@8.3.0"
```

---

### Task A2: Import-Umstellung 42 Dateien (Grün)

**Files:**
- Modify: alle 42 Dateien mit `from 'react-router-dom'` unter `frontend/src/**`
- Modify (Sonderfall): `frontend/src/main.tsx`

**Interfaces:**
- Consumes: react-router@8.3.0 aus A1.
- Produces: alle Imports auf `react-router` umgestellt; `RouterProvider` in `main.tsx` aus `react-router/dom`. typecheck + build wieder grün.

- [ ] **Step 1: Blanket-Rewrite aller react-router-dom-Imports**

Run (aus `frontend/`):
```bash
grep -rl "from 'react-router-dom'" src/ | xargs sed -i "s/from 'react-router-dom'/from 'react-router'/g"
```

- [ ] **Step 2: Verifizieren, dass kein react-router-dom-Import übrig ist**

Run: `grep -rn "react-router-dom" src/ ; echo "exit=$?"`
Expected: keine Treffer (`exit=1` von grep).

- [ ] **Step 3: Sonderfall RouterProvider fixen**

`main.tsx` importiert nur `RouterProvider`. Nach dem Blanket-Rewrite steht dort `from 'react-router'` — das MUSS `react-router/dom` sein. Ändere in `frontend/src/main.tsx`:

```ts
// vorher (nach Step 1):
import { RouterProvider } from 'react-router'
// nachher:
import { RouterProvider } from 'react-router/dom'
```

- [ ] **Step 4: Verifizieren**

Run: `grep -n "RouterProvider" src/main.tsx`
Expected: `import { RouterProvider } from 'react-router/dom'`

- [ ] **Step 5: Typecheck + Build (Grün)**

Run: `npx tsc --noEmit && npm run build`
Expected: PASS (0 Fehler, Build erzeugt `dist/`).

- [ ] **Step 6: Commit**

```bash
git add src/
git commit -m "refactor: migrate react-router-dom imports to react-router (v8)"
```

---

### Task A3: CI Node 20 → 22

**Files:**
- Modify: `.github/workflows/ci.yml` (setup-node Step, `node-version`)

**Interfaces:**
- Produces: CI baut unter Node 22 (erfüllt v8-Anforderung Node ≥ 22.22).

- [ ] **Step 1: node-version anheben**

In `.github/workflows/ci.yml`, im `frontend-build`-Job, ändere:
```yaml
        with:
          node-version: '20'
```
zu:
```yaml
        with:
          node-version: '22'
```

- [ ] **Step 2: Verifizieren**

Run (aus Repo-Root): `grep -n "node-version" .github/workflows/ci.yml`
Expected: `node-version: '22'`

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: bump setup-node 20 -> 22 for react-router v8 (Node >=22.22)"
```

---

### Task A4: Drei Interim-CVE-Ausnahmen entfernen

**Files:**
- Modify: `.trivyignore` (GHSA-qwww-vcr4-c8h2-Zeile raus)
- Modify: `.github/scripts/audit-gate.mjs` (ALLOWLIST-Eintrag raus)

**Interfaces:**
- Consumes: react-router@8.3.0 aus A1 (nicht mehr verwundbar).
- Produces: leere/ausnahmslose Gates, die trotzdem grün sind, weil die CVE echt weg ist.

- [ ] **Step 1: Vorbedingung — kein HIGH/CRITICAL mehr**

Run (aus `frontend/`): `npm audit --audit-level=high ; echo "exit=$?"`
Expected: `exit=0` (keine HIGH/CRITICAL; react-router 8.3.0 ist gefixt).

- [ ] **Step 2: `.trivyignore`-Ausnahme entfernen**

Aus `.trivyignore` den kompletten Block für `GHSA-qwww-vcr4-c8h2` entfernen (Kommentarzeilen + die `GHSA-qwww-vcr4-c8h2  # ...`-Zeile). Zurück bleibt nur der ursprüngliche Header (`# Begründete Ausnahmen. ...` + Beispielzeile).

- [ ] **Step 3: `audit-gate.mjs`-ALLOWLIST leeren**

In `.github/scripts/audit-gate.mjs` den `ALLOWLIST`-Eintrag `'GHSA-QWWW-VCR4-C8H2': '...'` entfernen, sodass gilt:
```js
const ALLOWLIST = {};
```
(Den erklärenden Kommentar über `ALLOWLIST` beibehalten.)

- [ ] **Step 4: Gate verifizieren (scharf, ohne Ausnahme, trotzdem grün)**

Run (aus `frontend/`): `node ../.github/scripts/audit-gate.mjs ; echo "exit=$?"`
Expected: `✅ npm-audit-Gate ok` und `exit=0`. (Kein „Aktive begründete Ausnahmen"-Zeile mehr.)

- [ ] **Step 5: Commit**

```bash
git add .trivyignore .github/scripts/audit-gate.mjs
git commit -m "revert(security): drop react-router CVE suppressions — fixed by v8 upgrade"
```

---

### Task A5: PR 1 öffnen & verifizieren

**Files:** keine (Integration)

- [ ] **Step 1: Push + PR**

```bash
git push -u origin worktree-react-router-v8
gh pr create --title "feat(deps): react-router v8 migration (fix GHSA-qwww-vcr4-c8h2)" --body "<Kurzbeschreibung: v8-Migration, behebt CVE echt, entfernt die drei Interim-Ausnahmen>"
```

- [ ] **Step 2: CI grün abwarten**

Run: `gh pr checks <pr-nummer>`
Expected: alle Checks `pass` — insbesondere `frontend-build` (npm-audit-Gate jetzt OHNE Ausnahme grün, Build unter Node 22) und der Trivy-Gate.

- [ ] **Step 3: Manueller DE/EN-Browser-Smoke**

App lokal starten (siehe reference-local-docker-run bzw. `npm run dev`) und in DE **und** EN prüfen: Login → Sidebar-Navigation (`useMatch`) → Board → Issue-Dialog via `?issue=` (`useSearchParams`) → Settings-Tabs (`Outlet`) → Deep-Link auf `/p/:key/issues/:issueKey` (`useParams`). Erwartung: alles funktioniert wie vor der Migration, keine Konsolenfehler.

- [ ] **Step 4: Merge**

Nach grüner CI + OK-Smoke: `gh pr merge <pr-nummer> --squash --delete-branch`
(Owed danach: Wolfgang-Release als eigene v1.0.x — shippt den echten CVE-Fix.)

---

## PR 2 — Lazy-Route-Code-Splitting

> Setzt auf gemergtem PR 1 auf. Worktree auf aktuellen `main` bringen (rebase/ff), neuen Branch `worktree-react-router-lazy` oder auf demselben Worktree weiterarbeiten.

### Task B1: RouteFallback-Komponente

**Files:**
- Create: `frontend/src/components/RouteFallback.tsx`

**Interfaces:**
- Produces: `export function RouteFallback(): JSX.Element` — zentrierter Spinner, wird als Suspense-Fallback in allen Layouts genutzt.

- [ ] **Step 1: Komponente anlegen**

Create `frontend/src/components/RouteFallback.tsx`:
```tsx
import { Loader2 } from 'lucide-react'

export function RouteFallback() {
  return (
    <div className="flex h-full min-h-40 items-center justify-center text-gray-500">
      <Loader2 className="animate-spin" size={28} aria-label="Loading" />
    </div>
  )
}
```
(`lucide-react` ist bereits Dependency — vgl. AppLayout-Imports.)

- [ ] **Step 2: Typecheck**

Run (aus `frontend/`): `npx tsc --noEmit`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add src/components/RouteFallback.tsx
git commit -m "feat(ui): RouteFallback spinner for lazy routes"
```

---

### Task B2: router.tsx auf React.lazy umstellen

**Files:**
- Modify (vollständiger Ersatz): `frontend/src/app/router.tsx`

**Interfaces:**
- Consumes: alle Page-Komponenten (unverändert), `RouteFallback` (nur indirekt — Fallback sitzt in den Layouts, s. B3).
- Produces: identische Routen-Struktur, aber Leaf-Pages via `React.lazy`; Layouts + `RequireAuth` + `Navigate` bleiben eager.

- [ ] **Step 1: router.tsx ersetzen**

Ersetze den gesamten Inhalt von `frontend/src/app/router.tsx` durch:
```tsx
import { lazy } from 'react'
import { createBrowserRouter, Navigate } from 'react-router'
import { AuthLayout } from '@/layouts/AuthLayout'
import { AppLayout } from '@/layouts/AppLayout'
import { SettingsLayout } from '@/layouts/SettingsLayout'

// Lazy-Helper: kapselt named- und default-Exports einheitlich als React.lazy.
const lazyPage = (imp: () => Promise<any>, name: string) =>
  lazy(() => imp().then((m) => ({ default: m[name] })))

// Leaf-Pages (lazy). default-Export-Pages nutzen name = 'default'.
const LoginPage = lazyPage(() => import('@/pages/auth/LoginPage'), 'LoginPage')
const RegisterPage = lazyPage(() => import('@/pages/auth/RegisterPage'), 'RegisterPage')
const DashboardPage = lazyPage(() => import('@/pages/dashboard/DashboardPage'), 'DashboardPage')
const ProjectListPage = lazyPage(() => import('@/pages/projects/ProjectListPage'), 'ProjectListPage')
const ProjectCreatePage = lazyPage(() => import('@/pages/projects/ProjectCreatePage'), 'ProjectCreatePage')
const IssueListPage = lazyPage(() => import('@/pages/issues/IssueListPage'), 'IssueListPage')
const IssueDetailPage = lazyPage(() => import('@/pages/issues/IssueDetailPage'), 'IssueDetailPage')
const BoardPage = lazyPage(() => import('@/pages/board/BoardPage'), 'BoardPage')
const BacklogPage = lazyPage(() => import('@/pages/backlog/BacklogPage'), 'BacklogPage')
const SprintsPage = lazyPage(() => import('@/pages/sprints/SprintsPage'), 'SprintsPage')
const ProjectDashboardPage = lazyPage(() => import('@/pages/project-dashboard/ProjectDashboardPage'), 'ProjectDashboardPage')
const ReportsPage = lazyPage(() => import('@/pages/reports/ReportsPage'), 'ReportsPage')
const NotificationsPage = lazyPage(() => import('@/pages/notifications/NotificationsPage'), 'NotificationsPage')
const WorkflowEditorPage = lazyPage(() => import('@/pages/settings/WorkflowEditorPage'), 'WorkflowEditorPage')
const AutomationPage = lazyPage(() => import('@/pages/automation/AutomationPage'), 'AutomationPage')
const AutomationRuleEditorPage = lazyPage(() => import('@/pages/automation/AutomationRuleEditorPage'), 'AutomationRuleEditorPage')
const AdminAutomationPage = lazyPage(() => import('@/pages/admin/AdminAutomationPage'), 'AdminAutomationPage')
const AuditLogPage = lazyPage(() => import('@/pages/admin/AuditLogPage'), 'default')
const SsoSettingsPage = lazyPage(() => import('@/pages/admin/SsoSettingsPage'), 'SsoSettingsPage')
const ApiKeysPage = lazyPage(() => import('@/pages/settings/ApiKeysPage'), 'ApiKeysPage')
const AccessTokensPage = lazyPage(() => import('@/pages/settings/AccessTokensPage'), 'AccessTokensPage')
const AccountSettingsPage = lazyPage(() => import('@/pages/settings/AccountSettingsPage'), 'AccountSettingsPage')
const ProfilePage = lazyPage(() => import('@/pages/settings/ProfilePage'), 'ProfilePage')
const SecurityPage = lazyPage(() => import('@/pages/settings/SecurityPage'), 'SecurityPage')
const NotificationSettingsPage = lazyPage(() => import('@/pages/settings/NotificationSettingsPage'), 'NotificationSettingsPage')
const AdminUsersPage = lazyPage(() => import('@/pages/admin/AdminUsersPage'), 'AdminUsersPage')
const WebhooksPage = lazyPage(() => import('@/pages/settings/WebhooksPage'), 'WebhooksPage')
const IntegrationsPage = lazyPage(() => import('@/pages/settings/IntegrationsPage'), 'IntegrationsPage')
const ProjectAuditPage = lazyPage(() => import('@/pages/projects/settings/ProjectAuditPage'), 'default')
const LabelsPage = lazyPage(() => import('@/pages/projects/settings/LabelsPage'), 'LabelsPage')
const VersionsPage = lazyPage(() => import('@/pages/projects/settings/VersionsPage'), 'VersionsPage')
const CustomFieldsPage = lazyPage(() => import('@/pages/projects/settings/CustomFieldsPage'), 'CustomFieldsPage')
const MembersPage = lazyPage(() => import('@/pages/projects/settings/MembersPage'), 'MembersPage')
const OrganizationSettingsPage = lazyPage(() => import('@/pages/projects/settings/OrganizationSettingsPage'), 'OrganizationSettingsPage')
const OrgsPage = lazyPage(() => import('@/pages/orgs/OrgsPage'), 'OrgsPage')
const OrgSettingsPage = lazyPage(() => import('@/pages/orgs/OrgSettingsPage'), 'OrgSettingsPage')
const ServiceDeskPage = lazyPage(() => import('@/pages/projects/servicedesk/ServiceDeskPage'), 'default')
const IncidentDashboardPage = lazyPage(() => import('@/pages/projects/servicedesk/IncidentDashboardPage'), 'default')

const isAuthenticated = () => !!localStorage.getItem('accessToken')

function RequireAuth({ children }: { children: React.ReactNode }) {
  return isAuthenticated() ? <>{children}</> : <Navigate to="/login" replace />
}

export const router = createBrowserRouter([
  {
    element: <AuthLayout />,
    children: [
      { path: '/login', element: <LoginPage /> },
      { path: '/register', element: <RegisterPage /> },
    ],
  },
  {
    element: <RequireAuth><AppLayout /></RequireAuth>,
    children: [
      { path: '/', element: <DashboardPage /> },
      { path: '/projects', element: <ProjectListPage /> },
      { path: '/projects/new', element: <ProjectCreatePage /> },
      { path: '/p/:key/issues', element: <IssueListPage /> },
      { path: '/p/:key/issues/:issueKey', element: <IssueDetailPage /> },
      { path: '/p/:key/dashboard', element: <ProjectDashboardPage /> },
      { path: '/p/:key/board', element: <BoardPage /> },
      { path: '/p/:key/backlog', element: <BacklogPage /> },
      { path: '/p/:key/sprints', element: <SprintsPage /> },
      { path: '/p/:key/reports', element: <ReportsPage /> },
      { path: '/notifications', element: <NotificationsPage /> },
      {
        path: '/settings',
        element: <SettingsLayout />,
        children: [
          { index: true, element: <Navigate to="/settings/profile" replace /> },
          { path: 'profile', element: <ProfilePage /> },
          { path: 'security', element: <SecurityPage /> },
          { path: 'notifications', element: <NotificationSettingsPage /> },
          { path: 'tokens', element: <AccessTokensPage /> },
          { path: 'account', element: <AccountSettingsPage /> },
        ],
      },
      { path: '/admin/users', element: <AdminUsersPage /> },
      { path: '/p/:key/settings/workflow', element: <WorkflowEditorPage /> },
      { path: '/p/:key/automation', element: <AutomationPage /> },
      { path: '/p/:key/automation/new', element: <AutomationRuleEditorPage /> },
      { path: '/p/:key/automation/:rid/edit', element: <AutomationRuleEditorPage /> },
      { path: '/p/:key/settings/api-keys', element: <ApiKeysPage /> },
      { path: '/p/:key/settings/webhooks', element: <WebhooksPage /> },
      { path: '/p/:key/settings/integrations', element: <IntegrationsPage /> },
      { path: '/p/:key/settings/audit', element: <ProjectAuditPage /> },
      { path: '/p/:key/settings/labels', element: <LabelsPage /> },
      { path: '/p/:key/settings/versions', element: <VersionsPage /> },
      { path: '/p/:key/settings/custom-fields', element: <CustomFieldsPage /> },
      { path: '/p/:key/settings/members', element: <MembersPage /> },
      { path: '/p/:key/settings/organization', element: <OrganizationSettingsPage /> },
      { path: '/admin/automation', element: <AdminAutomationPage /> },
      { path: '/admin/audit', element: <AuditLogPage /> },
      { path: '/admin/settings/sso', element: <SsoSettingsPage /> },
      { path: '/orgs', element: <OrgsPage /> },
      { path: '/orgs/:orgId/settings', element: <OrgSettingsPage /> },
      { path: '/p/:key/service-desk', element: <ServiceDeskPage /> },
      { path: '/p/:key/incidents', element: <IncidentDashboardPage /> },
    ],
  },
])
```

- [ ] **Step 2: Typecheck**

Run (aus `frontend/`): `npx tsc --noEmit`
Expected: PASS. (Build kommt erst nach B3, weil ohne Suspense-Boundary die lazy Pages sonst zur Laufzeit suspenden würden — Typecheck reicht hier.)

- [ ] **Step 3: Commit**

```bash
git add src/app/router.tsx
git commit -m "perf(router): lazy-load leaf pages via React.lazy"
```

---

### Task B3: Suspense-Boundaries in den 3 Layouts

**Files:**
- Modify: `frontend/src/layouts/AppLayout.tsx` (Outlet in `<main>`, Zeilen ~148-151)
- Modify: `frontend/src/layouts/SettingsLayout.tsx` (Outlet, Zeilen ~34-36)
- Modify: `frontend/src/layouts/AuthLayout.tsx` (Outlet, Zeile ~11)

**Interfaces:**
- Consumes: `RouteFallback` (B1), lazy Pages (B2).
- Produces: jeder `<Outlet/>` in eine `<Suspense fallback={<RouteFallback/>}>` gehüllt — Layout-Shell bleibt beim Lazy-Load stehen.

- [ ] **Step 1: AppLayout**

In `frontend/src/layouts/AppLayout.tsx`:
- Import ergänzen: `import { Suspense } from 'react'` und `import { RouteFallback } from '@/components/RouteFallback'`.
- Den `<main>`-Block ändern von:
```tsx
      <main className="flex-1 overflow-auto p-8">
        <Outlet />
        {projectKey && <IssueDialogHost projectKey={projectKey} />}
      </main>
```
zu:
```tsx
      <main className="flex-1 overflow-auto p-8">
        <Suspense fallback={<RouteFallback />}>
          <Outlet />
        </Suspense>
        {projectKey && <IssueDialogHost projectKey={projectKey} />}
      </main>
```

- [ ] **Step 2: SettingsLayout**

In `frontend/src/layouts/SettingsLayout.tsx`:
- Import ergänzen: `import { Suspense } from 'react'` und `import { RouteFallback } from '@/components/RouteFallback'`.
- Ändern von:
```tsx
      <div className="flex-1 min-h-0">
        <Outlet />
      </div>
```
zu:
```tsx
      <div className="flex-1 min-h-0">
        <Suspense fallback={<RouteFallback />}>
          <Outlet />
        </Suspense>
      </div>
```

- [ ] **Step 3: AuthLayout**

In `frontend/src/layouts/AuthLayout.tsx`:
- Import ergänzen: `import { Suspense } from 'react'` und `import { RouteFallback } from '@/components/RouteFallback'`.
- Ändern von:
```tsx
        <VersionTag className="block text-center mb-8" />
        <Outlet />
```
zu:
```tsx
        <VersionTag className="block text-center mb-8" />
        <Suspense fallback={<RouteFallback />}>
          <Outlet />
        </Suspense>
```

- [ ] **Step 4: Typecheck + Build (Grün)**

Run (aus `frontend/`): `npx tsc --noEmit && npm run build`
Expected: PASS.

- [ ] **Step 5: Chunk-Splitting belegen**

Run (aus `frontend/`): `ls dist/assets/*.js | wc -l`
Expected: deutlich mehr als vor der Migration (viele per-Page-Chunks statt eines Monolithen). Stichprobe:
Run: `ls dist/assets/ | grep -iE "BoardPage|IssueDetailPage|ReportsPage" | head`
Expected: eigene Chunk-Dateien für einzelne Pages sichtbar.

- [ ] **Step 6: Commit**

```bash
git add src/layouts/AppLayout.tsx src/layouts/SettingsLayout.tsx src/layouts/AuthLayout.tsx
git commit -m "feat(router): Suspense fallback around each layout Outlet"
```

---

### Task B4: PR 2 öffnen & verifizieren

**Files:** keine (Integration)

- [ ] **Step 1: Push + PR**

```bash
git push -u origin worktree-react-router-lazy
gh pr create --title "perf(router): route-based code-splitting via React.lazy" --body "<Kurzbeschreibung: Lazy-Leaf-Pages + Suspense pro Layout, kleineres Initial-Bundle>"
```

- [ ] **Step 2: CI grün abwarten**

Run: `gh pr checks <pr-nummer>`
Expected: alle Checks `pass`.

- [ ] **Step 3: Manueller DE/EN-Browser-Smoke (mit Fokus Lazy)**

In DE **und** EN: Hard-Refresh auf tiefen Deep-Links (`/p/:key/board`, `/p/:key/issues/:issueKey`, `/settings/security`) → kurz RouteFallback-Spinner im Content-Bereich, **Sidebar/Settings-Nav bleiben sichtbar**, dann Page. Navigation zwischen Pages: kein Flackern der Shell. Keine Konsolenfehler.

- [ ] **Step 4: Merge**

Nach grüner CI + OK-Smoke: `gh pr merge <pr-nummer> --squash --delete-branch`

---

## Abschluss

- [ ] Memory/CHANGELOG aktualisieren; `docs/superpowers/plans/2026-07-29-react-router-v8-migration.md` (Backlog) als erledigt markieren.
- [ ] Release als eigene v1.0.x (Wolfgangs Prozess) — shippt den echten CVE-Fix + Bundle-Splitting.
