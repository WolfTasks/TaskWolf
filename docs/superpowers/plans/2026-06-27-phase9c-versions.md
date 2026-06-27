# Phase 9c — Versions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add project-scoped versions (release milestones) assignable to issues as Fix Versions and Affects Versions, with a settings CRUD page and two independent issue-list filter dropdowns.

**Architecture:** Single `issue_versions` join table with a `type` discriminator (`'FIX'`/`'AFFECTS'`). A new `versions` backend module mirrors the `labels` module. An `IssueVersion` JPA entity owns the join table. Frontend mirrors labels: VersionChip, VersionSelector, VersionsPage.

**Tech Stack:** Kotlin/Spring Boot, Spring Data JPA, Flyway, React/TypeScript, React Query, Tailwind CSS.

## Global Constraints

- Flyway migration must be `V24__versions.sql` (V23 is labels)
- `type` column values: exactly `'FIX'` and `'AFFECTS'` (uppercase)
- No color field on versions — minimal design
- No on-the-fly version creation in VersionSelector (managed on VersionsPage only)
- Backend test tool: `cd backend && ./gradlew test --tests "<class>" -i 2>&1 | tail -40`
- Frontend type-check: `cd frontend && npm run build 2>&1 | tail -30`

---

### Task 1: V24 migration + Version entity + IssueVersion entity + Repositories

**Files:**
- Create: `backend/src/main/resources/db/migration/V24__versions.sql`
- Create: `backend/src/main/kotlin/com/taskowolf/versions/domain/Version.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/versions/domain/IssueVersion.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/versions/domain/IssueVersionId.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/versions/infrastructure/VersionRepository.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/versions/infrastructure/IssueVersionRepository.kt`

**Interfaces:**
- Produces: `Version`, `IssueVersion`, `IssueVersionId`, `VersionRepository`, `IssueVersionRepository` — used by Tasks 2 and 3

- [ ] **Step 1: Write the migration**

```sql
-- backend/src/main/resources/db/migration/V24__versions.sql
CREATE TABLE versions (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    project_id UUID        NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name       VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (project_id, name)
);

CREATE TABLE issue_versions (
    issue_id   UUID       NOT NULL REFERENCES issues(id)   ON DELETE CASCADE,
    version_id UUID       NOT NULL REFERENCES versions(id) ON DELETE CASCADE,
    type       VARCHAR(8) NOT NULL CHECK (type IN ('FIX', 'AFFECTS')),
    PRIMARY KEY (issue_id, version_id, type)
);
```

- [ ] **Step 2: Create Version entity**

```kotlin
// backend/src/main/kotlin/com/taskowolf/versions/domain/Version.kt
package com.taskowolf.versions.domain

import com.taskowolf.core.domain.AuditableEntity
import com.taskowolf.projects.domain.Project
import jakarta.persistence.*

@Entity
@Table(
    name = "versions",
    uniqueConstraints = [UniqueConstraint(columnNames = ["project_id", "name"])]
)
class Version(
    @Column(nullable = false, length = 50)
    var name: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    val project: Project
) : AuditableEntity()
```

- [ ] **Step 3: Create IssueVersionId and IssueVersion**

```kotlin
// backend/src/main/kotlin/com/taskowolf/versions/domain/IssueVersionId.kt
package com.taskowolf.versions.domain

import java.io.Serializable
import java.util.UUID

data class IssueVersionId(
    val issueId: UUID = UUID.randomUUID(),
    val versionId: UUID = UUID.randomUUID(),
    val type: String = ""
) : Serializable
```

```kotlin
// backend/src/main/kotlin/com/taskowolf/versions/domain/IssueVersion.kt
package com.taskowolf.versions.domain

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "issue_versions")
@IdClass(IssueVersionId::class)
class IssueVersion(
    @Id
    @Column(name = "issue_id")
    val issueId: UUID,

    @Id
    @Column(name = "version_id")
    val versionId: UUID,

    @Id
    @Column(name = "type", length = 8)
    val type: String
)
```

- [ ] **Step 4: Create VersionRepository**

```kotlin
// backend/src/main/kotlin/com/taskowolf/versions/infrastructure/VersionRepository.kt
package com.taskowolf.versions.infrastructure

import com.taskowolf.versions.domain.Version
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface VersionRepository : JpaRepository<Version, UUID> {
    fun findByProjectId(projectId: UUID): List<Version>
    fun existsByProjectIdAndName(projectId: UUID, name: String): Boolean

    @Query(
        value = "SELECT v.* FROM versions v INNER JOIN issue_versions iv ON v.id = iv.version_id WHERE iv.issue_id = :issueId AND iv.type = :type",
        nativeQuery = true
    )
    fun findByIssueIdAndType(@Param("issueId") issueId: UUID, @Param("type") type: String): List<Version>
}
```

- [ ] **Step 5: Create IssueVersionRepository**

```kotlin
// backend/src/main/kotlin/com/taskowolf/versions/infrastructure/IssueVersionRepository.kt
package com.taskowolf.versions.infrastructure

import com.taskowolf.versions.domain.IssueVersion
import com.taskowolf.versions.domain.IssueVersionId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface IssueVersionRepository : JpaRepository<IssueVersion, IssueVersionId> {
    @Modifying
    @Query("DELETE FROM IssueVersion iv WHERE iv.issueId = :issueId AND iv.type = :type")
    fun deleteByIssueIdAndType(@Param("issueId") issueId: UUID, @Param("type") type: String)
}
```

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/db/migration/V24__versions.sql \
        backend/src/main/kotlin/com/taskowolf/versions/
git commit -m "feat(versions): V24 migration + Version/IssueVersion entities + repositories"
```

---

### Task 2: VersionService + VersionController + DTOs + VersionServiceTest

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/versions/api/dto/VersionRequest.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/versions/api/dto/VersionResponse.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/versions/application/VersionService.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/versions/api/VersionController.kt`
- Create: `backend/src/test/kotlin/com/taskowolf/versions/VersionServiceTest.kt`

**Interfaces:**
- Consumes: `VersionRepository`, `ProjectService`, `Version` from Task 1
- Produces: `VersionService.list/create/update/delete`, `VersionController` at `/api/v1/projects/{key}/versions`, `VersionResponse.from()`

- [ ] **Step 1: Write the failing test**

