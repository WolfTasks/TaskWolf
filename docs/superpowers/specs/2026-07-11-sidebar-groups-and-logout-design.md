# Spec: Sidebar-Gruppen einzeln zusammenklappbar (#10) + Logout immer erreichbar (B3)

> Design 2026-07-11. Backlog: `2026-07-07-backlog-overview.md` #10 + B3.
> Frontend-only. Domäne: linke Sidebar (`frontend/src/layouts/AppLayout.tsx`).

## Kontext & Problem

Die linke Sidebar (`AppLayout.tsx`) rendert ihre Sektionen inline über einen
`sectionLabel(text)`-Helfer (Gruppen: **Admin**, **Account**, **Project**,
**Settings**). Zwei zusammenhängende Verbesserungen an genau dieser Komponente:

- **#10 — Sektionsgruppen einzeln einklappbar:** Im *ausgeklappten* Zustand soll
  jede Gruppe einen eigenen Collapse-Toggle (Chevron am Header) bekommen, um
  vertikal Platz zu sparen. Aufbauend auf #8 (Sidebar-als-Ganzes-Collapse).
- **B3 — Logout verschwindet:** Wird der Sidebar-Inhalt höher als der Viewport
  (viele Einträge / kleiner Bildschirm), rutscht der unten gepinnte
  Logout-Button (`mt-auto`-Footer) unter die Clip-Grenze der `h-screen
  overflow-hidden`-Shell und ist nicht mehr erreichbar.

**Ursache B3:** `<nav>` ist `flex-1` mit Default-Overflow. Wenn die Nav-Items
höher werden als der verfügbare Platz, wächst der Inhalt über die Flex-Zeile
hinaus und wird von der `overflow-hidden`-Shell abgeschnitten — der Footer liegt
nach `<nav>`, also fällt er raus.

## Ziel / Soll-Verhalten

- **B3:** Header und Footer (OrgSwitcher, NotificationBell, Logout, VersionTag)
  bleiben fix; nur die **Nav-Liste** scrollt intern. Logout ist bei jeder
  Viewport-Höhe erreichbar. Keine Doppel-Scrollbar (die Shell bleibt
  `overflow-hidden`, `<main>` behält seinen eigenen `overflow-auto`).
- **#10:** Jede Sektionsgruppe (Admin, Account, Project, Settings) hat im
  ausgeklappten Zustand einen Chevron-Toggle am Header. Klick klappt die
  Item-Liste der Gruppe ein/aus. Zustand pro Gruppe in `localStorage`
  persistiert. Default: **alle offen**.

## Design

### B3 — interne Scrollbarkeit der Nav

Eine gezielte Änderung an `<nav>` in `AppLayout.tsx`:

```
<nav className="flex flex-col gap-1 flex-1 min-h-0 overflow-y-auto">
```

- `min-h-0` erlaubt dem Flex-Kind, unter seine Inhaltsgröße zu schrumpfen —
  Voraussetzung dafür, dass `overflow-y-auto` überhaupt scrollt.
- `overflow-y-auto` → die Nav-Liste scrollt intern, Footer bleibt gepinnt.
- Keine neue State-Logik, kein neuer Hook.

### #10 — `SidebarSection`-Komponente + Persistenz-Hook

**Refactor (Isolation):** Die wiederholten `sectionLabel(...) + <div
className="flex flex-col gap-1">`-Blöcke werden in eine kleine, fokussierte
Komponente extrahiert:

`frontend/src/components/nav/SidebarSection.tsx`

```
interface SidebarSectionProps {
  id: string          // stabiler Key für Persistenz, z.B. 'admin' | 'account' | 'project' | 'project-settings'
  label: string       // Header-Text
  railMode: boolean   // true = Sidebar als Ganzes eingeklappt (Icon-Rail)
  children: ReactNode  // die NavItems der Gruppe
}
```

Verhalten:
- **railMode (Icon-Rail):** kein Header, kein Chevron — `children` immer sichtbar
  (wie heute; `sectionLabel` liefert im eingeklappten Zustand ohnehin nichts).
- **ausgeklappt:** Header wird ein `<button>` mit Label + Chevron
  (`ChevronDown` = offen, `ChevronRight` = zu, aus `lucide-react`),
  `aria-expanded`. Klick toggelt. `children` nur gerendert, wenn Gruppe offen.

**Persistenz-Hook:** `frontend/src/hooks/useSidebarSections.ts`

```
export function useSidebarSections(): {
  isOpen: (id: string) => boolean   // absent → true (default offen)
  toggle: (id: string) => void
}
```

- Ein `localStorage`-Key `sidebar-sections-collapsed`, Wert
  `Record<string, boolean>` (`true` = eingeklappt). Absent/kein Eintrag = offen.
- Analog zum bestehenden `useSidebarCollapsed`-Muster (#8).

**Einbindung in `AppLayout.tsx`:** Die vier Gruppen werden auf `SidebarSection`
umgestellt. `railMode` = das bestehende `collapsed` aus `useSidebarCollapsed`.
Top-Level-Einträge (Dashboard / Projects / Organizations) bleiben **ohne** Header
und damit nicht einklappbar (primäre Navigation, immer sichtbar).

## Abgrenzung (YAGNI / Out of Scope)

- Top-Level-Navigation (Dashboard/Projects/Organizations) bekommt **keinen**
  Collapse-Toggle.
- Kein „alle ein-/ausklappen“-Sammelschalter.
- Keine Animation über das hinaus, was Tailwind-Defaults hergeben.
- Verhalten im Icon-Rail-Modus bleibt exakt wie heute.

## Testing / Verifikation

Frontend hat **kein** Test-Framework (nur `tsc`-Typecheck + manuell, vgl. #5).

- `npm run build` / `tsc` grün (Typecheck).
- Manuelle Browser-Verifikation (Wolfgang):
  - In einem Projekt mit vielen Sektionen bei niedriger Viewport-Höhe:
    Nav scrollt intern, **Logout bleibt sichtbar** (B3).
  - Jede Gruppe (Admin/Account/Project/Settings) ein-/ausklappen (#10).
  - Reload → Zustand pro Gruppe bleibt erhalten (Persistenz).
  - Sidebar als Ganzes einklappen (Icon-Rail): Gruppen immer sichtbar, keine
    Chevrons, keine Regression.
  - Keine Doppel-Scrollbar zwischen `<aside>` und `<main>`.
