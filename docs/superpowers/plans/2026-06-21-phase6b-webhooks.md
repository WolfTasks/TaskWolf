# Phase 6b: Outgoing Webhooks — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Admin kann pro Projekt ausgehende Webhooks konfigurieren. Bei Domain Events feuert `WebhookDispatcher` asynchron HMAC-signierte HTTP-POSTs an alle passenden URLs. Deliveries werden geloggt. Fehlgeschlagene Deliveries werden automatisch bis zu 3× wiederholt. Deliveries älter als 30 Tage werden täglich gelöscht.

**Architecture:** Neues `integrations`-Modul. `Webhook`-Entity + `WebhookDelivery`-Entity (V14). `SsrfValidator` blockiert private IP-Ranges beim Speichern. `HmacSigner` signiert mit `HmacSHA256`. `WebhookService` (CRUD + SSRF-Check). `WebhookDispatcher` (`@EventListener` + `@Async`) dispatcht HTTP-POSTs. `DeliveryRetryJob` (`@Scheduled`) retried Deliveries mit `attemptCount < 3`. `DeliveryCleanupJob` (`@Scheduled`) löscht alte Deliveries. `TaskWolfApplication` bekommt `@EnableAsync` + `@EnableScheduling`. `WebhookController` + Frontend-Settings-Seite.

**Tech Stack:** Kotlin 2.x, Spring Boot 3.x, Spring Data JPA, Flyway, `RestTemplate`, `@Async`, `@Scheduled`, PostgreSQL 16, React 19, TypeScript, React Query

**Prerequisite:** Phase 6a (API Keys) muss abgeschlossen und gemergt sein.

## Global Constraints

- Entity IDs: UUID via `AuditableEntity`; SQL: `UUID NOT NULL DEFAULT gen_random_uuid()`
- `webhooks.events`: `TEXT NOT NULL` (JSON-Array als String) via `StringListConverter` — kein `TEXT[]` (H2-Kompatibilität)
- `webhook_deliveries.payload`: `TEXT NOT NULL` (JSON als String)
- Webhook-Secret: SHA-256 gehasht gespeichert; Plaintext nur bei Erstellung zurückgegeben
- HMAC-Signatur-Header: `X-TaskWolf-Signature: sha256=<hex>`
- Retry-Delays: attempt 1 → `nextRetryAt = now+1min`; attempt 2 → `now+5min`; attempt 3 → `now+30min`; nach 3 Versuchen kein weiterer Retry
- Admin-Check: `projectService.requireAdmin(projectKey, caller.id)`
- `@EnableAsync` + `@EnableScheduling` werden zu `TaskWolfApplication` hinzugefügt
- Test base: `IntegrationTestBase` (Testcontainers PostgreSQL). Unit tests: `@ExtendWith(MockitoExtension::class)`

---

## File Structure

**Create:**
- `backend/src/main/resources/db/migration/V14__webhooks.sql`
- `backend/src/main/kotlin/com/taskowolf/integrations/infrastructure/StringListConverter.kt`
- `backend/src/main/kotlin/com/taskowolf/integrations/domain/WebhookEventType.kt`
- `backend/src/main/kotlin/com/taskowolf/integrations/domain/Webhook.kt`
- `backend/src/main/kotlin/com/taskowolf/integrations/domain/WebhookDelivery.kt`
- `backend/src/main/kotlin/com/taskowolf/integrations/infrastructure/WebhookRepository.kt`
- `backend/src/main/kotlin/com/taskowolf/integrations/infrastructure/WebhookDeliveryRepository.kt`
- `backend/src/main/kotlin/com/taskowolf/integrations/application/SsrfValidator.kt`
- `backend/src/main/kotlin/com/taskowolf/integrations/application/HmacSigner.kt`
- `backend/src/main/kotlin/com/taskowolf/integrations/application/WebhookService.kt`
- `backend/src/main/kotlin/com/taskowolf/integrations/application/WebhookDispatcher.kt`
- `backend/src/main/kotlin/com/taskowolf/integrations/application/DeliveryRetryJob.kt`
- `backend/src/main/kotlin/com/taskowolf/integrations/application/DeliveryCleanupJob.kt`
- `backend/src/main/kotlin/com/taskowolf/integrations/api/dto/CreateWebhookRequest.kt`
- `backend/src/main/kotlin/com/taskowolf/integrations/api/dto/UpdateWebhookRequest.kt`
- `backend/src/main/kotlin/com/taskowolf/integrations/api/dto/WebhookResponse.kt`
- `backend/src/main/kotlin/com/taskowolf/integrations/api/dto/WebhookDeliveryResponse.kt`
- `backend/src/main/kotlin/com/taskowolf/integrations/api/WebhookController.kt`
- `backend/src/test/kotlin/com/taskowolf/integrations/SsrfValidatorTest.kt`
- `backend/src/test/kotlin/com/taskowolf/integrations/HmacSignerTest.kt`
- `backend/src/test/kotlin/com/taskowolf/integrations/WebhookServiceTest.kt`
- `backend/src/test/kotlin/com/taskowolf/integrations/WebhookControllerTest.kt`
- `frontend/src/hooks/useWebhooks.ts`
- `frontend/src/pages/settings/WebhooksPage.tsx`

**Modify:**
- `backend/src/main/kotlin/com/taskowolf/TaskWolfApplication.kt`
- `frontend/src/app/router.tsx`

---

### Task 4: V14 migration + Webhook/WebhookDelivery domain + StringListConverter

**Files:**
- Create: `backend/src/main/resources/db/migration/V14__webhooks.sql`
- Create: `backend/src/main/kotlin/com/taskowolf/integrations/infrastructure/StringListConverter.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/integrations/domain/WebhookEventType.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/integrations/domain/Webhook.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/integrations/domain/WebhookDelivery.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/integrations/infrastructure/WebhookRepository.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/integrations/infrastructure/WebhookDeliveryRepository.kt`

**Interfaces:**
- Produces: `Webhook`, `WebhookDelivery`, `WebhookRepository`, `WebhookDeliveryRepository`, `WebhookEventType`, `StringListConverter`

- [ ] **Step 1: Create the migration**

