# Profile / Settings Pages Implementation Plan (Backlog #3)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give each user grouped profile/settings pages (Profile / Security / Notifications / Access Tokens / Account) under a dedicated `/settings/*` shell, backed by new `/me` endpoints for display-name change, password change (revokes sessions), and per-user notification preferences.

**Architecture:** Backend adds three capabilities to the existing `auth` and `notifications` modules — a `PATCH /me` and `POST /me/password` on the existing `MeController`, plus a new `NotificationPreference` entity (Flyway V29) whose per-user/per-type flags gate both in-app (`NotificationService`) and e-mail (`EmailService`) dispatch. Frontend introduces a nested `SettingsLayout` route with its own sub-nav, three new pages, and a React-Query API module (`me.ts` + `useMe.ts`); the existing Access Tokens / Account pages move under the shell with **unchanged paths**.

**Tech Stack:** Kotlin 2.4 + Spring Boot 3.x + Spring Data JPA + Flyway (Postgres/H2) + MockK/JUnit (backend); React 19 + TypeScript + Vite + Tailwind 4 + React Query + axios + lucide-react (frontend).

## Global Constraints

- **Flyway:** current version is **V28**; the next (and only) new migration in this plan is **V29**. Postgres-native SQL only — no `JSONB`, no `SERIAL` (use `UUID DEFAULT gen_random_uuid()`).
- **Backend tests:** MockK (`mockk<T>()`, `every {}`, `verify {}`) — never Mockito. Unit tests instantiate the class directly (no Spring context). DB/migration tests extend `IntegrationTestBase` (real Testcontainers Postgres + Flyway).
- **Frontend tests:** the frontend has **no test framework**. "Test" = `cd frontend && npx tsc --noEmit` (must pass) + the manual browser checks listed per task.
- **Frontend conventions:** server state via React Query only; API modules are thin axios wrappers in `src/api/`; hooks in `src/hooks/` prefixed `use`; query keys are arrays; shadcn/ui imported from `@/components/ui/` (not used here — plain Tailwind). Mutations invalidate the matching query key on success.
- **Notification preferences are opt-out:** a missing row means **enabled** (`isEnabled` returns `true` when no row exists). Default new rows have both channels `true`.
- **Password change does NOT revoke Personal Access Tokens** (`twk_`). It revokes only refresh tokens.
- **Existing settings paths are frozen:** `/settings/tokens` and `/settings/account` must keep working (no dead links).
- Commits use Conventional Commits (`feat(backend): …`, `feat(frontend): …`, `docs: …`).

---

### Task 1: Profile update — `PATCH /api/v1/me`

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/auth/api/dto/UpdateProfileRequest.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/audit/domain/AuditAction.kt` (add `PROFILE_UPDATED`)
- Modify: `backend/src/main/kotlin/com/taskowolf/audit/application/SecurityAuditListener.kt` (add `onProfileUpdated`)
- Modify: `backend/src/main/kotlin/com/taskowolf/auth/application/UserAccountService.kt` (add `updateProfile`; add constructor deps)
- Modify: `backend/src/main/kotlin/com/taskowolf/auth/api/MeController.kt` (add `PATCH`)
- Test: `backend/src/test/kotlin/com/taskowolf/auth/UserAccountServiceTest.kt` (add tests + fix constructor)

**Interfaces:**
- Produces: `UserAccountService.updateProfile(userId: UUID, displayName: String): User` — returns the saved `User`.
- Produces: `SecurityAuditListener.onProfileUpdated(email: String)`.
- Consumes: existing `UserResponse.from(user: User)`.

> **Note (constructor change):** `UserAccountService` gains two new constructor params in this plan — `passwordEncoder: PasswordEncoder` (Task 2) and `securityAuditListener: SecurityAuditListener` (this task). Add **both** now so Task 2 does not re-touch the constructor, and update the existing test constructor call accordingly.

- [ ] **Step 1: Write the failing test** — append to `UserAccountServiceTest.kt`.

First update the field declarations and constructor at the top of the class (replace lines 21-24):

```kotlin
    private val userRepository = mockk<UserRepository>()
    private val accessTokenService = mockk<AccessTokenService>(relaxed = true)
    private val refreshTokenService = mockk<RefreshTokenService>(relaxed = true)
    private val passwordEncoder = mockk<org.springframework.security.crypto.password.PasswordEncoder>()
    private val securityAuditListener = mockk<com.taskowolf.audit.application.SecurityAuditListener>(relaxed = true)
    private val service = UserAccountService(
        userRepository, accessTokenService, refreshTokenService, passwordEncoder, securityAuditListener
    )
