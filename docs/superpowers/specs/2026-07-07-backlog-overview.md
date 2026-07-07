# Backlog-Überblick (Ideensammlung 2026-07-07)

> Diese Notiz hält die noch **nicht** designten Vorhaben fest. Jedes bekommt bei
> Bearbeitung seinen eigenen Brainstorming-→Spec-→Plan-Zyklus.

| # | Vorhaben | Typ | Status |
|---|----------|-----|--------|
| 1 | App-Version anzeigen | UI | **Spec fertig** → `2026-07-07-app-version-display-design.md` |
| 2 | Personal Access Tokens + User-Lebenszyklus | Full-Stack | **Spec fertig** → `2026-07-07-personal-access-tokens-design.md` |
| 3 | User-Profil-Seiten (gruppierte Einstellungen) | Feature | Backlog |
| 4 | Branches aufräumen | Housekeeping | Backlog (kein Spec nötig) |
| 5 | UI-Tests | Test-Infra | Backlog |
| 6 | Test-Deploy bei Selfhoster mit eigener URL | Ops | Backlog |

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
