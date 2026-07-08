# Design: Einklappbare Sidebar — Backlog #8

> Status: Design abgestimmt (2026-07-08). Nächster Schritt: Implementierungsplan (writing-plans).

## Ziel

Die linke Sidebar (`layouts/AppLayout.tsx`) lässt sich zwischen zwei Zuständen
umschalten:

- **Ausgeklappt** (`w-56`, wie heute) — Icon **+** Label pro Nav-Eintrag.
- **Icon-Rail** (schmal, `w-16`) — nur Icons; Label als Tooltip beim Hover.

Der Zustand wird pro Browser in `localStorage` persistiert. Unterhalb eines
Breakpoints erzwingt die App die Icon-Rail (kein Off-Canvas-Overlay).

## Scope

**In Scope:**
- Collapse/Expand-Toggle für die Sidebar.
- Icon-Rail-Modus (nur Icons + Tooltips) inkl. der verschachtelten Sektionen
  (Admin, Account, kontextuelle Project-/Settings-Navigation).
- Icons pro Nav-Eintrag via `lucide-react` (bereits Dependency, bisher nirgends
  importiert).
- Persistenz in `localStorage`.
- Auto-Collapse unterhalb eines Breakpoints (→ Icon-Rail).
- Refactor der Nav-Einträge in eine wiederverwendbare `NavItem`-Komponente.

**Bewusst ausgeschlossen:**
- Off-Canvas-Overlay / Hamburger / echtes Mobile-Layout. Unterhalb des
  Breakpoints wird die Sidebar zur schmalen Icon-Rail, nicht ausgeblendet.
  (Vom Nutzer bestätigt.)
- Gestylte Tooltip-Komponente — es genügt das native `title`-Attribut.
  (Vom Nutzer bestätigt.)

## Design

### Zustands-Hook — `frontend/src/hooks/useSidebarCollapsed.ts`

- Hält `collapsed: boolean`, initialisiert aus
  `localStorage['sidebar-collapsed']` (Default: ausgeklappt / `false`).
- `toggle()` schreibt den neuen Wert zurück in `localStorage`.
- **Responsive:** ein `matchMedia('(max-width: 1024px)')`-Listener erzwingt
  unterhalb des Breakpoints `collapsed = true`. Oberhalb wird wieder die
  gespeicherte User-Präferenz respektiert.
- Der manuelle Toggle ist unterhalb des Breakpoints wirkungslos (bleibt Rail).

### `NavItem`-Komponente

Kapselt Icon + Label + aktiven Zustand und beide Modi. Ersetzt die aktuell
doppelten Inline-Definitionen `navLinkClass` / `subNavLinkClass`
(`layouts/AppLayout.tsx:10-14`) und entzerrt die inzwischen große `AppLayout`.

- Prop `collapsed` steuert das Rendering:
  - ausgeklappt: `Icon` + Label nebeneinander;
  - eingeklappt: nur `Icon`, `title={label}` als Tooltip.
- Basiert weiter auf `NavLink` (aktiver Zustand aus React-Router).

### `AppLayout`-Umbau

- Ein Icon je Nav-Eintrag (via `lucide-react`):
  - Top-Level: Dashboard, Projects, Organizations.
  - Admin: Audit Log, Automation, Users.
  - Account: Access Tokens, Account. (Siehe Anmerkung #3 unten.)
  - Project + Settings: Dashboard, Board, Backlog, Sprints, Issues, Reports,
    Automation, Service Desk, Incidents, API Keys, Webhooks, Integrations,
    Audit Log, Labels, Versions, Custom Fields.
- **Ausgeklappt:** Sektions-Überschriften (`Admin`/`Account`/`Project`/
  `Settings`) sichtbar wie heute, jetzt zusätzlich mit Icons vor den Labels.
- **Eingeklappt (Icon-Rail):** Sektions-Überschriften ausgeblendet, nur dünne
  Trenner (`border-t border-gray-800`) zwischen den Gruppen. Jedes `NavItem`
  zeigt nur das Icon, Tooltip via `title`.
- **Toggle:** Chevron-Button oben neben dem 🐺-Logo
  (`ChevronLeft` ↔ `ChevronRight` aus `lucide-react`). Im eingeklappten Modus
  ersetzt/verkleinert sich das Logo entsprechend.
- **Breite** animiert per `transition-[width]`.
- **Footer** (OrgSwitcher, NotificationBell, Logout, VersionTag,
  `layouts/AppLayout.tsx:123-132`) wird icon-tauglich: eingeklappt nur Icons.

## Berührungspunkt mit anderen Backlog-Punkten

- **#3 (Settings-Shell):** #3 ersetzt die zwei Account-Links (Tokens, Account)
  durch **einen** „Settings"-Eintrag. Reihenfolge der Umsetzung beachten: wer
  zuerst gemergt wird, bestimmt, ob hier zwei Einträge oder ein „Settings"-Icon
  stehen. Sauber rebasen; kein anderer geteilter Code außer `AppLayout`.
- **#7 (DataTable):** beide fassen Layout-Höhen an, teilen aber keinen Code außer
  `AppLayout`. Beim zweiten Merge auf sauberes Rebase achten.

## Testing

Frontend hat kein Test-Framework (nur `tsc`-Typecheck + manuell).

- **Typecheck** grün.
- **Manuell:**
  - Toggeln klappt ein/aus; Zustand überlebt einen Reload (localStorage).
  - Fensterbreite < 1024px erzwingt die Icon-Rail; manueller Toggle dort ohne
    Wirkung; oberhalb wird die gespeicherte Präferenz wiederhergestellt.
  - Tooltips (`title`) erscheinen im eingeklappten Modus.
  - Aktive Route bleibt in beiden Modi hervorgehoben.
  - Kontextuelle Project-Navigation erscheint weiterhin nur innerhalb eines
    Projekts und funktioniert in beiden Modi.
