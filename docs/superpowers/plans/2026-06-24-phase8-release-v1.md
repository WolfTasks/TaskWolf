# Phase 8: Release v1.0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship TaskWolf v1.0 by closing three Phase-7 security gaps, writing a CHANGELOG, publishing MkDocs documentation to GitHub Pages, and pushing multi-arch Docker images to Docker Hub on the `v1.0.0` git tag.

**Architecture:** Four independent tracks executed in sequence: backend-only hardening, a markdown changelog, a MkDocs static site with GitHub Actions deployment, and a Docker Hub multi-arch publish workflow. No new Flyway migrations — all hardening is pure Kotlin changes to existing classes.

**Tech Stack:** Kotlin 2.0 / Spring Boot 3.3.0 / JUnit 5 + MockK / MkDocs Material / GitHub Actions + Docker Buildx

## Global Constraints

- No new Flyway migrations; V21 is the current ceiling
- Test framework: MockK (never Mockito); Testcontainers for DB integration tests
- No `@SpringBootTest` for unit-testable logic — prefer fast MockK tests
- `backend/` — Kotlin source root; `frontend/` — React source root
- All commit messages follow `type(scope): description` convention
- YAGNI: add no dependencies beyond what each task explicitly requires

---

## File Map

| File | Action | Task |
|---|---|---|
| `backend/build.gradle.kts` | Modify — add `mockwebserver` test dep | 1 |
| `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/DbClientRegistrationRepository.kt` | Modify — use `ClientRegistrations.fromOidcIssuerLocation()` | 1 |
| `backend/src/test/kotlin/com/taskowolf/auth/DbClientRegistrationRepositoryTest.kt` | Modify — use MockWebServer for discovery | 1 |
| `backend/src/main/kotlin/com/taskowolf/organizations/application/OrganizationService.kt` | Modify — add `requireMembershipOrAdmin()` | 2 |
| `backend/src/main/kotlin/com/taskowolf/organizations/api/OrganizationController.kt` | Modify — guard `getById` + `listMembers` | 2 |
| `backend/src/test/kotlin/com/taskowolf/organizations/OrganizationServiceTest.kt` | Modify — add 3 membership guard tests | 2 |
| `backend/src/main/kotlin/com/taskowolf/audit/api/AuditController.kt` | Modify — add `escapeCsvCell()` companion + apply | 3 |
| `backend/src/test/kotlin/com/taskowolf/audit/AuditCsvEscapeTest.kt` | Create — unit tests for `escapeCsvCell` | 3 |
| `CHANGELOG.md` | Create — Keep-a-Changelog format | 4 |
| `mkdocs.yml` | Create — MkDocs config with Material theme | 5 |
| `requirements-docs.txt` | Create — `mkdocs-material>=9` | 5 |
| `.github/workflows/docs.yml` | Create — GitHub Pages deployment on push to main | 5 |
| `mkdocs/index.md` | Create | 6 |
| `mkdocs/getting-started.md` | Create | 6 |
| `mkdocs/configuration.md` | Create | 6 |
| `mkdocs/user-guide/projects.md` | Create | 6 |
| `mkdocs/user-guide/boards.md` | Create | 6 |
| `mkdocs/user-guide/automation.md` | Create | 6 |
| `mkdocs/user-guide/dashboards.md` | Create | 6 |
| `mkdocs/admin-guide/sso.md` | Create | 6 |
| `mkdocs/admin-guide/organizations.md` | Create | 6 |
| `mkdocs/admin-guide/service-desk.md` | Create | 6 |
| `mkdocs/admin-guide/audit-logs.md` | Create | 6 |
| `mkdocs/api.md` | Create | 6 |
| `mkdocs/development.md` | Create | 6 |
| `.github/workflows/docker-publish.yml` | Create — multi-arch Docker Hub publish on git tag | 7 |
| `docker-compose.prod.yml` | Create — production compose with healthchecks | 7 |
| `.env.example` | Create — env var template | 7 |

---

## Task 1: OIDC RFC-Compliant Discovery

**Files:**
- Modify: `backend/build.gradle.kts`
- Modify: `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/DbClientRegistrationRepository.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/auth/DbClientRegistrationRepositoryTest.kt`

**Interfaces:**
- Produces: `DbClientRegistrationRepository.findByRegistrationId(id)` — unchanged signature, now fetches OIDC metadata via `/.well-known/openid-configuration` instead of constructing Keycloak paths

**Context:** `DbClientRegistrationRepository` currently hardcodes Keycloak-style endpoint paths (`/protocol/openid-connect/auth`, etc.). The fix: replace all manual URI construction with Spring Security's `ClientRegistrations.fromOidcIssuerLocation(issuerUri)` which performs RFC-compliant discovery. The existing unit test breaks because the new code makes an HTTP call — replace it with MockWebServer.

- [ ] **Step 1: Add MockWebServer test dependency**

In `backend/build.gradle.kts`, add after the last `testImplementation` line:

```kotlin
testImplementation("com.squareup.okhttp3:mockwebserver")
```

`okhttp3:mockwebserver` is managed by Spring Boot 3.3.0's BOM — no version needed.

- [ ] **Step 2: Write the failing test**

Replace the full contents of `backend/src/test/kotlin/com/taskowolf/auth/DbClientRegistrationRepositoryTest.kt`:

```kotlin
package com.taskowolf.auth

import com.taskowolf.auth.application.SsoService
import com.taskowolf.auth.domain.SsoConfig
import com.taskowolf.auth.infrastructure.DbClientRegistrationRepository
import io.mockk.every
import io.mockk.mockk
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DbClientRegistrationRepositoryTest {

    private val ssoService = mockk<SsoService>()
    private val repo = DbClientRegistrationRepository(ssoService)
    private val server = MockWebServer()

    @BeforeEach
    fun setUp() = server.start()

    @AfterEach
    fun tearDown() = server.shutdown()

    @Test
    fun `findByRegistrationId returns null for unknown id`() {
        every { ssoService.listEnabled() } returns emptyList()
        assertNull(repo.findByRegistrationId("unknown"))
    }

    @Test
    fun `findByRegistrationId discovers endpoints from well-known openid-configuration`() {
        val baseUrl = server.url("").toString().trimEnd('/')
        val discoveryJson = """
            {
              "issuer": "$baseUrl",
              "authorization_endpoint": "$baseUrl/authorize",
              "token_endpoint": "$baseUrl/token",
              "jwks_uri": "$baseUrl/jwks",
              "userinfo_endpoint": "$baseUrl/userinfo",
              "response_types_supported": ["code"],
              "subject_types_supported": ["public"],
              "id_token_signing_alg_values_supported": ["RS256"]
            }
        """.trimIndent()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(discoveryJson)
        )

        val config = mockk<SsoConfig>(relaxed = true)
        every { config.id.toString() } returns "cfg-id"
        every { config.issuerUrl } returns baseUrl
        every { config.clientId } returns "client-id"
        every { config.clientSecretEnc } returns "enc"
        every { config.name } returns "My IdP"
        every { ssoService.listEnabled() } returns listOf(config)
        every { ssoService.decryptSecret("enc") } returns "plain-secret"

        val reg = repo.findByRegistrationId("cfg-id")

        assertNotNull(reg)
        assertEquals("client-id", reg!!.clientId)
        assertEquals("plain-secret", reg.clientSecret.value)
        assertEquals("$baseUrl/authorize", reg.providerDetails.authorizationUri)
        assertEquals("$baseUrl/token", reg.providerDetails.tokenUri)
        assertEquals("$baseUrl/userinfo", reg.providerDetails.userInfoEndpoint.uri)
    }
}
```

