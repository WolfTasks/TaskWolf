# Spec: Projekt-Permissions-Fixes — Self-Role-Change (B1) + Read-only-Enforcement (B2)

> Design 2026-07-11. Backlog: `2026-07-07-backlog-overview.md` B1 + B2.
> Backend + kleine Frontend-Anteile. Baut auf #9 (Projekt-Permissions,
> `2026-07-11-project-permissions-design.md`, Release v1.0.10) auf.

## Kontext & Problem

Zwei gemeldete Sicherheits-Bugs (2026-07-11) an der in #9 eingeführten
Projekt-Rollen-/Rechteverwaltung (`VIEWER` / `MEMBER` / `ADMIN`):

- **B1 — Selbst-Rollenänderung:** Ein Nutzer kann seine **eigene** Rolle ändern.
  `ProjectService.changeMemberRole` prüft nur `requireAdmin(actor)`, aber **nicht**
  `actorId == targetUserId`. Ein Projekt-Admin kann sich damit selbst
  ummünzen (z.B. versehentlich aus der Admin-Rolle entfernen). Im Frontend ist
  das Rollen-`<select>` nur für die *Owner*-Zeile deaktiviert, nicht für die
  eigene Zeile.
- **B2 — Read-only greift nicht:** Das Write-Enforcement ist **opt-in pro
  Endpoint** über `@PreAuthorize("@projectSecurity.canWrite(#key,
  authentication)")`. Mehrere schreibende Controller haben diese Annotation nie
  bekommen und rufen im Service nur `requireMember` — dadurch kann ein **VIEWER
  dort schreiben**.

### B2 — vollständiges Audit (Stand 2026-07-11)

Alle projekt-scoped Controller (`/api/v1/projects/{key}/**`) mit
schreibenden Mappings geprüft. **Ungeschützte Löcher** (nur `requireMember`,
kein `@PreAuthorize`):

| Controller | Ungeschützte Writes |
|---|---|
| `WorkflowEditorController` | 7 — statuses (POST/PUT/DELETE), transitions (POST/DELETE), guards (PUT), layout (PUT) |
| `AutomationController` | 4 — create/update/delete/toggle rule |
| `DashboardController` | 3 — saveLayout (PUT), addWidget (POST), removeWidget (DELETE) |
| `WebhookController` | 4 — create/update/delete/testPing |
| `ApiKeyController` | 2 — create/revoke (Projekt-API-Keys) |
| `ProjectIntegrationController` | 2 — create/delete |

**Bewusste Nicht-Löcher (dürfen NICHT verändert werden):**
- `ServiceDeskController.submitTicket` (`POST /tickets`) ist **absichtlich offen**
  (öffentliche/Kunden-Ticket-Einreichung) → **kein** `canWrite` ergänzen.
- `ProjectMemberController` (add/changeRole/remove) ist bereits admin-gated via
  `requireAdmin` im Service → VIEWER/MEMBER geblockt.

**Bereits korrekt geschützt** (Referenz, nicht anfassen): Issue, Board, Comment,
Label, Sprint, Version, CustomField, Attachment, Incident, ServiceDesk
enable/sla-policies/escalation (isProjectAdmin).

## Design

### B1 — Selbst-Rollenänderung unterbinden

**Backend** — `ProjectService.changeMemberRole`:

```kotlin
val project = requireAdmin(projectKey, actorId)
if (actorId == targetUserId) throw ForbiddenException("You cannot change your own role")
if (project.owner.id == targetUserId) throw ForbiddenException("Cannot change the project owner's role")
...
```

Eine Rollenänderung muss von einem *anderen* Admin ausgehen.

**Frontend** — `MembersPage.tsx`: Rollen-`<select>` (und optional der
„Remove“-Button-Kontext) für die **eigene** Zeile deaktivieren
(`user.id === me.id`), mit dezentem „You“-Hinweis. Aktuelle User-ID über die
bestehende `['me']`-Query (`authApi.me()`).