```kotlin
// backend/src/test/kotlin/com/taskowolf/versions/VersionServiceTest.kt
package com.taskowolf.versions

import com.taskowolf.auth.domain.User
import com.taskowolf.core.infrastructure.ConflictException
import com.taskowolf.core.infrastructure.NotFoundException
import com.taskowolf.projects.application.ProjectService
import com.taskowolf.projects.domain.Project
import com.taskowolf.versions.api.dto.VersionRequest
import com.taskowolf.versions.application.VersionService
import com.taskowolf.versions.domain.Version
import com.taskowolf.versions.infrastructure.VersionRepository
import io.mockk.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Optional
import java.util.UUID

class VersionServiceTest {

    private val versionRepository = mockk<VersionRepository>()
    private val projectService = mockk<ProjectService>()
    private val service = VersionService(versionRepository, projectService)

    private val actor = User(email = "alice@test.com", displayName = "Alice")
    private val project = Project(key = "WOLF", name = "TaskWolf", owner = actor, workflow = null)

    @Test
    fun `list returns versions for project`() {
        val version = Version(name = "v1.0", project = project)
        every { projectService.requireMember("WOLF", actor.id) } returns project
        every { versionRepository.findByProjectId(project.id) } returns listOf(version)

        val result = service.list("WOLF", actor.id)

        assertEquals(1, result.size)
        assertEquals("v1.0", result[0].name)
    }

    @Test
    fun `create saves new version`() {
        every { projectService.requireMember("WOLF", actor.id) } returns project
        every { versionRepository.existsByProjectIdAndName(project.id, "v1.0") } returns false
        every { versionRepository.save(any()) } answers { firstArg() }

        val result = service.create("WOLF", VersionRequest("v1.0"), actor)

        assertEquals("v1.0", result.name)
        verify { versionRepository.save(any()) }
    }

    @Test
    fun `create throws ConflictException when name already exists`() {
        every { projectService.requireMember("WOLF", actor.id) } returns project
        every { versionRepository.existsByProjectIdAndName(project.id, "v1.0") } returns true

        assertThrows<ConflictException> {
            service.create("WOLF", VersionRequest("v1.0"), actor)
        }
    }

    @Test
    fun `update renames version`() {
        val version = Version(name = "v1.0", project = project)
        every { projectService.requireMember("WOLF", actor.id) } returns project
        every { versionRepository.findById(version.id) } returns Optional.of(version)
        every { versionRepository.existsByProjectIdAndName(project.id, "v1.1") } returns false
        every { versionRepository.save(any()) } answers { firstArg() }

        val result = service.update("WOLF", version.id, VersionRequest("v1.1"), actor)

        assertEquals("v1.1", result.name)
    }

    @Test
    fun `update throws ConflictException when new name already exists`() {
        val version = Version(name = "v1.0", project = project)
        every { projectService.requireMember("WOLF", actor.id) } returns project
        every { versionRepository.findById(version.id) } returns Optional.of(version)
        every { versionRepository.existsByProjectIdAndName(project.id, "v1.1") } returns true

        assertThrows<ConflictException> {
            service.update("WOLF", version.id, VersionRequest("v1.1"), actor)
        }
    }

    @Test
    fun `delete removes version`() {
        val version = Version(name = "v1.0", project = project)
        every { projectService.requireMember("WOLF", actor.id) } returns project
        every { versionRepository.findById(version.id) } returns Optional.of(version)
        every { versionRepository.delete(any()) } just Runs

        service.delete("WOLF", version.id, actor)

        verify { versionRepository.delete(version) }
    }

    @Test
    fun `delete throws NotFoundException when version not in project`() {
        val other = Project(key = "OTHER", name = "Other", owner = actor, workflow = null)
        val version = Version(name = "v1.0", project = other)
        every { projectService.requireMember("WOLF", actor.id) } returns project
        every { versionRepository.findById(version.id) } returns Optional.of(version)

        assertThrows<NotFoundException> {
            service.delete("WOLF", version.id, actor)
        }
    }
}
```

- [ ] **Step 2: Run test — expect compilation failure**

```bash
cd backend && ./gradlew test --tests "com.taskowolf.versions.VersionServiceTest" -i 2>&1 | tail -20
```

Expected: compile error — `VersionService`, `VersionRequest` not found.

- [ ] **Step 3: Create DTOs**

```kotlin
// backend/src/main/kotlin/com/taskowolf/versions/api/dto/VersionRequest.kt
package com.taskowolf.versions.api.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class VersionRequest(
    @field:NotBlank @field:Size(max = 50)
    val name: String
)
```

```kotlin
// backend/src/main/kotlin/com/taskowolf/versions/api/dto/VersionResponse.kt
package com.taskowolf.versions.api.dto

import com.taskowolf.versions.domain.Version
import java.util.UUID

data class VersionResponse(
    val id: UUID,
    val name: String
) {
    companion object {
        fun from(v: Version) = VersionResponse(id = v.id, name = v.name)
    }
}
```

- [ ] **Step 4: Create VersionService**

```kotlin
// backend/src/main/kotlin/com/taskowolf/versions/application/VersionService.kt
package com.taskowolf.versions.application

import com.taskowolf.auth.domain.User
import com.taskowolf.core.infrastructure.ConflictException
import com.taskowolf.core.infrastructure.NotFoundException
import com.taskowolf.projects.application.ProjectService
import com.taskowolf.versions.api.dto.VersionRequest
import com.taskowolf.versions.domain.Version
import com.taskowolf.versions.infrastructure.VersionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class VersionService(
    private val versionRepository: VersionRepository,
    private val projectService: ProjectService
) {
    @Transactional(readOnly = true)
    fun list(projectKey: String, userId: UUID): List<Version> {
        val project = projectService.requireMember(projectKey, userId)
        return versionRepository.findByProjectId(project.id)
    }

    @Transactional
    fun create(projectKey: String, request: VersionRequest, actor: User): Version {
        val project = projectService.requireMember(projectKey, actor.id)
        if (versionRepository.existsByProjectIdAndName(project.id, request.name)) {
            throw ConflictException("Version '${request.name}' already exists in this project")
        }
        return versionRepository.save(Version(name = request.name, project = project))
    }

    @Transactional
    fun update(projectKey: String, versionId: UUID, request: VersionRequest, actor: User): Version {
        val project = projectService.requireMember(projectKey, actor.id)
        val version = versionRepository.findById(versionId)
            .filter { it.project.id == project.id }
            .orElseThrow { NotFoundException("Version not found: $versionId") }
        if (version.name != request.name && versionRepository.existsByProjectIdAndName(project.id, request.name)) {
            throw ConflictException("Version '${request.name}' already exists in this project")
        }
        version.name = request.name
        return versionRepository.save(version)
    }

    @Transactional
    fun delete(projectKey: String, versionId: UUID, actor: User) {
        val project = projectService.requireMember(projectKey, actor.id)
        val version = versionRepository.findById(versionId)
            .filter { it.project.id == project.id }
            .orElseThrow { NotFoundException("Version not found: $versionId") }
        versionRepository.delete(version)
    }
}
```

