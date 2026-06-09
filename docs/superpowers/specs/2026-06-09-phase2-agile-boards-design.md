# Phase 2: Agile Boards — Design Spec

**Date:** 2026-06-09
**Status:** Approved
**Depends on:** Phase 1 (Core Foundation)

## Ziel

Agile Board-Funktionalität für TaskWolf: ein Sprint Board (Kanban-View des aktiven Sprints), ein Backlog (Sprint Planning), Burndown- und Velocity-Charts, sowie Real-time-Updates via WebSocket. Kein Swimlane-Support in dieser Phase.

---

## Entscheidungen

| Frage | Entscheidung | Begründung |
|---|---|---|
| Board-Typ | Kanban + Scrum (Board + Backlog) | Vollständig agile Workflow-Unterstützung |
| Sprint-Tiefe | Vollständig (Goal, Datum, Burndown, Velocity, unfertige Issues Dialog) | Näher an Jira/Plane, vollständiger Scrum-Lifecycle |
| WebSocket | Phase 2 | Jetzt implementieren vermeidet Umbau in Phase 3 |
| Swimlanes | Nein (defer) | Unnötige Komplexität für Phase 2 |
| Backend-Ansatz | Thin Backend (kein eigenständiges Boards-Modul) | Board ist Aggregations-View; Domain-Logik in `issues` + `sprints` |

---

## Backend

### Neues Modul: `sprints`

Hexagonal-Struktur wie alle anderen Module: `domain/`, `application/`, `infrastructure/`, `api/`.

#### Domain

**Sprint-Entity:**
```
id         UUID
name       VARCHAR(255)
goal       TEXT nullable
status     ENUM(PLANNED, ACTIVE, CLOSED)
startDate  DATE nullable
endDate    DATE nullable
projectId  UUID → projects
createdAt  TIMESTAMP
updatedAt  TIMESTAMP
```

**Invarianten:**
- Pro Projekt maximal ein ACTIVE Sprint gleichzeitig
- Start nur von PLANNED möglich
- Complete nur von ACTIVE möglich
- Beim Complete: alle Issues mit `status.category != DONE` bekommen `sprint_id = null` (zurück in Backlog)

**Domain Events:**
- `SprintStartedEvent(sprint: Sprint)`
- `SprintCompletedEvent(sprint: Sprint, movedToBacklog: Int)`

#### Application: `SprintService`

```kotlin
fun create(projectKey: String, request: CreateSprintRequest, actor: User): Sprint
fun update(projectKey: String, sprintId: UUID, request: UpdateSprintRequest, actor: User): Sprint
fun start(projectKey: String, sprintId: UUID, actor: User): Sprint
fun complete(projectKey: String, sprintId: UUID, actor: User): SprintCompleteResult
fun listByProject(projectKey: String, userId: UUID): List<Sprint>
```

`SprintCompleteResult` enthält den abgeschlossenen Sprint und die Anzahl zurückgesetzter Issues.

#### API Endpoints

```
GET    /api/v1/projects/{key}/sprints              → List<SprintResponse>
POST   /api/v1/projects/{key}/sprints              → SprintResponse (201)
PATCH  /api/v1/projects/{key}/sprints/{id}         → SprintResponse
POST   /api/v1/projects/{key}/sprints/{id}/start   → SprintResponse
POST   /api/v1/projects/{key}/sprints/{id}/complete → SprintCompleteResponse
```

`SprintCompleteResponse`: `{ sprint, movedToBacklogCount }`.

---

### Board-Aggregator (kein eigenes Domain-Modul)

Ein `BoardService` im `boards`-Package aggregiert Daten aus `issues` und `sprints`. Kein eigener Domain-Layer, keine eigenen Entitäten.

#### Endpoints

