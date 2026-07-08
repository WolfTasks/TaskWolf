# Backlog-Überblick (Ideensammlung 2026-07-07)

> Diese Notiz hält die noch **nicht** designten Vorhaben fest. Jedes bekommt bei
> Bearbeitung seinen eigenen Brainstorming-→Spec-→Plan-Zyklus.

| # | Vorhaben | Typ | Status |
|---|----------|-----|--------|
| 1 | App-Version anzeigen | UI | ✅ **AUSGELIEFERT** (PR #43, Release v1.0.07) |
| 2 | Personal Access Tokens + User-Lebenszyklus | Full-Stack | ✅ **AUSGELIEFERT** (PR #44, Release v1.0.07) |
| 3 | User-Profil-Seiten (gruppierte Einstellungen) | Feature | Design ✅ (`2026-07-08-profile-settings-design.md`) |
| 4 | Branches aufräumen | Housekeeping | Backlog (kein Spec nötig) |
| 5 | UI-Tests | Test-Infra | Backlog |
| 6 | Test-Deploy bei Selfhoster mit eigener URL | Ops | Backlog |
| 7 | Scrollbare Listen (z.B. Audit-Log) | UI | Design ✅ (`2026-07-08-scrollable-lists-design.md`) |
| 8 | Linkes Menü zusammenklappbar | UI | Design ✅ (`2026-07-08-collapsible-sidebar-design.md`) |
| 9 | User-Rechte-Verwaltung (Projekt-/Org-Freischaltung + Rollen) | Full-Stack | Backlog |
| H1 | nginx `index.html` no-cache Härtung | Ops/Hardening | Backlog (klein) |

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

## H1 — nginx `index.html` no-cache Härtung (Ops, klein)
Aus dem Debugging vom 2026-07-07 (wkozian sah nach dem v1.0.07-Redeploy keine
Projekte → Ursache war client-seitiger Stale-State, behoben per Hard-Reload):
nginx liefert `index.html` **ohne** explizites `Cache-Control`. SPA-Standard-
Härtung = `Cache-Control: no-cache` für die HTML-Einstiegsdatei setzen (gehashte
Assets bleiben `immutable`/1 Jahr). Dann holt der Browser nach jedem Deploy
garantiert das frische `index.html` mit den neuen Asset-Hashes — kein manueller
Hard-Reload mehr nötig. Kleine, isolierte Änderung an der nginx-Config im
Frontend-Image.
