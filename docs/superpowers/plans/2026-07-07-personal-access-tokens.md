# Personal Access Tokens + User-Lebenszyklus — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Nutzer erzeugen persönliche, user-gebundene Access Tokens (`twk_`, optional read-only) für externe Tools; deaktivierte/gelöschte Nutzer machen ihre Tokens sofort ungültig; Admins und Nutzer selbst können Konten deaktivieren/löschen (Soft-Delete).

**Architecture:** Neue Tabelle `access_tokens` + `AccessToken`-Entity/`TokenScope`-Enum/`AccessTokenService` im `auth`-Modul. `AccessTokenAuthFilter` (vor `JwtAuthFilter`) authentifiziert `Bearer twk_…` als den User mit dessen Systemrolle und erzwingt Read-Only über die HTTP-Methode. Neues `users.active`/`deleted_at`-Fundament; `UserAccountService` kapselt Deaktivierung/Aktivierung/Soft-Delete inkl. Token-Widerruf. Frontend: Account-Settings-Bereich (`/settings/tokens`, `/settings/account`) + Admin-Users-Seite.

**Tech Stack:** Kotlin 2.4, Spring Boot 3.5, Spring Data JPA, Flyway, PostgreSQL 16 / Testcontainers, React 19, TypeScript 6, TanStack Query

## Global Constraints

- Entity-ID: `UUID` via `AuditableEntity`-Basisklasse; Migration `UUID NOT NULL DEFAULT gen_random_uuid()`.
- Migration-Timestamps: `TIMESTAMPTZ NOT NULL`; `@CreatedDate`/`@LastModifiedDate` füllen `created_at`/`updated_at` automatisch.
- SHA-256-Muster (Hex): `MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }` — identisch zu `ApiKeyService`/`RefreshTokenService`.
- Klartext-Token wird **einmalig** bei Erstellung zurückgegeben, nie gespeichert, nie geloggt.
- Prefix `twk_`; kollidiert nicht mit dem projektbezogenen `tw_`.
- Read-Only-Durchsetzung: erlaubte Methoden = `GET`, `HEAD`, `OPTIONS`; sonst `403`.
- „Konto löschen" = **Soft-Delete + Anonymisierung** (kein DB-`DELETE`).
- Backend-Tests: Unit via `@ExtendWith(MockitoExtension::class)` + `org.mockito.kotlin.*`; Integration via `IntegrationTestBase` (Testcontainers-Postgres, `MockMvc`). Build: `./gradlew test` (aus `backend/`).
- Frontend hat **kein** Test-Framework → `npm run build` (`tsc && vite build`) + manuelle Prüfung. Pfad-Alias `@` → `frontend/src`. Kommandos aus `frontend/`.
- Erster jemals registrierter User wird `ADMIN` (`AuthService.register`); in Integrationstests Rollen deshalb explizit über `UserRepository` setzen, nicht auf Registrierungsreihenfolge verlassen.

---

### Task 1: User-Lebenszyklus-Fundament (`users.active`) + Aktiv-Prüfung in Auth

**Files:**
- Create: `backend/src/main/resources/db/migration/V27__users_active.sql`
- Modify: `backend/src/main/kotlin/com/taskowolf/auth/domain/User.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/JwtAuthFilter.kt:32-39`
- Modify: `backend/src/main/kotlin/com/taskowolf/auth/application/AuthService.kt:48-58`
- Test: `backend/src/test/kotlin/com/taskowolf/auth/UserActiveIntegrationTest.kt`

**Interfaces:**
- Produces: `User.active: Boolean` (default `true`), `User.deletedAt: Instant?`; Login und JWT-Auth lehnen inaktive Nutzer ab.

- [ ] **Step 1: Migration schreiben**

Create `backend/src/main/resources/db/migration/V27__users_active.sql`:
```sql
ALTER TABLE users ADD COLUMN active     BOOLEAN     NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMPTZ;
```

- [ ] **Step 2: Failing Integration-Test schreiben**

Create `backend/src/test/kotlin/com/taskowolf/auth/UserActiveIntegrationTest.kt`:
```kotlin
package com.taskowolf.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.IntegrationTestBase
import com.taskowolf.auth.infrastructure.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class UserActiveIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userRepository: UserRepository

    private fun register(email: String): String {
        val result = mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","displayName":"User","password":"password123"}""")
        ).andReturn()
        return objectMapper.readTree(result.response.contentAsString).get("accessToken").asText()
    }

    private fun deactivate(email: String) {
        val user = userRepository.findByEmail(email)!!
        user.active = false
        userRepository.save(user)
    }

    @Test
    fun `inactive user cannot login`() {
        register("inactive-login@test.com")
        deactivate("inactive-login@test.com")

        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"inactive-login@test.com","password":"password123"}""")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `inactive user JWT is rejected`() {
        val token = register("inactive-jwt@test.com")
        deactivate("inactive-jwt@test.com")

        mockMvc.perform(
            get("/api/v1/auth/me").header("Authorization", "Bearer $token")
        ).andExpect(status().isUnauthorized)
    }
}
```

- [ ] **Step 3: Test ausführen — muss fehlschlagen (kompiliert nicht)**

Run (in `backend/`): `./gradlew test --tests "com.taskowolf.auth.UserActiveIntegrationTest"`
Expected: Compile-Fehler — `user.active` existiert noch nicht.

- [ ] **Step 4: `User`-Entity erweitern**

In `backend/src/main/kotlin/com/taskowolf/auth/domain/User.kt` den Import ergänzen und zwei Felder hinzufügen. Datei vollständig ersetzen durch:
```kotlin
package com.taskowolf.auth.domain

import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "users")
class User(
    @Column(nullable = false, unique = true)
    var email: String,

    @Column(nullable = false)
    var displayName: String,

    var avatarUrl: String? = null,

    var passwordHash: String? = null,

    var oauthProvider: String? = null,

    var oauthSubject: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var systemRole: SystemRole = SystemRole.MEMBER,

    @Column
    var orgId: UUID? = null,

    @Column(nullable = false)
    var active: Boolean = true,

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null
) : AuditableEntity()
```

> Hinweis: `email` wird von `val` auf `var` geändert — wird in Task 4 für die Anonymisierung benötigt und ist hier schon mit erledigt.

- [ ] **Step 5: `JwtAuthFilter` auf Aktiv-Status prüfen**

In `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/JwtAuthFilter.kt` diese Zeile ersetzen:
```kotlin
                    if (user != null) {
```
durch:
```kotlin
                    if (user != null && user.active) {
```

- [ ] **Step 6: `AuthService.login` auf Aktiv-Status prüfen**

In `backend/src/main/kotlin/com/taskowolf/auth/application/AuthService.kt`, in `login`, direkt **nach** dem Credential-Check-Block (nach dem `throw ForbiddenException("Invalid credentials")`-`if`) einfügen:
```kotlin
        if (!user.active) {
            securityAuditListener.onLoginFailed(request.email, null)
            throw ForbiddenException("Account is disabled")
        }
```
(Danach folgt unverändert `val response = tokenPair(user.id)`.)

- [ ] **Step 7: Test ausführen — muss grün sein**

Run (in `backend/`): `./gradlew test --tests "com.taskowolf.auth.UserActiveIntegrationTest"`
Expected: beide Tests GRÜN.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/resources/db/migration/V27__users_active.sql \
        backend/src/main/kotlin/com/taskowolf/auth/domain/User.kt \
        backend/src/main/kotlin/com/taskowolf/auth/infrastructure/JwtAuthFilter.kt \
        backend/src/main/kotlin/com/taskowolf/auth/application/AuthService.kt \
        backend/src/test/kotlin/com/taskowolf/auth/UserActiveIntegrationTest.kt
