# Phase 5: Dashboards & Reports — Design Spec

**Date:** 2026-06-20
**Status:** Approved
**Depends on:** Phase 1 (Core Foundation), Phase 2 (Agile Boards)

---

## Ziel

Ein konfigurierbares, projekt-weites Dashboard mit Drag-and-Drop Widget-Canvas (analog zu Jira Dashboards). Admins können Widgets hinzufügen, verschieben und in der Größe ändern. Mitglieder sehen das Dashboard read-only. Neue Report-Typen: Cycle Time. Bestehende Burndown- und Velocity-Charts werden als Dashboard-Widgets wiederverwendet.

---

## Entscheidungen

| Frage | Entscheidung | Begründung |
|---|---|---|
| Dashboard-Scope | Pro Projekt, Admin konfiguriert | Einfacheres Berechtigungsmodell, kein User-spezifischer State |
| Widget-Canvas | Free-form Drag+Resize (`react-grid-layout`) | `@dnd-kit` eignet sich für Node-Verbindungen (Phase 4), nicht für resize-fähige Widget-Grids |
| Cycle Time Tracking | Bestehende `issue_activities` Tabelle nutzen | `STATUS_CHANGED` wird bereits geloggt mit `oldValue`/`newValue` und Timestamp — kein Schema-Change nötig |
| Widget-Config | Freeform JSON-Spalte pro Widget | Kein separates Config-Schema; jeder Widget-Typ definiert sein eigenes JSON-Shape |
| Layout speichern | Einzel-PUT mit vollem Layout-Array | Entspricht `react-grid-layout`'s `onLayoutChange`-Callback, vermeidet N PATCH-Calls |
| Reports-Seite | Bleibt bestehen unter `/p/:key/reports` | Full-page Charts bleiben; Dashboard-Widgets sind kleinere Inline-Versionen |

---

## Datenmodell

Zwei neue Tabellen im `reports`-Modul.

### `dashboard`

```sql
id          UUID        PRIMARY KEY
project_id  UUID        NOT NULL UNIQUE REFERENCES projects(id)
created_at  TIMESTAMP   NOT NULL
updated_at  TIMESTAMP   NOT NULL
```

Ein Dashboard pro Projekt. Wird beim ersten `GET /dashboard`-Request auto-angelegt (keine explizite Create-API).

### `dashboard_widget`

```sql
id            UUID        PRIMARY KEY
dashboard_id  UUID        NOT NULL REFERENCES dashboard(id)
type          VARCHAR(40) NOT NULL
config        TEXT                     -- JSON, widget-spezifisch
grid_x        INT         NOT NULL
grid_y        INT         NOT NULL
grid_w        INT         NOT NULL
grid_h        INT         NOT NULL
created_at    TIMESTAMP   NOT NULL
updated_at    TIMESTAMP   NOT NULL
```

**Widget-Typen (`type`):**

| Type | Config-Shape | Beschreibung |
|---|---|---|
| `BURNDOWN` | `{ "sprintId": "uuid" }` | Burndown-Chart für einen Sprint |
| `VELOCITY` | `{}` | Velocity Bar Chart über alle abgeschlossenen Sprints |
| `CYCLE_TIME` | `{ "sprintId": "uuid" }` | Avg. Cycle Time pro Sprint |
| `ISSUE_COUNT` | `{}` | Stat Card: Anzahl offener Issues |
| `ISSUES_BY_STATUS` | `{}` | Stat Cards: Anzahl pro Status-Kategorie |
| `ISSUE_LIST` | `{ "filter": "MY_OPEN" \| "RECENTLY_UPDATED" \| "OVERDUE" }` | Konfigurierbares Issue-List Widget |

**Cycle Time:** Keine Schema-Änderungen nötig. `issue_activities` loggt bereits `STATUS_CHANGED`-Events mit `old_value`/`new_value` (Status-Namen) und `created_at`. Der Service liest diese Tabelle direkt.

---

## Backend

### Modul: `reports` (erweitert)

Neue Klassen neben den bestehenden `ReportsService`, `ReportsController`, `BurndownResponse`, `VelocityResponse`.

#### Domain

```
Dashboard.kt          — Entity (id, projectId)
DashboardWidget.kt    — Entity (id, dashboardId, type, config, gridX, gridY, gridW, gridH)
WidgetType.kt         — Enum (BURNDOWN, VELOCITY, CYCLE_TIME, ISSUE_COUNT, ISSUES_BY_STATUS, ISSUE_LIST)
```

#### Infrastructure

```
DashboardRepository.kt        — findByProjectId(projectId): Dashboard?
DashboardWidgetRepository.kt  — findByDashboardId(dashboardId): List<DashboardWidget>
```