```
GET    /api/v1/projects/{key}/board
```
Response:
```json
{
  "sprint": { "id", "name", "goal", "startDate", "endDate", "daysRemaining", "totalPoints", "completedPoints" },
  "columns": [
    { "status": { "id", "name", "category", "color" }, "issues": [ IssueResponse, ... ] }
  ]
}
```
Gibt `204 No Content` zurück wenn kein aktiver Sprint existiert.

```
PATCH  /api/v1/projects/{key}/board/move
```
Request: `{ "issueId": UUID, "newStatusId": UUID }` — dünner Wrapper um `IssueService.update()`. Publiziert kein eigenes Event (das `IssueStatusChangedEvent` reicht).

```
GET    /api/v1/projects/{key}/backlog
```
Response:
```json
{
  "sprints": [
    { "sprint": SprintResponse, "issues": [ IssueResponse, ... ] }
  ],
  "backlogIssues": [ IssueResponse, ... ]
}
```
`sprints` enthält alle PLANNED-Sprints (inkl. Issues). `backlogIssues` = Issues ohne Sprint-Zuweisung.

---

### Reports-Endpoints

```
GET /api/v1/projects/{key}/reports/burndown?sprintId={id}
```
Response: `{ sprintId, days: [ { date, plannedPoints, remainingPoints } ] }`

Burndown-Logik: `plannedPoints` = Story Points beim Sprint-Start (Snapshot). `remainingPoints` = Points von Issues die an diesem Tag noch nicht DONE waren. Für zukünftige Tage im aktiven Sprint wird der aktuelle Stand fortgeschrieben.

```
GET /api/v1/projects/{key}/reports/velocity
```
Response: `[ { sprintId, sprintName, completedPoints, plannedPoints } ]` — nur CLOSED Sprints.

**Sprint-Points-Snapshot:** Beim `start`-Aufruf wird `Sprint.plannedPoints` (neues Feld) mit der aktuellen Story-Points-Summe befüllt. Damit bleibt der Burndown stabil auch wenn Issues später hinzugefügt werden.

---

### WebSocket

**Config:** Neuer `WebSocketConfig`-Bean mit STOMP-Endpoint `/ws`, Application Destination Prefix `/app`, Broker Topic `/topic`.

**BoardEventPublisher** (in `boards`-Package, lauscht via `@EventListener`):

| Domain Event | WebSocket Payload |
|---|---|
| `IssueStatusChangedEvent` | `{ type: "ISSUE_MOVED", issueId, newStatusId, projectKey }` → `/topic/projects/{key}` |
| `SprintStartedEvent` | `{ type: "SPRINT_UPDATED", sprintId, projectKey }` → `/topic/projects/{key}` |
| `SprintCompletedEvent` | `{ type: "SPRINT_UPDATED", sprintId, projectKey }` → `/topic/projects/{key}` |

---

### DB-Migration: V5

```sql
CREATE TABLE sprints (
    id           UUID         NOT NULL PRIMARY KEY,
    name         VARCHAR(255) NOT NULL,
    goal         TEXT,
    status       VARCHAR(20)  NOT NULL DEFAULT 'PLANNED',
    start_date   DATE,
    end_date     DATE,
    planned_points INT,
    project_id   UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL
);

CREATE INDEX idx_sprints_project ON sprints(project_id);

ALTER TABLE issues ADD CONSTRAINT fk_issues_sprint
    FOREIGN KEY (sprint_id) REFERENCES sprints(id) ON DELETE SET NULL;
```

---

## Frontend

### Neue Seiten

#### `/p/:key/board` — Sprint Board

- Sprint-Header: Name, Goal (falls gesetzt), Datum-Range, verbleibende Tage, `completedPoints / totalPoints`-Progress-Bar
- Status-Spalten (aus Workflow des Projekts)
- Issue-Karten mit: Key (Monospace), Titel, Priority-Badge (farbig), Story-Points-Chip (falls gesetzt), Assignee-Initials-Avatar
- Drag & Drop via `@dnd-kit`: Card zwischen Spalten ziehen = `PATCH /board/move`
- "Complete Sprint"-Button im Header → Bestätigungs-Dialog: "X Issues sind noch nicht abgeschlossen und werden in den Backlog verschoben. Sprint abschließen?"
- Leerer Zustand: Card mit Link zum Backlog ("Kein aktiver Sprint")
- WebSocket-Verbindung aktiv solange Seite offen