git commit -m "feat(auth): add users.active foundation + reject inactive users on login/JWT"
```

---

### Task 2: `access_tokens`-Modell + `AccessTokenService` (Unit-TDD)

**Files:**
- Create: `backend/src/main/resources/db/migration/V26__access_tokens.sql`
- Create: `backend/src/main/kotlin/com/taskowolf/auth/domain/TokenScope.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/auth/domain/AccessToken.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/AccessTokenRepository.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/auth/application/AuthenticatedToken.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/auth/api/dto/CreateAccessTokenResponse.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/auth/api/dto/AccessTokenResponse.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/auth/application/AccessTokenService.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/auth/AccessTokenServiceTest.kt`

**Interfaces:**
- Consumes: `User.active` (Task 1)
- Produces:
  - `TokenScope { READ_ONLY, READ_WRITE }`
  - `AccessTokenService.create(user: User, name: String, scope: TokenScope, expiresAt: Instant?): CreateAccessTokenResponse`
  - `AccessTokenService.list(user: User): List<AccessTokenResponse>`
  - `AccessTokenService.revoke(user: User, tokenId: UUID)`
  - `AccessTokenService.revokeAllForUser(userId: UUID)`
  - `AccessTokenService.authenticate(rawToken: String): AuthenticatedToken?`
  - `AccessTokenService.sha256(input: String): String`
  - `AuthenticatedToken(user: User, scope: TokenScope)`

- [ ] **Step 1: Migration schreiben**

Create `backend/src/main/resources/db/migration/V26__access_tokens.sql`:
```sql
CREATE TABLE access_tokens (
    id            UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id       UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name          VARCHAR(255) NOT NULL,
    token_hash    VARCHAR(64)  NOT NULL UNIQUE,
    token_prefix  VARCHAR(16)  NOT NULL,
    scope         VARCHAR(16)  NOT NULL,
    last_used_at  TIMESTAMPTZ,
    expires_at    TIMESTAMPTZ,
    revoked_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_access_tokens_user ON access_tokens (user_id);
CREATE INDEX idx_access_tokens_hash ON access_tokens (token_hash);
```

- [ ] **Step 2: Enum `TokenScope` anlegen**

Create `backend/src/main/kotlin/com/taskowolf/auth/domain/TokenScope.kt`:
```kotlin
package com.taskowolf.auth.domain

enum class TokenScope { READ_ONLY, READ_WRITE }
```

- [ ] **Step 3: `AccessToken`-Entity anlegen**

Create `backend/src/main/kotlin/com/taskowolf/auth/domain/AccessToken.kt`:
```kotlin
package com.taskowolf.auth.domain

import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "access_tokens")
class AccessToken(
    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(nullable = false)
    val name: String,

    @Column(nullable = false, unique = true)
    val tokenHash: String,

    @Column(nullable = false)
    val tokenPrefix: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val scope: TokenScope,

    @Column
    var lastUsedAt: Instant? = null,

    @Column
    val expiresAt: Instant? = null,

    @Column
    var revokedAt: Instant? = null
) : AuditableEntity()
```

- [ ] **Step 4: Repository anlegen**

Create `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/AccessTokenRepository.kt`:
```kotlin
package com.taskowolf.auth.infrastructure

import com.taskowolf.auth.domain.AccessToken
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AccessTokenRepository : JpaRepository<AccessToken, UUID> {
    fun findByTokenHash(tokenHash: String): AccessToken?
    fun findByUserIdAndRevokedAtIsNull(userId: UUID): List<AccessToken>
    fun findByIdAndUserId(id: UUID, userId: UUID): AccessToken?
}
```

- [ ] **Step 5: `AuthenticatedToken` + DTOs anlegen**

Create `backend/src/main/kotlin/com/taskowolf/auth/application/AuthenticatedToken.kt`:
```kotlin
package com.taskowolf.auth.application

import com.taskowolf.auth.domain.TokenScope
import com.taskowolf.auth.domain.User

data class AuthenticatedToken(val user: User, val scope: TokenScope)
```

Create `backend/src/main/kotlin/com/taskowolf/auth/api/dto/CreateAccessTokenResponse.kt`:
```kotlin
package com.taskowolf.auth.api.dto

import com.taskowolf.auth.domain.TokenScope
import java.util.UUID

data class CreateAccessTokenResponse(
    val id: UUID,
    val name: String,
    val tokenPrefix: String,
    val scope: TokenScope,
    val plaintext: String
)
```

Create `backend/src/main/kotlin/com/taskowolf/auth/api/dto/AccessTokenResponse.kt`:
```kotlin
package com.taskowolf.auth.api.dto

import com.taskowolf.auth.domain.AccessToken
import com.taskowolf.auth.domain.TokenScope
import java.time.Instant
import java.util.UUID

data class AccessTokenResponse(
    val id: UUID,
    val name: String,
    val tokenPrefix: String,
    val scope: TokenScope,
    val lastUsedAt: Instant?,
    val expiresAt: Instant?,
    val createdAt: Instant?
) {
    companion object {
        fun from(t: AccessToken) = AccessTokenResponse(
            t.id, t.name, t.tokenPrefix, t.scope, t.lastUsedAt, t.expiresAt, t.createdAt
        )
    }
}
```

- [ ] **Step 6: Failing Unit-Test schreiben**

Create `backend/src/test/kotlin/com/taskowolf/auth/AccessTokenServiceTest.kt`:
```kotlin
package com.taskowolf.auth

import com.taskowolf.auth.application.AccessTokenService
import com.taskowolf.auth.domain.AccessToken
import com.taskowolf.auth.domain.SystemRole
import com.taskowolf.auth.domain.TokenScope
import com.taskowolf.auth.domain.User
import com.taskowolf.auth.infrastructure.AccessTokenRepository
import com.taskowolf.auth.infrastructure.UserRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.time.Instant
import java.util.*

@ExtendWith(MockitoExtension::class)
class AccessTokenServiceTest {

    @Mock private lateinit var accessTokenRepository: AccessTokenRepository
    @Mock private lateinit var userRepository: UserRepository
    @InjectMocks private lateinit var service: AccessTokenService

    private fun mockUser(active: Boolean = true) =
        User(email = "t@t.com", displayName = "T", systemRole = SystemRole.MEMBER, active = active)

    @Test
    fun `create returns plaintext with twk_ prefix and stores hash only`() {
        val user = mockUser()
        whenever(accessTokenRepository.save(any<AccessToken>())).thenAnswer { it.arguments[0] as AccessToken }

        val res = service.create(user, "CI", TokenScope.READ_ONLY, null)

        assertTrue(res.plaintext.startsWith("twk_"), "plaintext must start with twk_")
        assertTrue(res.tokenPrefix.startsWith("twk_"), "prefix must start with twk_")
        assertEquals(TokenScope.READ_ONLY, res.scope)
        verify(accessTokenRepository).save(argThat { tokenHash == service.sha256(res.plaintext) })
    }

    @Test
    fun `authenticate returns user and scope for valid token`() {
        val user = mockUser()
        val raw = "twk_validtoken1234567890"
        val stored = AccessToken(
            userId = user.id, name = "t", tokenHash = service.sha256(raw),
            tokenPrefix = "twk_validto", scope = TokenScope.READ_WRITE
        )
        whenever(accessTokenRepository.findByTokenHash(service.sha256(raw))).thenReturn(stored)
        whenever(userRepository.findById(user.id)).thenReturn(Optional.of(user))
        whenever(accessTokenRepository.save(any<AccessToken>())).thenReturn(stored)

        val result = service.authenticate(raw)

        assertEquals(user.id, result?.user?.id)
        assertEquals(TokenScope.READ_WRITE, result?.scope)
    }

    @Test
    fun `authenticate returns null for non-twk token`() {
        assertNull(service.authenticate("tw_projectkey123456"))
        assertNull(service.authenticate("eyJhbGciOiJIUzI1NiJ9.some.jwt"))
    }

    @Test
    fun `authenticate returns null for revoked token`() {
        val raw = "twk_revoked1234567890"
        val stored = AccessToken(
            userId = UUID.randomUUID(), name = "t", tokenHash = service.sha256(raw),
            tokenPrefix = "twk_revoked", scope = TokenScope.READ_WRITE, revokedAt = Instant.now()
        )
        whenever(accessTokenRepository.findByTokenHash(service.sha256(raw))).thenReturn(stored)

        assertNull(service.authenticate(raw))
    }

    @Test
    fun `authenticate returns null for expired token`() {
        val raw = "twk_expired1234567890"
        val stored = AccessToken(
            userId = UUID.randomUUID(), name = "t", tokenHash = service.sha256(raw),
            tokenPrefix = "twk_expired", scope = TokenScope.READ_WRITE,
            expiresAt = Instant.now().minusSeconds(60)
        )
        whenever(accessTokenRepository.findByTokenHash(service.sha256(raw))).thenReturn(stored)

        assertNull(service.authenticate(raw))
    }

