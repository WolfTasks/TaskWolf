# Security Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the vulnerabilities found in the 2026-06-10 security review (cross-project IDOR, default JWT secret, unauthenticated WebSocket topics, irrevocable refresh tokens) and harden the default self-hosted deployment.

**Architecture:** Backend fixes follow the existing module layout (`auth`, `issues`, `projects`, `core`). New server-side refresh-token store (Flyway `V6`) makes refresh tokens revocable while access tokens stay stateless. A STOMP `ChannelInterceptor` adds per-user auth and per-project subscription authorization to WebSockets. Infra hardening lives in `docker/` and the compose files.

**Tech Stack:** Kotlin + Spring Boot 3 / Spring Security, jjwt 0.12, Flyway, MockK unit tests + Testcontainers integration tests, React 19 + @stomp/stompjs frontend, nginx + docker-compose.

**Security review findings addressed (severity → task):**

| # | Finding | Severity | Task |
|---|---------|----------|------|
| 1 | `IssueService.findByKey` returns issues from foreign projects (IDOR) | High | 1 |
| 2 | `parentId` in issue create can reference foreign projects' issues | High | 2 |
| 3 | Known default JWT secret falls back in prod (`application.yml`) | High | 3 |
| 4 | WebSocket: no STOMP auth, `*` origins, no topic authorization | High | 4 |
| 5 | Refresh tokens irrevocable for 7 days, logout is client-side only | Medium | 5 |
| 6 | Issue assignee not validated as project member | Medium | 6 |
| 7 | Open registration on self-hosted instances, no admin bootstrap | Medium | 7 |
| 8 | H2 console/Swagger permitAll + frameOptions disabled also in prod; DB error details leak to clients | Medium | 8 |
| 9 | No brute-force protection on `/api/v1/auth/*` | Medium | 9 |
| 10 | nginx without security headers/body limit; containers run as root; `changeme` DB password default | Medium | 10 |

Accepted (not addressed): JWT in `localStorage` (no `dangerouslySetInnerHTML` in the codebase, React auto-escaping; revisit when OAuth2 lands), email enumeration via register conflict message (standard UX trade-off, mitigated by Task 9 rate limiting).

---

### Task 1: Fix cross-project issue read (IDOR) in `IssueService.findByKey`

`findByKey` checks membership for `projectKey` but then loads the issue by its **global** key. Any user who is member of *any* project can read every issue of all projects via `GET /api/v1/projects/{ownKey}/issues/{foreignIssueKey}`.

**Files:**
- Modify: `backend/src/main/kotlin/com/taskowolf/issues/infrastructure/IssueRepository.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/issues/application/IssueService.kt:94-98`
- Test: `backend/src/test/kotlin/com/taskowolf/issues/IssueServiceTest.kt`

- [ ] **Step 1: Add the project-scoped finder to the repository**

In `IssueRepository.kt`, replace `fun findByKey(key: String): Issue?` with:

```kotlin
    fun findByKeyAndProjectId(key: String, projectId: UUID): Issue?
```

(Search for other usages of `findByKey` first: `grep -r "issueRepository.findByKey" backend/src` — at the time of the review, `IssueService.findByKey` is the only caller.)

- [ ] **Step 2: Write the failing test**

Add to `IssueServiceTest.kt`:

```kotlin
    @Test
    fun `findByKey does not return issues from another project`() {
        every { projectService.requireMember("WOLF", owner.id) } returns project
        every { issueRepository.findByKeyAndProjectId("OTHER-1", project.id) } returns null

        assertThrows<NotFoundException> {
            service.findByKey("WOLF", "OTHER-1", owner.id)
        }
    }
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.issues.IssueServiceTest"`
Expected: FAIL — compilation error (`findByKey(key)` no longer exists) or MockK "no answer found" because the service still calls the unscoped finder.

- [ ] **Step 4: Fix the service**

In `IssueService.kt`, replace the `findByKey` function with:

```kotlin
    @Transactional(readOnly = true)
    fun findByKey(projectKey: String, issueKey: String, userId: UUID): Issue {
        val project = projectService.requireMember(projectKey, userId)
        return issueRepository.findByKeyAndProjectId(issueKey, project.id)
            ?: throw NotFoundException("Issue not found: $issueKey")
    }
```

- [ ] **Step 5: Run all issue tests to verify they pass**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.issues.*"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/issues backend/src/test/kotlin/com/taskowolf/issues
git commit -m "fix(security): scope issue lookup by key to the requested project"
```

---

### Task 2: Prevent cross-project parent links in issue creation

`IssueService.create` loads `request.parentId` without checking it belongs to the same project, allowing probing/linking of foreign issues.

**Files:**
- Modify: `backend/src/main/kotlin/com/taskowolf/issues/application/IssueService.kt:50-52`
- Test: `backend/src/test/kotlin/com/taskowolf/issues/IssueServiceTest.kt`

- [ ] **Step 1: Write the failing test**

Add to `IssueServiceTest.kt` (imports for `Optional` and `IssuePriority`/`IssueType` already resolve via existing imports; add `import java.util.Optional` if missing):

```kotlin
    @Test
    fun `create rejects parent issue from another project`() {
        val workflowId = UUID.randomUUID()
        every { workflow.id } returns workflowId
        every { projectService.requireMember("WOLF", owner.id) } returns project
        every { workflowService.getDefaultStatus(workflowId) } returns status
        every { issueRepository.maxKeyNumberByProject(project.id) } returns 0
        every { issueRepository.save(any()) } returnsArgument 0

        val foreignProject = Project(key = "OTHER", name = "Other", owner = owner, workflow = workflow)
        val foreignParent = Issue(
            key = "OTHER-1", keyNumber = 1, title = "Foreign", type = IssueType.TASK,
            status = status, project = foreignProject, reporter = owner
        )
        every { issueRepository.findById(foreignParent.id) } returns Optional.of(foreignParent)

        assertThrows<NotFoundException> {
            service.create("WOLF", CreateIssueRequest("Child", parentId = foreignParent.id), owner)
        }
    }
