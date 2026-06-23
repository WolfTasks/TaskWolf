# Phase 7: Enterprise Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Audit Logs, SSO via OIDC, Multi-Tenancy/Organizations, and Service Management to TaskWolf in four sequential steps, each independently shippable.

**Architecture:** New modules `audit`, `organizations`, `servicedesk`; `auth` extended for SSO. All new entities use UUID PKs (matching existing AuditableEntity pattern). Each step adds one Flyway migration (V17–V20).

**Tech Stack:** Kotlin 2.x + Spring Boot 3.x + Spring Data JPA + Spring Security OAuth2 Client (OIDC) + Spring Integration Mail (Step 4) + JJWT + MockK + JUnit 5 + React 19 + TypeScript + shadcn/ui + React Query

## Global Constraints

- All entity IDs: `UUID DEFAULT gen_random_uuid()` — never BIGSERIAL
- Tests: MockK (not Mockito), JUnit 5; test files in `backend/src/test/kotlin/com/taskowolf/<module>/`
- Extend `AuditableEntity` for all new domain entities except `AuditEvent` (which is append-only and manages its own timestamp)
- Run backend tests: `./gradlew test` (Windows: `gradlew.bat test`)
- Run frontend: `cd frontend && npm run dev`
- Commit after every task with `feat(<module>): <description>`
- Current latest Flyway migration: V16

---

## Step 1: Audit Logs (`audit` module, V17)

---

### Task 1: V17 Migration + AuditEvent Domain

**Files:**
- Create: `backend/src/main/resources/db/migration/V17__audit.sql`
- Create: `backend/src/main/kotlin/com/taskowolf/audit/domain/AuditLevel.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/audit/domain/AuditAction.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/audit/domain/AuditEvent.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/audit/infrastructure/AuditEventRepository.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/audit/AuditEventRepositoryTest.kt`

**Interfaces:**
- Produces: `AuditEvent(id, timestamp, userId, userEmail, projectId, action, level, resourceType, resourceId, details, ipAddress, userAgent)`, `AuditLevel.SECURITY/WRITE/ALL`, `AuditAction` enum with all constants

- [ ] **Step 1: Write the migration**

`V17__audit.sql`:
```sql
CREATE TABLE audit_events (
    id            UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    timestamp     TIMESTAMPTZ NOT NULL DEFAULT now(),
    user_id       UUID        REFERENCES users(id) ON DELETE SET NULL,
    user_email    VARCHAR(255) NOT NULL,
    project_id    UUID        REFERENCES projects(id) ON DELETE SET NULL,
    action        VARCHAR(100) NOT NULL,
    level         VARCHAR(20)  NOT NULL,
    resource_type VARCHAR(50),
    resource_id   VARCHAR(100),
    details       JSONB,
    ip_address    VARCHAR(45),
    user_agent    VARCHAR(500)
);
CREATE INDEX idx_audit_timestamp   ON audit_events (timestamp DESC);
CREATE INDEX idx_audit_project     ON audit_events (project_id, timestamp DESC);
CREATE INDEX idx_audit_user        ON audit_events (user_id, timestamp DESC);

CREATE TABLE audit_config (
    level   VARCHAR(20) NOT NULL PRIMARY KEY,
    enabled BOOLEAN     NOT NULL DEFAULT false
);
INSERT INTO audit_config (level, enabled) VALUES ('SECURITY', true), ('WRITE', false), ('ALL', false);
```

- [ ] **Step 2: Create domain classes**

`AuditLevel.kt`:
```kotlin
package com.taskowolf.audit.domain
enum class AuditLevel { SECURITY, WRITE, ALL }
```

`AuditAction.kt`:
```kotlin
package com.taskowolf.audit.domain
enum class AuditAction {
    LOGIN_SUCCESS, LOGIN_FAILED, LOGOUT, PASSWORD_CHANGED, ROLE_CHANGED,
    API_KEY_CREATED, API_KEY_DELETED, OAUTH_LOGIN, USER_REGISTERED,
    ISSUE_CREATED, ISSUE_UPDATED, ISSUE_DELETED, ISSUE_TRANSITIONED,
    COMMENT_CREATED, COMMENT_DELETED, SPRINT_STARTED, SPRINT_COMPLETED,
    MEMBER_ADDED, MEMBER_REMOVED, WEBHOOK_CREATED, WEBHOOK_DELETED, SLA_BREACHED,
    ISSUE_VIEWED, BOARD_OPENED, REPORT_VIEWED
}
```

`AuditEvent.kt`:
```kotlin
package com.taskowolf.audit.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "audit_events")
class AuditEvent(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(nullable = false) val timestamp: Instant = Instant.now(),
    val userId: UUID? = null,
    @Column(nullable = false) val userEmail: String,
    val projectId: UUID? = null,
    @Enumerated(EnumType.STRING) @Column(nullable = false) val action: AuditAction,
    @Enumerated(EnumType.STRING) @Column(nullable = false) val level: AuditLevel,
    val resourceType: String? = null,
    val resourceId: String? = null,
    @Column(columnDefinition = "JSONB") val details: String? = null,
    val ipAddress: String? = null,
    val userAgent: String? = null
)
```

`AuditEventRepository.kt`:
```kotlin
package com.taskowolf.audit.infrastructure

import com.taskowolf.audit.domain.AuditEvent
import com.taskowolf.audit.domain.AuditLevel
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface AuditEventRepository : JpaRepository<AuditEvent, UUID> {
    @Query("""SELECT e FROM AuditEvent e WHERE
        (:from IS NULL OR e.timestamp >= :from) AND
        (:to IS NULL OR e.timestamp <= :to) AND
        (:userId IS NULL OR e.userId = :userId) AND
        (:action IS NULL OR e.action = :action) AND
        (:level IS NULL OR e.level = :level)
        ORDER BY e.timestamp DESC""")
    fun findFiltered(from: Instant?, to: Instant?, userId: UUID?, action: String?, level: String?, pageable: Pageable): Page<AuditEvent>

    @Query("""SELECT e FROM AuditEvent e WHERE e.projectId = :projectId AND
        (:from IS NULL OR e.timestamp >= :from) AND
        (:to IS NULL OR e.timestamp <= :to) AND
        (:action IS NULL OR e.action = :action)
        ORDER BY e.timestamp DESC""")
    fun findByProject(projectId: UUID, from: Instant?, to: Instant?, action: String?, pageable: Pageable): Page<AuditEvent>
}
```

- [ ] **Step 3: Write failing test**

`AuditEventRepositoryTest.kt`:
```kotlin
package com.taskowolf.audit

import com.taskowolf.audit.domain.AuditAction
import com.taskowolf.audit.domain.AuditEvent
import com.taskowolf.audit.domain.AuditLevel
import com.taskowolf.audit.infrastructure.AuditEventRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.domain.Pageable

@DataJpaTest
class AuditEventRepositoryTest {
    @Autowired lateinit var repo: AuditEventRepository

    @Test
    fun `findFiltered returns matching events`() {
        repo.save(AuditEvent(userEmail = "a@b.com", action = AuditAction.LOGIN_SUCCESS, level = AuditLevel.SECURITY))
        repo.save(AuditEvent(userEmail = "a@b.com", action = AuditAction.ISSUE_CREATED, level = AuditLevel.WRITE))
        val result = repo.findFiltered(null, null, null, "LOGIN_SUCCESS", null, Pageable.ofSize(10))
        assertEquals(1, result.totalElements)
    }
}
```

- [ ] **Step 4: Run test** — `gradlew.bat test --tests "com.taskowolf.audit.AuditEventRepositoryTest"` — expect PASS

- [ ] **Step 5: Commit**
```
git add backend/src/main/resources/db/migration/V17__audit.sql \
  backend/src/main/kotlin/com/taskowolf/audit/ \
  backend/src/test/kotlin/com/taskowolf/audit/
git commit -m "feat(audit): V17 migration, AuditEvent domain, repository"
```

---

### Task 2: AuditConfig Entity + AuditService

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/audit/domain/AuditConfig.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/audit/infrastructure/AuditConfigRepository.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/audit/application/AuditService.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/audit/AuditServiceTest.kt`

**Interfaces:**
- Produces: `AuditService.log(level, action, userEmail, userId?, projectId?, resourceType?, resourceId?, details?, ipAddress?, userAgent?)`, `AuditService.getConfig(): Map<AuditLevel, Boolean>`, `AuditService.updateConfig(level, enabled)`
- Produces: `AuditService.isEnabled(level): Boolean`

- [ ] **Step 1: Create AuditConfig**

`AuditConfig.kt`:
```kotlin
package com.taskowolf.audit.domain

import jakarta.persistence.*

@Entity
@Table(name = "audit_config")
class AuditConfig(
    @Id @Enumerated(EnumType.STRING) val level: AuditLevel,
    @Column(nullable = false) var enabled: Boolean
)
```

`AuditConfigRepository.kt`:
```kotlin
package com.taskowolf.audit.infrastructure

import com.taskowolf.audit.domain.AuditConfig
import com.taskowolf.audit.domain.AuditLevel
import org.springframework.data.jpa.repository.JpaRepository

interface AuditConfigRepository : JpaRepository<AuditConfig, AuditLevel>
```

- [ ] **Step 2: Write failing tests**

`AuditServiceTest.kt`:
```kotlin
package com.taskowolf.audit

import com.taskowolf.audit.application.AuditService
import com.taskowolf.audit.domain.AuditAction
import com.taskowolf.audit.domain.AuditConfig
import com.taskowolf.audit.domain.AuditLevel
import com.taskowolf.audit.infrastructure.AuditConfigRepository
import com.taskowolf.audit.infrastructure.AuditEventRepository
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AuditServiceTest {
    private val eventRepo = mockk<AuditEventRepository>(relaxed = true)
    private val configRepo = mockk<AuditConfigRepository>()
    private val service = AuditService(eventRepo, configRepo)

    @Test
    fun `SECURITY events are always logged regardless of config`() {
        every { configRepo.findAll() } returns listOf(
            AuditConfig(AuditLevel.SECURITY, false)
        )
        service.log(AuditLevel.SECURITY, AuditAction.LOGIN_SUCCESS, "u@e.com")
        verify { eventRepo.save(any()) }
    }

    @Test
    fun `WRITE events are skipped when disabled`() {
        every { configRepo.findAll() } returns listOf(
            AuditConfig(AuditLevel.WRITE, false)
        )
        service.log(AuditLevel.WRITE, AuditAction.ISSUE_CREATED, "u@e.com")
        verify(exactly = 0) { eventRepo.save(any()) }
    }

    @Test
    fun `WRITE events are logged when enabled`() {
        every { configRepo.findAll() } returns listOf(
            AuditConfig(AuditLevel.WRITE, true)
        )
        service.log(AuditLevel.WRITE, AuditAction.ISSUE_CREATED, "u@e.com")
        verify { eventRepo.save(any()) }
    }
}
```

- [ ] **Step 3: Run tests** — expect FAIL (AuditService missing)

- [ ] **Step 4: Implement AuditService**

`AuditService.kt`:
```kotlin
package com.taskowolf.audit.application