    @Test
    fun `authenticate returns null for inactive user`() {
        val user = mockUser(active = false)
        val raw = "twk_inactiveuser1234"
        val stored = AccessToken(
            userId = user.id, name = "t", tokenHash = service.sha256(raw),
            tokenPrefix = "twk_inactiv", scope = TokenScope.READ_WRITE
        )
        whenever(accessTokenRepository.findByTokenHash(service.sha256(raw))).thenReturn(stored)
        whenever(userRepository.findById(user.id)).thenReturn(Optional.of(user))

        assertNull(service.authenticate(raw))
    }
}
```

- [ ] **Step 7: Test ausführen — muss fehlschlagen (kompiliert nicht)**

Run (in `backend/`): `./gradlew test --tests "com.taskowolf.auth.AccessTokenServiceTest"`
Expected: Compile-Fehler — `AccessTokenService` existiert noch nicht.

- [ ] **Step 8: `AccessTokenService` implementieren**

Create `backend/src/main/kotlin/com/taskowolf/auth/application/AccessTokenService.kt`:
```kotlin
package com.taskowolf.auth.application

import com.taskowolf.auth.api.dto.AccessTokenResponse
import com.taskowolf.auth.api.dto.CreateAccessTokenResponse
import com.taskowolf.auth.domain.AccessToken
import com.taskowolf.auth.domain.TokenScope
import com.taskowolf.auth.domain.User
import com.taskowolf.auth.infrastructure.AccessTokenRepository
import com.taskowolf.auth.infrastructure.UserRepository
import com.taskowolf.core.infrastructure.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Service
class AccessTokenService(
    private val accessTokenRepository: AccessTokenRepository,
    private val userRepository: UserRepository
) {
    @Transactional
    fun create(user: User, name: String, scope: TokenScope, expiresAt: Instant?): CreateAccessTokenResponse {
        val plaintext = "twk_" + secureToken()
        val token = accessTokenRepository.save(
            AccessToken(
                userId = user.id,
                name = name,
                tokenHash = sha256(plaintext),
                tokenPrefix = plaintext.take(12),
                scope = scope,
                expiresAt = expiresAt
            )
        )
        return CreateAccessTokenResponse(token.id, token.name, token.tokenPrefix, token.scope, plaintext)
    }

    @Transactional(readOnly = true)
    fun list(user: User): List<AccessTokenResponse> =
        accessTokenRepository.findByUserIdAndRevokedAtIsNull(user.id).map { AccessTokenResponse.from(it) }

    @Transactional
    fun revoke(user: User, tokenId: UUID) {
        val token = accessTokenRepository.findByIdAndUserId(tokenId, user.id)
            ?: throw NotFoundException("Access token not found: $tokenId")
        if (token.revokedAt == null) {
            token.revokedAt = Instant.now()
            accessTokenRepository.save(token)
        }
    }

    @Transactional
    fun revokeAllForUser(userId: UUID) {
        val now = Instant.now()
        accessTokenRepository.findByUserIdAndRevokedAtIsNull(userId).forEach {
            it.revokedAt = now
            accessTokenRepository.save(it)
        }
    }

    @Transactional
    fun authenticate(rawToken: String): AuthenticatedToken? {
        if (!rawToken.startsWith("twk_")) return null
        val token = accessTokenRepository.findByTokenHash(sha256(rawToken)) ?: return null
        if (token.revokedAt != null) return null
        val expiresAt = token.expiresAt
        if (expiresAt != null && expiresAt.isBefore(Instant.now())) return null
        val user = userRepository.findById(token.userId).orElse(null) ?: return null
        if (!user.active) return null
        token.lastUsedAt = Instant.now()
        accessTokenRepository.save(token)
        return AuthenticatedToken(user, token.scope)
    }

    fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun secureToken(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
```

- [ ] **Step 9: Test ausführen — muss grün sein**

Run (in `backend/`): `./gradlew test --tests "com.taskowolf.auth.AccessTokenServiceTest"`
Expected: alle 6 Tests GRÜN.

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/resources/db/migration/V26__access_tokens.sql \
        backend/src/main/kotlin/com/taskowolf/auth/domain/TokenScope.kt \
        backend/src/main/kotlin/com/taskowolf/auth/domain/AccessToken.kt \
        backend/src/main/kotlin/com/taskowolf/auth/infrastructure/AccessTokenRepository.kt \
        backend/src/main/kotlin/com/taskowolf/auth/application/AuthenticatedToken.kt \
        backend/src/main/kotlin/com/taskowolf/auth/api/dto/CreateAccessTokenResponse.kt \
        backend/src/main/kotlin/com/taskowolf/auth/api/dto/AccessTokenResponse.kt \
        backend/src/main/kotlin/com/taskowolf/auth/application/AccessTokenService.kt \
        backend/src/test/kotlin/com/taskowolf/auth/AccessTokenServiceTest.kt
git commit -m "feat(auth): add access_tokens model + AccessTokenService (personal tokens)"
```

---

### Task 3: `AccessTokenAuthFilter` + Controller + Read-Only-Durchsetzung

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/auth/api/dto/CreateAccessTokenRequest.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/AccessTokenAuthFilter.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/SecurityConfig.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/auth/api/AccessTokenController.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/auth/AccessTokenControllerTest.kt`

**Interfaces:**
- Consumes: `AccessTokenService.create/list/revoke/authenticate` (Task 2), `AuthenticatedToken`, `TokenScope`
- Produces: REST `GET/POST/DELETE /api/v1/me/tokens`; `Bearer twk_…` authentifiziert Requests; Read-Only-Token → `403` bei nicht-sicheren Methoden.

- [ ] **Step 1: Request-DTO anlegen**

Create `backend/src/main/kotlin/com/taskowolf/auth/api/dto/CreateAccessTokenRequest.kt`:
```kotlin
package com.taskowolf.auth.api.dto

import com.taskowolf.auth.domain.TokenScope
import jakarta.validation.constraints.NotBlank
import java.time.Instant

data class CreateAccessTokenRequest(
    @field:NotBlank val name: String,
    val scope: TokenScope = TokenScope.READ_WRITE,
    val expiresAt: Instant? = null
)
```

- [ ] **Step 2: Failing Integration-Test schreiben**

Create `backend/src/test/kotlin/com/taskowolf/auth/AccessTokenControllerTest.kt`:
```kotlin
package com.taskowolf.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.IntegrationTestBase
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class AccessTokenControllerTest : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper

    private fun registerAndLogin(email: String): String {
        val result = mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","displayName":"User","password":"password123"}""")
        ).andReturn()
        return objectMapper.readTree(result.response.contentAsString).get("accessToken").asText()
    }

    private fun createToken(jwt: String, name: String, scope: String): String {
        val result = mockMvc.perform(
            post("/api/v1/me/tokens")
                .header("Authorization", "Bearer $jwt")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"$name","scope":"$scope"}""")
        ).andExpect(status().isCreated).andReturn()
        return objectMapper.readTree(result.response.contentAsString).get("plaintext").asText()
    }

    @Test
    fun `POST creates token with twk_ prefix returned once`() {
        val jwt = registerAndLogin("pat-create@test.com")
        mockMvc.perform(
            post("/api/v1/me/tokens")
                .header("Authorization", "Bearer $jwt")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"CLI","scope":"READ_WRITE"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.plaintext").value(startsWith("twk_")))
            .andExpect(jsonPath("$.tokenPrefix").value(startsWith("twk_")))
            .andExpect(jsonPath("$.scope").value("READ_WRITE"))
    }

    @Test
    fun `GET lists tokens without plaintext`() {
        val jwt = registerAndLogin("pat-list@test.com")
        createToken(jwt, "Key A", "READ_WRITE")
        mockMvc.perform(get("/api/v1/me/tokens").header("Authorization", "Bearer $jwt"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name").value("Key A"))
            .andExpect(jsonPath("$[0].tokenPrefix").value(startsWith("twk_")))
            .andExpect(jsonPath("$[0].plaintext").doesNotExist())
    }

    @Test
    fun `DELETE revokes token`() {
        val jwt = registerAndLogin("pat-revoke@test.com")
        val result = mockMvc.perform(
            post("/api/v1/me/tokens")
                .header("Authorization", "Bearer $jwt")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Del","scope":"READ_WRITE"}""")
        ).andReturn()
        val id = objectMapper.readTree(result.response.contentAsString).get("id").asText()

        mockMvc.perform(delete("/api/v1/me/tokens/$id").header("Authorization", "Bearer $jwt"))
            .andExpect(status().isNoContent)
        mockMvc.perform(get("/api/v1/me/tokens").header("Authorization", "Bearer $jwt"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `read-write token authenticates GET and POST`() {
        val jwt = registerAndLogin("pat-rw@test.com")
        val plaintext = createToken(jwt, "RW", "READ_WRITE")

        mockMvc.perform(get("/api/v1/me/tokens").header("Authorization", "Bearer $plaintext"))
            .andExpect(status().isOk)
        mockMvc.perform(
            post("/api/v1/me/tokens")
                .header("Authorization", "Bearer $plaintext")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"second","scope":"READ_WRITE"}""")
        ).andExpect(status().isCreated)
    }

    @Test
    fun `read-only token allows GET but forbids POST`() {
        val jwt = registerAndLogin("pat-ro@test.com")
        val plaintext = createToken(jwt, "RO", "READ_ONLY")

        mockMvc.perform(get("/api/v1/me/tokens").header("Authorization", "Bearer $plaintext"))
            .andExpect(status().isOk)
        mockMvc.perform(
            post("/api/v1/me/tokens")
                .header("Authorization", "Bearer $plaintext")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"blocked","scope":"READ_ONLY"}""")
        ).andExpect(status().isForbidden)
    }
}
```

- [ ] **Step 3: Test ausführen — muss fehlschlagen**

Run (in `backend/`): `./gradlew test --tests "com.taskowolf.auth.AccessTokenControllerTest"`
Expected: 404/401 auf allen `/me/tokens`-Endpunkten — Controller/Filter fehlen.

- [ ] **Step 4: `AccessTokenAuthFilter` anlegen**

Create `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/AccessTokenAuthFilter.kt`:
```kotlin
package com.taskowolf.auth.infrastructure