#### Application: `DashboardService`

```kotlin
fun getDashboard(projectKey: String, userId: UUID): DashboardResponse      // auto-create if missing
fun saveLayout(projectKey: String, items: List<LayoutItem>, userId: UUID): DashboardResponse  // admin
fun addWidget(projectKey: String, request: AddWidgetRequest, userId: UUID): WidgetResponse    // admin
fun removeWidget(projectKey: String, widgetId: UUID, userId: UUID)                            // admin
```

`DashboardService.getDashboard` ruft `projectService.requireMember` auf; `saveLayout`, `addWidget`, `removeWidget` rufen `projectService.requireAdmin` auf.

#### `ReportsService` — Erweiterung: `getCycleTime`

```kotlin
fun getCycleTime(projectKey: String, sprintId: UUID, userId: UUID): CycleTimeResponse
```

**Algorithmus:**
1. Issues des Sprints laden (`issueRepository.findBySprintId(sprintId)`)
2. Für jedes Issue: `issue_activities` nach `type = STATUS_CHANGED` abfragen, sortiert nach `created_at`
3. Ersten Timestamp finden, bei dem `newValue` auf einen Status mit `category = IN_PROGRESS` zeigt
4. Ersten Timestamp finden, bei dem `newValue` auf einen Status mit `category = DONE` zeigt
5. `cycleTimeHours = (doneAt - inProgressAt).toHours()`
6. Issues ohne vollständigen Durchlauf werden übersprungen (kein Fehler)
7. Response: `List<IssueCycleTime>` + `averageCycleTimeHours: Double?` (`null` wenn keine Daten)

Status-Name → Kategorie-Mapping über `StatusRepository` (bestehend im `workflows`-Modul).

#### API: `DashboardController`

```
GET    /api/v1/projects/{key}/dashboard                    → DashboardResponse       (member)
PUT    /api/v1/projects/{key}/dashboard/layout             → DashboardResponse       (admin)
POST   /api/v1/projects/{key}/dashboard/widgets            → WidgetResponse          (admin)
DELETE /api/v1/projects/{key}/dashboard/widgets/{widgetId} → 204                     (admin)
```

#### API: `ReportsController` — neuer Endpoint

```
GET    /api/v1/projects/{key}/reports/cycle-time             → CycleTimeAggregateResponse  (member)
GET    /api/v1/projects/{key}/reports/cycle-time?sprintId=x  → CycleTimeResponse           (member)
```

- **Ohne `sprintId`:** Gibt `{ sprints: [{ sprintId, sprintName, averageCycleTimeHours }] }` für alle abgeschlossenen Sprints zurück — wird vom `CycleTimeWidget`-Chart verwendet (X: Sprints, Y: Stunden).
- **Mit `sprintId`:** Gibt per-Issue-Detail für diesen Sprint zurück — für zukünftige Drill-Down-Ansicht.

#### DTOs

```
DashboardResponse       — id, projectId, widgets: List<WidgetResponse>
WidgetResponse          — id, type, config (raw JSON string), gridX, gridY, gridW, gridH
AddWidgetRequest        — type, config, gridX, gridY, gridW, gridH
LayoutItem              — id, gridX, gridY, gridW, gridH
CycleTimeAggregateResponse  — sprints: List<SprintCycleTime>
SprintCycleTime             — sprintId, sprintName, averageCycleTimeHours: Double?
CycleTimeResponse           — sprintId, issues: List<IssueCycleTime>, averageCycleTimeHours: Double?
IssueCycleTime              — issueId, issueKey, cycleTimeHours: Double?
```

#### Issues-Endpoint: neue Filter-Params

Das `ISSUE_LIST`-Widget nutzt `GET /api/v1/projects/{key}/issues` mit neuen optionalen Query-Params:

| Param | Wert | Effekt |
|---|---|---|
| `assigneeMe=true` | boolean | Nur Issues, die dem aufrufenden User zugewiesen sind |
| `sort=updatedAt` | string | Sortierung nach `updated_at DESC` |
| `overdue=true` | boolean | Nur Issues mit `due_date < today` und `status.category != DONE` |

Diese Params werden in `IssueService.listByProject` ergänzt. Bestehende Params (`statusId`, `page`, `size` etc.) bleiben unverändert.

---

## Frontend

### Neue Route

```
/p/:key/dashboard  →  DashboardPage.tsx
```

Sidebar-Nav erhält einen "Dashboard"-Eintrag direkt über "Board".

### Neue Seite

**`DashboardPage.tsx`** (`frontend/src/pages/dashboard/DashboardPage.tsx`)

