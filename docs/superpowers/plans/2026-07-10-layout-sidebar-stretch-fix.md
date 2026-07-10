# Layout Sidebar-Stretch Fix (#11) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Die linke Sidebar (`AppLayout`) streckt sich nicht mehr mit langem Seiteninhalt; der Logout-Block bleibt immer sichtbar und langer Inhalt scrollt intern in `<main>`.

**Architecture:** Ein einziger CSS-Klassen-Change an der App-Shell in `frontend/src/layouts/AppLayout.tsx`: `min-h-screen` → `h-screen overflow-hidden`. `<main>` besitzt bereits `overflow-auto` und scrollt dadurch intern statt die ganze Seite (und damit den `<aside>`) zu strecken. Kein neues Modul, keine neue Komponente.

**Tech Stack:** React 18, React Router, TailwindCSS, Vite, TypeScript. Frontend hat **kein** Test-Framework — Verifikation über `tsc --noEmit` (Typecheck) + manuelle Browser-Prüfung.

## Global Constraints

- Nur `frontend/src/layouts/AppLayout.tsx` ändern (außer eine Seite zeigt in der manuellen Prüfung eine kollidierende eigene Höhen-/Scroll-Annahme → dann punktuell dort).
- Keine Doppel-Scrollbar auf irgendeiner Seite.
- Sidebar inkl. `mt-auto`-Logout-Block muss auf jeder Seite ohne Seiten-Scroll erreichbar sein.
- Kein Refactoring von `useSidebarCollapsed`, `NavItem`, Sidebar-Struktur (Out of Scope).
- Verifikation: `npx tsc --noEmit` grün + manuelle Prüfung der in Task 1 gelisteten Seiten.

---

### Task 1: Shell auf feste Viewport-Höhe umstellen

**Files:**
- Modify: `frontend/src/layouts/AppLayout.tsx:48`

**Interfaces:**
- Consumes: nichts (isolierter CSS-Change).
- Produces: nichts (kein exportiertes Symbol ändert sich; JSX-Struktur identisch, nur Klassen von `#app-root`).

- [ ] **Step 1: Klassen der App-Shell ändern**

In `frontend/src/layouts/AppLayout.tsx`, Zeile 48, das Wurzel-`<div>` von:

```tsx
<div id="app-root" className="min-h-screen bg-gray-950 text-white flex">
```

ändern zu:

```tsx
<div id="app-root" className="h-screen overflow-hidden bg-gray-950 text-white flex">
```

`<aside>` (Zeile 49) und `<main>` (Zeile 138, bereits `flex-1 overflow-auto p-8`) bleiben **unverändert**.

- [ ] **Step 2: Typecheck ausführen**

Run (aus `frontend/`): `npx tsc --noEmit`
Expected: keine Fehler (exit 0). Es ist nur eine Klassen-String-Änderung, daher darf sich am Typecheck nichts ändern — dieser Schritt stellt sicher, dass keine Tippfehler o.ä. eingebaut wurden.

- [ ] **Step 3: Dev-Server starten und lange Seite prüfen**

Run (aus `frontend/`): `npm run dev`
Dann im Browser einloggen und **Auditlog** (`/admin/audit`) öffnen — die Seite mit dem ursprünglichen Fehlerbild.
Expected:
- Der Inhalt scrollt **innerhalb** des rechten `<main>`-Bereichs.
- Die linke Sidebar bleibt fix in Viewport-Höhe; **Logout unten ist ohne Seiten-Scroll sichtbar**.
- Es gibt **keine** äußere Seiten-Scrollbar und keine Doppel-Scrollbar.

- [ ] **Step 4: Weitere Seiten manuell verifizieren**

Im laufenden Dev-Server jede dieser Seiten öffnen und dasselbe Verhalten prüfen (intern scrollend, Logout sichtbar, keine Doppel-Scrollbar):
- **Lange Formulare / Nicht-DataTable-Seiten:** Settings-/Profil-Seiten (`/settings`), Admin-Automation.
- **Board / Backlog / Issues** eines Projekts (`/p/<key>/board`, `/backlog`, `/issues`) — horizontales Scrollen (Board-Spalten) muss weiter funktionieren.
- **Kurze Seite:** Dashboard (`/`) — kein unnötiger Scroll, kein Layout-Bruch.
- **IssueDialogHost-Modal:** ein Issue per `?issue=`-Link/Klick öffnen — Modal öffnet und ist voll bedienbar.
- **Routenwechsel:** von einer langen zu einer kurzen Seite navigieren — kein hängender Scroll-Offset.
- **Schmaler Viewport:** Fenster unter den Sidebar-Breakpoint verkleinern (`useSidebarCollapsed` → `belowBreakpoint`) — Collapse-Verhalten unverändert, Logout erreichbar.

Falls **eine** Seite eine eigene, nun kollidierende Höhen-/Scroll-Annahme zeigt (z.B. `min-h-screen` auf Seitenebene, das eine Doppel-Scrollbar erzeugt): dort punktuell korrigieren und die Änderung im Commit-Body notieren. Andernfalls keine weiteren Dateien anfassen.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/layouts/AppLayout.tsx
git commit -m "fix(layout): pin sidebar to viewport height so logout stays reachable (#11)"
```

(Falls in Step 4 eine Seite mitkorrigiert wurde, diese Datei mit `git add` ergänzen und im Commit-Body erwähnen.)

---

## Self-Review (Plan ↔ Spec)

- **Spec-Abdeckung:** Soll-Verhalten (Shell fixe Höhe, Logout sichtbar, interner `<main>`-Scroll, keine Doppel-Scrollbar) → Task 1 Step 1 (Fix) + Steps 3–4 (Verifikation). Verworfene Alternative (Sticky-`<aside>`) ist korrekt nicht Teil des Plans. Test-Scope der Spec (Auditlog, lange Formulare, Board/Backlog, Modal, Routenwechsel, schmaler Viewport) → Task 1 Steps 3–4 vollständig abgebildet.
- **Placeholder-Scan:** keine TBD/TODO; der einzige bedingte Zweig (kollidierende Seite) ist konkret beschrieben.
- **Typ-Konsistenz:** keine neuen Typen/Signaturen; reiner CSS-Klassen-Change.
