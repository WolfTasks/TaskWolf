# Backlog-Überblick (Ideensammlung 2026-07-07)

> Diese Notiz hält die noch **nicht** designten Vorhaben fest. Jedes bekommt bei
> Bearbeitung seinen eigenen Brainstorming-→Spec-→Plan-Zyklus.

| # | Vorhaben | Typ | Status |
|---|----------|-----|--------|
| 1 | App-Version anzeigen | UI | ✅ **AUSGELIEFERT** (PR #43, Release v1.0.07) |
| 2 | Personal Access Tokens + User-Lebenszyklus | Full-Stack | ✅ **AUSGELIEFERT** (PR #44, Release v1.0.07) |
| 3 | User-Profil-Seiten (gruppierte Einstellungen) | Feature | ✅ **AUSGELIEFERT** (PR #47, Release v1.0.09) |
| 4 | Branches aufräumen | Housekeeping | Backlog (kein Spec nötig) |
| 5 | UI-Tests | Test-Infra | Backlog |
| 6 | Test-Deploy bei Selfhoster mit eigener URL | Ops | Backlog |
| 7 | Scrollbare Listen (z.B. Audit-Log) | UI | ✅ **AUSGELIEFERT** (PR #45, Release v1.0.08) |
| 8 | Linkes Menü zusammenklappbar | UI | ✅ **AUSGELIEFERT** (PR #46, Release v1.0.08) |
| 9 | User-Rechte-Verwaltung (Projekt-Freischaltung + Rollen) | Full-Stack | ✅ **AUSGELIEFERT** (PR #52, Release v1.0.10) |
| 10 | Sidebar-Gruppen einzeln zusammenklappbar (Admin/Project/…) | UI | ✅ **AUSGELIEFERT** (PR #54, Release v1.0.11) |
| 11 | Layout-Fix: linkes Menü darf sich nicht mit Seiteninhalt strecken | UI/Bug | ✅ **AUSGELIEFERT** (PR #48, Release v1.0.10) |
| 12 | Dependabot-Alerts beheben (5 offen) | Ops/Security | ✅ **AUSGELIEFERT** (PR #49, alle Alerts bereinigt, Release v1.0.10) |
| 13 | Internationalisierung (UI in mehreren Sprachen) | Full-Stack/UI | 🔀 **PR #57 offen** (Fundament + Pilot-Slice, nicht gemergt) |
| 14 | Organisationen als Oberkategorie (Projekt-/Member-Zuordnung + Rechte-Vererbung) | Full-Stack | ✅ **AUSGELIEFERT** (Backend PR #55, Frontend PR #56, Release v1.0.12) |
| H1 | nginx `index.html` no-cache Härtung | Ops/Hardening | ✅ **AUSGELIEFERT** (PR #51, Release v1.0.10) |
| H2 | Notification-Prefs PUT: unbekannter Typ → 400 leakt Enum-Namen | Hardening | ✅ **AUSGELIEFERT** (PR #50, Release v1.0.10) |
| H3 | `changePassword`: `newPassword` erlaubt reine Leerzeichen | Hardening | ✅ **AUSGELIEFERT** (PR #50, Release v1.0.10) |
| B1 | User können ihre eigene Rolle ändern | Bug | ✅ **AUSGELIEFERT** (PR #53, Release v1.0.11) |
| B2 | Read-only greift nicht: User können Tickets trotz Read-only ändern | Bug | ⏸️ Zurückgestellt — bereits umgesetzt (client `useProjectRole`+server 403); nur Regressionstest offen |
| B3 | Logout-Button verschwindet, wenn das Menü länger als der Bildschirm ist | Bug/UI | ✅ **AUSGELIEFERT** (PR #54, Release v1.0.11) |

## #3 — User-Profil-Seiten mit gruppierten Einstellungen
Eigene Profil-/Einstellungsseiten pro Nutzer, gruppiert nach Themengebieten
(z.B. Profil, Sicherheit, Benachrichtigungen, Access Tokens, Konto).
**Berührungspunkt:** Baut auf dem in #2 angelegten Account-Settings-Bereich
(`/settings/*`) auf — #2 zuerst, dann #3 als Ausbau.

## #4 — Branches aufräumen
Gemergte/verwaiste Branches identifizieren und löschen (lokal + remote).
Reines Housekeeping, kann direkt ausgeführt werden (kein Design). Vorsicht bei
nicht-gemergten Branches — vor dem Löschen bestätigen lassen.

## #5 — UI-Tests
Das Frontend hat aktuell **kein** Test-Framework (nur `tsc`-Typecheck + manuell).
Vorhaben: Test-Infrastruktur einführen (z.B. Vitest + Testing Library, ggf.
Playwright für E2E) und erste kritische Flows abdecken. Eigener Design-Zyklus:
Framework-Wahl, Scope, CI-Integration.

## #6 — Test-Deploy bei einem Selfhoster mit eigener URL
Test-Instanz bei einem Self-Hosting-Anbieter deployen, erreichbar unter eigener
URL. **Offene Voraussetzung:** Zielanbieter/Server unklar. Vorhandene
Docker-Images können gezogen werden (Hinweis: `frontend/` hat kein
`.dockerignore` → lokaler Build problematisch, stattdessen Images pullen).
Eigener Ops-Zyklus, sobald Zielumgebung feststeht.

## #7 — Scrollbare Listen (z.B. Audit-Log)
Lange Listen sollen innerhalb eines begrenzten Bereichs scrollbar sein, statt die
ganze Seite zu strecken. Beispiel: Audit-Log (`AuditLogPage`), aber auch andere
lange Tabellen/Feeds. Idee: fester, scrollbarer Container mit sticky
Tabellenkopf; ggf. Konsistenz über eine wiederverwendbare Scroll-Container- bzw.
Tabellen-Komponente. Kleiner UI-Zyklus (Scope: welche Listen, Sticky-Header,
optional Virtualisierung bei sehr vielen Zeilen).

## #8 — Linkes Menü zusammenklappbar
Die linke Sidebar (`AppLayout`) soll ein-/ausklappbar sein, um horizontalen Platz
zu sparen (eingeklappt nur Icons/schmal, ausgeklappt wie heute). Zustand pro
Nutzer persistieren (z.B. `localStorage`). Kleiner UI-Zyklus (Collapse-Toggle,
Icon-Only-Modus, responsive Verhalten).

## #9 — User-Rechte-Verwaltung (Projekt-/Org-Freischaltung + Rollen)
> ✅ **AUSGELIEFERT** (2026-07-11, PR #52 squash `73d510c` — Backend Phase A +
> Frontend Phase B/C; Release v1.0.10). **Scope-Entscheid: nur Projekte** (Orgs haben bereits
> Member-Management → eigener Folge-Zyklus). Rollen `VIEWER/MEMBER/ADMIN` (bestehender
> Enum, keine Migration); read-only server- **und** clientseitig durchgesetzt.
> Spec: `2026-07-11-project-permissions-design.md` · Pläne:
> `../plans/2026-07-11-project-permissions-phase-a-backend.md`,
> `../plans/2026-07-11-project-permissions-phase-bc-frontend.md`. Offen: Wolfgangs
> manuelle Browser-Verifikation der drei Rollen-Flows.

UI + Backend, um Nutzer gezielt für **Projekte** und **Organisationen**
freizuschalten und ihnen Rollen zuzuweisen: **Read-only / Read & Write / Admin**.
**Abgrenzung/Berührungspunkte:**
- #2 hat nur den *system-weiten* Lebenszyklus + system-`ADMIN`/`MEMBER` sowie
  persönliche Tokens geliefert — **nicht** die per-Projekt/-Org-Zuweisung.
- Backend hat bereits Bausteine: `ProjectMember` + `ProjectRole` (aktuell
  `ADMIN`/…), Org-Membership (`organizations`-Modul). Zu klären: ob eine
  dedizierte Read-only-Projektrolle nötig ist (heute gibt es keine reine
  Lese-Projektrolle) und wie das mit den PAT-Scopes (`READ_ONLY`/`READ_WRITE`)
  zusammenspielt.
- Eigener Full-Stack-Design-Zyklus: Rollenmodell (evtl. neue `ProjectRole`-Werte),
  Einladungs-/Freischaltungs-Flow, Member-Management-UI pro Projekt/Org,
  Berechtigungsprüfungen serverseitig.
- Verwandt mit #3 (Profil-/Settings-Bereich) und der Admin-Users-Seite aus #2.

## #10 — Sidebar-Gruppen einzeln zusammenklappbar (Admin/Project/…)
Aufbauend auf #8 (Sidebar als Ganzes einklappbar → Icon-Rail): Im **ausgeklappten**
Zustand sollen die einzelnen Sektionsgruppen der linken Sidebar (`AppLayout.tsx`:
**Admin**, **Project**, **Settings**, **Account**, ggf. auch die Top-Ebene) je einen
eigenen Collapse-Toggle bekommen (Chevron am Sektions-Header via `sectionLabel`),
um vertikal Platz zu sparen. Zustand pro Gruppe persistieren (localStorage, analog
zum `useSidebarCollapsed`-Muster aus #8). Kleiner UI-Zyklus (Toggle je Gruppe,
Persistenz, sinnvolles Default = alle offen; im eingeklappten Icon-Rail-Modus
entfällt das Feature bzw. Gruppen bleiben immer sichtbar).

## #11 — Layout-Fix: linkes Menü darf sich nicht mit Seiteninhalt strecken
**Problem:** Auditlog und ähnliche Seiten strecken den Inhalt so weit, dass die
**ganze Seite** vertikal wächst und damit die linke Sidebar (`<aside>`) mitwächst —
der Logout-Button rutscht unter den sichtbaren Bereich, man muss die gesamte Seite
scrollen, um ihn zu erreichen. Ursache: In `AppLayout.tsx` ist die Shell
`min-h-screen flex`; wenn `<main>` höher als der Viewport wird, wächst die
Flex-Zeile und der `<aside>` (align-items: stretch) streckt sich mit.
**Soll:** Die linke Sidebar bleibt **fixe Viewport-Höhe**, Logout ist immer
erreichbar (unten gepinnt, `mt-auto` existiert bereits); der **rechte** Inhalt wird
intern scrollbar statt die Seite zu verlängern. **Fix-Ansatz:** Shell auf
`h-screen overflow-hidden` (statt `min-h-screen`), `<main>` behält `overflow-auto`
→ Inhalt scrollt innerhalb `<main>`, `<aside>` kann sich nicht mehr strecken.
Betrifft alle langen Seiten (auch die, die #7/DataTable noch nicht nutzen, z.B.
lange Formulare). Verwandt mit #7 (DataTable-Scroll) und dem dortigen Hinweis
„`overflow-hidden` auf `<main>`". Kleiner, aber sorgfältig zu testender UI-Fix
(alle Seiten auf internes Scrollen prüfen, keine Doppel-Scrollbar).

## #12 — Dependabot-Alerts beheben ✅ ERLEDIGT (PR #49, 2026-07-10)
**Ergebnis (0 offene Alerts):** logback-core & commons-compress via BOM-Override/
Constraint gebumpt (`backend/build.gradle.kts` + Lockfile) → **#79, #1, #2 nach
Merge automatisch geschlossen**. **#31** (commons-lang3, war bereits 3.18.0 im
Lockfile) als *inaccurate* dismissed. **#78** (jackson-databind, kein installierbarer
Patch) als *tolerable_risk* dismissed (Defer bis Spring-Boot-BOM 2.21.5 trägt, vgl.
Issue #33). Zusätzlich in PR #48 mitgelöst: Trivy-HIGH-Gate im Frontend-Image durch
`c-ares`-Bump (CVE-2026-33630) im Dockerfile. Details siehe Spec/Plan `2026-07-10-*`.

<details><summary>Ausgangslage (Stand 2026-07-09)</summary>

GitHub meldet offene Dependabot-Alerts auf `WolfTasks/TaskWolf` (alle transitiv über
`backend/settings.gradle.kts` / Spring-Boot-BOM). Abrufen immer mit
`gh api --paginate /repos/WolfTasks/TaskWolf/dependabot/alerts?state=open`:
- **#79 (low)** `ch.qos.logback:logback-core` < 1.5.35 → fixed **1.5.35** (Object Injection via HardenedObjectInputStream).
- **#78 (medium)** `com.fasterxml.jackson.core:jackson-databind` ≥ 2.19.0, < 2.21.5 → **noch kein Patch verfügbar** (`first_patched_version: null`); case-insensitive Deserialisierung umgeht `@JsonIgnoreProperties`. → vorerst nur **beobachten/deferren**, bis Upstream-Fix im akzeptierten Range erscheint (vgl. früheres Jackson-Defer, Issue #33).
- **#31 (medium)** `org.apache.commons:commons-lang3` < 3.18.0 → fixed **3.18.0** (Uncontrolled Recursion). Hinweis: für Code-Scanning wurde 3.18.0 schon per PR #32 adressiert — hier ggf. die tatsächliche Gradle-Version prüfen/anheben.
- **#1 & #2 (medium)** `org.apache.commons:commons-compress` < 1.26.0 → fixed **1.26.0** (DoS: Infinite-Loop bei DUMP-Datei / OOM bei Pack200).
**Ansatz:** Wo Fix verfügbar (logback, commons-lang3, commons-compress) via
Gradle-Versions-Constraints/-Overrides oder passendem Spring-Boot-Patch-Bump
anheben, Lockfile committen, CI-Security-Gates grün ziehen; jackson-databind
deferren bis Patch verfügbar. Ops/Security-Zyklus.
</details>

## #13 — Internationalisierung (UI in mehreren Sprachen)
> 🔀 **PR #57 offen** (2026-07-12, Branch `worktree-worktree-i18n-foundation`, nicht
> gemergt). Scope-Entscheid: **Fundament + Pilot-Slice** (nicht flächendeckend).
> Framework `react-i18next`; Sprachen `en`/`de`, **Fallback `en`**; Persistenz
> localStorage **+** Backend-User-Preference; **Backend-Texte nur Client-seitig**
> übersetzt (Spring `MessageSource` = Folge-Zyklus); Pilot = Nav-Chrome + Auth +
> Kern-Settings inkl. Sprachumschalter. Backend: Migration V30 (nullable
> `users.language`), `PATCH /api/v1/me/language` (en/de-Validierung → 400 generisch).
> SDD subagent-driven, finaler Whole-Branch-Review = merge-ready (keine Critical/
> Important). Spec: `2026-07-12-i18n-foundation-design.md` · Plan:
> `../plans/2026-07-12-i18n-foundation.md`. **Offen:** Wolfgangs manuelle
> Browser-Verifikation; Merge/Release. **Folge-Zyklen:** restliche Seiten,
> `MessageSource`, voller Intl-Zeit-Rollout (Feeds/Activity), weitere Sprachen.

Die Oberfläche soll in mehreren Sprachen nutzbar sein (mind. Deutsch + Englisch,
erweiterbar). Aktuell sind UI-Texte **hart im Frontend verdrahtet** (gemischt
DE/EN) und es gibt keine i18n-Infrastruktur.
**Umfang / zu klärende Punkte:**
- **i18n-Framework** im React-Frontend einführen (z.B. `react-i18next` /
  `i18next`): Übersetzungs-Ressourcen (JSON pro Locale), `t()`-Aufrufe,
  Namespaces/Struktur.
- **Bestandsaufnahme + Extraktion:** hart kodierte Strings identifizieren und in
  Ressourcen-Dateien überführen (schrittweise pro Seite/Feature möglich).
- **Sprachumschalter** in der UI (z.B. im Profil/Settings-Bereich aus #3) plus
  **Persistenz** der Sprachwahl (localStorage und/oder User-Preference im Backend
  → Berührungspunkt zu #3/User-Profil).
- **Locale-Erkennung** (Browser-`Accept-Language`) als sinnvoller Default.
- **Formatierung** von Datum/Zeit/Zahlen locale-abhängig (`Intl`), inkl. der
  bestehenden relativen Zeiten in Feeds/Activity.
- **Backend-seitige Texte** prüfen: Validierungs-/Fehlermeldungen und
  Notification-Texte sind heute serverseitig fix (meist EN) → entscheiden, ob
  diese ebenfalls lokalisiert werden (Spring `MessageSource`) oder ob nur die
  Client-Präsentation übersetzt wird.
- **Pluralisierung** und Platzhalter/Interpolation berücksichtigen.
Eigener Full-Stack/UI-Design-Zyklus: Framework-Wahl, Ressourcen-Struktur,
Umschalter+Persistenz, Roll-out-Reihenfolge (welche Seiten zuerst). Verwandt mit
#3 (Settings-Bereich für den Sprachumschalter) und #5 (UI-Tests sollten mit i18n
umgehen können, statt auf feste Strings zu prüfen).

## #14 — Organisationen als Oberkategorie (Projekt-/Member-Zuordnung + Rechte-Vererbung)
> ✅ **AUSGELIEFERT** (2026-07-12, Release v1.0.12). Backend Phase A: PR #55; Frontend
> Phase B: PR #56. Beide subagent-driven (SDD) + Whole-Branch-Reviews; §5-Autorisierungs-/
> Vererbungs-Matrix end-to-end gegen das Backend verifiziert (38/38). Nachfolge-Zyklus zu
> #9 (das bewusst „nur Projekte" scoped hatte). Spec:
> `2026-07-12-organizations-umbrella-design.md` (+ `-phase-b-frontend.md`) · Pläne:
> `../plans/2026-07-12-organizations-umbrella-phase-a-backend.md`,
> `../plans/2026-07-12-organizations-umbrella-phase-b-frontend.md`.
> **Hinweis:** nur UI-Visuelles (Banner/disabled-selects/inline-Fehler) blieb Wolfgangs
> manuellem Browser-Pass überlassen.

Organisationen werden **Oberkategorie** über Projekten und Nutzern: einer Org lassen
sich **Projekte** und **Member** zuordnen, und Rechte werden von der Org auf ihre
Projekte **vererbt** (Org-Admins → automatisch Projekt-Admins, andere Ebenen analog).
**Entscheidungen:** (1) alle Ebenen vererben auf **alle** Org-Projekte als additives
**Maximum** (explizite Projektrolle kann nur anheben); (2) Mapping `OrgOWNER/ADMIN →
ADMIN`, `OrgMEMBER → VIEWER`; (3) Projekt↔Org-Zuordnung **optional**, `orgId=null`
verhält sich wie heute → **keine Migration**; (4) OrgOWNER/ADMIN verwalten ihre eigene
Org (Member/Rollen/Projekt-Zuordnung), System-ADMIN alles.
**Mechanik (Ansatz A):** effektive Rolle zur Lesezeit in `ProjectService.roleOf`
berechnen (Maximum aus Owner/expliziter Projektrolle/Org-Erbe) via schmalem
`OrgMembershipLookup`-Port → gesamte #9-Durchsetzung erbt automatisch.
**Phasing** wie #9: Phase A Backend (roleOf-Erbe, Projektliste-Union, Projekt↔Org-
Endpoint, `OrgSecurity` + Org-Verwaltung inkl. Rollen-Ändern/Owner-/Self-Schutz),
Phase B Frontend (Org-Admin-Zugang, Member-Verwaltung auf `MembersPage`-Niveau,
Projekt↔Org-UI, geerbt-vs-explizit-Badge). **Nicht im Scope:** Multi-Tenant-
Datenisolation & `switch-org`. Details siehe Spec.

## H1 — nginx `index.html` no-cache Härtung (Ops, klein)
> ✅ **AUSGELIEFERT** (PR #51, 2026-07-10; Release v1.0.10). Spec:
> `2026-07-10-h1-nginx-index-nocache-design.md` ·
> Plan: `../plans/2026-07-10-h1-nginx-index-nocache.md`

Aus dem Debugging vom 2026-07-07 (wkozian sah nach dem v1.0.07-Redeploy keine
Projekte → Ursache war client-seitiger Stale-State, behoben per Hard-Reload):
nginx liefert `index.html` **ohne** explizites `Cache-Control`. SPA-Standard-
Härtung = `Cache-Control: no-cache` für die HTML-Einstiegsdatei setzen (gehashte
Assets bleiben `immutable`/1 Jahr). Dann holt der Browser nach jedem Deploy
garantiert das frische `index.html` mit den neuen Asset-Hashes — kein manueller
Hard-Reload mehr nötig. Kleine, isolierte Änderung an der nginx-Config im
Frontend-Image.

## H2 — Notification-Prefs PUT: unbekannter Typ → 400 leakt Enum-Namen (klein)
> ✅ **AUSGELIEFERT** (PR #50, 2026-07-10; Release v1.0.10). Spec:
> `2026-07-10-h2-notification-prefs-enum-leak-design.md` ·
> Plan: `../plans/2026-07-10-h2-notification-prefs-enum-leak.md`

Aus dem Final-Review von #3 (v1.0.09): `NotificationPreferenceController.update`
ruft `NotificationType.valueOf(it.type)` — bei unbekanntem `type` wirft das
`IllegalArgumentException`, vom `GlobalExceptionHandler` sauber auf **400**
gemappt (nicht 500). Nicht-blockierend, aber die Response echot `ex.message` =
`No enum constant com.taskowolf.notifications.domain.NotificationType.<X>` und
leakt damit den voll qualifizierten Enum-Namen. Fix: unbekannte Typen im
Controller ignorieren/validieren oder eine saubere Fehlermeldung zurückgeben.
Trusted UI schickt heute nur gültige Typen → niedrige Prio.

## H3 — `changePassword`: `newPassword` erlaubt reine Leerzeichen (klein)
> ✅ **AUSGELIEFERT** (PR #50, 2026-07-10; Release v1.0.10). Spec:
> `2026-07-10-h3-blank-password-validation-design.md` ·
> Plan: `../plans/2026-07-10-h3-blank-password-validation.md`.
> Scope-Entscheid: fixt **auch** `RegisterRequest.password` (gleiche Lücke).

Aus dem Final-Review von #3 (v1.0.09): `ChangePasswordRequest.newPassword` hat
`@Size(min = 8)`, aber kein `@NotBlank` — 8 Leerzeichen werden als Passwort
akzeptiert. Frontend erzwingt zusätzlich die Länge, daher niedriges Risiko. Fix:
`@NotBlank` ergänzen (ggf. konsistent mit der Registrierungs-Validierung).

## B1 — User können ihre eigene Rolle ändern 🐞
> Gemeldet 2026-07-11. Status: ✅ **AUSGELIEFERT** (PR #53, Release v1.0.11).
> `ProjectService.changeMemberRole` weist jetzt `actorId == targetUserId` mit 403
> ab (nach `requireAdmin`, vor Owner-Guard) + Integrationstest; `MembersPage`
> deaktiviert das eigene Rollen-`<select>`. Spec:
> `2026-07-11-project-permissions-fixes-design.md` · Plan:
> `plans/2026-07-11-b1-self-role-change.md`.

Ein Nutzer kann seine **eigene** Rolle ändern (Selbst-Rechteausweitung möglich).
Erwartetes Verhalten: die eigene Rolle darf nicht selbst geändert werden – eine
Rollenänderung soll nur durch einen anderen Berechtigten (Projekt-/Org-Admin)
möglich sein. Berührungspunkt: Rollen-/Rechte-Verwaltung aus #9
(Projekt-Permissions). Eigener Bug-Zyklus: Backend-Prüfung (Self-Update der Rolle
serverseitig unterbinden) **und** UI (eigene Rolle nicht editierbar anzeigen).

## B2 — Read-only greift nicht: User können Tickets trotz Read-only ändern 🐞
> Gemeldet 2026-07-11. Status: **⏸️ Zurückgestellt — bereits umgesetzt.**
> Zwei Fehldiagnosen unterwegs korrigiert (beide durch fehlerhafte Greps): (1) das
> Backend blockt Read-only bereits — Reproduktion VIEWER `PATCH /issues/{id}` →
> **403**, Titel unverändert (jeder Ticket-Write via `@PreAuthorize canWrite` bzw.
> Service-`requireAdmin`); (2) das **Frontend gated ebenfalls bereits**: Hook
> `useProjectRole` liefert `canWrite = myRole !== 'VIEWER'`, verdrahtet über **alle**
> Ticket-Oberflächen (`IssueDetailContent` inkl. Comments/Attachments `readOnly`,
> `BoardPage`/`DraggableCard`, `BacklogPage`, `IssueListPage`) sowie Settings-CRUD
> (`LabelsPage`/`VersionsPage`/`CustomFieldsPage`). B2-wie-gemeldet (Read-only
> kann keine Tickets ändern) ist damit **client + server** erfüllt (vermutlich mit
> #9 ausgeliefert). **Noch offen (klein, zurückgestellt):** (a) Backend-Regressionstest
> `VIEWER→PATCH issue → 403` (heute ungetestet); (b) optionaler Polish: Admin-only
> Config-Seiten (Workflow/Automation/Dashboard/API-Keys/Webhooks/Integrations)
> clientseitig auf `isAdmin` gaten statt nur Server-403. Details:
> `2026-07-11-project-permissions-fixes-design.md`.

Trotz Read-only-Rolle können Nutzer Tickets ändern – die Read-only-Durchsetzung
wirkt nicht. Erwartet: Read-only-Nutzer haben ausschließlich Lesezugriff, jegliche
schreibende Aktion wird abgelehnt. Berührungspunkt: read-only-Enforcement aus #9
(server- **und** clientseitig laut Spec) – hier liegt offenbar eine Regression bzw.
Lücke vor. Eigener Bug-Zyklus: server-seitige Autorisierungsprüfung auf allen
schreibenden Ticket-Endpunkten verifizieren/nachziehen (nicht nur UI ausblenden).

## B3 — Logout-Button verschwindet, wenn das Menü länger als der Bildschirm ist 🐞
> Gemeldet 2026-07-11. Status: ✅ **AUSGELIEFERT** (PR #54, Release v1.0.11).
> `<nav>` intern scrollbar (`min-h-0 overflow-y-auto`), Footer/Logout bleibt
> gepinnt. Spec: `2026-07-11-sidebar-groups-and-logout-design.md` · Plan:
> `plans/2026-07-11-sidebar-groups-and-logout.md`.

Wird die linke Sidebar vertikal länger als der Bildschirm (viele Einträge/kleiner
Viewport), rutscht der Logout-Button aus dem sichtbaren Bereich und ist nicht mehr
erreichbar. Erwartet: Logout bleibt immer erreichbar (z.B. gepinnt und die Sidebar
selbst intern scrollbar). Berührungspunkt: verwandt mit #11 (Layout-Fix, Logout
unten gepinnt / `<aside>` fixe Viewport-Höhe) – der Fix deckt diesen Fall (Sidebar
selbst höher als Viewport) offenbar noch nicht ab. Eigener UI-Bug-Zyklus.