Create `backend/src/main/resources/db/migration/V14__webhooks.sql`:
```sql
CREATE TABLE webhooks (
    id          UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    project_id  UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    url         VARCHAR(2048) NOT NULL,
    secret_hash VARCHAR(64)  NOT NULL,
    events      TEXT         NOT NULL,
    enabled     BOOLEAN      NOT NULL DEFAULT true,
    created_by  UUID         NOT NULL REFERENCES users(id),
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_webhooks_project ON webhooks (project_id, enabled);

CREATE TABLE webhook_deliveries (
    id              UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    webhook_id      UUID         NOT NULL REFERENCES webhooks(id) ON DELETE CASCADE,
    event_type      VARCHAR(100) NOT NULL,
    payload         TEXT         NOT NULL,
    response_status INTEGER,
    response_body   TEXT,
    attempt_count   INTEGER      NOT NULL DEFAULT 0,
    next_retry_at   TIMESTAMPTZ,
    delivered_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_webhook_deliveries_webhook   ON webhook_deliveries (webhook_id);
CREATE INDEX idx_webhook_deliveries_retry     ON webhook_deliveries (next_retry_at) WHERE delivered_at IS NULL;
```

- [ ] **Step 2: Create StringListConverter**

Create `backend/src/main/kotlin/com/taskowolf/integrations/infrastructure/StringListConverter.kt`:
```kotlin
package com.taskowolf.integrations.infrastructure

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter
class StringListConverter : AttributeConverter<List<String>, String> {
    private val mapper = ObjectMapper()
    private val typeRef = object : TypeReference<List<String>>() {}

    override fun convertToDatabaseColumn(attr: List<String>?): String =
        mapper.writeValueAsString(attr ?: emptyList<String>())

    override fun convertToEntityAttribute(dbData: String?): List<String> =
        if (dbData.isNullOrBlank()) emptyList() else mapper.readValue(dbData, typeRef)
}
```

- [ ] **Step 3: Create WebhookEventType**

Create `backend/src/main/kotlin/com/taskowolf/integrations/domain/WebhookEventType.kt`:
```kotlin
package com.taskowolf.integrations.domain

object WebhookEventType {
    const val ISSUE_CREATED       = "issue.created"
    const val ISSUE_UPDATED       = "issue.updated"
    const val ISSUE_STATUS_CHANGED = "issue.status_changed"
    const val ISSUE_ASSIGNED      = "issue.assigned"
    const val ISSUE_DELETED       = "issue.deleted"
    const val SPRINT_STARTED      = "sprint.started"
    const val SPRINT_COMPLETED    = "sprint.completed"
    const val COMMENT_CREATED     = "comment.created"
    const val ATTACHMENT_ADDED    = "attachment.added"

    val ALL = listOf(
        ISSUE_CREATED, ISSUE_UPDATED, ISSUE_STATUS_CHANGED, ISSUE_ASSIGNED,
        ISSUE_DELETED, SPRINT_STARTED, SPRINT_COMPLETED, COMMENT_CREATED, ATTACHMENT_ADDED
    )
}
```

- [ ] **Step 4: Create Webhook entity**

Create `backend/src/main/kotlin/com/taskowolf/integrations/domain/Webhook.kt`:
```kotlin
package com.taskowolf.integrations.domain

import com.taskowolf.core.domain.AuditableEntity
import com.taskowolf.integrations.infrastructure.StringListConverter
import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "webhooks")
class Webhook(
    @Column(name = "project_id", nullable = false)
    val projectId: UUID,

    @Column(nullable = false, length = 2048)
    var url: String,

    @Column(nullable = false)
    var secretHash: String,

    @Convert(converter = StringListConverter::class)
    @Column(nullable = false, columnDefinition = "TEXT")
    var events: List<String> = emptyList(),

    @Column(nullable = false)
    var enabled: Boolean = true,

    @Column(nullable = false)
    val createdBy: UUID
) : AuditableEntity()
```

- [ ] **Step 5: Create WebhookDelivery entity**

Create `backend/src/main/kotlin/com/taskowolf/integrations/domain/WebhookDelivery.kt`:
```kotlin
package com.taskowolf.integrations.domain

import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "webhook_deliveries")
class WebhookDelivery(
    @Column(name = "webhook_id", nullable = false)
    val webhookId: UUID,

    @Column(nullable = false)
    val eventType: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    val payload: String,

    @Column
    var responseStatus: Int? = null,

    @Column(columnDefinition = "TEXT")
    var responseBody: String? = null,

    @Column(nullable = false)
    var attemptCount: Int = 0,

    @Column
    var nextRetryAt: Instant? = null,

    @Column
    var deliveredAt: Instant? = null
) : AuditableEntity()
```

- [ ] **Step 6: Create repositories**

Create `backend/src/main/kotlin/com/taskowolf/integrations/infrastructure/WebhookRepository.kt`:
```kotlin
package com.taskowolf.integrations.infrastructure

import com.taskowolf.integrations.domain.Webhook
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface WebhookRepository : JpaRepository<Webhook, UUID> {
    fun findByProjectId(projectId: UUID): List<Webhook>
    fun findByProjectIdAndEnabled(projectId: UUID, enabled: Boolean): List<Webhook>
    fun findByIdAndProjectId(id: UUID, projectId: UUID): Webhook?
}
```

Create `backend/src/main/kotlin/com/taskowolf/integrations/infrastructure/WebhookDeliveryRepository.kt`:
```kotlin
package com.taskowolf.integrations.infrastructure

import com.taskowolf.integrations.domain.WebhookDelivery
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface WebhookDeliveryRepository : JpaRepository<WebhookDelivery, UUID> {
    fun findByWebhookId(webhookId: UUID, pageable: Pageable): Page<WebhookDelivery>

    @Query("""
        SELECT d FROM WebhookDelivery d
        WHERE d.deliveredAt IS NULL
          AND d.nextRetryAt <= :now
          AND d.attemptCount < 3
    """)
    fun findPendingRetries(now: Instant): List<WebhookDelivery>

    @Modifying
    @Query("DELETE FROM WebhookDelivery d WHERE d.createdAt < :cutoff")
    fun deleteOlderThan(cutoff: Instant): Int
}
```

- [ ] **Step 7: Verify compilation**

```
./gradlew compileKotlin
```
Expected: BUILD SUCCESSFUL — no compile errors.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/resources/db/migration/V14__webhooks.sql \
        backend/src/main/kotlin/com/taskowolf/integrations/
git commit -m "feat(integrations): add V14 migration, Webhook/WebhookDelivery domain"
```

---

### Task 5: SsrfValidator + HmacSigner + WebhookService (unit tests)

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/integrations/application/SsrfValidator.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/integrations/application/HmacSigner.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/integrations/application/WebhookService.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/integrations/api/dto/CreateWebhookRequest.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/integrations/api/dto/UpdateWebhookRequest.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/integrations/api/dto/WebhookResponse.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/integrations/api/dto/WebhookDeliveryResponse.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/integrations/SsrfValidatorTest.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/integrations/HmacSignerTest.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/integrations/WebhookServiceTest.kt`