- Lädt Dashboard via `useProjectDashboard(key)`
- Admins sehen "Edit"-Toggle-Button oben rechts
- Im Edit-Modus: Widgets sind drag+resize-fähig, "Add Widget"-Button erscheint, Layout-Änderungen werden bei "Save" via `PUT /dashboard/layout` gespeichert
- Im Read-Modus: Canvas ist statisch, keine Interaktion

### Neue Komponenten (`frontend/src/components/dashboard/`)

| Datei | Beschreibung |
|---|---|
| `DashboardCanvas.tsx` | `react-grid-layout`-Wrapper; rendert Widget-Grid; schaltet zwischen read/edit um |
| `WidgetPalette.tsx` | Modal zum Hinzufügen eines Widgets; zeigt Widget-Typen mit Icon + Name; schickt `POST /dashboard/widgets` |
| `WidgetWrapper.tsx` | Container für jeden Widget-Slot; zeigt Titel + Remove-Button im Edit-Modus; Error Boundary bei Render-Fehler |
| `BurndownWidget.tsx` | Burndown-Chart (Recharts LineChart); Sprint-Selector im Widget-Header |
| `VelocityWidget.tsx` | Velocity Bar Chart (Recharts BarChart) |
| `CycleTimeWidget.tsx` | Cycle Time Bar Chart (Recharts BarChart; X: Sprints, Y: Stunden); Sprint via `useCycleTime` |
| `IssueCountWidget.tsx` | Stat Card: Anzahl offener Issues |
| `IssuesByStatusWidget.tsx` | Reihe von Stat Cards, eine pro Status-Kategorie (TODO / IN_PROGRESS / DONE) |
| `IssueListWidget.tsx` | Scrollbare Liste (MY_OPEN / RECENTLY_UPDATED / OVERDUE); Links auf Issue-Detail; nutzt bestehenden `GET /projects/{key}/issues`-Endpoint mit neuen Query-Params (siehe unten) |

### Neue Hooks (`frontend/src/hooks/`)

```typescript
useProjectDashboard(key: string)         // GET /dashboard
useSaveDashboardLayout(key: string)      // PUT /dashboard/layout (mutation)
useAddWidget(key: string)                // POST /dashboard/widgets (mutation)
useRemoveWidget(key: string)             // DELETE /dashboard/widgets/:id (mutation)
useCycleTimeAggregate(key: string)                   // GET /reports/cycle-time (alle Sprints, für Chart)
useCycleTime(key: string, sprintId: string)          // GET /reports/cycle-time?sprintId= (per-Issue-Detail)
```

### Abhängigkeit

```
npm install react-grid-layout
npm install --save-dev @types/react-grid-layout
```

---

## Berechtigungen

| Aktion | Rolle |
|---|---|
| Dashboard lesen | Projekt-Mitglied |
| Widgets lesen | Projekt-Mitglied |
| Layout ändern | Projekt-Admin |
| Widget hinzufügen | Projekt-Admin |
| Widget entfernen | Projekt-Admin |
| Cycle-Time-Report lesen | Projekt-Mitglied |

---

## Fehlerbehandlung

- **Dashboard auto-create:** `GET /dashboard` legt ein leeres Dashboard an, falls keines existiert — kein separater Create-Schritt
- **Widget-Render-Fehler:** `WidgetWrapper` enthält eine React Error Boundary — fehlerhafter Config-JSON → roter "Failed to load widget"-Platzhalter, kein Full-Page-Crash
- **Cycle Time ohne Daten:** Gibt leere Liste + `averageCycleTimeHours: null` zurück (kein Fehler)
- **Layout-PUT idempotent:** Safe to retry bei Netzwerkfehler

---

## Tests

### Backend

**`DashboardServiceTest`** (Unit):
- `getDashboard` legt Dashboard auto an wenn nicht vorhanden
- Member kann lesen, Nicht-Admin kann nicht schreiben (→ `ForbiddenException`)
- `addWidget` / `removeWidget` / `saveLayout` nur für Admin

**`ReportsServiceTest`** — neue Testfälle für `getCycleTime`:
- Happy path: Issue durchläuft IN_PROGRESS → DONE → `cycleTimeHours` korrekt berechnet
- Issue nie IN_PROGRESS → wird übersprungen, kein Fehler
- Issue IN_PROGRESS aber nie DONE → wird übersprungen
- Mehrere Issues → `averageCycleTimeHours` korrekt gemittelt
- Keine Issues im Sprint → `averageCycleTimeHours: null`

**`DashboardControllerTest`** (Integration):
- `GET /dashboard` ohne existierendes Dashboard → 200 + leeres Widget-Array
- Member kann lesen, Nicht-Mitglied bekommt 403
- Nicht-Admin kann `POST /dashboard/widgets` nicht aufrufen → 403
- Admin kann Widget hinzufügen und Layout speichern
