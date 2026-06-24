# Phase 8: Release v1.0 — Design Spec

**Date:** 2026-06-24
**Status:** Approved
**Depends on:** Phase 1–7

---

## Ziel

TaskWolf v1.0 wird als vollständiges, produktionsreifes Release veröffentlicht. Phase 8 besteht aus vier gleichwertigen Bereichen: Security-Hardening der deferred Phase-7-Items, Changelog + GitHub Release, MkDocs-Dokumentationssite, und Multi-arch Docker Hub CI/CD.

---

## Ausführungsreihenfolge

1. **Hardening** — keine unsichere Version releasen
2. **Changelog** — in derselben Backend-Session; Git-Tag `v1.0.0` triggert CI
3. **MkDocs** — statische Docs-Site, deploybar auf GitHub Pages
4. **Docker Hub CI/CD** — GitHub Actions Workflow, Multi-arch Build

---

## Block 1: Security Hardening

### 1a — OIDC Discovery (RFC-konform)

**Problem:** Der Code konstruiert OIDC-Endpunkt-URLs hardcoded nach Keycloak-Muster (`{issuerUrl}/protocol/openid-connect/auth` etc.).

**Fix:** `OidcProvider` speichert nur noch die `issuerUri`. Spring Securitys `ClientRegistration.Builder.issuerUri()` macht automatisch einen HTTP-GET auf `{issuerUri}/.well-known/openid-configuration` und befüllt alle Endpunkte (Authorization, Token, JWKS) selbst.

- Kompatibel mit: Okta, Azure AD, Auth0, Google Workspace, Keycloak
- Keine neue DB-Migration — bestehende Felder, nur Builder-Aufruf ändert sich
- Betrifft: `SsoService` / wherever `ClientRegistration` heute manuell aufgebaut wird

### 1b — Org Membership Guard

**Problem:** `GET /organizations/{id}` und `GET /organizations/{id}/members` fehlt Membership-Check; jeder authentifizierte User kann fremde Org-Daten lesen.

**Fix:** `OrganizationService` erhält `requireMembershipOrAdmin(orgId: Long, currentUserId: Long)` — wirft `AccessDeniedException` wenn User weder SYSTEM_ADMIN noch Mitglied der Org ist. Aufruf am Anfang von `getById()` und `getMembers()`.

- Kein neues DB-Schema — Membership-Tabelle existiert bereits (Phase 7)
- Betrifft: `OrganizationController` + `OrganizationService`

### 1c — CSV Injection Escaping

**Problem:** `AuditController.exportCsv()` schreibt Zellwerte ohne Schutz gegen Formula-Injection.

**Fix:** Private Hilfsfunktion `escapeCsvCell(value: String): String` — prefixiert jeden Wert der mit `=`, `+`, `-`, `@`, Tab (`\t`) oder CR (`\r`) beginnt mit einem einfachen Anführungszeichen `'`. OWASP-empfohlener Standard; Excel und Google Sheets respektieren dieses Präfix.

- Betrifft: `AuditController.exportCsv()` — alle Zellwerte durch `escapeCsvCell()` leiten

---

## Block 2: Changelog + GitHub Release

