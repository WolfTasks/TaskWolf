# Personal Access Tokens + User-Lebenszyklus — Design

> Status: Entwurf zur Review · Datum: 2026-07-07 · Vorhaben #2

## Ziel

Nutzer sollen **persönliche Access Tokens** (PAT) erzeugen können, um externe
Tools/Skripte/CI gegen die TaskWolf-API zu authentifizieren — **an den Nutzer
gebunden**, mit **genau dessen Rechten**, optional **read-only**. Wie GitHub PATs.

Zusätzlich: Der Token hängt am Lebenszyklus des Nutzers. Wird ein Nutzer
**deaktiviert oder gelöscht**, werden seine Tokens sofort ungültig. Dafür braucht
es ein User-Aktiv-/Lösch-Konzept, das heute nicht existiert — dieses Vorhaben
liefert das Fundament **und** die zugehörige UI.

## Abgrenzung zu bestehenden `api_keys`

Es gibt bereits ein **projektbezogenes** API-Key-Feature aus Phase 6
(`tw_`-Prefix, `/api/v1/projects/{key}/api-keys`, admin-verwaltet, ohne Scope).
Dieses Vorhaben ist bewusst ein **eigenständiges, getrenntes Feature**:

| | Projekt-API-Keys (`tw_`) | Personal Access Tokens (`twk_`) |
|---|---|---|
| Bindung | Projekt | Nutzer (global) |
| Verwaltung | Projekt-Admin | jeder Nutzer, für sich selbst |
| Scope | keiner (Read-Write) | `READ_ONLY` / `READ_WRITE` |
| Revoke | Hard-Delete | Soft-Revoke (`revoked_at`) |
| Endpoint | `/api/v1/projects/{key}/api-keys` | `/api/v1/me/tokens` |

Die Prefixe `tw_` und `twk_` kollidieren nicht (bestehender Filter matcht
`Bearer tw_`, aber der neue Filter matcht `Bearer twk_` und beansprucht diese
Tokens exklusiv — siehe Filter-Reihenfolge unten). Die `api_keys` bleiben
unangetastet.

## Entscheidungen

| Frage | Entscheidung | Begründung |
|---|---|---|
| Architektur | Neue Tabelle/Entity/Service/Filter/Controller | Sauberes, isoliertes Feature; kein Umbau von Phase 6 |
| Prefix | `twk_` | Klar erkennbar, kollidiert nicht mit `tw_` |
| Hashing | SHA-256 (nur Hash gespeichert) | Deterministisch → per Hash lookup-fähig; BCrypt wäre nicht suchbar. Konsistent mit `ApiKeyService`/`RefreshTokenService` |
| Token-Erzeugung | `twk_` + 32 Byte `SecureRandom`, Base64URL ohne Padding | Kryptographisch sicher; Klartext nur einmalig zurückgegeben |
| Scope | Enum `TokenScope { READ_ONLY, READ_WRITE }` | Read-Only als ausdrücklicher Wunsch |
| Read-Only-Durchsetzung | Nur `GET`/`HEAD`/`OPTIONS` erlaubt, sonst `403` | Deckt alle Endpunkte automatisch ab, ohne Controller-Annotationen |
| Rechte | Systemrolle des Nutzers (`ROLE_<systemRole>`) | „Nur seine Rechte" — identisch zu einem normalen Login |
| Ablauf | Optional (30/60/90 Tage oder „nie", Default „nie") | Vertraute UX, wie bei `api_keys` |
| Revoke | Soft (`revoked_at` setzen) | Audit-Spur, verhindert Hash-Wiederverwendung |
| User-Deaktivierung | Neues `users.active`-Flag + Prüfung pro Request | Fundament für „User deaktiviert → Token tot" |
| User-Löschen | **Soft-Delete + Anonymisierung** (siehe unten) | Referenzielle Integrität über viele FKs bleibt erhalten |

### ⚠ Offener Bestätigungspunkt: Semantik von „Löschen"

Ein echtes DB-`DELETE` eines Users würde zahlreiche Fremdschlüssel verletzen
oder destruktiv kaskadieren: `projects.owner`, `api_keys.created_by`,
Issue-/Comment-Autoren, Sprints, Audit-Events u.v.m. Deshalb wird **„Konto
löschen" als Soft-Delete umgesetzt**:

- `active = false`, `deleted_at = now()`
- PII/Credentials entfernen bzw. anonymisieren: `email` → Platzhalter
  (z.B. `deleted-<uuid>@deleted.invalid`, erhält Unique-Constraint),
  `displayName` → „Deleted User", `passwordHash`/`oauthProvider`/`oauthSubject`/
  `avatarUrl` → `null`