import com.taskowolf.auth.application.AccessTokenService
import com.taskowolf.auth.domain.TokenScope
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class AccessTokenAuthFilter(
    private val accessTokenService: AccessTokenService
) : OncePerRequestFilter() {

    private val safeMethods = setOf("GET", "HEAD", "OPTIONS")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        val raw = request.getHeader("Authorization")
            ?.takeIf { it.startsWith("Bearer twk_") }
            ?.substring(7)

        if (raw != null && SecurityContextHolder.getContext().authentication == null) {
            val authenticated = accessTokenService.authenticate(raw)
            if (authenticated != null) {
                if (authenticated.scope == TokenScope.READ_ONLY && request.method !in safeMethods) {
                    response.sendError(
                        HttpServletResponse.SC_FORBIDDEN,
                        "Read-only token cannot perform ${request.method}"
                    )
                    return
                }
                SecurityContextHolder.getContext().authentication =
                    UsernamePasswordAuthenticationToken(
                        authenticated.user, null,
                        listOf(SimpleGrantedAuthority("ROLE_${authenticated.user.systemRole.name}"))
                    )
            }
        }
        chain.doFilter(request, response)
    }
}
```

- [ ] **Step 5: `SecurityConfig` — Filter registrieren**

In `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/SecurityConfig.kt`:

(a) Konstruktor-Parameter ergänzen — diese Zeile:
```kotlin
    private val apiKeyAuthFilter: ApiKeyAuthFilter,
```
ersetzen durch:
```kotlin
    private val apiKeyAuthFilter: ApiKeyAuthFilter,
    private val accessTokenAuthFilter: AccessTokenAuthFilter,
```

(b) Filter in die Chain einhängen — diese Zeile:
```kotlin
            .addFilterBefore(apiKeyAuthFilter, JwtAuthFilter::class.java)
```
ersetzen durch:
```kotlin
            .addFilterBefore(apiKeyAuthFilter, JwtAuthFilter::class.java)
            .addFilterBefore(accessTokenAuthFilter, JwtAuthFilter::class.java)
```

- [ ] **Step 6: `AccessTokenController` anlegen**

Create `backend/src/main/kotlin/com/taskowolf/auth/api/AccessTokenController.kt`:
```kotlin
package com.taskowolf.auth.api

import com.taskowolf.auth.api.dto.CreateAccessTokenRequest
import com.taskowolf.auth.application.AccessTokenService
import com.taskowolf.auth.domain.User
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/me/tokens")
class AccessTokenController(private val accessTokenService: AccessTokenService) {

    @GetMapping
    fun list(@AuthenticationPrincipal user: User) = accessTokenService.list(user)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: CreateAccessTokenRequest,
        @AuthenticationPrincipal user: User
    ) = accessTokenService.create(user, request.name, request.scope, request.expiresAt)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revoke(@PathVariable id: UUID, @AuthenticationPrincipal user: User) =
        accessTokenService.revoke(user, id)
}
```

- [ ] **Step 7: Tests ausführen — müssen grün sein**

Run (in `backend/`): `./gradlew test --tests "com.taskowolf.auth.AccessTokenControllerTest"`
Expected: alle 5 Tests GRÜN.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/auth/api/dto/CreateAccessTokenRequest.kt \
        backend/src/main/kotlin/com/taskowolf/auth/infrastructure/AccessTokenAuthFilter.kt \
        backend/src/main/kotlin/com/taskowolf/auth/infrastructure/SecurityConfig.kt \
        backend/src/main/kotlin/com/taskowolf/auth/api/AccessTokenController.kt \
        backend/src/test/kotlin/com/taskowolf/auth/AccessTokenControllerTest.kt
git commit -m "feat(auth): AccessTokenAuthFilter + /me/tokens controller + read-only enforcement"
```

---

### Task 4: User-Lebenszyklus — Deaktivieren/Aktivieren/Löschen (self + admin)

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/auth/api/dto/AdminUserResponse.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/UserRepository.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/auth/application/UserAccountService.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/auth/api/MeController.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/auth/api/AdminUserController.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/auth/UserAccountServiceTest.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/auth/AdminUserControllerTest.kt`

**Interfaces:**
- Consumes: `AccessTokenService.revokeAllForUser` (Task 2), `RefreshTokenService.revokeAllForUser`, `User.active/deletedAt/email` (Task 1)
- Produces:
  - `UserAccountService.deactivate(userId: UUID)`, `.activate(userId: UUID)`, `.softDelete(userId: UUID)`, `.list(): List<AdminUserResponse>`
  - `UserRepository.countBySystemRoleAndActiveTrue(role: SystemRole): Long`
  - REST `DELETE /api/v1/me`; `GET /api/v1/admin/users`, `POST /api/v1/admin/users/{id}/deactivate`, `POST .../activate`, `DELETE /api/v1/admin/users/{id}`

- [ ] **Step 1: `AdminUserResponse`-DTO anlegen**

Create `backend/src/main/kotlin/com/taskowolf/auth/api/dto/AdminUserResponse.kt`:
```kotlin
package com.taskowolf.auth.api.dto

import com.taskowolf.auth.domain.User
import java.util.UUID

data class AdminUserResponse(
    val id: UUID,
    val email: String,
    val displayName: String,
    val systemRole: String,
    val active: Boolean
) {
    companion object {
        fun from(u: User) = AdminUserResponse(u.id, u.email, u.displayName, u.systemRole.name, u.active)
    }
}
```

- [ ] **Step 2: Repository um Admin-Zählung erweitern**

In `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/UserRepository.kt` den Import + die Methode ergänzen. Datei vollständig ersetzen durch:
```kotlin
package com.taskowolf.auth.infrastructure

import com.taskowolf.auth.domain.SystemRole
import com.taskowolf.auth.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun findByEmail(email: String): User?
    fun existsByEmail(email: String): Boolean
    fun findByDisplayNameIgnoreCase(displayName: String): User?
    fun countBySystemRoleAndActiveTrue(systemRole: SystemRole): Long
}
```

- [ ] **Step 3: Failing Unit-Test für `UserAccountService` schreiben**

Create `backend/src/test/kotlin/com/taskowolf/auth/UserAccountServiceTest.kt`:
```kotlin
package com.taskowolf.auth

import com.taskowolf.auth.application.AccessTokenService
import com.taskowolf.auth.application.RefreshTokenService
import com.taskowolf.auth.application.UserAccountService
import com.taskowolf.auth.domain.SystemRole
import com.taskowolf.auth.domain.User
import com.taskowolf.auth.infrastructure.UserRepository
import com.taskowolf.core.infrastructure.ConflictException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.util.*

@ExtendWith(MockitoExtension::class)
class UserAccountServiceTest {

