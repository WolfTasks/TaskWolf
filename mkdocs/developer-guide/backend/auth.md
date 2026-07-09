# Module: auth

## Purpose

Manages user identity: JWT issuance and validation, OAuth2 (GitHub + Google), SSO/OIDC via dynamic DB-backed client registration, API key authentication, personal access tokens, refresh token rotation, role-based access control, and user lifecycle (deactivate/activate/soft-delete).

---

## Entities Owned

| Entity | Table | Key Fields |
|---|---|---|
| `User` | `users` | `email` (UNIQUE), `displayName`, `avatarUrl`, `passwordHash` (nullable), `oauthProvider`, `oauthSubject`, `systemRole: SystemRole`, `orgId: UUID?`, `active: Boolean` (default `true`), `deletedAt: Instant?` |
| `RefreshToken` | `refresh_tokens` | `tokenHash` (SHA-256 of the JWT string, UNIQUE), `userId` (FK→users), `expiresAt`, `revoked: Boolean` |
| `ApiKey` | `api_keys` | `name`, `keyHash` (SHA-256 of `tw_…` plaintext, UNIQUE), `keyPrefix` (first 12 chars of plaintext), `projectId` (FK→projects, nullable), `createdBy` (FK→users), `lastUsedAt`, `expiresAt` |
| `AccessToken` | `access_tokens` | `userId` (FK→users), `name`, `tokenHash` (SHA-256 of `twk_…` plaintext, UNIQUE), `tokenPrefix` (first 12 chars), `scope: TokenScope`, `lastUsedAt`, `expiresAt` (nullable = never), `revokedAt` (nullable) |
| `SsoConfig` | `sso_configs` | `name`, `issuerUrl`, `clientId`, `clientSecretEnc` (AES-GCM encrypted), `enabled`, `autoProvision` |

`TokenScope` is an enum with values `READ_ONLY` and `READ_WRITE`, mapped as `VARCHAR`.

`SystemRole` is an enum with values `ADMIN` and `MEMBER`. The first user to register is automatically promoted to `ADMIN`.

---

## DB Schema

### `users` (V1)

```sql
CREATE TABLE users (
    id            UUID         NOT NULL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    display_name  VARCHAR(255) NOT NULL,
    avatar_url    VARCHAR(512),
    password_hash VARCHAR(255),
    oauth_provider VARCHAR(50),
    oauth_subject VARCHAR(255),
    system_role   VARCHAR(50)  NOT NULL DEFAULT 'MEMBER',
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL
);
```

Index: `idx_users_email` on `email`.

`active` (V27, `BOOLEAN NOT NULL DEFAULT TRUE`) and `deleted_at` (V27, nullable `TIMESTAMPTZ`) were added by `V27__users_active.sql`. `active = false` blocks login (`AuthService.login`) and is re-checked on every JWT/access-token request. `deleted_at` is set by `UserAccountService.softDelete()`; the row itself is never removed.

### `refresh_tokens` (V6)

| Column | Type | Constraint |
|---|---|---|
| `id` | UUID | PK |
| `token_hash` | VARCHAR(64) | UNIQUE NOT NULL |
| `user_id` | UUID | FK→users ON DELETE CASCADE |
| `expires_at` | TIMESTAMP | NOT NULL |
| `revoked` | BOOLEAN | NOT NULL DEFAULT FALSE |

Index: `idx_refresh_tokens_user` on `user_id`.

### `api_keys` (V13)

| Column | Type | Constraint |
|---|---|---|
| `id` | UUID | PK |
| `name` | VARCHAR(255) | NOT NULL |
| `key_hash` | VARCHAR(64) | UNIQUE NOT NULL |
| `key_prefix` | VARCHAR(12) | NOT NULL |
| `project_id` | UUID | FK→projects ON DELETE CASCADE, nullable |
| `created_by` | UUID | FK→users NOT NULL |
| `last_used_at` | TIMESTAMPTZ | nullable |
| `expires_at` | TIMESTAMPTZ | nullable |

Indexes: `idx_api_keys_project`, `idx_api_keys_hash`.

### `access_tokens` (V28)

