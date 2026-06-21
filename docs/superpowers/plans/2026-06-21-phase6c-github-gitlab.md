# Phase 6c: GitHub/GitLab Auto-Linking — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** GitHub und GitLab können Commits und PRs automatisch mit TaskWolf-Issues verlinken. Admin konfiguriert pro Projekt eine Integration (Provider + Secret). TaskWolf empfängt Webhooks, verifiziert die HMAC-Signatur, parst Issue-Keys (z.B. `WOLF-42`) aus Commit-Messages und PR-Titles, und speichert `IssueRef`-Records. Die IssueDetailPage zeigt verlinkte Refs an.

**Architecture:** V15-Migration (`project_integrations` + `issue_refs`). `ProjectIntegrationService` verwaltet die Konfiguration (Secret gehasht gespeichert). `IncomingWebhookService` verifiziert HMAC und delegiert an `IssueKeyParser`. GitHub- und GitLab-Webhook-Endpoints sind aus dem JWT-Filter ausgenommen (HMAC statt JWT). `IssueController` wird erweitert: `IssueResponse` enthält ein `refs`-Array. Frontend: IntegrationsPage + IssueDetailPage-Erweiterung + AppLayout-Nav.

**Tech Stack:** Kotlin 2.x, Spring Boot 3.x, Spring Data JPA, Flyway, PostgreSQL 16, React 19, TypeScript, React Query

**Prerequisites:** Phase 6a (API Keys) und Phase 6b (Outgoing Webhooks) müssen abgeschlossen und gemergt sein (HmacSigner aus Phase 6b wird wiederverwendet).

## Global Constraints

- Entity IDs: UUID via `AuditableEntity`; SQL: `UUID NOT NULL DEFAULT gen_random_uuid()`
- Incoming-Webhook-URLs: `POST /api/v1/integrations/github/{projectKey}/webhook` und `.../gitlab/...` — enthält `{projectKey}` damit der Controller das Projekt sofort kennt
- HMAC-Verifikation: GitHub → `X-Hub-Signature-256`; GitLab → `X-Gitlab-Token` (pre-shared secret, kein HMAC)
- GitLab-Verifikation: GitLab sendet `X-Gitlab-Token: <plaintext-secret>` — Verifikation via SHA-256-Hash-Vergleich (kein HMAC-Header, Plaintext wird gehasht und verglichen)
- Secret-Storage: SHA-256-Hash, nur `sha256()`-Funktion aus `RefreshTokenService`-Pattern
- Incoming-Webhook-Endpoints sind in `SecurityConfig.permitAll()` einzutragen
- Regex für Issue-Key-Parser: `[A-Z][A-Z0-9]+-\d+` (mindestens 2 Buchstaben/Ziffern vor dem Bindestrich)
- Admin-Check: `projectService.requireAdmin(projectKey, caller.id)` für alle Konfigurations-Endpoints
- Test base: `IntegrationTestBase` (Testcontainers PostgreSQL). Unit tests: `@ExtendWith(MockitoExtension::class)`

---

## File Structure

**Create:**
- `backend/src/main/resources/db/migration/V15__integrations.sql`
- `backend/src/main/kotlin/com/taskowolf/integrations/domain/IntegrationProvider.kt`
- `backend/src/main/kotlin/com/taskowolf/integrations/domain/RefType.kt`
- `backend/src/main/kotlin/com/taskowolf/integrations/domain/ProjectIntegration.kt`
- `backend/src/main/kotlin/com/taskowolf/integrations/domain/IssueRef.kt`
- `backend/src/main/kotlin/com/taskowolf/integrations/infrastructure/ProjectIntegrationRepository.kt`
- `backend/src/main/kotlin/com/taskowolf/integrations/infrastructure/IssueRefRepository.kt`
- `backend/src/main/kotlin/com/taskowolf/integrations/application/IssueKeyParser.kt`
- `backend/src/main/kotlin/com/taskowolf/integrations/application/IncomingWebhookService.kt`
- `backend/src/main/kotlin/com/taskowolf/integrations/application/ProjectIntegrationService.kt`
- `backend/src/main/kotlin/com/taskowolf/integrations/api/dto/CreateProjectIntegrationRequest.kt`
- `backend/src/main/kotlin/com/taskowolf/integrations/api/dto/ProjectIntegrationResponse.kt`
- `backend/src/main/kotlin/com/taskowolf/integrations/api/dto/CreateProjectIntegrationResponse.kt`
- `backend/src/main/kotlin/com/taskowolf/integrations/api/dto/IssueRefResponse.kt`
- `backend/src/main/kotlin/com/taskowolf/integrations/api/ProjectIntegrationController.kt`
- `backend/src/main/kotlin/com/taskowolf/integrations/api/GitHubWebhookController.kt`
- `backend/src/main/kotlin/com/taskowolf/integrations/api/GitLabWebhookController.kt`
- `backend/src/test/kotlin/com/taskowolf/integrations/IssueKeyParserTest.kt`
- `backend/src/test/kotlin/com/taskowolf/integrations/GitHubWebhookControllerTest.kt`
- `frontend/src/hooks/useProjectIntegrations.ts`
- `frontend/src/pages/settings/IntegrationsPage.tsx`

**Modify:**
- `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/SecurityConfig.kt`
- `backend/src/main/kotlin/com/taskowolf/issues/api/IssueController.kt`
- `backend/src/main/kotlin/com/taskowolf/issues/api/dto/IssueResponse.kt`
- `frontend/src/app/router.tsx`
- `frontend/src/pages/issues/IssueDetailPage.tsx`

---

### Task 8: V15 migration + ProjectIntegration/IssueRef domain + IssueKeyParser

**Files:**
- Create: `backend/src/main/resources/db/migration/V15__integrations.sql`
- Create: `backend/src/main/kotlin/com/taskowolf/integrations/domain/IntegrationProvider.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/integrations/domain/RefType.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/integrations/domain/ProjectIntegration.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/integrations/domain/IssueRef.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/integrations/infrastructure/ProjectIntegrationRepository.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/integrations/infrastructure/IssueRefRepository.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/integrations/application/IssueKeyParser.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/integrations/IssueKeyParserTest.kt`

