# #9 — Projekt-Rechte-Verwaltung (Member-Management + Rollen-Durchsetzung)

> Spec, 2026-07-11. Backlog-Item #9 aus `2026-07-07-backlog-overview.md`.
> **Scope-Entscheid:** nur **Projekte** (Orgs haben bereits Member-Management
> `OWNER/ADMIN/MEMBER` + UI → eigener Folge-Zyklus).

## Problem / Ausgangslage

TaskWolf hat heute **keine** Projekt-Mitglieder-Verwaltung über die UI/API:
Mitglieder entstehen nur implizit (Projekt-Ersteller wird beim Anlegen als
`ProjectRole.ADMIN` eingetragen, `ProjectService.create`). `ProjectController`
bietet lediglich `GET /projects/{key}/members` (liefert bare `UserResponse`
**ohne Rolle**). Es gibt keinen Endpoint zum Hinzufügen, Entfernen oder Ändern
von Rollen.

Der Enum `ProjectRole { ADMIN, MEMBER, VIEWER }` existiert bereits, **aber
`VIEWER` ist tot**: Alle Schreibpfade nutzen `ProjectService.requireMember`
(prüft nur Mitgliedschaft/Owner, **nicht** die Rolle) oder `requireAdmin`.
Es gibt keine Durchsetzung von „read-only" — ein VIEWER dürfte heute schreiben.

Ziel von #9: Projekt-Admins können Nutzer gezielt freischalten und ihnen eine
von drei Rollen zuweisen — **Read-only / Read & Write / Admin** — und die
Read-only-Rolle wird server- **und** clientseitig tatsächlich durchgesetzt.

## Nicht-Ziele (YAGNI)

- **Organisationen:** außerhalb dieses Zyklus (bereits vorhanden).
- **Feingranulare Permissions** (pro-Feature-ACLs, custom roles): nein, nur die
  drei festen Rollen.
- **Einladungen per E-Mail / Pending-Invites:** nein. Ein Nutzer muss bereits
  existieren; er wird direkt freigeschaltet.
- **Bulk-Import / CSV:** nein.

## Rollenmodell

Wiederverwendung des vorhandenen `ProjectRole { ADMIN, MEMBER, VIEWER }` — **keine
DB-Migration nötig** (Enum enthält bereits alle drei Werte, gespeichert als
`EnumType.STRING`). UI-Labels bilden die fachlichen Rollen ab:

| ProjectRole (Backend/DB) | UI-Label        | Rechte                                             |
|--------------------------|-----------------|----------------------------------------------------|
| `VIEWER`                 | **Read-only**   | Nur Lesen. Alle Mutationen → 403.                  |
| `MEMBER`                 | **Read & Write**| Lesen + inhaltliche Mutationen (Issues, Kommentare, Labels, Sprints, Versionen, Custom-Field-Werte, Board, Transitions). Keine Projekt-Admin-Aktionen. |
| `ADMIN`                  | **Admin**       | Alles inkl. Projekt-Admin (Member-Verwaltung, Workflow-Editor, Webhooks, Integrationen, Service-Desk, API-Keys, Projekt-Dashboard-Config). |

`project.owner` ist **implizit immer Admin** (bereits so in
`ProjectService.isProjectAdmin`: Owner → `true`). Der Owner ist **nicht
entfernbar und nicht degradierbar**. Damit hat jedes Projekt garantiert ≥1 Admin.

## Autorisierung — Read-only-Durchsetzung

Kern der Read-only-Durchsetzung. **Mechanismus:** deklarative Methoden-Security am
Controller — analog zum bereits existierenden
`@PreAuthorize("@projectSecurity.isProjectAdmin(#key, authentication)")`
(AuditController, ServiceDeskController, IncidentController). Das minimiert die
Änderungsfläche (keine Eingriffe in die 6 Schreib-Services und ihre Unit-Tests)
und ist konsistent mit dem vorhandenen Muster.

### Neue Logik in `ProjectService`

```kotlin
// Rolle des Nutzers im Projekt: Owner → ADMIN; sonst Member-Rolle; Nicht-Mitglied → null
@Transactional(readOnly = true)
fun roleOf(project: Project, userId: UUID): ProjectRole? =
    if (project.owner.id == userId) ProjectRole.ADMIN
    else memberRepository.findByProjectIdAndUserId(project.id, userId)?.role

// Darf schreiben = Mitglied/Owner UND nicht VIEWER
@Transactional(readOnly = true)
fun canWrite(projectKey: String, userId: UUID): Boolean {
    val project = findByKey(projectKey)
    val role = roleOf(project, userId) ?: return false   // Nicht-Mitglied
    return role != ProjectRole.VIEWER
}
```

### Neuer `ProjectSecurity`-Check

```kotlin
fun canWrite(key: String, authentication: Authentication): Boolean {
    val user = authentication.principal as? User ?: return false
    return try { projectService.canWrite(key, user.id) } catch (_: Exception) { false }
}
```

