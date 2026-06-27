# Module: projects

## Purpose

Manages projects (create/update/archive), project membership (invite/remove/roles), and per-project settings.

---

## Entities Owned

| Entity | Table | Key Fields |
|---|---|---|
| `Project` | `projects` | `key` VARCHAR(10) UNIQUE NOT NULL (2–10 uppercase letters/digits), `name`, `description` TEXT, `owner` FK→users NOT NULL, `workflow` FK→workflows nullable, `archived` BOOLEAN DEFAULT false, `nodeId` VARCHAR(100), `orgId` UUID |
| `ProjectMember` | `project_members` | `project` FK→projects NOT NULL, `user` FK→users NOT NULL, `role: ProjectRole` NOT NULL DEFAULT MEMBER; UNIQUE (project_id, user_id) |

`ProjectRole` is an enum with values `ADMIN`, `MEMBER`, `VIEWER`.

The project creator is automatically added to `project_members` with role `ADMIN` and a default workflow is created via `WorkflowService.createDefault()`.

---

## DB Schema

### `projects` (V2)

```sql
CREATE TABLE projects (
    id          UUID         NOT NULL PRIMARY KEY,
    "key"       VARCHAR(10)  NOT NULL UNIQUE,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    owner_id    UUID         NOT NULL REFERENCES users(id),
    archived    BOOLEAN      NOT NULL DEFAULT FALSE,
    node_id     VARCHAR(100),
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL
);
```

Index: `idx_projects_key` on `"key"`.

Note: `workflow_id` added by V3 (`V3__create_workflows.sql`); `org_id` added by V19 (`V19__organizations.sql`). Both columns are not present in the V2 `CREATE TABLE` above — they are applied via `ALTER TABLE` in their respective migrations.

### `project_members` (V2)

| Column | Type | Constraint |
|---|---|---|
| `id` | UUID | PK |
| `project_id` | UUID | FK→projects ON DELETE CASCADE |
| `user_id` | UUID | FK→users ON DELETE CASCADE |
| `role` | VARCHAR(50) | NOT NULL DEFAULT 'MEMBER' |
| `created_at` / `updated_at` | TIMESTAMP | NOT NULL |

Unique constraint: `(project_id, user_id)`.
Indexes: `idx_project_members_project` on `project_id`, `idx_project_members_user` on `user_id`.

---

## API Endpoints

### `ProjectController` — `/api/v1/projects`

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/v1/projects` | USER | Lists all projects where the caller is owner or member |
| POST | `/api/v1/projects` | USER | Creates a new project; creator becomes ADMIN member; default workflow created |
| GET | `/api/v1/projects/{key}` | USER | Returns single project; caller must be a member |
| GET | `/api/v1/projects/{key}/members` | USER | Lists all members of a project; caller must be a member |

`{key}` is the short project identifier (e.g. `TW`). Project `id` is never used in URLs.

---

## Events Emitted

None. Project creation and membership changes are not published as domain events. No `*Event.kt` files exist in `projects/domain/`.

---

## Events Consumed

None. No `@EventListener` annotations exist in `projects/application/`.

---

## Key Files

| File | Responsibility |
|---|---|
| `backend/src/main/kotlin/com/taskowolf/projects/domain/Project.kt` | `@Entity` for `projects`; extends `AuditableEntity`; `key` is the URL identifier |
| `backend/src/main/kotlin/com/taskowolf/projects/domain/ProjectMember.kt` | `@Entity` for `project_members`; stores `role: ProjectRole` |
| `backend/src/main/kotlin/com/taskowolf/projects/domain/ProjectRole.kt` | Enum `{ ADMIN, MEMBER, VIEWER }` |
| `backend/src/main/kotlin/com/taskowolf/projects/api/ProjectController.kt` | REST endpoints for project CRUD and member listing |
| `backend/src/main/kotlin/com/taskowolf/projects/api/dto/CreateProjectRequest.kt` | Validates `key` is 2–10 chars matching `[A-Z0-9]+`; `@NotBlank name` |
| `backend/src/main/kotlin/com/taskowolf/projects/api/dto/ProjectResponse.kt` | Serializes `id`, `key`, `name`, `description`, `ownerId`, `archived` |
| `backend/src/main/kotlin/com/taskowolf/projects/application/ProjectService.kt` | `create`, `findByKey`, `findById`, `requireMember`, `requireAdmin`, `isMember`, `isProjectAdmin` |
| `backend/src/main/kotlin/com/taskowolf/projects/infrastructure/ProjectRepository.kt` | Custom JPQL: `findAllByMemberOrOwner` (DISTINCT join to avoid duplicates) |
| `backend/src/main/kotlin/com/taskowolf/projects/infrastructure/ProjectMemberRepository.kt` | `existsByProjectIdAndUserId`, `findByProjectIdAndUserId`, `findAllByProjectId` |
| `backend/src/main/kotlin/com/taskowolf/projects/infrastructure/ProjectSecurity.kt` | `@Component("projectSecurity")` — SpEL bean for `@PreAuthorize` expressions in other modules |

---

## Extension Points

**To add a new project-scoped setting:**

Project settings live directly on `Project.kt`; there is no separate settings entity.

1. Add `@Column var newSetting: T` to `Project.kt`.
2. Create a Flyway migration V23+ that adds the column to `projects`.
3. Add the field to `CreateProjectRequest` or a new `UpdateProjectRequest` (note: `UpdateProjectRequest` does not yet exist and would need to be created).
4. Handle the field in `ProjectService.create()` / `ProjectService.update()`.

**To add a new project-level role:**

1. Add the new value to `ProjectRole.kt`.
2. Update all callers of `ProjectService.isProjectAdmin()` and `ProjectSecurity.isProjectAdmin()` if the admin check must expand.
3. Update `project_members.role` column constraints in a new migration if you want DB-level validation.

**Using `ProjectSecurity` in SpEL:**

The `@Component("projectSecurity")` bean is designed for use in `@PreAuthorize` method security:

```kotlin
// Example from another module's controller
@RestController
@RequestMapping("/api/v1/projects/{key}/resources")
class ResourceController(private val resourceService: ResourceService) {