- [ ] **Step 5: Run test — expect pass**

```bash
cd backend && ./gradlew test --tests "com.taskowolf.versions.VersionServiceTest" -i 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL, 7 tests passed.

- [ ] **Step 6: Create VersionController**

```kotlin
// backend/src/main/kotlin/com/taskowolf/versions/api/VersionController.kt
package com.taskowolf.versions.api

import com.taskowolf.auth.domain.User
import com.taskowolf.versions.api.dto.VersionRequest
import com.taskowolf.versions.api.dto.VersionResponse
import com.taskowolf.versions.application.VersionService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/projects/{key}/versions")
class VersionController(private val versionService: VersionService) {

    @GetMapping
    fun list(@PathVariable key: String, @AuthenticationPrincipal user: User) =
        versionService.list(key, user.id).map { VersionResponse.from(it) }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @PathVariable key: String,
        @Valid @RequestBody request: VersionRequest,
        @AuthenticationPrincipal user: User
    ) = VersionResponse.from(versionService.create(key, request, user))

    @PutMapping("/{id}")
    fun update(
        @PathVariable key: String,
        @PathVariable id: UUID,
        @Valid @RequestBody request: VersionRequest,
        @AuthenticationPrincipal user: User
    ) = VersionResponse.from(versionService.update(key, id, request, user))

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable key: String,
        @PathVariable id: UUID,
        @AuthenticationPrincipal user: User
    ) = versionService.delete(key, id, user)
}
```

- [ ] **Step 7: Compile check**

```bash
cd backend && ./gradlew compileKotlin 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/versions/ \
        backend/src/test/kotlin/com/taskowolf/versions/
git commit -m "feat(versions): VersionService, VersionController, DTOs, VersionServiceTest"
```

---

### Task 3: Issue-Versions backend integration

**Files:**
- Modify: `backend/src/main/kotlin/com/taskowolf/issues/api/dto/UpdateIssueRequest.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/issues/api/dto/IssueResponse.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/issues/infrastructure/IssueRepository.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/issues/application/IssueService.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/issues/api/IssueController.kt`

**Interfaces:**
- Consumes: `VersionRepository.findByIssueIdAndType`, `IssueVersionRepository.deleteByIssueIdAndType`, `IssueVersion`, `VersionResponse` from Tasks 1–2
- Produces: `IssueResponse.fixVersions/affectsVersions`, filter params `fixVersionId`/`affectsVersionId` on `GET /issues`

- [ ] **Step 1: Update UpdateIssueRequest** — add two nullable list fields at the end

```kotlin
// full file replacement
package com.taskowolf.issues.api.dto

import com.taskowolf.issues.domain.IssuePriority
import com.taskowolf.issues.domain.IssueType
import java.time.LocalDate
import java.util.UUID

data class UpdateIssueRequest(
    val title: String? = null,
    val description: String? = null,
    val statusId: UUID? = null,
    val assigneeId: UUID? = null,
    val clearAssignee: Boolean = false,
    val priority: IssuePriority? = null,
    val storyPoints: Int? = null,
    val type: IssueType? = null,
    val dueDate: LocalDate? = null,
    val clearDueDate: Boolean = false,
    val sprintId: UUID? = null,
    val clearSprint: Boolean = false,
    val labelIds: List<UUID>? = null,
    val fixVersionIds: List<UUID>? = null,
    val affectsVersionIds: List<UUID>? = null
)
```

- [ ] **Step 2: Update IssueResponse** — add `fixVersions` and `affectsVersions`, update `from()`

```kotlin
// backend/src/main/kotlin/com/taskowolf/issues/api/dto/IssueResponse.kt
package com.taskowolf.issues.api.dto

import com.taskowolf.integrations.api.dto.IssueRefResponse
import com.taskowolf.issues.domain.Issue
import com.taskowolf.issues.domain.IssuePriority
import com.taskowolf.issues.domain.IssueType
import com.taskowolf.labels.api.dto.LabelResponse
import com.taskowolf.versions.api.dto.VersionResponse
import java.time.Instant
import java.time.LocalDate
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
    val assigneeName: String?,
    val reporterId: UUID,
    val reporterName: String,
    val parentId: UUID?,
    val dueDate: LocalDate?,
    val sprintId: UUID?,
    val sprintName: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val refs: List<IssueRefResponse> = emptyList(),
    val labels: List<LabelResponse> = emptyList(),
    val fixVersions: List<VersionResponse> = emptyList(),
    val affectsVersions: List<VersionResponse> = emptyList()
) {
    companion object {
        fun from(
            i: Issue,
            refs: List<IssueRefResponse> = emptyList(),
            labels: List<LabelResponse> = emptyList(),
            fixVersions: List<VersionResponse> = emptyList(),
            affectsVersions: List<VersionResponse> = emptyList()
        ) = IssueResponse(
            id = i.id,
            key = i.key,
            title = i.title,
            description = i.description,
            type = i.type,
            priority = i.priority,
            storyPoints = i.storyPoints,
            statusId = i.status.id,
            statusName = i.status.name,
            statusCategory = i.status.category.name,
            projectId = i.project.id,
            assigneeId = i.assignee?.id,
            assigneeName = i.assignee?.displayName,
            reporterId = i.reporter.id,
            reporterName = i.reporter.displayName,
            parentId = i.parent?.id,
            dueDate = i.dueDate,
            sprintId = i.sprint?.id,
            sprintName = i.sprint?.name,
            createdAt = i.createdAt ?: Instant.now(),
            updatedAt = i.updatedAt,
            refs = refs,
            labels = labels,
            fixVersions = fixVersions,
            affectsVersions = affectsVersions
        )
    }
}
```

- [ ] **Step 3: Add three version filter queries to IssueRepository**

Add these three methods at the end of `IssueRepository`, before the closing `}`:

```kotlin
@Query(
    value = "SELECT i.* FROM issues i INNER JOIN issue_versions iv ON i.id = iv.issue_id WHERE i.project_id = :projectId AND iv.version_id = :versionId AND iv.type = 'FIX'",
    countQuery = "SELECT count(*) FROM issues i INNER JOIN issue_versions iv ON i.id = iv.issue_id WHERE i.project_id = :projectId AND iv.version_id = :versionId AND iv.type = 'FIX'",
    nativeQuery = true
)
fun findAllByProjectIdAndFixVersionId(
    @Param("projectId") projectId: UUID,
    @Param("versionId") versionId: UUID,
    pageable: Pageable
): Page<Issue>