| Column | Type | Constraint |
|---|---|---|
| `id` | UUID | PK |
| `user_id` | UUID | FK→users ON DELETE CASCADE |
| `name` | VARCHAR(255) | NOT NULL |
| `token_hash` | VARCHAR(64) | UNIQUE NOT NULL |
| `token_prefix` | VARCHAR(16) | NOT NULL |
| `scope` | VARCHAR(16) | NOT NULL (`READ_ONLY` \| `READ_WRITE`) |
| `last_used_at` | TIMESTAMPTZ | nullable |
| `expires_at` | TIMESTAMPTZ | nullable — `NULL` means never expires |
| `revoked_at` | TIMESTAMPTZ | nullable — set on revoke, row is never deleted |

Indexes: `idx_access_tokens_user`, `idx_access_tokens_hash`.

### `sso_configs` (V18)

| Column | Type | Notes |
|---|---|---|
| `id` | UUID | PK |
| `name` | VARCHAR(100) | NOT NULL |
| `issuer_url` | VARCHAR(500) | NOT NULL |
| `client_id` | VARCHAR(255) | NOT NULL |
| `client_secret_enc` | VARCHAR(500) | AES-GCM encrypted with key derived from `TW_JWT_SECRET` |
| `enabled` | BOOLEAN | DEFAULT true |
| `auto_provision` | BOOLEAN | DEFAULT true — creates user on first OIDC login |

---

## API Endpoints

### `AuthController` — `/api/v1/auth`

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/v1/auth/register` | PUBLIC | Creates a new user account; first user becomes ADMIN |
| POST | `/api/v1/auth/login` | PUBLIC | Validates password, returns access + refresh tokens |
| POST | `/api/v1/auth/refresh` | PUBLIC | Rotates refresh token; returns new token pair |
| POST | `/api/v1/auth/logout` | USER | Revokes all refresh tokens for the authenticated user |
| GET | `/api/v1/auth/me` | USER | Returns `UserResponse` for the authenticated principal |
| POST | `/api/v1/auth/switch-org/{orgId}` | USER | Issues an access token scoped to the target org |

### `ApiKeyController` — `/api/v1/projects/{key}/api-keys`

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/v1/projects/{key}/api-keys` | USER | Lists API keys for a project (no plaintext) |
| POST | `/api/v1/projects/{key}/api-keys` | USER | Generates a new API key; returns plaintext once |
| DELETE | `/api/v1/projects/{key}/api-keys/{keyId}` | USER | Revokes (deletes) an API key; requires project admin |

### `AccessTokenController` — `/api/v1/me/tokens`

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/v1/me/tokens` | USER | Lists the authenticated user's tokens (no plaintext) |
| POST | `/api/v1/me/tokens` | USER | Creates a `twk_`-prefixed personal access token; returns plaintext once |
| DELETE | `/api/v1/me/tokens/{id}` | USER | Revokes (soft, sets `revokedAt`) a token owned by the caller |

### `MeController` — `/api/v1/me`

| Method | Path | Auth | Description |
|---|---|---|---|
| PATCH | `/api/v1/me` | USER | Updates the caller's `displayName` (`UserAccountService.updateProfile`); logs `PROFILE_UPDATED` via `SecurityAuditListener` |
| POST | `/api/v1/me/password` | USER | Changes the caller's password (`UserAccountService.changePassword`): verifies `currentPassword` against the stored hash, re-hashes `newPassword`, revokes all **refresh tokens** (forces re-login on other sessions) but leaves personal access tokens (`twk_`) untouched, and logs `PASSWORD_CHANGED` |
| DELETE | `/api/v1/me` | USER | Soft-deletes and anonymizes the caller's own account (`UserAccountService.softDelete`) |

### `AdminUserController` — `/api/v1/admin/users`

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/v1/admin/users` | ADMIN | Lists all users (`AdminUserResponse`, includes `active`) |
| POST | `/api/v1/admin/users/{id}/deactivate` | ADMIN | Deactivates a user; revokes their access tokens and refresh tokens |
| POST | `/api/v1/admin/users/{id}/activate` | ADMIN | Reactivates a previously deactivated user |
| DELETE | `/api/v1/admin/users/{id}` | ADMIN | Soft-deletes and anonymizes a user's account |

`deactivate` and `DELETE` both reject the request with `409` (via `ConflictException`) if the target is the last active `ADMIN` (`UserAccountService.requireNotLastActiveAdmin`).

