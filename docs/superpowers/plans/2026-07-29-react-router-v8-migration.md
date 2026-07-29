# React Router v7 → v8 Migration (Backlog)

**Status:** Backlog / geplant — noch NICHT umgesetzt
**Erstellt:** 2026-07-29
**Auslöser:** Security-Advisory `GHSA-qwww-vcr4-c8h2` (react-router, HIGH, CVSS 7.1)
**Aktueller Interim-Zustand:** CVE als *nicht ausnutzbar* suppressed
(`.trivyignore` + Dependabot-Alert #82 dismissed `not_used`, 2026-07-29). Dieser Plan
löst die Ausnahme sauber auf.

## Kontext / Warum

`GHSA-qwww-vcr4-c8h2` ist eine CSRF-Lücke (CWE-352) **ausschließlich im unstable
RSC-Modus** von React Router. Betroffene Range: `react-router >= 7.12.0 < 8.3.0`,
Fix nur in **8.3.0**. Kein v7-Patch existiert.

TaskWolf-Frontend ist eine **reine Client-SPA** (`createBrowserRouter` +
`RouterProvider`, Data-/Library-Mode, kein SSR/RSC, keine `unstable_`-APIs) →
der verwundbare Code-Pfad wird nicht genutzt, die CVE ist bei uns **nicht
ausnutzbar**. Deshalb ist die Migration eine geplante Aufräum-Aufgabe, keine
Notfall-Remediation.

`react-router-dom` hat **kein v8** (letzte Version 7.18.2, ebenfalls verwundbar):
das Paket wurde in v8 aufgelöst und in `react-router` zusammengeführt. Die Migration
ist damit unvermeidlich ein Major-Bump mit Import-Umstellung in 42 Dateien.

## Umfang (Ist-Stand main, verifiziert 2026-07-29)

- **42 Dateien** importieren aus `react-router-dom` (`frontend/src/**`).
- Genutzte Symbole (alle existieren in v8, keine dom-exklusiven APIs):
  `Link, NavLink, Navigate, Outlet, RouterProvider, createBrowserRouter,
  useMatch, useNavigate, useParams, useSearchParams`.
- Keine Loader/Actions/`meta`/`useMatches` → das v8-Rename `data → loaderData`
  betrifft uns **nicht**.
- Kein Framework-Mode → v8-Future-Flags (`v8_middleware`, `v8_viteEnvironmentApi`,
  Vite-Environment-API, Cloudflare-Plugin-Wechsel) betreffen uns **nicht**.

## v8-Breaking-Changes, die uns betreffen

1. **Paket entfernt:** `npm uninstall react-router-dom`, `react-router@^8.3.0` rein.
2. **Import-Umstellung** in allen 42 Dateien:
   - `Link, NavLink, Navigate, Outlet, createBrowserRouter, useMatch,
     useNavigate, useParams, useSearchParams` → `from 'react-router'`
   - ⚠️ **`RouterProvider` → `from 'react-router/dom'`** (Sonderfall! NICHT
     `react-router`). Betrifft nur `frontend/src/main.tsx:4`.

## Voraussetzungen / Blocker (WICHTIG)

- **React ≥ 19.2.7** — wir sind exakt bei `19.2.7` ✅ (peerDep von react-router@8.3.0
  ist `react >=19.2.7`, `react-dom >=19.2.7`).
- **Node ≥ 22.22** — ⚠️ **BLOCKER:** `.github/workflows/ci.yml` `setup-node`
  läuft aktuell auf **Node 20**. Muss im selben PR auf **≥ 22.22** (empf. 24) gehoben
  werden, sonst bricht `vite build` unter der v8-Engines-Anforderung. Frontend-Dockerfile
  nutzt bereits `node:26-alpine` ✅.
- **Vite ≥ 7** — wir sind bei `^8.1.4` ✅ (nur für Framework-Mode relevant, wir nicht).

## Aufgabenliste

1. **Worktree** anlegen (Wolfgangs Standard für Implementierungs-Sessions).
2. `frontend/package.json`: `react-router-dom` raus, `"react-router": "^8.3.0"` rein.
3. Import-Umstellung 42 Dateien (scripted sed für 9 Symbole → `react-router`;
   `main.tsx` `RouterProvider` → `react-router/dom` manuell/separat).
4. `.github/workflows/ci.yml`: `node-version: '20'` → `'22'` (oder `'24'`).
5. `npm install` → `package-lock.json` aktualisieren; prüfen, dass `react-router-dom`
   komplett aus dem Lockfile verschwindet.
6. `.trivyignore`: `GHSA-qwww-vcr4-c8h2`-Zeile **entfernen** (CVE dann echt behoben).
7. **Verifikation:**
   - `npm run typecheck` grün (Frontend hat kein Test-Framework → Typecheck ist das Gate).
   - `npm run build` grün.
   - Manueller Browser-Smoke DE/EN: Login → Navigation → Board → Issue-Dialog
     (`?issue=`) → Settings-Tabs → Deep-Link-Routen (`useParams`/`useSearchParams`).
   - PR-CI: Trivy-Gate grün OHNE die trivyignore-Ausnahme.
8. **Dependabot ignore lockern:** falls in `.github/dependabot.yml` ein react-router-
   Ignore ergänzt wurde, wieder entfernen (aktuell nur `typescript` semver-major ignored).
9. **Release:** In einer v1.0.x-Version ausliefern; Memory + CHANGELOG aktualisieren.

## Risiko / Hinweise

- Frontend hat **kein** Test-Framework (Typecheck + manuell) → sorgfältiger
  manueller Smoke ist Pflicht.
- Major-Bump: trotz identischer API-Namen auf subtile Verhaltensänderungen bei
  relativem Routing / `Navigate replace` / `useSearchParams`-Defaults achten.
- Reihenfolge im PR beachten: Node-Bump (Schritt 4) muss zusammen mit dem
  Paket-Bump landen, sonst CI-Rotlauf (vgl. TS7-Bump-Lektion, PR #81).
