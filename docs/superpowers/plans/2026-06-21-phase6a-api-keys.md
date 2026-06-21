# Phase 6a: API Keys — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add project-scoped API Keys to the `auth` module so external tools can authenticate with `Authorization: Bearer tw_<token>` instead of short-lived JWTs.

**Architecture:** New `ApiKey` entity (SHA-256 hash stored, never plaintext) + `ApiKeyRepository` in the `auth` module. `ApiKeyService` generates/lists/revokes/authenticates keys. `ApiKeyAuthFilter` (extends `OncePerRequestFilter`) runs before `JwtAuthFilter` in the security chain — detects `tw_`-prefixed tokens, looks up the hash, sets `SecurityContext`. `SecurityConfig` is updated to register the new filter. Frontend: one new settings page at `/p/:key/settings/api-keys`.

**Tech Stack:** Kotlin 2.x, Spring Boot 3.x, Spring Data JPA, Flyway, PostgreSQL 16 / H2 (dev), React 19, TypeScript, shadcn/ui, React Query

## Global Constraints

- Entity ID: `UUID` via `AuditableEntity` base class; migration uses `UUID NOT NULL DEFAULT gen_random_uuid()`
- Migration timestamps: `TIMESTAMPTZ NOT NULL` — JPA `@CreatedDate`/`@LastModifiedDate` populate them automatically
- SHA-256 pattern: `MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }` — same as `RefreshTokenService.hash()`
- Plaintext key returned **once** at creation, never stored
- Admin check: `projectService.requireAdmin(projectKey, caller.id)` for write operations
- Test base: `IntegrationTestBase` (Testcontainers PostgreSQL). Unit tests: `@ExtendWith(MockitoExtension::class)`
- Build/test: `./gradlew test` or `./gradlew test --tests "com.taskowolf.auth.ApiKeyServiceTest"`

---

## File Structure

**Create:**
- `backend/src/main/resources/db/migration/V13__api_keys.sql`
- `backend/src/main/kotlin/com/taskowolf/auth/domain/ApiKey.kt`
- `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/ApiKeyRepository.kt`
- `backend/src/main/kotlin/com/taskowolf/auth/application/ApiKeyService.kt`
- `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/ApiKeyAuthFilter.kt`
- `backend/src/main/kotlin/com/taskowolf/auth/api/dto/CreateApiKeyRequest.kt`
- `backend/src/main/kotlin/com/taskowolf/auth/api/dto/ApiKeyResponse.kt`
- `backend/src/main/kotlin/com/taskowolf/auth/api/dto/CreateApiKeyResponse.kt`
- `backend/src/main/kotlin/com/taskowolf/auth/api/ApiKeyController.kt`
- `backend/src/test/kotlin/com/taskowolf/auth/ApiKeyServiceTest.kt`
- `backend/src/test/kotlin/com/taskowolf/auth/ApiKeyControllerTest.kt`
- `frontend/src/hooks/useApiKeys.ts`
- `frontend/src/pages/settings/ApiKeysPage.tsx`

**Modify:**
- `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/SecurityConfig.kt`
- `frontend/src/app/router.tsx`
- `frontend/src/layouts/AppLayout.tsx`

---

### Task 1: V13 migration + ApiKey domain + ApiKeyService + ApiKeyAuthFilter + SecurityConfig

**Files:**
- Create: `backend/src/main/resources/db/migration/V13__api_keys.sql`
- Create: `backend/src/main/kotlin/com/taskowolf/auth/domain/ApiKey.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/ApiKeyRepository.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/auth/application/ApiKeyService.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/ApiKeyAuthFilter.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/SecurityConfig.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/auth/ApiKeyServiceTest.kt`

**Interfaces:**
- Produces: `ApiKeyService.generate(projectKey, name, expiresAt, caller): CreateApiKeyResponse`, `ApiKeyService.list(projectKey, caller): List<ApiKeyResponse>`, `ApiKeyService.revoke(projectKey, keyId, caller)`, `ApiKeyService.authenticate(rawToken): User?`, `ApiKeyService.sha256(input): String`

- [ ] **Step 1: Write the migration**

