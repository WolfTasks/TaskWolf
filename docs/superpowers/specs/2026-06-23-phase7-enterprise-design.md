# Phase 7: Enterprise — Design Spec

**Date:** 2026-06-23
**Status:** Approved
**Depends on:** Phase 1–6

---

## Ziel

TaskWolf erhält Enterprise-Features in vier aufeinander aufbauenden Schritten: Audit Logs, SSO via OIDC, Multi-Tenancy (Organisations-Ebene) und Service Management (Helpdesk + Incidents). Jeder Schritt ist eine eigenständige Implementierungs-Session mit eigenem Plan.

---

## Architektur-Entscheidungen

| Frage | Entscheidung | Begründung |
|---|---|---|
| Modul-Struktur | Pragmatisch: SSO extends `auth`, neue Module `audit`, `organizations`, `servicedesk` | Konsistent mit Phase-6-Muster (API Keys in `auth`) |
| SSO-Protokoll | OIDC only | Spring Security OAuth2 Client hat native Unterstützung; deckt Azure AD, Okta, Keycloak, Google Workspace ab |
| Audit Log Stufen | SECURITY (immer), WRITE + ALL (konfigurierbar) | Schlanker Normal-Betrieb, bei Bedarf verbose |
| Multi-Tenancy | Org-Ebene + Datenisolation via `org_id` auf allen Entitäten | Strukturelle Trennung, kein optionaler Check |
| Service Management | Helpdesk + ITSM-light (SLA, Eskalation, Incidents, Postmortem) | Kein Kundenportal (zu groß), Fokus auf internen Betrieb |

---

## Schritt 1: Audit Logs (`audit`-Modul, V17)

### Datenmodell

```sql
CREATE TABLE audit_events (
    id           BIGSERIAL PRIMARY KEY,
    timestamp    TIMESTAMPTZ NOT NULL DEFAULT now(),
    user_id      BIGINT REFERENCES users(id) ON DELETE SET NULL,
    user_email   VARCHAR(255) NOT NULL,
    project_id   BIGINT REFERENCES projects(id) ON DELETE SET NULL,
    action       VARCHAR(100) NOT NULL,
    level        VARCHAR(20)  NOT NULL, -- SECURITY | WRITE | ALL
    resource_type VARCHAR(50),
    resource_id  VARCHAR(100),
    details      JSONB,
    ip_address   VARCHAR(45),
    user_agent   VARCHAR(500)
);
CREATE INDEX ON audit_events (timestamp DESC);
CREATE INDEX ON audit_events (project_id, timestamp DESC);
CREATE INDEX ON audit_events (user_id, timestamp DESC);
```

### Log-Stufen

| Stufe | Aktiv | Beispiel-Actions |
|---|---|---|
| `SECURITY` | Immer | `LOGIN_SUCCESS`, `LOGIN_FAILED`, `LOGOUT`, `PASSWORD_CHANGED`, `ROLE_CHANGED`, `API_KEY_CREATED`, `API_KEY_DELETED`, `OAUTH_LOGIN`, `USER_REGISTERED` |
| `WRITE` | Konfigurierbar | `ISSUE_CREATED`, `ISSUE_UPDATED`, `ISSUE_DELETED`, `ISSUE_TRANSITIONED`, `COMMENT_CREATED`, `COMMENT_DELETED`, `SPRINT_STARTED`, `SPRINT_COMPLETED`, `MEMBER_ADDED`, `MEMBER_REMOVED`, `WEBHOOK_CREATED`, `WEBHOOK_DELETED`, `SLA_BREACHED` |
| `ALL` | Konfigurierbar | `ISSUE_VIEWED`, `BOARD_OPENED`, `REPORT_VIEWED`, Board-Moves |

Active levels gespeichert als System-Setting in DB (`audit_config`-Tabelle: `level`, `enabled`).

### Implementierung

- `AuditService.log(level, action, resourceType, resourceId, details)` — zentrale Schreibmethode, non-blocking (Spring `@Async`)
- `SecurityAuditListener`: `@EventListener` auf Auth-Events → `AuditService.log(SECURITY, ...)`
- `WriteAuditListener`: `@EventListener` auf Domain Events (IssueCreatedEvent etc.) → prüft ob WRITE aktiv → loggt
- Direkter Aufruf aus `AuthService` für Login/Logout (kein Domain Event vorhanden)

### API

```
GET  /api/v1/admin/audit?page=&size=&from=&to=&userId=&action=&level=   (ADMIN)
GET  /api/v1/admin/audit/export?format=csv|json                          (ADMIN)
GET  /api/v1/projects/{key}/audit?page=&size=&from=&to=&action=         (PROJECT_ADMIN)
GET  /api/v1/admin/audit/config                                          (ADMIN)
PUT  /api/v1/admin/audit/config                                          (ADMIN)
```

### Frontend

