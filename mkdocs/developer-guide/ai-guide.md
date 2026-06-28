# AI Implementation Guide

Read this page before writing any code. Every section is mandatory.

## Pre-Implementation Checklist

Complete every item before touching any file:

1. Read [Conventions](conventions.md) — all cross-cutting rules apply to every module.
2. Read the module page for the area you are modifying (e.g. `backend/issues.md`).
3. Check the current Flyway version: **V25** (`V25__custom_fields.sql`). The next migration must be **V26**.
4. Run `cd backend && ./gradlew test` — establish a passing baseline before making any changes.
5. Identify which pattern sections below apply to every layer you will touch.
6. **Do not infer patterns from source code.** Source files reflect history, not intent. Use this page as the source of truth for patterns.

---

## Pattern Catalogue

### Backend: Entity

Extend `AuditableEntity` (provides `id: UUID`, `createdAt: Instant`, `updatedAt: Instant`). Use `FetchType.LAZY` on all associations. Immutable fields are `val`; mutable fields are `var`.

```kotlin
// backend/src/main/kotlin/com/taskowolf/labels/domain/Label.kt
@Entity
@Table(
    name = "labels",
    uniqueConstraints = [UniqueConstraint(columnNames = ["project_id", "name"])]
)
class Label(
    @Column(nullable = false, length = 50)
    var name: String,

    @Column(nullable = false, length = 7)
    var color: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    val project: Project
) : AuditableEntity()
```

> **DO NOT** declare `id`, `createdAt`, or `updatedAt` — they are inherited from `AuditableEntity`.  
> **DO NOT** use `FetchType.EAGER` — it causes N+1 queries.

---

### Backend: Repository

Extend `JpaRepository<Entity, UUID>`. Use Spring Data method-name queries for simple lookups. Use `@Query` with `nativeQuery = true` only for joins that cannot be expressed as method names. For filterable list endpoints, also extend `JpaSpecificationExecutor<Entity>`.

```kotlin
// backend/src/main/kotlin/com/taskowolf/labels/infrastructure/LabelRepository.kt
interface LabelRepository : JpaRepository<Label, UUID> {
    fun findByProjectId(projectId: UUID): List<Label>
    fun existsByProjectIdAndName(projectId: UUID, name: String): Boolean

    @Query(
        value = "SELECT l.* FROM labels l INNER JOIN issue_labels il ON l.id = il.label_id WHERE il.issue_id = :issueId",
        nativeQuery = true
    )
    fun findByIssueId(@Param("issueId") issueId: UUID): List<Label>
}
```

> **DO NOT** use `@Query` JPQL for filterable list endpoints — use `JpaSpecificationExecutor` and `Specification<T>` (see `issues/infrastructure/IssueSpecification.kt`).

---

### Backend: Service

Use `@Service` with constructor injection. Annotate read methods with `@Transactional(readOnly = true)` and write methods with `@Transactional`. Publish cross-module side effects via `DomainEventPublisher` — never inject a `@Service` from another module (exception: `ProjectService` is a shared dependency used across modules).

```kotlin
// backend/src/main/kotlin/com/taskowolf/labels/application/LabelService.kt
@Service
class LabelService(
    private val labelRepository: LabelRepository,
    private val projectService: ProjectService
) {
    @Transactional(readOnly = true)
    fun list(projectKey: String, userId: UUID): List<Label> {
        val project = projectService.requireMember(projectKey, userId)
        return labelRepository.findByProjectId(project.id)
    }

    @Transactional
    fun create(projectKey: String, request: LabelRequest, actor: User): Label {
        val project = projectService.requireMember(projectKey, actor.id)
        if (labelRepository.existsByProjectIdAndName(project.id, request.name)) {
            throw ConflictException("Label '${request.name}' already exists in this project")
        }
        return labelRepository.save(Label(name = request.name, color = request.color, project = project))
    }

    @Transactional
    fun delete(projectKey: String, labelId: UUID, actor: User) {
        val project = projectService.requireMember(projectKey, actor.id)
        val label = labelRepository.findById(labelId)
            .filter { it.project.id == project.id }
            .orElseThrow { NotFoundException("Label not found: $labelId") }
        labelRepository.delete(label)
    }
    // ... update omitted for brevity
}
```

> **DO NOT** use field injection (`@Autowired var repo: LabelRepository`) — use constructor injection.  
> **DO NOT** inject a `@Service` from another module — publish a `DomainEventPublisher` event instead.  
> **DO NOT** omit `@Transactional` on write methods — dirty reads and partial writes will occur.

---

### Backend: Controller

No business logic in controllers. Delegate entirely to the service. Return mapped DTOs (via a companion `.from()` function), never raw entities. Use `@AuthenticationPrincipal User` for the current user. Set `@ResponseStatus` for non-200 responses.