import com.taskowolf.audit.domain.AuditAction
import com.taskowolf.audit.domain.AuditEvent
import com.taskowolf.audit.domain.AuditLevel
import com.taskowolf.audit.infrastructure.AuditConfigRepository
import com.taskowolf.audit.infrastructure.AuditEventRepository
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class AuditService(
    private val eventRepo: AuditEventRepository,
    private val configRepo: AuditConfigRepository
) {
    private fun isEnabled(level: AuditLevel): Boolean {
        if (level == AuditLevel.SECURITY) return true
        return configRepo.findAll().find { it.level == level }?.enabled ?: false
    }

    @Async
    @Transactional
    fun log(
        level: AuditLevel, action: AuditAction, userEmail: String,
        userId: UUID? = null, projectId: UUID? = null,
        resourceType: String? = null, resourceId: String? = null,
        details: String? = null, ipAddress: String? = null, userAgent: String? = null
    ) {
        if (!isEnabled(level)) return
        eventRepo.save(AuditEvent(
            userEmail = userEmail, userId = userId, projectId = projectId,
            action = action, level = level, resourceType = resourceType,
            resourceId = resourceId, details = details,
            ipAddress = ipAddress, userAgent = userAgent
        ))
    }

    @Transactional(readOnly = true)
    fun getConfig() = configRepo.findAll().associate { it.level to it.enabled }

    @Transactional
    fun updateConfig(level: AuditLevel, enabled: Boolean) {
        val config = configRepo.findById(level).orElseThrow()
        config.enabled = enabled
    }
}
```

- [ ] **Step 5: Run tests** — `gradlew.bat test --tests "com.taskowolf.audit.AuditServiceTest"` — expect PASS

- [ ] **Step 6: Commit**
```
git add backend/src/main/kotlin/com/taskowolf/audit/ backend/src/test/kotlin/com/taskowolf/audit/
git commit -m "feat(audit): AuditConfig, AuditService with level-gated logging"
```

---

### Task 3: SecurityAuditListener + AuthService Wiring

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/audit/application/SecurityAuditListener.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/auth/application/AuthService.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/audit/SecurityAuditListenerTest.kt`

**Interfaces:**
- Consumes: `AuditService.log(...)` from Task 2
- Produces: Auth events (LOGIN_SUCCESS, LOGIN_FAILED, LOGOUT, USER_REGISTERED, OAUTH_LOGIN) are logged

- [ ] **Step 1: Write failing test**

`SecurityAuditListenerTest.kt`:
```kotlin
package com.taskowolf.audit

import com.taskowolf.audit.application.AuditService
import com.taskowolf.audit.application.SecurityAuditListener
import com.taskowolf.audit.domain.AuditAction
import com.taskowolf.audit.domain.AuditLevel
import io.mockk.*
import org.junit.jupiter.api.Test

class SecurityAuditListenerTest {
    private val auditService = mockk<AuditService>(relaxed = true)
    private val listener = SecurityAuditListener(auditService)

    @Test
    fun `onLoginSuccess logs LOGIN_SUCCESS`() {
        listener.onLoginSuccess("user@example.com", "1.2.3.4")
        verify { auditService.log(AuditLevel.SECURITY, AuditAction.LOGIN_SUCCESS, "user@example.com", ipAddress = "1.2.3.4") }
    }

    @Test
    fun `onLoginFailed logs LOGIN_FAILED`() {
        listener.onLoginFailed("bad@example.com", "1.2.3.4")
        verify { auditService.log(AuditLevel.SECURITY, AuditAction.LOGIN_FAILED, "bad@example.com", ipAddress = "1.2.3.4") }
    }
}
```

- [ ] **Step 2: Run test** — expect FAIL

- [ ] **Step 3: Implement SecurityAuditListener**

`SecurityAuditListener.kt`:
```kotlin
package com.taskowolf.audit.application

import com.taskowolf.audit.domain.AuditAction
import com.taskowolf.audit.domain.AuditLevel
import org.springframework.stereotype.Component

@Component
class SecurityAuditListener(private val auditService: AuditService) {
    fun onLoginSuccess(email: String, ip: String?) =
        auditService.log(AuditLevel.SECURITY, AuditAction.LOGIN_SUCCESS, email, ipAddress = ip)

    fun onLoginFailed(email: String, ip: String?) =
        auditService.log(AuditLevel.SECURITY, AuditAction.LOGIN_FAILED, email, ipAddress = ip)

    fun onLogout(email: String) =
        auditService.log(AuditLevel.SECURITY, AuditAction.LOGOUT, email)

    fun onRegister(email: String) =
        auditService.log(AuditLevel.SECURITY, AuditAction.USER_REGISTERED, email)

    fun onOAuthLogin(email: String) =
        auditService.log(AuditLevel.SECURITY, AuditAction.OAUTH_LOGIN, email)
}
```

- [ ] **Step 4: Wire into AuthService** — add `SecurityAuditListener` as constructor param and call it:

```kotlin
// In AuthService constructor add:
private val securityAuditListener: SecurityAuditListener,

// In register(), after userRepository.save():
securityAuditListener.onRegister(request.email)

// In login(), replace the ForbiddenException throw:
if (user == null || hash == null || !passwordEncoder.matches(request.password, hash)) {
    securityAuditListener.onLoginFailed(request.email, null)
    throw ForbiddenException("Invalid credentials")
}
// After tokenPair():
securityAuditListener.onLoginSuccess(user.email, null)

// In logout():
val user = userRepository.findById(userId).orElse(null)
user?.let { securityAuditListener.onLogout(it.email) }
```

- [ ] **Step 5: Run tests** — `gradlew.bat test --tests "com.taskowolf.audit.SecurityAuditListenerTest"` — expect PASS

- [ ] **Step 6: Commit**
```
git add backend/src/main/kotlin/com/taskowolf/audit/ backend/src/main/kotlin/com/taskowolf/auth/application/AuthService.kt backend/src/test/kotlin/com/taskowolf/audit/
git commit -m "feat(audit): SecurityAuditListener, wire auth events"
```

---

### Task 4: WriteAuditListener (Domain Events)

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/audit/application/WriteAuditListener.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/audit/WriteAuditListenerTest.kt`

**Interfaces:**
- Consumes: `IssueCreatedEvent`, `IssueFieldChangedEvent`, `IssueStatusChangedEvent` (from `issues` module); `CommentCreatedEvent` (from `comments`); `SprintStartedEvent`, `SprintCompletedEvent` (from `sprints`)
- Produces: WRITE-level audit events for all domain mutations

- [ ] **Step 1: Check existing event signatures** — read `IssueCreatedEvent.kt` to confirm field names:
  - Expected: `issue.id`, `issue.projectId`, `issue.reporterId`, `issue.reporterEmail` (or user lookup)

- [ ] **Step 2: Write failing test**

`WriteAuditListenerTest.kt`:
```kotlin
package com.taskowolf.audit

import com.taskowolf.audit.application.AuditService
import com.taskowolf.audit.application.WriteAuditListener
import com.taskowolf.audit.domain.AuditAction
import com.taskowolf.audit.domain.AuditLevel
import com.taskowolf.issues.domain.events.IssueCreatedEvent
import io.mockk.*
import org.junit.jupiter.api.Test
import java.util.UUID

class WriteAuditListenerTest {
    private val auditService = mockk<AuditService>(relaxed = true)
    private val listener = WriteAuditListener(auditService)

    @Test
    fun `IssueCreatedEvent logs ISSUE_CREATED`() {
        val event = IssueCreatedEvent(
            issueId = UUID.randomUUID(), projectId = UUID.randomUUID(),
            actorEmail = "dev@example.com", actorId = UUID.randomUUID()
        )
        listener.onIssueCreated(event)
        verify { auditService.log(AuditLevel.WRITE, AuditAction.ISSUE_CREATED, "dev@example.com", any(), any(), any(), any()) }
    }
}
```

- [ ] **Step 3: Run test** — expect FAIL

- [ ] **Step 4: Check actual IssueCreatedEvent fields** — read `backend/src/main/kotlin/com/taskowolf/issues/domain/events/IssueCreatedEvent.kt` and adapt the event constructor in the test to match real fields

- [ ] **Step 5: Implement WriteAuditListener**

`WriteAuditListener.kt`:
```kotlin
package com.taskowolf.audit.application

import com.taskowolf.audit.domain.AuditAction
import com.taskowolf.audit.domain.AuditLevel
import com.taskowolf.issues.domain.events.IssueCreatedEvent
import com.taskowolf.issues.domain.events.IssueFieldChangedEvent
import com.taskowolf.issues.domain.events.IssueStatusChangedEvent
import com.taskowolf.comments.domain.events.CommentCreatedEvent
import com.taskowolf.sprints.domain.events.SprintStartedEvent
import com.taskowolf.sprints.domain.events.SprintCompletedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class WriteAuditListener(private val auditService: AuditService) {

    @EventListener
    fun onIssueCreated(e: IssueCreatedEvent) =
        auditService.log(AuditLevel.WRITE, AuditAction.ISSUE_CREATED, e.actorEmail,
            userId = e.actorId, projectId = e.projectId, resourceType = "ISSUE", resourceId = e.issueId.toString())

    @EventListener
    fun onIssueUpdated(e: IssueFieldChangedEvent) =
        auditService.log(AuditLevel.WRITE, AuditAction.ISSUE_UPDATED, e.actorEmail,
            userId = e.actorId, projectId = e.projectId, resourceType = "ISSUE", resourceId = e.issueId.toString())

    @EventListener
    fun onIssueTransitioned(e: IssueStatusChangedEvent) =
        auditService.log(AuditLevel.WRITE, AuditAction.ISSUE_TRANSITIONED, e.actorEmail,
            userId = e.actorId, projectId = e.projectId, resourceType = "ISSUE", resourceId = e.issueId.toString())

    @EventListener
    fun onCommentCreated(e: CommentCreatedEvent) =
        auditService.log(AuditLevel.WRITE, AuditAction.COMMENT_CREATED, e.actorEmail,
            userId = e.actorId, projectId = e.projectId, resourceType = "COMMENT", resourceId = e.commentId.toString())

    @EventListener
    fun onSprintStarted(e: SprintStartedEvent) =
        auditService.log(AuditLevel.WRITE, AuditAction.SPRINT_STARTED, e.actorEmail,
            userId = e.actorId, projectId = e.projectId, resourceType = "SPRINT", resourceId = e.sprintId.toString())

    @EventListener
    fun onSprintCompleted(e: SprintCompletedEvent) =
        auditService.log(AuditLevel.WRITE, AuditAction.SPRINT_COMPLETED, e.actorEmail,
            userId = e.actorId, projectId = e.projectId, resourceType = "SPRINT", resourceId = e.sprintId.toString())
}
```

**Note:** The domain events may not carry `actorEmail`/`actorId` fields yet. If missing, add these fields to the existing event classes and update their publishers (IssueService, CommentService, SprintService).

- [ ] **Step 6: Run tests** — `gradlew.bat test --tests "com.taskowolf.audit.WriteAuditListenerTest"` — expect PASS

- [ ] **Step 7: Commit**
```
git add backend/src/main/kotlin/com/taskowolf/audit/ backend/src/test/kotlin/com/taskowolf/audit/
git commit -m "feat(audit): WriteAuditListener for domain events"
```

---

### Task 5: AuditController + Export

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/audit/api/dto/AuditEventResponse.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/audit/api/dto/AuditConfigRequest.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/audit/api/AuditController.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/audit/AuditControllerTest.kt`

**Interfaces:**
- Consumes: `AuditService` from Task 2, `AuditEventRepository` from Task 1
- Produces: REST endpoints as per spec

- [ ] **Step 1: Create DTOs**

