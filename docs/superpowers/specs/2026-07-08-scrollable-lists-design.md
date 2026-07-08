# Design: Scrollbare Listen (`DataTable`) — Backlog #7

> Status: Design abgestimmt (2026-07-08). Nächster Schritt: Implementierungsplan (writing-plans).

## Ziel

Lange Tabellen scrollen **innerhalb** eines begrenzten Containers, der die
verbleibende Viewport-Höhe füllt; die Seite selbst wächst nicht mehr mit der
Zeilenzahl. Der Tabellenkopf bleibt beim Scrollen sichtbar (sticky). Eine
geteilte, virtualisierte, spalten-config-basierte Komponente sorgt für
Konsistenz über alle langen Tabellen hinweg.

## Scope

**In Scope:**
- Neue wiederverwendbare Komponente `DataTable` (div-basiert, CSS-Grid,
  virtualisiert, sticky Header).
- Migration **aller** bestehenden `<table>`-Listen auf `DataTable`:
  1. `AuditLogPage` (`pages/admin/AuditLogPage.tsx`) — Referenz-Umsetzung
     inkl. sticky Filterleiste
  2. `AdminUsersPage` (`pages/admin/AdminUsersPage.tsx`)
  3. `AccessTokensPage` (`pages/settings/AccessTokensPage.tsx`)
  4. `ApiKeysPage` (`pages/settings/ApiKeysPage.tsx`)
  5. `ProjectAuditPage` (`pages/projects/settings/ProjectAuditPage.tsx`)
- Neue Dependency `@tanstack/react-virtual`.

**Bewusst ausgeschlossen (YAGNI):**
- Comment-/Activity-Feeds (`components/comments/ActivityFeed.tsx`,
  `CommentThread.tsx`) — nicht-tabellarisch, haben bereits eigene
  „Load-more"-Pagination. Nicht Teil dieses Zyklus. (Vom Nutzer bestätigt.)
- Sortierbare Spalten, Spalten-Resize, Auswahl-Checkboxen.

## Komponenten-Design

### `frontend/src/components/table/DataTable.tsx`

```tsx
interface Column<T> {
  key: string
  header: ReactNode
  cell: (row: T) => ReactNode
  width?: string                        // CSS-Grid-Track, z.B. '1fr' | '120px'
  align?: 'left' | 'right' | 'center'
}

interface DataTableProps<T> {
  columns: Column<T>[]
  rows: T[]
  rowKey: (row: T) => string | number
  empty?: ReactNode                     // Leerzustand, Default z.B. "Keine Einträge"
  estimateRowHeight?: number            // Default 44
}
```

**Layout**
- Zeilen sind `div`s, nicht `<tr>`. Header-Zeile und Body-Zeilen teilen dieselbe
  `grid-template-columns`-Definition (abgeleitet aus `columns[].width`, Default
  `1fr` pro Spalte) → Spaltenbreiten bleiben synchron.
- Sticky Header via `position: sticky; top: 0` innerhalb des Scroll-Containers.
- Styling im bestehenden Dark-Theme-Stil der aktuellen Tabellen
  (`text-sm`, `border-b border-gray-800`, `hover:bg-gray-800/40`).

**Höhe**
- Der Scroll-Container ist ein Flex-Child mit `flex-1 min-h-0 overflow-auto`.
- Voraussetzung: die Seite ist ein `flex flex-col`-Layout mit begrenzter Höhe,
  sodass Seiten-Header/Filter oben fix bleiben und nur die Tabelle scrollt.

**Virtualisierung**
- `@tanstack/react-virtual` (`useVirtualizer`) über den Scroll-Container.
- Nur sichtbare Zeilen (+ Overscan) werden gerendert. Gesamthöhe über einen
  Spacer, Zeilen per `transform: translateY(...)` positioniert.
- Dynamische Zeilenhöhen via `measureElement` (Zeilen können mehrzeilige Zellen
  enthalten, z.B. Audit-Resource).

## Seiten-Höhen-Voraussetzung

Damit `flex-1 min-h-0` greift, muss der Seiten-Root eine begrenzte Höhe haben.

- `AppLayout`s `<main>` ist bereits `flex-1 overflow-auto p-8` (`layouts/AppLayout.tsx:134`).
- Für `DataTable`-Seiten wird der Seiteninhalt auf ein
  `flex flex-col min-h-0 h-full`-Muster umgestellt: Seiten-Header + Filter oben
  fix, darunter die `DataTable` als `flex-1`-Bereich.
- Achtung `overflow`: das `overflow-auto` des `<main>` darf den internen Scroll
  nicht doppeln. Betroffene Seiten setzen ihren Wrapper auf volle Höhe; falls
  nötig wird das äußere `overflow` für diese Seiten neutralisiert. Beim
  Referenz-Umbau (`AuditLogPage`) verifizieren, dass **kein** doppelter Scrollbar
  und **kein** horizontaler Seiten-Scroll entsteht.

## Migration je Seite

Jede Seite: `<table>`/`<thead>`/`<tbody>`-Markup entfernen, `columns`-Config
definieren (eine `Column` pro bisheriger Spalte, `cell` übernimmt das bisherige
`<td>`-Rendering inkl. Badges/Buttons), Seiten-Wrapper auf Flex-Höhe umstellen.
Verhalten (Filter, Export, Aktions-Buttons) bleibt unverändert.

## Testing

Frontend hat kein Test-Framework (nur `tsc`-Typecheck + manuell).

- **Typecheck** (`tsc`) muss grün sein (generische `DataTable<T>` sauber typisiert).
- **Manuelle Verifikation** (mind. Audit-Log mit vielen Zeilen):
  - Liste scrollt intern, Seite scrollt nicht vertikal mit.
  - Header bleibt beim Scrollen sichtbar (sticky).
  - Spalten von Header und Zeilen sind ausgerichtet.
  - Kein horizontaler Seiten-Scroll.
  - Filter/Export/Aktionen funktionieren wie zuvor.
  - Container füllt die Viewport-Resthöhe (großer + kleiner Screen).

## Offene Risiken

- Sticky Header + CSS-Grid + Virtualisierung zusammen erfordern Sorgfalt bei der
  Höhenmessung; `AuditLogPage` zuerst vollständig sauber bekommen, dann die
  übrigen vier Seiten nach demselben Muster.
- `@tanstack/react-virtual`-Version passend zur vorhandenen React-Version wählen
  (gleiche Familie wie das bereits genutzte `@tanstack/react-query`).