```

(If the `Issue` constructor signature differs, mirror the named arguments used in `IssueService.create` — only `project` must differ from the requesting project.)

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.issues.IssueServiceTest"`
Expected: FAIL — no exception is thrown; the foreign parent is accepted.

- [ ] **Step 3: Fix the parent lookup**

In `IssueService.create`, replace the `parent = ...` argument with:

```kotlin
                parent = request.parentId?.let { parentId ->
                    issueRepository.findById(parentId)
                        .filter { it.project.id == project.id }
                        .orElseThrow { NotFoundException("Parent issue not found: $parentId") }
                }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.issues.*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/issues backend/src/test/kotlin/com/taskowolf/issues
git commit -m "fix(security): reject parent issues from other projects"
```

---

### Task 3: Remove the production JWT secret fallback

`application.yml` falls back to the publicly known string `dev-secret-change-in-production-min-256-bits` when `TW_JWT_SECRET` is unset. Anyone can forge valid tokens for such an instance. The default must only exist in the `dev` profile; without an explicit secret, prod startup must fail loudly.

**Files:**
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/resources/application-dev.yml`
- Modify: `backend/src/main/kotlin/com/taskowolf/auth/application/JwtService.kt:19-24`
- Modify: `backend/src/test/kotlin/com/taskowolf/IntegrationTestBase.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/auth/JwtServiceTest.kt` (create if it does not exist)

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/kotlin/com/taskowolf/auth/JwtServiceTest.kt` (or add to it if present):

```kotlin
package com.taskowolf.auth

import com.taskowolf.auth.application.JwtService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class JwtServiceTest {

    @Test
    fun `startup validation rejects blank secret with actionable message`() {
        val service = JwtService(secret = "", accessExpiry = 900, refreshExpiry = 604800)
        val ex = assertThrows<IllegalArgumentException> { service.validate() }
        assert(ex.message!!.contains("TW_JWT_SECRET"))
    }

    @Test
    fun `startup validation rejects short secret`() {
        val service = JwtService(secret = "too-short", accessExpiry = 900, refreshExpiry = 604800)
        assertThrows<IllegalArgumentException> { service.validate() }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.auth.JwtServiceTest"`
Expected: FAIL — the current message says `taskowolf.jwt.secret must be at least 32 bytes` and does not contain `TW_JWT_SECRET`.

- [ ] **Step 3: Update the validation message in `JwtService`**

```kotlin
    @PostConstruct
    fun validate() {
        require(secret.isNotBlank() && secret.toByteArray().size >= 32) {
            "TW_JWT_SECRET must be set to a random value of at least 32 bytes " +
                "(e.g. generate one with: openssl rand -base64 48)"
        }
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.auth.JwtServiceTest"`
Expected: PASS

- [ ] **Step 5: Move the default secret into the dev profile**

In `application.yml`, change:

```yaml
taskowolf:
  jwt:
    secret: ${TW_JWT_SECRET:}
```

In `application-dev.yml`, add at top level:

```yaml
taskowolf:
  jwt:
    secret: ${TW_JWT_SECRET:dev-secret-change-in-production-min-256-bits}
```

- [ ] **Step 6: Provide the secret for integration tests**

In `IntegrationTestBase.configureProperties`, add:

```kotlin
            registry.add("taskowolf.jwt.secret") { "integration-test-secret-0123456789abcdef" }
```

- [ ] **Step 7: Run the full backend test suite**

Run: `cd backend && ./gradlew test`
Expected: PASS (integration tests boot with the injected secret; without Step 6 the context would fail to start).

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/resources backend/src/main/kotlin/com/taskowolf/auth backend/src/test/kotlin/com/taskowolf
git commit -m "fix(security): fail startup without explicit JWT secret outside dev"
```

---

### Task 4: Authenticate WebSocket connections and authorize topic subscriptions

`/ws-stomp` allows any origin, the frontend sends no credentials, and the simple broker performs no subscription checks — any client can subscribe to `/topic/projects/{key}` for any project. (Side effect of the current setup: since `anyRequest().authenticated()` also covers the handshake and the client sends no token, real-time updates silently fail today — this task also fixes that.)

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/core/infrastructure/StompAuthChannelInterceptor.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/core/infrastructure/WebSocketConfig.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/SecurityConfig.kt` (permit the handshake; final state in Task 8)
- Modify: `frontend/src/hooks/useProjectSocket.ts`
- Test: `backend/src/test/kotlin/com/taskowolf/core/StompAuthChannelInterceptorTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `backend/src/test/kotlin/com/taskowolf/core/StompAuthChannelInterceptorTest.kt`:

```kotlin
package com.taskowolf.core

import com.taskowolf.auth.application.JwtService
import com.taskowolf.core.infrastructure.StompAuthChannelInterceptor
import com.taskowolf.core.infrastructure.StompPrincipal
import com.taskowolf.projects.domain.Project
import com.taskowolf.auth.domain.User
import com.taskowolf.projects.infrastructure.ProjectMemberRepository
import com.taskowolf.projects.infrastructure.ProjectRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.MessagingException
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.MessageBuilder
import java.util.UUID

class StompAuthChannelInterceptorTest {

    private val jwtService = mockk<JwtService>()
    private val projectRepository = mockk<ProjectRepository>()
    private val memberRepository = mockk<ProjectMemberRepository>()
    private val interceptor = StompAuthChannelInterceptor(jwtService, projectRepository, memberRepository)
    private val channel = mockk<MessageChannel>()