    @Mock private lateinit var userRepository: UserRepository
    @Mock private lateinit var accessTokenService: AccessTokenService
    @Mock private lateinit var refreshTokenService: RefreshTokenService
    @InjectMocks private lateinit var service: UserAccountService

    @Test
    fun `softDelete anonymizes user and revokes tokens`() {
        val user = User(email = "real@x.com", displayName = "Real", systemRole = SystemRole.MEMBER)
        whenever(userRepository.findById(user.id)).thenReturn(Optional.of(user))

        service.softDelete(user.id)

        assertFalse(user.active)
        assertNotNull(user.deletedAt)
        assertEquals("Deleted User", user.displayName)
        assertTrue(user.email.startsWith("deleted-"))
        assertNull(user.passwordHash)
        verify(accessTokenService).revokeAllForUser(user.id)
        verify(refreshTokenService).revokeAllForUser(user.id)
    }

    @Test
    fun `deactivate blocks the last active admin`() {
        val admin = User(email = "a@x.com", displayName = "A", systemRole = SystemRole.ADMIN)
        whenever(userRepository.findById(admin.id)).thenReturn(Optional.of(admin))
        whenever(userRepository.countBySystemRoleAndActiveTrue(SystemRole.ADMIN)).thenReturn(1L)

        assertThrows(ConflictException::class.java) { service.deactivate(admin.id) }
        assertTrue(admin.active)
    }

    @Test
    fun `deactivate allowed when other admins exist`() {
        val admin = User(email = "a2@x.com", displayName = "A2", systemRole = SystemRole.ADMIN)
        whenever(userRepository.findById(admin.id)).thenReturn(Optional.of(admin))
        whenever(userRepository.countBySystemRoleAndActiveTrue(SystemRole.ADMIN)).thenReturn(2L)

        service.deactivate(admin.id)

        assertFalse(admin.active)
        verify(accessTokenService).revokeAllForUser(admin.id)
        verify(refreshTokenService).revokeAllForUser(admin.id)
    }
}
```

- [ ] **Step 4: Test ausführen — muss fehlschlagen**

Run (in `backend/`): `./gradlew test --tests "com.taskowolf.auth.UserAccountServiceTest"`
Expected: Compile-Fehler — `UserAccountService` existiert noch nicht.

- [ ] **Step 5: `UserAccountService` implementieren**

Create `backend/src/main/kotlin/com/taskowolf/auth/application/UserAccountService.kt`:
```kotlin
package com.taskowolf.auth.application

import com.taskowolf.auth.api.dto.AdminUserResponse
import com.taskowolf.auth.domain.SystemRole
import com.taskowolf.auth.domain.User
import com.taskowolf.auth.infrastructure.UserRepository
import com.taskowolf.core.infrastructure.ConflictException
import com.taskowolf.core.infrastructure.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class UserAccountService(
    private val userRepository: UserRepository,
    private val accessTokenService: AccessTokenService,
    private val refreshTokenService: RefreshTokenService
) {
    @Transactional(readOnly = true)
    fun list(): List<AdminUserResponse> = userRepository.findAll().map { AdminUserResponse.from(it) }

    @Transactional
    fun deactivate(userId: UUID) {
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User not found") }
        requireNotLastActiveAdmin(user)
        user.active = false
        userRepository.save(user)
        accessTokenService.revokeAllForUser(userId)
        refreshTokenService.revokeAllForUser(userId)
    }

    @Transactional
    fun activate(userId: UUID) {
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User not found") }
        user.active = true
        userRepository.save(user)
    }

    @Transactional
    fun softDelete(userId: UUID) {
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User not found") }
        requireNotLastActiveAdmin(user)
        user.active = false
        user.deletedAt = Instant.now()
        user.email = "deleted-${user.id}@deleted.invalid"
        user.displayName = "Deleted User"
        user.passwordHash = null
        user.oauthProvider = null
        user.oauthSubject = null
        user.avatarUrl = null
        userRepository.save(user)
        accessTokenService.revokeAllForUser(userId)
        refreshTokenService.revokeAllForUser(userId)
    }

    private fun requireNotLastActiveAdmin(user: User) {
        if (user.systemRole == SystemRole.ADMIN && user.active) {
            if (userRepository.countBySystemRoleAndActiveTrue(SystemRole.ADMIN) <= 1) {
                throw ConflictException("Cannot deactivate or delete the last active admin")
            }
        }
    }
}
```

- [ ] **Step 6: Unit-Test ausführen — muss grün sein**

Run (in `backend/`): `./gradlew test --tests "com.taskowolf.auth.UserAccountServiceTest"`
Expected: alle 3 Tests GRÜN.

- [ ] **Step 7: Controller anlegen (`MeController` + `AdminUserController`)**

Create `backend/src/main/kotlin/com/taskowolf/auth/api/MeController.kt`:
```kotlin
package com.taskowolf.auth.api

import com.taskowolf.auth.application.UserAccountService
import com.taskowolf.auth.domain.User
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/me")
class MeController(private val userAccountService: UserAccountService) {

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteAccount(@AuthenticationPrincipal user: User) = userAccountService.softDelete(user.id)
}
```

Create `backend/src/main/kotlin/com/taskowolf/auth/api/AdminUserController.kt`:
```kotlin
package com.taskowolf.auth.api

import com.taskowolf.auth.application.UserAccountService
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
class AdminUserController(private val userAccountService: UserAccountService) {

    @GetMapping
    fun list() = userAccountService.list()

    @PostMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deactivate(@PathVariable id: UUID) = userAccountService.deactivate(id)

    @PostMapping("/{id}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun activate(@PathVariable id: UUID) = userAccountService.activate(id)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID) = userAccountService.softDelete(id)
}
```

- [ ] **Step 8: Failing Integration-Test schreiben**

Create `backend/src/test/kotlin/com/taskowolf/auth/AdminUserControllerTest.kt`:
```kotlin
package com.taskowolf.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.IntegrationTestBase
import com.taskowolf.auth.domain.SystemRole
import com.taskowolf.auth.infrastructure.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.util.UUID

class AdminUserControllerTest : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userRepository: UserRepository

    private fun register(email: String): String {
        val result = mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","displayName":"User","password":"password123"}""")
        ).andReturn()
        return objectMapper.readTree(result.response.contentAsString).get("accessToken").asText()
    }

    private fun setRole(email: String, role: SystemRole): UUID {
        val user = userRepository.findByEmail(email)!!
        user.systemRole = role
        userRepository.save(user)
        return user.id
    }

    private fun createToken(jwt: String): String {
        val result = mockMvc.perform(
            post("/api/v1/me/tokens")
                .header("Authorization", "Bearer $jwt")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"t","scope":"READ_WRITE"}""")
        ).andReturn()
        return objectMapper.readTree(result.response.contentAsString).get("plaintext").asText()
    }

    @Test
    fun `admin deactivates user and their token stops working`() {
        val adminJwt = register("admin-deact@test.com")
        setRole("admin-deact@test.com", SystemRole.ADMIN)
        val memberJwt = register("member-deact@test.com")
        val memberId = setRole("member-deact@test.com", SystemRole.MEMBER)
        val memberToken = createToken(memberJwt)

        // token works before deactivation
        mockMvc.perform(get("/api/v1/me/tokens").header("Authorization", "Bearer $memberToken"))
            .andExpect(status().isOk)

        mockMvc.perform(post("/api/v1/admin/users/$memberId/deactivate").header("Authorization", "Bearer $adminJwt"))
            .andExpect(status().isNoContent)

        // token dead after deactivation
        mockMvc.perform(get("/api/v1/me/tokens").header("Authorization", "Bearer $memberToken"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `non-admin is forbidden from admin users endpoint`() {
        val memberJwt = register("member-forbidden@test.com")
        setRole("member-forbidden@test.com", SystemRole.MEMBER)
        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer $memberJwt"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `self delete anonymizes account and blocks login`() {
        val jwt = register("self-delete@test.com")
        setRole("self-delete@test.com", SystemRole.MEMBER)

        mockMvc.perform(delete("/api/v1/me").header("Authorization", "Bearer $jwt"))
            .andExpect(status().isNoContent)

        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"self-delete@test.com","password":"password123"}""")
        ).andExpect(status().isForbidden)
    }
}
```

> Hinweis: Rollen werden bewusst über `setRole(...)` gesetzt, weil nur der allererste je registrierte User automatisch `ADMIN` wird und der Testcontainer über Tests hinweg geteilt ist. In jedem Test existiert mindestens ein aktiver Admin, daher greift der Letzter-Admin-Guard hier nicht (dieser ist im Unit-Test abgedeckt).

- [ ] **Step 9: Tests ausführen — müssen grün sein**

Run (in `backend/`): `./gradlew test --tests "com.taskowolf.auth.AdminUserControllerTest"`
Expected: alle 3 Tests GRÜN.

- [ ] **Step 10: Volle Auth-Testsuite grün halten**

Run (in `backend/`): `./gradlew test --tests "com.taskowolf.auth.*"`
Expected: alle Auth-Tests (inkl. bestehender) GRÜN.

- [ ] **Step 11: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/auth/api/dto/AdminUserResponse.kt \
        backend/src/main/kotlin/com/taskowolf/auth/infrastructure/UserRepository.kt \
        backend/src/main/kotlin/com/taskowolf/auth/application/UserAccountService.kt \
        backend/src/main/kotlin/com/taskowolf/auth/api/MeController.kt \
        backend/src/main/kotlin/com/taskowolf/auth/api/AdminUserController.kt \
        backend/src/test/kotlin/com/taskowolf/auth/UserAccountServiceTest.kt \
        backend/src/test/kotlin/com/taskowolf/auth/AdminUserControllerTest.kt
git commit -m "feat(auth): user lifecycle (deactivate/activate/soft-delete) self + admin"
```

