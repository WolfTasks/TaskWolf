# Einklappbare Sidebar Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Die linke Sidebar (`AppLayout`) zwischen breit (Icon + Label) und schmaler Icon-Rail (nur Icons + `title`-Tooltips) umschaltbar machen, Zustand in `localStorage` persistiert, mit Auto-Collapse unterhalb 1024px.

**Architecture:** Ein Zustands-Hook `useSidebarCollapsed` kapselt `collapsed`-State, localStorage-Persistenz und einen `matchMedia`-Breakpoint-Listener. Eine neue `NavItem`-Komponente kapselt Icon + Label + aktiven Zustand für beide Modi und ersetzt die bisherigen Inline-Klassen-Helper. `AppLayout` wird auf `NavItem` + den Hook umgebaut, Icons kommen aus `lucide-react`.

**Tech Stack:** React 19, TypeScript 6, Tailwind CSS 4, react-router-dom 7 (`NavLink`), `lucide-react` (bereits Dependency, v1.23.0, bisher ungenutzt).

## Global Constraints

- Frontend hat **kein** Test-Framework. Automatischer Gate: `cd frontend && npx tsc --noEmit` muss exit 0 sein. Funktionaler Gate ist **manuelle** Verifikation im Dev-Server. Keine Test-Dateien erfinden.
- Import-Alias `@/` zeigt auf `frontend/src/`.
- Icons ausschließlich aus `lucide-react` (bereits installiert; keine neue Dependency).
- Persistenz-Key: `localStorage['sidebar-collapsed']` (String `'true'`/`'false'`).
- Breakpoint für Auto-Collapse: `max-width: 1024px`. Unterhalb → erzwungene Icon-Rail (kein Off-Canvas-Overlay).
- Tooltips im eingeklappten Modus: natives `title`-Attribut (keine Tooltip-Library).
- Bestehendes Dark-Theme beibehalten: aktive Top-Level `bg-gray-700 text-white`, aktive Sub-Links `bg-indigo-600 text-white`, inaktiv `text-gray-300`/`text-gray-400` mit `hover:bg-gray-800 hover:text-white`.
- Commit-Nachrichten enden mit:
  `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`

---

### Task 1: `useSidebarCollapsed`-Hook

**Files:**
- Create: `frontend/src/hooks/useSidebarCollapsed.ts`

**Interfaces:**
- Consumes: nichts.
- Produces:
  - `function useSidebarCollapsed(): { collapsed: boolean; toggle: () => void; belowBreakpoint: boolean }`
  - `collapsed` = effektiver Zustand (bei `belowBreakpoint` immer `true`).
  - `toggle()` schaltet die gespeicherte User-Präferenz um (oberhalb des Breakpoints wirksam; unterhalb bleibt der effektive Zustand `true`).
  - `belowBreakpoint` = aktuell `< 1024px`.

- [ ] **Step 1: Hook schreiben**

Create `frontend/src/hooks/useSidebarCollapsed.ts`:

```ts
import { useEffect, useState, useCallback } from 'react'

const STORAGE_KEY = 'sidebar-collapsed'
const BREAKPOINT = '(max-width: 1024px)'

function readStoredPreference(): boolean {
  return localStorage.getItem(STORAGE_KEY) === 'true'
}

export function useSidebarCollapsed(): {
  collapsed: boolean
  toggle: () => void
  belowBreakpoint: boolean
} {
  const [preference, setPreference] = useState<boolean>(readStoredPreference)
  const [belowBreakpoint, setBelowBreakpoint] = useState<boolean>(
    () => window.matchMedia(BREAKPOINT).matches,
  )

  useEffect(() => {
    const mql = window.matchMedia(BREAKPOINT)
    const onChange = (e: MediaQueryListEvent) => setBelowBreakpoint(e.matches)
    mql.addEventListener('change', onChange)
    return () => mql.removeEventListener('change', onChange)
  }, [])

  const toggle = useCallback(() => {
    setPreference(prev => {
      const next = !prev
      localStorage.setItem(STORAGE_KEY, String(next))
      return next
    })
  }, [])

  return {
    collapsed: belowBreakpoint || preference,
    toggle,
    belowBreakpoint,
  }
}
```