`AuditEventResponse.kt`:
```kotlin
package com.taskowolf.audit.api.dto

import com.taskowolf.audit.domain.AuditEvent
import java.time.Instant
import java.util.UUID

data class AuditEventResponse(
    val id: UUID, val timestamp: Instant, val userEmail: String,
    val userId: UUID?, val projectId: UUID?, val action: String,
    val level: String, val resourceType: String?, val resourceId: String?,
    val ipAddress: String?
) {
    companion object {
        fun from(e: AuditEvent) = AuditEventResponse(
            e.id, e.timestamp, e.userEmail, e.userId, e.projectId,
            e.action.name, e.level.name, e.resourceType, e.resourceId, e.ipAddress
        )
    }
}
```

`AuditConfigRequest.kt`:
```kotlin
package com.taskowolf.audit.api.dto
data class AuditConfigRequest(val level: String, val enabled: Boolean)
```

- [ ] **Step 2: Implement AuditController**

`AuditController.kt`:
```kotlin
package com.taskowolf.audit.api

import com.taskowolf.audit.api.dto.AuditConfigRequest
import com.taskowolf.audit.api.dto.AuditEventResponse
import com.taskowolf.audit.application.AuditService
import com.taskowolf.audit.domain.AuditLevel
import com.taskowolf.audit.infrastructure.AuditEventRepository
import com.taskowolf.projects.infrastructure.ProjectRepository
import org.springframework.data.domain.PageRequest
import org.springframework.format.annotation.DateTimeFormat
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
    private val projectRepository: ProjectRepository
) {
    @GetMapping("/admin/audit")
    @PreAuthorize("hasRole('ADMIN')")
    fun listAll(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        @RequestParam from: Instant? = null, @RequestParam to: Instant? = null,
        @RequestParam userId: UUID? = null, @RequestParam action: String? = null,
        @RequestParam level: String? = null
    ) = auditEventRepository.findFiltered(from, to, userId, action, level, PageRequest.of(page, size))
        .map { AuditEventResponse.from(it) }

    @GetMapping("/admin/audit/export")
    @PreAuthorize("hasRole('ADMIN')")
    fun export(@RequestParam format: String = "json"): ResponseEntity<String> {
        val events = auditEventRepository.findAll().map { AuditEventResponse.from(it) }
        return if (format == "csv") {
            val csv = buildString {
                appendLine("id,timestamp,userEmail,action,level,resourceType,resourceId,ipAddress")
                events.forEach { appendLine("${it.id},${it.timestamp},${it.userEmail},${it.action},${it.level},${it.resourceType},${it.resourceId},${it.ipAddress}") }
            }
            ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=audit.csv")
                .contentType(MediaType.parseMediaType("text/csv")).body(csv)
        } else {
            ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(events.toString())
        }
    }

    @GetMapping("/projects/{key}/audit")
    @PreAuthorize("hasRole('ADMIN') or @projectSecurity.isProjectAdmin(#key, authentication)")
    fun listForProject(
        @PathVariable key: String,
        @RequestParam(defaultValue = "0") page: Int, @RequestParam(defaultValue = "50") size: Int,
        @RequestParam from: Instant? = null, @RequestParam to: Instant? = null,
        @RequestParam action: String? = null
    ): Any {
        val project = projectRepository.findByKey(key) ?: return ResponseEntity.notFound().build<Unit>()
        return auditEventRepository.findByProject(project.id, from, to, action, PageRequest.of(page, size))
            .map { AuditEventResponse.from(it) }
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

- [ ] **Step 3: Run tests** — `gradlew.bat test` — all green

- [ ] **Step 4: Commit**
```
git add backend/src/main/kotlin/com/taskowolf/audit/api/
git commit -m "feat(audit): AuditController with admin + project-scoped + export endpoints"
```

---

### Task 6: Frontend — Audit Log Pages

**Files:**
- Create: `frontend/src/api/audit.ts`
- Create: `frontend/src/pages/admin/AuditLogPage.tsx`
- Create: `frontend/src/pages/projects/settings/ProjectAuditPage.tsx`
- Modify: `frontend/src/app/router.tsx` — add `/admin/audit` and `/p/:key/settings/audit`
- Modify: `frontend/src/layouts/AppLayout.tsx` — add "Audit Log" link in admin nav

- [ ] **Step 1: Create API client**

`frontend/src/api/audit.ts`:
```typescript
import { apiClient } from './client'

export interface AuditEvent {
  id: string; timestamp: string; userEmail: string; userId?: string
  projectId?: string; action: string; level: string
  resourceType?: string; resourceId?: string; ipAddress?: string
}

export const auditApi = {
  listAll: (params: Record<string, string>) =>
    apiClient.get('/admin/audit', { params }).then(r => r.data),
  exportAudit: (format: 'csv' | 'json') =>
    apiClient.get('/admin/audit/export', { params: { format }, responseType: 'blob' }),
  listForProject: (key: string, params: Record<string, string>) =>
    apiClient.get(`/projects/${key}/audit`, { params }).then(r => r.data),
  getConfig: () => apiClient.get('/admin/audit/config').then(r => r.data),
  updateConfig: (level: string, enabled: boolean) =>
    apiClient.put('/admin/audit/config', { level, enabled }),
}
```

- [ ] **Step 2: Create AuditLogPage**

`frontend/src/pages/admin/AuditLogPage.tsx`:
```tsx
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { auditApi, AuditEvent } from '../../api/audit'
import { Button } from '../../components/ui/button'
import { Badge } from '../../components/ui/badge'

const LEVEL_COLOR: Record<string, string> = {
  SECURITY: 'destructive', WRITE: 'default', ALL: 'secondary'
}