- Alle Access Tokens des Nutzers werden widerrufen; Refresh Tokens gelöscht
- Fachliche Referenzen (Issues, Kommentare, erstellte Objekte) bleiben mit dem
  anonymisierten User erhalten

**→ Bitte bei der Spec-Review bestätigen.** Falls stattdessen ein hartes Löschen
gewünscht ist, ist das ein deutlich größeres, separates Vorhaben (Reassignment/
Cascade-Strategie pro referenzierender Tabelle) und sollte ausgegliedert werden.

## Datenmodell

### Migration `V26__access_tokens.sql`

```sql
CREATE TABLE access_tokens (
    id            UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id       UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name          VARCHAR(255) NOT NULL,
    token_hash    VARCHAR(64)  NOT NULL UNIQUE,
    token_prefix  VARCHAR(16)  NOT NULL,
    scope         VARCHAR(16)  NOT NULL,
    last_used_at  TIMESTAMPTZ,
    expires_at    TIMESTAMPTZ,
    revoked_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_access_tokens_user ON access_tokens (user_id);
CREATE INDEX idx_access_tokens_hash ON access_tokens (token_hash);
```

### Migration `V27__users_active.sql` (Fundament User-Lebenszyklus)

```sql
ALTER TABLE users ADD COLUMN active     BOOLEAN     NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMPTZ;
```

> Reihenfolge: `V26` und `V27` sind unabhängig; getrennte Migrationen halten die
> beiden Belange sauber trennbar. Beide gehören zu diesem Vorhaben.

### Entities / Enum (`backend/.../auth/domain/`)

- `TokenScope` — `enum class TokenScope { READ_ONLY, READ_WRITE }`
- `AccessToken` — `@Entity @Table("access_tokens")`, Felder analog Migration;
  `@Enumerated(EnumType.STRING) scope`; erbt `AuditableEntity` (id/created/updated).
  `var lastUsedAt`, `var revokedAt` (mutierbar); Rest `val`.
- `User` — neue Felder `var active: Boolean = true`, `var deletedAt: Instant? = null`.

## Backend-Architektur

### `AccessTokenService` (`auth/application/`) — TDD, Test zuerst

Öffentliche Schnittstelle:
- `create(user: User, name: String, scope: TokenScope, expiresAt: Instant?): CreateAccessTokenResponse`
  — generiert `twk_`+Random, speichert nur SHA-256-Hash + Prefix, gibt Klartext
  **einmalig** zurück.
- `list(user: User): List<AccessTokenResponse>` — alle **nicht widerrufenen**
  Tokens des Nutzers (Prefix, Name, Scope, lastUsedAt, expiresAt, createdAt).
- `revoke(user: User, tokenId: UUID)` — setzt `revoked_at`; nur eigene Tokens
  (sonst `NotFoundException`).
- `authenticate(rawToken: String): AuthenticatedToken?` — Kern der Auth:
  1. Prefix `twk_` prüfen, sonst `null`.
  2. SHA-256 hashen, per `token_hash` laden, sonst `null`.
  3. Ungültig (→ `null`) wenn: `revoked_at != null`, `expires_at` abgelaufen,
     User nicht gefunden, **`user.active == false`**.
  4. `last_used_at = now()` aktualisieren.
  5. Ergebnis trägt `User` **und** `TokenScope` (für Read-Only-Enforcement).

`AuthenticatedToken` = kleines Value-Object `(user: User, scope: TokenScope)`.

Hash-Helper wie im Projekt üblich (SHA-256, Hex) — identisch zu
`ApiKeyService.sha256`.

### `AccessTokenAuthFilter` (`auth/infrastructure/`)

`OncePerRequestFilter`, registriert **vor** `JwtAuthFilter` (neben
`ApiKeyAuthFilter`). Verhalten:

1. Header `Authorization: Bearer twk_…`? Sonst durchreichen.
2. `SecurityContext` bereits gesetzt? Dann durchreichen.
3. `authenticate(token)`:
   - `null` → durchreichen (führt später zu `401` durch die Chain).
   - Erfolg **und** `scope == READ_ONLY` **und** Methode ∉ `{GET, HEAD, OPTIONS}`
     → **`403`** mit klarer Fehlermeldung, Chain **nicht** fortsetzen.
   - Sonst: `UsernamePasswordAuthenticationToken(user, null, [ROLE_<systemRole>])`
     im `SecurityContext` setzen, durchreichen.