Hinweise:
- Der effektive `collapsed` ist `belowBreakpoint || preference` — unterhalb 1024px immer eingeklappt, oberhalb greift die gespeicherte Präferenz.
- `toggle` verändert nur die persistierte Präferenz; unterhalb des Breakpoints bleibt der sichtbare Zustand eingeklappt.

- [ ] **Step 2: Typecheck**

Run:
```bash
cd frontend && npx tsc --noEmit
```
Expected: PASS (exit 0). Der Hook ist noch ungenutzt, muss aber kompilieren.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/hooks/useSidebarCollapsed.ts
git commit -m "feat(frontend): add useSidebarCollapsed hook (localStorage + breakpoint)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: `NavItem`-Komponente

**Files:**
- Create: `frontend/src/components/nav/NavItem.tsx`

**Interfaces:**
- Consumes: nichts (nur `react-router-dom` `NavLink`, `lucide-react` Icon-Typ).
- Produces:
  - `interface NavItemProps { to: string; label: string; icon: LucideIcon; collapsed: boolean; end?: boolean; variant?: 'top' | 'sub' }`
  - `function NavItem(props: NavItemProps): JSX.Element`
  - `variant` steuert aktives Styling: `'top'` (Default) → `bg-gray-700`; `'sub'` → `bg-indigo-600`.

- [ ] **Step 1: Komponente schreiben**

Create `frontend/src/components/nav/NavItem.tsx`:

```tsx
import { NavLink } from 'react-router-dom'
import type { LucideIcon } from 'lucide-react'

export interface NavItemProps {
  to: string
  label: string
  icon: LucideIcon
  collapsed: boolean
  end?: boolean
  variant?: 'top' | 'sub'
}

export function NavItem({ to, label, icon: Icon, collapsed, end, variant = 'top' }: NavItemProps) {
  const activeBg = variant === 'top' ? 'bg-gray-700 text-white font-semibold' : 'bg-indigo-600 text-white font-semibold'
  const idleText = variant === 'top' ? 'text-gray-300' : 'text-gray-400'
  const size = variant === 'top' ? 'py-2' : 'py-1.5'

  return (
    <NavLink
      to={to}
      end={end}
      title={collapsed ? label : undefined}
      className={({ isActive }) =>
        `flex items-center gap-3 rounded text-sm ${size} ${collapsed ? 'justify-center px-2' : 'px-3'} ` +
        (isActive ? activeBg : `${idleText} hover:bg-gray-800 hover:text-white`)
      }
    >
      <Icon size={18} className="shrink-0" />
      {!collapsed && <span className="truncate">{label}</span>}
    </NavLink>
  )
}
```

Hinweise:
- Eingeklappt: nur Icon, zentriert (`justify-center`), Label als `title`-Tooltip.
- Ausgeklappt: Icon + Label (leichter visueller Zugewinn gegenüber heute, wo Icons fehlten).
- `LucideIcon` ist der offizielle Typ für lucide-react-Icon-Komponenten.

- [ ] **Step 2: Typecheck**

Run:
```bash
cd frontend && npx tsc --noEmit
```
Expected: PASS (exit 0).

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/nav/NavItem.tsx
git commit -m "feat(frontend): add NavItem component (icon + collapsible label)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: `AppLayout` auf Hook + `NavItem` umbauen (Kern)

**Files:**
- Modify: `frontend/src/layouts/AppLayout.tsx`

**Interfaces:**
- Consumes:
  - `useSidebarCollapsed()` aus `@/hooks/useSidebarCollapsed`
  - `NavItem` aus `@/components/nav/NavItem`
  - Icons aus `lucide-react`.

- [ ] **Step 1: `AppLayout.tsx` komplett ersetzen**

Ersetze den gesamten Inhalt von `frontend/src/layouts/AppLayout.tsx`:

```tsx
import { Outlet, Link, useNavigate, useMatch } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import {
  LayoutDashboard, FolderKanban, Building2, ScrollText, Zap, Users,
  KeyRound, User, Kanban, ListChecks, CalendarRange, ListTodo, BarChart3,
  LifeBuoy, AlertTriangle, KeySquare, Webhook, Plug, Tags, Milestone,
  SlidersHorizontal, ChevronLeft, ChevronRight,
} from 'lucide-react'
import { NotificationBell } from '@/components/notifications/NotificationBell'
import { OrgSwitcher } from '@/components/OrgSwitcher'
import { authApi } from '@/api/auth'
import { serviceDeskApi } from '@/api/servicedesk'
import { IssueDialogHost } from '@/components/issue/IssueDialogHost'
import { VersionTag } from '@/components/VersionTag'
import { useSidebarCollapsed } from '@/hooks/useSidebarCollapsed'
import { NavItem } from '@/components/nav/NavItem'

export function AppLayout() {
  const navigate = useNavigate()
  const insideProject = useMatch('/p/:key/*')
  const projectKey = insideProject?.params.key
  const { collapsed, toggle, belowBreakpoint } = useSidebarCollapsed()

  const { data: me } = useQuery({
    queryKey: ['me'],
    queryFn: () => authApi.me().then(r => r.data),
  })

  const { data: serviceDeskConfig } = useQuery({
    queryKey: ['service-desk-config', projectKey],
    queryFn: () => serviceDeskApi.get(projectKey!),
    enabled: !!projectKey,
  })

  const logout = async () => {
    try { await authApi.logout() } catch { /* ignore */ }
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    navigate('/login')
  }

  const sectionLabel = (text: string) =>
    !collapsed && (
      <p className="px-3 text-xs font-semibold text-gray-500 uppercase tracking-wider mb-1">{text}</p>
    )

  return (
    <div id="app-root" className="min-h-screen bg-gray-950 text-white flex">
      <aside className={`${collapsed ? 'w-16' : 'w-56'} bg-gray-900 border-r border-gray-800 flex flex-col p-4 transition-[width] duration-200`}>
        <div className={`flex items-center mb-8 ${collapsed ? 'justify-center' : 'justify-between'}`}>
          {!collapsed && <Link to="/" className="text-xl font-bold">🐺 TaskWolf</Link>}
          {!belowBreakpoint && (
            <button
              onClick={toggle}
              title={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
              aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
              className="p-1 rounded text-gray-400 hover:bg-gray-800 hover:text-white"
            >
              {collapsed ? <ChevronRight size={18} /> : <ChevronLeft size={18} />}
            </button>
          )}
        </div>

        <nav className="flex flex-col gap-1 flex-1">
          <NavItem to="/" end label="Dashboard" icon={LayoutDashboard} collapsed={collapsed} />
          <NavItem to="/projects" end label="Projects" icon={FolderKanban} collapsed={collapsed} />
          <NavItem to="/orgs" end label="Organizations" icon={Building2} collapsed={collapsed} />

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

          <div className="mt-4">
            {sectionLabel('Account')}
            <div className="flex flex-col gap-1">
              <NavItem to="/settings/tokens" label="Access Tokens" icon={KeyRound} collapsed={collapsed} variant="sub" />
              <NavItem to="/settings/account" label="Account" icon={User} collapsed={collapsed} variant="sub" />
            </div>
          </div>

          {insideProject && projectKey && (
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

              <div className="mt-4">
                {sectionLabel('Settings')}
                <div className="flex flex-col gap-1">
                  <NavItem to={`/p/${projectKey}/settings/api-keys`} label="API Keys" icon={KeySquare} collapsed={collapsed} variant="sub" />
                  <NavItem to={`/p/${projectKey}/settings/webhooks`} label="Webhooks" icon={Webhook} collapsed={collapsed} variant="sub" />
                  <NavItem to={`/p/${projectKey}/settings/integrations`} label="Integrations" icon={Plug} collapsed={collapsed} variant="sub" />
                  <NavItem to={`/p/${projectKey}/settings/audit`} label="Audit Log" icon={ScrollText} collapsed={collapsed} variant="sub" />
                  <NavItem to={`/p/${projectKey}/settings/labels`} label="Labels" icon={Tags} collapsed={collapsed} variant="sub" />
                  <NavItem to={`/p/${projectKey}/settings/versions`} label="Versions" icon={Milestone} collapsed={collapsed} variant="sub" />
                  <NavItem to={`/p/${projectKey}/settings/custom-fields`} label="Custom Fields" icon={SlidersHorizontal} collapsed={collapsed} variant="sub" />
                </div>
              </div>
            </div>
          )}
        </nav>

        <div className="flex flex-col gap-1 mt-auto">
          <OrgSwitcher />{/* collapsed-Prop wird in Task 4 nachgerüstet */}
          <div className={`flex items-center gap-2 ${collapsed ? 'flex-col' : ''}`}>
            <NotificationBell />
            <button
              onClick={logout}
              title={collapsed ? 'Logout' : undefined}
              className={`flex items-center gap-3 px-3 py-2 text-sm text-gray-400 hover:text-white ${collapsed ? 'justify-center' : 'flex-1 text-left'}`}
            >
              <LogOut size={18} className="shrink-0" />
              {!collapsed && 'Logout'}
            </button>
          </div>
          {!collapsed && <VersionTag className="px-3 pt-2" />}
        </div>
      </aside>
      <main className="flex-1 overflow-auto p-8">
        <Outlet />
        {projectKey && <IssueDialogHost projectKey={projectKey} />}
      </main>
    </div>
  )
}
```