@Query(
    value = "SELECT i.* FROM issues i INNER JOIN issue_versions iv ON i.id = iv.issue_id WHERE i.project_id = :projectId AND iv.version_id = :versionId AND iv.type = 'AFFECTS'",
    countQuery = "SELECT count(*) FROM issues i INNER JOIN issue_versions iv ON i.id = iv.issue_id WHERE i.project_id = :projectId AND iv.version_id = :versionId AND iv.type = 'AFFECTS'",
    nativeQuery = true
)
fun findAllByProjectIdAndAffectsVersionId(
    @Param("projectId") projectId: UUID,
    @Param("versionId") versionId: UUID,
    pageable: Pageable
): Page<Issue>

@Query(
    value = "SELECT i.* FROM issues i INNER JOIN issue_versions iv1 ON i.id = iv1.issue_id AND iv1.type = 'FIX' INNER JOIN issue_versions iv2 ON i.id = iv2.issue_id AND iv2.type = 'AFFECTS' WHERE i.project_id = :projectId AND iv1.version_id = :fixVersionId AND iv2.version_id = :affectsVersionId",
    countQuery = "SELECT count(*) FROM issues i INNER JOIN issue_versions iv1 ON i.id = iv1.issue_id AND iv1.type = 'FIX' INNER JOIN issue_versions iv2 ON i.id = iv2.issue_id AND iv2.type = 'AFFECTS' WHERE i.project_id = :projectId AND iv1.version_id = :fixVersionId AND iv2.version_id = :affectsVersionId",
    nativeQuery = true
)
fun findAllByProjectIdAndBothVersionIds(
    @Param("projectId") projectId: UUID,
    @Param("fixVersionId") fixVersionId: UUID,
    @Param("affectsVersionId") affectsVersionId: UUID,
    pageable: Pageable
): Page<Issue>
```

- [ ] **Step 4: Update IssueService** — inject `VersionRepository` + `IssueVersionRepository`, update `update()` and `findByProject()`

Change the class constructor — add two new dependencies after `labelRepository`:

```kotlin
@Service
class IssueService(
    private val issueRepository: IssueRepository,
    private val projectService: ProjectService,
    private val workflowService: WorkflowService,
    private val userRepository: UserRepository,
    private val eventPublisher: DomainEventPublisher,
    private val sprintRepository: SprintRepository,
    private val labelRepository: LabelRepository,
    private val versionRepository: com.taskowolf.versions.infrastructure.VersionRepository,
    private val issueVersionRepository: com.taskowolf.versions.infrastructure.IssueVersionRepository
)
```

At the top of the file, add imports:
```kotlin
import com.taskowolf.versions.domain.IssueVersion
import com.taskowolf.versions.infrastructure.IssueVersionRepository
import com.taskowolf.versions.infrastructure.VersionRepository
```

Inside `update()`, add these two blocks after the `labelIds` block (before `return issueRepository.save(issue)`):

```kotlin
request.fixVersionIds?.let { ids ->
    val oldVersions = versionRepository.findByIssueIdAndType(issue.id, "FIX")
    val oldNames = oldVersions.map { it.name }.sorted().joinToString(", ")
    val newVersions = if (ids.isEmpty()) emptyList()
                      else versionRepository.findAllById(ids).filter { it.project.id == project.id }
    val newNames = newVersions.map { it.name }.sorted().joinToString(", ")
    if (oldNames != newNames) {
        issueVersionRepository.deleteByIssueIdAndType(issue.id, "FIX")
        issueVersionRepository.saveAll(newVersions.map { IssueVersion(issue.id, it.id, "FIX") })
        eventPublisher.publish(
            IssueFieldChangedEvent(issue, currentUser, "fixVersions",
                oldNames.ifEmpty { null }, newNames.ifEmpty { null })
        )
    }
}

request.affectsVersionIds?.let { ids ->
    val oldVersions = versionRepository.findByIssueIdAndType(issue.id, "AFFECTS")
    val oldNames = oldVersions.map { it.name }.sorted().joinToString(", ")
    val newVersions = if (ids.isEmpty()) emptyList()
                      else versionRepository.findAllById(ids).filter { it.project.id == project.id }
    val newNames = newVersions.map { it.name }.sorted().joinToString(", ")
    if (oldNames != newNames) {
        issueVersionRepository.deleteByIssueIdAndType(issue.id, "AFFECTS")
        issueVersionRepository.saveAll(newVersions.map { IssueVersion(issue.id, it.id, "AFFECTS") })
        eventPublisher.publish(
            IssueFieldChangedEvent(issue, currentUser, "affectsVersions",
                oldNames.ifEmpty { null }, newNames.ifEmpty { null })
        )
    }
}
```

Update `findByProject()` signature — add two optional params after `labelId`:

```kotlin
fun findByProject(
    projectKey: String,
    userId: UUID,
    page: Int,
    size: Int,
    assigneeMe: Boolean = false,
    sort: String? = null,
    overdue: Boolean = false,
    labelId: UUID? = null,
    fixVersionId: UUID? = null,
    affectsVersionId: UUID? = null
): org.springframework.data.domain.Page<Issue>
```

Replace the `if (labelId != null) return ...` block with:

```kotlin
if (fixVersionId != null && affectsVersionId != null)
    return issueRepository.findAllByProjectIdAndBothVersionIds(project.id, fixVersionId, affectsVersionId, pageable)
if (fixVersionId != null)
    return issueRepository.findAllByProjectIdAndFixVersionId(project.id, fixVersionId, pageable)
if (affectsVersionId != null)
    return issueRepository.findAllByProjectIdAndAffectsVersionId(project.id, affectsVersionId, pageable)
if (labelId != null)
    return issueRepository.findAllByProjectIdAndLabelId(project.id, labelId, pageable)