```

Then add these tests inside the class:

```kotlin
    @Test
    fun `updateProfile sets displayName and audits`() {
        val user = User(email = "u@x.com", displayName = "Old", systemRole = SystemRole.MEMBER)
        every { userRepository.findById(user.id) } returns Optional.of(user)
        every { userRepository.save(any()) } returnsArgument 0

        val result = service.updateProfile(user.id, "New Name")

        assertEquals("New Name", result.displayName)
        verify { userRepository.save(user) }
        verify { securityAuditListener.onProfileUpdated("u@x.com") }
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.auth.UserAccountServiceTest"`
Expected: FAIL — `updateProfile` is unresolved (and the constructor now needs 5 args).

- [ ] **Step 3: Implement**

Add `PROFILE_UPDATED` to the enum in `AuditAction.kt` (append to the first line's list):

```kotlin
    LOGIN_SUCCESS, LOGIN_FAILED, LOGOUT, PASSWORD_CHANGED, PROFILE_UPDATED, ROLE_CHANGED,
```

Add to `SecurityAuditListener.kt`:

```kotlin
    fun onProfileUpdated(email: String) =
        auditService.log(AuditLevel.SECURITY, AuditAction.PROFILE_UPDATED, email)

    fun onPasswordChanged(email: String) =
        auditService.log(AuditLevel.SECURITY, AuditAction.PASSWORD_CHANGED, email)
```

Create `UpdateProfileRequest.kt`:

```kotlin
package com.taskowolf.auth.api.dto

import jakarta.validation.constraints.NotBlank

data class UpdateProfileRequest(
    @field:NotBlank val displayName: String
)
```

Edit `UserAccountService.kt` — update the constructor and add the method. New constructor:

```kotlin
import org.springframework.security.crypto.password.PasswordEncoder
import com.taskowolf.audit.application.SecurityAuditListener
import com.taskowolf.core.infrastructure.ForbiddenException

@Service
class UserAccountService(
    private val userRepository: UserRepository,
    private val accessTokenService: AccessTokenService,
    private val refreshTokenService: RefreshTokenService,
    private val passwordEncoder: PasswordEncoder,
    private val securityAuditListener: SecurityAuditListener
) {
```

Add the method (anywhere in the class body):

```kotlin
    @Transactional
    fun updateProfile(userId: UUID, displayName: String): User {
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User not found") }
        user.displayName = displayName
        val saved = userRepository.save(user)
        securityAuditListener.onProfileUpdated(user.email)
        return saved
    }
```

Edit `MeController.kt` — add the endpoint and imports:

```kotlin
import com.taskowolf.auth.api.dto.UpdateProfileRequest
import com.taskowolf.auth.api.dto.UserResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody

    @PatchMapping
    fun updateProfile(
        @Valid @RequestBody request: UpdateProfileRequest,
        @AuthenticationPrincipal user: User
    ) = UserResponse.from(userAccountService.updateProfile(user.id, request.displayName))
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.auth.UserAccountServiceTest"`
Expected: PASS (all tests green, including the pre-existing ones).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/auth backend/src/main/kotlin/com/taskowolf/audit backend/src/test/kotlin/com/taskowolf/auth/UserAccountServiceTest.kt
git commit -m "feat(backend): PATCH /me to update display name (backlog #3)"
```

---

### Task 2: Change password — `POST /api/v1/me/password`

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/auth/api/dto/ChangePasswordRequest.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/auth/application/UserAccountService.kt` (add `changePassword`)
- Modify: `backend/src/main/kotlin/com/taskowolf/auth/api/MeController.kt` (add `POST /password`)
- Test: `backend/src/test/kotlin/com/taskowolf/auth/UserAccountServiceTest.kt`

**Interfaces:**
- Produces: `UserAccountService.changePassword(userId: UUID, currentPassword: String, newPassword: String)` — verifies current password, re-hashes, revokes refresh tokens, audits. Throws `ForbiddenException` on wrong/absent password.
- Consumes: `passwordEncoder`, `refreshTokenService.revokeAllForUser`, `securityAuditListener.onPasswordChanged` (added in Task 1).

- [ ] **Step 1: Write the failing test** — append to `UserAccountServiceTest.kt`:

```kotlin
    @Test
    fun `changePassword rehashes, revokes refresh tokens, keeps PATs, audits`() {
        val user = User(email = "u@x.com", displayName = "U", systemRole = SystemRole.MEMBER)
        user.passwordHash = "OLD_HASH"
        every { userRepository.findById(user.id) } returns Optional.of(user)
        every { passwordEncoder.matches("current", "OLD_HASH") } returns true
        every { passwordEncoder.encode("newpass12") } returns "NEW_HASH"
        every { userRepository.save(any()) } returnsArgument 0

        service.changePassword(user.id, "current", "newpass12")

        assertEquals("NEW_HASH", user.passwordHash)
        verify { refreshTokenService.revokeAllForUser(user.id) }
        verify(exactly = 0) { accessTokenService.revokeAllForUser(user.id) }
        verify { securityAuditListener.onPasswordChanged("u@x.com") }
    }

    @Test
    fun `changePassword rejects wrong current password`() {
        val user = User(email = "u@x.com", displayName = "U", systemRole = SystemRole.MEMBER)
        user.passwordHash = "OLD_HASH"
        every { userRepository.findById(user.id) } returns Optional.of(user)
        every { passwordEncoder.matches("wrong", "OLD_HASH") } returns false

        assertThrows<com.taskowolf.core.infrastructure.ForbiddenException> {
            service.changePassword(user.id, "wrong", "newpass12")
        }
        verify(exactly = 0) { userRepository.save(any()) }
        verify(exactly = 0) { refreshTokenService.revokeAllForUser(any()) }
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.auth.UserAccountServiceTest"`
Expected: FAIL — `changePassword` is unresolved.

- [ ] **Step 3: Implement**

Create `ChangePasswordRequest.kt`:

```kotlin
package com.taskowolf.auth.api.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class ChangePasswordRequest(
    @field:NotBlank val currentPassword: String,
    @field:Size(min = 8) val newPassword: String
)
```

Add to `UserAccountService.kt` (imports `ForbiddenException` + `ConflictException` are already present/added):

```kotlin
    @Transactional
    fun changePassword(userId: UUID, currentPassword: String, newPassword: String) {
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User not found") }
        val hash = user.passwordHash
            ?: throw ConflictException("This account has no password set")
        if (!passwordEncoder.matches(currentPassword, hash)) {
            throw ForbiddenException("Current password is incorrect")
        }
        user.passwordHash = passwordEncoder.encode(newPassword)
        userRepository.save(user)
        refreshTokenService.revokeAllForUser(userId)
        securityAuditListener.onPasswordChanged(user.email)
    }
```

Add to `MeController.kt` (imports: `ChangePasswordRequest`, `PostMapping`, `HttpStatus`, `ResponseStatus`):

```kotlin
    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun changePassword(
        @Valid @RequestBody request: ChangePasswordRequest,
        @AuthenticationPrincipal user: User
    ) = userAccountService.changePassword(user.id, request.currentPassword, request.newPassword)
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.auth.UserAccountServiceTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/auth backend/src/test/kotlin/com/taskowolf/auth/UserAccountServiceTest.kt
git commit -m "feat(backend): POST /me/password (revokes refresh tokens, keeps PATs) (backlog #3)"
```

---

### Task 3: `NotificationPreference` entity, channel enum, migration V29, repository

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/notifications/domain/NotificationChannel.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/notifications/domain/NotificationPreference.kt`
- Create: `backend/src/main/resources/db/migration/V29__notification_preferences.sql`
- Create: `backend/src/main/kotlin/com/taskowolf/notifications/infrastructure/NotificationPreferenceRepository.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/notifications/NotificationPreferenceRepositoryIntegrationTest.kt`

**Interfaces:**
- Produces: `enum class NotificationChannel { IN_APP, EMAIL }`.
- Produces: `NotificationPreference(userId: UUID, type: NotificationType, inAppEnabled: Boolean = true, emailEnabled: Boolean = true)` extending `AuditableEntity` (provides `id`, `createdAt`, `updatedAt`).
- Produces: `NotificationPreferenceRepository.findByUserId(userId): List<NotificationPreference>` and `findByUserIdAndType(userId, type): NotificationPreference?`.

- [ ] **Step 1: Write the failing test** — create `NotificationPreferenceRepositoryIntegrationTest.kt`:

```kotlin
package com.taskowolf.notifications

import com.taskowolf.IntegrationTestBase
import com.taskowolf.notifications.domain.NotificationPreference
import com.taskowolf.notifications.domain.NotificationType
import com.taskowolf.notifications.infrastructure.NotificationPreferenceRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID

class NotificationPreferenceRepositoryIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var repo: NotificationPreferenceRepository

    @Test
    fun `persists and finds by user and type`() {
        val userId = UUID.randomUUID()
        repo.save(NotificationPreference(userId, NotificationType.COMMENT_MENTION, inAppEnabled = false, emailEnabled = true))

        val found = repo.findByUserIdAndType(userId, NotificationType.COMMENT_MENTION)
        assertEquals(false, found?.inAppEnabled)
        assertEquals(true, found?.emailEnabled)
        assertEquals(1, repo.findByUserId(userId).size)
        assertNull(repo.findByUserIdAndType(userId, NotificationType.ISSUE_ASSIGNED))
        assertFalse(found!!.inAppEnabled)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.notifications.NotificationPreferenceRepositoryIntegrationTest"`
Expected: FAIL — types `NotificationPreference` / `NotificationPreferenceRepository` do not exist (compile error).

- [ ] **Step 3: Implement**

Create `NotificationChannel.kt`:

```kotlin
package com.taskowolf.notifications.domain

enum class NotificationChannel { IN_APP, EMAIL }
```

Create `NotificationPreference.kt`:

```kotlin
package com.taskowolf.notifications.domain

import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(
    name = "notification_preferences",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "type"])]
)
class NotificationPreference(
    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val type: NotificationType,

    @Column(name = "in_app_enabled", nullable = false)
    var inAppEnabled: Boolean = true,

    @Column(name = "email_enabled", nullable = false)
    var emailEnabled: Boolean = true
) : AuditableEntity()
```

Create `V29__notification_preferences.sql`:

```sql
CREATE TABLE notification_preferences (
    id             UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id        UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type           VARCHAR(30) NOT NULL,
    in_app_enabled BOOLEAN     NOT NULL DEFAULT TRUE,
    email_enabled  BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, type)
);

CREATE INDEX idx_notification_preferences_user ON notification_preferences (user_id);
```

Create `NotificationPreferenceRepository.kt`:

```kotlin
package com.taskowolf.notifications.infrastructure

import com.taskowolf.notifications.domain.NotificationPreference
import com.taskowolf.notifications.domain.NotificationType
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface NotificationPreferenceRepository : JpaRepository<NotificationPreference, UUID> {
    fun findByUserId(userId: UUID): List<NotificationPreference>
    fun findByUserIdAndType(userId: UUID, type: NotificationType): NotificationPreference?
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.notifications.NotificationPreferenceRepositoryIntegrationTest"`
Expected: PASS (Testcontainers Postgres boots, Flyway applies V29).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/notifications/domain backend/src/main/kotlin/com/taskowolf/notifications/infrastructure/NotificationPreferenceRepository.kt backend/src/main/resources/db/migration/V29__notification_preferences.sql backend/src/test/kotlin/com/taskowolf/notifications/NotificationPreferenceRepositoryIntegrationTest.kt
git commit -m "feat(backend): NotificationPreference entity + V29 migration (backlog #3)"
```

---

### Task 4: `NotificationPreferenceService` (matrix / upsert / isEnabled)

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/notifications/application/NotificationPreferenceService.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/notifications/NotificationPreferenceServiceTest.kt`

**Interfaces:**
- Produces: `getMatrix(userId: UUID): List<NotificationPreference>` — always returns exactly one entry per `NotificationType` value (4 rows), filling defaults for missing types.
- Produces: `update(userId: UUID, prefs: Map<NotificationType, Pair<Boolean, Boolean>>)` — upserts (first = inApp, second = email).
- Produces: `isEnabled(userId: UUID, type: NotificationType, channel: NotificationChannel): Boolean` — defaults to `true` when no row exists.

- [ ] **Step 1: Write the failing test** — create `NotificationPreferenceServiceTest.kt`:

```kotlin
package com.taskowolf.notifications

import com.taskowolf.notifications.application.NotificationPreferenceService
import com.taskowolf.notifications.domain.NotificationChannel
import com.taskowolf.notifications.domain.NotificationPreference
import com.taskowolf.notifications.domain.NotificationType
import com.taskowolf.notifications.infrastructure.NotificationPreferenceRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class NotificationPreferenceServiceTest {

    private val repository = mockk<NotificationPreferenceRepository>()
    private val service = NotificationPreferenceService(repository)
    private val userId = UUID.randomUUID()

    @Test
    fun `getMatrix returns all four types with defaults when none stored`() {
        every { repository.findByUserId(userId) } returns emptyList()

        val matrix = service.getMatrix(userId)

        assertEquals(NotificationType.entries.size, matrix.size)
        assertTrue(matrix.all { it.inAppEnabled && it.emailEnabled })
        assertEquals(NotificationType.entries.toSet(), matrix.map { it.type }.toSet())
    }

    @Test
    fun `getMatrix reflects a stored row`() {
        every { repository.findByUserId(userId) } returns listOf(
            NotificationPreference(userId, NotificationType.COMMENT_MENTION, inAppEnabled = false, emailEnabled = false)
        )

        val mention = service.getMatrix(userId).first { it.type == NotificationType.COMMENT_MENTION }
        assertFalse(mention.inAppEnabled)
        assertFalse(mention.emailEnabled)
    }

    @Test
    fun `update upserts existing and new rows`() {
        every { repository.findByUserIdAndType(userId, NotificationType.COMMENT_MENTION) } returns
            NotificationPreference(userId, NotificationType.COMMENT_MENTION)
        every { repository.findByUserIdAndType(userId, NotificationType.AUTOMATION) } returns null
        every { repository.save(any()) } returnsArgument 0

        service.update(userId, mapOf(
            NotificationType.COMMENT_MENTION to Pair(false, true),
            NotificationType.AUTOMATION to Pair(true, false),
        ))

        verify(exactly = 2) { repository.save(any()) }
    }

    @Test
    fun `isEnabled defaults to true when no row`() {
        every { repository.findByUserIdAndType(userId, NotificationType.ISSUE_ASSIGNED) } returns null
        assertTrue(service.isEnabled(userId, NotificationType.ISSUE_ASSIGNED, NotificationChannel.IN_APP))
    }

    @Test
    fun `isEnabled respects stored flags per channel`() {
        every { repository.findByUserIdAndType(userId, NotificationType.ISSUE_ASSIGNED) } returns
            NotificationPreference(userId, NotificationType.ISSUE_ASSIGNED, inAppEnabled = false, emailEnabled = true)

        assertFalse(service.isEnabled(userId, NotificationType.ISSUE_ASSIGNED, NotificationChannel.IN_APP))
        assertTrue(service.isEnabled(userId, NotificationType.ISSUE_ASSIGNED, NotificationChannel.EMAIL))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.notifications.NotificationPreferenceServiceTest"`
Expected: FAIL — `NotificationPreferenceService` does not exist.

- [ ] **Step 3: Implement** — create `NotificationPreferenceService.kt`:

```kotlin
package com.taskowolf.notifications.application

import com.taskowolf.notifications.domain.NotificationChannel
import com.taskowolf.notifications.domain.NotificationPreference
import com.taskowolf.notifications.domain.NotificationType
import com.taskowolf.notifications.infrastructure.NotificationPreferenceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class NotificationPreferenceService(
    private val repository: NotificationPreferenceRepository
) {
    @Transactional(readOnly = true)
    fun getMatrix(userId: UUID): List<NotificationPreference> {
        val existing = repository.findByUserId(userId).associateBy { it.type }
        return NotificationType.entries.map { type ->
            existing[type] ?: NotificationPreference(userId = userId, type = type)
        }
    }

    @Transactional
    fun update(userId: UUID, prefs: Map<NotificationType, Pair<Boolean, Boolean>>) {
        prefs.forEach { (type, flags) ->
            val row = repository.findByUserIdAndType(userId, type)
                ?: NotificationPreference(userId = userId, type = type)
            row.inAppEnabled = flags.first
            row.emailEnabled = flags.second
            repository.save(row)
        }
    }

    @Transactional(readOnly = true)
    fun isEnabled(userId: UUID, type: NotificationType, channel: NotificationChannel): Boolean {
        val pref = repository.findByUserIdAndType(userId, type) ?: return true
        return when (channel) {
            NotificationChannel.IN_APP -> pref.inAppEnabled
            NotificationChannel.EMAIL -> pref.emailEnabled
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.notifications.NotificationPreferenceServiceTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/notifications/application/NotificationPreferenceService.kt backend/src/test/kotlin/com/taskowolf/notifications/NotificationPreferenceServiceTest.kt
git commit -m "feat(backend): NotificationPreferenceService (matrix, upsert, isEnabled) (backlog #3)"
```

---

### Task 5: In-app dispatch gating in `NotificationService`

**Files:**
- Modify: `backend/src/main/kotlin/com/taskowolf/notifications/application/NotificationService.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/notifications/NotificationServiceTest.kt`

**Interfaces:**
- Consumes: `NotificationPreferenceService.isEnabled(userId, type, NotificationChannel.IN_APP)`.
- `NotificationService` constructor gains `preferences: NotificationPreferenceService` as the second param.

> **Note:** adding a constructor param breaks the existing `NotificationServiceTest` (`NotificationService(repository)`). Update it in Step 1.

- [ ] **Step 1: Write the failing test** — edit `NotificationServiceTest.kt`.

Replace the field/constructor lines (30-31) with:

```kotlin
    private val repository = mockk<NotificationRepository>()
    private val preferences = mockk<com.taskowolf.notifications.application.NotificationPreferenceService>()
    private val service = NotificationService(repository, preferences)
```

In the two existing tests `onMention …` and `onIssueFieldChanged …`, add a stub so the gate passes — insert at the start of each test body (before `every { repository.save(...) }`):

```kotlin
        every { preferences.isEnabled(any(), any(), any()) } returns true
```

Then add two new gating tests:

```kotlin
    @Test
    fun `onMention skips save when in-app preference disabled`() {
        every { preferences.isEnabled(user.id, NotificationType.COMMENT_MENTION,
            com.taskowolf.notifications.domain.NotificationChannel.IN_APP) } returns false
        val comment = Comment(issueId = issue.id, authorId = actor.id, body = "Hey @User")
        val event = MentionEvent(mentionedUser = user, comment = comment, issue = issue)

        service.onMention(event)

        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `createDirect skips save when in-app preference disabled`() {
        every { preferences.isEnabled(user.id, NotificationType.AUTOMATION,
            com.taskowolf.notifications.domain.NotificationChannel.IN_APP) } returns false

        service.createDirect(user.id, NotificationType.AUTOMATION, "t", "b", "/l")

        verify(exactly = 0) { repository.save(any()) }
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.notifications.NotificationServiceTest"`
Expected: FAIL — constructor arity mismatch / gating not implemented.

- [ ] **Step 3: Implement** — edit `NotificationService.kt`.

Update imports + constructor:

```kotlin
import com.taskowolf.notifications.domain.NotificationChannel

@Service
class NotificationService(
    private val repository: NotificationRepository,
    private val preferences: NotificationPreferenceService
) {
```

Add the gate as the first line of `onMention` (before `repository.save`):

```kotlin
    fun onMention(event: MentionEvent) {
        if (!preferences.isEnabled(event.mentionedUser.id, NotificationType.COMMENT_MENTION, NotificationChannel.IN_APP)) return
        repository.save(
```

Add the gate in `onIssueFieldChanged` right after `val assignee = event.issue.assignee ?: return`:

```kotlin
        val assignee = event.issue.assignee ?: return
        if (!preferences.isEnabled(assignee.id, NotificationType.ISSUE_ASSIGNED, NotificationChannel.IN_APP)) return
```

Add the gate as the first line of `createDirect`:

```kotlin
    fun createDirect(userId: UUID, type: NotificationType, title: String, body: String, link: String) {
        if (!preferences.isEnabled(userId, type, NotificationChannel.IN_APP)) return
        repository.save(Notification(userId = userId, type = type, title = title, body = body, link = link))
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.notifications.NotificationServiceTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/notifications/application/NotificationService.kt backend/src/test/kotlin/com/taskowolf/notifications/NotificationServiceTest.kt
git commit -m "feat(backend): gate in-app notifications by user preference (backlog #3)"
```

---

### Task 6: E-mail dispatch gating in `EmailService`

**Files:**
- Modify: `backend/src/main/kotlin/com/taskowolf/notifications/application/EmailService.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/notifications/EmailServiceTest.kt`

**Interfaces:**
- Consumes: `NotificationPreferenceService.isEnabled(userId, type, NotificationChannel.EMAIL)`.
- `EmailService` gains `preferences: NotificationPreferenceService` as the **first** constructor param.

> **Note:** the existing `EmailServiceTest` constructs `EmailService(mailSender, mailHost = …, fromAddress = …)` four times. Update every call in Step 1.

- [ ] **Step 1: Write the failing test** — edit `EmailServiceTest.kt`.

Add a shared preferences mock field near `mailSender`:

```kotlin
    private val mailSender = mockk<JavaMailSender>(relaxed = true)
    private val preferences = mockk<com.taskowolf.notifications.application.NotificationPreferenceService>(relaxed = true)
```

Make the four existing `EmailService(...)` constructions pass `preferences` first, e.g.:

```kotlin
        val service = EmailService(preferences, mailSender, mailHost = "smtp.example.com", fromAddress = "noreply@example.com")
```

(apply the same first-arg insertion to all four; a `relaxed = true` mock returns `true`-ish by default, but `isEnabled` returns `Boolean` — relaxed default for Boolean is `false`, so **stub it** in the two "sends email" tests). Add to the top of `onMention sends email …` and `onAssigned sends email …`:

```kotlin
        every { preferences.isEnabled(any(), any(), any()) } returns true
```

Then add a gating test:

```kotlin
    @Test
    fun `onMention skips email when email preference disabled`() {
        every { preferences.isEnabled(assignee.id,
            com.taskowolf.notifications.domain.NotificationType.COMMENT_MENTION,
            com.taskowolf.notifications.domain.NotificationChannel.EMAIL) } returns false
        val service = EmailService(preferences, mailSender, mailHost = "smtp.example.com", fromAddress = "noreply@example.com")
        val comment = Comment(issueId = issue.id, authorId = actor.id, body = "Hey @Assignee")
        val event = MentionEvent(mentionedUser = assignee, comment = comment, issue = issue)

        service.onMention(event)

        verify(exactly = 0) { mailSender.send(any<SimpleMailMessage>()) }
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.notifications.EmailServiceTest"`
Expected: FAIL — constructor arity mismatch / gating not implemented.

- [ ] **Step 3: Implement** — edit `EmailService.kt`.

Update imports + constructor:

```kotlin
import com.taskowolf.notifications.domain.NotificationChannel
import com.taskowolf.notifications.domain.NotificationType

@Service
class EmailService(
    private val preferences: NotificationPreferenceService,
    private val mailSender: JavaMailSender,
    @Value("\${spring.mail.host:}") private val mailHost: String,
    @Value("\${taskowolf.smtp.from:TaskWolf <noreply@example.com>}") private val fromAddress: String
) {
```

In `onMention`, after `if (!enabled) return`, add:

```kotlin
        if (!enabled) return
        if (!preferences.isEnabled(event.mentionedUser.id, NotificationType.COMMENT_MENTION, NotificationChannel.EMAIL)) return
```

In `onAssigned`, after `val assignee = event.issue.assignee ?: return`, add:

```kotlin
        val assignee = event.issue.assignee ?: return
        if (!preferences.isEnabled(assignee.id, NotificationType.ISSUE_ASSIGNED, NotificationChannel.EMAIL)) return
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.notifications.EmailServiceTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/notifications/application/EmailService.kt backend/src/test/kotlin/com/taskowolf/notifications/EmailServiceTest.kt
git commit -m "feat(backend): gate mention/assigned emails by user preference (backlog #3)"
```

---

### Task 7: Notification-preference endpoints — `GET`/`PUT /api/v1/me/notification-preferences`

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/notifications/api/dto/NotificationPreferencesResponse.kt` (Item + Response)
- Create: `backend/src/main/kotlin/com/taskowolf/notifications/api/dto/NotificationPreferencesRequest.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/notifications/api/NotificationPreferenceController.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/notifications/NotificationPreferenceControllerTest.kt`

**Interfaces:**
- HTTP: `GET /api/v1/me/notification-preferences` → `{ "preferences": [ { "type": "COMMENT_MENTION", "inApp": true, "email": true }, … ] }` (4 entries).
- HTTP: `PUT /api/v1/me/notification-preferences` with the same body shape → returns the refreshed matrix.
- Produces: `NotificationPreferenceItem(type: String, inApp: Boolean, email: Boolean)`.

- [ ] **Step 1: Write the failing test** — create `NotificationPreferenceControllerTest.kt` (thin unit test of the controller's mapping, MockK):

```kotlin
package com.taskowolf.notifications

import com.taskowolf.auth.domain.User
import com.taskowolf.notifications.api.NotificationPreferenceController
import com.taskowolf.notifications.api.dto.NotificationPreferenceItem
import com.taskowolf.notifications.api.dto.NotificationPreferencesRequest
import com.taskowolf.notifications.application.NotificationPreferenceService
import com.taskowolf.notifications.domain.NotificationPreference
import com.taskowolf.notifications.domain.NotificationType
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NotificationPreferenceControllerTest {

    private val service = mockk<NotificationPreferenceService>(relaxed = true)
    private val controller = NotificationPreferenceController(service)
    private val user = User(email = "u@x.com", displayName = "U")

    @Test
    fun `get maps matrix to dto`() {
        every { service.getMatrix(user.id) } returns listOf(
            NotificationPreference(user.id, NotificationType.COMMENT_MENTION, inAppEnabled = false, emailEnabled = true)
        )

        val body = controller.get(user)

        assertEquals(1, body.preferences.size)
        assertEquals("COMMENT_MENTION", body.preferences[0].type)
        assertEquals(false, body.preferences[0].inApp)
        assertEquals(true, body.preferences[0].email)
    }

    @Test
    fun `put converts items to typed map and returns refreshed matrix`() {
        every { service.getMatrix(user.id) } returns emptyList()
        val request = NotificationPreferencesRequest(listOf(
            NotificationPreferenceItem("ISSUE_ASSIGNED", inApp = false, email = false)
        ))

        controller.update(request, user)

        val captured = slot<Map<NotificationType, Pair<Boolean, Boolean>>>()
        verify { service.update(user.id, capture(captured)) }
        assertEquals(Pair(false, false), captured.captured[NotificationType.ISSUE_ASSIGNED])
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.notifications.NotificationPreferenceControllerTest"`
Expected: FAIL — controller/DTOs do not exist.

- [ ] **Step 3: Implement**

Create `NotificationPreferencesResponse.kt`:

```kotlin
package com.taskowolf.notifications.api.dto

import com.taskowolf.notifications.domain.NotificationPreference

data class NotificationPreferenceItem(
    val type: String,
    val inApp: Boolean,
    val email: Boolean
)

data class NotificationPreferencesResponse(val preferences: List<NotificationPreferenceItem>) {
    companion object {
        fun from(prefs: List<NotificationPreference>) = NotificationPreferencesResponse(
            prefs.map { NotificationPreferenceItem(it.type.name, it.inAppEnabled, it.emailEnabled) }
        )
    }
}
```

Create `NotificationPreferencesRequest.kt`:

```kotlin
package com.taskowolf.notifications.api.dto

data class NotificationPreferencesRequest(
    val preferences: List<NotificationPreferenceItem>
)
```

Create `NotificationPreferenceController.kt`:

```kotlin
package com.taskowolf.notifications.api

import com.taskowolf.auth.domain.User
import com.taskowolf.notifications.api.dto.NotificationPreferencesRequest
import com.taskowolf.notifications.api.dto.NotificationPreferencesResponse
import com.taskowolf.notifications.application.NotificationPreferenceService
import com.taskowolf.notifications.domain.NotificationType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/me/notification-preferences")
class NotificationPreferenceController(
    private val service: NotificationPreferenceService
) {
    @GetMapping
    fun get(@AuthenticationPrincipal user: User): NotificationPreferencesResponse =
        NotificationPreferencesResponse.from(service.getMatrix(user.id))

    @PutMapping
    fun update(
        @RequestBody request: NotificationPreferencesRequest,
        @AuthenticationPrincipal user: User
    ): NotificationPreferencesResponse {
        val map = request.preferences.associate {
            NotificationType.valueOf(it.type) to Pair(it.inApp, it.email)
        }
        service.update(user.id, map)
        return NotificationPreferencesResponse.from(service.getMatrix(user.id))
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.notifications.NotificationPreferenceControllerTest"`
Expected: PASS. Then run the whole backend suite to catch cross-cutting breakage: `cd backend && ./gradlew test` → BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/notifications/api backend/src/test/kotlin/com/taskowolf/notifications/NotificationPreferenceControllerTest.kt
git commit -m "feat(backend): GET/PUT /me/notification-preferences (backlog #3)"
```

---

### Task 8: Frontend API module + hooks (`me.ts`, `useMe.ts`)

**Files:**
- Create: `frontend/src/api/me.ts`
- Create: `frontend/src/hooks/useMe.ts`

**Interfaces:**
- Produces: `meApi.updateProfile / changePassword / getNotificationPreferences / updateNotificationPreferences`.
- Produces: hooks `useUpdateProfile()` (invalidates `['me']`), `useChangePassword()`, `useNotificationPreferences()` (query key `['notification-preferences']`), `useUpdateNotificationPreferences()`.
- Produces type: `NotificationPreferenceItem { type: string; inApp: boolean; email: boolean }`.

- [ ] **Step 1: Create `frontend/src/api/me.ts`**

```typescript
import { apiClient } from './client'
import type { User } from '@/types'

export interface NotificationPreferenceItem {
  type: string
  inApp: boolean
  email: boolean
}

export const meApi = {
  updateProfile: (displayName: string) =>
    apiClient.patch<User>('/me', { displayName }),
  changePassword: (currentPassword: string, newPassword: string) =>
    apiClient.post('/me/password', { currentPassword, newPassword }),
  getNotificationPreferences: () =>
    apiClient.get<{ preferences: NotificationPreferenceItem[] }>('/me/notification-preferences'),
  updateNotificationPreferences: (preferences: NotificationPreferenceItem[]) =>
    apiClient.put<{ preferences: NotificationPreferenceItem[] }>('/me/notification-preferences', { preferences }),
}
```

- [ ] **Step 2: Create `frontend/src/hooks/useMe.ts`**

```typescript
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { meApi, type NotificationPreferenceItem } from '@/api/me'

export function useUpdateProfile() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (displayName: string) => meApi.updateProfile(displayName).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['me'] }),
  })
}