- `/admin/audit` — Tabellen-View, Filter-Panel (Datum, User, Action, Level), Export-Button
- `/p/:key/settings/audit` — projekt-scoped View (nur projekt-relevante Events)
- Retention: keine automatische Löschung in Phase 7 (kann später als Audit-Config-Setting nachgerüstet werden)

---

## Schritt 2: SSO via OIDC (`auth`-Modul Erweiterung, V18)

### Datenmodell

```sql
CREATE TABLE sso_configs (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    issuer_url   VARCHAR(500) NOT NULL,
    client_id    VARCHAR(255) NOT NULL,
    client_secret_enc VARCHAR(500) NOT NULL, -- AES-verschlüsselt
    enabled      BOOLEAN NOT NULL DEFAULT true,
    auto_provision BOOLEAN NOT NULL DEFAULT true,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

`client_secret` wird mit AES-GCM verschlüsselt; Key wird aus `TW_JWT_SECRET` (SHA-256) abgeleitet.

### Funktionsweise

- Spring Security OAuth2 Client: `ClientRegistrationRepository` wird zur Runtime aus DB geladen (eigene `DbClientRegistrationRepository`-Implementierung)
- Login-Flow: Nutzer klickt "Sign in with SSO" → Redirect zu `/auth/sso/{configId}` → IDP → Callback → JWT (identischer Flow wie GitHub/Google OAuth2)
- `auto_provision=true`: bei erstem Login wird `User` aus OIDC-Claims angelegt (email, name, picture → avatarUrl); systemRole = USER
- Bestehende GitHub/Google-OAuth2-Pfade bleiben unverändert

### API

```
GET    /api/v1/admin/sso          (ADMIN) — Liste aller SSO-Configs
POST   /api/v1/admin/sso          (ADMIN) — neue Config anlegen
PUT    /api/v1/admin/sso/{id}     (ADMIN) — aktualisieren
DELETE /api/v1/admin/sso/{id}     (ADMIN) — deaktivieren
GET    /auth/sso/{configId}       (public) — Redirect zur IDP
```

### Frontend

- `/admin/settings/sso` — Liste aktiver SSO-Configs + Formular (Name, Issuer URL, Client ID, Client Secret, Auto-Provision Toggle)
- Login-Seite: "Sign in with SSO"-Button erscheint dynamisch wenn `GET /api/v1/admin/sso` mindestens eine aktive Config zurückgibt

---

## Schritt 3: Multi-Tenancy / Organizations (`organizations`-Modul, V19)

### Datenmodell

```sql
CREATE TABLE organizations (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    slug       VARCHAR(50)  NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE organization_members (
    org_id  BIGINT NOT NULL REFERENCES organizations(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    role    VARCHAR(20) NOT NULL, -- OWNER | ADMIN | MEMBER
    PRIMARY KEY (org_id, user_id)
);

-- Additive Spalten (nullable für Rückwärtskompatibilität):
ALTER TABLE users       ADD COLUMN org_id BIGINT REFERENCES organizations(id);
ALTER TABLE projects    ADD COLUMN org_id BIGINT REFERENCES organizations(id);
ALTER TABLE api_keys    ADD COLUMN org_id BIGINT REFERENCES organizations(id);
ALTER TABLE webhooks    ADD COLUMN org_id BIGINT REFERENCES organizations(id);
ALTER TABLE integrations ADD COLUMN org_id BIGINT REFERENCES organizations(id);
ALTER TABLE audit_events ADD COLUMN org_id BIGINT REFERENCES organizations(id);
```

Migration legt eine Default-Org `default` an und befüllt alle bestehenden Zeilen mit deren `id`.

### Datenisolation

- `org_id` wird bei Login als JWT-Claim `orgId` mitgegeben
- Alle Service-Queries filtern via JPA Specification: `root.get("orgId").eq(currentOrgId)`
- `OrganizationContextHolder` (ThreadLocal): setzt `orgId` aus JWT in jedem Request
- Cross-Org-Zugriff ist strukturell ausgeschlossen (kein Flag, kein Check — der Filter ist immer aktiv)

### API

```
POST   /api/v1/organizations                        (ADMIN)
GET    /api/v1/organizations/{slug}                 (Org-Member)
PUT    /api/v1/organizations/{slug}                 (Org-ADMIN)
GET    /api/v1/organizations/{slug}/members         (Org-Member)
POST   /api/v1/organizations/{slug}/members         (Org-ADMIN)
DELETE /api/v1/organizations/{slug}/members/{userId}(Org-ADMIN)
GET    /api/v1/organizations/{slug}/projects        (Org-Member)
POST   /api/v1/auth/switch-org/{orgId}             — neues JWT mit anderem orgId-Claim
```

### Frontend

- Org-Switcher in der Top-Navbar (nur sichtbar wenn Nutzer in mehreren Orgs ist)
- `/orgs` — Org-Liste (System-ADMIN)
- `/orgs/:slug/settings` — Org-Name, Mitglieder-Verwaltung (Org-ADMIN)

---

## Schritt 4: Service Management (`servicedesk`-Modul, V20)

### Datenmodell

```sql
CREATE TABLE service_desks (
    id           BIGSERIAL PRIMARY KEY,
    project_id   BIGINT NOT NULL REFERENCES projects(id),
    email_address VARCHAR(255),
    enabled      BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE sla_policies (
    id                  BIGSERIAL PRIMARY KEY,
    service_desk_id     BIGINT NOT NULL REFERENCES service_desks(id),
    name                VARCHAR(100) NOT NULL,
    priority            VARCHAR(20) NOT NULL, -- CRITICAL|HIGH|MEDIUM|LOW
    response_minutes    INT NOT NULL,
    resolution_minutes  INT NOT NULL
);

CREATE TABLE escalation_rules (
    id                    BIGSERIAL PRIMARY KEY,
    sla_policy_id         BIGINT NOT NULL REFERENCES sla_policies(id),
    escalate_after_minutes INT NOT NULL,
    assignee_id           BIGINT REFERENCES users(id),
    notify_user_ids       BIGINT[] NOT NULL DEFAULT '{}'
);

CREATE TABLE incidents (
    id                  BIGSERIAL PRIMARY KEY,
    issue_id            BIGINT NOT NULL REFERENCES issues(id),
    severity            VARCHAR(5) NOT NULL, -- P1|P2|P3|P4
    on_call_assignee_id BIGINT REFERENCES users(id),
    postmortem_body     TEXT,
    resolved_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### Ticket-Eingang

- **E-Mail:** konfigurierte Adresse (z.B. `support@instance.example.com`). Spring Integration (IMAP-Poller, neue Gradle-Dependency `spring-integration-mail`) parst eingehende Mails → `IssueService.createIssue(...)`. Subject = Title, Body = Description (Markdown). Erfordert IMAP-Konfiguration via `.env` (`TW_MAIL_IMAP_HOST`, `TW_MAIL_IMAP_USER`, `TW_MAIL_IMAP_PASS`).
- **Formular:** `POST /api/v1/projects/{key}/service-desk/tickets` — kein Auth erforderlich (permit-all in SecurityConfig)

### SLA & Eskalation

- SLA-Clock: `IssueStatusChangedEvent` → wenn neue Status-Kategorie = `IN_PROGRESS`, wird `sla_start_time` auf dem Issue gesetzt (neues Nullable-Feld via V20)
- `SlaMonitorJob` (`@Scheduled(fixedDelay = 60_000)`): selektiert Issues mit überschrittenem SLA, feuert `EscalationEvent`
- `EscalationListener`: sendet Notification an konfigurierte Nutzer, weist Issue ggf. neu zu
- SLA-Breach wird als `WRITE`-Level Audit-Event geloggt (`SLA_BREACHED`)

### Incident Management

- Jedes Issue kann per `POST /projects/{key}/incidents` als Incident markiert werden
- Severity P1–P4 (P1 = kritisch, P4 = niedrig)
- On-Call-Assignee + optionale Notify-Liste → sofortige Notification via bestehendem `NotificationService`
- Bei `PATCH .../incidents/{id}` mit `resolvedAt` gesetzt: Postmortem-Template wird automatisch als Comment am Issue angelegt

### API

```
POST   /projects/{key}/service-desk/enable
GET    /projects/{key}/service-desk
POST   /projects/{key}/sla-policies
GET    /projects/{key}/sla-policies
DELETE /projects/{key}/sla-policies/{id}
POST   /projects/{key}/sla-policies/{id}/escalation-rules
GET    /projects/{key}/service-desk/tickets
POST   /projects/{key}/service-desk/tickets              (permit-all)
POST   /projects/{key}/incidents
GET    /projects/{key}/incidents
PATCH  /projects/{key}/incidents/{id}
```

### Frontend

- "Service Desk"-Nav-Eintrag pro Projekt (nur wenn `service_desks.enabled = true`)
- Ticket-Queue-View: Liste mit SLA-Ampel (grün = ok, gelb = < 20% Zeit übrig, rot = breached)
- Incident-Dashboard: Karten mit Severity-Badge (P1 rot, P2 orange, P3 gelb, P4 grau)
- Postmortem-Editor: Markdown-Editor, öffnet automatisch nach Incident-Auflösung

---

## Flyway-Übersicht

| Version | Inhalt |
|---|---|
| V17 | `audit_events`, `audit_config` |
| V18 | `sso_configs` |
| V19 | `organizations`, `organization_members`, `org_id`-Spalten, Default-Org-Migration |
| V20 | `service_desks`, `sla_policies`, `escalation_rules`, `incidents`, `sla_start_time` auf `issues` |
