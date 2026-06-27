# Module: versions

## Purpose

Manages project-scoped release versions. Versions can be attached to issues in two distinct roles: **fix versions** (the release that resolves an issue) and **affects versions** (the releases where the problem is observed). Both roles are stored in a single join table with a `type` discriminator. Version CRUD is available from the dedicated settings page (`VersionsPage`), accessible via the project sidebar. Versions cannot be created on the fly from the issue detail view — they must be pre-created in settings.

---

## Entities Owned

| Entity | Table | Key Fields |
|---|---|---|
| `Version` | `versions` | `name` VARCHAR(50) NOT NULL, `project` FK→projects NOT NULL; UNIQUE (project_id, name) |
| `IssueVersion` | `issue_versions` | `issue_id` UUID, `version_id` UUID, `type` VARCHAR(8) CHECK IN ('FIX','AFFECTS'); PRIMARY KEY (issue_id, version_id, type) |

The `issue_versions` table is owned by the versions module, not by `Issue`. Both fix and affects assignments share this single table, differentiated by the `type` column.

---

## DB Schema

### `versions` (V24)

```sql
CREATE TABLE versions (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    project_id UUID        NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name       VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (project_id, name)
);
```

### `issue_versions` (V24)

Single join table for both fix and affects relationships. The `type` column is part of the primary key.

| Column | Type | Constraint |
|---|---|---|
| `issue_id` | UUID | FK→issues ON DELETE CASCADE |
| `version_id` | UUID | FK→versions ON DELETE CASCADE |
| `type` | VARCHAR(8) | CHECK (type IN ('FIX', 'AFFECTS')) |

Primary key: `(issue_id, version_id, type)`.

```sql
CREATE TABLE issue_versions (
    issue_id   UUID       NOT NULL REFERENCES issues(id)   ON DELETE CASCADE,
    version_id UUID       NOT NULL REFERENCES versions(id) ON DELETE CASCADE,
    type       VARCHAR(8) NOT NULL CHECK (type IN ('FIX', 'AFFECTS')),
    PRIMARY KEY (issue_id, version_id, type)
);
```

---

## Module Layout

```
versions/
  domain/
    Version.kt          — JPA entity
    IssueVersion.kt     — join table entity (@IdClass composite PK)
    IssueVersionId.kt   — composite PK data class (Serializable)
  infrastructure/
    VersionRepository.kt       — findByProjectId, existsByProjectIdAndName, findByIssueIdAndType (native)
    IssueVersionRepository.kt  — deleteByIssueIdAndType (@Modifying JPQL)
  application/
    VersionService.kt   — CRUD logic; checks project membership for all ops
  api/
    VersionController.kt
    dto/VersionRequest.kt   — {name}
    dto/VersionResponse.kt  — {id, name}
```

---

## API Endpoints

### `VersionController` — `/api/v1/projects/{key}/versions`

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/v1/projects/{key}/versions` | USER | Lists all versions for the project |
| POST | `/api/v1/projects/{key}/versions` | USER | Creates a version; returns 201 Created; 409 if name already exists in project |
| PUT | `/api/v1/projects/{key}/versions/{id}` | USER | Renames a version; 409 on name conflict |
| DELETE | `/api/v1/projects/{key}/versions/{id}` | USER | Deletes version; returns 204 No Content; `issue_versions` rows removed by DB cascade |

All endpoints require project membership (`ProjectService.requireMember()`).

---

## Events Emitted

None directly. Version assignment changes on issues are recorded as `IssueFieldChangedEvent` by `IssueService.update()` in the issues module (field names `"fixVersions"` and `"affectsVersions"`).

---

## Events Consumed

None.

---

## Issue Integration

### `PATCH /api/v1/projects/{key}/issues/{id}`

`UpdateIssueRequest` carries two optional version lists:

```kotlin
data class UpdateIssueRequest(
    // ...
    val fixVersionIds: List<UUID>? = null,
    val affectsVersionIds: List<UUID>? = null
)
```

Semantics follow the same null-vs-empty pattern as `labelIds`:

- `null` — field is absent from the PATCH; the existing version set is untouched.
- `[]` (empty list) — explicitly clears all versions of that type.
- Non-empty list — replaces the current set with the supplied version IDs.

`IssueService.update()` resolves each list against `VersionRepository.findAllById()`, filters to versions belonging to the current project, then calls `IssueVersionRepository.deleteByIssueIdAndType()` and re-inserts only if the resolved name set has actually changed.

Versions from a different project in the supplied IDs are **silently dropped** — no error is returned; only valid versions are applied.

### `GET /api/v1/projects/{key}/issues/{issueKey}`

`IssueController.get()` fetches both version sets explicitly via native SQL before building the response:

```kotlin
val fixVersions = versionRepository.findByIssueIdAndType(issue.id, "FIX")
    .map { VersionResponse.from(it) }
val affectsVersions = versionRepository.findByIssueIdAndType(issue.id, "AFFECTS")
    .map { VersionResponse.from(it) }