export function useChangePassword() {
  return useMutation({
    mutationFn: (vars: { currentPassword: string; newPassword: string }) =>
      meApi.changePassword(vars.currentPassword, vars.newPassword).then(r => r.data),
  })
}

export function useNotificationPreferences() {
  return useQuery({
    queryKey: ['notification-preferences'],
    queryFn: () => meApi.getNotificationPreferences().then(r => r.data.preferences),
  })
}

export function useUpdateNotificationPreferences() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (preferences: NotificationPreferenceItem[]) =>
      meApi.updateNotificationPreferences(preferences).then(r => r.data.preferences),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['notification-preferences'] }),
  })
}
```

- [ ] **Step 3: Typecheck**

Run: `cd frontend && npx tsc --noEmit`
Expected: no errors.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/api/me.ts frontend/src/hooks/useMe.ts
git commit -m "feat(frontend): me API module + hooks for profile/password/notification prefs (backlog #3)"
```

---

### Task 9: `ProfilePage`

**Files:**
- Create: `frontend/src/pages/settings/ProfilePage.tsx`

**Interfaces:**
- Consumes: `authApi.me()` (query key `['me']`), `useUpdateProfile()`.
- Produces: `ProfilePage` (named export) — routed in Task 12.

- [ ] **Step 1: Create `frontend/src/pages/settings/ProfilePage.tsx`**