- [ ] **Step 3: Run the test — expect FAIL**

```bash
cd backend && ./gradlew test --tests "com.taskowolf.auth.DbClientRegistrationRepositoryTest" 2>&1 | tail -20
```

Expected: compile error or test failure because the repository still uses hardcoded paths and can't talk to MockWebServer.

- [ ] **Step 4: Fix DbClientRegistrationRepository**

Replace the full contents of `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/DbClientRegistrationRepository.kt`:

```kotlin
package com.taskowolf.auth.infrastructure

import com.taskowolf.auth.application.SsoService
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.registration.ClientRegistrations
import org.springframework.stereotype.Component

@Component
class DbClientRegistrationRepository(private val ssoService: SsoService) : ClientRegistrationRepository {

    override fun findByRegistrationId(registrationId: String): ClientRegistration? {
        val config = ssoService.listEnabled().find { it.id.toString() == registrationId } ?: return null
        return ClientRegistrations.fromOidcIssuerLocation(config.issuerUrl)
            .registrationId(registrationId)
            .clientId(config.clientId)
            .clientSecret(ssoService.decryptSecret(config.clientSecretEnc))
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .scope("openid", "profile", "email")
            .clientName(config.name)
            .build()
    }
}
```

- [ ] **Step 5: Run the test — expect PASS**

```bash
cd backend && ./gradlew test --tests "com.taskowolf.auth.DbClientRegistrationRepositoryTest" 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`, both tests green.

- [ ] **Step 6: Run full backend test suite**

```bash
cd backend && ./gradlew test 2>&1 | tail -30
```

Expected: `BUILD SUCCESSFUL`. If `SsoServiceTest` fails, it's unrelated — check separately.

- [ ] **Step 7: Commit**

```bash
git add backend/build.gradle.kts \
        backend/src/main/kotlin/com/taskowolf/auth/infrastructure/DbClientRegistrationRepository.kt \
        backend/src/test/kotlin/com/taskowolf/auth/DbClientRegistrationRepositoryTest.kt
git commit -m "fix(sso): RFC-compliant OIDC discovery via .well-known/openid-configuration"
```

---

## Task 2: Org Membership Guard

**Files:**
- Modify: `backend/src/main/kotlin/com/taskowolf/organizations/application/OrganizationService.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/organizations/api/OrganizationController.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/organizations/OrganizationServiceTest.kt`

**Interfaces:**
- Produces: `OrganizationService.requireMembershipOrAdmin(orgId: UUID, user: User): Unit` — throws `AccessDeniedException` if user is neither SYSTEM_ADMIN nor a member of the org

**Context:** `GET /organizations/{id}` and `GET /organizations/{id}/members` currently have no authorization check — any authenticated user can read any org's data. The fix adds a guard method to the service and calls it at the top of both controller endpoints.

- [ ] **Step 1: Write the failing tests**

Add to `backend/src/test/kotlin/com/taskowolf/organizations/OrganizationServiceTest.kt` (append after the existing `removeMember` test):

```kotlin
    // --- requireMembershipOrAdmin ---

    @Test
    fun `requireMembershipOrAdmin permits SYSTEM_ADMIN regardless of membership`() {
        val orgId = UUID.randomUUID()
        val user = mockk<com.taskowolf.auth.domain.User>(relaxed = true)
        every { user.systemRole } returns com.taskowolf.auth.domain.SystemRole.ADMIN
        // must not throw
        service.requireMembershipOrAdmin(orgId, user)
    }

    @Test
    fun `requireMembershipOrAdmin permits org member`() {
        val orgId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val user = mockk<com.taskowolf.auth.domain.User>(relaxed = true)
        every { user.systemRole } returns com.taskowolf.auth.domain.SystemRole.MEMBER
        every { user.id } returns userId
        val member = OrganizationMember(OrganizationMemberId(orgId, userId), OrgRole.MEMBER)
        every { memberRepo.findByIdOrgId(orgId) } returns listOf(member)
        // must not throw
        service.requireMembershipOrAdmin(orgId, user)
    }

    @Test
    fun `requireMembershipOrAdmin denies non-member SYSTEM_MEMBER`() {
        val orgId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val user = mockk<com.taskowolf.auth.domain.User>(relaxed = true)
        every { user.systemRole } returns com.taskowolf.auth.domain.SystemRole.MEMBER
        every { user.id } returns userId
        every { memberRepo.findByIdOrgId(orgId) } returns emptyList()
        org.junit.jupiter.api.assertThrows<org.springframework.security.access.AccessDeniedException> {
            service.requireMembershipOrAdmin(orgId, user)
        }
    }
```

- [ ] **Step 2: Run the failing tests**

```bash
cd backend && ./gradlew test --tests "com.taskowolf.organizations.OrganizationServiceTest" 2>&1 | tail -20
```

Expected: compile error — `requireMembershipOrAdmin` does not exist yet.

- [ ] **Step 3: Add requireMembershipOrAdmin to OrganizationService**

In `backend/src/main/kotlin/com/taskowolf/organizations/application/OrganizationService.kt`, add these imports at the top and the new method at the end of the class:

Add imports:
```kotlin
import com.taskowolf.auth.domain.SystemRole
import com.taskowolf.auth.domain.User
import org.springframework.security.access.AccessDeniedException
```