**Interfaces:**
- Produces: `SsrfValidator.validate(url)`, `HmacSigner.sign(payload, secret): String`, `WebhookService.create/list/update/delete/listDeliveries/testPing`

- [ ] **Step 1: Write failing tests**

Create `backend/src/test/kotlin/com/taskowolf/integrations/SsrfValidatorTest.kt`:
```kotlin
package com.taskowolf.integrations

import com.taskowolf.integrations.application.SsrfValidator
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SsrfValidatorTest {
    private val validator = SsrfValidator()

    @Test
    fun `public URL is accepted`() {
        assertDoesNotThrow { validator.validate("https://hooks.example.com/payload") }
    }

    @Test
    fun `localhost is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            validator.validate("http://localhost:8080/hook")
        }
    }

    @Test
    fun `127_0_0_1 is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            validator.validate("http://127.0.0.1/hook")
        }
    }

    @Test
    fun `10_x private range is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            validator.validate("http://10.0.0.1/hook")
        }
    }

    @Test
    fun `192_168_x private range is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            validator.validate("http://192.168.1.100/hook")
        }
    }
}
```

Create `backend/src/test/kotlin/com/taskowolf/integrations/HmacSignerTest.kt`:
```kotlin
package com.taskowolf.integrations

import com.taskowolf.integrations.application.HmacSigner
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HmacSignerTest {
    private val signer = HmacSigner()

    @Test
    fun `sign produces sha256= prefixed hex string`() {
        val sig = signer.sign("""{"event":"ping"}""", "my-secret")
        assertTrue(sig.startsWith("sha256="), "signature must start with sha256=")
        assertEquals(71, sig.length, "sha256= (7) + 64 hex chars = 71")
    }

    @Test
    fun `same input produces same signature`() {
        val a = signer.sign("payload", "secret")
        val b = signer.sign("payload", "secret")
        assertEquals(a, b)
    }

    @Test
    fun `different secret produces different signature`() {
        val a = signer.sign("payload", "secret1")
        val b = signer.sign("payload", "secret2")
        assertNotEquals(a, b)
    }

    @Test
    fun `verify returns true for matching signature`() {
        val payload = """{"event":"issue.created"}"""
        val secret = "test-secret"
        val sig = signer.sign(payload, secret)
        assertTrue(signer.verify(payload, secret, sig))
    }

    @Test
    fun `verify returns false for tampered payload`() {
        val secret = "test-secret"
        val sig = signer.sign("original", secret)
        assertFalse(signer.verify("tampered", secret, sig))
    }
}
```

- [ ] **Step 2: Run tests to confirm failure**

```
./gradlew test --tests "com.taskowolf.integrations.SsrfValidatorTest" --tests "com.taskowolf.integrations.HmacSignerTest"
```
Expected: compilation error — classes don't exist yet.

- [ ] **Step 3: Create SsrfValidator**

Create `backend/src/main/kotlin/com/taskowolf/integrations/application/SsrfValidator.kt`:
```kotlin
package com.taskowolf.integrations.application

import org.springframework.stereotype.Component
import java.net.InetAddress
import java.net.URL

@Component
class SsrfValidator {
    fun validate(url: String) {
        val host = try { URL(url).host } catch (e: Exception) {
            throw IllegalArgumentException("Invalid URL: $url")
        }
        val addresses = try {
            InetAddress.getAllByName(host)
        } catch (e: Exception) {
            throw IllegalArgumentException("Cannot resolve host: $host")
        }
        for (addr in addresses) {
            if (isPrivate(addr)) {
                throw IllegalArgumentException(
                    "Webhook URL resolves to a private or reserved IP address"
                )
            }
        }
    }

    private fun isPrivate(addr: InetAddress): Boolean =
        addr.isLoopbackAddress ||
        addr.isSiteLocalAddress ||
        addr.isLinkLocalAddress ||
        addr.isAnyLocalAddress ||
        addr.isMulticastAddress
}
```

- [ ] **Step 4: Create HmacSigner**

Create `backend/src/main/kotlin/com/taskowolf/integrations/application/HmacSigner.kt`:
```kotlin
package com.taskowolf.integrations.application

import org.springframework.stereotype.Component
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class HmacSigner {
    fun sign(payload: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val hex = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "sha256=$hex"
    }

    fun verify(payload: String, secret: String, signature: String): Boolean =
        sign(payload, secret) == signature
}
```

- [ ] **Step 5: Run signer + validator tests — must pass**

```
./gradlew test --tests "com.taskowolf.integrations.SsrfValidatorTest" --tests "com.taskowolf.integrations.HmacSignerTest"
```
Expected: all 8 tests GREEN.

- [ ] **Step 6: Create DTOs**

Create `backend/src/main/kotlin/com/taskowolf/integrations/api/dto/CreateWebhookRequest.kt`:
```kotlin
package com.taskowolf.integrations.api.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty

data class CreateWebhookRequest(
    @field:NotBlank val url: String,
    @field:NotEmpty val events: List<String>,
    val secret: String? = null,
    val enabled: Boolean = true
)
```

Create `backend/src/main/kotlin/com/taskowolf/integrations/api/dto/UpdateWebhookRequest.kt`:
```kotlin
package com.taskowolf.integrations.api.dto

data class UpdateWebhookRequest(
    val url: String? = null,
    val events: List<String>? = null,
    val enabled: Boolean? = null
)
```

Create `backend/src/main/kotlin/com/taskowolf/integrations/api/dto/WebhookResponse.kt`:
```kotlin
package com.taskowolf.integrations.api.dto

import com.taskowolf.integrations.domain.Webhook
import java.time.Instant
import java.util.UUID

data class WebhookResponse(
    val id: UUID,
    val url: String,
    val events: List<String>,
    val enabled: Boolean,
    val createdAt: Instant?
) {
    companion object {
        fun from(w: Webhook) = WebhookResponse(w.id, w.url, w.events, w.enabled, w.createdAt)
    }
}
```

Create `backend/src/main/kotlin/com/taskowolf/integrations/api/dto/WebhookDeliveryResponse.kt`:
```kotlin
package com.taskowolf.integrations.api.dto

import com.taskowolf.integrations.domain.WebhookDelivery
import java.time.Instant
import java.util.UUID

data class WebhookDeliveryResponse(
    val id: UUID,
    val webhookId: UUID,
    val eventType: String,
    val payload: String,
    val responseStatus: Int?,
    val responseBody: String?,
    val attemptCount: Int,
    val deliveredAt: Instant?,
    val createdAt: Instant?
) {
    companion object {
        fun from(d: WebhookDelivery) = WebhookDeliveryResponse(
            d.id, d.webhookId, d.eventType, d.payload,
            d.responseStatus, d.responseBody, d.attemptCount, d.deliveredAt, d.createdAt
        )
    }
}
```

