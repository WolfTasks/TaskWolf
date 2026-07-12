# Organisationen als Oberkategorie — Projekt-/Member-Zuordnung & Rechte-Vererbung

> Design-Spec, 2026-07-12. Backlog-Eintrag **#14** (siehe
> `2026-07-07-backlog-overview.md`). Nachfolge-Zyklus zu #9 (Projekt-Permissions),
> der bewusst „nur Projekte" scoped hatte, weil Orgs einen eigenen Zyklus bekommen.

## 1. Ziel

Organisationen werden zur **Oberkategorie** über Projekten und Nutzern. Einer Org
lassen sich **Projekte** und **Member** zuordnen. Rechte werden von der Org auf ihre
Projekte **vererbt**: Org-Admins sind automatisch Projekt-Admins, andere Ebenen
analog. Ziel ist, dass Rechteverwaltung zentral auf Org-Ebene möglich wird, ohne die
bestehende feingranulare Projekt-Rechteverwaltung (#9) abzuschaffen.

## 2. Ist-Zustand (erkundet 2026-07-12)

- **`organizations`-Modul** existiert: `Organization` (name/slug),
  `OrgRole {OWNER, ADMIN, MEMBER}`, `OrganizationMember` (embedded id org+user),
  `OrganizationService` (Member-CRUD ohne Rollen-Ändern), `OrganizationContextFilter`
  (setzt Multi-Tenant-Kontext aus JWT-`orgId`). **Alle** Org-Endpoints sind heute nur
  für System-`ADMIN` offen (`@PreAuthorize("hasRole('ADMIN')")`).
- **`Project` besitzt bereits `orgId: UUID?`** — die Spalte für die Projekt→Org-
  Zuordnung existiert, wird beim Anlegen aber **nicht gesetzt** und **nirgends
  ausgewertet**.
- **Projekt-Permissions (#9)**: `ProjectRole {ADMIN, MEMBER, VIEWER}`,
  `ProjectService.roleOf/canWrite/isProjectAdmin`, Durchsetzung via
  `@PreAuthorize("@projectSecurity.canWrite/isProjectAdmin")`. **`roleOf` schaut heute
  nur auf `project.owner` + `project_members` — Org-Mitgliedschaft wird komplett
  ignoriert.**
- **Frontend Org-UI (Prototyp-Niveau)**: `OrgsPage` (System-Admin-only, Create/List),
  `OrgSettingsPage` (Member per **roher UUID**-Eingabe hinzufügen/entfernen, Liste
  zeigt rohe UUIDs, **kein** Rollen-Ändern, kein Owner-/Self-Schutz), `OrgSwitcher`
  (Multi-Tenant-JWT-Switch), `api/organizations.ts`. **Keine** Projekt↔Org-
  Zuordnungs-UI.
- **Aus #9 bereits vorhanden & wiederzuverwenden**: `GET /api/v1/users/search`
  (Autocomplete, liefert `{id, email, displayName}`), Projekt-`GET members`-Shape
  `{user, role}`, `MembersPage`-Muster (Owner-/Self-Schutz, Rollen-Editor).

**Kernlücke:** Org- und Projekt-Rechte sind völlig entkoppelt. Ein Org-Admin hat
aktuell keinerlei Projektrechte, außer er ist separat als Projekt-Member eingetragen.

## 3. Entscheidungen (Brainstorming)

1. **Vererbungsmodell**: Alle Ebenen vererben auf **alle** Projekte der Org (additives
   Maximum). Org-Member sehen automatisch alle Org-Projekte; explizite
   Projekt-Mitgliedschaft kann die effektive Rolle nur **anheben**.
2. **Rollen-Mapping**: `OrgOWNER/ADMIN → ProjectRole.ADMIN`, `OrgMEMBER →
   ProjectRole.VIEWER` (konservativer Default; Schreibrechte werden pro Projekt
   explizit vergeben).
3. **Projekt↔Org-Zuordnung**: **optional** — Projekte ohne Org (`orgId=null`) bleiben
   erlaubt und verhalten sich exakt wie heute (keine Vererbung). **Keine Migration.**
4. **Org-Verwaltung**: OrgOWNER/ADMIN verwalten ihre **eigene** Org (Member, Rollen,
   Projekt-Zuordnung); System-ADMIN darf weiterhin alles.

## 4. Kernmechanik — effektive Rolle (Ansatz A)

Gewählter Ansatz: **effektive Rolle zur Lesezeit berechnen** (statt vererbte
Mitgliedschaften zu materialisieren oder einen separaten Policy-Service einzuführen).

Rollen-Ordnung: `VIEWER (0) < MEMBER (1) < ADMIN (2)`.

```
effectiveRole(project, userId) =
    max(
        ownerAdmin,                    // project.owner.id == userId → ADMIN, sonst entfällt
        explicitProjectRole,           // project_members-Zeile, falls vorhanden
        inheritedOrgRole               // nur falls project.orgId != null:
                                       //   OWNER/ADMIN → ADMIN, MEMBER → VIEWER
    )
```

- Liefern alle drei Quellen nichts (kein Owner, keine Projekt-Zeile, keine
  Org-Mitgliedschaft bzw. `orgId=null`) → **kein Zugriff** (wie heute `null`).
- Ist `project.orgId == null`, entfällt der Org-Summand → **exakt heutiges Verhalten**.
- Abgeleitet **aus `OrganizationMember`-Zeilen**, **unabhängig** vom JWT-„aktive-Org"-
  Switch (`switchOrg`). Berechtigung folgt tatsächlicher Mitgliedschaft, nicht der
  transienten Org-Auswahl.

Da `canWrite`, `isProjectAdmin` und alle `@PreAuthorize`-Checks über `roleOf`
laufen (bzw. laufen sollen — siehe §5), erbt die **gesamte bestehende Durchsetzung**
(27+ Endpoints aus #9) die Vererbung automatisch, ohne einzeln angefasst zu werden.

**Cross-Modul-Abhängigkeit:** `projects` braucht einen Lese-Zugriff auf
Org-Mitgliedschaften. Umsetzung als schmale Port-Schnittstelle, im
`organizations`-Modul implementiert, ins `projects`-Modul injiziert:

```kotlin
// im projects-Modul (oder core) definiert, in organizations implementiert
interface OrgMembershipLookup {
    fun roleOf(orgId: UUID, userId: UUID): OrgRole?
}
```

Sauber mockbar (MockK), Abhängigkeitsrichtung klar (projects → port ← organizations).
`ProjectService` bekommt das Port injiziert (analog zur bestehenden
`UserRepository`-Injektion).

## 5. Backend (Phase A)

1. **`OrgMembershipLookup`-Port** + Implementierung im `organizations`-Modul (dünner
   Wrapper um `OrganizationMemberRepository`).
2. **`roleOf` erweitern** um den Org-Erbe-Summanden (Maximum-Bildung wie §4). Ein
   privates Mapping `OrgRole → ProjectRole` (OWNER/ADMIN→ADMIN, MEMBER→VIEWER).
3. **`isProjectAdmin` aus effektiver Rolle ableiten** (`effectiveRole == ADMIN`) statt
   die Logik zu duplizieren — **eine** Wahrheitsquelle. `canWrite` bleibt
   `effectiveRole != VIEWER` (funktioniert dann automatisch mit Erbe).
4. **`findAllForUser`** ergänzen: zusätzlich zu `findAllByMemberOrOwner` auch Projekte,
   deren `orgId` in den Orgs des Users liegt (Repository-Union, Duplikate entfernen).
   Sonst „sieht" ein reiner Org-Member seine geerbten Projekte nicht in der Liste.
5. **Projekt↔Org-Zuordnung** — neuer Endpoint `PATCH /api/v1/projects/{key}/organization`
   mit Body `{ orgId: UUID | null }`:
   - **Setzen/Ändern** (`orgId != null`): Actor muss **Projekt-ADMIN** (effektiv) **und**
     **OrgOWNER/ADMIN der Ziel-Org** sein. Verhindert „Projekt in fremde Org kippen"
     und „Org-Admin greift beliebige Projekte ab".
   - **Entfernen** (`orgId = null`): Actor muss **Projekt-ADMIN** sein.
   - System-ADMIN darf beides.
6. **`OrgSecurity`-Bean + Org-Verwaltungs-Autorisierung** (analog `ProjectSecurity`):
   `isOrgAdmin(orgId, auth)` = OrgOWNER/ADMIN **oder** System-ADMIN.
   - Bestehende `@PreAuthorize("hasRole('ADMIN')")` auf `addMember`/`removeMember`/
     `POST /{id}/members` **lockern** auf `@orgSecurity.isOrgAdmin(#id, authentication)`.
   - `create`/`listAll`/`delete` (Orgs anlegen/global listen) bleiben **System-ADMIN**.
   - Neuer Endpoint `PATCH /api/v1/organizations/{id}/members/{userId}` (Rolle ändern).
   - **Schutzregeln** (spiegeln #9/B1): eigene Rolle nicht selbst änderbar (403);
     OWNER-Rolle nicht durch ADMIN änder-/entfernbar; letzter OWNER bleibt geschützt
     (kein „org ohne Owner").
7. **Org-`GET /{id}/members` liefert `{user, role}`-Shape** (Displayname/E-Mail) statt
   roher UUID; Add-Flow nutzt `/users/search`. (Verbesserung des Prototyps im Vorbeigehen.)
8. **Fehlerbehandlung**: unbekannte OrgRole/ungültige Body-Werte → 400 (bestehender
   `HttpMessageNotReadableException`→400-Handler aus #9 greift); Zugriff verweigert → 403.

**Tests (MockK-Unit + Integration, TDD):**
- `roleOf`-Maximum-Matrix: alle Kombinationen aus {kein/Owner} × {keine/VIEWER/MEMBER/
  ADMIN Projekt-Zeile} × {keine/MEMBER/ADMIN/OWNER Org} → erwartete effektive Rolle.
- `orgId=null` → unverändertes Verhalten (Regression).
- Integration: geerbter VIEWER kann lesen aber **nicht** schreiben (PATCH issue → 403);
  geerbter Org-ADMIN kann Projekt-Admin-Aktionen (Member verwalten); explizite
  Projekt-ADMIN-Rolle hebt geerbten VIEWER an.
- Projekt↔Org-Endpoint: alle vier Autorisierungspfade (Projekt-ADMIN+OrgADMIN ok;
  nur eins → 403; entfernen als Projekt-ADMIN ok; System-ADMIN ok).
- Org-Verwaltung: OrgADMIN darf Member managen; Fremd-Org → 403; Self-/Owner-/
  Last-Owner-Schutz.

## 6. Frontend (Phase B)

1. **Zugang**: `OrgsPage` „meine Orgs" für Org-Admins (nicht nur System-ADMIN);
   `OrgSettingsPage` für OrgOWNER/ADMIN erreichbar. (System-ADMIN behält globale Sicht.)
2. **Member-Verwaltung** in `OrgSettingsPage` auf `MembersPage`-Niveau (#9) heben:
   Member-Add via **User-Suche** (`/users/search`) statt UUID-Textfeld; **Rollen-Editor**;
   Owner-/Self-Schutz (eigenes/OWNER-`<select>` deaktiviert); Liste zeigt Displayname.
3. **Projekt↔Org-Zuordnungs-UI** in den Projekt-Settings: Org auswählen/entfernen (nur
   für Actor mit Projekt-ADMIN + OrgADMIN sichtbar/aktiv).
4. **Geerbte vs. explizite Rolle** in der Projekt-Mitgliederliste kenntlich machen
   (Badge „geerbt aus Org"), damit Admins verstehen, warum jemand Zugriff hat.
5. **Read-only-Gating „funktioniert einfach"**: `GET /projects/{key}.myRole` reflektiert
   nach §5 automatisch die effektive Rolle → das bestehende `useProjectRole`-Gating
   greift für geerbte VIEWer ohne Zusatzarbeit.

Verifikation: `cd frontend && npx tsc --noEmit` (kein FE-Test-Framework) + manuell.

## 7. Nicht im Scope

- **Multi-Tenant-Datenisolation** und `switch-org`/`OrganizationContextFilter` bleiben
  unangetastet — orthogonal zu diesem Feature (Vererbung basiert auf Mitgliedschaft,
  nicht auf aktiver JWT-Org).
- **Keine** neuen OrgRole-Werte, **keine** DB-Migration (orgId bleibt nullable).
- Org-Löschung/Umbenennung, Org-weite Einladungs-E-Mails, Org-Billing o.ä.

## 8. Phasing

Wie bei #9: **Phase A (Backend)** und **Phase B (Frontend)** — je eigener
Implementierungsplan und eigene Session (ein Phase pro Session).

## 9. Autorisierungs-Matrix (Referenz)

| Aktion | Erlaubt für |
|--------|-------------|
| Org anlegen / global listen / löschen | System-ADMIN |
| Org-Member hinzufügen/entfernen/Rolle ändern | OrgOWNER/ADMIN der Org, System-ADMIN |
| eigene Org-Rolle ändern | niemand (403) |
| OWNER-Rolle ändern/entfernen | nur höher (System-ADMIN); letzter OWNER geschützt |
| Projekt einer Org zuordnen/ändern | Projekt-ADMIN **und** OrgOWNER/ADMIN der Ziel-Org (oder System-ADMIN) |
| Projekt aus Org lösen (orgId=null) | Projekt-ADMIN (oder System-ADMIN) |
| Projekt lesen | effektive Rolle ≥ VIEWER (inkl. Org-Erbe) |
| Projekt schreiben | effektive Rolle ≥ MEMBER |
| Projekt-Admin-Aktionen | effektive Rolle == ADMIN (inkl. Org-Erbe) |