```tsx
import { useState, useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { authApi } from '@/api/auth'
import { useUpdateProfile } from '@/hooks/useMe'

export function ProfilePage() {
  const { data: me } = useQuery({ queryKey: ['me'], queryFn: () => authApi.me().then(r => r.data) })
  const update = useUpdateProfile()
  const [displayName, setDisplayName] = useState('')
  const [saved, setSaved] = useState(false)

  useEffect(() => { if (me) setDisplayName(me.displayName) }, [me])

  async function handleSave() {
    if (!displayName.trim()) return
    try {
      await update.mutateAsync(displayName.trim())
      setSaved(true)
      setTimeout(() => setSaved(false), 2000)
    } catch (e: any) {
      alert(e.response?.data?.message || 'Failed to update profile')
    }
  }

  return (
    <div className="max-w-2xl">
      <h1 className="text-2xl font-bold mb-6">Profile</h1>
      <div className="flex flex-col gap-4">
        <label className="text-sm text-gray-300">
          Email
          <input
            type="email"
            value={me?.email ?? ''}
            readOnly
            className="w-full mt-1 px-3 py-2 bg-gray-900 rounded border border-gray-700 text-sm text-gray-400 cursor-not-allowed"
          />
        </label>
        <label className="text-sm text-gray-300">
          Display name
          <input
            type="text"
            value={displayName}
            onChange={e => setDisplayName(e.target.value)}
            className="w-full mt-1 px-3 py-2 bg-gray-900 rounded border border-gray-600 text-sm"
          />
        </label>
        <div className="flex items-center gap-3">
          <button
            onClick={handleSave}
            disabled={update.isPending || !displayName.trim()}
            className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 rounded text-sm font-medium"
          >
            {update.isPending ? 'Saving…' : 'Save'}
          </button>
          {saved && <span className="text-sm text-green-400">Saved</span>}
        </div>
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Typecheck**

Run: `cd frontend && npx tsc --noEmit`
Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/pages/settings/ProfilePage.tsx
git commit -m "feat(frontend): ProfilePage (edit display name) (backlog #3)"
```