#### `/p/:key/backlog` — Backlog

- Abschnitt pro PLANNED Sprint (ausgeklappt by default): Sprint-Name, Datum, Points-Summe, "Start Sprint"-Button
- "Start Sprint" öffnet Confirmation-Dialog (zeigt Issue-Count und Points)
- Backlog-Abschnitt unten: Issues ohne Sprint
- Drag & Drop: Issues zwischen Sprint-Abschnitten und Backlog verschieben = `PATCH issues/{id}` mit `{ sprintId }`
- "Neuer Sprint"-Button: Inline-Form mit Name (required), Goal (optional), Start/End-Datum (optional)

#### `/p/:key/reports` — Reports

- Sprint-Selektor (Dropdown, Default: letzter aktiver/abgeschlossener Sprint)
- **Burndown-Chart** (`Recharts LineChart`): X-Achse = Datum, Y-Achse = Story Points, zwei Linien: "Ideal" (gerade Linie von `plannedPoints` → 0) und "Tatsächlich" (aus `/reports/burndown`)
- **Velocity-Chart** (`Recharts BarChart`): ein gruppierter Balken pro abgeschlossenem Sprint (geplant vs. abgeschlossen)

### Navigation

`AppLayout` bekommt keinen projektseitigen Sub-Nav — das ist zu früh. Stattdessen: wenn ein Projekt-Key in der URL ist (`/p/:key/*`), zeigt die Sidebar unterhalb des Projektnamens drei Links: Board, Backlog, Reports.

### Neue API-Module

`frontend/src/api/sprints.ts` — Sprint CRUD + Lifecycle
`frontend/src/api/board.ts` — Board, Move, Backlog
`frontend/src/api/reports.ts` — Burndown, Velocity

### Neue Hooks

`useSprints(projectKey)`, `useCreateSprint`, `useStartSprint`, `useCompleteSprint`
`useBoard(projectKey)`, `useBacklog(projectKey)`, `useMoveIssue(projectKey)`
`useBurndown(projectKey, sprintId)`, `useVelocity(projectKey)`
`useProjectSocket(projectKey)` — WebSocket Hook, invalidiert React Query Cache bei Events

---

## Tests

### Unit Tests (MockK)

**`SprintServiceTest`:**
- Start eines PLANNED Sprints → status wird ACTIVE
- Start schlägt fehl wenn bereits ein ACTIVE Sprint existiert (`ConflictException`)
- Complete schlägt fehl wenn Sprint nicht ACTIVE (`IllegalStateException`)
- Complete setzt `sprint_id = null` für alle nicht-DONE Issues
- `SprintCompleteResult.movedToBacklogCount` gibt korrekte Anzahl zurück

**`BoardServiceTest`:**
- Board gibt nur Issues des aktiven Sprints zurück
- Board gibt 204 zurück wenn kein aktiver Sprint
- Move delegiert an `IssueService.update()`

### Integration Test (Testcontainers)

**`SprintLifecycleIntegrationTest`:**
Vollständiger Flow via HTTP:
1. Projekt + Issues anlegen
2. Sprint erstellen, Issues zuweisen
3. Sprint starten → Board-Endpoint gibt Issues zurück
4. Issue auf DONE setzen
5. Sprint abschließen → unfertige Issues im Backlog prüfen
6. Velocity-Endpoint gibt einen Eintrag zurück

---

## Nicht in Scope (Phase 2)

- Swimlanes (Phase 4/5)
- Sprint-Kapazitätsplanung / Planning Poker
- Cycle Time Report (Phase 5)
- Custom Dashboard (Phase 5)
- WebSocket für andere Module als Board/Sprints (Phase 3)
