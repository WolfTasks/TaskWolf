# Spec: Projekt-Permissions-Fixes — Self-Role-Change (B1) + Read-only-Enforcement (B2)

> Design 2026-07-11, **korrigiert 2026-07-11** nach Reproduktion. Backlog:
> `2026-07-07-backlog-overview.md` B1 + B2. Baut auf #9 (Projekt-Permissions,
> `2026-07-11-project-permissions-design.md`, Release v1.0.10) auf.
>
> **Aktueller Umsetzungs-Scope dieser Spec: nur B1** (kleiner, bestätigter
> Backend-Fix). B2 wurde nach Reproduktion neu eingeordnet (reines Frontend) und
> bekommt einen **eigenen Zyklus** — siehe Abschnitt „B2 (neu eingeordnet)".

## Kontext

Zwei gemeldete Bugs (2026-07-11) an der in #9 eingeführten Projekt-Rollen-/
Rechteverwaltung (`VIEWER` / `MEMBER` / `ADMIN`).

## B1 — Selbst-Rollenänderung unterbinden (bestätigt, in Scope)

**Ursache (bestätigt):** `ProjectService.changeMemberRole` prüft nur
`requireAdmin(actor)` und blockt die *Owner*-Zielzeile, aber **nicht**
`actorId == targetUserId`. Ein (Nicht-Owner-)Projekt-Admin kann damit seine
**eigene** Rolle ändern (z.B. sich versehentlich aus der Admin-Rolle entfernen).
Im Frontend ist das Rollen-`<select>` nur für die *Owner*-Zeile deaktiviert,
nicht für die eigene Zeile.

### Design B1

**Backend** — `ProjectService.changeMemberRole` (`.../projects/application/ProjectService.kt`):

```kotlin
val project = requireAdmin(projectKey, actorId)
if (actorId == targetUserId) throw ForbiddenException("You cannot change your own role")
if (project.owner.id == targetUserId) throw ForbiddenException("Cannot change the project owner's role")
...
```

Eine Rollenänderung muss von einem *anderen* Admin ausgehen.

**Frontend** — `MembersPage.tsx`: Rollen-`<select>` für die **eigene** Zeile
deaktivieren (`user.id === me.id`), mit dezentem „You"-Hinweis. Aktuelle
User-ID über den bestehenden `useMe()`-Hook (`@/hooks/useAuth`, liefert `me.id`).

**Abgrenzung:** B1 betrifft nur die *Rollenänderung*. Das *Selbst-Entfernen*
(`removeMember` der eigenen Mitgliedschaft = ein Projekt verlassen) bleibt
erlaubt und wird hier nicht geändert.

### Testing B1

Backend: JUnit + MockK, real-Postgres-Integrationstest (`IntegrationTestBase`,
Testcontainers). In `ProjectMemberIntegrationTest`:
- Ein (Nicht-Owner-)**Admin** PATCH auf die *eigene* Rolle → **403**.
- Positiv-Kontrolle: derselbe Admin ändert die Rolle eines *anderen* Members → 200.

Frontend: `tsc`-Typecheck grün + Wolfgangs manuelle Verifikation (eigene Zeile
nicht editierbar, fremde Zeilen weiterhin änderbar).

## B2 (neu eingeordnet) — Read-only-Enforcement ist Frontend-Sache

> **Status: aus dieser Spec ausgegliedert → eigener Frontend-Zyklus.**

### Korrektur der ursprünglichen Analyse

Die erste Fassung dieser Spec behauptete **6 ungeschützte Backend-Controller**
(Workflow, Automation, Dashboard, Webhook, ApiKey, Integration). Das war ein
**Falsch-Positiv**: Das Audit-Grep suchte nur nach der `@PreAuthorize`-Annotation
und übersah, dass dieses Codebase Writes **auch auf Service-Ebene** über
`projectService.requireAdmin(...)` / `requireMember(...)` absichert. Alle sechs
Controller rufen bereits `requireAdmin` → **VIEWER ist überall geblockt**.

### Reproduktion (2026-07-11)

Realer Integrationstest (VIEWER bearbeitet ein bestehendes Ticket):
- `PATCH /api/v1/projects/{key}/issues/{id}` als VIEWER → **403 Forbidden**.
- Ticket-Titel danach unverändert (`original`) → **nichts persistiert**.

Ergebnis: **Das Backend setzt Read-only für Tickets korrekt durch.** Jeder
schreibende Ticket-Pfad ist geschützt — der einzige Edit-Endpoint
`PATCH /issues/{id}` (Titel, Status/Transition, Assignee, Story Points, Labels,
Versionen, Custom Fields, Sprint) trägt `@PreAuthorize canWrite`; ebenso
create/delete, Board-Move, Comments, Attachments, Sprint-Zuordnung.

### Tatsächliche Ursache (B2)

Das **Frontend** konsultiert `project.myRole` **nirgends** in den
Edit-Oberflächen (Grep über `frontend/src` findet Rollen-Checks nur auf der
Members-Seite und beim Sidebar-„Members"-Link). Ein VIEWER bekommt daher die
volle Edit-UI (Inline-Issue-Editoren, Board-Drag, Story-Points, Backlog, sowie
Settings-Links wie Labels/Versionen/Custom-Fields/API-Keys/Webhooks). Seine
Writes prallen serverseitig mit 403 ab, aber die UI verhindert den Versuch nicht
und zeigt Änderungen ggf. optimistisch an → wirkt wie „Read-only greift nicht".

### Empfohlener Scope für den B2-Folgezyklus (nicht in dieser Spec umgesetzt)

- Ein abgeleitetes `canWrite`-Flag aus `project.myRole` (VIEWER → read-only)
  durch alle Edit-Oberflächen fädeln; Write-Affordances ausblenden/disablen.
- Zu klärende Design-Punkte (eigener Brainstorm): welche Oberflächen zuerst,
  wie read-only visuell dargestellt wird, ob Nicht-Admin-Settings-Links
  (API-Keys/Webhooks/Integrationen/Automation) ausgeblendet werden.
- **Defense-in-Depth (Backend):** Regressionstest ergänzen, der
  `VIEWER → PATCH /issues/{id}` → 403 festschreibt (dieser Guard ist heute
  ungetestet — die vorhandene `ProjectWriteEnforcementIntegrationTest` deckt nur
  create/board/label/comment/attachment ab).

## Reihenfolge / Abhängigkeiten

B1 ist unabhängig und wird jetzt umgesetzt (eigener isolierter Worktree,
Wolfgangs Standard). B2 folgt als separater Frontend-Zyklus mit eigenem
Brainstorm/Spec/Plan.