---

### Task 10: `SecurityPage`

**Files:**
- Create: `frontend/src/pages/settings/SecurityPage.tsx`

**Interfaces:**
- Consumes: `useChangePassword()`; on success clears tokens and navigates to `/login`.

- [ ] **Step 1: Create `frontend/src/pages/settings/SecurityPage.tsx`**

```tsx
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useChangePassword } from '@/hooks/useMe'

export function SecurityPage() {
  const change = useChangePassword()
  const navigate = useNavigate()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState('')

  async function handleSubmit() {
    setError('')
    if (next.length < 8) { setError('New password must be at least 8 characters'); return }
    if (next !== confirm) { setError('New passwords do not match'); return }
    try {
      await change.mutateAsync({ currentPassword: current, newPassword: next })
      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')
      navigate('/login')
    } catch (e: any) {
      setError(e.response?.data?.message || 'Failed to change password')
    }
  }

  const inputClass = 'w-full mt-1 px-3 py-2 bg-gray-900 rounded border border-gray-600 text-sm'

  return (
    <div className="max-w-2xl">
      <h1 className="text-2xl font-bold mb-6">Security</h1>
      <div className="flex flex-col gap-4">
        <label className="text-sm text-gray-300">
          Current password
          <input type="password" value={current} onChange={e => setCurrent(e.target.value)} className={inputClass} />
        </label>
        <label className="text-sm text-gray-300">
          New password
          <input type="password" value={next} onChange={e => setNext(e.target.value)} className={inputClass} />
        </label>
        <label className="text-sm text-gray-300">
          Confirm new password
          <input type="password" value={confirm} onChange={e => setConfirm(e.target.value)} className={inputClass} />
        </label>
        {error && <p className="text-sm text-red-400">{error}</p>}
        <p className="text-xs text-gray-500">
          Changing your password signs you out of all sessions. Personal access tokens keep working.
        </p>
        <button
          onClick={handleSubmit}
          disabled={change.isPending || !current || !next || !confirm}
          className="self-start px-4 py-2 bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 rounded text-sm font-medium"
        >
          {change.isPending ? 'Changing…' : 'Change password'}
        </button>
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Typecheck**

Run: `cd frontend && npx tsc --noEmit`
Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/pages/settings/SecurityPage.tsx
git commit -m "feat(frontend): SecurityPage (change password, re-login) (backlog #3)"
```

