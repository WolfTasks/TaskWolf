# Phase 6: Developer Tools & Integrations — Design Spec

**Date:** 2026-06-21
**Status:** Approved
**Depends on:** Phase 1 (Core Foundation)

---

## Ziel

Externe Systeme (CI-Pipelines, GitHub/GitLab, eigene Tools) können mit TaskWolf interagieren. TaskWolf feuert signierte Outgoing Webhooks bei konfigurierbaren Events. GitHub und GitLab können Commits und PRs automatisch mit Issues verlinken. Externe Tools authentifizieren sich über API Keys.

---

## Entscheidungen

| Frage | Entscheidung | Begründung |
|---|---|---|
| API Keys — Modul | `auth`-Modul (Erweiterung) | API Keys sind ein Auth-Mechanismus, konsistent mit JWT/OAuth2 |
| GitHub/GitLab Tiefe | Auto-Linking only (incoming Webhooks, kein Write-back) | Ausreichend für den primären Use Case; bidirektionale Schreiboperationen sind Phase-7-Scope |
| Webhook Events | Frei konfigurierbar per Webhook (Checkbox-Liste) | Maximale Flexibilität für externe Integrationen |
| Webhook Auth | API Keys + HMAC-SHA256 Signierung | API Keys für ausgehende Calls; Signierung für Empfänger-Verifikation |
| Integrations-Architektur | `integrations`-Modul (neu) + `auth` erweitern | Saubere Grenzen; API Keys gehören zu Auth, Rest zu Integrations |
| SSRF-Schutz | Blocklist privater IP-Ranges beim Speichern | Verhindert dass Webhooks interne Dienste ansprechen |
| Webhook-Deliveries Retention | 30 Tage, dann Cleanup-Job | Log bleibt nützlich ohne unbegrenzt zu wachsen |

---

## Architektur

### `auth`-Modul (Erweiterung)

Neuer `ApiKey`-Mechanismus parallel zu JWT und OAuth2:

- `ApiKey`-Entity: gespeicherter SHA-256-Hash (wie Refresh Tokens), Prefix für Anzeige
- Format: `tw_<32-random-chars>` — erkennbares Präfix, ähnlich `ghp_` bei GitHub
- `ApiKeyAuthFilter`: erkennt Bearer-Tokens mit `tw_`-Präfix, authentifiziert als Key-Ersteller
- Keys optional auf ein Projekt beschränkt (`project_id`)
- Plaintext wird nur einmalig bei Erstellung zurückgegeben

### `integrations`-Modul (neu)

Drei Concerns:

**1. Outgoing Webhooks**
- `WebhookDispatcher` hört alle `ApplicationEvent`s via `@EventListener`
- Filtert nach konfigurierten Event-Typen pro Webhook
- HTTP-POST mit JSON-Payload + `X-TaskWolf-Signature: sha256=<hmac>` Header
- HMAC-SHA256 mit dem gespeicherten Webhook-Secret
- Max. 3 Retry-Versuche mit exponential backoff (1 min → 5 min → 30 min)
- Jede Delivery wird geloggt (`webhook_deliveries`)

