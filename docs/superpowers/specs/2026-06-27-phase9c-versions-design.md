# Phase 9c — Versions: Design Spec

**Date:** 2026-06-27  
**Status:** Approved

---

## Overview

Project-scoped versions (release milestones) that can be assigned to issues in two roles:

- **Fix Version** — the version in which this issue will be / was resolved
- **Affects Version** — the version(s) of the product affected by this issue

Both roles are supported per issue (multi-select each). A dedicated settings page handles CRUD; the issue detail sidebar surfaces both selectors; the issue list offers two independent filter dropdowns.

---

## Database Schema — V24

```sql
CREATE TABLE versions (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    project_id UUID        NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name       VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (project_id, name)
);

CREATE TABLE issue_versions (
    issue_id   UUID       NOT NULL REFERENCES issues(id)    ON DELETE CASCADE,
    version_id UUID       NOT NULL REFERENCES versions(id)  ON DELETE CASCADE,
    type       VARCHAR(8) NOT NULL CHECK (type IN ('FIX', 'AFFECTS')),
    PRIMARY KEY (issue_id, version_id, type)
);
```

The composite PK `(issue_id, version_id, type)` allows the same version to appear as both FIX and AFFECTS on the same issue.

---

## Backend

### Module structure (mirrors `labels`)

```
com.taskowolf.versions/
  domain/         Version.kt
  infrastructure/ VersionRepository.kt
  application/    VersionService.kt
  api/
    VersionController.kt
    dto/  VersionRequest.kt  VersionResponse.kt
```

### REST endpoints

Base path: `/api/v1/projects/{key}/versions`

| Method   | Path    | Description                          |
|----------|---------|--------------------------------------|
| `GET`    | `/`     | List all versions for the project    |
| `POST`   | `/`     | Create a version                     |
| `PUT`    | `/{id}` | Rename a version                     |
| `DELETE` | `/{id}` | Delete a version (cascades to `issue_versions`) |

### DTOs

```kotlin
// VersionRequest
data class VersionRequest(@field:NotBlank @field:Size(max = 50) val name: String)

// VersionResponse
data class VersionResponse(val id: UUID, val name: String)
```

### Issue integration

**`UpdateIssueRequest`** — two new optional fields:

```kotlin
val fixVersionIds: List<UUID>?      // null = no change; empty list = remove all
val affectsVersionIds: List<UUID>?  // null = no change; empty list = remove all
```

**`IssueResponse`** — two new fields:

```kotlin
val fixVersions: List<VersionResponse>
val affectsVersions: List<VersionResponse>
```

**`IssueService.update()`** — processes `fixVersionIds` and `affectsVersionIds` independently, writing to `issue_versions` with the appropriate `type`. Mirrors the `labelIds` handling from Phase 9b.

**`VersionRepository`** — native SQL query `findByIssueIdAndType(issueId: UUID, type: String): List<Version>` used to populate IssueResponse.

### Issue list filter

`IssueController.list()` gets two new optional query parameters:

- `fixVersionId: UUID?`
- `affectsVersionId: UUID?`

Applied independently via JOIN on `issue_versions`. Both can be active simultaneously.

### Tests

`VersionServiceTest` — covers create, update (rename), delete, name-conflict (same project), and the usual membership guard. Pattern from `LabelServiceTest`.

---

## Frontend

### Types (`types/index.ts`)

```typescript
interface Version {
  id: string
  name: string
}
// Issue gains:
//   fixVersions: Version[]
//   affectsVersions: Version[]
```

### API + Hook

- `api/versions.ts` — `list`, `create`, `update`, `delete` (mirrors `labels.ts`)
- `hooks/useVersions.ts` — `useVersions(projectKey)` (mirrors `useLabels.ts`)

### Components

**`VersionChip`** — displays a version name as a small chip. No colour (versions are minimal). `onClick` stops propagation.

**`VersionSelector`** — multi-select dropdown for picking versions from the project's version list. No on-the-fly create (versions are managed on the settings page; labels had inline create because colours are per-label — versions don't need that).

### IssueDetailPage sidebar

Two new sidebar fields, rendered below Labels:

| Field           | Component        | UpdateIssueRequest field |
|-----------------|------------------|--------------------------|
| Fix Versions    | VersionSelector  | `fixVersionIds`          |
| Affects Versions| VersionSelector  | `affectsVersionIds`      |

### VersionsPage (Settings)

`pages/settings/VersionsPage.tsx` — list, create, rename, delete. No colour picker (minimal). AppLayout nav link added under Settings, same position pattern as LabelsPage.

Route: `/projects/:key/settings/versions`

### Issue list filter

Two independent filter dropdowns in the issue list toolbar, each showing the project's versions:

- **Fix Version** filter → sets `fixVersionId` query param
- **Affects Version** filter → sets `affectsVersionId` query param

Both filters are optional and independent (AND-combined when both active).

**Chip-to-filter:** Clicking a `VersionChip` on the IssueDetailPage sets the corresponding filter in the issue list (Fix chip → Fix Version filter, Affects chip → Affects Version filter), matching the label chip-to-filter behaviour from Phase 9b.

---

## Out of scope (deferred)

- Version status / lifecycle (Unreleased / Released / Archived)
- Version description or release date
- Multi-label-style filter (single version per filter slot is sufficient for now)
- Version-based reporting / roadmap view