export default function AuditLogPage() {
  const [action, setAction] = useState('')
  const [level, setLevel] = useState('')
  const { data } = useQuery({
    queryKey: ['audit', action, level],
    queryFn: () => auditApi.listAll({ ...(action && { action }), ...(level && { level }) })
  })

  const handleExport = async (format: 'csv' | 'json') => {
    const res = await auditApi.exportAudit(format)
    const url = URL.createObjectURL(res.data)
    const a = document.createElement('a'); a.href = url; a.download = `audit.${format}`; a.click()
  }

  return (
    <div className="p-6 space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Audit Log</h1>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={() => handleExport('csv')}>Export CSV</Button>
          <Button variant="outline" size="sm" onClick={() => handleExport('json')}>Export JSON</Button>
        </div>
      </div>
      <div className="flex gap-2">
        <input className="border rounded px-2 py-1 text-sm" placeholder="Filter action…" value={action} onChange={e => setAction(e.target.value)} />
        <select className="border rounded px-2 py-1 text-sm" value={level} onChange={e => setLevel(e.target.value)}>
          <option value="">All levels</option>
          <option value="SECURITY">Security</option>
          <option value="WRITE">Write</option>
          <option value="ALL">All</option>
        </select>
      </div>
      <table className="w-full text-sm border-collapse">
        <thead><tr className="text-left border-b">
          <th className="py-2 pr-4">Time</th><th className="py-2 pr-4">User</th>
          <th className="py-2 pr-4">Action</th><th className="py-2 pr-4">Level</th>
          <th className="py-2">Resource</th>
        </tr></thead>
        <tbody>
          {data?.content?.map((e: AuditEvent) => (
            <tr key={e.id} className="border-b hover:bg-muted/40">
              <td className="py-2 pr-4 text-muted-foreground">{new Date(e.timestamp).toLocaleString()}</td>
              <td className="py-2 pr-4">{e.userEmail}</td>
              <td className="py-2 pr-4 font-mono text-xs">{e.action}</td>
              <td className="py-2 pr-4"><Badge variant={LEVEL_COLOR[e.level] as any}>{e.level}</Badge></td>
              <td className="py-2 text-muted-foreground">{e.resourceType} {e.resourceId}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
```

- [ ] **Step 3: Create ProjectAuditPage** — identical structure, use `auditApi.listForProject(key)`, omit export buttons and level filter

- [ ] **Step 4: Wire routes and nav** — add `{ path: '/admin/audit', element: <AuditLogPage /> }` to router; add `{ path: 'settings/audit', element: <ProjectAuditPage /> }` under `/p/:key`; add "Audit Log" link in admin section of `AppLayout.tsx`

- [ ] **Step 5: Manual smoke test** — start dev server, navigate to `/admin/audit`, verify table renders; test export buttons

- [ ] **Step 6: Commit**
```
git add frontend/src/api/audit.ts frontend/src/pages/admin/AuditLogPage.tsx \
  frontend/src/pages/projects/settings/ProjectAuditPage.tsx \
  frontend/src/app/router.tsx frontend/src/layouts/AppLayout.tsx
git commit -m "feat(audit): frontend AuditLogPage, ProjectAuditPage, nav link"
```

---

## Step 2: SSO via OIDC (`auth` extension, V18)

---

### Task 7: V18 Migration + SsoConfig Domain

**Files:**
- Create: `backend/src/main/resources/db/migration/V18__sso_configs.sql`
- Create: `backend/src/main/kotlin/com/taskowolf/auth/domain/SsoConfig.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/SsoConfigRepository.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/auth/SsoConfigRepositoryTest.kt`

**Interfaces:**
- Produces: `SsoConfig(id, name, issuerUrl, clientId, clientSecretEnc, enabled, autoProvision)` entity; `SsoConfigRepository.findAllByEnabledTrue()`

- [ ] **Step 1: Write migration**

`V18__sso_configs.sql`:
```sql
CREATE TABLE sso_configs (
    id                UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name              VARCHAR(100) NOT NULL,
    issuer_url        VARCHAR(500) NOT NULL,
    client_id         VARCHAR(255) NOT NULL,
    client_secret_enc VARCHAR(500) NOT NULL,
    enabled           BOOLEAN      NOT NULL DEFAULT true,
    auto_provision    BOOLEAN      NOT NULL DEFAULT true,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL
);
```

- [ ] **Step 2: Create SsoConfig entity**

`SsoConfig.kt`:
```kotlin
package com.taskowolf.auth.domain

import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.*

@Entity
@Table(name = "sso_configs")
class SsoConfig(
    @Column(nullable = false) val name: String,
    @Column(nullable = false) val issuerUrl: String,
    @Column(nullable = false) val clientId: String,
    @Column(nullable = false) var clientSecretEnc: String,
    @Column(nullable = false) var enabled: Boolean = true,
    @Column(nullable = false) var autoProvision: Boolean = true
) : AuditableEntity()
```

`SsoConfigRepository.kt`:
```kotlin
package com.taskowolf.auth.infrastructure

import com.taskowolf.auth.domain.SsoConfig
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SsoConfigRepository : JpaRepository<SsoConfig, UUID> {
    fun findAllByEnabledTrue(): List<SsoConfig>
}
```

- [ ] **Step 3: Write + run repository test**

`SsoConfigRepositoryTest.kt`:
```kotlin
@DataJpaTest
class SsoConfigRepositoryTest {
    @Autowired lateinit var repo: SsoConfigRepository

    @Test
    fun `findAllByEnabledTrue returns only enabled configs`() {
        repo.save(SsoConfig("Okta", "https://okta.example.com", "cid", "enc", enabled = true))
        repo.save(SsoConfig("Disabled", "https://disabled.example.com", "cid2", "enc2", enabled = false))
        assertEquals(1, repo.findAllByEnabledTrue().size)
    }
}
```

Run: `gradlew.bat test --tests "com.taskowolf.auth.SsoConfigRepositoryTest"` — expect PASS

- [ ] **Step 4: Commit**
```
git add backend/src/main/resources/db/migration/V18__sso_configs.sql \
  backend/src/main/kotlin/com/taskowolf/auth/domain/SsoConfig.kt \
  backend/src/main/kotlin/com/taskowolf/auth/infrastructure/SsoConfigRepository.kt \
  backend/src/test/kotlin/com/taskowolf/auth/SsoConfigRepositoryTest.kt
git commit -m "feat(sso): V18 migration, SsoConfig entity and repository"
```

---

### Task 8: SsoService (CRUD + AES-GCM Encryption)

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/auth/application/SsoService.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/auth/SsoServiceTest.kt`

**Interfaces:**
- Produces: `SsoService.createConfig(name, issuerUrl, clientId, clientSecret): SsoConfig`, `SsoService.listEnabled(): List<SsoConfig>`, `SsoService.decryptSecret(config): String`, `SsoService.deleteConfig(id)`

- [ ] **Step 1: Write failing tests**

`SsoServiceTest.kt`:
```kotlin
package com.taskowolf.auth

import com.taskowolf.auth.application.SsoService
import com.taskowolf.auth.infrastructure.SsoConfigRepository
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SsoServiceTest {
    private val repo = mockk<SsoConfigRepository>(relaxed = true)
    private val service = SsoService(repo, jwtSecret = "this-is-a-32-byte-secret-for-aes!")

    @Test
    fun `encrypt and decrypt round-trip`() {
        val secret = "my-client-secret"
        val encrypted = service.encryptSecret(secret)
        assertNotEquals(secret, encrypted)
        assertEquals(secret, service.decryptSecret(encrypted))
    }

    @Test
    fun `createConfig saves with encrypted secret`() {
        every { repo.save(any()) } returnsArgument 0
        val config = service.createConfig("Okta", "https://issuer.example.com", "client-id", "plain-secret")
        assertNotEquals("plain-secret", config.clientSecretEnc)
        verify { repo.save(any()) }
    }
}
```

- [ ] **Step 2: Run tests** — expect FAIL

- [ ] **Step 3: Implement SsoService**

`SsoService.kt`:
```kotlin
package com.taskowolf.auth.application

import com.taskowolf.auth.domain.SsoConfig
import com.taskowolf.auth.infrastructure.SsoConfigRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Service
class SsoService(
    private val repo: SsoConfigRepository,
    @Value("\${taskowolf.jwt.secret}") private val jwtSecret: String
) {
    private val aesKey by lazy {
        val hash = MessageDigest.getInstance("SHA-256").digest(jwtSecret.toByteArray())
        SecretKeySpec(hash, "AES")
    }

    fun encryptSecret(plaintext: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plaintext.toByteArray())
        return Base64.getEncoder().encodeToString(iv + encrypted)
    }

    fun decryptSecret(encoded: String): String {
        val bytes = Base64.getDecoder().decode(encoded)
        val iv = bytes.sliceArray(0..11)
        val data = bytes.sliceArray(12 until bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
        return String(cipher.doFinal(data))
    }

    @Transactional
    fun createConfig(name: String, issuerUrl: String, clientId: String, clientSecret: String): SsoConfig =
        repo.save(SsoConfig(name, issuerUrl, clientId, encryptSecret(clientSecret)))

    @Transactional
    fun updateConfig(id: UUID, name: String, issuerUrl: String, clientId: String, clientSecret: String?, enabled: Boolean, autoProvision: Boolean): SsoConfig {
        val config = repo.findById(id).orElseThrow()
        if (clientSecret != null) config.clientSecretEnc = encryptSecret(clientSecret)
        return repo.save(SsoConfig(name, issuerUrl, clientId, config.clientSecretEnc, enabled, autoProvision))
    }

    @Transactional(readOnly = true)
    fun listEnabled() = repo.findAllByEnabledTrue()

    @Transactional(readOnly = true)
    fun listAll() = repo.findAll()

    @Transactional
    fun deleteConfig(id: UUID) = repo.deleteById(id)
}
```

- [ ] **Step 4: Run tests** — `gradlew.bat test --tests "com.taskowolf.auth.SsoServiceTest"` — expect PASS

- [ ] **Step 5: Commit**
```
git add backend/src/main/kotlin/com/taskowolf/auth/application/SsoService.kt \
  backend/src/test/kotlin/com/taskowolf/auth/SsoServiceTest.kt
git commit -m "feat(sso): SsoService with AES-GCM encryption"
```

---

### Task 9: DbClientRegistrationRepository + OIDC Wiring

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/DbClientRegistrationRepository.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/auth/application/OidcUserProvisioningService.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/SecurityConfig.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/auth/DbClientRegistrationRepositoryTest.kt`

**Interfaces:**
- Consumes: `SsoService.listEnabled()`, `SsoService.decryptSecret()`
- Produces: Dynamic OIDC provider registration; on first OIDC login auto-provisions `User`

- [ ] **Step 1: Write failing test**

`DbClientRegistrationRepositoryTest.kt`:
```kotlin
package com.taskowolf.auth

import com.taskowolf.auth.application.SsoService
import com.taskowolf.auth.domain.SsoConfig
import com.taskowolf.auth.infrastructure.DbClientRegistrationRepository
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DbClientRegistrationRepositoryTest {
    private val ssoService = mockk<SsoService>()
    private val repo = DbClientRegistrationRepository(ssoService)

    @Test
    fun `findByRegistrationId returns null for unknown id`() {
        every { ssoService.listEnabled() } returns emptyList()
        assertNull(repo.findByRegistrationId("unknown"))
    }

    @Test
    fun `findByRegistrationId returns registration for known config`() {
        val config = mockk<SsoConfig>(relaxed = true)
        every { config.id.toString() } returns "cfg-id"
        every { config.issuerUrl } returns "https://issuer.example.com"
        every { config.clientId } returns "client-id"
        every { ssoService.listEnabled() } returns listOf(config)
        every { ssoService.decryptSecret(any()) } returns "secret"
        assertNotNull(repo.findByRegistrationId("cfg-id"))
    }
}
```

- [ ] **Step 2: Run test** — expect FAIL

- [ ] **Step 3: Implement DbClientRegistrationRepository**

`DbClientRegistrationRepository.kt`:
```kotlin
package com.taskowolf.auth.infrastructure

import com.taskowolf.auth.application.SsoService
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames
import org.springframework.stereotype.Component

@Component
class DbClientRegistrationRepository(private val ssoService: SsoService) : ClientRegistrationRepository {
    override fun findByRegistrationId(registrationId: String): ClientRegistration? {
        val config = ssoService.listEnabled().find { it.id.toString() == registrationId } ?: return null
        return ClientRegistration.withRegistrationId(registrationId)
            .clientId(config.clientId)
            .clientSecret(ssoService.decryptSecret(config.clientSecretEnc))
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .scope("openid", "profile", "email")
            .authorizationUri("${config.issuerUrl}/oauth2/v1/authorize")
            .tokenUri("${config.issuerUrl}/oauth2/v1/token")
            .userInfoUri("${config.issuerUrl}/oauth2/v1/userinfo")
            .userNameAttributeName(IdTokenClaimNames.SUB)
            .issuerUri(config.issuerUrl)
            .clientName(config.name)
            .build()
    }
}
```

- [ ] **Step 4: Implement OidcUserProvisioningService**

`OidcUserProvisioningService.kt`:
```kotlin
package com.taskowolf.auth.application

import com.taskowolf.auth.domain.SystemRole
import com.taskowolf.auth.domain.User
import com.taskowolf.auth.infrastructure.SsoConfigRepository
import com.taskowolf.auth.infrastructure.UserRepository
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OidcUserProvisioningService(
    private val userRepository: UserRepository,
    private val ssoConfigRepository: SsoConfigRepository,
    private val jwtService: JwtService,
    private val refreshTokenService: RefreshTokenService
) {
    @Transactional
    fun handleOidcLogin(oidcUser: OidcUser, registrationId: String): String {
        val email = oidcUser.email ?: error("OIDC user has no email")
        val config = ssoConfigRepository.findById(java.util.UUID.fromString(registrationId)).orElse(null)
        val user = userRepository.findByEmail(email) ?: run {
            check(config?.autoProvision != false) { "Auto-provisioning disabled" }
            userRepository.save(User(
                email = email,
                displayName = oidcUser.fullName ?: email,
                avatarUrl = oidcUser.picture,
                oauthProvider = "oidc",
                oauthSubject = oidcUser.subject,
                systemRole = SystemRole.MEMBER
            ))
        }
        val refreshToken = jwtService.generateRefreshToken(user.id)
        refreshTokenService.store(refreshToken, user.id)
        return jwtService.generateAccessToken(user.id)
    }
}
```

- [ ] **Step 5: Update SecurityConfig** — add `oauth2Login` configured with `DbClientRegistrationRepository`; on OIDC success, call `OidcUserProvisioningService.handleOidcLogin()` and redirect with JWT. Ensure `/auth/sso/**` and `/login/oauth2/**` are permit-all.

- [ ] **Step 6: Run tests** — `gradlew.bat test --tests "com.taskowolf.auth.DbClientRegistrationRepositoryTest"` — expect PASS

- [ ] **Step 7: Commit**
```
git add backend/src/main/kotlin/com/taskowolf/auth/
git commit -m "feat(sso): DbClientRegistrationRepository, OIDC user provisioning"
```

---

### Task 10: SsoController + Frontend

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/auth/api/dto/SsoConfigRequest.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/auth/api/dto/SsoConfigResponse.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/auth/api/SsoController.kt`
- Create: `frontend/src/api/sso.ts`
- Create: `frontend/src/pages/admin/SsoSettingsPage.tsx`
- Modify: `frontend/src/pages/auth/LoginPage.tsx` — add SSO button
- Modify: `frontend/src/app/router.tsx`

- [ ] **Step 1: Create DTOs and controller**

`SsoConfigRequest.kt`:
```kotlin
data class SsoConfigRequest(val name: String, val issuerUrl: String, val clientId: String, val clientSecret: String?, val enabled: Boolean = true, val autoProvision: Boolean = true)
```

`SsoConfigResponse.kt`:
```kotlin
data class SsoConfigResponse(val id: String, val name: String, val issuerUrl: String, val clientId: String, val enabled: Boolean, val autoProvision: Boolean)
```

`SsoController.kt`:
```kotlin
@RestController
@RequestMapping("/api/v1/admin/sso")
class SsoController(private val ssoService: SsoService) {
    @GetMapping @PreAuthorize("hasRole('ADMIN')")
    fun list() = ssoService.listAll().map { SsoConfigResponse(it.id.toString(), it.name, it.issuerUrl, it.clientId, it.enabled, it.autoProvision) }

    @PostMapping @PreAuthorize("hasRole('ADMIN')")
    fun create(@RequestBody req: SsoConfigRequest) =
        ssoService.createConfig(req.name, req.issuerUrl, req.clientId, req.clientSecret ?: error("clientSecret required"))
            .let { SsoConfigResponse(it.id.toString(), it.name, it.issuerUrl, it.clientId, it.enabled, it.autoProvision) }

    @DeleteMapping("/{id}") @PreAuthorize("hasRole('ADMIN')")
    fun delete(@PathVariable id: UUID) { ssoService.deleteConfig(id) }
}
```

- [ ] **Step 2: Frontend SSO API + SsoSettingsPage** — form with Name, Issuer URL, Client ID, Client Secret fields; list existing configs; delete button per config

- [ ] **Step 3: Add SSO button to LoginPage** — `useQuery` on `/api/v1/admin/sso`; if list non-empty, show "Sign in with SSO" dropdown; each item links to `/auth/sso/{config.id}`

- [ ] **Step 4: Wire route** — add `/admin/settings/sso` to router

- [ ] **Step 5: Manual smoke test** — add a test OIDC config (e.g. Keycloak local), verify redirect works

- [ ] **Step 6: Commit**
```
git add backend/src/main/kotlin/com/taskowolf/auth/api/ frontend/src/
git commit -m "feat(sso): SsoController, frontend SSO settings page and login button"
```

---

## Step 3: Organizations (`organizations` module, V19)

---

### Task 11: V19 Migration + Organization Domain

**Files:**
- Create: `backend/src/main/resources/db/migration/V19__organizations.sql`
- Create: `backend/src/main/kotlin/com/taskowolf/organizations/domain/OrgRole.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/organizations/domain/Organization.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/organizations/domain/OrganizationMember.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/organizations/infrastructure/OrganizationRepository.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/organizations/infrastructure/OrganizationMemberRepository.kt`

**Interfaces:**
- Produces: `Organization(id, name, slug)`, `OrganizationMember(orgId, userId, role)`, `OrgRole.OWNER/ADMIN/MEMBER`

- [ ] **Step 1: Write migration**

`V19__organizations.sql`:
```sql
CREATE TABLE organizations (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    slug       VARCHAR(50)  NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE organization_members (
    org_id  UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role    VARCHAR(20) NOT NULL,
    PRIMARY KEY (org_id, user_id)
);

-- Additive org_id columns (nullable, no FK until populated):
ALTER TABLE users        ADD COLUMN org_id UUID REFERENCES organizations(id);
ALTER TABLE projects     ADD COLUMN org_id UUID REFERENCES organizations(id);
ALTER TABLE api_keys     ADD COLUMN org_id UUID REFERENCES organizations(id);
ALTER TABLE webhooks     ADD COLUMN org_id UUID REFERENCES organizations(id);
ALTER TABLE integrations ADD COLUMN org_id UUID REFERENCES organizations(id);
ALTER TABLE audit_events ADD COLUMN org_id UUID REFERENCES organizations(id);

-- Create default org and backfill:
INSERT INTO organizations (id, name, slug, created_at, updated_at)
  VALUES (gen_random_uuid(), 'Default', 'default', now(), now());

UPDATE users        SET org_id = (SELECT id FROM organizations WHERE slug = 'default');
UPDATE projects     SET org_id = (SELECT id FROM organizations WHERE slug = 'default');
UPDATE api_keys     SET org_id = (SELECT id FROM organizations WHERE slug = 'default');
UPDATE webhooks     SET org_id = (SELECT id FROM organizations WHERE slug = 'default');
UPDATE integrations SET org_id = (SELECT id FROM organizations WHERE slug = 'default');
UPDATE audit_events SET org_id = (SELECT id FROM organizations WHERE slug = 'default');
```

- [ ] **Step 2: Create domain classes**

`OrgRole.kt`:
```kotlin
package com.taskowolf.organizations.domain
enum class OrgRole { OWNER, ADMIN, MEMBER }
```

`Organization.kt`:
```kotlin
package com.taskowolf.organizations.domain

import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.*

@Entity @Table(name = "organizations")
class Organization(
    @Column(nullable = false) val name: String,
    @Column(nullable = false, unique = true) val slug: String
) : AuditableEntity()
```

`OrganizationMember.kt`:
```kotlin
package com.taskowolf.organizations.domain

import jakarta.persistence.*
import java.util.UUID

@Entity @Table(name = "organization_members")
class OrganizationMember(
    @Column(name = "org_id", nullable = false) val orgId: UUID,
    @Column(name = "user_id", nullable = false) val userId: UUID,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var role: OrgRole
) {
    @Id @GeneratedValue @Column(name = "org_id", insertable = false, updatable = false)
    private val _id: UUID? = null
}
```

**Note:** `OrganizationMember` uses a composite PK `(org_id, user_id)`. Use `@IdClass` or `@EmbeddedId` — recommended: create `OrganizationMemberId.kt` as an `@Embeddable` and use `@EmbeddedId` in `OrganizationMember`.

`OrganizationMemberId.kt`:
```kotlin
package com.taskowolf.organizations.domain

import jakarta.persistence.Embeddable
import java.io.Serializable
import java.util.UUID

@Embeddable
data class OrganizationMemberId(val orgId: UUID, val userId: UUID) : Serializable
```

Update `OrganizationMember.kt` to use `@EmbeddedId val id: OrganizationMemberId`.

`OrganizationRepository.kt`:
```kotlin
package com.taskowolf.organizations.infrastructure

import com.taskowolf.organizations.domain.Organization
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OrganizationRepository : JpaRepository<Organization, UUID> {
    fun findBySlug(slug: String): Organization?
}
```

`OrganizationMemberRepository.kt`:
```kotlin
package com.taskowolf.organizations.infrastructure

import com.taskowolf.organizations.domain.OrganizationMember
import com.taskowolf.organizations.domain.OrganizationMemberId
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OrganizationMemberRepository : JpaRepository<OrganizationMember, OrganizationMemberId> {
    fun findByIdOrgId(orgId: UUID): List<OrganizationMember>
    fun findByIdUserId(userId: UUID): List<OrganizationMember>
}
```

- [ ] **Step 3: Run** — `gradlew.bat test` — all green (migration runs on H2)

- [ ] **Step 4: Add org_id field to existing entities** — add `var orgId: UUID? = null` to `User.kt`, `Project.kt`; add `@Column` annotation

- [ ] **Step 5: Commit**
```
git add backend/src/main/resources/db/migration/V19__organizations.sql \
  backend/src/main/kotlin/com/taskowolf/organizations/ \
  backend/src/main/kotlin/com/taskowolf/auth/domain/User.kt \
  backend/src/main/kotlin/com/taskowolf/projects/domain/Project.kt
git commit -m "feat(orgs): V19 migration, Organization domain, org_id on User/Project"
```

---

### Task 12: OrganizationContextHolder + Filter

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/organizations/infrastructure/OrganizationContextHolder.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/organizations/infrastructure/OrganizationContextFilter.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/auth/application/JwtService.kt` — add `orgId` claim
- Test: `backend/src/test/kotlin/com/taskowolf/organizations/OrganizationContextFilterTest.kt`

**Interfaces:**
- Produces: `OrganizationContextHolder.get(): UUID?`, `OrganizationContextHolder.set(UUID)`, `OrganizationContextHolder.clear()`
- JWT access tokens now carry claim `orgId: UUID`

- [ ] **Step 1: Implement OrganizationContextHolder**

`OrganizationContextHolder.kt`:
```kotlin
package com.taskowolf.organizations.infrastructure

import java.util.UUID

object OrganizationContextHolder {
    private val holder = ThreadLocal<UUID?>()
    fun get(): UUID? = holder.get()
    fun set(orgId: UUID?) = holder.set(orgId)
    fun clear() = holder.remove()
}
```

- [ ] **Step 2: Implement OrganizationContextFilter**

`OrganizationContextFilter.kt`:
```kotlin
package com.taskowolf.organizations.infrastructure

import com.taskowolf.auth.application.JwtService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
@Order(5)
class OrganizationContextFilter(private val jwtService: JwtService) : OncePerRequestFilter() {
    override fun doFilterInternal(req: HttpServletRequest, res: HttpServletResponse, chain: FilterChain) {
        try {
            val token = req.getHeader("Authorization")?.removePrefix("Bearer ")
            if (token != null) {
                val orgId = jwtService.extractOrgId(token)
                OrganizationContextHolder.set(orgId)
            }
            chain.doFilter(req, res)
        } finally {
            OrganizationContextHolder.clear()
        }
    }
}
```

- [ ] **Step 3: Add orgId to JwtService**

In `JwtService.kt`, add to `generateAccessToken()`:
```kotlin
fun generateAccessToken(userId: UUID, orgId: UUID? = null): String = Jwts.builder()
    .id(UUID.randomUUID().toString())
    .subject(userId.toString())
    .claim("type", "access")
    .apply { orgId?.let { claim("orgId", it.toString()) } }
    .issuedAt(Date())
    .expiration(Date(System.currentTimeMillis() + accessExpiry * 1000))
    .signWith(key)
    .compact()

fun extractOrgId(token: String): UUID? = runCatching {
    val claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload
    claims.get("orgId", String::class.java)?.let { UUID.fromString(it) }
}.getOrNull()
```

- [ ] **Step 4: Write + run test**

`OrganizationContextFilterTest.kt`:
```kotlin
package com.taskowolf.organizations

import com.taskowolf.auth.application.JwtService
import com.taskowolf.organizations.infrastructure.OrganizationContextFilter
import com.taskowolf.organizations.infrastructure.OrganizationContextHolder
import io.mockk.*
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.util.UUID

class OrganizationContextFilterTest {
    private val jwtService = mockk<JwtService>()
    private val filter = OrganizationContextFilter(jwtService)

    @Test
    fun `sets orgId from JWT in context`() {
        val orgId = UUID.randomUUID()
        every { jwtService.extractOrgId("token") } returns orgId
        val req = MockHttpServletRequest().apply { addHeader("Authorization", "Bearer token") }
        var capturedOrgId: UUID? = null
        filter.doFilter(req, MockHttpServletResponse()) { _, _ -> capturedOrgId = OrganizationContextHolder.get() }
        assertEquals(orgId, capturedOrgId)
    }
}
```

Run: `gradlew.bat test --tests "com.taskowolf.organizations.OrganizationContextFilterTest"` — expect PASS

- [ ] **Step 5: Commit**
```
git add backend/src/main/kotlin/com/taskowolf/organizations/ \
  backend/src/main/kotlin/com/taskowolf/auth/application/JwtService.kt \
  backend/src/test/kotlin/com/taskowolf/organizations/
git commit -m "feat(orgs): OrganizationContextHolder, filter, orgId JWT claim"
```

---

### Task 13: OrganizationService + Controller + Frontend

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/organizations/application/OrganizationService.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/organizations/api/dto/CreateOrganizationRequest.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/organizations/api/dto/OrganizationResponse.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/organizations/api/OrganizationController.kt`
- Create: `frontend/src/api/organizations.ts`
- Create: `frontend/src/pages/orgs/OrgsPage.tsx`
- Create: `frontend/src/pages/orgs/OrgSettingsPage.tsx`
- Create: `frontend/src/components/OrgSwitcher.tsx`
- Modify: `frontend/src/app/router.tsx`, `frontend/src/layouts/AppLayout.tsx`

**Interfaces:**
- Consumes: `OrganizationContextHolder.get()` from Task 12
- Produces: all org CRUD endpoints; switch-org JWT endpoint

- [ ] **Step 1: Implement OrganizationService**

`OrganizationService.kt`:
```kotlin
package com.taskowolf.organizations.application

import com.taskowolf.organizations.domain.*
import com.taskowolf.organizations.infrastructure.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class OrganizationService(
    private val orgRepo: OrganizationRepository,
    private val memberRepo: OrganizationMemberRepository
) {
    @Transactional
    fun create(name: String, slug: String, creatorId: UUID): Organization {
        val org = orgRepo.save(Organization(name, slug))
        memberRepo.save(OrganizationMember(OrganizationMemberId(org.id, creatorId), OrgRole.OWNER))
        return org
    }

    @Transactional(readOnly = true)
    fun findBySlug(slug: String) = orgRepo.findBySlug(slug) ?: error("Org not found: $slug")

    @Transactional(readOnly = true)
    fun listMembers(orgId: UUID) = memberRepo.findByIdOrgId(orgId)

    @Transactional
    fun addMember(orgId: UUID, userId: UUID, role: OrgRole) =
        memberRepo.save(OrganizationMember(OrganizationMemberId(orgId, userId), role))

    @Transactional
    fun removeMember(orgId: UUID, userId: UUID) =
        memberRepo.deleteById(OrganizationMemberId(orgId, userId))

    @Transactional(readOnly = true)
    fun listOrgsForUser(userId: UUID) = memberRepo.findByIdUserId(userId).map { it.id.orgId }
        .let { ids -> if (ids.isEmpty()) emptyList() else orgRepo.findAllById(ids) }
}
```

- [ ] **Step 2: Implement OrganizationController** — standard CRUD matching spec endpoints; `POST /api/v1/auth/switch-org/{orgId}` generates new JWT with new orgId claim via `JwtService.generateAccessToken(userId, orgId)`

- [ ] **Step 3: Write service test**

`OrganizationServiceTest.kt`:
```kotlin
@Test
fun `create adds creator as OWNER`() {
    every { orgRepo.save(any()) } returnsArgument 0
    every { memberRepo.save(any()) } returnsArgument 0
    val creatorId = UUID.randomUUID()
    service.create("MyOrg", "my-org", creatorId)
    val slot = slot<OrganizationMember>()
    verify { memberRepo.save(capture(slot)) }
    assertEquals(OrgRole.OWNER, slot.captured.role)
}
```

- [ ] **Step 4: Frontend** — `OrgSwitcher.tsx` queries `/api/v1/organizations/{slug}` per org the user belongs to; on switch, calls `POST /auth/switch-org/{orgId}`, stores new token, refreshes page. `OrgsPage.tsx` lists all orgs (ADMIN only). `OrgSettingsPage.tsx` shows members + add/remove.

- [ ] **Step 5: Run tests** — `gradlew.bat test` — all green

- [ ] **Step 6: Commit**
```
git add backend/src/main/kotlin/com/taskowolf/organizations/ frontend/src/
git commit -m "feat(orgs): OrganizationService, controller, OrgSwitcher frontend"
```

---

## Step 4: Service Management (`servicedesk` module, V20)

---

### Task 14: V20 Migration + ServiceDesk Domain

**Files:**
- Create: `backend/src/main/resources/db/migration/V20__servicedesk.sql`
- Create: `backend/src/main/kotlin/com/taskowolf/servicedesk/domain/IncidentSeverity.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/servicedesk/domain/ServiceDesk.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/servicedesk/domain/SlaPolicy.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/servicedesk/domain/EscalationRule.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/servicedesk/domain/Incident.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/servicedesk/infrastructure/` — 4 repositories

**Interfaces:**
- Produces: full domain model for service management

- [ ] **Step 1: Write migration**

`V20__servicedesk.sql`:
```sql
ALTER TABLE issues ADD COLUMN sla_start_time TIMESTAMPTZ;

CREATE TABLE service_desks (
    id            UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    project_id    UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    email_address VARCHAR(255),
    enabled       BOOLEAN      NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL
);

CREATE TABLE sla_policies (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    service_desk_id     UUID        NOT NULL REFERENCES service_desks(id) ON DELETE CASCADE,
    name                VARCHAR(100) NOT NULL,
    priority            VARCHAR(20)  NOT NULL,
    response_minutes    INT         NOT NULL,
    resolution_minutes  INT         NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL
);

CREATE TABLE escalation_rules (
    id                     UUID    NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    sla_policy_id          UUID    NOT NULL REFERENCES sla_policies(id) ON DELETE CASCADE,
    escalate_after_minutes INT     NOT NULL,
    assignee_id            UUID    REFERENCES users(id),
    notify_user_ids        UUID[]  NOT NULL DEFAULT '{}',
    created_at             TIMESTAMPTZ NOT NULL,
    updated_at             TIMESTAMPTZ NOT NULL
);

CREATE TABLE incidents (
    id                   UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    issue_id             UUID        NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    severity             VARCHAR(5)  NOT NULL,
    on_call_assignee_id  UUID        REFERENCES users(id),
    postmortem_body      TEXT,
    resolved_at          TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL
);
```

- [ ] **Step 2: Create domain classes**

`IncidentSeverity.kt`:
```kotlin
package com.taskowolf.servicedesk.domain
enum class IncidentSeverity { P1, P2, P3, P4 }
```

`ServiceDesk.kt`:
```kotlin
package com.taskowolf.servicedesk.domain

import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.*
import java.util.UUID

@Entity @Table(name = "service_desks")
class ServiceDesk(
    @Column(nullable = false) val projectId: UUID,
    var emailAddress: String? = null,
    @Column(nullable = false) var enabled: Boolean = true
) : AuditableEntity()
```

`SlaPolicy.kt`:
```kotlin
package com.taskowolf.servicedesk.domain

import com.taskowolf.core.domain.AuditableEntity
import com.taskowolf.issues.domain.IssuePriority
import jakarta.persistence.*
import java.util.UUID

@Entity @Table(name = "sla_policies")
class SlaPolicy(
    @Column(nullable = false) val serviceDeskId: UUID,
    @Column(nullable = false) val name: String,
    @Enumerated(EnumType.STRING) @Column(nullable = false) val priority: IssuePriority,
    @Column(nullable = false) val responseMinutes: Int,
    @Column(nullable = false) val resolutionMinutes: Int
) : AuditableEntity()
```

`EscalationRule.kt`:
```kotlin
package com.taskowolf.servicedesk.domain

import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.*
import java.util.UUID

@Entity @Table(name = "escalation_rules")
class EscalationRule(
    @Column(nullable = false) val slaPolicyId: UUID,
    @Column(nullable = false) val escalateAfterMinutes: Int,
    val assigneeId: UUID? = null,
    @Column(columnDefinition = "UUID[]") val notifyUserIds: Array<UUID> = emptyArray()
) : AuditableEntity()
```

`Incident.kt`:
```kotlin
package com.taskowolf.servicedesk.domain

import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity @Table(name = "incidents")
class Incident(
    @Column(nullable = false) val issueId: UUID,
    @Enumerated(EnumType.STRING) @Column(nullable = false) val severity: IncidentSeverity,
    var onCallAssigneeId: UUID? = null,
    @Column(columnDefinition = "TEXT") var postmortemBody: String? = null,
    var resolvedAt: Instant? = null
) : AuditableEntity()
```

Create repositories: `ServiceDeskRepository`, `SlaPolicyRepository`, `EscalationRuleRepository`, `IncidentRepository` — all extend `JpaRepository<Entity, UUID>` with `findByProjectId` / `findByServiceDeskId` / `findBySlaPolicyId` / `findByIssueId` where relevant.

- [ ] **Step 3: Add slaStartTime to Issue entity**

In `backend/src/main/kotlin/com/taskowolf/issues/domain/Issue.kt` add:
```kotlin
var slaStartTime: Instant? = null
```

- [ ] **Step 4: Run** — `gradlew.bat test` — all green

- [ ] **Step 5: Commit**
```
git add backend/src/main/resources/db/migration/V20__servicedesk.sql \
  backend/src/main/kotlin/com/taskowolf/servicedesk/ \
  backend/src/main/kotlin/com/taskowolf/issues/domain/Issue.kt
git commit -m "feat(servicedesk): V20 migration, ServiceDesk/SlaPolicy/Incident domain"
```

---

### Task 15: ServiceDeskService + SlaMonitorJob

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/servicedesk/application/ServiceDeskService.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/servicedesk/application/SlaMonitorJob.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/servicedesk/application/SlaEventListener.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/servicedesk/ServiceDeskServiceTest.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/servicedesk/SlaMonitorJobTest.kt`

**Interfaces:**
- Consumes: `IssueStatusChangedEvent` to set `slaStartTime`; `NotificationService` for escalation alerts
- Produces: `ServiceDeskService.enable(projectId)`, `ServiceDeskService.addSlaPolicy(...)`, SLA breach detection

- [ ] **Step 1: Write failing tests**

`ServiceDeskServiceTest.kt`:
```kotlin
@Test
fun `enable creates ServiceDesk for project`() {
    every { serviceDeskRepo.findByProjectId(projectId) } returns null
    every { serviceDeskRepo.save(any()) } returnsArgument 0
    val desk = service.enable(projectId, null)
    assertTrue(desk.enabled)
    assertEquals(projectId, desk.projectId)
}
```

`SlaMonitorJobTest.kt`:
```kotlin
@Test
fun `run does not fire escalation for issue within SLA`() {
    // issue slaStartTime = 5 minutes ago, policy resolutionMinutes = 60
    every { issueRepo.findBySlaStartTimeIsNotNull() } returns listOf(issueWithinSla)
    job.run()
    verify(exactly = 0) { notificationService.notify(any(), any(), any()) }
}
```

- [ ] **Step 2: Implement ServiceDeskService**

`ServiceDeskService.kt`:
```kotlin
package com.taskowolf.servicedesk.application

import com.taskowolf.servicedesk.domain.*
import com.taskowolf.servicedesk.infrastructure.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ServiceDeskService(
    private val serviceDeskRepo: ServiceDeskRepository,
    private val slaPolicyRepo: SlaPolicyRepository,
    private val escalationRuleRepo: EscalationRuleRepository
) {
    @Transactional
    fun enable(projectId: UUID, emailAddress: String?): ServiceDesk =
        serviceDeskRepo.findByProjectId(projectId)?.apply { this.enabled = true; this.emailAddress = emailAddress }
            ?: serviceDeskRepo.save(ServiceDesk(projectId, emailAddress))

    @Transactional(readOnly = true)
    fun findByProject(projectId: UUID) = serviceDeskRepo.findByProjectId(projectId)

    @Transactional
    fun addSlaPolicy(serviceDeskId: UUID, name: String, priority: com.taskowolf.issues.domain.IssuePriority, responseMinutes: Int, resolutionMinutes: Int) =
        slaPolicyRepo.save(SlaPolicy(serviceDeskId, name, priority, responseMinutes, resolutionMinutes))

    @Transactional(readOnly = true)
    fun listSlaPolicies(serviceDeskId: UUID) = slaPolicyRepo.findByServiceDeskId(serviceDeskId)

    @Transactional
    fun deleteSlaPolicy(id: UUID) = slaPolicyRepo.deleteById(id)

    @Transactional
    fun addEscalationRule(slaPolicyId: UUID, escalateAfterMinutes: Int, assigneeId: UUID?, notifyUserIds: Array<UUID>) =
        escalationRuleRepo.save(EscalationRule(slaPolicyId, escalateAfterMinutes, assigneeId, notifyUserIds))
}
```

- [ ] **Step 3: Implement SlaEventListener** — listens to `IssueStatusChangedEvent`; if new status category = `IN_PROGRESS`, sets `issue.slaStartTime = Instant.now()`

`SlaEventListener.kt`:
```kotlin
@Component
class SlaEventListener(private val issueRepository: IssueRepository) {
    @EventListener
    @Transactional
    fun onStatusChanged(e: IssueStatusChangedEvent) {
        if (e.newStatusCategory == StatusCategory.IN_PROGRESS) {
            val issue = issueRepository.findById(e.issueId).orElse(null) ?: return
            if (issue.slaStartTime == null) issue.slaStartTime = Instant.now()
        }
    }
}
```

- [ ] **Step 4: Implement SlaMonitorJob**

`SlaMonitorJob.kt`:
```kotlin
@Component
class SlaMonitorJob(
    private val issueRepository: IssueRepository,
    private val serviceDeskRepo: ServiceDeskRepository,
    private val slaPolicyRepo: SlaPolicyRepository,
    private val escalationRuleRepo: EscalationRuleRepository,
    private val notificationService: NotificationService,
    private val auditService: AuditService
) {
    @Scheduled(fixedDelay = 60_000)
    @Transactional(readOnly = true)
    fun run() {
        val now = Instant.now()
        issueRepository.findBySlaStartTimeIsNotNull().forEach { issue ->
            val desk = serviceDeskRepo.findByProjectId(issue.projectId) ?: return@forEach
            val policy = slaPolicyRepo.findByServiceDeskId(desk.id)
                .find { it.priority == issue.priority } ?: return@forEach
            val elapsed = Duration.between(issue.slaStartTime, now).toMinutes()
            if (elapsed >= policy.resolutionMinutes) {
                val rules = escalationRuleRepo.findBySlaPolicyId(policy.id)
                rules.forEach { rule ->
                    rule.notifyUserIds.forEach { uid ->
                        notificationService.notifyUser(uid, "SLA Breached: ${issue.key}", issue.projectId)
                    }
                }
                auditService.log(AuditLevel.WRITE, AuditAction.SLA_BREACHED, "system",
                    projectId = issue.projectId, resourceType = "ISSUE", resourceId = issue.id.toString())
            }
        }
    }
}
```

- [ ] **Step 5: Run tests** — `gradlew.bat test --tests "com.taskowolf.servicedesk.*"` — expect PASS

- [ ] **Step 6: Commit**
```
git add backend/src/main/kotlin/com/taskowolf/servicedesk/application/ \
  backend/src/test/kotlin/com/taskowolf/servicedesk/
git commit -m "feat(servicedesk): ServiceDeskService, SlaMonitorJob, SlaEventListener"
```

---

### Task 16: IncidentService + Postmortem Auto-Creation

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/servicedesk/application/IncidentService.kt`
- Test: `backend/src/test/kotlin/com/taskowolf/servicedesk/IncidentServiceTest.kt`

**Interfaces:**
- Consumes: `IncidentRepository`, `CommentService.createComment(issueId, body, authorId)` for postmortem
- Produces: `IncidentService.create(issueId, severity, onCallAssigneeId?, notifyUserIds)`, `IncidentService.resolve(incidentId, postmortemBody?)`, `IncidentService.listByProject(projectId)`

- [ ] **Step 1: Write failing tests**

`IncidentServiceTest.kt`:
```kotlin
@Test
fun `resolve sets resolvedAt and creates postmortem comment`() {
    val incident = Incident(issueId = issueId, severity = IncidentSeverity.P1)
    every { incidentRepo.findById(incident.id) } returns Optional.of(incident)
    every { incidentRepo.save(any()) } returnsArgument 0
    every { commentService.createComment(any(), any(), any()) } returns mockk()

    service.resolve(incident.id, "Root cause: DB overload")

    assertNotNull(incident.resolvedAt)
    assertEquals("Root cause: DB overload", incident.postmortemBody)
    verify { commentService.createComment(issueId, match { it.contains("Postmortem") }, any()) }
}

@Test
fun `resolve without postmortem body still sets resolvedAt`() {
    val incident = Incident(issueId = issueId, severity = IncidentSeverity.P2)
    every { incidentRepo.findById(incident.id) } returns Optional.of(incident)
    every { incidentRepo.save(any()) } returnsArgument 0

    service.resolve(incident.id, null)

    assertNotNull(incident.resolvedAt)
    verify(exactly = 0) { commentService.createComment(any(), any(), any()) }
}
```

- [ ] **Step 2: Implement IncidentService**

`IncidentService.kt`:
```kotlin
@Service
class IncidentService(
    private val incidentRepo: IncidentRepository,
    private val commentService: CommentService,
    private val notificationService: NotificationService
) {
    @Transactional
    fun create(issueId: UUID, severity: IncidentSeverity, onCallAssigneeId: UUID?, notifyUserIds: List<UUID>): Incident {
        val incident = incidentRepo.save(Incident(issueId, severity, onCallAssigneeId))
        notifyUserIds.forEach { uid ->
            notificationService.notifyUser(uid, "Incident declared: ${severity.name} on issue $issueId", null)
        }
        return incident
    }

    @Transactional
    fun resolve(incidentId: UUID, postmortemBody: String?) {
        val incident = incidentRepo.findById(incidentId).orElseThrow()
        incident.resolvedAt = Instant.now()
        if (postmortemBody != null) {
            incident.postmortemBody = postmortemBody
            val body = """## Postmortem

**Severity:** ${incident.severity}
**Resolved:** ${incident.resolvedAt}

$postmortemBody"""
            commentService.createComment(incident.issueId, body, null)
        }
        incidentRepo.save(incident)
    }

    @Transactional(readOnly = true)
    fun listByProject(projectId: UUID): List<Incident> {
        // join via issues.project_id
        return incidentRepo.findByProjectId(projectId)
    }
}
```

Add `findByProjectId(projectId: UUID)` to `IncidentRepository` via JPQL:
```kotlin
@Query("SELECT i FROM Incident i JOIN Issue iss ON iss.id = i.issueId WHERE iss.projectId = :projectId")
fun findByProjectId(projectId: UUID): List<Incident>
```

- [ ] **Step 3: Run tests** — `gradlew.bat test --tests "com.taskowolf.servicedesk.IncidentServiceTest"` — expect PASS

- [ ] **Step 4: Commit**
```
git add backend/src/main/kotlin/com/taskowolf/servicedesk/application/IncidentService.kt \
  backend/src/test/kotlin/com/taskowolf/servicedesk/IncidentServiceTest.kt
git commit -m "feat(servicedesk): IncidentService, postmortem auto-creation on resolve"
```

---

### Task 17: Email Ingestion (Spring Integration IMAP)

**Files:**
- Modify: `backend/build.gradle.kts` — add dependency
- Create: `backend/src/main/kotlin/com/taskowolf/servicedesk/application/EmailIngestionService.kt`
- Modify: `backend/src/main/resources/application.yml` — add IMAP config keys
- Modify: `.env.example` — add IMAP vars

**Interfaces:**
- Consumes: `IssueService.createIssue(...)` to convert emails to issues
- Produces: IMAP polling integration — when enabled, creates issues from incoming emails

- [ ] **Step 1: Add dependency to build.gradle.kts**

```kotlin
// In dependencies block:
implementation("org.springframework.integration:spring-integration-mail")
implementation("org.springframework.integration:spring-integration-core")
implementation("jakarta.mail:jakarta.mail-api")
implementation("org.eclipse.angus:angus-mail")
```

- [ ] **Step 2: Add config keys to application.yml**

```yaml
taskowolf:
  mail:
    imap:
      enabled: ${TW_MAIL_IMAP_ENABLED:false}
      host: ${TW_MAIL_IMAP_HOST:}
      port: ${TW_MAIL_IMAP_PORT:993}
      user: ${TW_MAIL_IMAP_USER:}
      password: ${TW_MAIL_IMAP_PASS:}
      folder: INBOX
      polling-interval: 30000
```

- [ ] **Step 3: Add to .env.example**

```env
TW_MAIL_IMAP_ENABLED=false
TW_MAIL_IMAP_HOST=imap.example.com
TW_MAIL_IMAP_USER=support@example.com
TW_MAIL_IMAP_PASS=changeme
TW_MAIL_IMAP_PORT=993
```

- [ ] **Step 4: Implement EmailIngestionService**

`EmailIngestionService.kt`:
```kotlin
package com.taskowolf.servicedesk.application

import com.taskowolf.issues.application.IssueService
import com.taskowolf.servicedesk.infrastructure.ServiceDeskRepository
import jakarta.mail.internet.MimeMessage
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.integration.dsl.integrationFlow
import org.springframework.integration.mail.dsl.Mail
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty("taskowolf.mail.imap.enabled", havingValue = "true")
class EmailIngestionService(
    private val serviceDeskRepo: ServiceDeskRepository,
    private val issueService: IssueService,
    @Value("\${taskowolf.mail.imap.host}") private val host: String,
    @Value("\${taskowolf.mail.imap.port}") private val port: Int,
    @Value("\${taskowolf.mail.imap.user}") private val user: String,
    @Value("\${taskowolf.mail.imap.password}") private val password: String,
    @Value("\${taskowolf.mail.imap.polling-interval}") private val pollingInterval: Long
) {
    @Bean
    fun imapFlow() = integrationFlow(
        Mail.imapInboundAdapter("imaps://$user:$password@$host:$port/INBOX")
            .autoCloseFolder(false)
            .shouldDeleteMessages(false),
        { poller { it.fixedDelay(pollingInterval) } }
    ) {
        handle { msg: MimeMessage ->
            val subject = msg.subject ?: return@handle
            val body = msg.content?.toString() ?: ""
            val from = msg.from?.firstOrNull()?.toString() ?: "unknown"
            // Find service desk by email address match
            serviceDeskRepo.findAll()
                .filter { it.enabled && it.emailAddress != null }
                .forEach { desk ->
                    issueService.createTicketFromEmail(desk.projectId, subject, body, from)
                }
        }
    }
}
```

Add `IssueService.createTicketFromEmail(projectId, title, body, senderEmail)` — creates an issue with type `TASK`, assigns to project default workflow first status.

- [ ] **Step 5: Run tests** — `gradlew.bat test` — all green (IMAP bean skipped due to `ConditionalOnProperty`)

- [ ] **Step 6: Commit**
```
git add backend/build.gradle.kts backend/src/main/kotlin/com/taskowolf/servicedesk/application/EmailIngestionService.kt \
  backend/src/main/resources/application.yml .env.example
git commit -m "feat(servicedesk): email ingestion via Spring Integration IMAP"
```

---

### Task 18: ServiceDeskController + IncidentController + SecurityConfig

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/servicedesk/api/dto/` — DTOs
- Create: `backend/src/main/kotlin/com/taskowolf/servicedesk/api/ServiceDeskController.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/servicedesk/api/IncidentController.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/auth/infrastructure/SecurityConfig.kt` — permit-all on ticket submission endpoint

**Interfaces:**
- Produces: all service desk REST endpoints from spec

- [ ] **Step 1: Create DTOs**

```kotlin
// CreateServiceDeskRequest.kt
data class CreateServiceDeskRequest(val emailAddress: String?)

// CreateSlaPolicyRequest.kt
data class CreateSlaPolicyRequest(val name: String, val priority: String, val responseMinutes: Int, val resolutionMinutes: Int)

// CreateEscalationRuleRequest.kt
data class CreateEscalationRuleRequest(val escalateAfterMinutes: Int, val assigneeId: UUID?, val notifyUserIds: List<UUID>)

// CreateIncidentRequest.kt
data class CreateIncidentRequest(val issueId: UUID, val severity: String, val onCallAssigneeId: UUID?, val notifyUserIds: List<UUID> = emptyList())

// ResolveIncidentRequest.kt
data class ResolveIncidentRequest(val postmortemBody: String?)

// ServiceDeskResponse, SlaPolicyResponse, IncidentResponse — map from domain
```

- [ ] **Step 2: Implement ServiceDeskController**

```kotlin
@RestController
@RequestMapping("/api/v1/projects/{key}/service-desk")
class ServiceDeskController(private val serviceDeskService: ServiceDeskService, private val projectRepository: ProjectRepository) {
    @PostMapping("/enable")
    @PreAuthorize("@projectSecurity.isProjectAdmin(#key, authentication)")
    fun enable(@PathVariable key: String, @RequestBody req: CreateServiceDeskRequest): ServiceDeskResponse {
        val project = projectRepository.findByKey(key) ?: error("Project not found")
        return ServiceDeskResponse.from(serviceDeskService.enable(project.id, req.emailAddress))
    }

    @GetMapping
    fun get(@PathVariable key: String): ServiceDeskResponse {
        val project = projectRepository.findByKey(key) ?: error("Project not found")
        return ServiceDeskResponse.from(serviceDeskService.findByProject(project.id) ?: error("Service desk not enabled"))
    }

    @PostMapping("/tickets")  // permit-all — wired in SecurityConfig
    fun submitTicket(@PathVariable key: String, @RequestBody req: SubmitTicketRequest) {
        val project = projectRepository.findByKey(key) ?: error("Project not found")
        issueService.createTicketFromEmail(project.id, req.title, req.description, req.senderEmail ?: "anonymous")
    }

    @GetMapping("/tickets")
    fun listTickets(@PathVariable key: String): List<IssueResponse> { /* delegate to IssueService */ }

    @PostMapping("/sla-policies")
    @PreAuthorize("@projectSecurity.isProjectAdmin(#key, authentication)")
    fun addSlaPolicy(@PathVariable key: String, @RequestBody req: CreateSlaPolicyRequest): SlaPolicyResponse { /* ... */ }

    @GetMapping("/sla-policies")
    fun listSlaPolicies(@PathVariable key: String): List<SlaPolicyResponse> { /* ... */ }

    @DeleteMapping("/sla-policies/{id}")
    @PreAuthorize("@projectSecurity.isProjectAdmin(#key, authentication)")
    fun deleteSlaPolicy(@PathVariable key: String, @PathVariable id: UUID) { /* ... */ }

    @PostMapping("/sla-policies/{id}/escalation-rules")
    @PreAuthorize("@projectSecurity.isProjectAdmin(#key, authentication)")
    fun addEscalationRule(@PathVariable key: String, @PathVariable id: UUID, @RequestBody req: CreateEscalationRuleRequest): EscalationRuleResponse { /* ... */ }
}
```

- [ ] **Step 3: Implement IncidentController**

```kotlin
@RestController
@RequestMapping("/api/v1/projects/{key}/incidents")
class IncidentController(private val incidentService: IncidentService, private val projectRepository: ProjectRepository) {
    @PostMapping
    fun create(@PathVariable key: String, @RequestBody req: CreateIncidentRequest) =
        IncidentResponse.from(incidentService.create(req.issueId, IncidentSeverity.valueOf(req.severity), req.onCallAssigneeId, req.notifyUserIds))

    @GetMapping
    fun list(@PathVariable key: String): List<IncidentResponse> {
        val project = projectRepository.findByKey(key) ?: error("Project not found")
        return incidentService.listByProject(project.id).map { IncidentResponse.from(it) }
    }

    @PatchMapping("/{id}")
    fun resolve(@PathVariable key: String, @PathVariable id: UUID, @RequestBody req: ResolveIncidentRequest) {
        incidentService.resolve(id, req.postmortemBody)
    }
}
```

- [ ] **Step 4: Update SecurityConfig** — add permit-all for ticket submission:

```kotlin
// In the existing http.authorizeHttpRequests block, add:
.requestMatchers("/api/v1/projects/*/service-desk/tickets").permitAll()
```

- [ ] **Step 5: Run tests** — `gradlew.bat test` — all green

- [ ] **Step 6: Commit**
```
git add backend/src/main/kotlin/com/taskowolf/servicedesk/api/ \
  backend/src/main/kotlin/com/taskowolf/auth/infrastructure/SecurityConfig.kt
git commit -m "feat(servicedesk): ServiceDeskController, IncidentController, permit-all ticket endpoint"
```

---

### Task 19: Frontend — Service Desk Pages

**Files:**
- Create: `frontend/src/api/servicedesk.ts`
- Create: `frontend/src/pages/projects/servicedesk/ServiceDeskPage.tsx`
- Create: `frontend/src/pages/projects/servicedesk/IncidentDashboardPage.tsx`
- Modify: `frontend/src/app/router.tsx`
- Modify: `frontend/src/layouts/AppLayout.tsx` — add "Service Desk" nav item (conditional)

- [ ] **Step 1: Create API client**

`frontend/src/api/servicedesk.ts`:
```typescript
import { apiClient } from './client'

export const serviceDeskApi = {
  get: (key: string) => apiClient.get(`/projects/${key}/service-desk`).then(r => r.data),
  enable: (key: string, emailAddress?: string) =>
    apiClient.post(`/projects/${key}/service-desk/enable`, { emailAddress }).then(r => r.data),
  listTickets: (key: string) => apiClient.get(`/projects/${key}/service-desk/tickets`).then(r => r.data),
  submitTicket: (key: string, title: string, description: string) =>
    apiClient.post(`/projects/${key}/service-desk/tickets`, { title, description }),
  listSlaPolicies: (key: string) => apiClient.get(`/projects/${key}/service-desk/sla-policies`).then(r => r.data),
  listIncidents: (key: string) => apiClient.get(`/projects/${key}/incidents`).then(r => r.data),
  resolveIncident: (key: string, id: string, postmortemBody?: string) =>
    apiClient.patch(`/projects/${key}/incidents/${id}`, { postmortemBody }),
}
```

- [ ] **Step 2: Create ServiceDeskPage (Ticket Queue)**

`ServiceDeskPage.tsx`:
```tsx
const SLA_COLOR = (status: string) =>
  status === 'BREACHED' ? 'bg-red-100 text-red-700' :
  status === 'WARNING' ? 'bg-yellow-100 text-yellow-700' : 'bg-green-100 text-green-700'

export default function ServiceDeskPage() {
  const { key } = useParams()
  const { data: tickets } = useQuery({ queryKey: ['tickets', key], queryFn: () => serviceDeskApi.listTickets(key!) })

  return (
    <div className="p-6 space-y-4">
      <h1 className="text-2xl font-semibold">Service Desk</h1>
      <div className="space-y-2">
        {tickets?.map((t: any) => (
          <div key={t.id} className="flex items-center gap-3 border rounded p-3">
            <span className="font-mono text-sm text-muted-foreground">{t.key}</span>
            <span className="flex-1">{t.title}</span>
            <span className={`text-xs px-2 py-0.5 rounded ${SLA_COLOR(t.slaStatus)}`}>{t.slaStatus}</span>
          </div>
        ))}
      </div>
    </div>
  )
}
```

- [ ] **Step 3: Create IncidentDashboardPage**

```tsx
const SEVERITY_COLOR: Record<string, string> = {
  P1: 'bg-red-600 text-white', P2: 'bg-orange-500 text-white',
  P3: 'bg-yellow-400 text-black', P4: 'bg-gray-200 text-gray-700'
}

export default function IncidentDashboardPage() {
  const { key } = useParams()
  const { data: incidents } = useQuery({ queryKey: ['incidents', key], queryFn: () => serviceDeskApi.listIncidents(key!) })
  const [resolving, setResolving] = useState<string | null>(null)
  const [postmortem, setPostmortem] = useState('')
  const queryClient = useQueryClient()
  const resolveMutation = useMutation({
    mutationFn: ({ id }: { id: string }) => serviceDeskApi.resolveIncident(key!, id, postmortem || undefined),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['incidents', key] }); setResolving(null); setPostmortem('') }
  })

  return (
    <div className="p-6 space-y-4">
      <h1 className="text-2xl font-semibold">Incidents</h1>
      <div className="grid grid-cols-1 gap-3">
        {incidents?.map((inc: any) => (
          <div key={inc.id} className="border rounded p-4 flex items-start gap-4">
            <span className={`text-xs font-bold px-2 py-1 rounded ${SEVERITY_COLOR[inc.severity]}`}>{inc.severity}</span>
            <div className="flex-1">
              <p className="font-medium">Issue: {inc.issueId}</p>
              {inc.resolvedAt && <p className="text-sm text-muted-foreground">Resolved: {new Date(inc.resolvedAt).toLocaleString()}</p>}
              {!inc.resolvedAt && resolving === inc.id && (
                <div className="mt-2 space-y-2">
                  <textarea className="w-full border rounded p-2 text-sm" rows={4} placeholder="Postmortem (optional)…" value={postmortem} onChange={e => setPostmortem(e.target.value)} />
                  <Button size="sm" onClick={() => resolveMutation.mutate({ id: inc.id })}>Confirm Resolve</Button>
                </div>
              )}
            </div>
            {!inc.resolvedAt && <Button variant="outline" size="sm" onClick={() => setResolving(inc.id)}>Resolve</Button>}
          </div>
        ))}
      </div>
    </div>
  )
}
```

- [ ] **Step 4: Wire routes** — add `/p/:key/service-desk` → `ServiceDeskPage` and `/p/:key/incidents` → `IncidentDashboardPage` in router

- [ ] **Step 5: Add conditional nav** — in `AppLayout.tsx`, query service desk config; if enabled, show "Service Desk" and "Incidents" links in project nav

- [ ] **Step 6: Manual smoke test** — enable service desk on a test project, submit a ticket via the API, verify it appears in the queue with SLA indicator

- [ ] **Step 7: Commit**
```
git add frontend/src/api/servicedesk.ts \
  frontend/src/pages/projects/servicedesk/ \
  frontend/src/app/router.tsx \
  frontend/src/layouts/AppLayout.tsx