Create `backend/src/main/resources/db/migration/V13__api_keys.sql`:
```sql
CREATE TABLE api_keys (
    id           UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name         VARCHAR(255) NOT NULL,
    key_hash     VARCHAR(64)  NOT NULL UNIQUE,
    key_prefix   VARCHAR(12)  NOT NULL,
    project_id   UUID         REFERENCES projects(id) ON DELETE CASCADE,
    created_by   UUID         NOT NULL REFERENCES users(id),
    last_used_at TIMESTAMPTZ,
    expires_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_api_keys_project ON api_keys (project_id);
CREATE INDEX idx_api_keys_hash    ON api_keys (key_hash);
```

- [ ] **Step 2: Write the failing unit test**

Create `backend/src/test/kotlin/com/taskowolf/auth/ApiKeyServiceTest.kt`:
```kotlin
package com.taskowolf.auth

import com.taskowolf.auth.application.ApiKeyService
import com.taskowolf.auth.domain.ApiKey
import com.taskowolf.auth.domain.SystemRole
import com.taskowolf.auth.domain.User
import com.taskowolf.auth.infrastructure.ApiKeyRepository
import com.taskowolf.auth.infrastructure.UserRepository
import com.taskowolf.projects.application.ProjectService
import com.taskowolf.projects.domain.Project
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
class ApiKeyServiceTest {

    @Mock private lateinit var apiKeyRepository: ApiKeyRepository
    @Mock private lateinit var userRepository: UserRepository
    @Mock private lateinit var projectService: ProjectService
    @InjectMocks private lateinit var service: ApiKeyService

    private fun mockUser() = User(email = "test@test.com", displayName = "Test", systemRole = SystemRole.USER)
    private fun mockProject(user: User) = Project(key = "WOLF", name = "Wolf", owner = user)

    @Test
    fun `generate returns plaintext starting with tw_ and stores hash only`() {
        val user = mockUser()
        val project = mockProject(user)
        whenever(projectService.requireAdmin("WOLF", user.id)).thenReturn(project)
        whenever(apiKeyRepository.save(any<ApiKey>())).thenAnswer { it.arguments[0] as ApiKey }

        val response = service.generate("WOLF", "CI Key", null, user)

        assertTrue(response.plaintext.startsWith("tw_"), "plaintext must start with tw_")
        assertTrue(response.keyPrefix.startsWith("tw_"), "prefix must start with tw_")
        verify(apiKeyRepository).save(argThat { keyHash == service.sha256(response.plaintext) })
    }

    @Test
    fun `authenticate returns user for valid unexpired token`() {
        val user = mockUser()
        val token = "tw_validtoken1234567890123456"
        val storedKey = ApiKey(
            name = "test", keyHash = service.sha256(token), keyPrefix = "tw_validtoke",
            projectId = null, createdBy = user.id, expiresAt = null
        )
        whenever(apiKeyRepository.findByKeyHash(service.sha256(token))).thenReturn(storedKey)
        whenever(userRepository.findById(user.id)).thenReturn(Optional.of(user))
        whenever(apiKeyRepository.save(any<ApiKey>())).thenReturn(storedKey)

        val result = service.authenticate(token)

        assertEquals(user.id, result?.id)
    }

    @Test
    fun `authenticate returns null for non-tw_ token`() {
        assertNull(service.authenticate("eyJhbGciOiJIUzI1NiJ9.some.jwt"))
    }

    @Test
    fun `authenticate returns null for expired key`() {
        val token = "tw_expiredtoken1234567890123"
        val expiredKey = ApiKey(
            name = "test", keyHash = service.sha256(token), keyPrefix = "tw_expiredto",
            projectId = null, createdBy = UUID.randomUUID(),
            expiresAt = Instant.now().minusSeconds(60)
        )
        whenever(apiKeyRepository.findByKeyHash(service.sha256(token))).thenReturn(expiredKey)

        assertNull(service.authenticate(token))
    }

    @Test
    fun `authenticate returns null for unknown token`() {
        val token = "tw_unknowntoken123456789012"
        whenever(apiKeyRepository.findByKeyHash(service.sha256(token))).thenReturn(null)

        assertNull(service.authenticate(token))
    }
}
```

- [ ] **Step 3: Run test to confirm it fails**

```
./gradlew test --tests "com.taskowolf.auth.ApiKeyServiceTest" -i
```
Expected: compilation error — `ApiKeyService` does not exist yet.

