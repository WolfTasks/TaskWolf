# React Router v7 → v8 Migration — Design / Spec

**Datum:** 2026-07-29
**Status:** Design approved — bereit für Planerstellung
**Auslöser:** `GHSA-qwww-vcr4-c8h2` (react-router, HIGH, CVSS 7.1) — aktuell an drei
Gates als *nicht ausnutzbar* suppressed (siehe [Interim-Zustand](#interim-zustand)).
Diese Migration löst die Ausnahme echt auf und nutzt zugleich den einzigen im
Library-Mode verfügbaren v8-Gewinn (Route-Code-Splitting).

Ergänzt den Backlog-Plan `docs/superpowers/plans/2026-07-29-react-router-v8-migration.md`.

---

## 1. Ziel & Nicht-Ziele

**Ziel**
- react-router von 7.18.1 auf **8.3.0** heben → `GHSA-qwww-vcr4-c8h2` echt beheben.
- Route-basiertes **Lazy-Loading / Code-Splitting** einführen (kleineres Initial-Bundle).
- Alle drei Interim-Ausnahmen wieder entfernen.

**Nicht-Ziele (YAGNI)**
- **Kein** Framework-Mode. Wir bleiben Data-/Library-Mode (`createBrowserRouter` +
  `RouterProvider`). Damit greifen die v8-Framework-Features (`v8_splitRouteModules`,
  `v8_viteEnvironmentApi`, Vite-Environment-API, Cloudflare-Plugin) **nicht** und sind
  ausdrücklich raus.
- **Kein** Middleware/Loader-Umbau. Auth bleibt der bestehende `RequireAuth`-Wrapper —
  Middleware bringt ohne Loaders/Actions keinen Mehrwert.
- **Keine** unrelated Refactorings.

## 2. Kontext (verifiziert 2026-07-29, main)

- **42 Dateien** unter `frontend/src/**` importieren aus `react-router-dom`.
- Genutzte Symbole (alle in v8 vorhanden, keine dom-exklusiven / Data-Router-APIs):
  `Link, NavLink, Navigate, Outlet, RouterProvider, createBrowserRouter, useMatch,
  useNavigate, useParams, useSearchParams`.
- **Keine** Data-Router-/Form-APIs im Einsatz (kein `useLoaderData`/`useActionData`/
  `useSubmit`/`useFetcher`/`<Form>`/`loader:`/`action:`/`useMatches`/`ScrollRestoration`)
  → das v8-Rename `data → loaderData` betrifft uns nicht.
- `useMatch` in 2 Dateien mit Pfad-Pattern-Strings (`AppLayout.tsx`, `IssueDialogHost.tsx`)
  — in v8 unverändert.
- **Kein** separates `@types/react-router-dom` (v7 liefert eigene Types) → nichts extra zu entfernen.
- `frontend/src/main.tsx`: `StrictMode > QueryClientProvider > RouterProvider` — der
  einzige `RouterProvider`-Import.
- `frontend/src/app/router.tsx`: `createBrowserRouter` mit ~40 **statisch** importierten
  Leaf-Pages + 3 Layout-Shells (`AuthLayout`, `AppLayout`, `SettingsLayout`); `RequireAuth`
  umschließt `AppLayout` inline (`element: <RequireAuth><AppLayout/></RequireAuth>`).
- **Keine** existierende Spinner-/Loading-Komponente, **kein** `Suspense` im Code.
- 4 Pages sind **default**-Exports (`AuditLogPage`, `ProjectAuditPage`, `ServiceDeskPage`,
  `IncidentDashboardPage`); der Rest named.

## 3. Voraussetzungen / Blocker

- **React ≥ 19.2.7** — wir sind exakt `19.2.7` ✅ (peerDep von react-router@8.3.0:
  `react >=19.2.7`, `react-dom >=19.2.7`).
- **Node ≥ 22.22** — ⚠️ **BLOCKER:** `.github/workflows/ci.yml` `setup-node` läuft auf
  **Node 20**. Muss **im selben PR wie der Paket-Bump** auf **22** (LTS, erfüllt ≥22.22)
  gehoben werden, sonst bricht `vite build` (v8-Engines). Frontend-Dockerfile nutzt bereits
  `node:26-alpine` ✅. (Lektion aus TS7-Bump PR #81: Toolchain-Anforderung muss mit dem
  Bump landen.)
- **Vite ≥ 7** — wir sind `^8.1.4` ✅ (nur Framework-Mode-relevant).

## 4. Architektur & Sequenzierung

Zwei logische Schritte, **zwei getrennte PRs**, beide primär in `router.tsx`,
im selben Worktree nacheinander:

### Schritt A — Security-Migration (PR 1, Pflicht)

Behebt die CVE echt und ist unabhängig mergebar/releasebar.

1. `package.json`: `react-router-dom` entfernen, `"react-router": "^8.3.0"` hinzufügen.
2. `npm install` → `package-lock.json` neu; verifizieren, dass `react-router-dom`
   **vollständig** aus dem Lockfile verschwindet.
3. Import-Umstellung in 42 Dateien (scripted → Review): `from 'react-router-dom'`
   → `from 'react-router'`.
4. **Sonderfall** `main.tsx`: `RouterProvider` → `from 'react-router/dom'` (NICHT
   `react-router`).
5. `ci.yml`: `node-version: '20'` → `'22'`.
6. Interim-Ausnahmen entfernen:
   - `.trivyignore`: `GHSA-qwww-vcr4-c8h2`-Zeile raus.
   - `.github/scripts/audit-gate.mjs`: ALLOWLIST-Eintrag raus (leere ALLOWLIST ok, Gate
     bleibt scharf — Negativ-Test bleibt gültig).
   - Dependabot-Alert #82: löst sich mit dem Upgrade automatisch auf.

**Verifikation A:** `npm run typecheck` + `npm run build` grün; PR-CI (Trivy-Gate +
`audit-gate.mjs`) grün **ohne** jede Ausnahme = CVE nachweislich behoben; `npm audit`
zeigt 0 HIGH/CRITICAL.

### Schritt B — Lazy-Route-Code-Splitting (PR 2, Enhancement)

Setzt auf dem gemergten Schritt A auf.

1. **`React.lazy` + top-level `<Suspense>`** (nicht RR-native Route-`lazy`). Begründung:
   RR-native `lazy` koppelt seinen Initial-Load-Fallback an `HydrateFallback`, das an
   Hydration/Loaders hängt und in reinen CSR-SPAs **ohne** Loader bekanntermaßen quirky
   ist (remix-run/react-router#12699 „HydrateFallback rendered intermittently on refresh
   in SPA without SSR"). Wir haben keine Loader und kein SSR → identisches Code-Splitting,
   aber glasklare Fallback-Story. Helper kapselt named/default-Export:
   ```ts
   import { lazy } from 'react'
   const lazyPage = (imp: () => Promise<any>, name: string) =>
     lazy(() => imp().then(m => ({ default: m[name] })))
   // named:   const BoardPage = lazyPage(() => import('@/pages/board/BoardPage'), 'BoardPage')
   // default: const AuditLogPage = lazyPage(() => import('@/pages/admin/AuditLogPage'), 'default')
   // Route bleibt element-basiert: { path:'/p/:key/board', element: <BoardPage/> }
   ```
2. Nur die **~40 Leaf-Pages** lazy laden. Die 3 Layout-Shells + `RequireAuth` bleiben
   **eager** (First-Paint immer nötig, Auth-Wrapper unangetastet).
3. **`RouteFallback`**-Komponente: minimaler zentrierter Spinner (Tailwind `animate-spin`).
   `<Suspense fallback={<RouteFallback/>}>` wird **um den `<Outlet/>` in jedem der 3 Layouts**
   gelegt (nicht top-level um `RouterProvider`) — so bleibt die Layout-Shell (Sidebar/
   Settings-Nav) beim Lazy-Load stehen und nur der Content-Bereich zeigt den Fallback,
   sowohl beim Initial-Load als auch bei Navigation. Da alle Routen unter `AuthLayout` oder
   `AppLayout` (mit ggf. verschachteltem `SettingsLayout`) hängen, deckt das jede Leaf-Page ab.

**Verifikation B:** typecheck+build grün; `dist/`-Ausgabe zeigt **separate Page-Chunks**
(Beleg fürs Splitting, statt eines Monolith-Bundles); manueller DE/EN-Smoke inkl.
Deep-Link auf lazy Routen.

## 5. Verifikation & Risiko

- **Kein Frontend-Test-Framework** → Gate = Typecheck + Build + **manueller DE/EN-Browser-Smoke**:
  Login → Navigation (Sidebar/`useMatch`) → Board → Issue-Dialog (`?issue=`,
  `useSearchParams`) → Settings-Tabs (`Outlet`) → Deep-Link-Routen (`useParams`).
- **Risiko:** Major-Bump trotz identischer API-Namen → gezielt auf relatives Routing,
  `<Navigate replace>` und `useSearchParams`-Defaults achten.
- **Reihenfolge kritisch:** Node-Bump (A.5) muss mit dem Paket-Bump (A.1) im selben PR
  landen (TS7-Lektion).
- **Worktree** (Standard-Workflow); Schritt A vollständig grün & gemergt, dann Schritt B.
- **Release:** eigene v1.0.x, shippt den echten CVE-Fix → Wolfgangs Release-Prozess (owed).

## <a id="interim-zustand"></a>6. Interim-Zustand (was diese Migration ablöst)

Seit 2026-07-29 (PR #103, gemergt `c6832ef`) ist `GHSA-qwww-vcr4-c8h2` an drei Gates als
*nicht ausnutzbar* suppressed (Frontend ist reine Client-SPA, `createBrowserRouter`, kein
RSC/SSR → verwundbarer RSC-Pfad ungenutzt):
1. `.trivyignore` (Trivy-Nightly + ci.yml-Spiegel),
2. `.github/scripts/audit-gate.mjs` ALLOWLIST (npm-audit-Gate),
3. Dependabot-Alert #82 dismissed `not_used`.

Nach dieser Migration werden (1) und (2) entfernt; (3) wird gegenstandslos.