---

### Task 5: Frontend — Access-Tokens-Seite

**Files:**
- Create: `frontend/src/hooks/useAccessTokens.ts`
- Create: `frontend/src/pages/settings/AccessTokensPage.tsx`
- Modify: `frontend/src/app/router.tsx`
- Modify: `frontend/src/layouts/AppLayout.tsx`

**Interfaces:**
- Consumes: REST `GET/POST/DELETE /api/v1/me/tokens`
- Produces: Route `/settings/tokens`, Sidebar-Link „Access Tokens"

- [ ] **Step 1: Hook anlegen**

Create `frontend/src/hooks/useAccessTokens.ts`:
```typescript
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/api/client'

export type TokenScope = 'READ_ONLY' | 'READ_WRITE'

export interface AccessTokenItem {
  id: string
  name: string
  tokenPrefix: string
  scope: TokenScope
  lastUsedAt: string | null
  expiresAt: string | null
  createdAt: string | null
}

export interface CreateAccessTokenResponse {
  id: string
  name: string
  tokenPrefix: string
  scope: TokenScope
  plaintext: string
}

export interface CreateAccessTokenBody {
  name: string
  scope: TokenScope
  expiresAt?: string | null
}

export function useAccessTokens() {
  return useQuery<AccessTokenItem[]>({
    queryKey: ['access-tokens'],
    queryFn: () => apiClient.get('/me/tokens').then(r => r.data),
  })
}

export function useCreateAccessToken() {
  const qc = useQueryClient()
  return useMutation<CreateAccessTokenResponse, Error, CreateAccessTokenBody>({
    mutationFn: body => apiClient.post('/me/tokens', body).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['access-tokens'] }),
  })
}

export function useRevokeAccessToken() {
  const qc = useQueryClient()
  return useMutation<void, Error, string>({
    mutationFn: id => apiClient.delete(`/me/tokens/${id}`).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['access-tokens'] }),
  })
}
```

- [ ] **Step 2: `AccessTokensPage` anlegen**

Create `frontend/src/pages/settings/AccessTokensPage.tsx`:
```tsx
import { useState } from 'react'
import {
  useAccessTokens, useCreateAccessToken, useRevokeAccessToken,
} from '@/hooks/useAccessTokens'
import type { CreateAccessTokenResponse, TokenScope } from '@/hooks/useAccessTokens'

function expiryFromDays(days: string): string | null {
  if (!days) return null
  const d = new Date()
  d.setDate(d.getDate() + parseInt(days, 10))
  return d.toISOString()
}

export function AccessTokensPage() {
  const { data: tokens = [], isLoading } = useAccessTokens()
  const createToken = useCreateAccessToken()
  const revokeToken = useRevokeAccessToken()

  const [showCreate, setShowCreate] = useState(false)
  const [name, setName] = useState('')
  const [scope, setScope] = useState<TokenScope>('READ_WRITE')
  const [expiryDays, setExpiryDays] = useState('')
  const [newToken, setNewToken] = useState<CreateAccessTokenResponse | null>(null)
  const [copied, setCopied] = useState(false)

  async function handleCreate() {
    if (!name.trim()) return
    try {
      const result = await createToken.mutateAsync({
        name, scope, expiresAt: expiryFromDays(expiryDays),
      })
      setNewToken(result)
      setName(''); setScope('READ_WRITE'); setExpiryDays('')
      setShowCreate(false)
    } catch (e: any) {
      alert(e.response?.data?.message || 'Failed to create token')
    }
  }

  function handleCopy() {
    if (newToken) {
      navigator.clipboard.writeText(newToken.plaintext)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    }
  }

  if (isLoading) return <div className="text-gray-400">Loading…</div>

  return (
    <div className="max-w-2xl">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">Personal Access Tokens</h1>
        <button
          onClick={() => setShowCreate(true)}
          className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 rounded text-sm font-medium"
        >
          Create Token
        </button>
      </div>

      {newToken && (
        <div className="mb-6 p-4 bg-yellow-900/30 border border-yellow-600 rounded">
          <p className="text-yellow-400 text-sm font-semibold mb-2">
            ⚠ Copy your token now — it will not be shown again.
          </p>
          <div className="flex items-center gap-2">
            <code className="flex-1 bg-gray-900 px-3 py-2 rounded text-sm text-green-400 break-all">
              {newToken.plaintext}
            </code>
            <button
              onClick={handleCopy}
              className="px-3 py-2 bg-gray-700 hover:bg-gray-600 rounded text-sm"
            >
              {copied ? 'Copied!' : 'Copy'}
            </button>
          </div>
          <button
            onClick={() => setNewToken(null)}
            className="mt-2 text-xs text-gray-400 hover:text-white"
          >
            Dismiss
          </button>
        </div>
      )}

      {showCreate && (
        <div className="mb-6 p-4 bg-gray-800 rounded border border-gray-700 flex flex-col gap-3">
          <h2 className="text-sm font-semibold">New Token</h2>
          <input
            type="text"
            placeholder="Token name (e.g. My CLI)"
            value={name}
            onChange={e => setName(e.target.value)}
            className="w-full px-3 py-2 bg-gray-900 rounded border border-gray-600 text-sm"
          />
          <label className="text-sm text-gray-300">
            Scope
            <select
              value={scope}
              onChange={e => setScope(e.target.value as TokenScope)}
              className="w-full mt-1 px-3 py-2 bg-gray-900 rounded border border-gray-600 text-sm"
            >
              <option value="READ_WRITE">Read &amp; Write</option>
              <option value="READ_ONLY">Read-only</option>
            </select>
          </label>
          <label className="text-sm text-gray-300">
            Expires
            <select
              value={expiryDays}
              onChange={e => setExpiryDays(e.target.value)}
              className="w-full mt-1 px-3 py-2 bg-gray-900 rounded border border-gray-600 text-sm"
            >
              <option value="">Never</option>
              <option value="30">30 days</option>
              <option value="60">60 days</option>
              <option value="90">90 days</option>
            </select>
          </label>
          <div className="flex gap-2">
            <button
              onClick={handleCreate}
              disabled={createToken.isPending}
              className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 rounded text-sm"
            >
              {createToken.isPending ? 'Creating…' : 'Create'}
            </button>
            <button
              onClick={() => setShowCreate(false)}
              className="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded text-sm"
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      {tokens.length === 0 ? (
        <p className="text-gray-400 text-sm">No tokens yet.</p>
      ) : (
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-gray-400 border-b border-gray-700">
              <th className="pb-2">Prefix</th>
              <th className="pb-2">Name</th>
              <th className="pb-2">Scope</th>
              <th className="pb-2">Last Used</th>
              <th className="pb-2">Expires</th>
              <th className="pb-2"></th>
            </tr>
          </thead>
          <tbody>
            {tokens.map(t => (
              <tr key={t.id} className="border-b border-gray-800">
                <td className="py-3"><code className="text-green-400">{t.tokenPrefix}…</code></td>
                <td className="py-3">{t.name}</td>
                <td className="py-3">
                  <span className={`px-2 py-0.5 rounded text-xs ${
                    t.scope === 'READ_ONLY' ? 'bg-gray-700 text-gray-300' : 'bg-indigo-900/50 text-indigo-300'
                  }`}>
                    {t.scope === 'READ_ONLY' ? 'Read-only' : 'Read & Write'}
                  </span>
                </td>
                <td className="py-3 text-gray-400">
                  {t.lastUsedAt ? new Date(t.lastUsedAt).toLocaleDateString() : 'Never'}
                </td>
                <td className="py-3 text-gray-400">
                  {t.expiresAt ? new Date(t.expiresAt).toLocaleDateString() : 'Never'}
                </td>
                <td className="py-3 text-right">
                  <button
                    onClick={() => revokeToken.mutate(t.id, {
                      onError: (e: any) => alert(e.response?.data?.message || 'Failed to revoke token'),
                    })}
                    className="px-3 py-1 bg-red-900/40 hover:bg-red-800 text-red-400 hover:text-red-300 rounded text-xs"
                  >
                    Revoke
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}
```