### Annotierte Schreib-Endpoints

`@PreAuthorize("@projectSecurity.canWrite(#key, authentication)")` auf allen
mutierenden Endpoints (alle haben `@PathVariable key`). Fehlschlag →
`AccessDeniedException` → **403** über den bestehenden `GlobalExceptionHandler`
(generische Message, kein Enum-/Interna-Leak, H2-konform). Betroffen:

- **IssueController:** `create` (POST), `update` (PATCH), `delete` (DELETE).
- **CommentController:** `addComment` (POST). `editComment`/`deleteComment`
  bleiben autor-/admin-gegated (ein VIEWER kann ohnehin keinen Kommentar verfasst
  haben, da `addComment` blockiert).
- **LabelController:** `create` / `update` / `delete`.
- **SprintController:** `create` / `update` / `start` / `complete` /
  `assignIssue` / `unassignIssue`.
- **VersionController:** `create` / `update` / `delete`.
- **CustomFieldController:** `create` / `update` / `reorder` / `delete` /
  `createOption` / `updateOption` / `deleteOption`.

**Reine Lese-Endpoints** bleiben ungegated bzw. bei `requireMember` (VIEWER darf
lesen). **`BoardService`** hat nur Lesepfade (`getBoard`/`getBacklog`) — keine
Board-Mutation; Board-Änderungen laufen über `IssueService.update` (Status) bzw.
`SprintService.assignIssue` und sind darüber bereits abgedeckt.

**Unverändert:** `requireAdmin`- und `isProjectAdmin`-Gates (Workflow-Editor,
Webhooks, Integrationen, Service-Desk, API-Keys, Projekt-Dashboard-Config)
bleiben Admin. Die Schreib-Services behalten ihr internes `requireMember`
(harmlose Defense-in-Depth; kein Umbau nötig).

**Member-Verwaltung** selbst erfordert `requireAdmin` (Admin/Owner/System-ADMIN).

## Backend-API

Alle unter `/api/v1/projects/{key}/members`, sofern nicht anders vermerkt.

| Methode | Pfad | Auth | Verhalten |
|---------|------|------|-----------|
| `GET`   | `…/members` | `requireMember` | Liefert `List<ProjectMemberResponse>` (User **+ Rolle**). **Breaking Change** ggü. heute (bare `UserResponse`) — Frontend-Consumer mitziehen. |
| `POST`  | `…/members` | `requireAdmin` | Body `{ userId: UUID, role: ProjectRole }`. Fügt Mitglied hinzu. `409` wenn bereits Mitglied. `404` wenn User unbekannt. |
| `PATCH` | `…/members/{userId}` | `requireAdmin` | Body `{ role: ProjectRole }`. Ändert Rolle. Owner-Rolle ändern → `403`. |
| `DELETE`| `…/members/{userId}` | `requireAdmin` | Entfernt Mitglied. Owner entfernen → `403`. Self-Remove erlaubt (außer Owner). |
| `GET`   | `/api/v1/users/search?q=…` | System-ADMIN **oder** Admin ≥1 Projekts | User-Lookup für Add-Dialog. Min. Query-Länge **2**, sonst leere Liste. Liefert `List<{ id, username, email }>` (limit z.B. 10). |
| `GET`   | `/api/v1/projects/{key}` | `requireMember` | `ProjectResponse` erhält **neues Feld `myRole: ProjectRole`** (Rolle des Callers; Owner → `ADMIN`) fürs Frontend-Gating. |

### DTOs

- `ProjectMemberResponse { user: UserResponse, role: ProjectRole }`
- `AddProjectMemberRequest { userId: UUID, role: ProjectRole }` (`@NotNull`)
- `UpdateProjectMemberRoleRequest { role: ProjectRole }` (`@NotNull`)
- `UserSearchResponse { id: UUID, username: String, email: String }`
- `ProjectResponse` + `myRole: ProjectRole`

### User-Suche — Sicherheit

- Nur für Nutzer freigegeben, die **System-ADMIN** sind oder in **mindestens
  einem** Projekt Admin/Owner. Verhindert, dass beliebige Nutzer das User-Directory
  enumerieren.
- Match auf `username` **oder** `email` (case-insensitive, `contains`/`startsWith`).
- Nur aktive Nutzer (`active = true`, kein `deletedAt`) — konsistent mit dem
  User-Lifecycle aus #2.
- Response enthält **keine** sensiblen Felder (kein Passwort-Hash, keine Rollen).

## Frontend

### MembersPage

Neu: `frontend/src/pages/projects/settings/MembersPage.tsx` (analog zu
`LabelsPage`/`VersionsPage`/`CustomFieldsPage`/`ProjectAuditPage` im selben
Ordner). Route + Sidebar-Link **„Members"** in der Project-Sektion von
`AppLayout.tsx`. Sichtbar/aktiv nur für Projekt-Admins (via `myRole === 'ADMIN'`).