- [ ] **Step 4: Create ApiKey entity**

Create `backend/src/main/kotlin/com/taskowolf/auth/domain/ApiKey.kt`:
```kotlin
package com.taskowolf.auth.domain

import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "api_keys")
class ApiKey(
    @Column(nullable = false)
    val name: String,

    @Column(nullable = false, unique = true)
    val keyHash: String,

    @Column(nullable = false)
    val keyPrefix: String,

    @Column(name = "project_id")
    val projectId: UUID? = null,

    @Column(nullable = false)
    val createdBy: UUID,

    @Column
    var lastUsedAt: Instant? = null,

    @Column
    val expiresAt: Instant? = null
) : AuditableEntity()
```

- [ ] **Step 5: Create ApiKeyRepository**

Create `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/ApiKeyRepository.kt`:
```kotlin
package com.taskowolf.auth.infrastructure

import com.taskowolf.auth.domain.ApiKey
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ApiKeyRepository : JpaRepository<ApiKey, UUID> {
    fun findByKeyHash(keyHash: String): ApiKey?
    fun findByProjectId(projectId: UUID): List<ApiKey>
    fun findByIdAndProjectId(id: UUID, projectId: UUID): ApiKey?
}
```

- [ ] **Step 6: Create ApiKeyService**

Create `backend/src/main/kotlin/com/taskowolf/auth/application/ApiKeyService.kt`:
```kotlin
package com.taskowolf.auth.application

import com.taskowolf.auth.api.dto.ApiKeyResponse
import com.taskowolf.auth.api.dto.CreateApiKeyResponse
import com.taskowolf.auth.domain.ApiKey
import com.taskowolf.auth.domain.User
import com.taskowolf.auth.infrastructure.ApiKeyRepository
import com.taskowolf.auth.infrastructure.UserRepository
import com.taskowolf.core.infrastructure.NotFoundException
import com.taskowolf.projects.application.ProjectService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Service
class ApiKeyService(
    private val apiKeyRepository: ApiKeyRepository,
    private val userRepository: UserRepository,
    private val projectService: ProjectService
) {
    @Transactional
    fun generate(projectKey: String, name: String, expiresAt: Instant?, caller: User): CreateApiKeyResponse {
        val project = projectService.requireAdmin(projectKey, caller.id)
        val plaintext = "tw_" + secureToken()
        val hash = sha256(plaintext)
        val prefix = plaintext.take(12)
        val key = apiKeyRepository.save(
            ApiKey(name = name, keyHash = hash, keyPrefix = prefix,
                   projectId = project.id, createdBy = caller.id, expiresAt = expiresAt)
        )
        return CreateApiKeyResponse(key.id, key.name, key.keyPrefix, plaintext)
    }

    @Transactional(readOnly = true)
    fun list(projectKey: String, caller: User): List<ApiKeyResponse> {
        val project = projectService.requireMember(projectKey, caller.id)
        return apiKeyRepository.findByProjectId(project.id).map { ApiKeyResponse.from(it) }
    }

    @Transactional
    fun revoke(projectKey: String, keyId: UUID, caller: User) {
        val project = projectService.requireAdmin(projectKey, caller.id)
        val key = apiKeyRepository.findByIdAndProjectId(keyId, project.id)
            ?: throw NotFoundException("API key not found: $keyId")
        apiKeyRepository.delete(key)
    }

    @Transactional
    fun authenticate(rawToken: String): User? {
        if (!rawToken.startsWith("tw_")) return null
        val hash = sha256(rawToken)
        val key = apiKeyRepository.findByKeyHash(hash) ?: return null
        if (key.expiresAt != null && key.expiresAt.isBefore(Instant.now())) return null
        key.lastUsedAt = Instant.now()
        apiKeyRepository.save(key)
        return userRepository.findById(key.createdBy).orElse(null)
    }

    fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun secureToken(): String {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
```

- [ ] **Step 7: Create DTOs (needed for compile)**

Create `backend/src/main/kotlin/com/taskowolf/auth/api/dto/CreateApiKeyRequest.kt`:
```kotlin
package com.taskowolf.auth.api.dto

import jakarta.validation.constraints.NotBlank
import java.time.Instant

data class CreateApiKeyRequest(
    @field:NotBlank val name: String,
    val expiresAt: Instant? = null
)
```