- [ ] **Step 7: Write WebhookService unit test**

Create `backend/src/test/kotlin/com/taskowolf/integrations/WebhookServiceTest.kt`:
```kotlin
package com.taskowolf.integrations

import com.taskowolf.auth.domain.SystemRole
import com.taskowolf.auth.domain.User
import com.taskowolf.integrations.api.dto.CreateWebhookRequest
import com.taskowolf.integrations.application.HmacSigner
import com.taskowolf.integrations.application.SsrfValidator
import com.taskowolf.integrations.application.WebhookService
import com.taskowolf.integrations.domain.Webhook
import com.taskowolf.integrations.infrastructure.WebhookDeliveryRepository
import com.taskowolf.integrations.infrastructure.WebhookRepository
import com.taskowolf.projects.application.ProjectService
import com.taskowolf.projects.domain.Project
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class WebhookServiceTest {

    @Mock private lateinit var webhookRepository: WebhookRepository
    @Mock private lateinit var deliveryRepository: WebhookDeliveryRepository
    @Mock private lateinit var projectService: ProjectService
    @Mock private lateinit var ssrfValidator: SsrfValidator
    @Mock private lateinit var hmacSigner: HmacSigner
    @InjectMocks private lateinit var service: WebhookService

    private fun mockUser() = User(email = "u@t.com", displayName = "U", systemRole = SystemRole.USER)
    private fun mockProject(user: User) = Project(key = "WH", name = "WH", owner = user)

    @Test
    fun `create validates SSRF and stores hashed secret`() {
        val user = mockUser()
        val project = mockProject(user)
        whenever(projectService.requireAdmin("WH", user.id)).thenReturn(project)
        whenever(webhookRepository.save(any<Webhook>())).thenAnswer { it.arguments[0] as Webhook }

        val req = CreateWebhookRequest(
            url = "https://hooks.example.com/payload",
            events = listOf("issue.created"),
            secret = "mysecret"
        )
        service.create("WH", req, user)

        verify(ssrfValidator).validate("https://hooks.example.com/payload")
        verify(webhookRepository).save(argThat { secretHash != "mysecret" })
    }

    @Test
    fun `create rejects SSRF URL`() {
        val user = mockUser()
        val project = mockProject(user)
        whenever(projectService.requireAdmin("WH", user.id)).thenReturn(project)
        doThrow(IllegalArgumentException("private IP")).whenever(ssrfValidator).validate(any())

        assertThrows<IllegalArgumentException> {
            service.create("WH", CreateWebhookRequest("http://localhost/hook", listOf("issue.created")), user)
        }
        verify(webhookRepository, never()).save(any())
    }
}
```

- [ ] **Step 8: Create WebhookService**

Create `backend/src/main/kotlin/com/taskowolf/integrations/application/WebhookService.kt`:
```kotlin
package com.taskowolf.integrations.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.auth.domain.User
import com.taskowolf.core.infrastructure.NotFoundException
import com.taskowolf.integrations.api.dto.CreateWebhookRequest
import com.taskowolf.integrations.api.dto.UpdateWebhookRequest
import com.taskowolf.integrations.api.dto.WebhookDeliveryResponse
import com.taskowolf.integrations.api.dto.WebhookResponse
import com.taskowolf.integrations.domain.Webhook
import com.taskowolf.integrations.domain.WebhookDelivery
import com.taskowolf.integrations.infrastructure.WebhookDeliveryRepository
import com.taskowolf.integrations.infrastructure.WebhookRepository
import com.taskowolf.projects.application.ProjectService
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Service
class WebhookService(
    private val webhookRepository: WebhookRepository,
    private val deliveryRepository: WebhookDeliveryRepository,
    private val projectService: ProjectService,
    private val ssrfValidator: SsrfValidator,
    private val hmacSigner: HmacSigner,
    private val objectMapper: ObjectMapper
) {
    data class CreateResult(val webhook: WebhookResponse, val plaintextSecret: String)

    @Transactional
    fun create(projectKey: String, req: CreateWebhookRequest, caller: User): CreateResult {
        val project = projectService.requireAdmin(projectKey, caller.id)
        ssrfValidator.validate(req.url)
        val plainSecret = req.secret?.takeIf { it.isNotBlank() } ?: generateSecret()
        val hook = webhookRepository.save(
            Webhook(
                projectId = project.id, url = req.url,
                secretHash = sha256(plainSecret), events = req.events,
                enabled = req.enabled, createdBy = caller.id
            )
        )
        return CreateResult(WebhookResponse.from(hook), plainSecret)
    }

    @Transactional(readOnly = true)
    fun list(projectKey: String, caller: User): List<WebhookResponse> {
        val project = projectService.requireMember(projectKey, caller.id)
        return webhookRepository.findByProjectId(project.id).map { WebhookResponse.from(it) }
    }

    @Transactional
    fun update(projectKey: String, webhookId: UUID, req: UpdateWebhookRequest, caller: User): WebhookResponse {
        val project = projectService.requireAdmin(projectKey, caller.id)
        val hook = webhookRepository.findByIdAndProjectId(webhookId, project.id)
            ?: throw NotFoundException("Webhook not found: $webhookId")
        req.url?.let { ssrfValidator.validate(it); hook.url = it }
        req.events?.let { hook.events = it }
        req.enabled?.let { hook.enabled = it }
        return WebhookResponse.from(webhookRepository.save(hook))
    }

    @Transactional
    fun delete(projectKey: String, webhookId: UUID, caller: User) {
        val project = projectService.requireAdmin(projectKey, caller.id)
        val hook = webhookRepository.findByIdAndProjectId(webhookId, project.id)
            ?: throw NotFoundException("Webhook not found: $webhookId")
        webhookRepository.delete(hook)
    }

    @Transactional(readOnly = true)
    fun listDeliveries(projectKey: String, webhookId: UUID, caller: User, page: Int, size: Int): List<WebhookDeliveryResponse> {
        val project = projectService.requireMember(projectKey, caller.id)
        webhookRepository.findByIdAndProjectId(webhookId, project.id)
            ?: throw NotFoundException("Webhook not found: $webhookId")
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        return deliveryRepository.findByWebhookId(webhookId, pageable).map { WebhookDeliveryResponse.from(it) }
    }

    @Transactional
    fun testPing(projectKey: String, webhookId: UUID, caller: User): WebhookDeliveryResponse {
        val project = projectService.requireAdmin(projectKey, caller.id)
        val hook = webhookRepository.findByIdAndProjectId(webhookId, project.id)
            ?: throw NotFoundException("Webhook not found: $webhookId")
        val payload = objectMapper.writeValueAsString(
            mapOf("event" to "ping", "project" to projectKey, "timestamp" to Instant.now().toString())
        )
        val delivery = deliveryRepository.save(
            WebhookDelivery(webhookId = hook.id, eventType = "ping", payload = payload)
        )
        return WebhookDeliveryResponse.from(delivery)
    }

    fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun generateSecret(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
```

