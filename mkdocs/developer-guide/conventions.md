# Conventions

Cross-cutting patterns that apply to every module. Module pages assume you have read this.

## Module Structure

Every module follows hexagonal architecture:

```
<module>/
  domain/         @Entity classes, value objects, *Event.kt domain events
  application/    *Service.kt — business logic, @EventListener handlers
  infrastructure/ *Repository.kt (Spring Data JPA), external adapters
  api/            *Controller.kt — REST endpoints only, no business logic
```

Rules:
- Controllers call Services only. Never call Repositories directly from Controllers.
- Services may call Repositories in the same module. Never inject a Service from another module.
- Cross-module side effects: publish an `ApplicationEvent` via `ApplicationEventPublisher`. The other module listens with `@EventListener`.
- Domain events live in `<module>/domain/` and are plain data classes (no Spring annotations).

## Base Entity

All `@Entity` classes extend `AuditableEntity` from `core`:

```kotlin
// core/domain/AuditableEntity.kt
abstract class AuditableEntity(
    @Id val id: UUID = UUID.randomUUID(),
    @CreatedDate @Column(nullable = false, updatable = false) var createdAt: Instant? = null,
    @LastModifiedDate @Column(nullable = false) var updatedAt: Instant = Instant.now()
) : Persistable<UUID>
```

Do not add `id`, `createdAt`, or `updatedAt` fields to entity subclasses — they are inherited.

## Error Handling

Use the exception classes from `core/infrastructure/GlobalExceptionHandler.kt`:

| Exception | HTTP Status | Error Code |
|-----------|------------|------------|
| `NotFoundException` | 404 | `NOT_FOUND` |
| `ForbiddenException` | 403 | `FORBIDDEN` |
| `ConflictException` | 409 | `CONFLICT` |
| `BadRequestException` | 400 | `BAD_REQUEST` |
| `IllegalArgumentException` | 400 | `BAD_REQUEST` |
| `MethodArgumentNotValidException` | 400 | `VALIDATION_ERROR` |
| `DataIntegrityViolationException` | 409 | `CONFLICT` |

Error response shape:
```json
{ "code": "NOT_FOUND", "message": "Issue not found", "details": {} }
```

Do NOT throw raw `RuntimeException` — use the typed exceptions above.

## Inter-Module Communication

```kotlin
// Publisher side (in any Service):
@Service
class IssueService(private val eventPublisher: ApplicationEventPublisher) {
    fun updateStatus(...) {
        eventPublisher.publishEvent(IssueStatusChangedEvent(issueId, newStatus))
    }
}

// Listener side (in another module's Service):
@Service
class NotificationService {
    @EventListener
    fun onIssueStatusChanged(event: IssueStatusChangedEvent) { ... }
}
```

- Events are synchronous by default (same thread, same transaction).
- Event class names: `<Entity><Action>Event` (e.g. `IssueStatusChangedEvent`).
- Event classes live in the publishing module's `domain/` package.
- Never add a return type to `@EventListener` methods — the return value is ignored.

## Flyway Migrations

- Migration files: `backend/src/main/resources/db/migration/V{n}__{description}.sql`
- Current version: **V22** (`V22__audit_details_text.sql`)
- Next migration must be **V23**
- H2 compatibility: avoid `JSONB`, `UUID` functions, `SERIAL`; use `TEXT`, `VARCHAR(36)`, `BIGSERIAL`
- PostgreSQL-only features: use `-- H2: skip` comment and conditional logic in application code

## Security

Security is configured in `auth/infrastructure/SecurityConfig.kt`.

- Authenticated paths: all `/api/v1/**` routes require a valid JWT by default
- Permit-all paths: `/api/v1/auth/**`, `/api/v1/integrations/**/webhook`, `actuator/health`
- Role enforcement: use `@PreAuthorize("hasRole('ADMIN')")` on Controller methods, not Service methods
- API key auth: `ApiKeyAuthFilter` runs before `JwtAuthFilter`; sets a `UsernamePasswordAuthenticationToken` from the API key owner
- Do NOT add `permitAll()` entries without a security review

JWT claims available via `SecurityContextHolder`:
```kotlin
val userId = (SecurityContextHolder.getContext().authentication.principal as UserDetails).username
```

## API Conventions

- Base path: `/api/v1/projects/{key}/...` for project-scoped resources
- Global resources: `/api/v1/auth/...`, `/api/v1/admin/...`, `/api/v1/orgs/...`
- Pagination: `GET` list endpoints return `{ content: [...], totalElements, totalPages, number, size }`
- IDs: always UUID strings in JSON
- Timestamps: ISO-8601 strings (`2024-01-15T10:30:00Z`)

## Testing

- Framework: **JUnit 5 + MockK** (NOT Mockito — `mockk<T>()`, `every { }`, `verify { }`)
- Unit tests: mock all dependencies with MockK; no Spring context loaded
- Integration tests: use `@SpringBootTest` + Testcontainers PostgreSQL
- Test files: `backend/src/test/kotlin/com/taskowolf/<module>/`
- Naming: `<Subject>Test.kt` for unit tests, `<Subject>IntegrationTest.kt` for integration tests

Minimal MockK unit test pattern:
```kotlin
class IssueServiceTest {
    private val repo = mockk<IssueRepository>()
    private val service = IssueService(repo, mockk())

    @Test
    fun `returns issue by id`() {
        val issue = Issue(title = "T", projectId = UUID.randomUUID())
        every { repo.findById(issue.id) } returns Optional.of(issue)
        val result = service.getById(issue.id)
        assertThat(result.id).isEqualTo(issue.id)
    }
}
```