```

- [ ] **Step 5: Update IssueController** — inject `VersionRepository`, update `get()` and `list()`

Add `VersionRepository` to the constructor:

```kotlin
@RestController
@RequestMapping("/api/v1/projects/{key}/issues")
class IssueController(
    private val issueService: IssueService,
    private val issueRefRepository: IssueRefRepository,
    private val labelRepository: LabelRepository,
    private val versionRepository: com.taskowolf.versions.infrastructure.VersionRepository
)
```

Add import at top:
```kotlin
import com.taskowolf.versions.api.dto.VersionResponse
import com.taskowolf.versions.infrastructure.VersionRepository
```

Update `get()` to populate versions:

```kotlin
@GetMapping("/{issueKey}")
fun get(
    @PathVariable key: String,
    @PathVariable issueKey: String,
    @AuthenticationPrincipal user: User
): IssueResponse {
    val issue = issueService.findByKey(key, issueKey, user.id)
    val refs = issueRefRepository.findByIssueIdOrderByCreatedAtAsc(issue.id).map { IssueRefResponse.from(it) }
    val labels = labelRepository.findByIssueId(issue.id).map { LabelResponse.from(it) }
    val fixVersions = versionRepository.findByIssueIdAndType(issue.id, "FIX").map { VersionResponse.from(it) }
    val affectsVersions = versionRepository.findByIssueIdAndType(issue.id, "AFFECTS").map { VersionResponse.from(it) }
    return IssueResponse.from(issue, refs, labels, fixVersions, affectsVersions)
}
```

Update `list()` — add two new `@RequestParam` and pass to `findByProject`:

```kotlin
@GetMapping
fun list(
    @PathVariable key: String,
    @AuthenticationPrincipal user: User,
    @RequestParam(defaultValue = "0") page: Int,
    @RequestParam(defaultValue = "50") size: Int,
    @RequestParam(defaultValue = "false") assigneeMe: Boolean,
    @RequestParam(required = false) sort: String?,
    @RequestParam(defaultValue = "false") overdue: Boolean,
    @RequestParam(required = false) labelId: UUID?,
    @RequestParam(required = false) fixVersionId: UUID?,
    @RequestParam(required = false) affectsVersionId: UUID?
) = issueService.findByProject(key, user.id, page, size, assigneeMe, sort, overdue, labelId, fixVersionId, affectsVersionId)
        .map { IssueResponse.from(it) }
```

- [ ] **Step 6: Compile check**

```bash
cd backend && ./gradlew compileKotlin compileTestKotlin 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/issues/
git commit -m "feat(versions): issue-versions backend integration — UpdateIssueRequest, IssueResponse, IssueService, IssueRepository, IssueController"
```

---

### Task 4: Frontend types + versionsApi + useVersions + update issuesApi/useIssues

**Files:**
- Modify: `frontend/src/types/index.ts`
- Create: `frontend/src/api/versions.ts`
- Create: `frontend/src/hooks/useVersions.ts`
- Modify: `frontend/src/api/issues.ts`
- Modify: `frontend/src/hooks/useIssues.ts`

**Interfaces:**
- Produces: `Version` type, `Issue.fixVersions`, `Issue.affectsVersions`, `versionsApi`, `useVersions/useCreateVersion/useUpdateVersion/useDeleteVersion`, `useIssues` with version filter opts

- [ ] **Step 1: Update types/index.ts** — add `Version` interface and update `Issue`

Add after the `Label` interface:

```typescript
export interface Version {
  id: string
  name: string
}
```

Add to the `Issue` interface (after `labels?: Label[]`):

```typescript
  fixVersions?: Version[]
  affectsVersions?: Version[]
```

- [ ] **Step 2: Create api/versions.ts**

```typescript
// frontend/src/api/versions.ts
import { apiClient } from './client'
import type { Version } from '@/types'

export const versionsApi = {
  list: (projectKey: string) =>
    apiClient.get<Version[]>(`/projects/${projectKey}/versions`),
  create: (projectKey: string, data: { name: string }) =>
    apiClient.post<Version>(`/projects/${projectKey}/versions`, data),
  update: (projectKey: string, id: string, data: { name: string }) =>
    apiClient.put<Version>(`/projects/${projectKey}/versions/${id}`, data),
  delete: (projectKey: string, id: string) =>
    apiClient.delete(`/projects/${projectKey}/versions/${id}`),
}
```

- [ ] **Step 3: Create hooks/useVersions.ts**

```typescript
// frontend/src/hooks/useVersions.ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { versionsApi } from '@/api/versions'

export function useVersions(projectKey: string) {
  return useQuery({
    queryKey: ['versions', projectKey],
    queryFn: () => versionsApi.list(projectKey).then(r => r.data),
  })
}

export function useCreateVersion(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: { name: string }) =>
      versionsApi.create(projectKey, data).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['versions', projectKey] }),
  })
}

export function useUpdateVersion(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, ...data }: { id: string; name: string }) =>
      versionsApi.update(projectKey, id, data).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['versions', projectKey] }),
  })
}

export function useDeleteVersion(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => versionsApi.delete(projectKey, id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['versions', projectKey] }),
  })
}
```

- [ ] **Step 4: Update api/issues.ts** — add `fixVersionId` and `affectsVersionId` to `list`

```typescript
// frontend/src/api/issues.ts
import { apiClient } from './client'
import type { Issue, Page } from '@/types'

export const issuesApi = {
  list: (projectKey: string, page = 0, size = 50, labelId?: string, fixVersionId?: string, affectsVersionId?: string) =>
    apiClient.get<Page<Issue>>(`/projects/${projectKey}/issues`, {
      params: {
        page, size,
        ...(labelId ? { labelId } : {}),
        ...(fixVersionId ? { fixVersionId } : {}),
        ...(affectsVersionId ? { affectsVersionId } : {}),
      }
    }),
  get: (projectKey: string, issueKey: string) =>
    apiClient.get<Issue>(`/projects/${projectKey}/issues/${issueKey}`),
  create: (projectKey: string, data: { title: string; type?: string; priority?: string; description?: string }) =>
    apiClient.post<Issue>(`/projects/${projectKey}/issues`, data),
  update: (projectKey: string, issueId: string, data: Partial<Issue & { statusId: string }>) =>
    apiClient.patch<Issue>(`/projects/${projectKey}/issues/${issueId}`, data),
}
```

- [ ] **Step 5: Update hooks/useIssues.ts** — accept opts object

```typescript
// frontend/src/hooks/useIssues.ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { issuesApi } from '@/api/issues'

interface IssueListOpts {
  labelId?: string
  fixVersionId?: string
  affectsVersionId?: string
}

export function useIssues(projectKey: string, opts: IssueListOpts = {}) {
  const { labelId, fixVersionId, affectsVersionId } = opts
  return useQuery({
    queryKey: ['issues', projectKey, { labelId, fixVersionId, affectsVersionId }],
    queryFn: () => issuesApi.list(projectKey, 0, 50, labelId, fixVersionId, affectsVersionId).then(r => r.data)
  })
}

export function useIssue(projectKey: string, issueKey: string) {
  return useQuery({
    queryKey: ['issues', projectKey, issueKey],
    queryFn: () => issuesApi.get(projectKey, issueKey).then(r => r.data)
  })
}

export function useCreateIssue(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: { title: string; type?: string; priority?: string; description?: string }) =>
      issuesApi.create(projectKey, data).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['issues', projectKey] })
  })
}