    private fun message(command: StompCommand, configure: (StompHeaderAccessor) -> Unit) =
        StompHeaderAccessor.create(command).apply(configure)
            .let { MessageBuilder.createMessage(ByteArray(0), it.messageHeaders) }

    @Test
    fun `CONNECT without token is rejected`() {
        val msg = message(StompCommand.CONNECT) {}
        assertThrows<MessagingException> { interceptor.preSend(msg, channel) }
    }

    @Test
    fun `CONNECT with valid token is accepted`() {
        val userId = UUID.randomUUID()
        every { jwtService.validateToken("good-token") } returns userId
        val msg = message(StompCommand.CONNECT) {
            it.addNativeHeader("Authorization", "Bearer good-token")
        }
        interceptor.preSend(msg, channel) // must not throw
    }

    @Test
    fun `SUBSCRIBE to a project the user is not a member of is rejected`() {
        val userId = UUID.randomUUID()
        val owner = User(email = "o@t.io", displayName = "O")
        val project = Project(key = "WOLF", name = "TaskWolf", owner = owner)
        every { projectRepository.findByKey("WOLF") } returns project
        every { memberRepository.existsByProjectIdAndUserId(project.id, userId) } returns false

        val msg = message(StompCommand.SUBSCRIBE) {
            it.destination = "/topic/projects/WOLF"
            it.user = StompPrincipal(userId)
        }
        assertThrows<MessagingException> { interceptor.preSend(msg, channel) }
    }

    @Test
    fun `SUBSCRIBE to a project the user is a member of is accepted`() {
        val userId = UUID.randomUUID()
        val owner = User(email = "o@t.io", displayName = "O")
        val project = Project(key = "WOLF", name = "TaskWolf", owner = owner)
        every { projectRepository.findByKey("WOLF") } returns project
        every { memberRepository.existsByProjectIdAndUserId(project.id, userId) } returns true

        val msg = message(StompCommand.SUBSCRIBE) {
            it.destination = "/topic/projects/WOLF"
            it.user = StompPrincipal(userId)
        }
        interceptor.preSend(msg, channel) // must not throw
    }
}
```

(If `Project`'s constructor requires `workflow`, pass `workflow = null`.)

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.core.StompAuthChannelInterceptorTest"`
Expected: FAIL — `StompAuthChannelInterceptor` and `StompPrincipal` do not exist (compilation error).

- [ ] **Step 3: Implement the interceptor**

Create `backend/src/main/kotlin/com/taskowolf/core/infrastructure/StompAuthChannelInterceptor.kt`:

```kotlin
package com.taskowolf.core.infrastructure

import com.taskowolf.auth.application.JwtService
import com.taskowolf.projects.infrastructure.ProjectMemberRepository
import com.taskowolf.projects.infrastructure.ProjectRepository
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.MessagingException
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.stereotype.Component
import java.security.Principal
import java.util.UUID

data class StompPrincipal(val userId: UUID) : Principal {
    override fun getName(): String = userId.toString()
}

@Component
class StompAuthChannelInterceptor(
    private val jwtService: JwtService,
    private val projectRepository: ProjectRepository,
    private val memberRepository: ProjectMemberRepository
) : ChannelInterceptor {

    private val projectTopic = Regex("^/topic/projects/([A-Z0-9]{2,10})$")

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*> {
        val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)
            ?: return message
        when (accessor.command) {
            StompCommand.CONNECT -> {
                val token = accessor.getFirstNativeHeader("Authorization")
                    ?.takeIf { it.startsWith("Bearer ") }
                    ?.substring(7)
                    ?: throw MessagingException("Missing Authorization header")
                val userId = jwtService.validateToken(token)
                    ?: throw MessagingException("Invalid or expired token")
                accessor.user = StompPrincipal(userId)
            }
            StompCommand.SUBSCRIBE -> {
                val principal = accessor.user as? StompPrincipal
                    ?: throw MessagingException("Not authenticated")
                val destination = accessor.destination ?: ""
                val key = projectTopic.find(destination)?.groupValues?.get(1)
                    ?: throw MessagingException("Subscription to $destination is not allowed")
                val project = projectRepository.findByKey(key)
                    ?: throw MessagingException("Unknown project")
                val allowed = project.owner.id == principal.userId ||
                    memberRepository.existsByProjectIdAndUserId(project.id, principal.userId)
                if (!allowed) throw MessagingException("Not a member of project $key")
            }
            else -> {}
        }
        return message
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.core.StompAuthChannelInterceptorTest"`
Expected: PASS

- [ ] **Step 5: Wire the interceptor and restrict origins in `WebSocketConfig`**

Replace `WebSocketConfig.kt` with:

```kotlin
package com.taskowolf.core.infrastructure

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer

@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig(
    private val stompAuthChannelInterceptor: StompAuthChannelInterceptor,
    @Value("\${taskowolf.allowed-origins}") private val allowedOrigins: List<String>
) : WebSocketMessageBrokerConfigurer {

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        registry.enableSimpleBroker("/topic", "/queue")
        registry.setApplicationDestinationPrefixes("/app")
    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws-stomp").setAllowedOriginPatterns(*allowedOrigins.toTypedArray())
    }

    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        registration.interceptors(stompAuthChannelInterceptor)
    }
}
```

Note: the unused SockJS endpoint `/ws` is removed deliberately — the frontend only uses `/ws-stomp`, and nginx's `location /ws` prefix-matches `/ws-stomp` too.

- [ ] **Step 6: Add the origins property**

In `application.yml` under `taskowolf:`, add:

```yaml
  allowed-origins: ${TW_ALLOWED_ORIGINS:${taskowolf.base-url}}
```

In `application-dev.yml`, add (Vite dev server runs on its own port):

```yaml
taskowolf:
  allowed-origins: "http://localhost:*"
```

(If `application-dev.yml` already has a `taskowolf:` block from Task 3, merge the keys into it.)