Wichtig — Import von `LogOut` ergänzen: die Icon-Import-Liste oben enthält `LogOut` NICHT. Füge `LogOut` zur `lucide-react`-Import-Liste hinzu (in der ersten `import { ... } from 'lucide-react'`-Zeile), sonst schlägt der Typecheck fehl. Vollständige benötigte Icon-Liste: `LayoutDashboard, FolderKanban, Building2, ScrollText, Zap, Users, KeyRound, User, Kanban, ListChecks, CalendarRange, ListTodo, BarChart3, LifeBuoy, AlertTriangle, KeySquare, Webhook, Plug, Tags, Milestone, SlidersHorizontal, ChevronLeft, ChevronRight, LogOut`.

Anmerkungen:
- `<OrgSwitcher />` bleibt in diesem Task **ohne** Prop (wie im Code-Block gezeigt) — der eingeklappte OrgSwitcher wird erst in Task 4 nachgerüstet. So bleibt der Typecheck dieses Tasks grün, ohne dass `OrgSwitcher` schon eine `collapsed`-Prop akzeptieren muss.
- Alle o.g. Icon-Namen wurden gegen lucide-react 1.23.0 verifiziert (alle vorhanden).

- [ ] **Step 2: Typecheck**

Run:
```bash
cd frontend && npx tsc --noEmit
```
Expected: PASS (exit 0). Falls ein Icon-Name in lucide-react nicht existiert, meldet der Typecheck es — dann den betreffenden Icon-Import gegen einen existierenden lucide-react-Namen tauschen (z.B. `node -e "console.log(typeof require('lucide-react').Kanban)"` in `frontend/` prüfen).

- [ ] **Step 3: Manuelle Verifikation (Referenz — sorgfältig)**

Run:
```bash
cd frontend && npm run dev
```
Prüfe:
- Sidebar zeigt ausgeklappt Icon **+** Label; aktive Route hervorgehoben (Top-Level grau, Sub-Links indigo).
- Chevron-Button oben klappt ein/aus; eingeklappt nur Icons (`w-16`), zentriert, Sektions-Überschriften ausgeblendet.
- Eingeklappt: Hover über ein Icon zeigt den `title`-Tooltip mit dem Label.
- Zustand überlebt einen Reload (localStorage).
- Fensterbreite < 1024px erzwingt die Icon-Rail; der Chevron-Toggle verschwindet dort; oberhalb wird die gespeicherte Präferenz wiederhergestellt.
- Kontextuelle Project-Navigation erscheint weiterhin nur innerhalb eines Projekts.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/layouts/AppLayout.tsx
git commit -m "feat(frontend): collapsible sidebar (icon rail + tooltips + persistence)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 4: `OrgSwitcher` für den eingeklappten Modus

**Files:**
- Modify: `frontend/src/components/OrgSwitcher.tsx`
- Modify: `frontend/src/layouts/AppLayout.tsx` (nur die `<OrgSwitcher />`-Zeile → `collapsed`-Prop durchreichen)

**Interfaces:**
- Consumes: `collapsed: boolean` aus `AppLayout`.
- Produces: `OrgSwitcher` akzeptiert eine neue optionale Prop `collapsed?: boolean`.

- [ ] **Step 1: Aktuellen `OrgSwitcher` lesen**

Run:
```bash
cat frontend/src/components/OrgSwitcher.tsx
```
Erwartung: Verständnis der aktuellen Struktur (Dropdown/Button, das die aktive Organisation zeigt und Wechsel erlaubt).