Create `backend/src/main/kotlin/com/taskowolf/auth/api/dto/ApiKeyResponse.kt`:
```kotlin
package com.taskowolf.auth.api.dto

import com.taskowolf.auth.domain.ApiKey
import java.time.Instant
import java.util.UUID

data class ApiKeyResponse(
    val id: UUID,
    val name: String,
    val keyPrefix: String,
    val lastUsedAt: Instant?,
    val expiresAt: Instant?,
    val createdAt: Instant?
) {
    companion object {
        fun from(k: ApiKey) = ApiKeyResponse(
            k.id, k.name, k.keyPrefix, k.lastUsedAt, k.expiresAt, k.createdAt
        )
    }
}
```

Create `backend/src/main/kotlin/com/taskowolf/auth/api/dto/CreateApiKeyResponse.kt`:
```kotlin
package com.taskowolf.auth.api.dto

import java.util.UUID

data class CreateApiKeyResponse(
    val id: UUID,
    val name: String,
    val keyPrefix: String,
    val plaintext: String
)
```

- [ ] **Step 8: Run tests — must pass**

```
./gradlew test --tests "com.taskowolf.auth.ApiKeyServiceTest"
```
Expected: all 5 tests GREEN.

- [ ] **Step 9: Create ApiKeyAuthFilter**

Create `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/ApiKeyAuthFilter.kt`:
```kotlin
package com.taskowolf.auth.infrastructure

import com.taskowolf.auth.application.ApiKeyService
import com.taskowolf.auth.domain.User
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class ApiKeyAuthFilter(
    private val apiKeyService: ApiKeyService
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        val token = request.getHeader("Authorization")
            ?.takeIf { it.startsWith("Bearer tw_") }
            ?.substring(7)

        if (token != null && SecurityContextHolder.getContext().authentication == null) {
            val user = apiKeyService.authenticate(token)
            if (user != null) {
                SecurityContextHolder.getContext().authentication =
                    UsernamePasswordAuthenticationToken(
                        user, null,
                        listOf(SimpleGrantedAuthority("ROLE_${user.systemRole.name}"))
                    )
            }
        }
        chain.doFilter(request, response)
    }
}
```

- [ ] **Step 10: Update SecurityConfig**

Modify `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/SecurityConfig.kt`:
```kotlin
package com.taskowolf.auth.infrastructure

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
class SecurityConfig(
    private val jwtAuthFilter: JwtAuthFilter,
    private val apiKeyAuthFilter: ApiKeyAuthFilter
) {
    @Bean
    fun passwordEncoder() = BCryptPasswordEncoder()

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers(
                    "/api/v1/auth/**",
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                    "/h2-console/**",
                    "/ws/**",
                    "/ws-stomp/**"
                ).permitAll()
                it.anyRequest().authenticated()
            }
            .headers { it.frameOptions { fo -> fo.disable() } }
            .addFilterBefore(apiKeyAuthFilter, JwtAuthFilter::class.java)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }
}
```

- [ ] **Step 11: Run all auth tests**

```
./gradlew test --tests "com.taskowolf.auth.*"
```
Expected: all existing auth tests still GREEN.

- [ ] **Step 12: Commit**

```bash
git add backend/src/main/resources/db/migration/V13__api_keys.sql \
        backend/src/main/kotlin/com/taskowolf/auth/domain/ApiKey.kt \
        backend/src/main/kotlin/com/taskowolf/auth/infrastructure/ApiKeyRepository.kt \
        backend/src/main/kotlin/com/taskowolf/auth/application/ApiKeyService.kt \
        backend/src/main/kotlin/com/taskowolf/auth/infrastructure/ApiKeyAuthFilter.kt \
        backend/src/main/kotlin/com/taskowolf/auth/api/dto/CreateApiKeyRequest.kt \
        backend/src/main/kotlin/com/taskowolf/auth/api/dto/ApiKeyResponse.kt \
        backend/src/main/kotlin/com/taskowolf/auth/api/dto/CreateApiKeyResponse.kt \
        backend/src/main/kotlin/com/taskowolf/auth/infrastructure/SecurityConfig.kt \
        backend/src/test/kotlin/com/taskowolf/auth/ApiKeyServiceTest.kt
git commit -m "feat(auth): add ApiKey domain, ApiKeyService, ApiKeyAuthFilter"
```