export function useUpdateIssue(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: Record<string, unknown> }) =>
      issuesApi.update(projectKey, id, data).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['issues', projectKey] })
  })
}
```

Note: `IssueListPage` currently calls `useIssues(key!, labelId)` — update that call site in Task 7.

- [ ] **Step 6: Type-check**

```bash
cd frontend && npm run build 2>&1 | tail -30
```

Expected: compiled with no TypeScript errors (or only pre-existing warnings).

- [ ] **Step 7: Commit**

```bash
git add frontend/src/types/index.ts \
        frontend/src/api/versions.ts \
        frontend/src/hooks/useVersions.ts \
        frontend/src/api/issues.ts \
        frontend/src/hooks/useIssues.ts
git commit -m "feat(versions): frontend types, versionsApi, useVersions, update issuesApi/useIssues"
```

---

### Task 5: VersionChip + VersionSelector + IssueDetailPage sidebar

**Files:**
- Create: `frontend/src/components/issue/VersionChip.tsx`
- Create: `frontend/src/components/issue/VersionSelector.tsx`
- Modify: `frontend/src/pages/issues/IssueDetailPage.tsx`

**Interfaces:**
- Consumes: `Version`, `useVersions` from Task 4
- Produces: `VersionChip`, `VersionSelector` used by IssueDetailPage and IssueListPage

- [ ] **Step 1: Create VersionChip**

```tsx
// frontend/src/components/issue/VersionChip.tsx
import type { Version } from '@/types'

interface Props {
  version: Version
  onClick?: () => void
}