- [ ] **Step 9: Run WebhookService tests**

```
./gradlew test --tests "com.taskowolf.integrations.WebhookServiceTest"
```
Expected: both tests GREEN.

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/integrations/application/ \
        backend/src/main/kotlin/com/taskowolf/integrations/api/dto/ \
        backend/src/test/kotlin/com/taskowolf/integrations/
git commit -m "feat(integrations): add SsrfValidator, HmacSigner, WebhookService"
```

---

### Task 6: WebhookDispatcher + DeliveryRetryJob + DeliveryCleanupJob

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/integrations/application/WebhookDispatcher.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/integrations/application/DeliveryRetryJob.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/integrations/application/DeliveryCleanupJob.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/TaskWolfApplication.kt`

**Interfaces:**
- Consumes: All Spring `ApplicationEvent`s from `issues`, `sprints`, `comments`, `attachments` modules
- Produces: HTTP POSTs with `X-TaskWolf-Signature` header to configured webhook URLs

- [ ] **Step 1: Add @EnableAsync + @EnableScheduling to TaskWolfApplication**

Modify `backend/src/main/kotlin/com/taskowolf/TaskWolfApplication.kt`:
```kotlin
package com.taskowolf

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableJpaAuditing
@EnableAsync
@EnableScheduling
class TaskWolfApplication

fun main(args: Array<String>) {
    runApplication<TaskWolfApplication>(*args)
}
```

- [ ] **Step 2: Create WebhookDispatcher**

Create `backend/src/main/kotlin/com/taskowolf/integrations/application/WebhookDispatcher.kt`:
```kotlin
package com.taskowolf.integrations.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.attachments.domain.events.AttachmentAddedEvent
import com.taskowolf.comments.domain.events.CommentCreatedEvent
import com.taskowolf.integrations.domain.WebhookDelivery
import com.taskowolf.integrations.domain.WebhookEventType
import com.taskowolf.integrations.infrastructure.WebhookDeliveryRepository
import com.taskowolf.integrations.infrastructure.WebhookRepository
import com.taskowolf.issues.domain.events.IssueCreatedEvent
import com.taskowolf.issues.domain.events.IssueFieldChangedEvent
import com.taskowolf.issues.domain.events.IssueStatusChangedEvent
import com.taskowolf.sprints.domain.events.SprintCompletedEvent
import com.taskowolf.sprints.domain.events.SprintStartedEvent
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestTemplate
import java.time.Instant
import java.util.UUID

@Component
class WebhookDispatcher(
    private val webhookRepository: WebhookRepository,
    private val deliveryRepository: WebhookDeliveryRepository,
    private val hmacSigner: HmacSigner,
    private val webhookService: WebhookService,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(WebhookDispatcher::class.java)
    private val restTemplate = RestTemplate()

    @EventListener
    fun onIssueCreated(event: IssueCreatedEvent) =
        dispatch(WebhookEventType.ISSUE_CREATED, event.issue.project.id,
            mapOf("issueId" to event.issue.id, "issueKey" to event.issue.key, "title" to event.issue.title))

    @EventListener
    fun onStatusChanged(event: IssueStatusChangedEvent) =
        dispatch(WebhookEventType.ISSUE_STATUS_CHANGED, event.issue.project.id,
            mapOf("issueId" to event.issue.id, "issueKey" to event.issue.key,
                  "newStatus" to event.newStatus.name, "oldStatus" to event.oldStatus.name))

    @EventListener
    fun onFieldChanged(event: IssueFieldChangedEvent) {
        val eventType = if (event.field == "assignee") WebhookEventType.ISSUE_ASSIGNED
                        else WebhookEventType.ISSUE_UPDATED
        dispatch(eventType, event.issue.project.id,
            mapOf("issueId" to event.issue.id, "issueKey" to event.issue.key,
                  "field" to event.field, "newValue" to (event.newValue ?: "")))
    }

    @EventListener
    fun onSprintStarted(event: SprintStartedEvent) =
        dispatch(WebhookEventType.SPRINT_STARTED, event.sprint.project.id,
            mapOf("sprintId" to event.sprint.id, "sprintName" to event.sprint.name))

    @EventListener
    fun onSprintCompleted(event: SprintCompletedEvent) =
        dispatch(WebhookEventType.SPRINT_COMPLETED, event.sprint.project.id,
            mapOf("sprintId" to event.sprint.id, "sprintName" to event.sprint.name))

    @EventListener
    fun onCommentCreated(event: CommentCreatedEvent) =
        dispatch(WebhookEventType.COMMENT_CREATED, event.issue.project.id,
            mapOf("issueId" to event.issue.id, "commentId" to event.comment.id))

    @EventListener
    fun onAttachmentAdded(event: AttachmentAddedEvent) =
        dispatch(WebhookEventType.ATTACHMENT_ADDED, event.projectId,
            mapOf("attachmentId" to event.attachment.id, "filename" to event.attachment.filename))

    private fun dispatch(eventType: String, projectId: UUID, data: Map<String, Any?>) {
        val hooks = webhookRepository.findByProjectIdAndEnabled(projectId, true)
            .filter { it.events.contains(eventType) }
        if (hooks.isEmpty()) return

        val payload = objectMapper.writeValueAsString(
            mapOf("event" to eventType, "projectId" to projectId, "data" to data,
                  "timestamp" to Instant.now().toString())
        )
        for (hook in hooks) {
            val delivery = deliveryRepository.save(
                WebhookDelivery(webhookId = hook.id, eventType = eventType, payload = payload,
                    nextRetryAt = Instant.now())
            )
            sendAsync(delivery.id)
        }
    }

    @Async
    @Transactional
    fun sendAsync(deliveryId: UUID) {
        val delivery = deliveryRepository.findById(deliveryId).orElse(null) ?: return
        val hook = webhookRepository.findById(delivery.webhookId).orElse(null) ?: return
        send(delivery, hook.url, webhookService.sha256(hook.secretHash))
    }

    fun send(delivery: WebhookDelivery, url: String, secretHash: String) {
        delivery.attemptCount++
        try {
            val signature = hmacSigner.sign(delivery.payload, secretHash)
            val headers = HttpHeaders().apply {
                set("Content-Type", "application/json")
                set("X-TaskWolf-Signature", signature)
                set("X-TaskWolf-Event", delivery.eventType)
            }
            val response = restTemplate.postForEntity(url, HttpEntity(delivery.payload, headers), String::class.java)
            delivery.responseStatus = response.statusCodeValue
            delivery.responseBody = response.body?.take(4096)
            delivery.deliveredAt = Instant.now()
            delivery.nextRetryAt = null
        } catch (e: Exception) {
            log.warn("Webhook delivery {} failed (attempt {}): {}", delivery.id, delivery.attemptCount, e.message)
            delivery.responseBody = e.message?.take(4096)
            if (delivery.attemptCount < 3) {
                delivery.nextRetryAt = Instant.now().plusSeconds(retryDelaySeconds(delivery.attemptCount))
            }
        }
        deliveryRepository.save(delivery)
    }

    private fun retryDelaySeconds(attempt: Int): Long = when (attempt) {
        1 -> 60L
        2 -> 300L
        else -> 1800L
    }
}
```