---

### Task 2: ApiKeyController + integration test

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/auth/api/ApiKeyController.kt`
- Create: `backend/src/test/kotlin/com/taskowolf/auth/ApiKeyControllerTest.kt`

**Interfaces:**
- Consumes: `ApiKeyService.generate()`, `ApiKeyService.list()`, `ApiKeyService.revoke()`
- Produces: REST endpoints `GET/POST/DELETE /api/v1/projects/{key}/api-keys`

- [ ] **Step 1: Write failing integration test**

Create `backend/src/test/kotlin/com/taskowolf/auth/ApiKeyControllerTest.kt`:
```kotlin
package com.taskowolf.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class ApiKeyControllerTest : IntegrationTestBase() {

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

    private fun createProject(token: String, key: String) {
        mockMvc.perform(
            post("/api/v1/projects")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"key":"$key","name":"$key Project"}""")
        ).andExpect(status().isCreated)
    }

    @Test
    fun `POST api-keys creates key and returns plaintext once`() {
        val token = registerAndLogin("apikey1@test.com")
        createProject(token, "AK1")

        val result = mockMvc.perform(
            post("/api/v1/projects/AK1/api-keys")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"CI Key"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.plaintext").isString)
            .andExpect(jsonPath("$.keyPrefix").value(org.hamcrest.Matchers.startsWith("tw_")))
            .andReturn()

        val plaintext = objectMapper.readTree(result.response.contentAsString).get("plaintext").asText()
        assert(plaintext.startsWith("tw_")) { "plaintext must start with tw_" }
    }

    @Test
    fun `GET api-keys lists keys without plaintext`() {
        val token = registerAndLogin("apikey2@test.com")
        createProject(token, "AK2")
        mockMvc.perform(
            post("/api/v1/projects/AK2/api-keys")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"My Key"}""")
        ).andExpect(status().isCreated)

        mockMvc.perform(
            get("/api/v1/projects/AK2/api-keys")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name").value("My Key"))
            .andExpect(jsonPath("$[0].keyPrefix").isString)
    }

    @Test
    fun `DELETE api-keys revokes key`() {
        val token = registerAndLogin("apikey3@test.com")
        createProject(token, "AK3")
        val createResult = mockMvc.perform(
            post("/api/v1/projects/AK3/api-keys")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Del Key"}""")
        ).andReturn()
        val keyId = objectMapper.readTree(createResult.response.contentAsString).get("id").asText()

        mockMvc.perform(
            delete("/api/v1/projects/AK3/api-keys/$keyId")
                .header("Authorization", "Bearer $token")
        ).andExpect(status().isNoContent)

        mockMvc.perform(
            get("/api/v1/projects/AK3/api-keys")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `API key can authenticate requests`() {
        val token = registerAndLogin("apikey4@test.com")
        createProject(token, "AK4")
        val createResult = mockMvc.perform(
            post("/api/v1/projects/AK4/api-keys")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Auth Key"}""")
        ).andReturn()
        val plaintext = objectMapper.readTree(createResult.response.contentAsString).get("plaintext").asText()

        mockMvc.perform(
            get("/api/v1/projects/AK4/api-keys")
                .header("Authorization", "Bearer $plaintext")
        ).andExpect(status().isOk)
    }
}
```

- [ ] **Step 2: Run test to confirm it fails**

```
./gradlew test --tests "com.taskowolf.auth.ApiKeyControllerTest"
```
Expected: 404 on all endpoints — controller not registered yet.

- [ ] **Step 3: Create ApiKeyController**

Create `backend/src/main/kotlin/com/taskowolf/auth/api/ApiKeyController.kt`:
```kotlin
package com.taskowolf.auth.api

import com.taskowolf.auth.api.dto.CreateApiKeyRequest
import com.taskowolf.auth.application.ApiKeyService
import com.taskowolf.auth.domain.User
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/projects/{key}/api-keys")
class ApiKeyController(private val apiKeyService: ApiKeyService) {