---

### Task 11: `NotificationSettingsPage`

**Files:**
- Create: `frontend/src/pages/settings/NotificationSettingsPage.tsx`

**Interfaces:**
- Consumes: `useNotificationPreferences()`, `useUpdateNotificationPreferences()`, type `NotificationPreferenceItem`.
- Renders a matrix: rows = the 4 notification types, columns = In-app / Email. Email checkboxes for `AUTOMATION` and `SLA_BREACHED` are editable but marked "no email sent yet" (future-proof).

- [ ] **Step 1: Create `frontend/src/pages/settings/NotificationSettingsPage.tsx`**

```tsx
import { useState, useEffect } from 'react'
import { useNotificationPreferences, useUpdateNotificationPreferences } from '@/hooks/useMe'
import type { NotificationPreferenceItem } from '@/api/me'

const TYPE_LABELS: Record<string, string> = {
  COMMENT_MENTION: 'Mentions',
  ISSUE_ASSIGNED: 'Issue assigned to me',
  AUTOMATION: 'Automation',
  SLA_BREACHED: 'SLA breached',
}
const EMAIL_SUPPORTED = new Set(['COMMENT_MENTION', 'ISSUE_ASSIGNED'])

export function NotificationSettingsPage() {
  const { data, isLoading } = useNotificationPreferences()
  const update = useUpdateNotificationPreferences()
  const [rows, setRows] = useState<NotificationPreferenceItem[]>([])
  const [saved, setSaved] = useState(false)

  useEffect(() => { if (data) setRows(data) }, [data])

  function toggle(type: string, channel: 'inApp' | 'email') {
    setRows(rows.map(r => (r.type === type ? { ...r, [channel]: !r[channel] } : r)))
  }

  async function handleSave() {
    try {
      await update.mutateAsync(rows)
      setSaved(true)
      setTimeout(() => setSaved(false), 2000)
    } catch (e: any) {
      alert(e.response?.data?.message || 'Failed to save preferences')
    }
  }

  if (isLoading) return <div className="text-gray-400">Loading…</div>

  return (
    <div className="max-w-2xl">
      <h1 className="text-2xl font-bold mb-6">Notifications</h1>
      <table className="w-full text-sm">
        <thead>
          <tr className="text-left text-gray-400 border-b border-gray-700">
            <th className="py-2">Type</th>
            <th className="py-2 w-24 text-center">In-app</th>
            <th className="py-2 w-24 text-center">Email</th>
          </tr>
        </thead>
        <tbody>
          {rows.map(row => {
            const emailSupported = EMAIL_SUPPORTED.has(row.type)
            return (
              <tr key={row.type} className="border-b border-gray-800">
                <td className="py-3">{TYPE_LABELS[row.type] ?? row.type}</td>
                <td className="py-3 text-center">
                  <input type="checkbox" checked={row.inApp} onChange={() => toggle(row.type, 'inApp')} />
                </td>
                <td className="py-3 text-center">
                  <input
                    type="checkbox"
                    checked={row.email}
                    onChange={() => toggle(row.type, 'email')}
                    title={emailSupported ? undefined : 'No email is sent for this type yet — saved for the future.'}
                  />
                  {!emailSupported && <span className="ml-1 text-xs text-gray-500">*</span>}
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
      <p className="mt-2 text-xs text-gray-500">* No email is currently sent for these types; the preference is saved for future use.</p>
      <div className="flex items-center gap-3 mt-6">
        <button
          onClick={handleSave}
          disabled={update.isPending}
          className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 rounded text-sm font-medium"
        >
          {update.isPending ? 'Saving…' : 'Save'}
        </button>
        {saved && <span className="text-sm text-green-400">Saved</span>}
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Typecheck**

Run: `cd frontend && npx tsc --noEmit`
Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/pages/settings/NotificationSettingsPage.tsx
git commit -m "feat(frontend): NotificationSettingsPage (per-type in-app/email matrix) (backlog #3)"
```