### `SsoController` — `/api/v1/admin/sso`

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/v1/admin/sso/public` | PUBLIC | Lists enabled SSO configs (id + name only, no secrets) |
| GET | `/api/v1/admin/sso` | ADMIN | Lists all SSO configs with full details |
| POST | `/api/v1/admin/sso` | ADMIN | Creates a new SSO/OIDC provider config |
| DELETE | `/api/v1/admin/sso/{id}` | ADMIN | Deletes an SSO config |

---

## Events Emitted

None. Auth changes (registration, login, logout) are not published as domain events. Security-relevant actions are recorded directly via `SecurityAuditListener` (in the `audit` module).

---

## Events Consumed

None. No `@EventListener` annotations exist in `auth/application/`.

---

## Key Files

| File | Responsibility |
|---|---|
| `backend/src/main/kotlin/com/taskowolf/auth/domain/User.kt` | `@Entity` for `users`; extends `AuditableEntity` |
| `backend/src/main/kotlin/com/taskowolf/auth/domain/RefreshToken.kt` | `@Entity` for `refresh_tokens`; stores SHA-256 hash, not plaintext |
| `backend/src/main/kotlin/com/taskowolf/auth/domain/ApiKey.kt` | `@Entity` for `api_keys`; stores SHA-256 hash and `tw_`-prefixed 12-char prefix |
| `backend/src/main/kotlin/com/taskowolf/auth/domain/AccessToken.kt` | `@Entity` for `access_tokens`; stores SHA-256 hash and `twk_`-prefixed 12-char prefix; `scope: TokenScope` |
| `backend/src/main/kotlin/com/taskowolf/auth/domain/TokenScope.kt` | Enum `{ READ_ONLY, READ_WRITE }`; mapped as `VARCHAR` in DB |
| `backend/src/main/kotlin/com/taskowolf/auth/domain/SsoConfig.kt` | `@Entity` for `sso_configs`; `clientSecretEnc` is AES-GCM encrypted |
| `backend/src/main/kotlin/com/taskowolf/auth/domain/SystemRole.kt` | Enum `{ ADMIN, MEMBER }`; mapped as `VARCHAR` in DB |
| `backend/src/main/kotlin/com/taskowolf/auth/application/JwtService.kt` | Generates and validates HS256 JWTs; enforces `type` claim (`access`/`refresh`) to prevent token-type confusion; `@PostConstruct` validates secret is ≥ 32 bytes |
| `backend/src/main/kotlin/com/taskowolf/auth/application/AuthService.kt` | Register, login, refresh, logout; first user promoted to ADMIN; registration can be disabled via `taskowolf.auth.registration-enabled` |
| `backend/src/main/kotlin/com/taskowolf/auth/application/RefreshTokenService.kt` | Rotation: each consumed token is immediately revoked; stores SHA-256 hash of the JWT string |
| `backend/src/main/kotlin/com/taskowolf/auth/application/ApiKeyService.kt` | Generates `tw_`-prefixed tokens (24 random bytes, URL-safe base64); stores only SHA-256 hash; `authenticate()` is called per-request by `ApiKeyAuthFilter` |
| `backend/src/main/kotlin/com/taskowolf/auth/application/AccessTokenService.kt` | Generates `twk_`-prefixed personal access tokens (32 random bytes, URL-safe base64); `create`/`list`/`revoke`/`revokeAllForUser`/`authenticate`; `authenticate()` re-checks `user.active` and expiry on every call and returns `AuthenticatedToken(user, scope)` |
| `backend/src/main/kotlin/com/taskowolf/auth/application/UserAccountService.kt` | `updateProfile`/`changePassword`/`deactivate`/`activate`/`softDelete`; guards the last active admin via `requireNotLastActiveAdmin`; `softDelete` anonymizes PII and never deletes the row; both `deactivate` and `softDelete` call `AccessTokenService.revokeAllForUser` and `RefreshTokenService.revokeAllForUser`; `changePassword` calls only `RefreshTokenService.revokeAllForUser` (access tokens are left active) |
| `backend/src/main/kotlin/com/taskowolf/auth/api/MeController.kt` | `/api/v1/me` — `PATCH` (update display name), `POST /password` (change password), `DELETE` (self soft-delete) |
| `backend/src/main/kotlin/com/taskowolf/auth/application/SsoService.kt` | AES-GCM encrypt/decrypt for OIDC client secrets; encryption key is SHA-256 of `TW_JWT_SECRET` |
| `backend/src/main/kotlin/com/taskowolf/auth/application/OidcUserProvisioningService.kt` | Creates `User` on first OIDC login when `autoProvision=true`; auto-assigns to `default` org |
| `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/SecurityConfig.kt` | Spring Security filter chain; stateless JWT sessions; `@EnableMethodSecurity` for `@PreAuthorize`; routes `oauth2Login` through `DbClientRegistrationRepository` |
| `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/JwtAuthFilter.kt` | Extracts `Bearer <token>` header, validates with `JwtService`, populates `SecurityContext` with `User` principal and `ROLE_<systemRole>` authority |
| `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/ApiKeyAuthFilter.kt` | Runs before `JwtAuthFilter`; detects `Bearer tw_` prefix; delegates to `ApiKeyService.authenticate()` |
| `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/AccessTokenAuthFilter.kt` | Runs before `JwtAuthFilter`; detects `Bearer twk_` prefix; delegates to `AccessTokenService.authenticate()`; returns `403` if the token's scope is `READ_ONLY` and the request method is not `GET`/`HEAD`/`OPTIONS` |
| `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/AuthRateLimitFilter.kt` | IP-based sliding-window rate limiter applied to `/api/v1/auth/login` and `/api/v1/auth/register`; returns 429 on breach |
| `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/JwtStompInterceptor.kt` | Validates JWT in STOMP `CONNECT` frame `Authorization` header; sets `StompHeaderAccessor.user` so WebSocket subscriptions are authenticated |
| `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/DbClientRegistrationRepository.kt` | Implements `ClientRegistrationRepository` by reading live `sso_configs` rows; enables runtime OIDC provider registration without application restart |
| `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/RateLimiter.kt` | In-memory `ConcurrentHashMap` sliding-window counter; configurable via `taskowolf.auth.rate-limit.max-requests` (default 10) and `taskowolf.auth.rate-limit.window-seconds` (default 60) |

---

## Extension Points

**Adding a new OIDC/OAuth2 provider at runtime:**

Insert a row into `sso_configs` or call `POST /api/v1/admin/sso`. `DbClientRegistrationRepository.findByRegistrationId()` reads the DB on every OAuth2 authorization request — no code change or restart is needed.

```kotlin
// DbClientRegistrationRepository.kt — how dynamic registration works
override fun findByRegistrationId(registrationId: String): ClientRegistration? {
    val config = ssoService.listEnabled().find { it.id.toString() == registrationId }
        ?: return null
    return ClientRegistrations.fromOidcIssuerLocation(config.issuerUrl)
        .registrationId(registrationId)
        .clientId(config.clientId)
        .clientSecret(ssoService.decryptSecret(config.clientSecretEnc))
        .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
        .scope("openid", "profile", "email")
        .build()
}
```

**Adding a new role:**

1. Add the new value to `SystemRole` enum in `backend/src/main/kotlin/com/taskowolf/auth/domain/SystemRole.kt`.
2. Update `JwtAuthFilter` and `ApiKeyAuthFilter` if the authority string construction needs to change.
3. Add `@PreAuthorize("hasRole('NEW_ROLE')")` to the relevant controller methods.
4. Update `SecurityConfig.authorizeHttpRequests` if a URL-level check is required.

---

## Common Pitfalls

- **DO NOT** store API key tokens unhashed. Only `keyHash` (SHA-256 of the `tw_…` plaintext) is persisted. The plaintext is returned once in `CreateApiKeyResponse.plaintext` and never stored.
- **DO NOT** store personal access tokens unhashed. Only `tokenHash` (SHA-256 of the `twk_…` plaintext) is persisted. The plaintext is returned once in `CreateAccessTokenResponse.plaintext` and never stored.
- **DO NOT** confuse `tw_` (project API keys) with `twk_` (personal access tokens) — they are separate entities, tables, and filters, even though both use the SHA-256-hash-only-storage pattern.
- **DO NOT** inject `UserRepository` from outside the `auth` module. Other modules must use the `SecurityContext` principal (`@AuthenticationPrincipal User`) or an event/service boundary to access user data.
- **DO NOT** add `permitAll()` entries to `SecurityConfig` without a security review. Each new public route increases the attack surface.
- **DO NOT** hard-delete a `User` row when deactivating or deleting an account. `UserAccountService.softDelete()` anonymizes PII (`email`, `displayName`, `passwordHash`, `oauthProvider`, `oauthSubject`, `avatarUrl`) and sets `deletedAt`, but keeps the row for FK integrity with existing issues/comments/audit rows.
- **DO NOT** deactivate or delete the last active `ADMIN`. `UserAccountService.requireNotLastActiveAdmin()` throws `ConflictException` (409) when `countBySystemRoleAndActiveTrue(ADMIN) <= 1`.
- **Webhook secrets** (used for HMAC validation in the `integrations` module) are stored in plaintext intentionally — HMAC requires the raw value. Do not apply the API-key SHA-256 hashing pattern there.

---

## Example

API key generation endpoint — plaintext is returned once and never re-derivable:

```kotlin
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
fun create(
    @PathVariable key: String,
    @Valid @RequestBody request: CreateApiKeyRequest,
    @AuthenticationPrincipal user: User
) = apiKeyService.generate(key, request.name, request.expiresAt, user)