    @GetMapping
    fun list(@PathVariable key: String, @AuthenticationPrincipal user: User) =
        apiKeyService.list(key, user)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @PathVariable key: String,
        @Valid @RequestBody request: CreateApiKeyRequest,
        @AuthenticationPrincipal user: User
    ) = apiKeyService.generate(key, request.name, request.expiresAt, user)

    @DeleteMapping("/{keyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revoke(
        @PathVariable key: String,
        @PathVariable keyId: UUID,
        @AuthenticationPrincipal user: User
    ) = apiKeyService.revoke(key, keyId, user)
}
```

- [ ] **Step 4: Run tests — all must pass**

```
./gradlew test --tests "com.taskowolf.auth.ApiKeyControllerTest"
```
Expected: all 4 tests GREEN.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/auth/api/ApiKeyController.kt \
        backend/src/test/kotlin/com/taskowolf/auth/ApiKeyControllerTest.kt
git commit -m "feat(auth): add ApiKeyController with integration tests"
```

---

### Task 3: Frontend — ApiKeysPage + hook + router + nav

**Files:**
- Create: `frontend/src/hooks/useApiKeys.ts`
- Create: `frontend/src/pages/settings/ApiKeysPage.tsx`
- Modify: `frontend/src/app/router.tsx`
- Modify: `frontend/src/layouts/AppLayout.tsx`

**Interfaces:**
- Consumes: REST `GET/POST/DELETE /api/v1/projects/{key}/api-keys`
- Produces: route `/p/:key/settings/api-keys`, sidebar sub-link "Settings → API Keys"

- [ ] **Step 1: Create the React Query hook**

Create `frontend/src/hooks/useApiKeys.ts`:
```typescript
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/api/client'

export interface ApiKeyItem {
  id: string
  name: string
  keyPrefix: string
  lastUsedAt: string | null
  expiresAt: string | null
  createdAt: string | null
}

export interface CreateApiKeyResponse {
  id: string
  name: string
  keyPrefix: string
  plaintext: string
}

export function useApiKeys(projectKey: string) {
  return useQuery<ApiKeyItem[]>({
    queryKey: ['api-keys', projectKey],
    queryFn: () => apiClient.get(`/projects/${projectKey}/api-keys`).then(r => r.data),
  })
}

export function useCreateApiKey(projectKey: string) {
  const qc = useQueryClient()
  return useMutation<CreateApiKeyResponse, Error, { name: string; expiresAt?: string }>({
    mutationFn: body => apiClient.post(`/projects/${projectKey}/api-keys`, body).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['api-keys', projectKey] }),
  })
}

export function useRevokeApiKey(projectKey: string) {
  const qc = useQueryClient()
  return useMutation<void, Error, string>({
    mutationFn: keyId => apiClient.delete(`/projects/${projectKey}/api-keys/${keyId}`).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['api-keys', projectKey] }),
  })
}
```

- [ ] **Step 2: Create ApiKeysPage**

