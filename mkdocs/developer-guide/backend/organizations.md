# Module: organizations

## Purpose

Multi-tenancy layer. Each project, user, API key, webhook, integration, and audit event belongs to an organization. `OrganizationContextFilter` extracts the `orgId` from the JWT `Bearer` token on every request and stores it in `OrganizationContextHolder` (ThreadLocal). All per-org resource queries must filter by `OrganizationContextHolder.get()`. SSO configs (OIDC) are stored per-installation with AES-GCM encrypted client secrets.

---

## Entities Owned

| Entity | Table | Key Fields |
|---|---|---|
| `Organization` | `organizations` | `name` VARCHAR(100), `slug` VARCHAR(50) UNIQUE, extends `AuditableEntity` (id, createdAt, updatedAt) |
| `OrganizationMember` | `organization_members` | composite PK `OrganizationMemberId(orgId UUID, userId UUID)`, `role: OrgRole` |

`OrgRole` values: `OWNER`, `ADMIN`, `MEMBER`.

`sso_configs` (V18) is logically coupled to the org SSO feature. Key columns: `issuerUrl`, `clientId`, `clientSecretEnc` (AES-GCM ciphertext), `enabled`, `autoProvision`.

V19 added `org_id UUID REFERENCES organizations(id)` to: `users`, `projects`, `api_keys`, `webhooks`, `project_integrations`, `audit_events`. All existing rows were backfilled to a seed "Default" org on migration.

---

## DB Schema

### `sso_configs` (V18)

```sql
CREATE TABLE sso_configs (
    id                UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name              VARCHAR(100) NOT NULL,
    issuer_url        VARCHAR(500) NOT NULL,
    client_id         VARCHAR(255) NOT NULL,
    client_secret_enc VARCHAR(500) NOT NULL,
    enabled           BOOLEAN      NOT NULL DEFAULT true,
    auto_provision    BOOLEAN      NOT NULL DEFAULT true,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL
);
```

### `organizations`, `organization_members` (V19)

```sql
CREATE TABLE organizations (
    id         UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    slug       VARCHAR(50)  NOT NULL UNIQUE,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL
);
CREATE TABLE organization_members (
    org_id  UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role    VARCHAR(20) NOT NULL,
    PRIMARY KEY (org_id, user_id)
);
```

`slug` must match `^[a-z0-9-]+$`; enforced by `@Pattern` on `CreateOrganizationRequest`.

---

## API Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/v1/organizations` | ADMIN | List all organizations |
| `POST` | `/api/v1/organizations` | ADMIN | Create org; body: `{"name":"...","slug":"..."}` (slug regex `^[a-z0-9-]+$`); creator added as `OWNER`; returns 201 |
| `GET` | `/api/v1/organizations/mine` | USER | List orgs the authenticated user belongs to |
| `GET` | `/api/v1/organizations/{id}` | USER | Get org by UUID; calls `requireMembershipOrAdmin()` |
| `GET` | `/api/v1/organizations/{id}/members` | USER | List members; calls `requireMembershipOrAdmin()` |
| `POST` | `/api/v1/organizations/{id}/members` | ADMIN | Add member; body: `{"userId":"...","role":"MEMBER"}` |
| `DELETE` | `/api/v1/organizations/{id}/members/{userId}` | ADMIN | Remove member; returns 204 |

`requireMembershipOrAdmin(orgId, user)` in `OrganizationService` permits `SystemRole.ADMIN` unconditionally; for other users it loads all org members and checks if `user.id` appears in the list, throwing `AccessDeniedException` if not.

---

## Events Emitted

None. The organizations module does not publish domain events.

---

## Events Consumed

None. Organization context is established per-request by `OrganizationContextFilter`, not via events.

---

## Key Files