```kotlin
// backend/src/main/kotlin/com/taskowolf/labels/api/LabelController.kt
@RestController
@RequestMapping("/api/v1/projects/{key}/labels")
class LabelController(private val labelService: LabelService) {

    @GetMapping
    fun list(@PathVariable key: String, @AuthenticationPrincipal user: User) =
        labelService.list(key, user.id).map { LabelResponse.from(it) }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @PathVariable key: String,
        @Valid @RequestBody request: LabelRequest,
        @AuthenticationPrincipal user: User
    ) = LabelResponse.from(labelService.create(key, request, user))

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable key: String,
        @PathVariable id: UUID,
        @AuthenticationPrincipal user: User
    ) = labelService.delete(key, id, user)
    // ... update omitted for brevity
}
```

> **DO NOT** put business logic, repository calls, or exception handling in controllers.  
> **DO NOT** return entity objects directly — always map to a DTO via `.from()`.

---

### Backend: Domain Event

Place event data classes in `<module>/domain/events/`. Name them `<Entity><Action>Event`. Publish via `DomainEventPublisher.publish()`. Consume in the target module's service using `@EventListener`. Events are synchronous (same thread, same transaction).

```kotlin
// Publishing side — in IssueService:
data class IssueCreatedEvent(
    val issueId: UUID,
    val projectId: UUID,
    val reporterId: UUID
)

eventPublisher.publish(IssueCreatedEvent(issue.id, project.id, reporter.id))

// Consuming side — in NotificationService:
@EventListener
fun onIssueCreated(event: IssueCreatedEvent) {
    // react to the event
}
```

> **DO NOT** return a value from `@EventListener` methods — the return value is ignored.  
> **DO NOT** publish events from `@Controller` or `@Repository` — only from `@Service`.  
> **DO NOT** call `ApplicationEventPublisher` directly — use `DomainEventPublisher` (wrapper in `core`).

---

### Backend: Unit Test

Use MockK (`io.mockk`), not Mockito. Instantiate the class under test directly with mocked dependencies — do not load a Spring context. Use `every { } returns` for stubs, `every { } answers { }` when you need to return the argument, and `verify { }` for interaction assertions.

```kotlin
// backend/src/test/kotlin/com/taskowolf/labels/LabelServiceTest.kt
class LabelServiceTest {
    private val labelRepository = mockk<LabelRepository>()
    private val projectService = mockk<ProjectService>()
    private val service = LabelService(labelRepository, projectService)

    private val actor = User(email = "alice@test.com", displayName = "Alice")
    private val project = Project(key = "WOLF", name = "TaskWolf", owner = actor, workflow = null)

    @Test
    fun `create saves new label`() {
        every { projectService.requireMember("WOLF", actor.id) } returns project
        every { labelRepository.existsByProjectIdAndName(project.id, "bug") } returns false
        every { labelRepository.save(any()) } answers { firstArg() }

        val result = service.create("WOLF", LabelRequest("bug", "#e11d48"), actor)

        assertEquals("bug", result.name)
        verify { labelRepository.save(any()) }
    }

    @Test
    fun `create throws ConflictException when name already exists`() {
        every { projectService.requireMember("WOLF", actor.id) } returns project
        every { labelRepository.existsByProjectIdAndName(project.id, "bug") } returns true

        assertThrows<ConflictException> {
            service.create("WOLF", LabelRequest("bug", "#e11d48"), actor)
        }
    }
}
```

> **DO NOT** use Mockito (`mock()`, `when()`, `thenReturn()`) — use MockK (`mockk<T>()`, `every {}`, `verify {}`).  
> **DO NOT** annotate unit test classes with `@SpringBootTest` — instantiate directly.

---

### Backend: Flyway Migration

File: `backend/src/main/resources/db/migration/V{n}__{description}.sql`. Current version is **V25** — the next file must be named **V26**. Use PostgreSQL-native syntax. Avoid `JSONB` (not supported by H2 in tests).

```sql
-- backend/src/main/resources/db/migration/V23__labels.sql
CREATE TABLE labels (
    id         UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    project_id UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name       VARCHAR(50)  NOT NULL,
    color      VARCHAR(7)   NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (project_id, name)
);
-- issue_labels join table omitted for brevity
```

> **DO NOT** use `JSONB` columns — H2 does not support it.  
> **DO NOT** use `SERIAL` or `BIGSERIAL` for primary keys — use `UUID DEFAULT gen_random_uuid()`.  
> **DO NOT** skip `ON DELETE CASCADE` on foreign keys to `projects(id)` — orphaned rows will cause constraint violations.  
> **DO NOT** use a version number already taken — check `backend/src/main/resources/db/migration/` first.

---

### Frontend: API Module

One file per resource in `frontend/src/api/`. Export a single `const` object with one method per operation. All methods call `apiClient.get/post/put/delete`. Import types from `@/types`.