- [ ] **Step 7: Permit the WebSocket handshake in `SecurityConfig`**

The STOMP CONNECT frame now carries the credentials, so the HTTP upgrade itself can be anonymous. In `SecurityConfig.securityFilterChain`, add `"/ws-stomp/**"` to the `permitAll` matcher list:

```kotlin
                it.requestMatchers(
                    "/api/v1/auth/**",
                    "/ws-stomp/**",
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                    "/h2-console/**"
                ).permitAll()
```

(Task 8 makes the swagger/h2 entries dev-only; keep `/ws-stomp/**` permitted there.)

- [ ] **Step 8: Send the token from the frontend**

Replace the `Client` construction in `frontend/src/hooks/useProjectSocket.ts`:

```typescript
    const client = new Client({
      brokerURL: `${protocol}://${window.location.host}/ws-stomp`,
      reconnectDelay: 5000,
      beforeConnect: () => {
        client.connectHeaders = {
          Authorization: `Bearer ${localStorage.getItem('accessToken') ?? ''}`,
        }
      },
      onConnect: () => {
        // ... existing subscribe block unchanged ...
      },
    })
```

`beforeConnect` re-reads the token on every (re)connect so a refreshed access token is picked up.

- [ ] **Step 9: Run backend tests and frontend build**

Run: `cd backend && ./gradlew test`
Expected: PASS
Run: `cd frontend && npm run build`
Expected: build succeeds.

- [ ] **Step 10: Manual smoke test (dev)**

Start the dev stack, log in with two users where user B is *not* a member of project `WOLF`. In B's browser console, connecting and subscribing to `/topic/projects/WOLF` must terminate with a STOMP ERROR frame. Moving an issue as user A must still live-update A's board.

- [ ] **Step 11: Commit**

```bash
git add backend/src frontend/src
git commit -m "fix(security): authenticate STOMP connections and authorize project topic subscriptions"
```

---

### Task 5: Server-side refresh-token store with rotation, revocation and logout

Refresh tokens are pure JWTs: a stolen token works for 7 days and cannot be invalidated; logout only clears `localStorage`. Store a SHA-256 hash of each refresh token, rotate on use, and revoke on logout. Access tokens stay stateless (15 min lifetime bounds the exposure).

**Files:**
- Create: `backend/src/main/resources/db/migration/V6__create_refresh_tokens.sql`
- Create: `backend/src/main/kotlin/com/taskowolf/auth/domain/RefreshToken.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/RefreshTokenRepository.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/auth/application/RefreshTokenService.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/auth/application/AuthService.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/auth/api/AuthController.kt`
- Modify: `frontend/src/layouts/AppLayout.tsx`
- Test: `backend/src/test/kotlin/com/taskowolf/auth/AuthServiceTest.kt`, `backend/src/test/kotlin/com/taskowolf/auth/AuthControllerIntegrationTest.kt`

- [ ] **Step 1: Create the migration**

`backend/src/main/resources/db/migration/V6__create_refresh_tokens.sql`:

```sql
CREATE TABLE refresh_tokens (
    id          UUID         NOT NULL PRIMARY KEY,
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at  TIMESTAMP    NOT NULL,
    revoked     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
```

- [ ] **Step 2: Create entity and repository**

`backend/src/main/kotlin/com/taskowolf/auth/domain/RefreshToken.kt`:

```kotlin
package com.taskowolf.auth.domain

import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "refresh_tokens")
class RefreshToken(
    @Column(nullable = false, unique = true)
    val tokenHash: String,

    @Column(nullable = false)
    val userId: UUID,

    @Column(nullable = false)
    val expiresAt: Instant,

    @Column(nullable = false)
    var revoked: Boolean = false
) : AuditableEntity()
```

`backend/src/main/kotlin/com/taskowolf/auth/infrastructure/RefreshTokenRepository.kt`:

```kotlin
package com.taskowolf.auth.infrastructure

import com.taskowolf.auth.domain.RefreshToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface RefreshTokenRepository : JpaRepository<RefreshToken, UUID> {
    fun findByTokenHash(tokenHash: String): RefreshToken?

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.userId = :userId AND r.revoked = false")
    fun revokeAllByUserId(userId: UUID): Int
}
```

- [ ] **Step 3: Write the failing service test**

Create `backend/src/test/kotlin/com/taskowolf/auth/RefreshTokenServiceTest.kt`:

```kotlin
package com.taskowolf.auth

import com.taskowolf.auth.application.RefreshTokenService
import com.taskowolf.auth.domain.RefreshToken
import com.taskowolf.auth.infrastructure.RefreshTokenRepository
import com.taskowolf.core.infrastructure.ForbiddenException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

class RefreshTokenServiceTest {

    private val repository = mockk<RefreshTokenRepository>(relaxed = true)
    private val service = RefreshTokenService(repository, refreshExpiry = 604800)
    private val userId = UUID.randomUUID()

    @Test
    fun `consume revokes a valid token (rotation)`() {
        val stored = RefreshToken(
            tokenHash = service.hash("the-token"), userId = userId,
            expiresAt = Instant.now().plusSeconds(3600)
        )
        every { repository.findByTokenHash(service.hash("the-token")) } returns stored

        service.consume("the-token")

        assert(stored.revoked)
        verify { repository.save(stored) }
    }

    @Test
    fun `consume rejects an unknown token`() {
        every { repository.findByTokenHash(any()) } returns null
        assertThrows<ForbiddenException> { service.consume("unknown") }
    }

    @Test
    fun `consume rejects an already revoked token`() {
        val stored = RefreshToken(
            tokenHash = service.hash("re-used"), userId = userId,
            expiresAt = Instant.now().plusSeconds(3600), revoked = true
        )
        every { repository.findByTokenHash(service.hash("re-used")) } returns stored
        assertThrows<ForbiddenException> { service.consume("re-used") }
    }