Create `frontend/src/pages/settings/ApiKeysPage.tsx`:
```typescript
import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { useApiKeys, useCreateApiKey, useRevokeApiKey } from '@/hooks/useApiKeys'
import type { CreateApiKeyResponse } from '@/hooks/useApiKeys'

export function ApiKeysPage() {
  const { key } = useParams<{ key: string }>()
  const projectKey = key!
  const { data: keys = [], isLoading } = useApiKeys(projectKey)
  const createKey = useCreateApiKey(projectKey)
  const revokeKey = useRevokeApiKey(projectKey)

  const [showCreate, setShowCreate] = useState(false)
  const [keyName, setKeyName] = useState('')
  const [newKey, setNewKey] = useState<CreateApiKeyResponse | null>(null)
  const [copied, setCopied] = useState(false)

  async function handleCreate() {
    if (!keyName.trim()) return
    const result = await createKey.mutateAsync({ name: keyName })
    setNewKey(result)
    setKeyName('')
    setShowCreate(false)
  }

  function handleCopy() {
    if (newKey) {
      navigator.clipboard.writeText(newKey.plaintext)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    }
  }

  if (isLoading) return <div className="text-gray-400">Loading…</div>

  return (
    <div className="max-w-2xl">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">API Keys</h1>
        <button
          onClick={() => setShowCreate(true)}
          className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 rounded text-sm font-medium"
        >
          Create API Key
        </button>
      </div>

      {newKey && (
        <div className="mb-6 p-4 bg-yellow-900/30 border border-yellow-600 rounded">
          <p className="text-yellow-400 text-sm font-semibold mb-2">
            ⚠ Copy your key now — it will not be shown again.
          </p>
          <div className="flex items-center gap-2">
            <code className="flex-1 bg-gray-900 px-3 py-2 rounded text-sm text-green-400 break-all">
              {newKey.plaintext}
            </code>
            <button
              onClick={handleCopy}
              className="px-3 py-2 bg-gray-700 hover:bg-gray-600 rounded text-sm"
            >
              {copied ? 'Copied!' : 'Copy'}
            </button>
          </div>
          <button
            onClick={() => setNewKey(null)}
            className="mt-2 text-xs text-gray-400 hover:text-white"
          >
            Dismiss
          </button>
        </div>
      )}

      {showCreate && (
        <div className="mb-6 p-4 bg-gray-800 rounded border border-gray-700">
          <h2 className="text-sm font-semibold mb-3">New API Key</h2>
          <input
            type="text"
            placeholder="Key name (e.g. CI Pipeline)"
            value={keyName}
            onChange={e => setKeyName(e.target.value)}
            className="w-full px-3 py-2 bg-gray-900 rounded border border-gray-600 text-sm mb-3"
          />
          <div className="flex gap-2">
            <button
              onClick={handleCreate}
              disabled={createKey.isPending}
              className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 rounded text-sm"
            >
              {createKey.isPending ? 'Creating…' : 'Create'}
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

      {keys.length === 0 ? (
        <p className="text-gray-400 text-sm">No API keys yet.</p>
      ) : (
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-gray-400 border-b border-gray-700">
              <th className="pb-2">Prefix</th>
              <th className="pb-2">Name</th>
              <th className="pb-2">Last Used</th>
              <th className="pb-2">Expires</th>
              <th className="pb-2"></th>
            </tr>
          </thead>
          <tbody>
            {keys.map(k => (
              <tr key={k.id} className="border-b border-gray-800">
                <td className="py-3">
                  <code className="text-green-400">{k.keyPrefix}…</code>
                </td>
                <td className="py-3">{k.name}</td>
                <td className="py-3 text-gray-400">
                  {k.lastUsedAt ? new Date(k.lastUsedAt).toLocaleDateString() : 'Never'}
                </td>
                <td className="py-3 text-gray-400">
                  {k.expiresAt ? new Date(k.expiresAt).toLocaleDateString() : 'Never'}
                </td>
                <td className="py-3 text-right">
                  <button
                    onClick={() => revokeKey.mutate(k.id)}
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

- [ ] **Step 3: Add route to router.tsx**

In `frontend/src/app/router.tsx`, add the import and route:
```typescript
// Add import:
import { ApiKeysPage } from '@/pages/settings/ApiKeysPage'

// Add route inside the RequireAuth children array (alongside other /p/:key/* routes):
{ path: '/p/:key/settings/api-keys', element: <ApiKeysPage /> },
```

- [ ] **Step 4: Add Settings sub-nav to AppLayout.tsx**

In `frontend/src/layouts/AppLayout.tsx`, add a Settings group below the existing project nav links:
```typescript
// Inside the {insideProject && projectKey && ( ... )} block,
// after the existing <div className="flex flex-col gap-1"> with project links,
// add a new section:

<div className="mt-4">
  <p className="px-3 text-xs font-semibold text-gray-500 uppercase tracking-wider mb-1">
    Settings
  </p>
  <div className="flex flex-col gap-1">
    <NavLink to={`/p/${projectKey}/settings/api-keys`} className={subNavLinkClass}>
      API Keys
    </NavLink>
    <NavLink to={`/p/${projectKey}/settings/webhooks`} className={subNavLinkClass}>
      Webhooks
    </NavLink>
    <NavLink to={`/p/${projectKey}/settings/integrations`} className={subNavLinkClass}>
      Integrations
    </NavLink>
  </div>
</div>
```

- [ ] **Step 5: Run full test suite**

```
./gradlew test
```
Expected: all backend tests GREEN.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/hooks/useApiKeys.ts \
        frontend/src/pages/settings/ApiKeysPage.tsx \
        frontend/src/app/router.tsx \
        frontend/src/layouts/AppLayout.tsx
git commit -m "feat(frontend): add API Keys settings page"
```