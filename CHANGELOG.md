# Changelog

All notable changes to TaskWolf are documented here.
Format: [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [1.0.0] - 2026-06-24

### Added

#### Core Foundation
- Projects with custom keys (e.g. `WOLF-42`)
- Issues: Epics, Stories, Bugs, Tasks, Subtasks — with priority, labels, story points, due dates, and parent–child linking
- Configurable workflows with custom statuses and transitions
- JWT authentication + OAuth2 (GitHub, Google) + API key authentication
- Role-based access control: System Admin, Project Admin, Member
- OpenAPI 3 documentation at `/swagger-ui.html`
- Docker + docker-compose deployment with nginx reverse proxy

#### Agile Boards
- Kanban and Scrum board views with drag-and-drop column management
- Sprint lifecycle: Planned → Active → Closed
- Backlog management and sprint planning view
- Burndown chart per sprint

#### Collaboration
- Markdown comments with `@mention` autocomplete
- Real-time in-app notifications via WebSocket (STOMP)
- File attachments per issue
- Activity feed per issue

#### Workflows & Automation
- Visual workflow canvas editor with drag-and-drop transition builder
- No-code automation rules: When / If / Then
- Triggers: issue created, status changed, field updated, sprint started/completed
- Action types: assign user, set status, set field, add comment, create sub-issue, send notification

#### Dashboards & Reports
- Burndown chart (sprint-level)
- Velocity chart (multi-sprint trend)
- Cycle time analysis per issue type
- Custom dashboards with drag-and-drop widget layout
- Widget types: key metrics, charts, issue tables, activity feeds

#### Developer Tools & Integrations
- Outgoing webhooks with HMAC-SHA256 signing and automatic retry with exponential backoff
- GitHub and GitLab incoming webhooks — auto-link commits and pull requests to issues
- API key authentication (`tw_*` prefix, SHA-256 hashed storage) for CI/CD pipelines
- SSRF-safe HTTP delivery for outgoing webhooks

#### Enterprise
- Audit logs with three configurable levels: SECURITY (always on), WRITE, ALL — with JSON and CSV export
- SSO via OIDC — dynamic provider registration, AES-GCM encrypted client secrets, user auto-provisioning
- Organizations (multi-tenancy) with org switcher, JWT org context, and member management
- Service desks with SLA policies (breach detection + escalation rules)
- Incident tracking with automatic postmortem creation on resolution
- Email-to-ticket ingestion via IMAP

### Fixed
- OIDC provider discovery now uses RFC-compliant `/.well-known/openid-configuration` instead of hardcoded Keycloak paths — compatible with Okta, Azure AD, Auth0, Google Workspace
- `GET /organizations/{id}` and `GET /organizations/{id}/members` now enforce membership authorization; non-members receive 403
- Audit log CSV export uses OWASP-recommended formula-injection escaping (values starting with `=`, `+`, `-`, `@`, tab, or carriage return are prefixed with `'`)