// In ApiKeyService.generate():
val plaintext = "tw_" + secureToken()          // 24 random bytes, base64url
val hash = sha256(plaintext)                    // SHA-256 hex string
val prefix = plaintext.take(12)                 // stored for display only
val savedKey = apiKeyRepository.save(
    ApiKey(name = name, keyHash = hash, keyPrefix = prefix,
           createdBy = user.id, expiresAt = expiresAt)
    // projectId and lastUsedAt use their nullable defaults
)
return CreateApiKeyResponse(savedKey.id, savedKey.name, savedKey.keyPrefix, plaintext)
```

The caller receives the full `plaintext` value once. All subsequent authentication uses `sha256(incomingToken)` compared against `keyHash`.

---

## Test Patterns

### Unit tests (MockK, no Spring context)

| File | What is tested |
|---|---|
| `AuthServiceTest` | Registration conflict, token pair generation, disabled registration, first-user ADMIN promotion, refresh token rejection |
| `JwtServiceTest` | `@PostConstruct` validation rejects blank and short secrets with actionable error messages |
| `RefreshTokenServiceTest` | Rotation: valid token is revoked on consume; unknown, revoked, and expired tokens are rejected |
| `ApiKeyServiceTest` | `generate` stores SHA-256 hash not plaintext; `authenticate` resolves user for valid token, returns null for non-`tw_` tokens, expired keys, and unknown hashes |
| `AccessTokenServiceTest` | `create` returns `twk_`-prefixed plaintext and stores hash only; `authenticate` resolves user + scope for a valid token, returns null for a non-`twk_` token, a revoked token, an expired token, and a token whose user is inactive |
| `UserAccountServiceTest` | `softDelete` anonymizes the user and revokes their tokens; `deactivate` is blocked when the target is the last active admin, allowed when other admins exist; `updateProfile` sets `displayName` and audits `PROFILE_UPDATED`; `changePassword` re-hashes the password, revokes refresh tokens, keeps PATs, and audits `PASSWORD_CHANGED`; `changePassword` rejects an incorrect current password |
| `SsoServiceTest` | AES-GCM encrypt/decrypt round-trip; `createConfig` persists encrypted (not plaintext) secret |
| `RateLimiterTest` | Sliding window: allows up to limit, rejects beyond limit, independent per IP, resets correctly |

### Integration tests (Spring Boot Test + MockMvc + real DB, extend `IntegrationTestBase`)

| File | What is tested |
|---|---|
| `AuthControllerIntegrationTest` | Full register → login flow; duplicate email returns 409 |
| `ApiKeyControllerTest` | Create key returns `tw_`-prefixed plaintext once; list returns keys without plaintext; delete removes key; API key token authenticates subsequent requests |
| `AccessTokenControllerTest` | Create/list/delete via `/api/v1/me/tokens`; a `READ_WRITE` token authenticates both GET and POST, a `READ_ONLY` token allows GET but is forbidden (403) on POST |
| `AdminUserControllerTest` | Admin deactivates a user and their token immediately stops authenticating; non-admin is forbidden from `/api/v1/admin/users`; self-`DELETE /api/v1/me` anonymizes the account and blocks subsequent login |