**`CHANGELOG.md`** im Repo-Root, Format: [Keep a Changelog](https://keepachangelog.com).

Struktur:
```
## [1.0.0] - 2026-06-24
### Added
  - Phase 1–7 Features (Core, Agile, Collaboration, Automation, Dashboards, Integrations, Enterprise)
### Fixed
  - OIDC RFC-konforme Discovery (Phase 7 deferred)
  - Org Membership Guard auf GET /organizations/{id} (Phase 7 deferred)
  - CSV Injection Escaping im Audit-Export (Phase 7 deferred)
```

**GitHub Release:**
- `gh release create v1.0.0 --title "TaskWolf v1.0.0" --notes-file CHANGELOG.md`
- Git-Tag `v1.0.0` triggert gleichzeitig den Docker Hub CI/CD Workflow (Block 4)

Scope: nur `CHANGELOG.md` + GitHub Release + Git-Tag. Kein `CONTRIBUTING.md`, kein Code-of-Conduct.

---

## Block 3: MkDocs Documentation

**`mkdocs.yml`** im Repo-Root mit `docs_dir: mkdocs/` (bestehende `docs/superpowers/` bleibt unberührt).

### Dateistruktur

```
mkdocs/
  index.md                  ← Landing page (gekürztes README)
  getting-started.md        ← Docker-compose Quick Start, Env-Vars, erster Login
  configuration.md          ← Alle application.yml / Env-Vars mit Erklärung
  user-guide/
    projects.md             ← Projekte, Issues, Workflows
    boards.md               ← Kanban, Scrum, Sprints
    automation.md           ← No-code Rules
    dashboards.md           ← Widgets, Custom Dashboards
  admin-guide/
    sso.md                  ← OIDC Provider registrieren
    organizations.md        ← Multi-Tenancy, Org-Switcher
    service-desk.md         ← SLA, Incidents, Email-Ingestion
    audit-logs.md           ← Log-Level, CSV-Export
  api.md                    ← Link auf /swagger-ui.html + API Key Erklärung
  development.md            ← Build from source, Tests laufen lassen
```

### Theme + Config

- **Theme:** Material for MkDocs (`mkdocs-material>=9`) mit Navigation-Tabs und Search
- **`requirements-docs.txt`** im Repo-Root für reproduzierbare CI-Builds

### GitHub Pages Deployment

Workflow `.github/workflows/docs.yml`:
- **Trigger:** Push auf `main`
- **Build:** `pip install -r requirements-docs.txt && mkdocs build`
- **Deploy:** `mkdocs gh-deploy --force` → `gh-pages` Branch
- **URL:** `https://<user>.github.io/TaskWolf/`

README erhält einen Docs-Badge mit Link auf die GitHub Pages URL.

---

## Block 4: Docker Hub CI/CD (Multi-arch)

### GitHub Actions Workflow `.github/workflows/docker-publish.yml`

- **Trigger:** Git-Tag matching `v*.*.*`
- **Runner:** `ubuntu-latest`
- **Steps:**
  1. `docker/setup-qemu-action` — QEMU für ARM-Emulation
  2. `docker/setup-buildx-action` — BuildKit Multi-arch Support
  3. `docker/login-action` mit `DOCKERHUB_USERNAME` + `DOCKERHUB_TOKEN` (GitHub Secrets)
  4. `docker buildx build --platform linux/amd64,linux/arm64 --push`
  5. Tags: `taskwolf/taskwolf:{version}` + `taskwolf/taskwolf:latest`
  6. Version aus Git-Tag extrahiert (`v1.0.0` → `1.0.0`)

### `docker-compose.prod.yml`

Ergänzt das bestehende `docker-compose.yml`, nutzt `taskwolf/taskwolf:latest` statt lokalem Build-Context:

- `restart: unless-stopped` auf App + Postgres
- `healthcheck` auf App-Container (`GET /actuator/health`, Intervall 30s, 3 Retries)
- `mem_limit: 512m` als Self-Hosting-Default
- Alle Secrets via `.env` Datei (`.env.example` als Template im Repo)

### README

- Docker Hub Badge + Image-Link
- Kurzer Abschnitt "Production Deployment" mit Verweis auf `docker-compose.prod.yml` und die MkDocs-Docs

---

## Flyway

Keine neuen Migrationen in Phase 8. Aktueller Stand: V21. Alle Hardening-Items sind reine Kotlin/Java-Änderungen.

---

## Testing

- **Hardening:** Unit-Tests für `escapeCsvCell()`, `requireMembershipOrAdmin()` (AccessDeniedException-Case), OIDC-Discovery-Integration-Test (MockWebServer simuliert `.well-known/openid-configuration`)
- **MkDocs:** `mkdocs build --strict` im CI schlägt fehl bei broken links
- **Docker:** CI verifiziert, dass der Multi-arch Build erfolgreich abschließt und das Image auf Docker Hub gepusht wurde. Ein vollständiger Smoke-Test (App startet, Healthcheck grün) erfordert docker-compose mit PostgreSQL und ist manuell nach dem Release-Tag zu verifizieren.

---

## Nicht in Scope

- `CONTRIBUTING.md`, Code of Conduct, Issue Templates
- Kundenportal / externes Service-Desk-Frontend
- Helm Chart / Kubernetes Manifeste
- Lokalisierung / i18n