- [ ] **Step 3: Route registrieren**

In `frontend/src/app/router.tsx`:

(a) Import ergänzen (bei den anderen Settings-Page-Imports):
```tsx
import { AccessTokensPage } from '@/pages/settings/AccessTokensPage'
```

(b) Route in das `children`-Array des `RequireAuth`-Blocks einfügen (z.B. nach der `/notifications`-Zeile):
```tsx
      { path: '/settings/tokens', element: <AccessTokensPage /> },
```

- [ ] **Step 4: Sidebar-Link ergänzen**

In `frontend/src/layouts/AppLayout.tsx`, im Haupt-`<nav>` **nach** dem Admin-`<div className="mt-4">…</div>`-Block (der „Admin" mit Audit Log/Automation enthält) einen Account-Block einfügen:
```tsx
          <div className="mt-4">
            <p className="px-3 text-xs font-semibold text-gray-500 uppercase tracking-wider mb-1">
              Account
            </p>
            <div className="flex flex-col gap-1">
              <NavLink to="/settings/tokens" className={subNavLinkClass}>Access Tokens</NavLink>
            </div>
          </div>
```

- [ ] **Step 5: Build/Typecheck**

Run (in `frontend/`): `npm run build`
Expected: kein TypeScript-Fehler.

- [ ] **Step 6: Manuelle Prüfung**

Run (in `frontend/`): `npm run dev` — einloggen, `/settings/tokens` öffnen:
- Token mit Scope „Read-only" + Ablauf „30 days" erstellen → Klartext einmalig sichtbar, Copy funktioniert.
- Tabelle zeigt Prefix/Scope-Badge; Revoke entfernt den Token aus der Liste.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/hooks/useAccessTokens.ts \
        frontend/src/pages/settings/AccessTokensPage.tsx \
        frontend/src/app/router.tsx frontend/src/layouts/AppLayout.tsx
git commit -m "feat(frontend): personal access tokens settings page"
```

---

### Task 6: Frontend — Konto löschen + Admin-Users-Seite

**Files:**
- Create: `frontend/src/hooks/useAccount.ts`
- Create: `frontend/src/hooks/useAdminUsers.ts`
- Create: `frontend/src/pages/settings/AccountSettingsPage.tsx`
- Create: `frontend/src/pages/admin/AdminUsersPage.tsx`
- Modify: `frontend/src/app/router.tsx`
- Modify: `frontend/src/layouts/AppLayout.tsx`

**Interfaces:**
- Consumes: REST `DELETE /api/v1/me`; `GET /api/v1/admin/users`, `POST .../{id}/deactivate`, `POST .../{id}/activate`, `DELETE .../{id}`; `GET /api/v1/auth/me` (Rolle für bedingte Anzeige)
- Produces: Routen `/settings/account`, `/admin/users`; Sidebar-Links „Account" und (nur Admin) „Users"

- [ ] **Step 1: Account-Hook anlegen**

Create `frontend/src/hooks/useAccount.ts`:
```typescript
import { useMutation } from '@tanstack/react-query'
import { apiClient } from '@/api/client'

export function useDeleteAccount() {
  return useMutation<void, Error, void>({
    mutationFn: () => apiClient.delete('/me').then(r => r.data),
  })
}
```

- [ ] **Step 2: Admin-Users-Hook anlegen**

Create `frontend/src/hooks/useAdminUsers.ts`:
```typescript
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/api/client'

export interface AdminUser {
  id: string
  email: string
  displayName: string
  systemRole: 'ADMIN' | 'MEMBER'
  active: boolean
}

export function useAdminUsers() {
  return useQuery<AdminUser[]>({
    queryKey: ['admin-users'],
    queryFn: () => apiClient.get('/admin/users').then(r => r.data),
  })
}

export function useDeactivateUser() {
  const qc = useQueryClient()
  return useMutation<void, Error, string>({
    mutationFn: id => apiClient.post(`/admin/users/${id}/deactivate`).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-users'] }),
  })
}

export function useActivateUser() {
  const qc = useQueryClient()
  return useMutation<void, Error, string>({
    mutationFn: id => apiClient.post(`/admin/users/${id}/activate`).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-users'] }),
  })
}

export function useDeleteUser() {
  const qc = useQueryClient()
  return useMutation<void, Error, string>({
    mutationFn: id => apiClient.delete(`/admin/users/${id}`).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-users'] }),
  })
}
```

- [ ] **Step 3: `AccountSettingsPage` anlegen**

Create `frontend/src/pages/settings/AccountSettingsPage.tsx`:
```tsx
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useDeleteAccount } from '@/hooks/useAccount'

export function AccountSettingsPage() {
  const del = useDeleteAccount()
  const navigate = useNavigate()
  const [confirming, setConfirming] = useState(false)

  async function handleDelete() {
    try {
      await del.mutateAsync()
      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')
      navigate('/login')
    } catch (e: any) {
      alert(e.response?.data?.message || 'Failed to delete account')
    }
  }

  return (
    <div className="max-w-2xl">
      <h1 className="text-2xl font-bold mb-6">Account</h1>
      <div className="p-4 bg-red-900/20 border border-red-800 rounded">
        <h2 className="text-sm font-semibold text-red-400 mb-2">Delete account</h2>
        <p className="text-sm text-gray-400 mb-3">
          Your account will be deactivated and anonymized, and all your access tokens
          will be revoked. This cannot be undone.
        </p>
        {confirming ? (
          <div className="flex gap-2">
            <button
              onClick={handleDelete}
              disabled={del.isPending}
              className="px-4 py-2 bg-red-700 hover:bg-red-600 rounded text-sm"
            >
              {del.isPending ? 'Deleting…' : 'Yes, delete my account'}
            </button>
            <button
              onClick={() => setConfirming(false)}
              className="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded text-sm"
            >
              Cancel
            </button>
          </div>
        ) : (
          <button
            onClick={() => setConfirming(true)}
            className="px-4 py-2 bg-red-900/40 hover:bg-red-800 text-red-300 rounded text-sm"
          >
            Delete account
          </button>
        )}
      </div>
    </div>
  )
}
```

- [ ] **Step 4: `AdminUsersPage` anlegen**

Create `frontend/src/pages/admin/AdminUsersPage.tsx`:
```tsx
import {
  useAdminUsers, useActivateUser, useDeactivateUser, useDeleteUser,
} from '@/hooks/useAdminUsers'