---

### Task 12: Settings shell — `SettingsLayout`, router restructure, sidebar entry

**Files:**
- Create: `frontend/src/layouts/SettingsLayout.tsx`
- Modify: `frontend/src/app/router.tsx`
- Modify: `frontend/src/layouts/AppLayout.tsx`

**Interfaces:**
- Consumes: `ProfilePage`, `SecurityPage`, `NotificationSettingsPage` (Tasks 9–11), existing `AccessTokensPage`, `AccountSettingsPage`.
- Produces: nested `/settings` route with children `profile / security / notifications / tokens / account`; `/settings` index redirects to `/settings/profile`.

- [ ] **Step 1: Create `frontend/src/layouts/SettingsLayout.tsx`**

```tsx
import { Outlet, NavLink } from 'react-router-dom'
import { User, Shield, Bell, KeyRound, UserX } from 'lucide-react'

const items = [
  { to: '/settings/profile', label: 'Profile', icon: User },
  { to: '/settings/security', label: 'Security', icon: Shield },
  { to: '/settings/notifications', label: 'Notifications', icon: Bell },
  { to: '/settings/tokens', label: 'Access Tokens', icon: KeyRound },
  { to: '/settings/account', label: 'Account', icon: UserX },
]

export function SettingsLayout() {
  return (
    <div className="flex gap-8 h-full min-h-0">
      <nav className="w-48 shrink-0 flex flex-col gap-1">
        <h2 className="px-3 text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">Settings</h2>
        {items.map(({ to, label, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            className={({ isActive }) =>
              `flex items-center gap-3 px-3 py-2 rounded text-sm ${
                isActive ? 'bg-indigo-600 text-white font-semibold' : 'text-gray-400 hover:bg-gray-800 hover:text-white'
              }`
            }
          >
            <Icon size={18} className="shrink-0" />
            <span className="truncate">{label}</span>
          </NavLink>
        ))}
      </nav>
      <div className="flex-1 min-h-0">
        <Outlet />
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Restructure the router** — edit `frontend/src/app/router.tsx`.

Add imports (near the other page imports):

```typescript
import { SettingsLayout } from '@/layouts/SettingsLayout'
import { ProfilePage } from '@/pages/settings/ProfilePage'
import { SecurityPage } from '@/pages/settings/SecurityPage'
import { NotificationSettingsPage } from '@/pages/settings/NotificationSettingsPage'
```

Replace these two lines:

```typescript
      { path: '/settings/tokens', element: <AccessTokensPage /> },
      { path: '/settings/account', element: <AccountSettingsPage /> },