return IssueResponse.from(issue, refs, labels, fixVersions, affectsVersions)
```

`IssueResponse` includes `fixVersions: List<VersionResponse>` and `affectsVersions: List<VersionResponse>`.

---

## Issue List Filter

`GET /api/v1/projects/{key}/issues` accepts two optional version filter params:

| Param | Type | Description |
|---|---|---|
| `fixVersionId` | UUID (optional) | Return only issues where this version is a fix version |
| `affectsVersionId` | UUID (optional) | Return only issues where this version is an affects version |

Both params are AND-combinable — supplying both narrows results to issues that match on both axes simultaneously. Version filters take priority over the existing `labelId` filter — if any version filter param is present, `labelId` is not applied.

---

## Key Files

| File | Purpose |
|---|---|
| `backend/src/main/resources/db/migration/V24__versions.sql` | Creates `versions` and `issue_versions` tables |
| `backend/src/main/kotlin/com/taskowolf/versions/domain/Version.kt` | JPA entity |
| `backend/src/main/kotlin/com/taskowolf/versions/domain/IssueVersion.kt` | Join table entity with composite PK |
| `backend/src/main/kotlin/com/taskowolf/versions/domain/IssueVersionId.kt` | Composite PK data class |
| `backend/src/main/kotlin/com/taskowolf/versions/infrastructure/VersionRepository.kt` | `findByProjectId`, `existsByProjectIdAndName`, `findByIssueIdAndType` (native SQL) |
| `backend/src/main/kotlin/com/taskowolf/versions/infrastructure/IssueVersionRepository.kt` | `deleteByIssueIdAndType` (@Modifying JPQL) |
| `backend/src/main/kotlin/com/taskowolf/versions/application/VersionService.kt` | CRUD logic |
| `backend/src/main/kotlin/com/taskowolf/versions/api/VersionController.kt` | REST controller |
| `backend/src/main/kotlin/com/taskowolf/versions/api/dto/VersionRequest.kt` | `{name}` for POST/PUT |
| `backend/src/main/kotlin/com/taskowolf/versions/api/dto/VersionResponse.kt` | `{id, name}` |
| `backend/src/test/kotlin/com/taskowolf/versions/VersionServiceTest.kt` | Unit tests |
| `frontend/src/api/versions.ts` | `versionsApi` — list, create, update, delete HTTP calls |

---

## Key Design Decisions

- **Single join table with type discriminator.** Using one `issue_versions` table with a `type` column (rather than two separate tables `issue_fix_versions` / `issue_affects_versions`) keeps the schema compact and allows both roles to be queried with the same repository interface. The `type` column is part of the primary key, so the same version can appear as both a fix version and an affects version on the same issue.
- **No color field.** Versions are plain named entries — there is no color field (contrast with `labels`). The `VersionResponse` returns only `{id, name}`.
- **No on-the-fly creation.** Labels can be created inline from the issue detail selector; versions cannot. Versions must be created from the `VersionsPage` settings UI before they can be assigned to issues.

---

## Common Pitfalls

- **`VersionRepository` is injected in two cross-module locations.** `IssueController.get()` uses `findByIssueIdAndType` (native SQL) to load both version sets for the single-issue GET. `IssueService.update()` uses `findAllById` to resolve incoming version IDs before saving. Both are deliberate exceptions to the no-cross-module-injection rule.
- **`IssueVersionRepository` is injected into `IssueService` as a cross-module dependency.** Like `VersionRepository`, this is a deliberate exception to enforce transactional cleanup of version assignments. `IssueService.update()` calls `deleteByIssueIdAndType()` before re-inserting to detect actual changes and emit appropriate events.
- **`null` vs empty list on `UpdateIssueRequest.fixVersionIds` / `affectsVersionIds`.** `null` = no change; `[]` = clear all versions of that type. The service checks `request.fixVersionIds != null` before touching the set.
- **Versions from a different project are silently dropped.** `IssueService.update()` filters resolved versions by `it.project.id == project.id`. No error is returned — the call succeeds and only valid versions are applied.
- **`IssueVersionRepository.deleteByIssueIdAndType` is a `@Modifying` JPQL query.** It requires an active transaction and must run before `saveAll` in the same transaction. Do not call it outside a `@Transactional` context.

---

## Frontend Integration

| Concept | Detail |
|---|---|
| API client | `frontend/src/api/versions.ts` — `versionsApi.list`, `create`, `update`, `delete` |
| Hooks | `useVersions`, `useCreateVersion`, `useUpdateVersion`, `useDeleteVersion` (query key: `['versions', projectKey]`) |
| `VersionChip` | Read-only pill showing a version name |
| `VersionSelector` | Dropdown for assigning fix/affects versions on the issue detail sidebar |
| `VersionsPage` | Settings page at `/projects/{key}/settings/versions`; full CRUD |
| Issue list filter | `IssueListPage` stores active version filters in `?fixVersionId=<UUID>` and `?affectsVersionId=<UUID>` search params |

---

## Example

```kotlin
// Create a version then mark it as a fix version on an issue
val version = versionService.create("WOLF", VersionRequest("v1.1"), actor)
issueService.update(
    "WOLF", issueId,
    UpdateIssueRequest(fixVersionIds = listOf(version.id)),
    actor
)
```

---

## Test Patterns

| File | What is tested |
|---|---|
| `VersionServiceTest` | `list` returns versions for the correct project |
| `VersionServiceTest` | `create` saves a new version |
| `VersionServiceTest` | `create` throws `ConflictException` when name already exists in project |
| `VersionServiceTest` | `update` renames a version |
| `VersionServiceTest` | `update` throws `ConflictException` when new name already exists in project |
| `VersionServiceTest` | `delete` removes the version |
| `VersionServiceTest` | `delete` throws `NotFoundException` when version belongs to a different project |
| `IssueServiceTest` | `update` sets fix versions when `fixVersionIds` is a non-empty list |
| `IssueServiceTest` | `update` clears fix versions when `fixVersionIds` is an empty list |
| `IssueServiceTest` | `update` sets affects versions when `affectsVersionIds` is a non-empty list |
