# Release Notes

Version history for TaskWolf. Docker images are published to Docker Hub as
`kwolfgang/taskowolf-backend:<version>` and `kwolfgang/taskowolf-frontend:<version>`
(e.g. `1.0.10`). See the [GitHub releases](https://github.com/WolfTasks/TaskWolf/releases) for downloads.

## v1.0.10 — 2026-07-11

#### Highlights

- **#9 Project permissions** (PR #52): per-project roles (Read-only / Read & Write / Admin), a project **Members** management UI (add/change-role/remove with user search), and read-only enforcement server- **and** client-side. Owner is protected as implicit admin. No DB migration.

#### Hardening & fixes

- **H1** (PR #51): nginx serves `index.html` with `Cache-Control: no-cache` so deploys pick up fresh asset hashes without a manual hard-reload.
- **H2 / H3** (PR #50): notification-prefs no longer leaks enum names on an unknown type (400); `changePassword`/register reject all-whitespace passwords.
- **#12** (PR #49): Dependabot alerts cleared (logback-core 1.5.35, commons-compress 1.26.0).
- **#11** (PR #48): sidebar pinned to viewport height so Logout stays reachable on long pages.

## v1.0.09 — 2026-07-09

#### Backlog #3 — Profile / Settings pages (#47)

Per-user profile & settings under a dedicated `/settings/*` shell.

**Backend**
- `PATCH /api/v1/me` — update display name.
- `POST /api/v1/me/password` — change password (verifies current password, revokes refresh tokens but keeps personal access tokens, forces re-login).
- Per-user notification preferences (`NotificationPreference` + Flyway V29), opt-out model, gating both in-app and email dispatch; `GET`/`PUT /api/v1/me/notification-preferences`.

**Frontend**
- Profile, Security, and Notifications pages plus a Settings shell with sub-nav; the sidebar Account section is now a single **Settings** entry. Existing Access Tokens / Account pages moved under the shell (paths unchanged).

## v1.0.08 — 2026-07-08

#### Neuerungen seit v1.0.07

##### Scrollbare Listen (#45)
- Lange Tabellen (Audit-Log, Users, Access Tokens, API Keys, Projekt-Audit) scrollen jetzt **intern** in einem Container, der die Fensterhöhe füllt — mit **sticky Tabellenkopf** und **Virtualisierung** (nur sichtbare Zeilen werden gerendert). Die Seite selbst wächst nicht mehr mit der Zeilenzahl.
- Grundlage: neue wiederverwendbare `DataTable`-Komponente.

##### Einklappbare Sidebar (#46)
- Die linke Navigation lässt sich zwischen **breit** (Icon + Text) und schmaler **Icon-Leiste** umschalten; eingeklappt erscheinen die Labels als Tooltip beim Hover.
- Der Zustand wird pro Browser gespeichert; unter 1024px Breite klappt die Sidebar automatisch zur Icon-Leiste ein.

**Vollständiges Changelog:** https://github.com/WolfTasks/TaskWolf/compare/v1.0.07...v1.0.08

## v1.0.07 — 2026-07-07

#### Neuerungen seit v1.0.06

##### Personal Access Tokens (`twk_`) + User-Lebenszyklus (#44)
- Persönliche, user-gebundene Access Tokens für externe Tools — `Authorization: Bearer twk_…`, mit den Rechten des jeweiligen Nutzers.
- Scope **Read-only** / **Read & Write** (Read-only erlaubt nur GET/HEAD/OPTIONS, sonst 403).
- Verwaltung unter „Account → Access Tokens" (`/api/v1/me/tokens`); nur SHA-256-Hash gespeichert, Klartext einmalig, Revoke als Soft-Revoke.
- User-Lebenszyklus: Deaktivieren/Aktivieren + Konto löschen (Soft-Delete + Anonymisierung), self via `DELETE /api/v1/me` und Admin via `/api/v1/admin/users`; deaktivierte/gelöschte Nutzer verlieren sofort alle Tokens; Letzter-aktiver-Admin-Guard.
- Nebenbei behoben: unauthentifizierte API-Requests liefern nun `401` (statt SSO-302); `@PreAuthorize`-Denials liefern korrekt `403` (statt 500).

##### App-Version-Anzeige (#43)
- Aktuelle Version auf dem Startbildschirm und unten links in der Sidebar, aus `package.json` (Single Source of Truth).

##### Wartung
- Frontend-Dependencies aktualisiert (#42).

**Vollständiges Changelog:** https://github.com/WolfTasks/TaskWolf/compare/v1.0.06...v1.0.07

## v1.0.06 — 2026-07-06

#### What's Changed
* feat(issue): Comments/Activity tabs, pinned sidebar & paginated feeds by @WolfTasks in https://github.com/WolfTasks/TaskWolf/pull/41


**Full Changelog**: https://github.com/WolfTasks/TaskWolf/compare/v1.0.05...v1.0.06

## v1.0.05 — 2026-07-05

#### What's Changed
* feat(issue): Phase 1 issue dialog (modal via ?issue= param) by @WolfTasks in https://github.com/WolfTasks/TaskWolf/pull/38
* feat(issue): Phase 2 editable story points (Fibonacci) by @WolfTasks in https://github.com/WolfTasks/TaskWolf/pull/39
* feat(sprints): Phase 3 Sprint-Übersicht + deferred Phase-1 minors by @WolfTasks in https://github.com/WolfTasks/TaskWolf/pull/40


**Full Changelog**: https://github.com/WolfTasks/TaskWolf/compare/v1.0.04...v1.0.05

## v1.0.03 — 2026-07-04

#### What's Changed
* Backend CVE remediation: Spring Boot 3.5.x upgrade by @WolfTasks in https://github.com/WolfTasks/TaskWolf/pull/10
* build(deps): bump the actions group across 1 directory with 15 updates by @dependabot[bot] in https://github.com/WolfTasks/TaskWolf/pull/11
* build(deps): bump eclipse-temurin from 21-jre-alpine to 25-jre-alpine in /backend by @dependabot[bot] in https://github.com/WolfTasks/TaskWolf/pull/3
* build(deps): bump node from 20-alpine to 26-alpine in /frontend by @dependabot[bot] in https://github.com/WolfTasks/TaskWolf/pull/4
* chore(dependabot): ignore Spring Boot major bumps (stay on 3.5.x) by @WolfTasks in https://github.com/WolfTasks/TaskWolf/pull/14
* fix(frontend): drop deprecated tsconfig baseUrl (unblocks TypeScript 6) by @WolfTasks in https://github.com/WolfTasks/TaskWolf/pull/16
* fix(frontend): TS 6 CSS side-effect imports + recharts 3 formatter type by @WolfTasks in https://github.com/WolfTasks/TaskWolf/pull/20
* build(deps): bump the frontend-deps group across 1 directory with 20 updates by @dependabot[bot] in https://github.com/WolfTasks/TaskWolf/pull/6
* build(deps): bump nginx from `35cd774` to `54f2a90` in /frontend by @dependabot[bot] in https://github.com/WolfTasks/TaskWolf/pull/15
* chore(dependabot): ignore all backend semver-major bumps by @WolfTasks in https://github.com/WolfTasks/TaskWolf/pull/21
* build(deps): Kotlin 2.4 / Gradle 9 backend toolchain migration by @WolfTasks in https://github.com/WolfTasks/TaskWolf/pull/25
* build(security): bump OWASP dependency-check 11.1.0 → 12.2.2 (fix nightly scan) by @WolfTasks in https://github.com/WolfTasks/TaskWolf/pull/28
* build(deps): bump the backend-deps group across 1 directory with 6 updates by @dependabot[bot] in https://github.com/WolfTasks/TaskWolf/pull/29
* build(deps): bump the actions group across 1 directory with 6 updates by @dependabot[bot] in https://github.com/WolfTasks/TaskWolf/pull/22
* fix(security): replace NVD-bound OWASP nightly with Trivy SCA scan (#30) by @WolfTasks in https://github.com/WolfTasks/TaskWolf/pull/31
* fix(security): bump commons-lang3 3.17.0 -> 3.18.0 (CVE-2025-48924) by @WolfTasks in https://github.com/WolfTasks/TaskWolf/pull/32
* fix(security): upgrade libexpat 2.8.1-r0 -> 2.8.2-r0 in base images by @WolfTasks in https://github.com/WolfTasks/TaskWolf/pull/34
* docs(readme): add GitHub Pages documentation link by @WolfTasks in https://github.com/WolfTasks/TaskWolf/pull/35
* fix(ci): publish versioned Docker tags for v1.0.0x releases by @WolfTasks in https://github.com/WolfTasks/TaskWolf/pull/36

#### New Contributors
* @dependabot[bot] made their first contribution in https://github.com/WolfTasks/TaskWolf/pull/11

**Full Changelog**: https://github.com/WolfTasks/TaskWolf/compare/v1.0.02...v1.0.03

## v1.0.02 — 2026-06-28

**Full Changelog**: https://github.com/WolfTasks/TaskWolf/compare/v1.0.01...v1.0.02

## v1.0.01 — 2026-06-27

#### What's Changed
* feat(issues): editable issue detail — click-to-edit fields, rich text description, audit fix by @WolfTasks in https://github.com/WolfTasks/TaskWolf/pull/2


**Full Changelog**: https://github.com/WolfTasks/TaskWolf/compare/v1.0.0...v1.0.01

## v1.0.0 — 2026-06-24

### Changelog

All notable changes to TaskWolf are documented here.
Format: [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

#### [1.0.0] - 2026-06-24

##### Added

###### Core Foundation
- Projects with custom keys (e.g. `WOLF-42`)
- Issues: Epics, Stories, Bugs, Tasks, Subtasks — with priority, labels, story points, due dates, and parent–child linking
- Configurable workflows with custom statuses and transitions
- JWT authentication + OAuth2 (GitHub, Google) + API key authentication
- Role-based access control: System Admin, Project Admin, Member
- OpenAPI 3 documentation at `/swagger-ui.html`
- Docker + docker-compose deployment with nginx reverse proxy

###### Agile Boards
- Kanban and Scrum board views with drag-and-drop column management
- Sprint lifecycle: Planned → Active → Closed
- Backlog management and sprint planning view
- Burndown chart per sprint

###### Collaboration
- Markdown comments with `@mention` autocomplete
- Real-time in-app notifications via WebSocket (STOMP)
- File attachments per issue
- Activity feed per issue

###### Workflows & Automation
- Visual workflow canvas editor with drag-and-drop transition builder
- No-code automation rules: When / If / Then
- Triggers: issue created, status changed, field updated, sprint started/completed
- Action types: assign user, set status, set field, add comment, create sub-issue, send notification

###### Dashboards & Reports
- Burndown chart (sprint-level)
- Velocity chart (multi-sprint trend)
- Cycle time analysis per issue type
- Custom dashboards with drag-and-drop widget layout
- Widget types: key metrics, charts, issue tables, activity feeds

###### Developer Tools & Integrations
- Outgoing webhooks with HMAC-SHA256 signing and automatic retry with exponential backoff
- GitHub and GitLab incoming webhooks — auto-link commits and pull requests to issues
- API key authentication (`tw_*` prefix, SHA-256 hashed storage) for CI/CD pipelines
- SSRF-safe HTTP delivery for outgoing webhooks

###### Enterprise
- Audit logs with three configurable levels: SECURITY (always on), WRITE, ALL — with JSON and CSV export
- SSO via OIDC — dynamic provider registration, AES-GCM encrypted client secrets, user auto-provisioning
- Organizations (multi-tenancy) with org switcher, JWT org context, and member management
- Service desks with SLA policies (breach detection + escalation rules)
- Incident tracking with automatic postmortem creation on resolution
- Email-to-ticket ingestion via IMAP

##### Fixed
- OIDC provider discovery now uses RFC-compliant `/.well-known/openid-configuration` instead of hardcoded Keycloak paths — compatible with Okta, Azure AD, Auth0, Google Workspace
- `GET /organizations/{id}` and `GET /organizations/{id}/members` now enforce membership authorization; non-members receive 403
- Audit log CSV export uses OWASP-recommended formula-injection escaping (values starting with `=`, `+`, `-`, `@`, tab, or carriage return are prefixed with `'`)
