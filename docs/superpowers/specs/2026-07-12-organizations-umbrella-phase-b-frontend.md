# Organisationen als Oberkategorie — Phase B (Frontend)

> Design-Spec, 2026-07-12. Backlog **#14**, Phase B. Setzt auf Phase A (Backend)
> auf: `2026-07-12-organizations-umbrella-design.md` (§6 skizziert die Frontend-
> Arbeit; dieses Dokument detailliert sie zur Umsetzungsreife). Nachfolge-Zyklus zu
> #9 (Projekt-Permissions-Frontend), dessen Muster wiederverwendet werden.

## 1. Ziel

Das Frontend so erweitern, dass **Org-Admins ihre eigene Organisation verwalten**
(Member, Rollen), **Projekte einer Org zugeordnet** werden können und die in Phase A
implementierte **Rechte-Vererbung** im UI sichtbar/nutzbar wird. Kein neues
Backend — ausschließlich Konsum der in Phase A gelieferten Endpoints.

## 2. Ausgangslage & Abhängigkeit

- **Phase A ist gemergt-fähig, aber noch offen** (PR #55, Branch
  `worktree-org-umbrella-phase-a`). Der `main`-Checkout enthält noch den **alten**
  Org-Controller (System-ADMIN-only, kein `OrgSecurity`, keine PATCH-Rollen-Route,
  alte Member-Shape). Die echten Phase-B-Ziele existieren nur im Worktree.
- **Konsequenz (Entscheidung):** Phase B wird in einem **neuen Worktree gebaut, der
  von `worktree-org-umbrella-phase-a` abzweigt** — so kompiliert das Frontend gegen
  die echten Endpoints und der volle Stack ist lokal lauffähig. Merge nach/mit #55.

### Von Phase A gelieferte Endpoints (Vertrag für Phase B)

| Methode/Route | Auth | Body / Response |
|---|---|---|
| `GET /api/v1/organizations` | System-ADMIN | `OrganizationResponse[]` |
| `POST /api/v1/organizations` | System-ADMIN | `{name, slug}` → `OrganizationResponse` |
| `GET /api/v1/organizations/mine` | authentifiziert | `OrganizationResponse[]` (Orgs des Users) |
| `GET /api/v1/organizations/{id}` | Member **oder** System-ADMIN | `OrganizationResponse` |
| `GET /api/v1/organizations/{id}/members` | Member **oder** System-ADMIN | `OrganizationMemberResponse[]` |
| `POST /api/v1/organizations/{id}/members` | `@orgSecurity.isOrgAdmin` | `AddMemberRequest` → `OrganizationMemberResponse` |
| `PATCH /api/v1/organizations/{id}/members/{userId}` | `@orgSecurity.isOrgAdmin` | `UpdateOrgMemberRoleRequest` → `OrganizationMemberResponse` |
| `DELETE /api/v1/organizations/{id}/members/{userId}` | `@orgSecurity.isOrgAdmin` | 204 |
| `PATCH /api/v1/projects/{key}/organization` | Setzen: Projekt-ADMIN **und** OrgOWNER/ADMIN der Ziel-Org; Lösen: Projekt-ADMIN; System-ADMIN darf beides | `{orgId: UUID \| null}` → `ProjectResponse` |

Bestätigte DTO-Shapes (Worktree):

```
OrganizationResponse       = { id: UUID, name: string, slug: string }
UserResponse               = { id: UUID, email, displayName, avatarUrl: string|null, role: string }
OrganizationMemberResponse = { user: UserResponse, role: OrgRole }   // OrgRole ∈ OWNER|ADMIN|MEMBER
AddMemberRequest           = { userId: UUID, role: OrgRole = MEMBER }
UpdateOrgMemberRoleRequest = { role: OrgRole }
ProjectResponse            = { id, key, name, description, ownerId, archived, orgId: UUID|null, myRole: ProjectRole|null }
SetProjectOrganizationRequest = { orgId: UUID | null }
```

### Wiederverwendbare Frontend-Muster (aus #9)

- `pages/projects/settings/MembersPage.tsx` — `AddMemberForm` mit
  `useUserSearch`-Autocomplete (Debounce 300 ms), Rollen-`<select>`, Owner-/Self-
  Schutz (`disabled`), 409-Handling.
- `hooks/useUserSearch.ts` (Autocomplete gegen `/users/search`),
  `hooks/useProjectMembers.ts` (Query + Add/Update/Remove-Mutations mit
  `invalidateQueries`), `hooks/useProjectRole.ts` / `myRole`-Gating.