**Interfaces:**
- Produces: `IssueKeyParser.parseKeys(text): List<String>`, `ProjectIntegration`, `IssueRef`, `ProjectIntegrationRepository`, `IssueRefRepository`

- [ ] **Step 1: Write failing IssueKeyParser test**

Create `backend/src/test/kotlin/com/taskowolf/integrations/IssueKeyParserTest.kt`:
```kotlin
package com.taskowolf.integrations

import com.taskowolf.integrations.application.IssueKeyParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IssueKeyParserTest {
    private val parser = IssueKeyParser()

    @Test
    fun `parses single key from commit message`() {
        val keys = parser.parseKeys("fix: resolve WOLF-42 crash on login")
        assertEquals(listOf("WOLF-42"), keys)
    }

    @Test
    fun `parses multiple keys from PR title`() {
        val keys = parser.parseKeys("feat: implement WOLF-10 and WLF-23 feature")
        assertEquals(listOf("WOLF-10", "WLF-23"), keys)
    }

    @Test
    fun `returns empty list when no keys found`() {
        val keys = parser.parseKeys("fix typo in README")
        assertEquals(emptyList<String>(), keys)
    }

    @Test
    fun `ignores lowercase matches`() {
        val keys = parser.parseKeys("wolf-42 is not a key")
        assertEquals(emptyList<String>(), keys)
    }

    @Test
    fun `deduplicates repeated keys`() {
        val keys = parser.parseKeys("WOLF-1 and WOLF-1 again")
        assertEquals(listOf("WOLF-1"), keys)
    }

    @Test
    fun `parses key at start of string`() {
        val keys = parser.parseKeys("WOLF-99: fix bug")
        assertEquals(listOf("WOLF-99"), keys)
    }
}
```

- [ ] **Step 2: Run test to confirm failure**

```
./gradlew test --tests "com.taskowolf.integrations.IssueKeyParserTest"
```
Expected: compilation error — `IssueKeyParser` doesn't exist yet.

- [ ] **Step 3: Create the migration**

Create `backend/src/main/resources/db/migration/V15__integrations.sql`:
```sql
CREATE TABLE project_integrations (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    project_id          UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    provider            VARCHAR(20)  NOT NULL,
    webhook_secret_hash VARCHAR(64)  NOT NULL,
    repo_url            VARCHAR(2048),
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    UNIQUE (project_id, provider)
);

CREATE TABLE issue_refs (
    id          UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    issue_id    UUID         NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    provider    VARCHAR(20)  NOT NULL,
    ref_type    VARCHAR(20)  NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    url         VARCHAR(2048) NOT NULL,
    title       VARCHAR(1024),
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    UNIQUE (issue_id, provider, ref_type, external_id)
);

CREATE INDEX idx_issue_refs_issue ON issue_refs (issue_id);
```

- [ ] **Step 4: Create enums**

Create `backend/src/main/kotlin/com/taskowolf/integrations/domain/IntegrationProvider.kt`:
```kotlin
package com.taskowolf.integrations.domain

enum class IntegrationProvider { GITHUB, GITLAB }
```

Create `backend/src/main/kotlin/com/taskowolf/integrations/domain/RefType.kt`:
```kotlin
package com.taskowolf.integrations.domain

enum class RefType { COMMIT, PR, BRANCH }
```

- [ ] **Step 5: Create ProjectIntegration entity**

Create `backend/src/main/kotlin/com/taskowolf/integrations/domain/ProjectIntegration.kt`:
```kotlin
package com.taskowolf.integrations.domain

import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "project_integrations",
    uniqueConstraints = [UniqueConstraint(columnNames = ["project_id", "provider"])])
class ProjectIntegration(
    @Column(name = "project_id", nullable = false)
    val projectId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val provider: IntegrationProvider,

    @Column(nullable = false)
    var webhookSecretHash: String,

    @Column(length = 2048)
    var repoUrl: String? = null
) : AuditableEntity()
```

- [ ] **Step 6: Create IssueRef entity**

Create `backend/src/main/kotlin/com/taskowolf/integrations/domain/IssueRef.kt`:
```kotlin
package com.taskowolf.integrations.domain

import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "issue_refs",
    uniqueConstraints = [UniqueConstraint(columnNames = ["issue_id", "provider", "ref_type", "external_id"])])
class IssueRef(
    @Column(name = "issue_id", nullable = false)
    val issueId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val provider: IntegrationProvider,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val refType: RefType,

    @Column(nullable = false)
    val externalId: String,

    @Column(nullable = false, length = 2048)
    val url: String,

    @Column(length = 1024)
    val title: String? = null
) : AuditableEntity()
```

- [ ] **Step 7: Create repositories**

Create `backend/src/main/kotlin/com/taskowolf/integrations/infrastructure/ProjectIntegrationRepository.kt`:
```kotlin
package com.taskowolf.integrations.infrastructure

import com.taskowolf.integrations.domain.IntegrationProvider
import com.taskowolf.integrations.domain.ProjectIntegration
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ProjectIntegrationRepository : JpaRepository<ProjectIntegration, UUID> {
    fun findByProjectId(projectId: UUID): List<ProjectIntegration>
    fun findByProjectIdAndProvider(projectId: UUID, provider: IntegrationProvider): ProjectIntegration?
    fun findByIdAndProjectId(id: UUID, projectId: UUID): ProjectIntegration?
}
```

Create `backend/src/main/kotlin/com/taskowolf/integrations/infrastructure/IssueRefRepository.kt`:
```kotlin
package com.taskowolf.integrations.infrastructure

import com.taskowolf.integrations.domain.IssueRef
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface IssueRefRepository : JpaRepository<IssueRef, UUID> {
    fun findByIssueId(issueId: UUID): List<IssueRef>
}
```

- [ ] **Step 8: Create IssueKeyParser**

Create `backend/src/main/kotlin/com/taskowolf/integrations/application/IssueKeyParser.kt`:
```kotlin
package com.taskowolf.integrations.application

import org.springframework.stereotype.Component

@Component
class IssueKeyParser {
    private val pattern = Regex("""\b([A-Z][A-Z0-9]+-\d+)\b""")

    fun parseKeys(text: String): List<String> =
        pattern.findAll(text).map { it.groupValues[1] }.distinct().toList()
}
```

