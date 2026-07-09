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
| 9 | User-Rechte-Verwaltung (Projekt-/Org-Freischaltung + Rollen) | Full-Stack | Backlog |
| 10 | Sidebar-Gruppen einzeln zusammenklappbar (Admin/Project/…) | UI | Backlog |
| 11 | Layout-Fix: linkes Menü darf sich nicht mit Seiteninhalt strecken | UI/Bug | Backlog |
| 12 | Dependabot-Alerts beheben (5 offen) | Ops/Security | Backlog |
| H1 | nginx `index.html` no-cache Härtung | Ops/Hardening | Backlog (klein) |
| H2 | Notification-Prefs PUT: unbekannter Typ → 400 leakt Enum-Namen | Hardening | Backlog (klein) |
| H3 | `changePassword`: `newPassword` erlaubt reine Leerzeichen | Hardening | Backlog (klein) |

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

## #12 — Dependabot-Alerts beheben (5 offen, Stand 2026-07-09)
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

## H1 — nginx `index.html` no-cache Härtung (Ops, klein)
Aus dem Debugging vom 2026-07-07 (wkozian sah nach dem v1.0.07-Redeploy keine
Projekte → Ursache war client-seitiger Stale-State, behoben per Hard-Reload):
nginx liefert `index.html` **ohne** explizites `Cache-Control`. SPA-Standard-
Härtung = `Cache-Control: no-cache` für die HTML-Einstiegsdatei setzen (gehashte
Assets bleiben `immutable`/1 Jahr). Dann holt der Browser nach jedem Deploy
garantiert das frische `index.html` mit den neuen Asset-Hashes — kein manueller
Hard-Reload mehr nötig. Kleine, isolierte Änderung an der nginx-Config im
Frontend-Image.

## H2 — Notification-Prefs PUT: unbekannter Typ → 400 leakt Enum-Namen (klein)
Aus dem Final-Review von #3 (v1.0.09): `NotificationPreferenceController.update`
ruft `NotificationType.valueOf(it.type)` — bei unbekanntem `type` wirft das
`IllegalArgumentException`, vom `GlobalExceptionHandler` sauber auf **400**
gemappt (nicht 500). Nicht-blockierend, aber die Response echot `ex.message` =
`No enum constant com.taskowolf.notifications.domain.NotificationType.<X>` und
leakt damit den voll qualifizierten Enum-Namen. Fix: unbekannte Typen im
Controller ignorieren/validieren oder eine saubere Fehlermeldung zurückgeben.
Trusted UI schickt heute nur gültige Typen → niedrige Prio.

## H3 — `changePassword`: `newPassword` erlaubt reine Leerzeichen (klein)
Aus dem Final-Review von #3 (v1.0.09): `ChangePasswordRequest.newPassword` hat
`@Size(min = 8)`, aber kein `@NotBlank` — 8 Leerzeichen werden als Passwort
akzeptiert. Frontend erzwingt zusätzlich die Länge, daher niedriges Risiko. Fix:
`@NotBlank` ergänzen (ggf. konsistent mit der Registrierungs-Validierung).