```

with the nested route:

```typescript
      {
        path: '/settings',
        element: <SettingsLayout />,
        children: [
          { index: true, element: <Navigate to="/settings/profile" replace /> },
          { path: 'profile', element: <ProfilePage /> },
          { path: 'security', element: <SecurityPage /> },
          { path: 'notifications', element: <NotificationSettingsPage /> },
          { path: 'tokens', element: <AccessTokensPage /> },
          { path: 'account', element: <AccountSettingsPage /> },
        ],
      },
```

(`Navigate` is already imported at the top of the file.)

- [ ] **Step 3: Collapse the sidebar "Account" section to a single Settings entry** — edit `frontend/src/layouts/AppLayout.tsx`.

In the icon import block, remove `KeyRound, User,` (now unused) and add `Settings`. The changed import line becomes:

```typescript
  LayoutDashboard, FolderKanban, Building2, ScrollText, Zap, Users,
  Kanban, ListChecks, CalendarRange, ListTodo, BarChart3,
  LifeBuoy, AlertTriangle, KeySquare, Webhook, Plug, Tags, Milestone,
  SlidersHorizontal, ChevronLeft, ChevronRight, LogOut, Settings,
```

Replace the Account section block:

```tsx
          <div className="mt-4">
            {sectionLabel('Account')}
            <div className="flex flex-col gap-1">
              <NavItem to="/settings/tokens" label="Access Tokens" icon={KeyRound} collapsed={collapsed} variant="sub" />
              <NavItem to="/settings/account" label="Account" icon={User} collapsed={collapsed} variant="sub" />
            </div>
          </div>
```

with:

```tsx
          <div className="mt-4">
            {sectionLabel('Account')}
            <div className="flex flex-col gap-1">
              <NavItem to="/settings" label="Settings" icon={Settings} collapsed={collapsed} variant="sub" />
            </div>
          </div>
```

- [ ] **Step 4: Typecheck**

Run: `cd frontend && npx tsc --noEmit`
Expected: no errors (confirms `KeyRound`/`User` are no longer referenced and everything resolves).

- [ ] **Step 5: Manual browser verification**

Start the app (or use the running dev server) and verify:
1. Sidebar shows a single **Settings** entry under "Account"; clicking it lands on `/settings/profile`.
2. Sub-nav switches between Profile / Security / Notifications / Access Tokens / Account.
3. **Profile:** email is read-only; changing the display name and saving updates the name shown in the sidebar (the `['me']` invalidation).
4. **Security:** wrong current password shows an error; a valid change redirects to `/login` and the old session no longer works.
5. **Notifications:** the matrix loads with all four rows; toggling + Save persists across reload.
6. **Access Tokens** page still lists tokens and its list scrolls inside the pane (DataTable height chain intact under the new layout); **Account** delete flow still works.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/layouts/SettingsLayout.tsx frontend/src/app/router.tsx frontend/src/layouts/AppLayout.tsx
git commit -m "feat(frontend): settings shell with sub-nav; single sidebar Settings entry (backlog #3)"
```

---

### Task 13: Wiki docs + `ai-guide.md` update (mandatory closing task)

**Files:**
- Modify: `mkdocs/developer-guide/ai-guide.md` (bump current Flyway version to **V29**)
- Modify: `mkdocs/developer-guide/backend/notifications.md` (document preferences + gating)
- Modify: the auth module doc that covers `/me` endpoints (locate under `mkdocs/developer-guide/backend/`) — add `PATCH /me` and `POST /me/password`
- Modify: `mkdocs/user-guide/` settings/notifications page if one exists (add the Settings area + notification preferences)

**Interfaces:** documentation only — no code contract.

- [ ] **Step 1: Bump Flyway version references in `ai-guide.md`**

Update the checklist and Flyway-migration section so both read **V29** as the current version and **V30** as the next. Replace occurrences of "**V28**" / "next migration must be **V29**" accordingly (lines around the Pre-Implementation Checklist item 3 and the "Backend: Flyway Migration" section).

- [ ] **Step 2: Document the notification preference model**

In `mkdocs/developer-guide/backend/notifications.md`, add a short section: `NotificationPreference` entity (per user × type, `in_app_enabled` / `email_enabled`), opt-out default (missing row = enabled), `NotificationPreferenceService.isEnabled(...)` gating both `NotificationService` (in-app, covers all 4 types incl. `AUTOMATION`/`SLA_BREACHED` via `createDirect`) and `EmailService` (email, mention/assigned only), and the `GET`/`PUT /api/v1/me/notification-preferences` endpoints.

- [ ] **Step 3: Document the new `/me` endpoints**

Add `PATCH /api/v1/me` (update display name) and `POST /api/v1/me/password` (verify current password → re-hash → revoke refresh tokens, **not** PATs → force re-login) to the relevant auth/me doc page.

- [ ] **Step 4: Verify docs build (if the toolchain is available)**

Run: `cd mkdocs && mkdocs build --strict` (skip if MkDocs is not installed locally; otherwise expect a clean build).

- [ ] **Step 5: Commit**

```bash
git add mkdocs
git commit -m "docs: settings pages, /me endpoints, notification preferences; Flyway V29 (backlog #3)"
```

---

## Self-Review

**Spec coverage (`2026-07-08-profile-settings-design.md`):**
- Dedicated Settings shell with sub-nav → Task 12 (`SettingsLayout`, router, sidebar). ✅
- Profile / change display name (`PATCH /me`) → Tasks 1, 9. ✅
- Security / change password revoking sessions, PATs kept → Tasks 2, 10. ✅
- Notification preferences: entity + V29 + dispatch-gating, 4 types × 2 channels → Tasks 3–7, 11. ✅
- Move existing Access Tokens / Account under shell, paths unchanged → Task 12 (nested children `tokens`/`account`). ✅
- Explicitly out of scope (email change, avatar) → not planned. ✅
- Backend TDD (MockK) + real-Postgres migration test → Tasks 1–7 (MockK) + Task 3 (`IntegrationTestBase`). ✅
- Frontend typecheck + manual → every frontend task. ✅
- Wiki docs + `ai-guide` Flyway bump → Task 13. ✅
- Audit events `USER_PROFILE_UPDATED` / `USER_PASSWORD_CHANGED` → mapped to `PROFILE_UPDATED` (new) and existing `PASSWORD_CHANGED` via `SecurityAuditListener` (Tasks 1–2). ✅

**Placeholder scan:** no TBD/TODO; every code step contains complete code; every test step contains real assertions. ✅

**Type consistency:** `NotificationPreferenceService.isEnabled/getMatrix/update`, `NotificationChannel.{IN_APP,EMAIL}`, `NotificationPreferenceItem{type,inApp,email}`, and the `NotificationService(repository, preferences)` / `EmailService(preferences, mailSender, …)` / `UserAccountService(…, passwordEncoder, securityAuditListener)` constructor orders are used consistently across producing and consuming tasks. Existing tests that construct these services are updated in the same task that changes the constructor (Tasks 1, 5, 6). ✅

**Ordering:** backend Tasks 1–7 are self-contained; frontend Tasks 9–11 create page files that only get routed in Task 12, so each intermediate `tsc --noEmit` passes. ✅