- [ ] **Step 9: Run IssueKeyParser tests**

```
./gradlew test --tests "com.taskowolf.integrations.IssueKeyParserTest"
```
Expected: all 6 tests GREEN.

- [ ] **Step 10: Verify compilation**

```
./gradlew compileKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 11: Commit**

```bash
git add backend/src/main/resources/db/migration/V15__integrations.sql \
        backend/src/main/kotlin/com/taskowolf/integrations/domain/IntegrationProvider.kt \
        backend/src/main/kotlin/com/taskowolf/integrations/domain/RefType.kt \
        backend/src/main/kotlin/com/taskowolf/integrations/domain/ProjectIntegration.kt \
        backend/src/main/kotlin/com/taskowolf/integrations/domain/IssueRef.kt \
        backend/src/main/kotlin/com/taskowolf/integrations/infrastructure/ProjectIntegrationRepository.kt \
        backend/src/main/kotlin/com/taskowolf/integrations/infrastructure/IssueRefRepository.kt \
        backend/src/main/kotlin/com/taskowolf/integrations/application/IssueKeyParser.kt \
        backend/src/test/kotlin/com/taskowolf/integrations/IssueKeyParserTest.kt
git commit -m "feat(integrations): add V15 migration, ProjectIntegration, IssueRef, IssueKeyParser"
```

---

### Task 9: ProjectIntegrationService + IncomingWebhookService + controllers + integration test

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/integrations/application/ProjectIntegrationService.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/integrations/application/IncomingWebhookService.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/integrations/api/dto/CreateProjectIntegrationRequest.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/integrations/api/dto/ProjectIntegrationResponse.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/integrations/api/dto/CreateProjectIntegrationResponse.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/integrations/api/ProjectIntegrationController.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/integrations/api/GitHubWebhookController.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/integrations/api/GitLabWebhookController.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/SecurityConfig.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/integrations/GitHubWebhookControllerTest.kt`

**Interfaces:**
- Consumes: `IssueKeyParser.parseKeys()`, `HmacSigner.verify()`, `IssueRefRepository`, `ProjectIntegrationRepository`
- Produces: `POST/GET/DELETE /api/v1/projects/{key}/integrations`, `POST /api/v1/integrations/github/{projectKey}/webhook`, `POST /api/v1/integrations/gitlab/{projectKey}/webhook`

- [ ] **Step 1: Create DTOs**

Create `backend/src/main/kotlin/com/taskowolf/integrations/api/dto/CreateProjectIntegrationRequest.kt`:
```kotlin
package com.taskowolf.integrations.api.dto

data class CreateProjectIntegrationRequest(
    val provider: String,
    val repoUrl: String? = null
)
```

Create `backend/src/main/kotlin/com/taskowolf/integrations/api/dto/ProjectIntegrationResponse.kt`:
```kotlin
package com.taskowolf.integrations.api.dto

import com.taskowolf.integrations.domain.ProjectIntegration
import java.time.Instant
import java.util.UUID

data class ProjectIntegrationResponse(
    val id: UUID,
    val provider: String,
    val repoUrl: String?,
    val createdAt: Instant?
) {
    companion object {
        fun from(p: ProjectIntegration) =
            ProjectIntegrationResponse(p.id, p.provider.name, p.repoUrl, p.createdAt)
    }
}
```

Create `backend/src/main/kotlin/com/taskowolf/integrations/api/dto/CreateProjectIntegrationResponse.kt`:
```kotlin
package com.taskowolf.integrations.api.dto

import java.util.UUID

data class CreateProjectIntegrationResponse(
    val id: UUID,
    val provider: String,
    val webhookUrl: String,
    val plaintextSecret: String,
    val repoUrl: String?
)
```

Create `backend/src/main/kotlin/com/taskowolf/integrations/api/dto/IssueRefResponse.kt`:
```kotlin
package com.taskowolf.integrations.api.dto

import com.taskowolf.integrations.domain.IssueRef
import java.time.Instant
import java.util.UUID

data class IssueRefResponse(
    val id: UUID,
    val provider: String,
    val refType: String,
    val externalId: String,
    val url: String,
    val title: String?,
    val createdAt: Instant?
) {
    companion object {
        fun from(r: IssueRef) = IssueRefResponse(
            r.id, r.provider.name, r.refType.name, r.externalId, r.url, r.title, r.createdAt
        )
    }
}
```

- [ ] **Step 2: Create ProjectIntegrationService**