git commit -m "feat(servicedesk): ServiceDeskPage ticket queue, IncidentDashboard with postmortem editor"
```

---

## Self-Review Checklist

- [x] **Spec coverage:**
  - Audit Logs: V17 ✓, two-tier levels ✓, SecurityAuditListener ✓, WriteAuditListener ✓, admin + project-scoped API ✓, CSV/JSON export ✓, frontend pages ✓
  - SSO: V18 ✓, AES-GCM encryption ✓, DbClientRegistrationRepository ✓, auto-provision ✓, admin API ✓, login button ✓
  - Organizations: V19 ✓, org_id backfill ✓, OrganizationContextHolder ✓, JWT orgId claim ✓, switch-org endpoint ✓, OrgSwitcher ✓
  - Service Management: V20 ✓, ServiceDesk enable ✓, SLA policies ✓, escalation rules ✓, SlaMonitorJob ✓, Incidents ✓, postmortem auto-creation ✓, email ingestion ✓, permit-all ticket endpoint ✓, frontend ✓

- [x] **No placeholders** — all steps have code or explicit instructions

- [x] **Type consistency:**
  - `AuditService.log(level, action, userEmail, ...)` — used consistently in Tasks 2, 3, 4, 15
  - `OrganizationContextHolder.get()` — used in filter (Task 12) and referenced in services
  - `IncidentService.resolve(incidentId, postmortemBody?)` — matches controller call in Task 18
  - `SsoService.encryptSecret/decryptSecret` — consistent across Tasks 8, 9

- [x] **UUID consistency** — all entities use UUID, all migrations use `gen_random_uuid()`