**2. GitHub/GitLab Auto-Linking**
- Admin konfiguriert pro Projekt eine Integration (Provider + Webhook-Secret)
- Incoming Webhook-Endpoints sind aus dem JWT-Filter ausgenommen
- Verifikation via HMAC-SHA256 (identisch zu GitHub's `X-Hub-Signature-256`)
- Parser: Regex `[A-Z]+-\d+` in Commit-Messages und PR/MR-Titles
- Gefundene Keys werden gegen `projects.key` geprüft, passende Issues werden mit `IssueRef` verlinkt

**3. SSRF-Schutz**
- Outgoing Webhook-URLs werden beim Speichern gegen private IP-Ranges geprüft:
  `localhost`, `127.x`, `10.x`, `172.16–31.x`, `192.168.x`, `169.254.x`, `::1`, `fd00::/8`
- Bei Treffer: 400 Bad Request

---

## Datenmodell

### V13 — `api_keys`

```sql
CREATE TABLE api_keys (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    key_hash    VARCHAR(64)  NOT NULL UNIQUE,
    key_prefix  VARCHAR(12)  NOT NULL,
    project_id  BIGINT REFERENCES projects(id) ON DELETE CASCADE,
    created_by  BIGINT NOT NULL REFERENCES users(id),
    last_used_at TIMESTAMP,
    expires_at  TIMESTAMP,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);
```

### V14 — `webhooks` + `webhook_deliveries`

```sql
CREATE TABLE webhooks (
    id          BIGSERIAL PRIMARY KEY,
    project_id  BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    url         VARCHAR(2048) NOT NULL,
    secret_hash VARCHAR(64)  NOT NULL,
    events      TEXT[]       NOT NULL,
    enabled     BOOLEAN      NOT NULL DEFAULT true,
    created_by  BIGINT NOT NULL REFERENCES users(id),
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE webhook_deliveries (
    id              BIGSERIAL PRIMARY KEY,
    webhook_id      BIGINT NOT NULL REFERENCES webhooks(id) ON DELETE CASCADE,
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB NOT NULL,
    response_status INTEGER,
    response_body   TEXT,
    attempt_count   INTEGER NOT NULL DEFAULT 0,
    next_retry_at   TIMESTAMP,
    delivered_at    TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);
```

### V15 — `project_integrations` + `issue_refs`

```sql
CREATE TABLE project_integrations (
    id                  BIGSERIAL PRIMARY KEY,
    project_id          BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    provider            VARCHAR(20) NOT NULL,  -- GITHUB | GITLAB
    webhook_secret_hash VARCHAR(64) NOT NULL,
    repo_url            VARCHAR(2048),
    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (project_id, provider)
);

CREATE TABLE issue_refs (
    id          BIGSERIAL PRIMARY KEY,
    issue_id    BIGINT NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    provider    VARCHAR(20) NOT NULL,     -- GITHUB | GITLAB
    ref_type    VARCHAR(20) NOT NULL,     -- COMMIT | PR | BRANCH
    external_id VARCHAR(255) NOT NULL,   -- SHA oder PR-Nummer
    url         VARCHAR(2048) NOT NULL,
    title       VARCHAR(1024),
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (issue_id, provider, ref_type, external_id)
);
```

**Invarianten:**
- `api_keys.key_hash` — nur SHA-256 gespeichert; Plaintext einmalig zurückgegeben und dann verworfen
- `webhooks.events` — PostgreSQL `text[]`; H2-Kompatibilität via Custom Type Converter
- `webhook_deliveries` — `@Scheduled`-Job (täglich, 02:00 UTC) löscht Records mit `created_at < now() - 30 days`
- `project_integrations.webhook_secret_hash` — SHA-256; Plaintext nur beim Anlegen sichtbar

---

## API

### API Keys — `/api/v1/projects/{key}/api-keys`

```
GET    /api/v1/projects/{key}/api-keys          → Liste (id, name, prefix, lastUsedAt, expiresAt)
POST   /api/v1/projects/{key}/api-keys          → Erstellen → { id, name, prefix, plaintext } einmalig
DELETE /api/v1/projects/{key}/api-keys/{id}     → Widerrufen
```

### Outgoing Webhooks — `/api/v1/projects/{key}/webhooks`

```
GET    /api/v1/projects/{key}/webhooks                          → Liste
POST   /api/v1/projects/{key}/webhooks                          → Erstellen
PUT    /api/v1/projects/{key}/webhooks/{id}                     → Bearbeiten
DELETE /api/v1/projects/{key}/webhooks/{id}                     → Löschen
GET    /api/v1/projects/{key}/webhooks/{id}/deliveries          → Delivery-Log (paginiert)
POST   /api/v1/projects/{key}/webhooks/{id}/test               → Test-Ping senden
```

### Incoming Webhooks — public (kein JWT, HMAC-Verifikation)

```
POST   /api/v1/integrations/github/webhook     → GitHub Push/PR Events
POST   /api/v1/integrations/gitlab/webhook     → GitLab Push/MR Events
```

### Projekt-Integrationen — `/api/v1/projects/{key}/integrations`

```
GET    /api/v1/projects/{key}/integrations             → Aktive Integrationen
POST   /api/v1/projects/{key}/integrations             → Einrichten → gibt webhookUrl + plaintext-Secret zurück
DELETE /api/v1/projects/{key}/integrations/{id}        → Entfernen
```

### Issue-Refs — Teil des Issue-Responses

```
GET    /api/v1/projects/{key}/issues/{id}      → IssueResponse erhält refs[]-Array
```

**Berechtigungen:** Alle schreibenden Endpoints erfordern Projekt-Admin-Rolle. Incoming-Webhook-Endpoints sind aus dem `JwtAuthFilter` ausgenommen.

**Konfigurierbare Event-Typen:**
```
issue.created        issue.updated       issue.status_changed
issue.assigned       issue.deleted       sprint.started
sprint.completed     comment.created     attachment.added
```

**Test-Ping-Payload** (`POST .../test`): Sendet ein synthetisches Event mit `event_type: "ping"` und `{"project": "<key>", "timestamp": "<iso8601>"}`. Wird ebenfalls als `webhook_delivery` geloggt.

---

## Frontend

### Neue Settings-Seiten (nur Admins)

**`/p/:key/settings/api-keys`**
Tabelle: Prefix, Name, Last Used, Ablaufdatum. "Create API Key"-Dialog: Name + optionales Ablaufdatum. Nach Erstellung: Plaintext einmalig anzeigen mit Copy-Button + Warnhinweis. Revoke-Button pro Row.

**`/p/:key/settings/webhooks`**
Liste aller Webhooks: URL (gekürzt), aktive Event-Typen als Badges, enabled/disabled Toggle.
"Add Webhook"-Dialog: URL-Input, Event-Checkbox-Liste (alle 9 Event-Typen), Secret-Feld (auto-generiert, editierbar).
Pro Webhook: Delivery-Log-Drawer (Event-Type, Timestamp, HTTP-Status, Response-Body expandierbar, "Redeliver"-Button), Test-Ping-Button.

**`/p/:key/settings/integrations`**
Zwei Karten: GitHub und GitLab.
- Nicht eingerichtet: "Connect"-Button → Dialog mit Webhook-URL (Copy-Button) + Secret-Feld (einmalig) + optionale Repo-URL.
- Eingerichtet: Status "Active", Repo-URL, "Remove"-Button.

### Erweiterung bestehender Seiten

**`IssueDetailPage`** — neuer "References"-Abschnitt unter dem Activity Feed: Liste verlinkter Commits/PRs mit Provider-Icon (GitHub/GitLab Octicon/Logo), Titel, Datum, externer Link.

**`AppLayout` Sidebar** — Settings-Bereich für Admins erweitert um: API Keys, Webhooks, Integrations.

---

## Sicherheit

- **SSRF:** Webhook-URL-Validierung beim POST/PUT gegen private IP-Ranges (Blocklist)
- **API Keys:** Nur SHA-256-Hash gespeichert; `ApiKeyAuthFilter` hasht den eingehenden Key vor DB-Lookup
- **Incoming Webhooks:** HMAC-SHA256-Verifikation (`X-Hub-Signature-256` für GitHub, `X-Gitlab-Token` für GitLab); bei Fehlschlag → 401
- **Outgoing Webhooks:** HMAC-SHA256 Signature im `X-TaskWolf-Signature`-Header; Empfänger kann authentizität prüfen
- **Secret-Storage:** `project_integrations.webhook_secret_hash` — SHA-256; Incoming-Secret wird beim Anlegen gehasht

---

## Nicht im Scope (Phase 6)

- Write-back zu GitHub/GitLab (PR-Kommentare, Labels setzen)
- CI/CD Build-Status-Anzeige auf Issues
- Slack / MS Teams Integrationen
- Webhook-Retry-Queue via Redis (Spring `@Async` + DB-State reicht für Phase 6)
- Globale (projektübergreifende) API Keys