Create `backend/src/main/kotlin/com/taskowolf/integrations/application/ProjectIntegrationService.kt`:
```kotlin
package com.taskowolf.integrations.application

import com.taskowolf.auth.domain.User
import com.taskowolf.core.infrastructure.ConflictException
import com.taskowolf.core.infrastructure.NotFoundException
import com.taskowolf.integrations.api.dto.CreateProjectIntegrationRequest
import com.taskowolf.integrations.api.dto.CreateProjectIntegrationResponse
import com.taskowolf.integrations.api.dto.ProjectIntegrationResponse
import com.taskowolf.integrations.domain.IntegrationProvider
import com.taskowolf.integrations.domain.ProjectIntegration
import com.taskowolf.integrations.infrastructure.ProjectIntegrationRepository
import com.taskowolf.projects.application.ProjectService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

@Service
class ProjectIntegrationService(
    private val integrationRepository: ProjectIntegrationRepository,
    private val projectService: ProjectService,
    @Value("\${taskowolf.base-url:http://localhost:8080}") private val baseUrl: String
) {
    @Transactional
    fun create(projectKey: String, req: CreateProjectIntegrationRequest, caller: User): CreateProjectIntegrationResponse {
        val project = projectService.requireAdmin(projectKey, caller.id)
        val provider = try { IntegrationProvider.valueOf(req.provider.uppercase()) }
                       catch (e: IllegalArgumentException) { throw IllegalArgumentException("Unknown provider: ${req.provider}") }
        if (integrationRepository.findByProjectIdAndProvider(project.id, provider) != null) {
            throw ConflictException("Integration already exists for $provider in project $projectKey")
        }
        val plainSecret = generateSecret()
        val integration = integrationRepository.save(
            ProjectIntegration(
                projectId = project.id, provider = provider,
                webhookSecretHash = sha256(plainSecret), repoUrl = req.repoUrl
            )
        )
        val providerPath = provider.name.lowercase()
        val webhookUrl = "$baseUrl/api/v1/integrations/$providerPath/$projectKey/webhook"
        return CreateProjectIntegrationResponse(
            integration.id, provider.name, webhookUrl, plainSecret, req.repoUrl
        )
    }

    @Transactional(readOnly = true)
    fun list(projectKey: String, caller: User): List<ProjectIntegrationResponse> {
        val project = projectService.requireMember(projectKey, caller.id)
        return integrationRepository.findByProjectId(project.id).map { ProjectIntegrationResponse.from(it) }
    }

    @Transactional
    fun delete(projectKey: String, integrationId: UUID, caller: User) {
        val project = projectService.requireAdmin(projectKey, caller.id)
        val integration = integrationRepository.findByIdAndProjectId(integrationId, project.id)
            ?: throw NotFoundException("Integration not found: $integrationId")
        integrationRepository.delete(integration)
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

- [ ] **Step 3: Create IncomingWebhookService**

Create `backend/src/main/kotlin/com/taskowolf/integrations/application/IncomingWebhookService.kt`:
```kotlin
package com.taskowolf.integrations.application

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.integrations.domain.IntegrationProvider
import com.taskowolf.integrations.domain.IssueRef
import com.taskowolf.integrations.domain.RefType
import com.taskowolf.integrations.infrastructure.IssueRefRepository
import com.taskowolf.integrations.infrastructure.ProjectIntegrationRepository
import com.taskowolf.issues.infrastructure.IssueRepository
import com.taskowolf.projects.infrastructure.ProjectRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class IncomingWebhookService(
    private val integrationRepository: ProjectIntegrationRepository,
    private val issueRefRepository: IssueRefRepository,
    private val projectRepository: ProjectRepository,
    private val issueRepository: IssueRepository,
    private val issueKeyParser: IssueKeyParser,
    private val hmacSigner: HmacSigner,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(IncomingWebhookService::class.java)

    @Transactional
    fun handleGitHub(projectKey: String, payload: String, signatureHeader: String?) {
        val project = projectRepository.findByKey(projectKey) ?: return
        val integration = integrationRepository.findByProjectIdAndProvider(project.id, IntegrationProvider.GITHUB) ?: return
        if (!verifyGitHubSignature(payload, integration.webhookSecretHash, signatureHeader)) {
            log.warn("GitHub webhook signature mismatch for project {}", projectKey)
            throw SecurityException("Invalid GitHub webhook signature")
        }
        val node = objectMapper.readTree(payload)
        processGitHubPayload(node, project.id, projectKey)
    }

    @Transactional
    fun handleGitLab(projectKey: String, payload: String, tokenHeader: String?) {
        val project = projectRepository.findByKey(projectKey) ?: return
        val integration = integrationRepository.findByProjectIdAndProvider(project.id, IntegrationProvider.GITLAB) ?: return
        val expectedHash = integration.webhookSecretHash
        val actualHash = sha256(tokenHeader ?: "")
        if (expectedHash != actualHash) {
            log.warn("GitLab webhook token mismatch for project {}", projectKey)
            throw SecurityException("Invalid GitLab webhook token")
        }
        val node = objectMapper.readTree(payload)
        processGitLabPayload(node, project.id, projectKey)
    }

    private fun verifyGitHubSignature(payload: String, secretHash: String, signatureHeader: String?): Boolean {
        if (signatureHeader == null) return false
        return hmacSigner.verify(payload, secretHash, signatureHeader)
    }

    private fun processGitHubPayload(node: JsonNode, projectId: java.util.UUID, projectKey: String) {
        val eventType = detectGitHubEventType(node)
        when (eventType) {
            "push" -> {
                val commits = node.path("commits")
                for (commit in commits) {
                    val sha = commit.path("id").asText()
                    val message = commit.path("message").asText()
                    val url = commit.path("url").asText()
                    linkKeys(issueKeyParser.parseKeys(message), projectKey, IntegrationProvider.GITHUB,
                        RefType.COMMIT, sha, url, message.take(100))
                }
            }
            "pull_request" -> {
                val pr = node.path("pull_request")
                val number = node.path("number").asText()
                val title = pr.path("title").asText()
                val url = pr.path("html_url").asText()
                val texts = listOf(title, pr.path("body").asText(""))
                val keys = texts.flatMap { issueKeyParser.parseKeys(it) }.distinct()
                linkKeys(keys, projectKey, IntegrationProvider.GITHUB, RefType.PR, number, url, title)
            }
        }
    }

    private fun processGitLabPayload(node: JsonNode, projectId: java.util.UUID, projectKey: String) {
        when (node.path("object_kind").asText()) {
            "push" -> {
                val commits = node.path("commits")
                for (commit in commits) {
                    val sha = commit.path("id").asText()
                    val message = commit.path("message").asText()
                    val url = commit.path("url").asText()
                    linkKeys(issueKeyParser.parseKeys(message), projectKey, IntegrationProvider.GITLAB,
                        RefType.COMMIT, sha, url, message.take(100))
                }
            }
            "merge_request" -> {
                val mr = node.path("object_attributes")
                val iid = mr.path("iid").asText()
                val title = mr.path("title").asText()
                val url = mr.path("url").asText()
                val keys = issueKeyParser.parseKeys(title) + issueKeyParser.parseKeys(mr.path("description").asText(""))
                linkKeys(keys.distinct(), projectKey, IntegrationProvider.GITLAB, RefType.PR, iid, url, title)
            }
        }
    }

    private fun detectGitHubEventType(node: JsonNode): String =
        if (node.has("pull_request")) "pull_request" else "push"

    private fun linkKeys(
        keys: List<String>, projectKey: String,
        provider: IntegrationProvider, refType: RefType,
        externalId: String, url: String, title: String?
    ) {
        for (key in keys) {
            if (!key.startsWith("$projectKey-")) continue
            val issue = issueRepository.findByKey(key) ?: continue
            try {
                issueRefRepository.save(
                    IssueRef(issueId = issue.id, provider = provider, refType = refType,
                        externalId = externalId, url = url, title = title)
                )
                log.info("Linked {} {} to issue {}", provider, refType, key)
            } catch (e: Exception) {
                log.debug("Duplicate ref ignored for issue {} {} {}", key, refType, externalId)
            }
        }
    }

    private fun sha256(input: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
```

- [ ] **Step 4: Verify IssueRepository has findByKey — check the file**

Open `backend/src/main/kotlin/com/taskowolf/issues/infrastructure/IssueRepository.kt` and confirm `findByKey(key: String): Issue?` exists. If it uses a different method name (e.g., `findByKeyIgnoreCase`), use that in `IncomingWebhookService.linkKeys()`.

- [ ] **Step 5: Create ProjectIntegrationController**

Create `backend/src/main/kotlin/com/taskowolf/integrations/api/ProjectIntegrationController.kt`:
```kotlin
package com.taskowolf.integrations.api

import com.taskowolf.auth.domain.User
import com.taskowolf.integrations.api.dto.CreateProjectIntegrationRequest
import com.taskowolf.integrations.application.ProjectIntegrationService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/projects/{key}/integrations")
class ProjectIntegrationController(private val service: ProjectIntegrationService) {

    @GetMapping
    fun list(@PathVariable key: String, @AuthenticationPrincipal user: User) =
        service.list(key, user)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @PathVariable key: String,
        @RequestBody req: CreateProjectIntegrationRequest,
        @AuthenticationPrincipal user: User
    ) = service.create(key, req, user)

    @DeleteMapping("/{integrationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable key: String,
        @PathVariable integrationId: UUID,
        @AuthenticationPrincipal user: User
    ) = service.delete(key, integrationId, user)
}
```

- [ ] **Step 6: Create GitHubWebhookController**

Create `backend/src/main/kotlin/com/taskowolf/integrations/api/GitHubWebhookController.kt`:
```kotlin
package com.taskowolf.integrations.api

import com.taskowolf.integrations.application.IncomingWebhookService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/integrations/github")
class GitHubWebhookController(private val incomingWebhookService: IncomingWebhookService) {

    @PostMapping("/{projectKey}/webhook")
    fun receive(
        @PathVariable projectKey: String,
        @RequestBody payload: String,
        @RequestHeader(value = "X-Hub-Signature-256", required = false) signature: String?
    ): ResponseEntity<Map<String, String>> {
        return try {
            incomingWebhookService.handleGitHub(projectKey, payload, signature)
            ResponseEntity.ok(mapOf("status" to "ok"))
        } catch (e: SecurityException) {
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to e.message.orEmpty()))
        }
    }
}
```

- [ ] **Step 7: Create GitLabWebhookController**

Create `backend/src/main/kotlin/com/taskowolf/integrations/api/GitLabWebhookController.kt`:
```kotlin
package com.taskowolf.integrations.api