- [ ] **Step 3: Create DeliveryRetryJob**

Create `backend/src/main/kotlin/com/taskowolf/integrations/application/DeliveryRetryJob.kt`:
```kotlin
package com.taskowolf.integrations.application

import com.taskowolf.integrations.infrastructure.WebhookDeliveryRepository
import com.taskowolf.integrations.infrastructure.WebhookRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class DeliveryRetryJob(
    private val deliveryRepository: WebhookDeliveryRepository,
    private val webhookRepository: WebhookRepository,
    private val dispatcher: WebhookDispatcher
) {
    private val log = LoggerFactory.getLogger(DeliveryRetryJob::class.java)

    @Scheduled(fixedDelay = 30_000)
    fun retryPending() {
        val pending = deliveryRepository.findPendingRetries(Instant.now())
        if (pending.isNotEmpty()) {
            log.info("Retrying {} webhook deliveries", pending.size)
        }
        for (delivery in pending) {
            val hook = webhookRepository.findById(delivery.webhookId).orElse(null) ?: continue
            dispatcher.send(delivery, hook.url, hook.secretHash)
        }
    }
}
```

- [ ] **Step 4: Create DeliveryCleanupJob**

Create `backend/src/main/kotlin/com/taskowolf/integrations/application/DeliveryCleanupJob.kt`:
```kotlin
package com.taskowolf.integrations.application

import com.taskowolf.integrations.infrastructure.WebhookDeliveryRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component
class DeliveryCleanupJob(
    private val deliveryRepository: WebhookDeliveryRepository
) {
    private val log = LoggerFactory.getLogger(DeliveryCleanupJob::class.java)

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    fun cleanupOldDeliveries() {
        val cutoff = Instant.now().minus(30, ChronoUnit.DAYS)
        val deleted = deliveryRepository.deleteOlderThan(cutoff)
        log.info("Cleaned up {} webhook deliveries older than 30 days", deleted)
    }
}
```

- [ ] **Step 5: Check for missing event types — verify SprintStartedEvent and SprintCompletedEvent**

```
./gradlew compileKotlin
```
If `SprintStartedEvent` or `SprintCompletedEvent` don't exist yet, check `backend/src/main/kotlin/com/taskowolf/sprints/domain/events/`. If missing, create them following the pattern of `IssueCreatedEvent`. Similarly verify `AttachmentAddedEvent` has a `projectId` field; if not, add it or adjust the listener to read `event.attachment.issue.project.id`.

- [ ] **Step 6: Verify compilation**

```
./gradlew compileKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Run all tests**

```
./gradlew test
```
Expected: all tests GREEN.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/TaskWolfApplication.kt \
        backend/src/main/kotlin/com/taskowolf/integrations/application/WebhookDispatcher.kt \
        backend/src/main/kotlin/com/taskowolf/integrations/application/DeliveryRetryJob.kt \
        backend/src/main/kotlin/com/taskowolf/integrations/application/DeliveryCleanupJob.kt
git commit -m "feat(integrations): add WebhookDispatcher, retry job, cleanup job"
```

---

### Task 7: WebhookController + integration test + Frontend WebhooksPage

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/integrations/api/WebhookController.kt`
- Create: `backend/src/test/kotlin/com/taskowolf/integrations/WebhookControllerTest.kt`
- Create: `frontend/src/hooks/useWebhooks.ts`
- Create: `frontend/src/pages/settings/WebhooksPage.tsx`
- Modify: `frontend/src/app/router.tsx`

**Interfaces:**
- Produces: REST endpoints `GET/POST/PUT/DELETE /api/v1/projects/{key}/webhooks`, delivery log, test-ping

- [ ] **Step 1: Write failing integration test**

Create `backend/src/test/kotlin/com/taskowolf/integrations/WebhookControllerTest.kt`:
```kotlin
package com.taskowolf.integrations

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class WebhookControllerTest : IntegrationTestBase() {

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
    fun `POST webhook creates webhook and returns secret once`() {
        val token = registerAndLogin("wh1@test.com")
        createProject(token, "WH1")

        val result = mockMvc.perform(
            post("/api/v1/projects/WH1/webhooks")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"url":"https://hooks.example.com/payload","events":["issue.created"]}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.webhook.url").value("https://hooks.example.com/payload"))
            .andExpect(jsonPath("$.plaintextSecret").isString)
            .andReturn()

        val secret = objectMapper.readTree(result.response.contentAsString).get("plaintextSecret").asText()
        assert(secret.isNotBlank())
    }

    @Test
    fun `GET webhooks lists webhooks`() {
        val token = registerAndLogin("wh2@test.com")
        createProject(token, "WH2")
        mockMvc.perform(
            post("/api/v1/projects/WH2/webhooks")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"url":"https://hooks.example.com/p","events":["sprint.started"]}""")
        ).andExpect(status().isCreated)

        mockMvc.perform(
            get("/api/v1/projects/WH2/webhooks")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].url").value("https://hooks.example.com/p"))
            .andExpect(jsonPath("$[0].events[0]").value("sprint.started"))
    }

    @Test
    fun `POST webhook rejects private IP`() {
        val token = registerAndLogin("wh3@test.com")
        createProject(token, "WH3")

        mockMvc.perform(
            post("/api/v1/projects/WH3/webhooks")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"url":"http://192.168.1.1/hook","events":["issue.created"]}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `DELETE webhook removes it`() {
        val token = registerAndLogin("wh4@test.com")
        createProject(token, "WH4")
        val createResult = mockMvc.perform(
            post("/api/v1/projects/WH4/webhooks")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"url":"https://hooks.example.com/del","events":["issue.created"]}""")
        ).andReturn()
        val webhookId = objectMapper.readTree(createResult.response.contentAsString)
            .get("webhook").get("id").asText()

        mockMvc.perform(
            delete("/api/v1/projects/WH4/webhooks/$webhookId")
                .header("Authorization", "Bearer $token")
        ).andExpect(status().isNoContent)

        mockMvc.perform(
            get("/api/v1/projects/WH4/webhooks")
                .header("Authorization", "Bearer $token")
        ).andExpect(jsonPath("$.length()").value(0))
    }
}
```

- [ ] **Step 2: Run test to confirm failure**

```
./gradlew test --tests "com.taskowolf.integrations.WebhookControllerTest"
```
Expected: 404 on all endpoints — controller not registered yet.

- [ ] **Step 3: Create WebhookController**

Create `backend/src/main/kotlin/com/taskowolf/integrations/api/WebhookController.kt`:
```kotlin
package com.taskowolf.integrations.api

