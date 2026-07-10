# Design: Layout-Fix — linke Sidebar streckt sich nicht mehr (Backlog #11)

- **Datum:** 2026-07-10
- **Backlog-Item:** #11 (UI/Bug)
- **Typ:** Kleiner, sorgfältig zu testender Frontend-UI-Fix
- **Betroffene Datei:** `frontend/src/layouts/AppLayout.tsx`

## Problem

`#app-root` ist `min-h-screen bg-gray-950 text-white flex` (`AppLayout.tsx:48`).
Die App-Shell ist eine horizontale Flex-Zeile aus `<aside>` (Sidebar) und
`<main>` (Seiteninhalt). Wird `<main>` höher als der Viewport (lange Seiten wie
Auditlog, lange Formulare), wächst die Flex-Zeile über die Viewport-Höhe hinaus.
Weil Flex-Items standardmäßig `align-items: stretch` haben, **streckt sich der
`<aside>` mit** — die Sidebar wird so hoch wie der gesamte Seiteninhalt.

Folge: Der unten via `mt-auto` gepinnte Block (OrgSwitcher, NotificationBell,
**Logout**, VersionTag; `AppLayout.tsx:122`) rutscht unter den sichtbaren
Viewport-Bereich. Um den Logout-Button zu erreichen, muss man die **gesamte
Seite** scrollen.

`<main>` besitzt bereits `overflow-auto` (`AppLayout.tsx:138`), scrollt aber
heute nicht intern, weil die äußere Shell mitwächst statt die Höhe zu begrenzen.

## Soll-Verhalten

- Die App-Shell hat **feste Viewport-Höhe**; sie wächst nie über den Viewport
  hinaus.
- Der `<aside>` bleibt exakt viewport-hoch; der `mt-auto`-Block (inkl. Logout)
  ist **immer** ohne Scrollen sichtbar.
- Langer Seiteninhalt scrollt **intern in `<main>`**, nicht über die ganze Seite.
- Keine Doppel-Scrollbar (weder eine äußere Seiten-Scrollbar zusätzlich zur
  `<main>`-Scrollbar, noch verschachtelte Scroll-Container).

## Lösung

Ein zentraler Change in `AppLayout.tsx`:

- `#app-root`: `min-h-screen` → `h-screen overflow-hidden` (bleibt `flex`, Farben
  unverändert).
- `<main>`: bleibt `flex-1 overflow-auto p-8` (unverändert).
- `<aside>`: unverändert (`flex flex-col`, Logout-Block via `mt-auto`).

```
<div id="app-root" class="h-screen overflow-hidden bg-gray-950 text-white flex">
  <aside class="... flex flex-col ...">   // = h-screen, mt-auto-Block immer sichtbar
    ...
  </aside>
  <main class="flex-1 overflow-auto p-8">  // scrollt intern
    <Outlet />
  </main>
</div>
```

**Wirkung:** `h-screen` fixiert die Shell auf Viewport-Höhe, `overflow-hidden`
verhindert das Wachsen/äußere Scrollen der Shell. Beide Flex-Kinder erben damit
die feste Höhe; `<aside>` kann sich nicht mehr strecken, `<main>` scrollt intern
über sein bereits vorhandenes `overflow-auto`.

Dieser Ansatz ist konsistent mit dem `overflow-hidden`-Muster, das mit
#7/DataTable eingeführt wurde.

### Verworfene Alternative

Sticky-`<aside>` (`min-h-screen` behalten, `<aside>` auf `sticky top-0 h-screen
self-start`): weniger invasiv, lässt aber den äußeren Seiten-Scroll bestehen →
Risiko einer Doppel-Scrollbar mit den internen Scroll-Containern aus #7. Verworfen
zugunsten der einheitlichen „Shell fix, `<main>` scrollt"-Struktur.

## Testplan (das eigentliche Risiko)

Das Frontend hat **kein** Test-Framework (nur `tsc`-Typecheck + manuell). Daher
sorgfältige manuelle Verifikation aller langen Seiten:

1. `npm run typecheck` (bzw. Projekt-Äquivalent) grün.
2. Manuell prüfen, dass Inhalt **innerhalb `<main>`** scrollt und die Sidebar
   inkl. Logout stets sichtbar bleibt, auf:
   - **Auditlog** (`/admin/audit`) — der ursprüngliche Auslöser.
   - **Lange Formulare / Nicht-DataTable-Seiten** (z.B. Settings-/Profil-Seiten
     aus #3), die #7/DataTable noch nicht nutzen.
   - **Board / Backlog / Issues** (breite + lange Inhalte, horizontales Scrollen
     darf weiterhin funktionieren).
   - Kurze Seiten (Dashboard) — kein unnötiger Scroll, kein Layout-Bruch.
3. **Keine Doppel-Scrollbar** auf irgendeiner Seite.
4. **`IssueDialogHost`-Modal** (in `<main>` gerendert, `AppLayout.tsx:140`):
   Modal öffnet korrekt und ist voll bedienbar (fixed-positioniert → sollte
   unkritisch sein, dennoch verifizieren).
5. **Routenwechsel:** Scroll-Position verhält sich sinnvoll (kein „hängender"
   Scroll-Offset auf der neuen Seite).
6. **Schmaler Viewport / `belowBreakpoint`** (`useSidebarCollapsed`): Sidebar-
   Collapse-Verhalten unverändert, Logout erreichbar.

## Out of Scope

- Keine Änderung an `useSidebarCollapsed`, `NavItem`, Sidebar-Struktur.
- Keine neue Scroll-Container-Komponente (das ist #7-Territorium).
- Keine Änderung an den einzelnen Seiten außer, falls in Schritt 2 eine Seite
  eine eigene (nun redundante/kollidierende) Höhen-/Scroll-Annahme zeigt — dann
  punktuell dort korrigieren und dokumentieren.
```