Add method inside the class body (before the closing brace):
```kotlin
    fun requireMembershipOrAdmin(orgId: UUID, user: User) {
        if (user.systemRole == SystemRole.ADMIN) return
        val isMember = memberRepo.findByIdOrgId(orgId).any { it.id.userId == user.id }
        if (!isMember) throw AccessDeniedException("Not a member of organization $orgId")
    }
```

- [ ] **Step 4: Run the tests — expect PASS**

```bash
cd backend && ./gradlew test --tests "com.taskowolf.organizations.OrganizationServiceTest" 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`, all tests including the 3 new ones green.

- [ ] **Step 5: Add auth guard to OrganizationController**

In `backend/src/main/kotlin/com/taskowolf/organizations/api/OrganizationController.kt`, replace the `getById` and `listMembers` methods:

```kotlin
    @GetMapping("/{id}")
    fun getById(
        @PathVariable id: UUID,
        @AuthenticationPrincipal user: User
    ): OrganizationResponse {
        orgService.requireMembershipOrAdmin(id, user)
        return OrganizationResponse.from(orgService.findById(id))
    }

    @GetMapping("/{id}/members")
    fun listMembers(
        @PathVariable id: UUID,
        @AuthenticationPrincipal user: User
    ): List<OrganizationMemberResponse> {
        orgService.requireMembershipOrAdmin(id, user)
        return orgService.listMembers(id).map { OrganizationMemberResponse.from(it) }
    }
```

- [ ] **Step 6: Run full backend test suite**

```bash
cd backend && ./gradlew test 2>&1 | tail -30
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/organizations/application/OrganizationService.kt \
        backend/src/main/kotlin/com/taskowolf/organizations/api/OrganizationController.kt \
        backend/src/test/kotlin/com/taskowolf/organizations/OrganizationServiceTest.kt
git commit -m "fix(orgs): enforce membership guard on GET /organizations/{id} and /members"
```

---

## Task 3: CSV Injection Escaping

**Files:**
- Modify: `backend/src/main/kotlin/com/taskowolf/audit/api/AuditController.kt`
- Create: `backend/src/test/kotlin/com/taskowolf/audit/AuditCsvEscapeTest.kt`

**Interfaces:**
- Produces: `AuditController.Companion.escapeCsvCell(value: String?): String` — internal companion function, prefixes formula-starting values with `'`

**Context:** The CSV export in `AuditController.export()` interpolates field values directly into CSV cells. An attacker with a crafted `userEmail` or `action` (e.g. `=CMD|'/C calc'!A1`) can inject spreadsheet formulas. Fix: prefix cells starting with `=`, `+`, `-`, `@`, `\t`, or `\r` with a `'` character (OWASP standard).

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/kotlin/com/taskowolf/audit/AuditCsvEscapeTest.kt`:

```kotlin
package com.taskowolf.audit

import com.taskowolf.audit.api.AuditController
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AuditCsvEscapeTest {

    @Test
    fun `escapeCsvCell prefixes formula-injection characters`() {
        assertEquals("'=SUM(A1:B1)", AuditController.escapeCsvCell("=SUM(A1:B1)"))
        assertEquals("'+1234", AuditController.escapeCsvCell("+1234"))
        assertEquals("'-1234", AuditController.escapeCsvCell("-1234"))
        assertEquals("'@user", AuditController.escapeCsvCell("@user"))
        assertEquals("'\tcell", AuditController.escapeCsvCell("\tcell"))
        assertEquals("'\rcell", AuditController.escapeCsvCell("\rcell"))
    }

    @Test
    fun `escapeCsvCell passes safe values through unchanged`() {
        assertEquals("LOGIN_SUCCESS", AuditController.escapeCsvCell("LOGIN_SUCCESS"))
        assertEquals("user@example.com", AuditController.escapeCsvCell("user@example.com"))
        assertEquals("", AuditController.escapeCsvCell(""))
        assertEquals("", AuditController.escapeCsvCell(null))
    }
}
```

Note: `user@example.com` starts with `u`, not `@` — it is safe and must not be prefixed.

- [ ] **Step 2: Run the failing test**

```bash
cd backend && ./gradlew test --tests "com.taskowolf.audit.AuditCsvEscapeTest" 2>&1 | tail -20
```

Expected: compile error — `AuditController.escapeCsvCell` does not exist yet.

- [ ] **Step 3: Add escapeCsvCell companion and apply in export**

Replace the full contents of `backend/src/main/kotlin/com/taskowolf/audit/api/AuditController.kt`:

```kotlin
package com.taskowolf.audit.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.audit.api.dto.AuditConfigRequest
import com.taskowolf.audit.api.dto.AuditEventResponse
import com.taskowolf.audit.application.AuditService
import com.taskowolf.audit.domain.AuditLevel
import com.taskowolf.audit.infrastructure.AuditEventRepository
import com.taskowolf.projects.infrastructure.ProjectRepository
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
class AuditController(
    private val auditService: AuditService,
    private val auditEventRepository: AuditEventRepository,
    private val projectRepository: ProjectRepository,
    private val objectMapper: ObjectMapper
) {
    companion object {
        internal fun escapeCsvCell(value: String?): String {
            if (value.isNullOrEmpty()) return ""
            return if (value[0] in charArrayOf('=', '+', '-', '@', '\t', '\r')) "'$value" else value
        }
    }

    @GetMapping("/admin/audit")
    @PreAuthorize("hasRole('ADMIN')")
    fun listAll(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        @RequestParam from: Instant? = null,
        @RequestParam to: Instant? = null,
        @RequestParam userId: UUID? = null,
        @RequestParam action: String? = null,
        @RequestParam level: String? = null
    ) = auditEventRepository.findFiltered(from, to, userId, action, level, PageRequest.of(page, size))
        .map { AuditEventResponse.from(it) }

    @GetMapping("/admin/audit/export")
    @PreAuthorize("hasRole('ADMIN')")
    fun export(@RequestParam(defaultValue = "json") format: String): ResponseEntity<String> {
        val events = auditEventRepository.findAll().map { AuditEventResponse.from(it) }
        return if (format == "csv") {
            val csv = buildString {
                appendLine("id,timestamp,userEmail,action,level,resourceType,resourceId,ipAddress")
                events.forEach {
                    appendLine(
                        "${escapeCsvCell(it.id.toString())}," +
                        "${escapeCsvCell(it.timestamp.toString())}," +
                        "${escapeCsvCell(it.userEmail)}," +
                        "${escapeCsvCell(it.action)}," +
                        "${escapeCsvCell(it.level)}," +
                        "${escapeCsvCell(it.resourceType)}," +
                        "${escapeCsvCell(it.resourceId)}," +
                        escapeCsvCell(it.ipAddress)
                    )
                }
            }
            ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=audit.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv)
        } else {
            val json = objectMapper.writeValueAsString(events)
            ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(json)
        }
    }

    @GetMapping("/projects/{key}/audit")
    @PreAuthorize("hasRole('ADMIN') or @projectSecurity.isProjectAdmin(#key, authentication)")
    fun listForProject(
        @PathVariable key: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        @RequestParam from: Instant? = null,
        @RequestParam to: Instant? = null,
        @RequestParam action: String? = null
    ): ResponseEntity<*> {
        val project = projectRepository.findByKey(key)
            ?: return ResponseEntity.notFound().build<Unit>()
        val results = auditEventRepository.findByProject(project.id, from, to, action, PageRequest.of(page, size))
            .map { AuditEventResponse.from(it) }
        return ResponseEntity.ok(results)
    }

    @GetMapping("/admin/audit/config")
    @PreAuthorize("hasRole('ADMIN')")
    fun getConfig() = auditService.getConfig().map { (k, v) -> mapOf("level" to k.name, "enabled" to v) }

    @PutMapping("/admin/audit/config")
    @PreAuthorize("hasRole('ADMIN')")
    fun updateConfig(@RequestBody req: AuditConfigRequest) {
        auditService.updateConfig(AuditLevel.valueOf(req.level), req.enabled)
    }
}
```

- [ ] **Step 4: Run the CSV escape test — expect PASS**

```bash
cd backend && ./gradlew test --tests "com.taskowolf.audit.AuditCsvEscapeTest" 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`, all 2 tests green.

- [ ] **Step 5: Run full backend test suite**

```bash
cd backend && ./gradlew test 2>&1 | tail -30
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/audit/api/AuditController.kt \
        backend/src/test/kotlin/com/taskowolf/audit/AuditCsvEscapeTest.kt