    @Test
    fun `consume rejects an expired token`() {
        val stored = RefreshToken(
            tokenHash = service.hash("old"), userId = userId,
            expiresAt = Instant.now().minusSeconds(60)
        )
        every { repository.findByTokenHash(service.hash("old")) } returns stored
        assertThrows<ForbiddenException> { service.consume("old") }
    }
}
```

- [ ] **Step 4: Run the test to verify it fails**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.auth.RefreshTokenServiceTest"`
Expected: FAIL — `RefreshTokenService` does not exist (compilation error).

- [ ] **Step 5: Implement `RefreshTokenService`**

`backend/src/main/kotlin/com/taskowolf/auth/application/RefreshTokenService.kt`:

```kotlin
package com.taskowolf.auth.application

import com.taskowolf.auth.domain.RefreshToken
import com.taskowolf.auth.infrastructure.RefreshTokenRepository
import com.taskowolf.core.infrastructure.ForbiddenException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

@Service
class RefreshTokenService(
    private val repository: RefreshTokenRepository,
    @Value("\${taskowolf.jwt.refresh-token-expiry}") private val refreshExpiry: Long
) {
    fun store(token: String, userId: UUID) {
        repository.save(
            RefreshToken(
                tokenHash = hash(token),
                userId = userId,
                expiresAt = Instant.now().plusSeconds(refreshExpiry)
            )
        )
    }

    @Transactional
    fun consume(token: String) {
        val stored = repository.findByTokenHash(hash(token))
            ?: throw ForbiddenException("Invalid refresh token")
        if (stored.revoked || stored.expiresAt.isBefore(Instant.now())) {
            throw ForbiddenException("Invalid refresh token")
        }
        stored.revoked = true
        repository.save(stored)
    }

    @Transactional
    fun revokeAllForUser(userId: UUID) {
        repository.revokeAllByUserId(userId)
    }

    fun hash(token: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.auth.RefreshTokenServiceTest"`
Expected: PASS

- [ ] **Step 7: Wire it into `AuthService`**

Update `AuthService` — new constructor parameter and changed `refresh`/`tokenPair`, plus a `logout` function:

```kotlin
@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val refreshTokenService: RefreshTokenService
) {
    // register(...) and login(...) unchanged — they already end in tokenPair(user.id)

    @Transactional
    fun refresh(refreshToken: String): AuthResponse {
        val userId = jwtService.validateToken(refreshToken, "refresh")
            ?: throw ForbiddenException("Invalid refresh token")
        refreshTokenService.consume(refreshToken)
        return tokenPair(userId)
    }

    @Transactional
    fun logout(userId: UUID) {
        refreshTokenService.revokeAllForUser(userId)
    }

    private fun tokenPair(userId: UUID): AuthResponse {
        val refreshToken = jwtService.generateRefreshToken(userId)
        refreshTokenService.store(refreshToken, userId)
        return AuthResponse(
            accessToken = jwtService.generateAccessToken(userId),
            refreshToken = refreshToken
        )
    }
}
```

Note: `login` is currently `@Transactional(readOnly = true)` — change it to `@Transactional` because `tokenPair` now writes the refresh-token row.

Update `AuthServiceTest.kt`: add `private val refreshTokenService = mockk<RefreshTokenService>(relaxed = true)` and pass it as the fourth constructor argument.

- [ ] **Step 8: Add the logout endpoint**

In `AuthController.kt`:

```kotlin
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(@AuthenticationPrincipal user: User) = authService.logout(user.id)
```

Note: `/api/v1/auth/**` is `permitAll`, so an unauthenticated call has `user == null`; Spring will reject it with an error before reaching the service — acceptable, logout only makes sense with a valid access token.

- [ ] **Step 9: Add an integration test for rotation**

Add to `AuthControllerIntegrationTest.kt` (follow the existing MockMvc style in that file for register/login helpers):

```kotlin
    @Test
    fun `a refresh token can only be used once`() {
        val tokens = registerAndGetTokens("rotate@test.com")   // reuse/extract the file's existing register helper

        mockMvc.post("/api/v1/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"refreshToken": "${tokens.refreshToken}"}"""
        }.andExpect { status { isOk() } }

        mockMvc.post("/api/v1/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"refreshToken": "${tokens.refreshToken}"}"""
        }.andExpect { status { isForbidden() } }
    }
```