export function AdminUsersPage() {
  const { data: users = [], isLoading } = useAdminUsers()
  const activate = useActivateUser()
  const deactivate = useDeactivateUser()
  const del = useDeleteUser()

  function onError(e: any) {
    alert(e.response?.data?.message || 'Action failed')
  }

  if (isLoading) return <div className="text-gray-400">Loading…</div>

  return (
    <div className="max-w-3xl">
      <h1 className="text-2xl font-bold mb-6">Users</h1>
      <table className="w-full text-sm">
        <thead>
          <tr className="text-left text-gray-400 border-b border-gray-700">
            <th className="pb-2">Email</th>
            <th className="pb-2">Name</th>
            <th className="pb-2">Role</th>
            <th className="pb-2">Status</th>
            <th className="pb-2"></th>
          </tr>
        </thead>
        <tbody>
          {users.map(u => (
            <tr key={u.id} className="border-b border-gray-800">
              <td className="py-3">{u.email}</td>
              <td className="py-3">{u.displayName}</td>
              <td className="py-3 text-gray-400">{u.systemRole}</td>
              <td className="py-3">
                <span className={`px-2 py-0.5 rounded text-xs ${
                  u.active ? 'bg-green-900/50 text-green-300' : 'bg-gray-700 text-gray-400'
                }`}>
                  {u.active ? 'Active' : 'Inactive'}
                </span>
              </td>
              <td className="py-3 text-right flex gap-2 justify-end">
                {u.active ? (
                  <button
                    onClick={() => deactivate.mutate(u.id, { onError })}
                    className="px-3 py-1 bg-gray-700 hover:bg-gray-600 rounded text-xs"
                  >
                    Deactivate
                  </button>
                ) : (
                  <button
                    onClick={() => activate.mutate(u.id, { onError })}
                    className="px-3 py-1 bg-green-900/40 hover:bg-green-800 text-green-300 rounded text-xs"
                  >
                    Activate
                  </button>
                )}
                <button
                  onClick={() => {
                    if (confirm(`Delete ${u.email}? This anonymizes the account.`)) {
                      del.mutate(u.id, { onError })
                    }
                  }}
                  className="px-3 py-1 bg-red-900/40 hover:bg-red-800 text-red-300 rounded text-xs"
                >
                  Delete
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
```

- [ ] **Step 5: Routen registrieren**

In `frontend/src/app/router.tsx`:

(a) Imports ergänzen:
```tsx
import { AccountSettingsPage } from '@/pages/settings/AccountSettingsPage'
import { AdminUsersPage } from '@/pages/admin/AdminUsersPage'
```

(b) Routen in das `RequireAuth`-`children`-Array einfügen (z.B. bei den anderen `/admin/*`- und `/settings/*`-Routen):
```tsx
      { path: '/settings/account', element: <AccountSettingsPage /> },
      { path: '/admin/users', element: <AdminUsersPage /> },
```

- [ ] **Step 6: Sidebar-Links ergänzen**

In `frontend/src/layouts/AppLayout.tsx`:

(a) Aktuellen Nutzer laden — nach `const projectKey = insideProject?.params.key` einfügen:
```tsx
  const { data: me } = useQuery({
    queryKey: ['me'],
    queryFn: () => authApi.me().then(r => r.data),
  })
```

(b) Im „Account"-Block (aus Task 5) den Account-Link ergänzen — der Block wird zu:
```tsx
          <div className="mt-4">
            <p className="px-3 text-xs font-semibold text-gray-500 uppercase tracking-wider mb-1">
              Account
            </p>
            <div className="flex flex-col gap-1">
              <NavLink to="/settings/tokens" className={subNavLinkClass}>Access Tokens</NavLink>
              <NavLink to="/settings/account" className={subNavLinkClass}>Account</NavLink>
            </div>
          </div>
```

(c) Im bestehenden „Admin"-Block den Users-Link **nur für Admins** ergänzen — nach dem `Automation`-`NavLink` innerhalb des Admin-`<div className="flex flex-col gap-1">`:
```tsx
              {me?.role === 'ADMIN' && (
                <NavLink to="/admin/users" className={subNavLinkClass}>Users</NavLink>
              )}
```

> `authApi.me()` liefert `User` mit Feld `role` (siehe `UserResponse`/`frontend/src/types`).

- [ ] **Step 7: Build/Typecheck**

Run (in `frontend/`): `npm run build`
Expected: kein TypeScript-Fehler.

- [ ] **Step 8: Manuelle Prüfung**

Run (in `frontend/`): `npm run dev`:
- Als Admin: „Users"-Link sichtbar, `/admin/users` listet Nutzer; Deactivate/Activate/Delete funktionieren.
- `/settings/account`: „Delete account" mit Bestätigung → Logout + Redirect zu `/login`.

- [ ] **Step 9: Commit**

```bash
git add frontend/src/hooks/useAccount.ts frontend/src/hooks/useAdminUsers.ts \
        frontend/src/pages/settings/AccountSettingsPage.tsx \
        frontend/src/pages/admin/AdminUsersPage.tsx \
        frontend/src/app/router.tsx frontend/src/layouts/AppLayout.tsx
git commit -m "feat(frontend): account deletion + admin user management"
```

---

### Task 7: Wiki-/AI-Guide-Doku

**Files:**
- Modify: passende Seite(n) im Wiki-/Docs-Bereich (Auth/Tokens) + `ai-guide.md`, falls vorhanden.

**Interfaces:**
- Consumes: das fertige Feature (Tasks 1–6)
- Produces: Nutzer- und Entwicklerdoku

- [ ] **Step 1: Vorhandene Doku-Struktur prüfen**

Run: `ls docs/` bzw. Wiki-Verzeichnis lokalisieren (Muster wie bei früheren Phasen). Falls eine `ai-guide.md` existiert, öffnen.

- [ ] **Step 2: PAT-Doku ergänzen**

Dokumentieren:
- Personal Access Tokens: Erzeugung unter „Account → Access Tokens", `twk_`-Prefix, Klartext nur einmalig.
- Nutzung: `Authorization: Bearer twk_…` gegen die API; Rechte = Rechte des Nutzers.
- Read-Only-Scope: nur `GET`/`HEAD`/`OPTIONS`, sonst `403`.
- Abgrenzung zu projektbezogenen `tw_`-API-Keys.
- User-Lebenszyklus: Deaktivieren/Aktivieren (Admin) und Konto löschen (self + Admin, Soft-Delete/Anonymisierung); Wirkung auf Tokens und Login.

- [ ] **Step 3: `ai-guide.md` aktualisieren (falls vorhanden)**

Neue Auth-Muster ergänzen: `AccessTokenAuthFilter`-Reihenfolge, `TokenScope`, `users.active`-Prüfung in `JwtAuthFilter`/Login, `UserAccountService`-Anonymisierungsregeln.

- [ ] **Step 4: Commit**

```bash
git add docs/
git commit -m "docs: personal access tokens + user lifecycle"
```

---

## Self-Review

**Spec-Abdeckung:**
- `access_tokens`-Tabelle/Entity/Enum → Task 2 ✓
- `twk_` + SHA-256 + Klartext einmalig → Task 2 ✓
- `create/list/revoke/authenticate` (TDD) → Task 2 ✓
- Auth-Filter + Bearer `twk_` + Rechte des Nutzers → Task 3 ✓
- Read-Only-Durchsetzung (403) → Task 3 (Filter + Test) ✓
- Optionaler Ablauf (30/60/90/nie) → Task 2 (Feld) + Task 5 (UI) ✓
- Soft-Revoke (`revoked_at`) → Task 2 ✓
- `users.active`/`deleted_at`-Fundament → Task 1 ✓
- Aktiv-Prüfung in JWT-Filter + Login → Task 1 ✓
- „User deaktiviert/gelöscht → Token tot" → Task 2 (`authenticate` prüft `active`) + Task 4 (revoke bei Deaktivierung/Löschen) ✓
- Deaktivieren/Aktivieren/Löschen (Admin) → Task 4 ✓
- Selbst-Löschen (`DELETE /me`) → Task 4 ✓
- Soft-Delete + Anonymisierung → Task 4 (Service + Unit-Test) ✓
- Letzter-Admin-Guard → Task 4 (Service + Unit-Test) ✓
- Frontend Tokens-Seite (Scope-Auswahl, Klartext einmalig, Revoke) → Task 5 ✓
- Frontend Account löschen + Admin-Users → Task 6 ✓
- Navigation/Routen → Task 5 + Task 6 ✓
- Wiki-/AI-Guide-Doku → Task 7 ✓

**Platzhalter:** keine (Task 7 beschreibt Doku-Inhalte konkret; Datei-Pfad hängt von der vorhandenen Wiki-Struktur ab und wird in Step 1 lokalisiert).

**Typkonsistenz:**
- `AccessTokenService`-Signaturen identisch in Tasks 2/3/4 (`create(user, name, scope, expiresAt)`, `revokeAllForUser(userId)`, `authenticate(raw): AuthenticatedToken?`).
- `TokenScope`-Werte `READ_ONLY`/`READ_WRITE` konsistent Backend↔Frontend.
- `tokenPrefix`/`scope`/`plaintext` in DTO ↔ Frontend-Interfaces konsistent.
- `UserRepository.countBySystemRoleAndActiveTrue` einheitlich in Task 4 definiert und genutzt.
- `AuthenticatedToken(user, scope)` in Task 2 definiert, in Task 3 genutzt.