import com.taskowolf.auth.domain.User
import com.taskowolf.integrations.api.dto.CreateWebhookRequest
import com.taskowolf.integrations.api.dto.UpdateWebhookRequest
import com.taskowolf.integrations.application.WebhookService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/projects/{key}/webhooks")
class WebhookController(private val webhookService: WebhookService) {

    @GetMapping
    fun list(@PathVariable key: String, @AuthenticationPrincipal user: User) =
        webhookService.list(key, user)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @PathVariable key: String,
        @Valid @RequestBody req: CreateWebhookRequest,
        @AuthenticationPrincipal user: User
    ): Map<String, Any> {
        val result = webhookService.create(key, req, user)
        return mapOf("webhook" to result.webhook, "plaintextSecret" to result.plaintextSecret)
    }

    @PutMapping("/{webhookId}")
    fun update(
        @PathVariable key: String,
        @PathVariable webhookId: UUID,
        @RequestBody req: UpdateWebhookRequest,
        @AuthenticationPrincipal user: User
    ) = webhookService.update(key, webhookId, req, user)

    @DeleteMapping("/{webhookId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable key: String,
        @PathVariable webhookId: UUID,
        @AuthenticationPrincipal user: User
    ) = webhookService.delete(key, webhookId, user)

    @GetMapping("/{webhookId}/deliveries")
    fun deliveries(
        @PathVariable key: String,
        @PathVariable webhookId: UUID,
        @AuthenticationPrincipal user: User,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ) = webhookService.listDeliveries(key, webhookId, user, page, size)

    @PostMapping("/{webhookId}/test")
    @ResponseStatus(HttpStatus.CREATED)
    fun testPing(
        @PathVariable key: String,
        @PathVariable webhookId: UUID,
        @AuthenticationPrincipal user: User
    ) = webhookService.testPing(key, webhookId, user)
}
```

- [ ] **Step 4: Run integration tests**

```
./gradlew test --tests "com.taskowolf.integrations.WebhookControllerTest"
```
Expected: all 4 tests GREEN.

- [ ] **Step 5: Create frontend hook**

Create `frontend/src/hooks/useWebhooks.ts`:
```typescript
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/api/client'

export interface WebhookItem {
  id: string
  url: string
  events: string[]
  enabled: boolean
  createdAt: string | null
}

export interface WebhookDelivery {
  id: string
  webhookId: string
  eventType: string
  payload: string
  responseStatus: number | null
  responseBody: string | null
  attemptCount: number
  deliveredAt: string | null
  createdAt: string | null
}

export interface CreateWebhookResult {
  webhook: WebhookItem
  plaintextSecret: string
}

export const ALL_WEBHOOK_EVENTS = [
  'issue.created', 'issue.updated', 'issue.status_changed',
  'issue.assigned', 'issue.deleted', 'sprint.started',
  'sprint.completed', 'comment.created', 'attachment.added',
]

export function useWebhooks(projectKey: string) {
  return useQuery<WebhookItem[]>({
    queryKey: ['webhooks', projectKey],
    queryFn: () => apiClient.get(`/projects/${projectKey}/webhooks`).then(r => r.data),
  })
}

export function useCreateWebhook(projectKey: string) {
  const qc = useQueryClient()
  return useMutation<CreateWebhookResult, Error, { url: string; events: string[]; secret?: string }>({
    mutationFn: body => apiClient.post(`/projects/${projectKey}/webhooks`, body).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['webhooks', projectKey] }),
  })
}

export function useDeleteWebhook(projectKey: string) {
  const qc = useQueryClient()
  return useMutation<void, Error, string>({
    mutationFn: id => apiClient.delete(`/projects/${projectKey}/webhooks/${id}`).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['webhooks', projectKey] }),
  })
}

export function useWebhookDeliveries(projectKey: string, webhookId: string | null) {
  return useQuery<WebhookDelivery[]>({
    queryKey: ['webhook-deliveries', projectKey, webhookId],
    queryFn: () => apiClient.get(`/projects/${projectKey}/webhooks/${webhookId}/deliveries`).then(r => r.data),
    enabled: !!webhookId,
  })
}

export function useTestPing(projectKey: string) {
  const qc = useQueryClient()
  return useMutation<WebhookDelivery, Error, string>({
    mutationFn: webhookId => apiClient.post(`/projects/${projectKey}/webhooks/${webhookId}/test`, {}).then(r => r.data),
    onSuccess: (_, webhookId) => qc.invalidateQueries({ queryKey: ['webhook-deliveries', projectKey, webhookId] }),
  })
}
```

- [ ] **Step 6: Create WebhooksPage**

Create `frontend/src/pages/settings/WebhooksPage.tsx`:
```typescript
import { useState } from 'react'
import { useParams } from 'react-router-dom'
import {
  useWebhooks, useCreateWebhook, useDeleteWebhook,
  useWebhookDeliveries, useTestPing, ALL_WEBHOOK_EVENTS,
} from '@/hooks/useWebhooks'
import type { CreateWebhookResult } from '@/hooks/useWebhooks'