**Abgrenzung:** B1 betrifft nur die *Rollenänderung*. Das *Selbst-Entfernen*
(`removeMember` der eigenen Mitgliedschaft = ein Projekt verlassen) bleibt
erlaubt und wird hier nicht geändert.

### B2 — Read-only-Löcher schließen (zwei Guard-Stufen)

Guard-Stufen nach Sensitivität (Entscheid 2026-07-11: „Two tiers“):

**Tier 1 — Inhalt/Config → `canWrite` (MEMBER+):**
- `WorkflowEditorController` (alle 7 Writes)
- `AutomationController` (alle 4 Writes)
- `DashboardController` (alle 3 Writes)

**Tier 2 — sensible Infrastruktur → `isProjectAdmin` (nur ADMIN):**
- `ApiKeyController` (create/revoke)
- `WebhookController` (create/update/delete/testPing)
- `ProjectIntegrationController` (create/delete)

Umsetzung: Methoden-`@PreAuthorize` analog zu den bereits geschützten
Controllern:
- Tier 1: `@PreAuthorize("@projectSecurity.canWrite(#key, authentication)")`
- Tier 2: `@PreAuthorize("@projectSecurity.isProjectAdmin(#key, authentication)")`

Beide Ausdrücke existieren bereits (`ProjectSecurity.canWrite` /
`ProjectSecurity.isProjectAdmin`). `#key` ist der Pfad-Parametername in allen
sechs Controllern.

**Regressions-Verhalten Tier 2:** Ein MEMBER, der bisher Webhooks/API-Keys/
Integrationen anlegen konnte, kann das danach **nicht mehr** — bewusste
Verschärfung (Zugang zu externen/programmatischen Credentials).

### Frontend (leicht, Defense-in-Depth)

Wo Edit-Affordances (Buttons/Formulare) für Workflow-Editor, Automation,
Dashboard-Widgets, Webhooks, API-Keys, Integrationen für **VIEWER** (bzw. bei
Tier 2 für Nicht-Admins) noch sichtbar sind: ausblenden/disablen anhand
`myRole`. Server bleibt die maßgebliche Durchsetzung; UI reduziert nur tote
Klicks. Umfang: nur die tatsächlich betroffenen Seiten prüfen und angleichen.

## Testing / Verifikation

Backend: JUnit + MockK, real-Postgres-Integrationstests (vgl. #9,
`IntegrationTestBase`).

- **B1:** Integrationstest — ein (Nicht-Owner-)**Admin** PATCH auf die *eigene*
  Rolle → **403**. Positiv-Kontrolle: Admin ändert die Rolle eines *anderen*
  Members → 200.
- **B2 — Regressions-Guard-Test** (erweitert `ProjectWriteEnforcementIntegrationTest`):
  - **Tier 1:** VIEWER auf je einem repräsentativen Write pro Controller
    (create status, create transition, save layout, create automation rule, add
    dashboard widget) → **403**; MEMBER auf denselben → erlaubt (2xx).
  - **Tier 2:** VIEWER **und** MEMBER auf create webhook / create API key /
    create integration → **403**; ADMIN/Owner → erlaubt (2xx).
  - **Negativ-Kontrolle:** `POST /service-desk/tickets` bleibt für VIEWER
    erreichbar (nicht versehentlich mitgeschützt).
- Gesamte Backend-Suite grün (`./gradlew test`).

Frontend: `tsc`-Typecheck grün + Wolfgangs manuelle Verifikation der drei
Rollen-Flows (VIEWER kann nichts schreiben, MEMBER Inhalte aber keine
Infra-Configs, Admin alles; eigene Rolle nicht editierbar).

## Reihenfolge / Abhängigkeiten

B1 und B2 sind unabhängig, teilen aber Testdatei/Modul. Empfohlen: in einem
Backend-Zyklus umsetzen (erst B2-Audit+Guards+Test, dann B1), Frontend-Anteile
am Ende. Eigener isolierter Worktree (Wolfgangs Standard).
