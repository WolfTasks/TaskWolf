# Module: core

## Purpose

Provides the shared base classes, error handling, WebSocket configuration, and domain event bus used by every other module.
No business logic lives here — only infrastructure contracts.

---

## Entities Owned

`core` defines one `@MappedSuperclass`, not a concrete `@Entity`:

| Class | Type | Key Fields |
|---|---|---|
| `AuditableEntity` | `@MappedSuperclass` | `id: UUID` (auto-generated), `createdAt: Instant?` (set on insert, `null` until persisted), `updatedAt: Instant` (updated on every save) |

`AuditableEntity` implements `Persistable<UUID>`. The `isNew()` method returns `createdAt == null`, which tells JPA to use `INSERT` rather than `SELECT + INSERT` for new entities. Every `@Entity` in the codebase must extend this class.

---

## DB Schema

`core` owns **no database tables**. It is infrastructure-only — no `@Entity` annotation maps to a table.

---

## API Endpoints

`core` exposes **no REST endpoints**. It contains no `@RestController`.

---

## Events Emitted

None. `core` provides the event bus (`DomainEventPublisher`) but does not publish events itself.

---

## Events Consumed

None.

---

## Key Files

| File | Responsibility |
|---|---|
| `backend/src/main/kotlin/com/taskowolf/core/domain/AuditableEntity.kt` | `@MappedSuperclass` with `id`, `createdAt`, `updatedAt`; implements `Persistable<UUID>` for correct JPA new-entity detection |
| `backend/src/main/kotlin/com/taskowolf/core/application/DomainEventPublisher.kt` | Thin `@Component` wrapper around Spring's `ApplicationEventPublisher`; call `publish(event)` from any `@Service` to fire synchronous Spring application events |
| `backend/src/main/kotlin/com/taskowolf/core/infrastructure/GlobalExceptionHandler.kt` | `@RestControllerAdvice` that maps domain exceptions to HTTP responses; also declares the four domain exception classes (`NotFoundException`, `ForbiddenException`, `ConflictException`, `BadRequestException`) |
| `backend/src/main/kotlin/com/taskowolf/core/infrastructure/WebSocketConfig.kt` | Configures the STOMP message broker: topic prefix `/topic`, queue prefix `/queue`, app prefix `/app`; registers `/ws` (SockJS) and `/ws-stomp` (native) endpoints; wires `JwtStompInterceptor` on the inbound channel |
| `backend/src/main/kotlin/com/taskowolf/core/infrastructure/ErrorResponse.kt` | Data class `ErrorResponse(code: String, message: String, details: Map<String, String>)` — the uniform JSON shape for all error responses |

---

## Extension Points

**Adding a new global exception type:**

Add the exception class and a corresponding `@ExceptionHandler` method to `GlobalExceptionHandler.kt`. Pattern:

```kotlin
// 1. Declare the exception (top of GlobalExceptionHandler.kt)
class UnprocessableException(message: String) : RuntimeException(message)

// 2. Add the handler inside GlobalExceptionHandler
@ExceptionHandler(UnprocessableException::class)
fun handleUnprocessable(ex: UnprocessableException) =
    ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
        .body(ErrorResponse("UNPROCESSABLE", ex.message ?: "Unprocessable entity"))
```

Throw `UnprocessableException` from any `@Service` — `GlobalExceptionHandler` will catch it and return the correct HTTP status.

---

## Common Pitfalls

- **DO NOT** extend anything other than `AuditableEntity` for `@Entity` classes. Bypassing it breaks JPA new-entity detection and removes audit timestamps.
- **DO NOT** call `DomainEventPublisher.publish()` from a `@Repository`. Event publishing must originate from a `@Service` that is already inside a transaction boundary.
- **DO NOT** add business logic to `GlobalExceptionHandler`. It maps exceptions to HTTP responses only — validation, authorization, and domain decisions belong in the module's service layer.

---

## Example

**Before** — throwing a raw Spring exception loses the standard error shape:

```kotlin
// BAD: throws ResponseStatusException, bypasses GlobalExceptionHandler
throw ResponseStatusException(HttpStatus.NOT_FOUND, "Widget not found")
```

**After** — throw the core domain exception so the handler returns `ErrorResponse`:

```kotlin
// GOOD: GlobalExceptionHandler maps this to 404 + {"code":"NOT_FOUND",...}
throw NotFoundException("Widget not found: $id")
```

The `GlobalExceptionHandler` handles `NotFoundException` and returns:

```json
{ "code": "NOT_FOUND", "message": "Widget not found: abc123", "details": {} }
```

---

## Test Patterns

No tests exist in `backend/src/test/kotlin/com/taskowolf/core/`. All `core` behaviour (exception mapping, WebSocket connectivity, event publishing) is exercised by the integration tests in the module packages that use it.