```typescript
// frontend/src/api/labels.ts
import { apiClient } from './client'
import type { Label } from '@/types'

export const labelsApi = {
  list: (projectKey: string) =>
    apiClient.get<Label[]>(`/projects/${projectKey}/labels`),
  create: (projectKey: string, data: { name: string; color: string }) =>
    apiClient.post<Label>(`/projects/${projectKey}/labels`, data),
  update: (projectKey: string, id: string, data: { name: string; color: string }) =>
    apiClient.put<Label>(`/projects/${projectKey}/labels/${id}`, data),
  delete: (projectKey: string, id: string) =>
    apiClient.delete(`/projects/${projectKey}/labels/${id}`),
}
```

> **DO NOT** call `apiClient` directly from a component or page — always via a hook.  
> **DO NOT** add business logic to API module functions — they are thin wrappers only.

---

### Frontend: Hook

One file per resource in `frontend/src/hooks/`. Prefix all exports with `use`. Query key is always an array: `['resource', projectKey]`. Mutations call `queryClient.invalidateQueries` with the same key on success.

```typescript
// frontend/src/hooks/useLabels.ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { labelsApi } from '@/api/labels'

export function useLabels(projectKey: string) {
  return useQuery({
    queryKey: ['labels', projectKey],
    queryFn: () => labelsApi.list(projectKey).then(r => r.data),
  })
}

export function useCreateLabel(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: { name: string; color: string }) =>
      labelsApi.create(projectKey, data).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['labels', projectKey] }),
  })
}

export function useDeleteLabel(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => labelsApi.delete(projectKey, id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['labels', projectKey] }),
  })
}
// ... useUpdateLabel omitted for brevity
```

> **DO NOT** use `useState` + `useEffect` + `fetch` for server data — use React Query.  
> **DO NOT** use a string as the query key — always an array: `['labels', projectKey]`, not `'labels'`.  
> **DO NOT** omit `projectKey` from the query key for project-scoped resources — cross-project cache collisions will occur.

---

### Frontend: Component

One component per file. Filename matches the exported component name (e.g. `LabelChip.tsx` exports `LabelChip`). Import shadcn/ui from `@/components/ui/` (the local copy), never the npm package. Use Tailwind for all static styling. Use `cn()` for conditional class names. Use `style` only for dynamic values (e.g. a runtime color).

```typescript
// frontend/src/components/issue/LabelChip.tsx
import type { Label } from '@/types'

interface Props {
  label: Label
  onClick?: () => void
}

export function LabelChip({ label, onClick }: Props) {
  const base = 'inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium border'
  return (
    <span
      className={`${base} ${onClick ? 'cursor-pointer hover:opacity-80' : ''}`}
      style={{
        backgroundColor: label.color + '26',  // ~15% opacity
        color: label.color,
        borderColor: label.color + '4d',      // ~30% opacity
      }}
      onClick={onClick ? (e: React.MouseEvent) => { e.stopPropagation(); onClick() } : undefined}
    >
      {label.name}
    </span>
  )
}
```

> **DO NOT** import shadcn components from the npm package — import from `@/components/ui/`.  
> **DO NOT** use inline `style` props for static values — use Tailwind classes.  
> **DO NOT** put data fetching or hook calls inside presentational components — fetch in the parent page or container.

---

## Architecture Decisions

| Decision | Rejected | Reason |
|---|---|---|
| MockK (`io.mockk`) | Mockito | Kotlin-idiomatic DSL; `every {}` matches Kotlin syntax; Mockito requires Java-style verbosity |
| `DomainEventPublisher` for cross-module side effects | Direct `@Service` injection across modules | Enforces decoupling; prevents circular dependencies; modules can evolve independently |
| React Query for all server state | `useState` + `useEffect` + manual fetch | Automatic caching, background refetch, loading/error states; no boilerplate |
| Zustand for UI-only state | Redux or React Query for UI state | Minimal boilerplate; clear boundary: Zustand = UI display state, React Query = server state |
| Project key in all URLs (`/api/v1/projects/{key}/...`) | Project UUID in URLs | Human-readable; enables future nginx sharding by key prefix |
| JPA `Specification<T>` for composable filters | if-return-early JPQL query method | Adding a filter does not require rewriting the query; filters compose with AND |
| shadcn/ui as local copy (`frontend/src/components/ui/`) | Direct npm package import | Components are owned code — customizable without upstream library changes |
| Hexagonal package structure (`domain/application/infrastructure/api`) | Flat by feature or flat by layer | Enforces dependency direction: `domain` has no Spring imports; `infrastructure` imports `domain`, never the reverse |
| `AuditableEntity` base class for all `@Entity` | Declaring `id`/`createdAt`/`updatedAt` per entity | Single source of truth; consistent UUID generation; JPA auditing wired once |