**Filter-Reihenfolge in `SecurityConfig`:** `accessTokenAuthFilter` und
`apiKeyAuthFilter` vor `jwtAuthFilter`. Da beide Filter jeweils nur ihren eigenen
Prefix (`twk_` bzw. `tw_`) beanspruchen, ist die relative Reihenfolge
untereinander unkritisch.

### API — `/api/v1/me/tokens` (`AccessTokenController`)

```
GET    /api/v1/me/tokens        → List<AccessTokenResponse>
POST   /api/v1/me/tokens        → CreateAccessTokenResponse (plaintext einmalig)   [201]
DELETE /api/v1/me/tokens/{id}   → revoke (revoked_at)                              [204]
```
Immer `@AuthenticationPrincipal user: User` — kein Projektkontext.

DTOs:
- `CreateAccessTokenRequest(name: @NotBlank String, scope: TokenScope = READ_WRITE, expiresAt: Instant?)`
- `CreateAccessTokenResponse(id, name, tokenPrefix, scope, plaintext)`
- `AccessTokenResponse(id, name, tokenPrefix, scope, lastUsedAt, expiresAt, createdAt)`

### User-Lebenszyklus — Endpoints

**Selbstverwaltung (jeder authentifizierte Nutzer):**
```
DELETE /api/v1/me                 → eigenes Konto löschen (Soft-Delete + Anonymisierung)  [204]
```

**Admin (`@PreAuthorize("hasRole('ADMIN')")`):** neuer `AdminUserController`
unter `/api/v1/admin/users`:
```
GET    /api/v1/admin/users              → Liste (id, email, displayName, systemRole, active)
POST   /api/v1/admin/users/{id}/deactivate  → active=false (+ Tokens widerrufen)   [204]
POST   /api/v1/admin/users/{id}/activate    → active=true                          [204]
DELETE /api/v1/admin/users/{id}             → Soft-Delete + Anonymisierung         [204]
```

Logik in `UserService` (bzw. Erweiterung des bestehenden Auth-Service-Layers):
- `deactivate(userId)` / `activate(userId)` — setzt `active`; bei Deaktivierung
  zusätzlich alle Access Tokens des Users widerrufen und Refresh Tokens löschen.
- `softDelete(userId)` — Anonymisierung wie oben, `active=false`,
  `deleted_at=now()`, Tokens/Refresh-Tokens invalidiert.
- Guard: Ein Admin kann sich nicht selbst über den Admin-Endpoint deaktivieren/
  löschen, wenn er der **letzte aktive Admin** ist (verhindert Aussperren).

**Wirkung auf bestehende Auth:** `JwtAuthFilter` und Login prüfen künftig
ebenfalls `user.active` — ein deaktivierter/gelöschter Nutzer kann sich weder
einloggen noch mit vorhandenem JWT weiterarbeiten. (Kleiner, aber notwendiger
Zusatz, damit Deaktivierung real greift — nicht nur für PATs.)

## Frontend

Es gibt heute **keine** persönlichen/Account-Settings-Routen (nur
projektbezogene). Dieses Vorhaben legt einen minimalen Account-Settings-Bereich
an.

**Neue Routen (unter `RequireAuth`):**
- `/settings/tokens` → `AccessTokensPage`
- `/settings/account` → `AccountSettingsPage` (Konto löschen)
- `/admin/users` → `AdminUsersPage`

**`AccessTokensPage`** (analog `ApiKeysPage`):
- „Create Token"-Dialog: Name, **Scope-Auswahl (Read-Only / Read-Write)**,
  optionales Ablaufdatum (30/60/90 Tage / nie).