import com.taskowolf.integrations.application.IncomingWebhookService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/integrations/gitlab")
class GitLabWebhookController(private val incomingWebhookService: IncomingWebhookService) {

    @PostMapping("/{projectKey}/webhook")
    fun receive(
        @PathVariable projectKey: String,
        @RequestBody payload: String,
        @RequestHeader(value = "X-Gitlab-Token", required = false) token: String?
    ): ResponseEntity<Map<String, String>> {
        return try {
            incomingWebhookService.handleGitLab(projectKey, payload, token)
            ResponseEntity.ok(mapOf("status" to "ok"))
        } catch (e: SecurityException) {
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to e.message.orEmpty()))
        }
    }
}
```

- [ ] **Step 8: Update SecurityConfig to permit incoming webhook paths**

Modify `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/SecurityConfig.kt` — add the incoming webhook paths to `permitAll()`:
```kotlin
it.requestMatchers(
    "/api/v1/auth/**",
    "/api/v1/integrations/github/*/webhook",
    "/api/v1/integrations/gitlab/*/webhook",
    "/swagger-ui/**",
    "/v3/api-docs/**",
    "/h2-console/**",
    "/ws/**",
    "/ws-stomp/**"
).permitAll()
```

- [ ] **Step 9: Write GitHub webhook integration test**

Create `backend/src/test/kotlin/com/taskowolf/integrations/GitHubWebhookControllerTest.kt`:
```kotlin
package com.taskowolf.integrations

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.IntegrationTestBase
import com.taskowolf.integrations.application.HmacSigner
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class GitHubWebhookControllerTest : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var hmacSigner: HmacSigner

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

    private fun setupGitHubIntegration(token: String, projectKey: String): String {
        val result = mockMvc.perform(
            post("/api/v1/projects/$projectKey/integrations")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"provider":"GITHUB"}""")
        ).andExpect(status().isCreated).andReturn()
        return objectMapper.readTree(result.response.contentAsString).get("plaintextSecret").asText()
    }

    @Test
    fun `GitHub push webhook with valid signature returns 200`() {
        val token = registerAndLogin("gh1@test.com")
        createProject(token, "GH1")
        val secret = setupGitHubIntegration(token, "GH1")

        val payload = """{"ref":"refs/heads/main","commits":[{"id":"abc123","message":"fix bug","url":"https://github.com/org/repo/commit/abc123"}]}"""
        val sha256secret = hmacSigner.sign(payload, sha256(secret))

        mockMvc.perform(
            post("/api/v1/integrations/github/GH1/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .header("X-Hub-Signature-256", sha256secret)
        ).andExpect(status().isOk)
    }

    @Test
    fun `GitHub webhook with invalid signature returns 401`() {
        val token = registerAndLogin("gh2@test.com")
        createProject(token, "GH2")
        setupGitHubIntegration(token, "GH2")

        val payload = """{"ref":"refs/heads/main","commits":[]}"""
        mockMvc.perform(
            post("/api/v1/integrations/github/GH2/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .header("X-Hub-Signature-256", "sha256=invalidsignature")
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `GitHub push with issue key creates IssueRef`() {
        val token = registerAndLogin("gh3@test.com")
        createProject(token, "GH3")
        val secret = setupGitHubIntegration(token, "GH3")

        val issueResult = mockMvc.perform(
            post("/api/v1/projects/GH3/issues")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"Test Issue","type":"TASK","priority":"MEDIUM"}""")
        ).andReturn()
        val issueKey = objectMapper.readTree(issueResult.response.contentAsString).get("key").asText()

        val payload = """{"ref":"refs/heads/main","commits":[{"id":"def456","message":"fix $issueKey crash","url":"https://github.com/org/repo/commit/def456"}]}"""
        val sig = hmacSigner.sign(payload, sha256(secret))

        mockMvc.perform(
            post("/api/v1/integrations/github/GH3/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .header("X-Hub-Signature-256", sig)
        ).andExpect(status().isOk)

        mockMvc.perform(
            get("/api/v1/projects/GH3/issues/$issueKey")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.refs[0].provider").value("GITHUB"))
            .andExpect(jsonPath("$.refs[0].refType").value("COMMIT"))
    }

    private fun sha256(input: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
```

Note: The third test (`creates IssueRef`) depends on Task 10 (IssueResponse with refs). Run the first two tests first, add the third after Task 10 is complete.

- [ ] **Step 10: Run first two tests**

```
./gradlew test --tests "com.taskowolf.integrations.GitHubWebhookControllerTest.GitHub push webhook with valid signature returns 200" --tests "com.taskowolf.integrations.GitHubWebhookControllerTest.GitHub webhook with invalid signature returns 401"
```
Expected: both GREEN.

- [ ] **Step 11: Run all tests**

```
./gradlew test
```
Expected: all tests GREEN.

- [ ] **Step 12: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/integrations/application/ProjectIntegrationService.kt \
        backend/src/main/kotlin/com/taskowolf/integrations/application/IncomingWebhookService.kt \
        backend/src/main/kotlin/com/taskowolf/integrations/api/dto/ \
        backend/src/main/kotlin/com/taskowolf/integrations/api/ProjectIntegrationController.kt \
        backend/src/main/kotlin/com/taskowolf/integrations/api/GitHubWebhookController.kt \
        backend/src/main/kotlin/com/taskowolf/integrations/api/GitLabWebhookController.kt \
        backend/src/main/kotlin/com/taskowolf/auth/infrastructure/SecurityConfig.kt \
        backend/src/test/kotlin/com/taskowolf/integrations/GitHubWebhookControllerTest.kt
git commit -m "feat(integrations): add ProjectIntegrationService, IncomingWebhookService, GitHub/GitLab controllers"
```

---

### Task 10: IssueRef in IssueResponse (issues module extension)

**Files:**
- Modify: `backend/src/main/kotlin/com/taskowolf/issues/api/dto/IssueResponse.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/issues/api/IssueController.kt`

**Interfaces:**
- Consumes: `IssueRefRepository.findByIssueId(issueId)`, `IssueRefResponse.from()`
- Produces: `IssueResponse.refs: List<IssueRefResponse>`

- [ ] **Step 1: Update IssueResponse to include refs**

Modify `backend/src/main/kotlin/com/taskowolf/issues/api/dto/IssueResponse.kt` — add `refs` field and update `from()`. Note: `from()` can no longer be a simple companion function since refs come from a separate query; the factory will be moved to the controller. Change the file to:
```kotlin
package com.taskowolf.issues.api.dto

import com.taskowolf.integrations.api.dto.IssueRefResponse
import com.taskowolf.issues.domain.Issue
import com.taskowolf.issues.domain.IssuePriority
import com.taskowolf.issues.domain.IssueType
import java.util.UUID

data class IssueResponse(
    val id: UUID,
    val key: String,
    val title: String,
    val description: String?,
    val type: IssueType,
    val priority: IssuePriority,
    val storyPoints: Int?,
    val statusId: UUID,
    val statusName: String,
    val statusCategory: String,
    val projectId: UUID,
    val assigneeId: UUID?,
    val reporterId: UUID,
    val parentId: UUID?,
    val refs: List<IssueRefResponse> = emptyList()
) {
    companion object {
        fun from(i: Issue, refs: List<IssueRefResponse> = emptyList()) = IssueResponse(
            i.id, i.key, i.title, i.description, i.type, i.priority, i.storyPoints,
            i.status.id, i.status.name, i.status.category.name,
            i.project.id, i.assignee?.id, i.reporter.id, i.parent?.id,
            refs
        )
    }
}
```

- [ ] **Step 2: Update IssueController to load refs for single-issue GET**

Modify `backend/src/main/kotlin/com/taskowolf/issues/api/IssueController.kt` — inject `IssueRefRepository` and use it in `get()`:
```kotlin
package com.taskowolf.issues.api

import com.taskowolf.auth.domain.User
import com.taskowolf.integrations.api.dto.IssueRefResponse
import com.taskowolf.integrations.infrastructure.IssueRefRepository
import com.taskowolf.issues.api.dto.CreateIssueRequest
import com.taskowolf.issues.api.dto.IssueResponse
import com.taskowolf.issues.api.dto.UpdateIssueRequest
import com.taskowolf.issues.application.IssueService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/projects/{key}/issues")
class IssueController(
    private val issueService: IssueService,
    private val issueRefRepository: IssueRefRepository
) {

    @GetMapping
    fun list(
        @PathVariable key: String,
        @AuthenticationPrincipal user: User,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        @RequestParam(defaultValue = "false") assigneeMe: Boolean,
        @RequestParam(required = false) sort: String?,
        @RequestParam(defaultValue = "false") overdue: Boolean
    ) = issueService.findByProject(key, user.id, page, size, assigneeMe, sort, overdue)
            .map { IssueResponse.from(it) }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @PathVariable key: String,
        @Valid @RequestBody request: CreateIssueRequest,
        @AuthenticationPrincipal user: User
    ) = IssueResponse.from(issueService.create(key, request, user))

    @GetMapping("/{issueKey}")
    fun get(
        @PathVariable key: String,
        @PathVariable issueKey: String,
        @AuthenticationPrincipal user: User
    ): IssueResponse {
        val issue = issueService.findByKey(key, issueKey, user.id)
        val refs = issueRefRepository.findByIssueId(issue.id).map { IssueRefResponse.from(it) }
        return IssueResponse.from(issue, refs)
    }

    @PatchMapping("/{id}")
    fun update(
        @PathVariable key: String,
        @PathVariable id: UUID,
        @RequestBody request: UpdateIssueRequest,
        @AuthenticationPrincipal user: User
    ) = IssueResponse.from(issueService.update(key, id, request, user))

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable key: String,
        @PathVariable id: UUID,
        @AuthenticationPrincipal user: User
    ) = issueService.delete(key, id, user)
}
```

- [ ] **Step 3: Run the third GitHubWebhookController test**

```
./gradlew test --tests "com.taskowolf.integrations.GitHubWebhookControllerTest"
```
Expected: all 3 tests GREEN (including the IssueRef round-trip test).

- [ ] **Step 4: Run all tests**

```
./gradlew test
```
Expected: all tests GREEN.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/issues/api/dto/IssueResponse.kt \
        backend/src/main/kotlin/com/taskowolf/issues/api/IssueController.kt
git commit -m "feat(issues): add refs[] to IssueResponse via IssueRefRepository"
```

---

### Task 11: Frontend — IntegrationsPage + IssueDetailPage refs + AppLayout nav + router

**Files:**
- Create: `frontend/src/hooks/useProjectIntegrations.ts`
- Create: `frontend/src/pages/settings/IntegrationsPage.tsx`
- Modify: `frontend/src/pages/issues/IssueDetailPage.tsx`
- Modify: `frontend/src/app/router.tsx`

**Interfaces:**
- Consumes: REST `GET/POST/DELETE /api/v1/projects/{key}/integrations`, `GET /api/v1/projects/{key}/issues/:issueKey` (with `refs[]`)
- Produces: route `/p/:key/settings/integrations`, References section in IssueDetailPage

- [ ] **Step 1: Create the integrations hook**

Create `frontend/src/hooks/useProjectIntegrations.ts`:
```typescript
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/api/client'

export interface ProjectIntegration {
  id: string
  provider: 'GITHUB' | 'GITLAB'
  repoUrl: string | null
  createdAt: string | null
}

export interface CreateIntegrationResponse {
  id: string
  provider: string
  webhookUrl: string
  plaintextSecret: string
  repoUrl: string | null
}

export function useProjectIntegrations(projectKey: string) {
  return useQuery<ProjectIntegration[]>({
    queryKey: ['integrations', projectKey],
    queryFn: () => apiClient.get(`/projects/${projectKey}/integrations`).then(r => r.data),
  })
}

export function useCreateIntegration(projectKey: string) {
  const qc = useQueryClient()
  return useMutation<CreateIntegrationResponse, Error, { provider: string; repoUrl?: string }>({
    mutationFn: body => apiClient.post(`/projects/${projectKey}/integrations`, body).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['integrations', projectKey] }),
  })
}