- Sidebar: `layouts/AppLayout.tsx` — globaler „Organizations"-`NavItem` (→`/orgs`)
  existiert bereits; Projekt-Settings-Untereinträge sind einzeln gated
  (`currentProject?.myRole === 'ADMIN'` für „Members").
- Router: `app/router.tsx` — flache Projekt-Settings-Routen
  `/p/:key/settings/*`; Org-Routen `/orgs` und `/orgs/:orgId/settings` existieren.

## 3. Entscheidungen (Brainstorming Phase B)

1. **Base-Branch:** neuer Worktree abzweigend von `worktree-org-umbrella-phase-a`
   (nicht von `main`), damit FE gegen die echten Endpoints baut und verifizierbar ist.
2. **Vererbung im Projekt-UI:** **Info-Banner** auf der Members-Seite (keine
   Synthese geerbter Zeilen — das würde das eingefrorene Phase-A-Backend erneut
   anfassen). Ehrliche Erklärung „warum hat jemand Zugriff".
3. **Projekt→Org-Zuordnung:** **eigene** Projekt-Settings-Seite
   `/p/:key/settings/organization` (Admin-only NavItem, analog Members/Labels/…).
4. **OrgsPage-Zugang:** „Meine Organisationen" via `/mine` für **alle**; System-ADMIN
   sieht zusätzlich Create-Formular + „Alle Organisationen" (`listAll`). Der globale
   Sidebar-Link existiert bereits.

## 4. Umsetzung

### 4.1 API-Client & Typen

`api/organizations.ts` (breaking + additive):

- `OrganizationMember` → Shape auf `{ user: UserResponse; role: OrgRole }` ändern
  (Endpoint liefert jetzt so). `UserResponse` aus den bestehenden Auth-Typen
  wiederverwenden; `OrgRole = 'OWNER' | 'ADMIN' | 'MEMBER'`.
- **Neu:** `changeMemberRole(orgId, userId, role)` →
  `PATCH /organizations/{id}/members/{userId}` mit `{ role }`.
- Erhalten: `listMine()`, `listAll()`, `getById()`, `create()`, `addMember()`,
  `removeMember()`, `switchOrg()` (unangetastet).

Projekt-API + Projekt-Typ:

- **Neu:** `setOrganization(key, orgId: string | null)` →
  `PATCH /projects/{key}/organization` mit `{ orgId }`.
- `Project`/`ProjectResponse`-Typ um `orgId: string | null` ergänzen.

### 4.2 `OrgsPage` — Zugang öffnen

- Harte „You do not have permission"-Wand **entfernen**.
- **Alle** authentifizierten User: Abschnitt „My Organizations" aus `listMine()`;
  jede Zeile verlinkt auf `/orgs/{id}/settings`.
- **System-ADMIN** zusätzlich: Create-Formular (wie heute) + Abschnitt „All
  Organizations" aus `listAll()`. Gating weiterhin über `me?.role === 'ADMIN'`.
- Globaler Sidebar-Link bleibt unverändert (schon für alle sichtbar).

### 4.3 `OrgSettingsPage` — auf MembersPage-Niveau heben

- Member-Liste rendert `{user, role}`: **Displayname + E-Mail** statt roher UUID.
- **Add-Member** via User-Search-Autocomplete (Muster aus `MembersPage.AddMemberForm`,
  `useUserSearch`) + Rollen-`<select>` → `addMember`. Kein UUID-Textfeld mehr.
- **Rollen-Editor** pro Zeile → `changeMemberRole` (PATCH), mit
  `invalidateQueries(['org-members', orgId])`.
- **Schutzregeln** (spiegeln die Autorisierungs-Matrix, §9 des Phase-A-Specs):
  - Eigene Zeile: Rollen-`<select>` und „Remove" **deaktiviert** (Backend 403).
  - OWNER-Zeilen: Rolle ändern / entfernen **deaktiviert**, außer der Betrachter ist
    System-ADMIN (Org-ADMIN darf OWNER nicht anfassen).
  - Add-Formular bietet die Option **OWNER** nur, wenn der Betrachter Org-OWNER
    **oder** System-ADMIN ist.
  - Letzter-OWNER-/Duplikat-Schutz ist serverseitig; im UI 4xx abfangen und als
    Inline-Fehler anzeigen (409 „already a member", sonst generische Meldung).
- **Betrachter-Rolle & Read-only-Gating:** die eigene Org-Rolle aus der Member-Liste
  ableiten (Zeile mit `user.id === me.id`) bzw. System-ADMIN. Ist der Betrachter
  **weder** Org-OWNER/ADMIN **noch** System-ADMIN → **Read-only-Ansicht**:
  Verwaltungs-Controls (Add-Form, Rollen-Select, Remove) ausgeblendet/deaktiviert.
  `getById`/Member-Liste sind für jeden Member lesbar.

### 4.4 Neue Projekt-Settings-Seite `/p/:key/settings/organization`

- Route in `app/router.tsx`; `OrganizationSettingsPage` neu unter
  `pages/projects/settings/`.
- **NavItem** unter Projekt-„Settings" in `AppLayout.tsx`, sichtbar bei
  `currentProject?.myRole === 'ADMIN'` (analog „Members").
- Inhalt:
  - Aktuelle Zuordnung: Org-Name (via `getById(project.orgId)`) oder „Not assigned".
  - **Change/Assign:** Dropdown aus `listMine()`; Auswahl → `setOrganization(key, orgId)`.
    *Bekannte Einschränkung:* `/mine` trägt keine Rolle, das Dropdown kann daher auch
    Orgs listen, in denen der Betrachter nur MEMBER ist; der Backend-403 (verlangt
    Org-OWNER/ADMIN der Ziel-Org) ist der Guard und wird als **Inline-Fehler**
    angezeigt.
  - **Remove:** `setOrganization(key, null)` (nur Projekt-ADMIN; Backend erzwingt).
  - Seiten-Level-Gate: bei `myRole !== 'ADMIN'` „no permission"-Hinweis (wie
    MembersPage).

### 4.5 Vererbungs-Banner auf der Members-Seite

- In `MembersPage.tsx`: wenn `project.orgId != null`, Org-Name laden und ein
  Info-Banner oberhalb der Add-Form zeigen:
  > „This project belongs to **{orgName}**. Its Owners/Admins are admins here;
  > Members are viewers."
- Erklärt, warum Personen Zugriff haben, ohne in der expliziten Liste zu erscheinen.
  Keine Synthese geerbter Member-Zeilen.

### 4.6 Read-only-Gating — kein Code

`GET /projects/{key}.myRole` liefert nach Phase A automatisch die **effektive**
(geerbte) Rolle → `useProjectRole` greift für geerbte VIEWER ohne Zusatzarbeit.
Nur **verifizieren**.

## 5. Verifikation

Kein FE-Test-Framework → `cd frontend && npx tsc --noEmit` (muss sauber sein) +
**manuell gegen den laufenden vollen Stack** (Phase-A-Backend im Worktree):

1. Nicht-System-Org-Admin erreicht „My Organizations", öffnet Org-Settings, fügt via
   Suche Member hinzu, ändert Rollen (Self-/OWNER-Guard greift im UI), entfernt Member.
2. Org-Member (nicht Admin) sieht Org-Settings **read-only**.
3. Projekt-Admin ordnet Projekt einer Org zu und wieder ab (Organization-Settings-Seite).
4. Zuordnung zu einer Org, in der man **nicht** Org-Admin ist → Inline-403-Fehler.
5. Geerbter VIEWER: Projekt read-only (Schreib-Affordances deaktiviert) + Banner sichtbar.
6. System-ADMIN: Create + „All Organizations" weiterhin verfügbar.

## 6. Nicht im Scope

- `OrgSwitcher` / Multi-Tenant / `switch-org` — unangetastet.
- **Keine** neuen Backend-Endpoints, keine DB-Migration.
- **Kein** Auflisten geerbter Member in der Projekt-Mitgliederliste (nur Banner).
- Org-Umbenennung/-Löschung, Einladungs-E-Mails, Org-Billing.

## 7. Betroffene Dateien (Orientierung)

- `frontend/src/api/organizations.ts` — Shape-Fix + `changeMemberRole`.
- `frontend/src/api/*` (Projekt-API) + Projekt-Typ — `setOrganization`, `orgId`.
- `frontend/src/pages/orgs/OrgsPage.tsx` — Zugang „my/all".
- `frontend/src/pages/orgs/OrgSettingsPage.tsx` — Member-Uplift + Guards.
- `frontend/src/pages/projects/settings/OrganizationSettingsPage.tsx` — **neu**.
- `frontend/src/pages/projects/settings/MembersPage.tsx` — Vererbungs-Banner.
- `frontend/src/app/router.tsx` — neue Route.
- `frontend/src/layouts/AppLayout.tsx` — NavItem „Organization" (Projekt-Settings).
- Evtl. kleiner Org-Hook (`useOrgMembers`/`useOrgs`) analog `useProjectMembers`.

## 8. Wiki-Docs (Abschlussaufgabe)

Wie üblich als letzte Task: Org-Verwaltung + Vererbung in den Wiki-Docs beschreiben
(User-Sicht: „Was bedeutet Org-Zugehörigkeit für Projektrechte"). `ai-guide.md` nur
anfassen, falls sich Muster/Flyway ändern (hier nicht).