- Nach Erstellung: Klartext **einmalig** anzeigen mit Copy-Button + Warnhinweis
  („wird nicht erneut angezeigt").
- Tabelle: Prefix, Name, Scope-Badge, Last Used, Expires, Revoke-Button.

**`AccountSettingsPage`**: Abschnitt „Konto löschen" mit Bestätigungsdialog
(Hinweis auf Anonymisierung + Token-Widerruf, irreversibel), ruft `DELETE /me`,
danach lokaler Logout.

**`AdminUsersPage`** (nur für `systemRole=ADMIN` sichtbar): Tabelle der Nutzer
mit Aktiv-Status; Aktionen Deaktivieren/Aktivieren/Löschen.

**Navigation (`AppLayout.tsx`):** Im Sidebar-Fußbereich ein „Account"-Bereich
mit Links zu „Access Tokens" und „Account". Der „Admin"-Bereich bekommt einen
Link „Users" (nur bei Admin-Rolle).

**Hooks:** `useAccessTokens` (list/create/revoke), `useDeleteAccount`,
`useAdminUsers` (list/activate/deactivate/delete) — Muster wie `useApiKeys`.

## Komponentengrenzen

- **`AccessTokenService`** — kapselt Erzeugung/Hashing/Validierung; einzige
  Stelle, die Klartext kennt. Konsumenten: Controller (CRUD) + Filter (auth).
- **`AccessTokenAuthFilter`** — übersetzt `twk_`-Header in einen
  `SecurityContext` und erzwingt Read-Only. Kennt nur den Service.
- **User-Lebenszyklus-Logik** — gekapselt im Service; Controller (self + admin)
  sind dünne Adapter. Anonymisierungsregeln an genau einer Stelle.

## Sicherheit

- Nur SHA-256-Hash gespeichert; eingehender Token wird vor DB-Lookup gehasht.
- Klartext nur einmalig bei Erstellung; nie geloggt.
- Read-Only-Tokens sind hart auf sichere HTTP-Methoden begrenzt (`403` sonst).
- Widerruf/Deaktivierung/Löschung wirken **sofort** (Prüfung pro Request, kein
  Caching der User-/Token-Gültigkeit).
- Letzter-Admin-Guard verhindert versehentliches Aussperren.
- Anonymisierung entfernt PII und Login-Credentials beim Löschen.

## Tests

**Backend (TDD, JUnit/MockK + Testcontainers-Postgres — Muster wie
`ApiKeyServiceTest`/`ApiKeyControllerTest`):**
- `AccessTokenServiceTest` (Unit):
  - `create` liefert Klartext mit `twk_`-Prefix, speichert nur Hash.
  - `authenticate` gibt User+Scope für gültigen Token.
  - `authenticate` → `null` bei: falschem Prefix, unbekanntem Hash, abgelaufen,
    widerrufen, **inaktivem/gelöschtem User**.
- `AccessTokenControllerTest` (Integration): CRUD + „Token authentifiziert
  Request"; **Read-Only-Token → `403` bei POST, `200` bei GET**.
- User-Lebenszyklus (Integration): Deaktivieren widerruft Tokens (Token danach
  `401`); Soft-Delete anonymisiert + invalidiert; Admin-Guard letzter Admin;
  deaktivierter User kann sich nicht einloggen.

**Frontend:** kein Test-Framework → `tsc && vite build` + manuelle Prüfung
(Token erstellen/Read-Only/Revoke; Konto löschen; Admin-Aktionen).

## Umfang / Nicht enthalten

- Feingranulare Scopes über Read-Only/Read-Write hinaus (z.B. pro Ressource).
- Token-Rotation/Ablauf-Benachrichtigungen.
- Hartes DB-Löschen von Usern inkl. Reassignment (bewusst ausgegliedert, s.o.).
- Änderungen an den bestehenden Projekt-`api_keys`.

## Betroffene Dateien (Überblick)

**Backend — neu:**
- `db/migration/V26__access_tokens.sql`, `db/migration/V27__users_active.sql`
- `auth/domain/TokenScope.kt`, `auth/domain/AccessToken.kt`
- `auth/infrastructure/AccessTokenRepository.kt`
- `auth/application/AccessTokenService.kt`
- `auth/infrastructure/AccessTokenAuthFilter.kt`
- `auth/api/AccessTokenController.kt` + DTOs (`CreateAccessTokenRequest/Response`,
  `AccessTokenResponse`)
- `auth/api/AdminUserController.kt`, User-Lebenszyklus-Service(-Erweiterung)
- Tests: `AccessTokenServiceTest`, `AccessTokenControllerTest`,
  User-Lebenszyklus-Integrationstest

**Backend — ändern:**
- `auth/domain/User.kt` (`active`, `deletedAt`)
- `auth/infrastructure/SecurityConfig.kt` (Filter registrieren)
- `auth/infrastructure/JwtAuthFilter.kt` + Login (aktiv-Prüfung)
- `auth/api/AuthController.kt` (`DELETE /api/v1/me`)

**Frontend — neu:**
- `pages/settings/AccessTokensPage.tsx`, `pages/settings/AccountSettingsPage.tsx`
- `pages/admin/AdminUsersPage.tsx`
- `hooks/useAccessTokens.ts`, `hooks/useAccount.ts`, `hooks/useAdminUsers.ts`

**Frontend — ändern:**
- `app/router.tsx`, `layouts/AppLayout.tsx`

## Wiki / Doku

Als Abschluss: Wiki-Doku ergänzen (PAT-Nutzung, Read-Only, User-Lebenszyklus)
und ggf. `ai-guide.md` um die neuen Auth-/Token-Muster erweitern.