(Adapt helper names to what `AuthControllerIntegrationTest` actually provides; if it has none, register via `mockMvc.post("/api/v1/auth/register")` and parse the JSON response with the file's existing ObjectMapper.)

- [ ] **Step 10: Run the full backend suite**

Run: `cd backend && ./gradlew test`
Expected: PASS

- [ ] **Step 11: Call logout from the frontend**

In `frontend/src/layouts/AppLayout.tsx`, the logout handler currently only clears `localStorage`. Change it to:

```typescript
  const handleLogout = async () => {
    try {
      await api.post('/api/v1/auth/logout')
    } catch {
      // best effort — clear local state regardless
    }
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    navigate('/login')
  }
```

(`api` is the axios instance from `frontend/src/api/client.ts`; match the import and navigation style already used in the file.)

- [ ] **Step 12: Build the frontend**

Run: `cd frontend && npm run build`
Expected: build succeeds.

- [ ] **Step 13: Commit**

```bash
git add backend/src frontend/src
git commit -m "feat(security): server-side refresh token rotation, revocation and logout"
```

---

### Task 6: Validate that the assignee is a project member

`CreateIssueRequest.assigneeId` / `UpdateIssueRequest.assigneeId` accept any user ID in the system, allowing assignment of strangers to issues (and probing which user IDs exist).

**Files:**
- Modify: `backend/src/main/kotlin/com/taskowolf/projects/application/ProjectService.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/issues/application/IssueService.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/issues/IssueServiceTest.kt`

- [ ] **Step 1: Add a membership predicate to `ProjectService`**

```kotlin
    @Transactional(readOnly = true)
    fun isMember(project: Project, userId: UUID): Boolean =
        project.owner.id == userId || memberRepository.existsByProjectIdAndUserId(project.id, userId)
```

- [ ] **Step 2: Write the failing test**

Add to `IssueServiceTest.kt`:

```kotlin
    @Test
    fun `create rejects assignee who is not a project member`() {
        val workflowId = UUID.randomUUID()
        val stranger = User(email = "stranger@test.com", displayName = "Stranger")
        every { workflow.id } returns workflowId
        every { projectService.requireMember("WOLF", owner.id) } returns project
        every { workflowService.getDefaultStatus(workflowId) } returns status
        every { issueRepository.maxKeyNumberByProject(project.id) } returns 0
        every { issueRepository.save(any()) } returnsArgument 0
        every { userRepository.findById(stranger.id) } returns java.util.Optional.of(stranger)
        every { projectService.isMember(project, stranger.id) } returns false

        assertThrows<NotFoundException> {
            service.create("WOLF", CreateIssueRequest("Task", assigneeId = stranger.id), owner)
        }
    }
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.issues.IssueServiceTest"`
Expected: FAIL — no exception thrown (or MockK missing-stub error on `isMember` once the call exists; the assertion failure is the relevant signal before implementation).

- [ ] **Step 4: Enforce membership in `IssueService`**

Add a private helper and use it in both `create` and `update`:

```kotlin
    private fun resolveAssignee(assigneeId: UUID, project: com.taskowolf.projects.domain.Project): User {
        val assignee = userRepository.findById(assigneeId)
            .orElseThrow { NotFoundException("Assignee not found: $assigneeId") }
        if (!projectService.isMember(project, assignee.id)) {
            throw NotFoundException("Assignee not found: $assigneeId")
        }
        return assignee
    }
```

In `create`, replace the `assignee = ...` argument with:

```kotlin
                assignee = request.assigneeId?.let { resolveAssignee(it, project) },
```

In `update`, replace the `request.assigneeId?.let { ... }` block with:

```kotlin
        request.assigneeId?.let { issue.assignee = resolveAssignee(it, project) }
```

(The identical `NotFoundException` message for "user does not exist" and "user is not a member" is deliberate — it avoids confirming which user IDs exist.)

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.issues.*"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin backend/src/test/kotlin
git commit -m "fix(security): require issue assignees to be project members"
```

---

### Task 7: Configurable registration + first user becomes admin

Every self-hosted instance currently allows anyone with network access to register. Add `taskowolf.auth.registration-enabled` (default `true`, overridable via `TW_REGISTRATION_ENABLED`), and make the very first registered user the instance `ADMIN` so operators can bootstrap and then disable registration.

**Files:**
- Modify: `backend/src/main/kotlin/com/taskowolf/auth/application/AuthService.kt`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `docker-compose.yml`
- Test: `backend/src/test/kotlin/com/taskowolf/auth/AuthServiceTest.kt`

- [ ] **Step 1: Write the failing tests**

Add to `AuthServiceTest.kt` (the service is constructed inline here because the flag differs per test):

```kotlin
    @Test
    fun `register is rejected when registration is disabled`() {
        val service = AuthService(userRepository, passwordEncoder, jwtService, refreshTokenService, registrationEnabled = false)
        assertThrows<ForbiddenException> {
            service.register(RegisterRequest("new@test.com", "New User", "password123"))
        }
    }

    @Test
    fun `first registered user becomes admin`() {
        every { userRepository.existsByEmail("first@test.com") } returns false
        every { userRepository.count() } returns 0L
        every { userRepository.save(any()) } returnsArgument 0
        every { passwordEncoder.encode(any()) } returns "hash"
        every { jwtService.generateAccessToken(any()) } returns "a"
        every { jwtService.generateRefreshToken(any()) } returns "r"

        service.register(RegisterRequest("first@test.com", "First", "password123"))

        verify { userRepository.save(match { it.systemRole == SystemRole.ADMIN }) }
    }
```

(Adapt mock setup to the existing style in `AuthServiceTest.kt`; the default `service` in the file gets `registrationEnabled = true`.)

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.auth.AuthServiceTest"`
Expected: FAIL — `registrationEnabled` parameter does not exist (compilation error).

- [ ] **Step 3: Implement the flag and admin bootstrap**

In `AuthService`:

```kotlin
@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val refreshTokenService: RefreshTokenService,
    @Value("\${taskowolf.auth.registration-enabled}") private val registrationEnabled: Boolean
) {
    @Transactional
    fun register(request: RegisterRequest): AuthResponse {
        if (!registrationEnabled) {
            throw ForbiddenException("Registration is disabled on this instance")
        }
        if (userRepository.existsByEmail(request.email)) {
            throw ConflictException("Email already registered: ${request.email}")
        }
        val isFirstUser = userRepository.count() == 0L
        val user = userRepository.save(
            User(
                email = request.email,
                displayName = request.displayName,
                passwordHash = passwordEncoder.encode(request.password),
                systemRole = if (isFirstUser) SystemRole.ADMIN else SystemRole.MEMBER
            )
        )
        return tokenPair(user.id)
    }
    // rest unchanged
}
```

Add the import `org.springframework.beans.factory.annotation.Value` and `com.taskowolf.auth.domain.SystemRole`.

- [ ] **Step 4: Add the property and compose wiring**

`application.yml` under `taskowolf:`:

```yaml
  auth:
    registration-enabled: ${TW_REGISTRATION_ENABLED:true}
```

`docker-compose.yml`, in the `app` service environment:

```yaml
      TW_REGISTRATION_ENABLED: ${TW_REGISTRATION_ENABLED:-true}
```

- [ ] **Step 5: Run the full backend suite**

Run: `cd backend && ./gradlew test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src docker-compose.yml
git commit -m "feat(security): registration toggle and first-user admin bootstrap"
```

---

### Task 8: Dev-only H2/Swagger exposure and sanitized error responses

`/h2-console/**`, `/swagger-ui/**`, `/v3/api-docs/**` are `permitAll` and `frameOptions` is disabled in **all** profiles. The H2 console is an RCE vector wherever it is servlet-mapped, and Swagger discloses the full API surface of prod instances. Additionally `GlobalExceptionHandler` leaks raw DB constraint messages to clients.

**Files:**
- Modify: `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/SecurityConfig.kt`
- Modify: `backend/src/main/resources/application-prod.yml`
- Modify: `backend/src/main/kotlin/com/taskowolf/core/infrastructure/GlobalExceptionHandler.kt:40-43`

- [ ] **Step 1: Make dev-only endpoints conditional on the dev profile**

Replace `SecurityConfig.kt` with:

```kotlin
package com.taskowolf.auth.infrastructure

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
class SecurityConfig(
    private val jwtAuthFilter: JwtAuthFilter,
    private val environment: Environment
) {

    @Bean
    fun passwordEncoder() = BCryptPasswordEncoder()

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        val devMode = environment.acceptsProfiles(Profiles.of("dev"))
        val publicPaths = buildList {
            add("/api/v1/auth/**")
            add("/ws-stomp/**")
            if (devMode) {
                add("/swagger-ui/**")
                add("/v3/api-docs/**")
                add("/h2-console/**")
            }
        }
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers(*publicPaths.toTypedArray()).permitAll()
                it.anyRequest().authenticated()
            }
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
        if (devMode) {
            // H2 console renders itself in a frame
            http.headers { it.frameOptions { fo -> fo.disable() } }
        }
        return http.build()
    }
}
```

- [ ] **Step 2: Disable springdoc in prod**

Add to `application-prod.yml`:

```yaml
springdoc:
  api-docs:
    enabled: false
  swagger-ui:
    enabled: false
```

- [ ] **Step 3: Stop leaking DB error details**

In `GlobalExceptionHandler`, add a logger and replace the `DataIntegrityViolationException` handler:

```kotlin
import org.slf4j.LoggerFactory
```

```kotlin
    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(ex: DataIntegrityViolationException): ResponseEntity<ErrorResponse> {
        log.warn("Data integrity violation: {}", ex.mostSpecificCause.message)
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse("CONFLICT", "The request conflicts with existing data"))
    }
```

Also log unexpected errors in `handleGeneric` (currently swallowed silently):

```kotlin
    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception): ResponseEntity<ErrorResponse> {
        log.error("Unhandled exception", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"))
    }
```

- [ ] **Step 4: Run the full backend suite**

Run: `cd backend && ./gradlew test`
Expected: PASS. Note: integration tests run without the `dev` profile, so swagger/H2 paths are now authenticated there — no existing test should hit them; if one does, set `@ActiveProfiles("dev")` is **not** the fix — assert 401 instead.

- [ ] **Step 5: Commit**

```bash
git add backend/src
git commit -m "fix(security): restrict H2 console and Swagger to dev profile, sanitize DB error responses"
```

---

### Task 9: Rate-limit the auth endpoints

`/api/v1/auth/login` (and register/refresh) have no brute-force protection. Add a small in-memory fixed-window limiter — adequate for the single-node self-hosted default, no new dependencies.

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/AuthRateLimitFilter.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/auth/AuthRateLimitFilterTest.kt`

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/kotlin/com/taskowolf/auth/AuthRateLimitFilterTest.kt`:

```kotlin
package com.taskowolf.auth

import com.taskowolf.auth.infrastructure.AuthRateLimitFilter
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class AuthRateLimitFilterTest {

    private val filter = AuthRateLimitFilter()

    private fun request(ip: String) = MockHttpServletRequest("POST", "/api/v1/auth/login").apply {
        requestURI = "/api/v1/auth/login"
        remoteAddr = ip
    }

    @Test
    fun `requests over the limit get 429`() {
        var lastStatus = 200
        repeat(25) {
            val response = MockHttpServletResponse()
            filter.doFilter(request("10.0.0.1"), response, MockFilterChain())
            lastStatus = response.status
        }
        assert(lastStatus == 429)
    }

    @Test
    fun `limits are tracked per client`() {
        repeat(25) {
            filter.doFilter(request("10.0.0.2"), MockHttpServletResponse(), MockFilterChain())
        }
        val response = MockHttpServletResponse()
        filter.doFilter(request("10.0.0.3"), response, MockFilterChain())
        assert(response.status == 200)
    }

    @Test
    fun `non-auth endpoints are not limited`() {
        val req = MockHttpServletRequest("GET", "/api/v1/projects").apply {
            requestURI = "/api/v1/projects"
            remoteAddr = "10.0.0.4"
        }
        repeat(25) {
            val response = MockHttpServletResponse()
            filter.doFilter(req, response, MockFilterChain())
            assert(response.status == 200)
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.auth.AuthRateLimitFilterTest"`
Expected: FAIL — `AuthRateLimitFilter` does not exist (compilation error).

- [ ] **Step 3: Implement the filter**

Create `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/AuthRateLimitFilter.kt`:

```kotlin
package com.taskowolf.auth.infrastructure

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Fixed-window in-memory rate limiter for the unauthenticated auth endpoints.
 * Single-node only by design; behind nginx the client IP arrives via X-Real-IP
 * (the app port is not published in the prod compose file, so the header is trustworthy there).
 */
@Component
class AuthRateLimitFilter : OncePerRequestFilter() {

    private val windowMs = 60_000L
    private val maxRequestsPerWindow = 20
    private val maxTrackedClients = 10_000

    private class Window(val startedAt: Long) {
        val count = AtomicInteger(0)
    }

    private val windows = ConcurrentHashMap<String, Window>()

    override fun shouldNotFilter(request: HttpServletRequest) =
        !request.requestURI.startsWith("/api/v1/auth/")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        val now = System.currentTimeMillis()
        if (windows.size > maxTrackedClients) {
            windows.entries.removeIf { now - it.value.startedAt > windowMs }
        }
        val clientKey = request.getHeader("X-Real-IP") ?: request.remoteAddr
        val window = windows.compute(clientKey) { _, current ->
            if (current == null || now - current.startedAt > windowMs) Window(now) else current
        }!!
        if (window.count.incrementAndGet() > maxRequestsPerWindow) {
            response.status = 429
            response.contentType = "application/json"
            response.writer.write("""{"code":"RATE_LIMITED","message":"Too many requests, try again later"}""")
            return
        }
        chain.doFilter(request, response)
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.auth.AuthRateLimitFilterTest"`
Expected: PASS

- [ ] **Step 5: Check the integration tests still pass**

Run: `cd backend && ./gradlew test`
Expected: PASS. (`AuthControllerIntegrationTest` shares one MockMvc client; if it makes more than 20 auth calls per minute it will now hit 429 — in that case raise `maxRequestsPerWindow` to a value safely above the test count rather than disabling the filter in tests.)

- [ ] **Step 6: Commit**

```bash
git add backend/src
git commit -m "feat(security): rate-limit auth endpoints"
```

---

### Task 10: Infrastructure hardening (nginx headers, non-root containers, required secrets)

nginx serves without security headers or an upload size limit; the backend container runs as root; compose silently falls back to the DB password `changeme`.

**Files:**
- Modify: `docker/nginx.conf`
- Modify: `backend/Dockerfile`
- Modify: `docker-compose.yml`
- Create: `.env.example`

- [ ] **Step 1: Add security headers and a body limit to nginx**

Replace `docker/nginx.conf` with:

```nginx
upstream backend {
    server app:8080;
}

server {
    listen 80;

    client_max_body_size 20m;

    add_header X-Content-Type-Options nosniff always;
    add_header X-Frame-Options DENY always;
    add_header Referrer-Policy strict-origin-when-cross-origin always;
    add_header Content-Security-Policy "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; connect-src 'self' ws: wss:" always;

    location /api/ {
        proxy_pass http://backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    location /ws-stomp {
        proxy_pass http://backend;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header X-Real-IP $remote_addr;
    }

    location / {
        proxy_pass http://frontend:80;
    }
}
```

(`style-src 'unsafe-inline'` is required by Tailwind's injected styles; `location /ws` was renamed to `/ws-stomp` to match the only endpoint left after Task 4.)

- [ ] **Step 2: Run the backend container as non-root**

Replace `backend/Dockerfile` with:

```dockerfile
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S taskowolf && adduser -S taskowolf -G taskowolf
WORKDIR /app
COPY build/libs/*.jar app.jar
USER taskowolf
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Note: the `attachments` named volume is mounted at `/data/attachments`; named volumes are created root-owned. Add an explicit ownership fix to `docker-compose.yml`'s app service **or** simpler: since attachments are not implemented yet (Phase 3), leave the volume mount commented out until then:

```yaml
    # volumes:                       # re-enable in Phase 3 (attachments)
    #   - attachments:/data/attachments
```

- [ ] **Step 3: Make secrets mandatory in compose**

In `docker-compose.yml`, replace the fallback-bearing variables of the `app` and `db` services:

```yaml
      TW_DB_PASS: ${TW_DB_PASS:?TW_DB_PASS must be set - see .env.example}
      TW_JWT_SECRET: ${TW_JWT_SECRET:?TW_JWT_SECRET must be set - see .env.example}
```

```yaml
      POSTGRES_PASSWORD: ${TW_DB_PASS:?TW_DB_PASS must be set - see .env.example}
```

- [ ] **Step 4: Create `.env.example`**

```bash
# Copy to .env and fill in real values before running docker compose.

# Database
TW_DB_USER=taskowolf
TW_DB_PASS=                # required - pick a strong password
# TW_DB_URL=jdbc:postgresql://db:5432/taskowolf

# Auth
TW_JWT_SECRET=             # required - generate with: openssl rand -base64 48
TW_REGISTRATION_ENABLED=true   # set to false after creating the first (admin) account

# Public base URL of the instance (used for WebSocket origin checks)
TW_BASE_URL=http://localhost
# TW_ALLOWED_ORIGINS=https://tasks.example.com

# TLS: terminate HTTPS in front of nginx (reverse proxy/load balancer) or extend
# docker/nginx.conf with a 443 server block + certificates. Do not run plain HTTP
# on the public internet.
```

- [ ] **Step 5: Validate the compose files**

Run: `docker compose -f docker-compose.yml config` (without `TW_DB_PASS`/`TW_JWT_SECRET` set)
Expected: fails with `TW_DB_PASS must be set - see .env.example` — proving the fallback is gone.

Run: `TW_DB_PASS=x TW_JWT_SECRET=y docker compose -f docker-compose.yml config`
Expected: renders successfully.

- [ ] **Step 6: Commit**

```bash
git add docker/nginx.conf backend/Dockerfile docker-compose.yml .env.example
git commit -m "feat(security): nginx security headers, non-root backend container, mandatory secrets"
```

---

## Final verification

- [ ] Run the complete backend suite: `cd backend && ./gradlew test` — all green.
- [ ] Build the frontend: `cd frontend && npm run build` — succeeds.
- [ ] Boot the dev stack and smoke-test: register (first user is ADMIN), login, create project/issue, board live-update via WebSocket, logout, verify the old refresh token is rejected.
- [ ] Re-check the findings table at the top — every row maps to a completed task.