    @DeleteMapping("/{id}")
    @PreAuthorize("@projectSecurity.isProjectAdmin(#key, authentication)")
    fun deleteResource(
        @PathVariable key: String,
        @PathVariable id: UUID,
        @AuthenticationPrincipal user: User
    ) {
        resourceService.delete(id)
    }
}
```

---

## Common Pitfalls

- Update and archive endpoints are not yet implemented. `ProjectController` currently exposes GET (list, single, members) and POST (create) only. There is no PATCH or archive endpoint; calling them returns 404.
- **DO NOT** bypass project membership checks. Always call `projectService.requireMember(key, user.id)` before returning any project data. Returning data based on `key` alone without a membership check exposes all projects to any authenticated user.
- **DO NOT** use project `id` in URLs. All project URLs use the `key` (e.g. `TW`). Using `id` breaks the URL contract and bypasses key-uniqueness enforcement.
- **DO NOT** call `ProjectMemberRepository` from outside the `projects` package without first going through `ProjectService`. Raw membership checks in other modules duplicate authorization logic.
- When querying `findAllByMemberOrOwner`, the DISTINCT in the JPQL query is required to prevent duplicate rows when a user is both owner and a member entry.

---

## Example

Membership check pattern — called by every read endpoint before returning project data:

```kotlin
// ProjectService.kt
@Transactional(readOnly = true)
fun requireMember(projectKey: String, userId: UUID): Project {
    val project = findByKey(projectKey)
    val isMember = memberRepository.existsByProjectIdAndUserId(project.id, userId)
    val isOwner = project.owner.id == userId
    if (!isMember && !isOwner) throw ForbiddenException("Not a member of project $projectKey")
    return project
}

// ProjectController.kt — every GET endpoint calls requireMember first
@GetMapping("/{key}")
fun get(@PathVariable key: String, @AuthenticationPrincipal user: User) =
    ProjectResponse.from(projectService.requireMember(key, user.id))
```

The owner check (`project.owner.id == userId`) exists because a project owner is not guaranteed to have a `ProjectMember` row — this is the fallback.

---

## Test Patterns

### Unit tests (MockK, no Spring context)

| File | What is tested |
|---|---|
| `ProjectServiceTest` | Duplicate key throws `ConflictException`; `create` saves project, adds ADMIN member, calls `WorkflowService.createDefault()` exactly once; `projectRepository.save` is called twice (once before, once after workflow assignment) |

### Integration tests (Spring Boot Test + MockMvc + real DB, extend `IntegrationTestBase`)

| File | What is tested |
|---|---|
| `ProjectAndIssueIntegrationTest` | `POST /projects` creates project with default workflow having 3 statuses; first issue gets key `{PREFIX}-1`, second gets `{PREFIX}-2`; non-member `GET /projects/{key}` returns 403 |