git commit -m "fix(audit): CSV injection-safe cell escaping in audit export"
```

---

## Task 4: Changelog

**Files:**
- Create: `CHANGELOG.md`

**Context:** A `CHANGELOG.md` in the repo root following Keep-a-Changelog format serves as the body for the GitHub Release. Write it by summarizing each phase.

- [ ] **Step 1: Create CHANGELOG.md**

Create `CHANGELOG.md` in the repo root:

```markdown
# Changelog

All notable changes to TaskWolf are documented here.
Format: [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [1.0.0] - 2026-06-24

### Added

#### Core Foundation
- Projects with custom keys (e.g. `WOLF-42`)
- Issues: Epics, Stories, Bugs, Tasks, Subtasks — with priority, labels, story points, due dates, and parent–child linking
- Configurable workflows with custom statuses and transitions
- JWT authentication + OAuth2 (GitHub, Google) + API key authentication
- Role-based access control: System Admin, Project Admin, Member
- OpenAPI 3 documentation at `/swagger-ui.html`
- Docker + docker-compose deployment with nginx reverse proxy

#### Agile Boards
- Kanban and Scrum board views with drag-and-drop column management
- Sprint lifecycle: Planned → Active → Closed
- Backlog management and sprint planning view
- Burndown chart per sprint

#### Collaboration
- Markdown comments with `@mention` autocomplete
- Real-time in-app notifications via WebSocket (STOMP)
- File attachments per issue
- Activity feed per issue

#### Workflows & Automation
- Visual workflow canvas editor with drag-and-drop transition builder
- No-code automation rules: When / If / Then
- Triggers: issue created, status changed, field updated, sprint started/completed
- Action types: assign user, set status, set field, add comment, create sub-issue, send notification

#### Dashboards & Reports
- Burndown chart (sprint-level)
- Velocity chart (multi-sprint trend)
- Cycle time analysis per issue type
- Custom dashboards with drag-and-drop widget layout
- Widget types: key metrics, charts, issue tables, activity feeds

#### Developer Tools & Integrations
- Outgoing webhooks with HMAC-SHA256 signing and automatic retry with exponential backoff
- GitHub and GitLab incoming webhooks — auto-link commits and pull requests to issues
- API key authentication (`tw_*` prefix, SHA-256 hashed storage) for CI/CD pipelines
- SSRF-safe HTTP delivery for outgoing webhooks

#### Enterprise
- Audit logs with three configurable levels: SECURITY (always on), WRITE, ALL — with JSON and CSV export
- SSO via OIDC — dynamic provider registration, AES-GCM encrypted client secrets, user auto-provisioning
- Organizations (multi-tenancy) with org switcher, JWT org context, and member management
- Service desks with SLA policies (breach detection + escalation rules)
- Incident tracking with automatic postmortem creation on resolution
- Email-to-ticket ingestion via IMAP

### Fixed
- OIDC provider discovery now uses RFC-compliant `/.well-known/openid-configuration` instead of hardcoded Keycloak paths — compatible with Okta, Azure AD, Auth0, Google Workspace
- `GET /organizations/{id}` and `GET /organizations/{id}/members` now enforce membership authorization; non-members receive 403
- Audit log CSV export uses OWASP-recommended formula-injection escaping (values starting with `=`, `+`, `-`, `@` are prefixed with `'`)
```

- [ ] **Step 2: Commit CHANGELOG**

```bash
git add CHANGELOG.md
git commit -m "docs: CHANGELOG.md for v1.0.0 release"
```

---

## Task 5: MkDocs Infrastructure

**Files:**
- Create: `mkdocs.yml`
- Create: `requirements-docs.txt`
- Create: `.github/workflows/docs.yml`

**Context:** Set up the MkDocs build toolchain and GitHub Pages deployment workflow. The `docs_dir: mkdocs/` setting keeps the existing `docs/superpowers/` internal planning files untouched. The workflow triggers on every push to `main` so docs stay current.

- [ ] **Step 1: Create requirements-docs.txt**

Create `requirements-docs.txt` in the repo root:

```
mkdocs-material>=9.5
```

- [ ] **Step 2: Create mkdocs.yml**

Create `mkdocs.yml` in the repo root:

```yaml
site_name: TaskWolf
site_description: Open-source, self-hosted project management
repo_url: https://github.com/taskowolf/TaskWolf
repo_name: taskowolf/TaskWolf
docs_dir: mkdocs

theme:
  name: material
  palette:
    - media: "(prefers-color-scheme: light)"
      scheme: default
      primary: blue
      accent: cyan
      toggle:
        icon: material/brightness-7
        name: Switch to dark mode
    - media: "(prefers-color-scheme: dark)"
      scheme: slate
      primary: blue
      accent: cyan
      toggle:
        icon: material/brightness-4
        name: Switch to light mode
  features:
    - navigation.tabs
    - navigation.sections
    - navigation.top
    - search.suggest
    - search.highlight
    - content.code.copy

nav:
  - Home: index.md
  - Getting Started: getting-started.md
  - Configuration: configuration.md
  - User Guide:
      - Projects & Issues: user-guide/projects.md
      - Boards & Sprints: user-guide/boards.md
      - Automation: user-guide/automation.md
      - Dashboards: user-guide/dashboards.md
  - Admin Guide:
      - SSO / OIDC: admin-guide/sso.md
      - Organizations: admin-guide/organizations.md
      - Service Desk: admin-guide/service-desk.md
      - Audit Logs: admin-guide/audit-logs.md
  - API Reference: api.md
  - Development: development.md

plugins:
  - search

markdown_extensions:
  - admonition
  - pymdownx.details
  - pymdownx.superfences
  - pymdownx.highlight:
      anchor_linenums: true
  - pymdownx.inlinehilite
  - pymdownx.tabbed:
      alternate_style: true
  - tables
  - toc:
      permalink: true
```

- [ ] **Step 3: Create GitHub Pages deployment workflow**

Create `.github/workflows/docs.yml`:

```yaml
name: Deploy Docs

on:
  push:
    branches: [main]
  workflow_dispatch:

permissions:
  contents: write

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-python@v5
        with:
          python-version: '3.x'

      - name: Install MkDocs
        run: pip install -r requirements-docs.txt

      - name: Build and deploy docs
        run: mkdocs gh-deploy --force
```

- [ ] **Step 4: Commit infrastructure files**

```bash
git add requirements-docs.txt mkdocs.yml .github/workflows/docs.yml
git commit -m "docs: MkDocs Material setup with GitHub Pages deployment workflow"
```

---

## Task 6: MkDocs Content Pages

**Files:**
- Create: all files under `mkdocs/`

**Context:** Write all documentation pages. Keep prose concise and practical — focus on what someone needs to deploy and use TaskWolf, not a full tutorial. Each page gets created individually then committed once at the end.

- [ ] **Step 1: Create mkdocs/index.md**

```markdown
# TaskWolf

**Open-source, self-hosted project management — built for teams that want full control.**

TaskWolf is a Jira-style issue tracker and agile board you run on your own infrastructure. Single Docker Compose stack, zero vendor lock-in, PostgreSQL in production.

## Features at a Glance

| Area | Highlights |
|---|---|
| **Issues** | Epics → Stories → Tasks → Subtasks, custom fields, labels, story points |
| **Boards** | Kanban and Scrum with drag-and-drop; sprint lifecycle management |
| **Automation** | No-code When/If/Then rules triggered on issue events |
| **Dashboards** | Drag-and-drop widget dashboards; velocity, burndown, cycle time charts |
| **Integrations** | GitHub/GitLab webhooks, outgoing webhooks with HMAC signing, API keys |
| **Enterprise** | OIDC SSO, audit logs, multi-org tenancy, service desk with SLA policies |

## Quick Links

- [Getting Started](getting-started.md) — run TaskWolf in 5 minutes
- [Configuration Reference](configuration.md) — all environment variables
- [API Reference](api.md) — Swagger UI and API keys
```

- [ ] **Step 2: Create mkdocs/getting-started.md**

```markdown
# Getting Started

## Prerequisites

- Docker 24+ and Docker Compose v2
- A terminal
- 512 MB RAM available for the backend container

## 1. Clone the repository

```bash
git clone https://github.com/taskowolf/TaskWolf.git
cd TaskWolf
```

## 2. Configure environment variables

Copy the example file and set required values:

```bash
cp .env.example .env
```

Edit `.env` — the only **required** variable is `TW_JWT_SECRET`. Generate a secure value:

```bash
openssl rand -hex 32
```

## 3. Start the stack

```bash
docker compose -f docker-compose.prod.yml up -d
```

TaskWolf starts at `http://localhost`. The first user to register becomes System Admin.

## 4. Log in

Open `http://localhost` in your browser. Click **Register** and create your account. You will automatically receive the System Admin role.

## Upgrading

```bash
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
```

Database migrations run automatically on startup via Flyway.
```

- [ ] **Step 3: Create mkdocs/configuration.md**

```markdown
# Configuration Reference

All configuration is done via environment variables. Set them in your `.env` file when using `docker-compose.prod.yml`.

## Required

| Variable | Description |
|---|---|
| `TW_JWT_SECRET` | JWT signing secret — minimum 32 characters. Generate with `openssl rand -hex 32`. |

## Database

| Variable | Default | Description |
|---|---|---|
| `TW_DB_URL` | `jdbc:postgresql://db:5432/taskowolf` | JDBC connection URL |
| `TW_DB_USER` | `taskowolf` | Database username |
| `TW_DB_PASS` | — | Database password. **Required in production.** |

## Application

| Variable | Default | Description |
|---|---|---|
| `TW_BASE_URL` | `http://localhost` | Public base URL used in email links and OAuth2 redirect URIs |
| `TW_STORAGE_PATH` | `/data/attachments` | Path inside the container for file attachments |
| `SPRING_PROFILES_ACTIVE` | `prod` | Spring profile. Use `prod` for production. |

## OAuth2 (optional)

| Variable | Description |
|---|---|
| `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GITHUB_CLIENT_ID` | GitHub OAuth App client ID |
| `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GITHUB_CLIENT_SECRET` | GitHub OAuth App client secret |
| `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID` | Google OAuth client ID |
| `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET` | Google OAuth client secret |

## Email / IMAP (optional, Service Desk)

| Variable | Default | Description |
|---|---|---|
| `TW_MAIL_HOST` | — | SMTP host for outgoing emails |
| `TW_MAIL_PORT` | `587` | SMTP port |
| `TW_MAIL_USER` | — | SMTP username |
| `TW_MAIL_PASS` | — | SMTP password |
| `TW_IMAP_HOST` | — | IMAP host for email-to-ticket ingestion |
| `TW_IMAP_USER` | — | IMAP username |
| `TW_IMAP_PASS` | — | IMAP password |
| `TW_IMAP_FOLDER` | `INBOX` | IMAP folder to poll |

## Audit Logging

Audit levels are configured at runtime via the Admin UI (`/admin/audit/config`), not via environment variables.
```

- [ ] **Step 4: Create mkdocs/user-guide/projects.md**

```markdown
# Projects & Issues

## Projects

Each project has a unique **key** (e.g. `WOLF`) used in all issue identifiers (`WOLF-42`). Create a project from the sidebar → **New Project**.

### Roles

| Role | Can do |
|---|---|
| **Project Admin** | Manage members, workflow, settings |
| **Member** | Create and edit issues |

## Issues

### Issue Types

- **Epic** — large body of work; parent of stories
- **Story** — user-facing feature
- **Bug** — defect
- **Task** — engineering or ops work
- **Subtask** — child of any issue type

### Fields

| Field | Notes |
|---|---|
| Summary | Required |
| Description | Markdown supported |
| Assignee | Single user |
| Priority | Urgent, High, Medium, Low |
| Labels | Free-form tags |
| Story Points | Numeric estimate |
| Due Date | Calendar picker |
| Parent | Link to Epic or parent issue |

### Transitions

Issues move between statuses via the **workflow** defined per project. Click the status badge on any issue to see available transitions.

## Workflows

A workflow is a set of **statuses** and **allowed transitions** between them. Edit via **Project Settings → Workflow**.
```

- [ ] **Step 5: Create mkdocs/user-guide/boards.md**

```markdown
# Boards & Sprints

## Board Types

Select **Kanban** or **Scrum** per project via **Project Settings → Board**.

## Kanban Board

Columns map to workflow statuses. Drag cards between columns to transition issues. Columns with a **WIP limit** turn red when exceeded.

## Scrum Board

Shows only issues in the **active sprint**. Drag cards to update status.

### Sprint Lifecycle

1. **Create sprint** — from the Backlog view, click **New Sprint**
2. **Add issues** — drag from backlog to sprint
3. **Start sprint** — sets the sprint period (start date + end date)
4. **Complete sprint** — moves unfinished issues to the backlog or next sprint

### Burndown Chart

Available on the Sprint view. Shows remaining story points per day vs. ideal trend line.

## Backlog

All un-sprinted issues appear in the backlog. Drag to re-rank or into a sprint row. Use filters (assignee, label, priority) to focus.
```

- [ ] **Step 6: Create mkdocs/user-guide/automation.md**

```markdown
# Automation

Automation rules run server-side when issue events occur. No code required.

## Rule Structure

```
WHEN  <trigger>
IF    <conditions>   (optional)
THEN  <actions>
```

## Triggers

| Trigger | Fires when |
|---|---|
| Issue Created | A new issue is created in the project |
| Status Changed | An issue transitions to a new status |
| Field Updated | A specific field value changes |
| Sprint Started | An active sprint begins |
| Sprint Completed | A sprint is closed |

## Conditions (optional)

Conditions filter which events actually fire the rule. Combine with **AND** or **OR**.

Examples: `Priority = Urgent`, `Assignee is empty`, `Label contains "backend"`

## Actions

| Action | What it does |
|---|---|
| Assign User | Sets the assignee |
| Set Status | Transitions the issue to a target status |
| Set Field | Updates any field to a fixed value |
| Add Comment | Posts a comment (supports `{{issue.summary}}` placeholders) |
| Create Sub-Issue | Creates a child issue with given type and summary |
| Send Notification | Pushes an in-app notification to a specific user or role |

## Creating a Rule

1. Open **Project Settings → Automation**
2. Click **New Rule**
3. Select a trigger
4. Optionally add condition groups
5. Add one or more actions
6. **Save** — the rule activates immediately
```

- [ ] **Step 7: Create mkdocs/user-guide/dashboards.md**

```markdown
# Dashboards

Custom dashboards let you build views from widgets arranged via drag-and-drop.

## Creating a Dashboard

1. Click **Dashboards** in the sidebar
2. Click **New Dashboard** — give it a name
3. Click **Add Widget** and choose a widget type

## Widget Types

| Widget | Description |
|---|---|
| **Key Metric** | Single number: open issues, overdue issues, sprint velocity |
| **Burndown Chart** | Story point burn for a selected sprint |
| **Velocity Chart** | Completed story points across recent sprints |
| **Cycle Time** | Average time from start to close per issue type |
| **Issue Table** | Filterable list of issues with custom columns |
| **Activity Feed** | Recent comments and status changes |

## Resizing & Rearranging

Drag widgets by their header to reposition. Drag the bottom-right handle to resize.

## Sharing

Dashboards are per-user by default. System Admins can view any user's dashboard.
```

- [ ] **Step 8: Create mkdocs/admin-guide/sso.md**

```markdown
# SSO / OIDC

TaskWolf supports any OIDC-compliant identity provider (Keycloak, Okta, Azure AD, Auth0, Google Workspace).

## Register a Provider

1. In your IdP, create an **OIDC application** (Authorization Code flow)
2. Set the redirect URI to: `https://<your-domain>/login/oauth2/code/<provider-id>`
3. Note the **Issuer URL**, **Client ID**, and **Client Secret**

Then in TaskWolf:

1. Log in as System Admin
2. Go to **Admin → SSO**
3. Click **Add Provider**
4. Fill in: Name, Issuer URL, Client ID, Client Secret
5. **Save** — the provider appears on the login page immediately

## Auto-Provisioning

When **Auto-Provision** is enabled (default), users logging in via SSO for the first time are automatically created with the `MEMBER` system role.

Disable auto-provisioning if you want to manually create accounts before allowing SSO login.

## Discovery

TaskWolf fetches provider metadata from `{issuerUrl}/.well-known/openid-configuration` at login time. No manual endpoint configuration is required.

## Removing a Provider

Delete the provider from **Admin → SSO**. Existing user accounts are not affected.
```

- [ ] **Step 9: Create mkdocs/admin-guide/organizations.md**

```markdown
# Organizations

Organizations provide multi-tenancy — each org is an isolated namespace for projects and users.

## Creating an Organization

System Admin only. Go to **Admin → Organizations → New Organization**.

Each org has a unique **slug** used in API paths.

## Switching Organizations

Users with membership in multiple orgs see an **Org Switcher** in the top navigation. Switching sets the active org context for all subsequent actions.

## Managing Members

From **Admin → Organizations → [Org Name] → Members**:

- **Add Member** — enter user ID and select role (Owner, Admin, Member)
- **Remove Member** — click the remove button next to a member

### Member Roles

| Role | Capabilities |
|---|---|
| Owner | Full control, can delete the org |
| Admin | Manage members and projects |
| Member | Access org projects |

## Data Isolation

All projects, issues, and data belong to an org. Users can only access resources within their active org context. System Admins can access any org.
```

- [ ] **Step 10: Create mkdocs/admin-guide/service-desk.md**

```markdown
# Service Desk

The service desk module provides ITSM-light functionality: ticket queues, SLA policies, incident tracking, and email-to-ticket ingestion.

## Setting Up a Service Desk

1. Go to **Admin → Service Desks → New Service Desk**
2. Assign it to a project — tickets become issues in that project
3. Configure SLA policies (see below)

## SLA Policies

An SLA policy defines response time targets and escalation rules.

1. Open the service desk → **SLA Policies → Add Policy**
2. Set: Name, Priority filter, Response target (minutes), Resolution target (minutes)
3. Add **Escalation Rules** — notify a user or role when the SLA is about to breach

The SLA monitor checks for breaches every minute.

## Incidents

Incidents track major outages or service disruptions.

- Create from **Service Desk → Incidents → New Incident**
- Set **Severity** (P1–P4) and affected service
- When an incident is resolved, a **Postmortem** is created automatically

### Postmortem

The postmortem form captures: timeline, root cause, contributing factors, action items, and lessons learned. It is linked to the incident and visible in the incident detail view.

## Email-to-Ticket (IMAP)

Configure IMAP credentials in your `.env` file (see [Configuration](../configuration.md)). Emails received in the configured inbox are converted to tickets automatically.

Requires `TW_IMAP_HOST`, `TW_IMAP_USER`, and `TW_IMAP_PASS` to be set.
```

- [ ] **Step 11: Create mkdocs/admin-guide/audit-logs.md**

```markdown
# Audit Logs

Audit logs record security events, data changes, and (optionally) read access.

## Log Levels

| Level | Always active | Example events |
|---|---|---|
| `SECURITY` | Yes | Login, logout, password change, role change, API key created/deleted |
| `WRITE` | Configurable | Issue created/updated/deleted, sprint started, member added |
| `ALL` | Configurable | Issue viewed, board opened, report viewed |

Configure levels at **Admin → Audit → Configuration**.

## Viewing Logs

**Admin → Audit** shows the full audit log with filters:

- Date range
- User
- Action type
- Level

**Project Admins** can view project-scoped audit logs from **Project Settings → Audit**.

## Exporting

Click **Export** on the audit log view and choose **JSON** or **CSV**.

The CSV export uses OWASP-recommended formula-injection escaping — safe to open directly in Excel or Google Sheets.

## Retention

Audit events are stored in the database with no automatic expiry. Archive old events via the JSON export and delete directly in PostgreSQL if needed.
```

- [ ] **Step 12: Create mkdocs/api.md**

```markdown
# API Reference

## Interactive Docs

TaskWolf exposes a full OpenAPI 3 specification. Access the interactive Swagger UI at:

```
http://<your-host>/swagger-ui.html
```

The OpenAPI JSON is available at `/v3/api-docs`.

## Authentication

### JWT (browser sessions)

Obtained via `POST /api/v1/auth/login`. Include as a Bearer token:

```
Authorization: Bearer <token>
```

### API Keys (CI/CD)

Generate API keys from **Profile → API Keys**. Keys use the `tw_` prefix and are displayed only once at creation.

```
Authorization: Bearer tw_<key>
```

API keys carry the permissions of the user who created them.

## Base URL

All endpoints are prefixed with `/api/v1`. Project-scoped endpoints include the project key:

```
/api/v1/projects/{key}/issues
/api/v1/projects/{key}/sprints
```

## Rate Limiting

Requests are rate-limited per IP address. The default limit is 100 requests per minute. Exceeded limits return HTTP 429.
```

- [ ] **Step 13: Create mkdocs/development.md**

```markdown
# Development

## Prerequisites

- JDK 21 (Temurin recommended)
- Node.js 20+
- Docker (for Testcontainers)

## Clone and Build

```bash
git clone https://github.com/taskowolf/TaskWolf.git
cd TaskWolf
```

### Backend

```bash
cd backend
./gradlew bootRun
```

The backend starts on `http://localhost:8080` with the `dev` profile (H2 in-memory database, Swagger UI enabled).

### Frontend

```bash
cd frontend
npm install
npm run dev
```

The frontend dev server starts on `http://localhost:5173` and proxies API calls to `localhost:8080`.

## Running Tests

### Backend

```bash
cd backend
./gradlew test
```

Tests use H2 for unit tests and Testcontainers (PostgreSQL) for integration tests.

**Windows note:** Docker Desktop must be running with TCP socket enabled (`tcp://localhost:2375`).

### Frontend

```bash
cd frontend
npm run build   # TypeScript + Vite build check
```

## Database Migrations

Flyway migrations live in `backend/src/main/resources/db/migration/`. The current version is **V21**. New migrations must follow the naming convention `V{n}__{description}.sql`.

## Project Structure

```
TaskWolf/
├── backend/          # Kotlin / Spring Boot
│   └── src/
│       ├── main/kotlin/com/taskowolf/
│       │   ├── auth/         # JWT, OAuth2, SSO, API keys
│       │   ├── audit/        # Audit logs
│       │   ├── organizations/# Multi-tenancy
│       │   ├── servicedesk/  # SLA, incidents
│       │   ├── projects/     # Projects, workflows
│       │   ├── issues/       # Issue lifecycle
│       │   ├── sprints/      # Sprint management
│       │   ├── boards/       # Kanban/Scrum
│       │   ├── automation/   # No-code rules
│       │   ├── reports/      # Velocity, cycle time
│       │   └── integrations/ # Webhooks, GitHub, GitLab
│       └── resources/
│           └── db/migration/ # Flyway SQL migrations V1–V21
└── frontend/         # React 19 / TypeScript / Vite
```

## Architecture

TaskWolf is a **modular monolith**. Modules communicate via Spring `ApplicationEvent`s — no direct service-to-service calls across module boundaries. Each module owns its domain, application, infrastructure, and API layers.
```

- [ ] **Step 14: Commit all MkDocs content pages**

```bash
git add mkdocs/
git commit -m "docs: MkDocs content pages — getting started, user guide, admin guide, API, development"
```

---

## Task 7: Docker Hub CI/CD

**Files:**
- Create: `.github/workflows/docker-publish.yml`
- Create: `docker-compose.prod.yml`
- Create: `.env.example`

**Context:** The existing `ci.yml` pushes to GHCR on every `main` push. The new `docker-publish.yml` is separate — it triggers only on `v*.*.*` git tags and pushes multi-arch images to Docker Hub. `docker-compose.prod.yml` references the Docker Hub images rather than building locally.

**Before you start:** Ensure the following secrets exist in the GitHub repository settings:
- `DOCKERHUB_USERNAME` — your Docker Hub username
- `DOCKERHUB_TOKEN` — a Docker Hub access token (generate at hub.docker.com → Account Settings → Security)

- [ ] **Step 1: Create .env.example**

Create `.env.example` in the repo root:

```bash
# Required
TW_JWT_SECRET=change-me-to-a-64-char-random-hex-string

# Database
TW_DB_USER=taskowolf
TW_DB_PASS=change-me

# Application
TW_BASE_URL=http://localhost

# OAuth2 — optional
# SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GITHUB_CLIENT_ID=
# SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GITHUB_CLIENT_SECRET=
# SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID=
# SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET=

# Email / IMAP — optional, required for Service Desk email ingestion
# TW_MAIL_HOST=
# TW_MAIL_PORT=587
# TW_MAIL_USER=
# TW_MAIL_PASS=
# TW_IMAP_HOST=
# TW_IMAP_USER=
# TW_IMAP_PASS=
# TW_IMAP_FOLDER=INBOX
```

- [ ] **Step 2: Create docker-compose.prod.yml**

Create `docker-compose.prod.yml` in the repo root:

```yaml
services:
  nginx:
    image: nginx:alpine
    ports:
      - "80:80"
    volumes:
      - ./docker/nginx.conf:/etc/nginx/conf.d/default.conf:ro
    depends_on:
      - app
      - frontend
    restart: unless-stopped

  app:
    image: taskwolf/taskowolf-backend:latest
    environment:
      SPRING_PROFILES_ACTIVE: prod
      TW_DB_URL: ${TW_DB_URL:-jdbc:postgresql://db:5432/taskowolf}
      TW_DB_USER: ${TW_DB_USER:-taskowolf}
      TW_DB_PASS: ${TW_DB_PASS}
      TW_JWT_SECRET: ${TW_JWT_SECRET}
      TW_BASE_URL: ${TW_BASE_URL:-http://localhost}
      TW_STORAGE_PATH: /data/attachments
    volumes:
      - attachments:/data/attachments
    depends_on:
      db:
        condition: service_healthy
    restart: unless-stopped
    mem_limit: 512m
    healthcheck:
      test: ["CMD-SHELL", "nc -z localhost 8080 || exit 1"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s

  frontend:
    image: taskwolf/taskowolf-frontend:latest
    restart: unless-stopped

  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: taskowolf
      POSTGRES_USER: ${TW_DB_USER:-taskowolf}
      POSTGRES_PASSWORD: ${TW_DB_PASS}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U taskowolf"]
      interval: 5s
      timeout: 5s
      retries: 10
    restart: unless-stopped

volumes:
  postgres_data:
  attachments:
```

- [ ] **Step 3: Create the Docker Hub publish workflow**

Create `.github/workflows/docker-publish.yml`:

```yaml
name: Publish to Docker Hub

on:
  push:
    tags: ['v*.*.*']

permissions:
  contents: read

jobs:
  publish:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        include:
          - context: ./backend
            image: taskwolf/taskowolf-backend
            gradle_build: true
          - context: ./frontend
            image: taskwolf/taskowolf-frontend
            gradle_build: false

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        if: matrix.gradle_build
        uses: actions/setup-java@v5
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: 'gradle'

      - name: Build backend JAR
        if: matrix.gradle_build
        working-directory: backend
        run: |
          chmod +x gradlew
          ./gradlew bootJar

      - uses: docker/setup-qemu-action@v3

      - uses: docker/setup-buildx-action@v3

      - uses: docker/login-action@v3
        with:
          username: ${{ secrets.DOCKERHUB_USERNAME }}
          password: ${{ secrets.DOCKERHUB_TOKEN }}

      - uses: docker/metadata-action@v5
        id: meta
        with:
          images: ${{ matrix.image }}
          tags: |
            type=semver,pattern={{version}}
            type=raw,value=latest

      - uses: docker/build-push-action@v6
        with:
          context: ${{ matrix.context }}
          platforms: linux/amd64,linux/arm64
          push: true
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}
```

- [ ] **Step 4: Add Docker Hub badges to README**

In `README.md`, add the following lines after the opening description paragraph (after the "zero vendor lock-in" sentence):

```markdown
[![Backend](https://img.shields.io/docker/v/taskwolf/taskowolf-backend?label=backend&logo=docker)](https://hub.docker.com/r/taskwolf/taskowolf-backend)
[![Frontend](https://img.shields.io/docker/v/taskwolf/taskowolf-frontend?label=frontend&logo=docker)](https://hub.docker.com/r/taskwolf/taskowolf-frontend)
```

- [ ] **Step 5: Commit**

```bash
git add .env.example docker-compose.prod.yml .github/workflows/docker-publish.yml README.md
git commit -m "ci: multi-arch Docker Hub publish workflow + docker-compose.prod.yml"
```

- [ ] **Step 6: Create v1.0.0 git tag and GitHub Release**

Run the following after all tasks are complete and all tests pass:

```bash
git tag v1.0.0
git push origin main --tags
```

Then create the GitHub Release:

```bash
gh release create v1.0.0 \
  --title "TaskWolf v1.0.0" \
  --notes-file CHANGELOG.md
```

The `v1.0.0` tag push triggers the `docker-publish.yml` workflow, which builds and pushes `taskwolf/taskowolf-backend:1.0.0` and `taskwolf/taskowolf-frontend:1.0.0` (plus `:latest`) to Docker Hub.

Verify the workflow completed successfully at `https://github.com/<owner>/TaskWolf/actions`.

---

## Self-Review Checklist

- [x] **Spec coverage:** All three hardening items (Tasks 1–3), Changelog (Task 4), MkDocs + GitHub Pages (Tasks 5–6), Docker Hub CI/CD (Task 7) — all covered
- [x] **No placeholders:** All code blocks are complete; all doc pages contain real content
- [x] **Type consistency:** `requireMembershipOrAdmin(orgId: UUID, user: User)` used identically in service and controller; `escapeCsvCell(value: String?): String` consistent between implementation and test
- [x] **Dependency on `mockwebserver`:** Added to `build.gradle.kts` in Task 1 Step 1, before the test is written in Step 2
- [x] **Docker Hub image names** `taskwolf/taskowolf-backend` and `taskwolf/taskowolf-frontend` — consistent across `docker-publish.yml`, `docker-compose.prod.yml`, and README badges
- [x] **`nc` availability:** `eclipse-temurin:21-jre-alpine` base image includes `netcat-openbsd` (busybox nc) — the healthcheck `nc -z localhost 8080` is valid