export function VersionChip({ version, onClick }: Props) {
  return (
    <span
      className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-indigo-900 text-indigo-200 border border-indigo-700 ${onClick ? 'cursor-pointer hover:opacity-80' : ''}`}
      onClick={onClick ? (e: React.MouseEvent) => { e.stopPropagation(); onClick() } : undefined}
    >
      {version.name}
    </span>
  )
}
```

- [ ] **Step 2: Create VersionSelector**

VersionSelector is a multi-select dropdown without inline create (versions are managed on VersionsPage).

```tsx
// frontend/src/components/issue/VersionSelector.tsx
import { useState, useRef, useEffect } from 'react'
import type { Version } from '@/types'
import { VersionChip } from './VersionChip'

interface Props {
  value: Version[]
  allVersions: Version[]
  onSave: (versionIds: string[]) => void
  onChipClick?: (version: Version) => void
}

export function VersionSelector({ value, allVersions, onSave, onChipClick }: Props) {
  const [open, setOpen] = useState(false)
  const [selected, setSelected] = useState<Version[]>(value)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => { setSelected(value) }, [value])

  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        if (open) {
          onSave(selected.map(v => v.id))
          setOpen(false)
        }
      }
    }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [open, selected, onSave])

  function toggle(version: Version) {
    setSelected(prev =>
      prev.some(v => v.id === version.id)
        ? prev.filter(v => v.id !== version.id)
        : [...prev, version]
    )
  }

  return (
    <div ref={ref} className="relative">
      <div
        className="flex flex-wrap gap-1 cursor-pointer min-h-[24px]"
        onClick={() => setOpen(o => !o)}
      >
        {selected.length === 0
          ? <span className="text-sm text-gray-500 hover:text-gray-300">None</span>
          : selected.map(v => (
            <VersionChip
              key={v.id}
              version={v}
              onClick={onChipClick ? () => onChipClick(v) : undefined}
            />
          ))
        }
      </div>
      {open && (
        <div className="absolute z-50 top-7 left-0 bg-gray-800 border border-gray-700 rounded shadow-lg py-1 min-w-52 max-h-64 overflow-y-auto">
          {allVersions.length === 0 && (
            <p className="px-3 py-2 text-sm text-gray-500">No versions. Create them in project settings.</p>
          )}
          {allVersions.map(v => (
            <button
              key={v.id}
              onClick={() => toggle(v)}
              className="w-full text-left px-3 py-1.5 text-sm text-gray-300 hover:bg-gray-700 flex items-center gap-2"
            >
              <span className={`w-3 h-3 rounded-full flex-shrink-0 border ${selected.some(s => s.id === v.id) ? 'border-white bg-indigo-500' : 'border-gray-500 bg-transparent'}`} />
              {v.name}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
```

- [ ] **Step 3: Update IssueDetailPage** — add `useVersions`, import `VersionSelector`, add two sidebar fields

Add import at top of `IssueDetailPage.tsx`:
```typescript
import { useVersions } from '@/hooks/useVersions'
import { VersionSelector } from '@/components/issue/VersionSelector'
```

Add hook call after `const { data: allLabels = [] } = useLabels(key!)`:
```typescript
const { data: allVersions = [] } = useVersions(key!)
```

Replace the Labels `SidebarField` block with (keep the existing labels block, add versions after it):
```tsx
<SidebarField label="Labels">
  <LabelSelector
    projectKey={key!}
    value={issue.labels ?? []}
    allLabels={allLabels}
    onSave={labelIds => patch({ labelIds })}
    onChipClick={l => navigate(`/p/${key}/issues?labelId=${l.id}`)}
  />
</SidebarField>

<SidebarField label="Fix Versions">
  <VersionSelector
    value={issue.fixVersions ?? []}
    allVersions={allVersions}
    onSave={fixVersionIds => patch({ fixVersionIds })}
    onChipClick={v => navigate(`/p/${key}/issues?fixVersionId=${v.id}`)}
  />
</SidebarField>

<SidebarField label="Affects Versions">
  <VersionSelector
    value={issue.affectsVersions ?? []}
    allVersions={allVersions}
    onSave={affectsVersionIds => patch({ affectsVersionIds })}
    onChipClick={v => navigate(`/p/${key}/issues?affectsVersionId=${v.id}`)}
  />
</SidebarField>
```

- [ ] **Step 4: Type-check**

```bash
cd frontend && npm run build 2>&1 | tail -30
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/issue/VersionChip.tsx \
        frontend/src/components/issue/VersionSelector.tsx \
        frontend/src/pages/issues/IssueDetailPage.tsx
git commit -m "feat(versions): VersionChip, VersionSelector, IssueDetailPage sidebar integration"
```

---

### Task 6: VersionsPage settings + router + AppLayout nav link

**Files:**
- Create: `frontend/src/pages/projects/settings/VersionsPage.tsx`
- Modify: `frontend/src/app/router.tsx`
- Modify: `frontend/src/layouts/AppLayout.tsx`

**Interfaces:**
- Consumes: `useVersions`, `useCreateVersion`, `useUpdateVersion`, `useDeleteVersion` from Task 4

- [ ] **Step 1: Create VersionsPage**

```tsx
// frontend/src/pages/projects/settings/VersionsPage.tsx
import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { useVersions, useCreateVersion, useUpdateVersion, useDeleteVersion } from '@/hooks/useVersions'
import type { Version } from '@/types'

function VersionForm({
  initial,
  onSubmit,
  onCancel,
}: {
  initial?: { name: string }
  onSubmit: (name: string) => void
  onCancel: () => void
}) {
  const [name, setName] = useState(initial?.name ?? '')
  const [error, setError] = useState('')

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!name.trim()) { setError('Name is required'); return }
    if (name.trim().length > 50) { setError('Max 50 characters'); return }
    onSubmit(name.trim())
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-3 p-4 bg-gray-800 rounded-lg border border-gray-700">
      <div>
        <label className="block text-xs text-gray-400 mb-1">Name</label>
        <input
          value={name}
          onChange={e => { setName(e.target.value); setError('') }}
          maxLength={50}
          placeholder="e.g. v1.0.0"
          autoFocus
          className="w-full bg-gray-700 border border-gray-600 rounded px-3 py-1.5 text-sm text-white outline-none focus:border-blue-500"
        />
        {error && <p className="text-xs text-red-400 mt-1">{error}</p>}
      </div>
      <div className="flex gap-2">
        <button type="submit" className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-1.5 rounded text-sm font-medium">
          {initial ? 'Save' : 'Create'}
        </button>
        <button type="button" onClick={onCancel} className="text-gray-400 hover:text-white px-3 py-1.5 text-sm">
          Cancel
        </button>
      </div>
    </form>
  )
}

export function VersionsPage() {
  const { key } = useParams<{ key: string }>()
  const { data: versions = [], isLoading } = useVersions(key!)
  const createVersion = useCreateVersion(key!)
  const updateVersion = useUpdateVersion(key!)
  const deleteVersion = useDeleteVersion(key!)

  const [showCreate, setShowCreate] = useState(false)
  const [editing, setEditing] = useState<Version | null>(null)
  const [apiError, setApiError] = useState('')

  async function handleCreate(name: string) {
    try {
      await createVersion.mutateAsync({ name })
      setShowCreate(false)
      setApiError('')
    } catch {
      setApiError('A version with that name already exists.')
    }
  }

  async function handleUpdate(name: string) {
    if (!editing) return
    try {
      await updateVersion.mutateAsync({ id: editing.id, name })
      setEditing(null)
      setApiError('')
    } catch {
      setApiError('A version with that name already exists.')
    }
  }

  async function handleDelete(id: string) {
    if (!confirm('Delete this version? It will be removed from all issues.')) return
    await deleteVersion.mutateAsync(id)
  }

  if (isLoading) return <div className="text-gray-400 p-6">Loading…</div>

  return (
    <div className="p-6 space-y-6 max-w-2xl">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Versions</h1>
        {!showCreate && (
          <button
            onClick={() => setShowCreate(true)}
            className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded text-sm font-medium"
          >
            + New Version
          </button>
        )}
      </div>

      {apiError && <p className="text-sm text-red-400">{apiError}</p>}

      {showCreate && (
        <VersionForm onSubmit={handleCreate} onCancel={() => { setShowCreate(false); setApiError('') }} />
      )}

      <div className="flex flex-col gap-2">
        {versions.map(version => (
          <div key={version.id}>
            {editing?.id === version.id ? (
              <VersionForm
                initial={{ name: version.name }}
                onSubmit={handleUpdate}
                onCancel={() => { setEditing(null); setApiError('') }}
              />
            ) : (
              <div className="flex items-center gap-3 px-4 py-3 bg-gray-900 border border-gray-800 rounded-lg">
                <span className="text-sm text-white font-mono">{version.name}</span>
                <div className="ml-auto flex gap-2">
                  <button
                    onClick={() => setEditing(version)}
                    className="text-xs text-gray-400 hover:text-white px-2 py-1 rounded hover:bg-gray-700"
                  >
                    Edit
                  </button>
                  <button
                    onClick={() => handleDelete(version.id)}
                    className="text-xs text-red-400 hover:text-red-300 px-2 py-1 rounded hover:bg-gray-700"
                  >
                    Delete
                  </button>
                </div>
              </div>
            )}
          </div>
        ))}
        {versions.length === 0 && !showCreate && (
          <p className="text-sm text-gray-500 py-8 text-center">No versions yet. Create your first one!</p>
        )}
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Add route to router.tsx**

Add import after the `LabelsPage` import:
```typescript
import { VersionsPage } from '@/pages/projects/settings/VersionsPage'
```

Add route after the labels route:
```typescript
{ path: '/p/:key/settings/versions', element: <VersionsPage /> },
```

- [ ] **Step 3: Add nav link to AppLayout.tsx**

Add after the Labels `NavLink` inside the Settings section:
```tsx
<NavLink to={`/p/${projectKey}/settings/versions`} className={subNavLinkClass}>
  Versions
</NavLink>
```

- [ ] **Step 4: Type-check**

```bash
cd frontend && npm run build 2>&1 | tail -30
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/projects/settings/VersionsPage.tsx \
        frontend/src/app/router.tsx \
        frontend/src/layouts/AppLayout.tsx
git commit -m "feat(versions): VersionsPage settings, route, AppLayout nav link"
```

---

### Task 7: Issue list version filters

**Files:**
- Modify: `frontend/src/pages/issues/IssueListPage.tsx`

**Interfaces:**
- Consumes: `useVersions` from Task 4, updated `useIssues` opts from Task 4

- [ ] **Step 1: Rewrite IssueListPage with version filters**

```tsx
// frontend/src/pages/issues/IssueListPage.tsx
import { useState, useEffect } from 'react'
import { useParams, Link, useSearchParams } from 'react-router-dom'
import { useIssues, useCreateIssue } from '@/hooks/useIssues'
import { useLabels } from '@/hooks/useLabels'
import { useVersions } from '@/hooks/useVersions'
import { StatusBadge } from '@/components/issue/StatusBadge'

export function IssueListPage() {
  const { key } = useParams<{ key: string }>()
  const [searchParams, setSearchParams] = useSearchParams()
  const labelId = searchParams.get('labelId') ?? undefined
  const fixVersionId = searchParams.get('fixVersionId') ?? undefined
  const affectsVersionId = searchParams.get('affectsVersionId') ?? undefined

  const { data: page, isLoading } = useIssues(key!, { labelId, fixVersionId, affectsVersionId })
  const { data: labels = [] } = useLabels(key!)
  const { data: versions = [] } = useVersions(key!)
  const createIssue = useCreateIssue(key!)
  const [title, setTitle] = useState('')
  const [showForm, setShowForm] = useState(false)

  useEffect(() => {
    if (labelId && labels.length > 0 && !labels.some(l => l.id === labelId)) {
      setSearchParams(prev => { const n = new URLSearchParams(prev); n.delete('labelId'); return n })
    }
  }, [labelId, labels, setSearchParams])

  useEffect(() => {
    if (fixVersionId && versions.length > 0 && !versions.some(v => v.id === fixVersionId)) {
      setSearchParams(prev => { const n = new URLSearchParams(prev); n.delete('fixVersionId'); return n })
    }
  }, [fixVersionId, versions, setSearchParams])

  useEffect(() => {
    if (affectsVersionId && versions.length > 0 && !versions.some(v => v.id === affectsVersionId)) {
      setSearchParams(prev => { const n = new URLSearchParams(prev); n.delete('affectsVersionId'); return n })
    }
  }, [affectsVersionId, versions, setSearchParams])

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!title.trim()) return
    await createIssue.mutateAsync({ title })
    setTitle('')
    setShowForm(false)
  }

  function setParam(key: string, value: string | undefined) {
    setSearchParams(prev => {
      const n = new URLSearchParams(prev)
      if (value) n.set(key, value)
      else n.delete(key)
      return n
    })
  }

  if (isLoading) return <div className="text-gray-400">Loading...</div>

  return (
    <div>
      <div className="flex items-center justify-between mb-4">
        <h1 className="text-2xl font-bold">{key} — Issues</h1>
        <button onClick={() => setShowForm(true)}
          className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded text-sm font-medium">
          + Create Issue
        </button>
      </div>

      {/* Toolbar filters */}
      <div className="flex items-center gap-3 mb-4 flex-wrap">
        <select
          value={labelId ?? ''}
          onChange={e => setParam('labelId', e.target.value || undefined)}
          className="bg-gray-800 border border-gray-700 text-sm text-white rounded px-3 py-1.5 outline-none"
        >
          <option value="">All Labels</option>
          {labels.map(l => (
            <option key={l.id} value={l.id}>{l.name}</option>
          ))}
        </select>

        <select
          value={fixVersionId ?? ''}
          onChange={e => setParam('fixVersionId', e.target.value || undefined)}
          className="bg-gray-800 border border-gray-700 text-sm text-white rounded px-3 py-1.5 outline-none"
        >
          <option value="">All Fix Versions</option>
          {versions.map(v => (
            <option key={v.id} value={v.id}>{v.name}</option>
          ))}
        </select>

        <select
          value={affectsVersionId ?? ''}
          onChange={e => setParam('affectsVersionId', e.target.value || undefined)}
          className="bg-gray-800 border border-gray-700 text-sm text-white rounded px-3 py-1.5 outline-none"
        >
          <option value="">All Affects Versions</option>
          {versions.map(v => (
            <option key={v.id} value={v.id}>{v.name}</option>
          ))}
        </select>

        {(labelId || fixVersionId || affectsVersionId) && (
          <button
            onClick={() => setSearchParams({})}
            className="text-xs text-gray-400 hover:text-white"
          >
            ✕ Clear filters
          </button>
        )}
      </div>

      {showForm && (
        <form onSubmit={handleCreate} className="mb-4 flex gap-2">
          <input value={title} onChange={e => setTitle(e.target.value)} placeholder="Issue title" autoFocus required
            className="flex-1 bg-gray-800 border border-gray-700 rounded px-3 py-2 text-white text-sm" />
          <button type="submit" className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded text-sm">Save</button>
          <button type="button" onClick={() => setShowForm(false)} className="text-gray-400 hover:text-white px-3 py-2 text-sm">Cancel</button>
        </form>
      )}

      <div className="flex flex-col gap-2">
        {page?.content.map(issue => (
          <Link key={issue.id} to={`/p/${key}/issues/${issue.key}`}
            className="bg-gray-900 border border-gray-800 hover:border-gray-600 rounded-lg px-4 py-3 flex items-center gap-4">
            <span className="text-xs text-gray-500 font-mono w-20">{issue.key}</span>
            <span className="flex-1 text-sm text-white">{issue.title}</span>
            <StatusBadge name={issue.statusName} category={issue.statusCategory} />
            <span className={`text-xs px-2 py-0.5 rounded font-medium ${
              issue.priority === 'CRITICAL' ? 'text-red-400' :
              issue.priority === 'HIGH' ? 'text-orange-400' :
              issue.priority === 'MEDIUM' ? 'text-yellow-400' : 'text-green-400'
            }`}>{issue.priority}</span>
          </Link>
        ))}
        {page?.content.length === 0 && (
          <p className="text-gray-500 text-sm py-8 text-center">No issues yet. Create your first one!</p>
        )}
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Type-check**

```bash
cd frontend && npm run build 2>&1 | tail -30
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/pages/issues/IssueListPage.tsx
git commit -m "feat(versions): issue list Fix Version and Affects Version filter dropdowns"
```

---

### Task 8: Wiki documentation

**Files:**
- Create: `mkdocs/developer-guide/backend/versions.md`
- Check: `mkdocs/mkdocs.yml` or `mkdocs/nav` config — add `versions.md` entry alongside `labels.md`

- [ ] **Step 1: Find where labels.md is listed in mkdocs nav config**

```bash
grep -r "labels" mkdocs/ --include="*.yml" -l
```

Open the found config file and note the exact nav entry for `labels.md` to replicate the pattern for `versions.md`.

- [ ] **Step 2: Write versions.md** — mirror the structure of `mkdocs/developer-guide/backend/labels.md` but for versions. Cover:
  - Overview (what versions are, fix vs affects)
  - DB schema (`versions`, `issue_versions` with type discriminator)
  - REST endpoints (`GET/POST/PUT/DELETE /api/v1/projects/{key}/versions`)
  - Issue integration (how `fixVersionIds`/`affectsVersionIds` work in `PATCH /issues/{id}`, how `GET /issues/{key}` returns `fixVersions`/`affectsVersions`)
  - Issue list filter (`fixVersionId` and `affectsVersionId` query params)
  - Backend module layout (`versions/domain`, `versions/infrastructure`, `versions/application`, `versions/api`)

- [ ] **Step 3: Add nav entry** — add `versions.md` in `mkdocs.yml` nav in the same section as `labels.md`

- [ ] **Step 4: Commit**

```bash
git add mkdocs/
git commit -m "docs(wiki): add versions module page (Phase 9c)"
```