export function useDeleteIntegration(projectKey: string) {
  const qc = useQueryClient()
  return useMutation<void, Error, string>({
    mutationFn: id => apiClient.delete(`/projects/${projectKey}/integrations/${id}`).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['integrations', projectKey] }),
  })
}
```

- [ ] **Step 2: Create IntegrationsPage**

Create `frontend/src/pages/settings/IntegrationsPage.tsx`:
```typescript
import { useState } from 'react'
import { useParams } from 'react-router-dom'
import {
  useProjectIntegrations, useCreateIntegration, useDeleteIntegration
} from '@/hooks/useProjectIntegrations'
import type { CreateIntegrationResponse } from '@/hooks/useProjectIntegrations'

const PROVIDERS = ['GITHUB', 'GITLAB'] as const
type Provider = typeof PROVIDERS[number]

export function IntegrationsPage() {
  const { key } = useParams<{ key: string }>()
  const projectKey = key!
  const { data: integrations = [], isLoading } = useProjectIntegrations(projectKey)
  const createIntegration = useCreateIntegration(projectKey)
  const deleteIntegration = useDeleteIntegration(projectKey)

  const [connectingProvider, setConnectingProvider] = useState<Provider | null>(null)
  const [repoUrl, setRepoUrl] = useState('')
  const [newIntegration, setNewIntegration] = useState<CreateIntegrationResponse | null>(null)
  const [copiedUrl, setCopiedUrl] = useState(false)
  const [copiedSecret, setCopiedSecret] = useState(false)

  async function handleConnect(provider: Provider) {
    try {
      const result = await createIntegration.mutateAsync({ provider, repoUrl: repoUrl || undefined })
      setNewIntegration(result)
      setConnectingProvider(null)
      setRepoUrl('')
    } catch (e: any) {
      alert(e.response?.data?.message || 'Failed to connect integration')
    }
  }

  function copy(text: string, which: 'url' | 'secret') {
    navigator.clipboard.writeText(text)
    if (which === 'url') { setCopiedUrl(true); setTimeout(() => setCopiedUrl(false), 2000) }
    else { setCopiedSecret(true); setTimeout(() => setCopiedSecret(false), 2000) }
  }

  if (isLoading) return <div className="text-gray-400">Loading…</div>

  const activeProviders = new Set(integrations.map(i => i.provider))

  return (
    <div className="max-w-2xl">
      <h1 className="text-2xl font-bold mb-6">Integrations</h1>

      {newIntegration && (
        <div className="mb-6 p-4 bg-yellow-900/30 border border-yellow-600 rounded">
          <p className="text-yellow-400 text-sm font-semibold mb-3">
            ⚠ Save these values now — the secret will not be shown again.
          </p>
          <div className="space-y-2">
            <div>
              <p className="text-xs text-gray-400 mb-1">Webhook URL (paste into {newIntegration.provider}):</p>
              <div className="flex gap-2">
                <code className="flex-1 bg-gray-900 px-3 py-2 rounded text-xs text-blue-400 break-all">
                  {newIntegration.webhookUrl}
                </code>
                <button onClick={() => copy(newIntegration.webhookUrl, 'url')}
                  className="px-3 py-2 bg-gray-700 hover:bg-gray-600 rounded text-xs shrink-0">
                  {copiedUrl ? 'Copied!' : 'Copy'}
                </button>
              </div>
            </div>
            <div>
              <p className="text-xs text-gray-400 mb-1">Webhook Secret:</p>
              <div className="flex gap-2">
                <code className="flex-1 bg-gray-900 px-3 py-2 rounded text-xs text-green-400 break-all">
                  {newIntegration.plaintextSecret}
                </code>
                <button onClick={() => copy(newIntegration.plaintextSecret, 'secret')}
                  className="px-3 py-2 bg-gray-700 hover:bg-gray-600 rounded text-xs shrink-0">
                  {copiedSecret ? 'Copied!' : 'Copy'}
                </button>
              </div>
            </div>
          </div>
          <button onClick={() => setNewIntegration(null)} className="mt-3 text-xs text-gray-400 hover:text-white">
            Dismiss
          </button>
        </div>
      )}

      <div className="space-y-4">
        {PROVIDERS.map(provider => {
          const existing = integrations.find(i => i.provider === provider)
          const isActive = activeProviders.has(provider)
          const isConnecting = connectingProvider === provider

          return (
            <div key={provider} className="p-4 bg-gray-800 rounded border border-gray-700">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <div className="w-8 h-8 bg-gray-700 rounded flex items-center justify-center text-sm font-bold">
                    {provider === 'GITHUB' ? 'GH' : 'GL'}
                  </div>
                  <div>
                    <p className="font-medium">{provider === 'GITHUB' ? 'GitHub' : 'GitLab'}</p>
                    {existing?.repoUrl && (
                      <p className="text-xs text-gray-400">{existing.repoUrl}</p>
                    )}
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  {isActive ? (
                    <>
                      <span className="text-xs text-green-400 font-medium">● Active</span>
                      <button
                        onClick={() => existing && deleteIntegration.mutate(existing.id)}
                        className="px-3 py-1 bg-red-900/40 hover:bg-red-800 text-red-400 rounded text-xs"
                      >
                        Remove
                      </button>
                    </>
                  ) : (
                    <button
                      onClick={() => setConnectingProvider(provider)}
                      className="px-3 py-1 bg-indigo-600 hover:bg-indigo-700 rounded text-sm"
                    >
                      Connect
                    </button>
                  )}
                </div>
              </div>

              {isConnecting && (
                <div className="mt-4 border-t border-gray-700 pt-4">
                  <p className="text-xs text-gray-400 mb-2">
                    Repository URL (optional, for display only):
                  </p>
                  <input
                    type="text"
                    placeholder="https://github.com/org/repo"
                    value={repoUrl}
                    onChange={e => setRepoUrl(e.target.value)}
                    className="w-full px-3 py-2 bg-gray-900 rounded border border-gray-600 text-sm mb-3"
                  />
                  <div className="flex gap-2">
                    <button
                      onClick={() => handleConnect(provider)}
                      disabled={createIntegration.isPending}
                      className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 rounded text-sm"
                    >
                      {createIntegration.isPending ? 'Connecting…' : 'Generate Webhook URL'}
                    </button>
                    <button
                      onClick={() => { setConnectingProvider(null); setRepoUrl('') }}
                      className="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded text-sm"
                    >
                      Cancel
                    </button>
                  </div>
                </div>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}
```

- [ ] **Step 3: Add refs section to IssueDetailPage**

Open `frontend/src/pages/issues/IssueDetailPage.tsx` and add a References section. Find where the Activity Feed / CommentThread is rendered and add below it:
```typescript
// Add to imports at the top:
// (IssueRef data comes from the existing issue query — ensure IssueResponse type includes refs)

// After the CommentThread or ActivityFeed section, add:
{issue.refs && issue.refs.length > 0 && (
  <div className="mt-6">
    <h3 className="text-sm font-semibold text-gray-400 uppercase tracking-wider mb-3">
      References
    </h3>
    <div className="space-y-2">
      {issue.refs.map((ref: any) => (
        <a
          key={ref.id}
          href={ref.url}
          target="_blank"
          rel="noopener noreferrer"
          className="flex items-center gap-3 p-3 bg-gray-800 rounded hover:bg-gray-700 transition-colors"
        >
          <span className="text-xs font-bold px-2 py-0.5 rounded bg-gray-700 text-gray-300">
            {ref.provider}
          </span>
          <span className="text-xs px-2 py-0.5 rounded bg-gray-700 text-gray-400">
            {ref.refType}
          </span>
          <span className="text-sm text-blue-400 truncate">
            {ref.title || ref.externalId}
          </span>
          <span className="text-xs text-gray-500 shrink-0">
            {ref.createdAt ? new Date(ref.createdAt).toLocaleDateString() : ''}
          </span>
        </a>
      ))}
    </div>
  </div>
)}
```

- [ ] **Step 4: Add route to router.tsx**

In `frontend/src/app/router.tsx`, add:
```typescript
import { IntegrationsPage } from '@/pages/settings/IntegrationsPage'
// In the children array:
{ path: '/p/:key/settings/integrations', element: <IntegrationsPage /> },
```

(The Webhooks route and API Keys route were already added in Phase 6a and 6b. The AppLayout nav already shows all three Settings links from Phase 6a Task 3.)

- [ ] **Step 5: Run full test suite**

```
./gradlew test
```
Expected: all tests GREEN.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/hooks/useProjectIntegrations.ts \
        frontend/src/pages/settings/IntegrationsPage.tsx \
        frontend/src/pages/issues/IssueDetailPage.tsx \
        frontend/src/app/router.tsx
git commit -m "feat(frontend): add IntegrationsPage, IssueDetailPage refs section"
```

---

## Phase 6 Complete

After all three sub-plans (6a, 6b, 6c) are implemented and all tests pass:

```bash
git log --oneline -20
./gradlew test
```

All Phase 6 features are now available:
- API Keys: `POST /api/v1/projects/{key}/api-keys` → `tw_*` tokens authenticate requests
- Outgoing Webhooks: `POST /api/v1/projects/{key}/webhooks` → signed HTTP POSTs on domain events
- GitHub/GitLab Auto-Linking: `POST /api/v1/projects/{key}/integrations` → setup; commits/PRs with issue keys auto-link
- Frontend: three Settings pages at `/p/:key/settings/api-keys`, `/p/:key/settings/webhooks`, `/p/:key/settings/integrations`
- IssueDetailPage shows linked commits and PRs in a References section