export function WebhooksPage() {
  const { key } = useParams<{ key: string }>()
  const projectKey = key!
  const { data: webhooks = [], isLoading } = useWebhooks(projectKey)
  const createWebhook = useCreateWebhook(projectKey)
  const deleteWebhook = useDeleteWebhook(projectKey)
  const testPing = useTestPing(projectKey)

  const [showCreate, setShowCreate] = useState(false)
  const [url, setUrl] = useState('')
  const [selectedEvents, setSelectedEvents] = useState<string[]>([])
  const [newSecret, setNewSecret] = useState<CreateWebhookResult | null>(null)
  const [selectedWebhookId, setSelectedWebhookId] = useState<string | null>(null)
  const [copiedSecret, setCopiedSecret] = useState(false)

  const { data: deliveries = [] } = useWebhookDeliveries(projectKey, selectedWebhookId)

  function toggleEvent(e: string) {
    setSelectedEvents(prev => prev.includes(e) ? prev.filter(x => x !== e) : [...prev, e])
  }

  async function handleCreate() {
    if (!url.trim() || selectedEvents.length === 0) return
    try {
      const result = await createWebhook.mutateAsync({ url, events: selectedEvents })
      setNewSecret(result)
      setUrl(''); setSelectedEvents([]); setShowCreate(false)
    } catch (e: any) {
      alert(e.response?.data?.message || 'Failed to create webhook')
    }
  }

  if (isLoading) return <div className="text-gray-400">Loading…</div>

  return (
    <div className="max-w-3xl">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">Webhooks</h1>
        <button onClick={() => setShowCreate(true)}
          className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 rounded text-sm font-medium">
          Add Webhook
        </button>
      </div>

      {newSecret && (
        <div className="mb-6 p-4 bg-yellow-900/30 border border-yellow-600 rounded">
          <p className="text-yellow-400 text-sm font-semibold mb-2">
            ⚠ Copy your webhook secret now — it will not be shown again.
          </p>
          <div className="flex items-center gap-2">
            <code className="flex-1 bg-gray-900 px-3 py-2 rounded text-sm text-green-400 break-all">
              {newSecret.plaintextSecret}
            </code>
            <button onClick={() => { navigator.clipboard.writeText(newSecret.plaintextSecret); setCopiedSecret(true); setTimeout(() => setCopiedSecret(false), 2000) }}
              className="px-3 py-2 bg-gray-700 hover:bg-gray-600 rounded text-sm">
              {copiedSecret ? 'Copied!' : 'Copy'}
            </button>
          </div>
          <button onClick={() => setNewSecret(null)} className="mt-2 text-xs text-gray-400 hover:text-white">Dismiss</button>
        </div>
      )}

      {showCreate && (
        <div className="mb-6 p-4 bg-gray-800 rounded border border-gray-700">
          <h2 className="text-sm font-semibold mb-3">New Webhook</h2>
          <input type="text" placeholder="https://hooks.example.com/payload"
            value={url} onChange={e => setUrl(e.target.value)}
            className="w-full px-3 py-2 bg-gray-900 rounded border border-gray-600 text-sm mb-3" />
          <p className="text-xs text-gray-400 mb-2">Events:</p>
          <div className="grid grid-cols-3 gap-1 mb-3">
            {ALL_WEBHOOK_EVENTS.map(ev => (
              <label key={ev} className="flex items-center gap-2 text-xs cursor-pointer">
                <input type="checkbox" checked={selectedEvents.includes(ev)} onChange={() => toggleEvent(ev)} />
                <span className="text-gray-300">{ev}</span>
              </label>
            ))}
          </div>
          <div className="flex gap-2">
            <button onClick={handleCreate} disabled={createWebhook.isPending}
              className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 rounded text-sm">
              {createWebhook.isPending ? 'Creating…' : 'Create'}
            </button>
            <button onClick={() => setShowCreate(false)}
              className="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded text-sm">Cancel</button>
          </div>
        </div>
      )}

      <div className="space-y-3">
        {webhooks.map(wh => (
          <div key={wh.id} className="p-4 bg-gray-800 rounded border border-gray-700">
            <div className="flex items-start justify-between">
              <div>
                <code className="text-sm text-blue-400">{wh.url}</code>
                <div className="flex flex-wrap gap-1 mt-2">
                  {wh.events.map(ev => (
                    <span key={ev} className="px-2 py-0.5 bg-gray-700 rounded text-xs text-gray-300">{ev}</span>
                  ))}
                </div>
              </div>
              <div className="flex gap-2 ml-4 shrink-0">
                <button onClick={() => testPing.mutate(wh.id)}
                  className="px-3 py-1 bg-gray-700 hover:bg-gray-600 rounded text-xs">Ping</button>
                <button onClick={() => setSelectedWebhookId(selectedWebhookId === wh.id ? null : wh.id)}
                  className="px-3 py-1 bg-gray-700 hover:bg-gray-600 rounded text-xs">
                  {selectedWebhookId === wh.id ? 'Hide Log' : 'Log'}
                </button>
                <button onClick={() => deleteWebhook.mutate(wh.id)}
                  className="px-3 py-1 bg-red-900/40 hover:bg-red-800 text-red-400 rounded text-xs">Delete</button>
              </div>
            </div>

            {selectedWebhookId === wh.id && (
              <div className="mt-3 border-t border-gray-700 pt-3">
                <p className="text-xs text-gray-400 mb-2">Delivery Log</p>
                {deliveries.length === 0 ? (
                  <p className="text-xs text-gray-500">No deliveries yet.</p>
                ) : (
                  <div className="space-y-1">
                    {deliveries.map(d => (
                      <div key={d.id} className="flex items-center gap-3 text-xs">
                        <span className={d.responseStatus && d.responseStatus < 300 ? 'text-green-400' : 'text-red-400'}>
                          {d.responseStatus ?? '—'}
                        </span>
                        <span className="text-gray-400">{d.eventType}</span>
                        <span className="text-gray-500">
                          {d.createdAt ? new Date(d.createdAt).toLocaleString() : ''}
                        </span>
                        <span className="text-gray-500">attempt {d.attemptCount}</span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>
        ))}
        {webhooks.length === 0 && <p className="text-gray-400 text-sm">No webhooks configured.</p>}
      </div>
    </div>
  )
}
```

- [ ] **Step 7: Add route to router.tsx**

In `frontend/src/app/router.tsx`, add:
```typescript
import { WebhooksPage } from '@/pages/settings/WebhooksPage'
// In the children array:
{ path: '/p/:key/settings/webhooks', element: <WebhooksPage /> },
```

- [ ] **Step 8: Run full test suite**

```
./gradlew test
```
Expected: all tests GREEN.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/integrations/api/WebhookController.kt \
        backend/src/test/kotlin/com/taskowolf/integrations/WebhookControllerTest.kt \
        frontend/src/hooks/useWebhooks.ts \
        frontend/src/pages/settings/WebhooksPage.tsx \
        frontend/src/app/router.tsx
git commit -m "feat(integrations): add WebhookController, WebhooksPage"
```