- `backend/src/main/kotlin/com/taskowolf/organizations/domain/Organization.kt`
- `backend/src/main/kotlin/com/taskowolf/organizations/domain/OrganizationMember.kt`
- `backend/src/main/kotlin/com/taskowolf/organizations/domain/OrganizationMemberId.kt`
- `backend/src/main/kotlin/com/taskowolf/organizations/domain/OrgRole.kt`
- `backend/src/main/kotlin/com/taskowolf/organizations/application/OrganizationService.kt`
- `backend/src/main/kotlin/com/taskowolf/organizations/infrastructure/OrganizationContextHolder.kt`
- `backend/src/main/kotlin/com/taskowolf/organizations/infrastructure/OrganizationContextFilter.kt`
- `backend/src/main/kotlin/com/taskowolf/organizations/infrastructure/OrganizationRepository.kt`
- `backend/src/main/kotlin/com/taskowolf/organizations/infrastructure/OrganizationMemberRepository.kt`
- `backend/src/main/kotlin/com/taskowolf/organizations/api/OrganizationController.kt`
- `backend/src/main/resources/db/migration/V18__sso_configs.sql`
- `backend/src/main/resources/db/migration/V19__organizations.sql`

---

## Extension Points

- **To scope a new resource to an organization:** add an `org_id UUID REFERENCES organizations(id)` column in a new migration (V23+); filter all repository queries by `OrganizationContextHolder.get()`.
- **To add org-aware JWT claims:** update `JwtService` to embed `orgId` when issuing tokens; `OrganizationContextFilter` will propagate the claim automatically because it reads `orgId` via `jwtService.extractOrgId(token)`.
- **To add a new org role:** add the value to `OrgRole`; no migration required (stored as VARCHAR). Update `requireMembershipOrAdmin()` if the new role requires different access semantics.

---

## Common Pitfalls

- **`OrganizationContextHolder` uses `ThreadLocal`.** It is cleared in the `finally` block of `OrganizationContextFilter` after every request. Never cache the result of `OrganizationContextHolder.get()` outside the current request thread (e.g., in a `@Component` field, a coroutine, or an `@Async` method). Reading it from a different thread returns `null`.
- **SSO client secrets are AES-GCM encrypted at rest.** The `client_secret_enc` column stores ciphertext only. Never write a plaintext OIDC client secret to `sso_configs`; decryption is handled by the auth module before use.
- **Known gap (Phase 7): membership check is O(n).** `requireMembershipOrAdmin()` calls `findByIdOrgId(orgId)` and checks `user.id` against the full member list in memory. There is no index on `user_id` alone in `organization_members`. Do not use this method on hot paths; add a `findByIdOrgIdAndIdUserId` derived query if per-user lookup frequency grows.
- **`org_id` columns are nullable.** V19 backfilled existing rows to the default org but did not add NOT NULL constraints. New queries must handle `org_id IS NULL` rows or explicitly filter them.
- **`OrganizationContextHolder.get()` returns `null` for unauthenticated requests.** `OrganizationContextFilter` only calls `set()` when a `Bearer` token is present. Public endpoints receive `null`; guard against it before dereferencing.

---

## Example

Reading the current org from `OrganizationContextHolder` inside a service:

```kotlin
@Service
class ProjectService(
    private val projectRepository: ProjectRepository
) {
    @Transactional(readOnly = true)
    fun listForCurrentOrg(): List<Project> {
        val orgId = OrganizationContextHolder.get()
            ?: error("No organization context — endpoint requires authentication")
        return projectRepository.findByOrgId(orgId)
    }
}
```

`OrganizationContextFilter` runs at `@Order(5)`, before Spring Security's authentication filter processes the JWT, so `orgId` is available for the full duration of the request.

---

## Test Patterns

- **`OrganizationServiceTest`** — pure unit test with MockK. Verifies: `create()` saves the org then saves the creator as `OWNER`; `listOrgsForUser()` returns empty list when user has no memberships; `addMember()` persists the correct role; `removeMember()` calls `deleteById` with the composite key; `requireMembershipOrAdmin()` permits `SystemRole.ADMIN` without loading members; throws `AccessDeniedException` for a non-member with `SystemRole.MEMBER`.
- **`OrganizationContextFilterTest`** — uses `MockHttpServletRequest` and `MockHttpServletResponse`; mocks `JwtService`. Verifies that a `Bearer token` header causes `OrganizationContextHolder.get()` to return the UUID extracted by `jwtService.extractOrgId()` during `doFilter`. Captures the value inside the filter chain lambda.