Inhalt:
- **Mitgliederliste** mit Spalten Nutzer (Username/E-Mail) und Rolle. Rolle als
  Inline-`<select>` (Read-only / Read & Write / Admin) → `PATCH`. Remove-Button
  → `DELETE`. **Owner-Zeile:** Rolle/Remove disabled, Badge „Owner".
- **Add-Member-Form:** Autocomplete-Eingabe (E-Mail/Username) → `users/search`
  (debounced, ab 2 Zeichen), Treffer als Dropdown, Auswahl + Rollen-Select →
  `POST`. Fehlerbehandlung: schon Mitglied (409) sauber anzeigen.

API-Client (`frontend/src/api/projects.ts`) + Hooks (`useProjectMembers.ts`
erweitern: liefert jetzt Rollen; neue Mutations-Hooks für add/update/remove;
`useUserSearch`).

### Read-only-Gating (Defense-in-Depth)

- Kleiner Hook `useProjectRole(key)` / abgeleiteter `canWrite` aus
  `project.myRole` (`myRole !== 'VIEWER'`).
- Schreib-Controls werden bei Read-only **disabled/versteckt**: Issue-Edit &
  Inline-Editoren, Comment-Box, Board-Drag & Karten-Aktionen, Backlog-Aktionen,
  Labels/Versions/CustomFields-Buttons, Status-Transitions, „New Issue".
- Das Backend-403 bleibt die **harte** Grenze; das Gating ist nur UX.

## Fehlerbehandlung & Edge Cases

- **VIEWER → Write:** `403 ForbiddenException` mit generischer Message (keine
  Interna).
- **Owner schützen:** PATCH/DELETE auf Owner → `403`.
- **Doppel-Add:** `409 ConflictException`.
- **Unbekannter User bei Add:** `404 NotFoundException`.
- **Leere/zu kurze Suche:** leere Liste (kein Fehler).
- **Self-Demote eines Admins:** erlaubt — Owner sichert weiterhin ≥1 Admin.
- **Rollen-Enum-Parsing** (PATCH/POST/Add): unbekannte Rolle → sauberer `400`
  ohne Enum-Namen zu leaken (H2-konform; z.B. Bean-Validation / explizite Prüfung
  statt roher `valueOf`-Message).

## Testing

- **Backend (MockK, Unit):**
  - `roleOf` / `canWrite`: VIEWER → false; MEMBER/ADMIN/Owner → true; Nicht-Mitglied → false.
  - Member-CRUD-Autorisierung: Nicht-Admin → 403; Owner-Schutz (PATCH/DELETE); Doppel-Add → 409; unbekannter User → 404.
  - User-Suche: Berechtigung (nur Admin/System-ADMIN), Min-Länge, nur aktive Nutzer, keine sensiblen Felder.
  - `ProjectResponse.myRole` korrekt für Owner/Admin/Member/Viewer.
- **Backend (Real-Postgres):** wo Rollen-Persistenz / Repository-Query zählt
  (Member speichern/lesen, User-Suche-Query) — konsistent mit der Projekt-Regel,
  DB-nahe Logik gegen echtes Postgres zu testen.
- **Frontend:** kein Test-Framework → `tsc`-Typecheck + manuelle Verifikation
  (Wolfgang) der drei Rollen-Flows.

## Phasierung (für den Plan)

Großes, aber kohärentes Full-Stack-Feature. Passend zu „eine Phase pro Session"
wird der Implementierungsplan in Teilphasen geschnitten:

- **Phase A — Backend:** Rollen-CRUD-API + `canWrite`-Enforcement
  (`@PreAuthorize` an Schreib-Endpoints) + `myRole` im `ProjectResponse` +
  `users/search` + Tests.
- **Phase B — Frontend MembersPage:** Verwaltungs-UI (Liste, Rollenwechsel,
  Add mit Autocomplete, Remove) + Route/Sidebar-Link.
- **Phase C — Frontend Read-only-Gating:** `canWrite`-Ableitung + Disable/Hide
  der Schreib-Controls über die betroffenen Seiten.

## Berührungspunkte

- **#2** (PAT + User-Lifecycle): liefert `User.active`/`deletedAt` (User-Suche
  filtert darauf) und den system-`ADMIN`/`MEMBER`-Lebenszyklus. #9 ergänzt die
  **per-Projekt**-Zuweisung, die #2 explizit nicht lieferte.
- **#3** (Profil/Settings): keine harte Abhängigkeit; MembersPage lebt in der
  Project-Settings-Sektion.
- **PAT-Scopes** (`READ_ONLY`/`READ_WRITE`): orthogonal — Token-Scope begrenzt,
  was ein Token darf; die Projektrolle begrenzt, was der Nutzer darf. Effektives
  Recht = Schnittmenge (bestehende Scope-Prüfung bleibt; keine Änderung nötig).
```