- [ ] **Step 2: `collapsed`-Prop einführen**

Passe `frontend/src/components/OrgSwitcher.tsx` an:
- Signatur um optionale Prop erweitern: `export function OrgSwitcher({ collapsed = false }: { collapsed?: boolean } = {})`.
- Wenn `collapsed === true`: eine kompakte Darstellung rendern — ein zentriertes Icon/Kürzel-Button (z.B. `Building2`-Icon aus `lucide-react` oder das erste Zeichen des Org-Namens) mit `title={<orgName>}` als Tooltip; das volle Dropdown/den Namen ausblenden.
- Wenn `collapsed === false`: exakt die bisherige Darstellung beibehalten.
- Falls die kompakte Variante ein Icon nutzt: `import { Building2 } from 'lucide-react'`.

Da die konkrete OrgSwitcher-Struktur erst in Step 1 sichtbar wird, gilt: **minimal-invasiv** — nur ein `if (collapsed) return <kompakte Variante>` am Anfang des Renders ergänzen bzw. die Breite/Labels bedingt ausblenden, ohne die Wechsel-Logik (Query, Mutation, `switch-org`) zu verändern.

- [ ] **Step 3: `collapsed` in `AppLayout` durchreichen**

In `frontend/src/layouts/AppLayout.tsx` die Zeile
```tsx
<OrgSwitcher />
```
ersetzen durch
```tsx
<OrgSwitcher collapsed={collapsed} />
```

- [ ] **Step 4: Typecheck**

Run:
```bash
cd frontend && npx tsc --noEmit
```
Expected: PASS (exit 0).

- [ ] **Step 5: Manuelle Kurz-Verifikation**

Im Dev-Server: eingeklappte Sidebar zeigt einen kompakten OrgSwitcher (Icon/Kürzel + Tooltip), ausgeklappt unverändert; Org-Wechsel funktioniert in beiden Modi.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/OrgSwitcher.tsx frontend/src/layouts/AppLayout.tsx
git commit -m "feat(frontend): compact OrgSwitcher for collapsed sidebar

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Abschluss / Definition of Done

- [ ] `npx tsc --noEmit` grün.
- [ ] Sidebar klappt ein/aus, Zustand persistiert über Reload.
- [ ] Eingeklappt: nur Icons + `title`-Tooltips, Sektions-Überschriften ausgeblendet, Breite `w-16`.
- [ ] Auto-Collapse < 1024px (Icon-Rail, kein Overlay); Toggle dort ausgeblendet; oberhalb Präferenz wiederhergestellt.
- [ ] Aktive Route in beiden Modi hervorgehoben; Project-Navigation kontextuell.
- [ ] Backlog-Overview-Status für #8 nach Merge auf „Ausgeliefert" aktualisieren.

## Self-Review-Ergebnis (gegen Spec)

- **Spec-Abdeckung:** Zustands-Hook mit localStorage + `matchMedia`-Breakpoint (Task 1); `NavItem`-Refactor der doppelten Klassen-Helper (Task 2); Icons pro Nav-Item, breit/schmal-Modi, Chevron-Toggle, Sektions-Überschriften ausgeblendet, `title`-Tooltips, `transition-[width]`, Footer icon-tauglich (Task 3); OrgSwitcher eingeklappt (Task 4). ✅
- **Platzhalter:** keine — jeder Code-Step enthält vollständigen Code; OrgSwitcher-Anpassung ist bewusst nach dem Lesen der Ist-Struktur minimal-invasiv beschrieben (Ist-Datei erst in Step 1 sichtbar), mit klarer Nicht-Berührung der Wechsel-Logik.
- **Typ-Konsistenz:** `useSidebarCollapsed()`-Rückgabe (`collapsed`/`toggle`/`belowBreakpoint`) in Task 1 definiert, in Task 3 konsumiert; `NavItemProps` in Task 2 definiert, in Task 3 konsumiert; `OrgSwitcher`-`collapsed`-Prop in Task 4 definiert und dort durchgereicht.
- **Berührungspunkt:** `AppLayout` wird in Task 3 **und** Task 4 (nur die OrgSwitcher-Zeile) angefasst — Task 3 lässt `<OrgSwitcher />` ohne Prop, Task 4 rüstet die Prop nach; kein Konflikt.
