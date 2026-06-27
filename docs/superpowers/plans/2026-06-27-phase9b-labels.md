# Phase 9b — Labels Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add project-scoped colored labels to TaskWolf: CRUD management in settings, multi-label assignment on issues (with on-the-fly creation), single-label filter in the issue list.

**Architecture:** New `labels` module (domain/service/controller) following the existing Kotlin domain-driven structure. Labels are linked to issues via a `@ManyToMany` JPA relationship; the join table is owned by `Issue.labels`. Issue list filtering adds a single `labelId` query parameter; label assignment is an extension of the existing `UpdateIssueRequest` PATCH pattern.

**Tech Stack:** Kotlin / Spring Boot / Spring Data JPA / PostgreSQL (Flyway V23), React + TypeScript + TanStack Query + Tailwind CSS, Vite (no test runner in frontend — use `tsc --noEmit` for type checks).

## Global Constraints

- All IDs are `UUID` (not `Long`) — never use `Long` for entity IDs.
- Backend packages live under `com.taskowolf.<module>.*`.
- Backend test framework: JUnit 5 + MockK (`io.mockk:mockk`). Run tests with `cd backend && ./gradlew test --tests "com.taskowolf.<test-class>"`.
- Frontend type-check: `cd frontend && npx tsc --noEmit`.
- Next available Flyway migration version: **V23**.
- Color palette constant (12 hex values):
  `["#e11d48","#f97316","#eab308","#22c55e","#14b8a6","#3b82f6","#8b5cf6","#ec4899","#64748b","#0ea5e9","#84cc16","#f43f5e"]`
- Spec: `docs/superpowers/specs/2026-06-27-phase9b-labels-design.md`

---

## File Map

### Backend — New
| File | Purpose |
|------|---------|
| `backend/src/main/resources/db/migration/V23__labels.sql` | Creates `labels` and `issue_labels` tables |
| `backend/src/main/kotlin/com/taskowolf/labels/domain/Label.kt` | JPA entity |
| `backend/src/main/kotlin/com/taskowolf/labels/infrastructure/LabelRepository.kt` | Spring Data JPA repository |
| `backend/src/main/kotlin/com/taskowolf/labels/api/dto/LabelRequest.kt` | `{name, color}` for POST/PUT |
| `backend/src/main/kotlin/com/taskowolf/labels/api/dto/LabelResponse.kt` | `{id, name, color}` response DTO |
| `backend/src/main/kotlin/com/taskowolf/labels/application/LabelService.kt` | CRUD logic |
| `backend/src/main/kotlin/com/taskowolf/labels/api/LabelController.kt` | REST controller |
| `backend/src/test/kotlin/com/taskowolf/labels/LabelServiceTest.kt` | Unit tests |

### Backend — Modified
| File | Change |
|------|--------|
| `backend/src/main/kotlin/com/taskowolf/issues/domain/Issue.kt` | Add `@ManyToMany var labels: MutableSet<Label>` |
| `backend/src/main/kotlin/com/taskowolf/issues/api/dto/IssueResponse.kt` | Add `labels: List<LabelResponse>` param |
| `backend/src/main/kotlin/com/taskowolf/issues/api/dto/UpdateIssueRequest.kt` | Add `labelIds: List<UUID>?` |
| `backend/src/main/kotlin/com/taskowolf/issues/application/IssueService.kt` | Inject `LabelRepository`, handle `labelIds` in `update()`, add `labelId` filter to `findByProject()` |
| `backend/src/main/kotlin/com/taskowolf/issues/infrastructure/IssueRepository.kt` | Add `findAllByProjectIdAndLabelId()` |
| `backend/src/main/kotlin/com/taskowolf/issues/api/IssueController.kt` | Inject `LabelRepository`, pass labels in `get()`, add `labelId` param to `list()` |
| `backend/src/test/kotlin/com/taskowolf/issues/IssueServiceTest.kt` | Add label assignment and filter tests |

### Frontend — New
| File | Purpose |
|------|---------|
| `frontend/src/api/labels.ts` | `labelsApi` (getLabels, createLabel, updateLabel, deleteLabel) |
| `frontend/src/hooks/useLabels.ts` | `useLabels`, `useCreateLabel`, `useUpdateLabel`, `useDeleteLabel` |
| `frontend/src/components/issue/LabelChip.tsx` | Colored pill component |
| `frontend/src/components/issue/LabelSelector.tsx` | Multi-select popover with on-the-fly creation |
| `frontend/src/pages/projects/settings/LabelsPage.tsx` | Settings page: list + create/edit/delete |

### Frontend — Modified
| File | Change |
|------|--------|
| `frontend/src/types/index.ts` | Add `Label` interface; add `labels: Label[]` to `Issue` |
| `frontend/src/api/issues.ts` | Accept optional `labelId` param in `list()` |
| `frontend/src/hooks/useIssues.ts` | Accept optional `labelId` param in `useIssues()` |
| `frontend/src/pages/issues/IssueDetailPage.tsx` | Add `LabelSelector` to sidebar; clicking a chip navigates to filtered list |
| `frontend/src/pages/issues/IssueListPage.tsx` | Add label filter dropdown in toolbar |
| `frontend/src/app/router.tsx` | Add `/p/:key/settings/labels` route |
| `frontend/src/layouts/AppLayout.tsx` | Add "Labels" link in project Settings section |

---

## Task 1: DB Migration + Label Domain

**Files:**
- Create: `backend/src/main/resources/db/migration/V23__labels.sql`
- Create: `backend/src/main/kotlin/com/taskowolf/labels/domain/Label.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/labels/infrastructure/LabelRepository.kt`

**Interfaces:**
- Produces: `Label` entity with fields `name`, `color`, `project`; `LabelRepository` with `findByProjectId`, `existsByProjectIdAndName`, `findByIssueId`

---

- [ ] **Step 1: Write the migration**

Create `backend/src/main/resources/db/migration/V23__labels.sql`:

```sql
CREATE TABLE labels (
    id         UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    project_id UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name       VARCHAR(50)  NOT NULL,
    color      VARCHAR(7)   NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (project_id, name)
);

CREATE TABLE issue_labels (
    issue_id  UUID NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    label_id  UUID NOT NULL REFERENCES labels(id) ON DELETE CASCADE,
    PRIMARY KEY (issue_id, label_id)
);
```

- [ ] **Step 2: Create Label.kt entity**

Create `backend/src/main/kotlin/com/taskowolf/labels/domain/Label.kt`:

```kotlin
package com.taskowolf.labels.domain

import com.taskowolf.core.domain.AuditableEntity
import com.taskowolf.projects.domain.Project
import jakarta.persistence.*

@Entity
@Table(
    name = "labels",
    uniqueConstraints = [UniqueConstraint(columnNames = ["project_id", "name"])]
)
class Label(
    @Column(nullable = false, length = 50)
    var name: String,

    @Column(nullable = false, length = 7)
    var color: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    val project: Project
) : AuditableEntity()
```

- [ ] **Step 3: Create LabelRepository.kt**

Create `backend/src/main/kotlin/com/taskowolf/labels/infrastructure/LabelRepository.kt`:

```kotlin
package com.taskowolf.labels.infrastructure

import com.taskowolf.labels.domain.Label
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface LabelRepository : JpaRepository<Label, UUID> {
    fun findByProjectId(projectId: UUID): List<Label>
    fun existsByProjectIdAndName(projectId: UUID, name: String): Boolean

    @Query(
        value = "SELECT l.* FROM labels l INNER JOIN issue_labels il ON l.id = il.label_id WHERE il.issue_id = :issueId",
        nativeQuery = true
    )
    fun findByIssueId(@Param("issueId") issueId: UUID): List<Label>
}
```

- [ ] **Step 4: Verify the migration compiles by starting the backend**

```bash
cd backend && ./gradlew bootRun
```

Look for `Flyway` log line: `Successfully applied 1 migration to schema "public", now at version v23`. Then stop with Ctrl+C.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/db/migration/V23__labels.sql \
        backend/src/main/kotlin/com/taskowolf/labels/domain/Label.kt \
        backend/src/main/kotlin/com/taskowolf/labels/infrastructure/LabelRepository.kt
git commit -m "feat(labels): V23 migration + Label entity + LabelRepository"
```

---

## Task 2: Labels CRUD Backend

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/labels/api/dto/LabelRequest.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/labels/api/dto/LabelResponse.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/labels/application/LabelService.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/labels/api/LabelController.kt`
- Create: `backend/src/test/kotlin/com/taskowolf/labels/LabelServiceTest.kt`

**Interfaces:**
- Consumes: `LabelRepository` (from Task 1), `ProjectService.requireMember()`
- Produces: `GET/POST/PUT/DELETE /api/v1/projects/{key}/labels` endpoints; `LabelResponse(id, name, color)`

---

- [ ] **Step 1: Write failing tests for LabelService**

Create `backend/src/test/kotlin/com/taskowolf/labels/LabelServiceTest.kt`:

```kotlin
package com.taskowolf.labels

import com.taskowolf.auth.domain.User
import com.taskowolf.core.infrastructure.ConflictException
import com.taskowolf.core.infrastructure.NotFoundException
import com.taskowolf.labels.api.dto.LabelRequest
import com.taskowolf.labels.application.LabelService
import com.taskowolf.labels.domain.Label
import com.taskowolf.labels.infrastructure.LabelRepository
import com.taskowolf.projects.application.ProjectService
import com.taskowolf.projects.domain.Project
import io.mockk.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Optional
import java.util.UUID

class LabelServiceTest {

    private val labelRepository = mockk<LabelRepository>()
    private val projectService = mockk<ProjectService>()
    private val service = LabelService(labelRepository, projectService)

    private val actor = User(email = "alice@test.com", displayName = "Alice")
    private val project = Project(key = "WOLF", name = "TaskWolf", owner = actor, workflow = null)

    @Test
    fun `list returns labels for project`() {
        val label = Label(name = "bug", color = "#e11d48", project = project)
        every { projectService.requireMember("WOLF", actor.id) } returns project
        every { labelRepository.findByProjectId(project.id) } returns listOf(label)

        val result = service.list("WOLF", actor.id)

        assertEquals(1, result.size)
        assertEquals("bug", result[0].name)
    }

    @Test
    fun `create saves new label`() {
        every { projectService.requireMember("WOLF", actor.id) } returns project
        every { labelRepository.existsByProjectIdAndName(project.id, "bug") } returns false
        every { labelRepository.save(any()) } answers { firstArg() }

        val result = service.create("WOLF", LabelRequest("bug", "#e11d48"), actor)

        assertEquals("bug", result.name)
        assertEquals("#e11d48", result.color)
        verify { labelRepository.save(any()) }
    }

    @Test
    fun `create throws ConflictException when name already exists`() {
        every { projectService.requireMember("WOLF", actor.id) } returns project
        every { labelRepository.existsByProjectIdAndName(project.id, "bug") } returns true

        assertThrows<ConflictException> {
            service.create("WOLF", LabelRequest("bug", "#e11d48"), actor)
        }
    }

    @Test
    fun `update changes name and color`() {
        val label = Label(name = "old", color = "#000000", project = project)
        every { projectService.requireMember("WOLF", actor.id) } returns project
        every { labelRepository.findById(label.id) } returns Optional.of(label)
        every { labelRepository.existsByProjectIdAndName(project.id, "new") } returns false
        every { labelRepository.save(any()) } answers { firstArg() }

        val result = service.update("WOLF", label.id, LabelRequest("new", "#ffffff"), actor)

        assertEquals("new", result.name)
        assertEquals("#ffffff", result.color)
    }

    @Test
    fun `delete removes label`() {
        val label = Label(name = "bug", color = "#e11d48", project = project)
        every { projectService.requireMember("WOLF", actor.id) } returns project
        every { labelRepository.findById(label.id) } returns Optional.of(label)
        every { labelRepository.delete(any()) } just Runs

        service.delete("WOLF", label.id, actor)

        verify { labelRepository.delete(label) }
    }

    @Test
    fun `delete throws NotFoundException when label not in project`() {
        val otherProject = Project(key = "OTHER", name = "Other", owner = actor, workflow = null)
        val label = Label(name = "bug", color = "#e11d48", project = otherProject)
        every { projectService.requireMember("WOLF", actor.id) } returns project
        every { labelRepository.findById(label.id) } returns Optional.of(label)

        assertThrows<NotFoundException> {
            service.delete("WOLF", label.id, actor)
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd backend && ./gradlew test --tests "com.taskowolf.labels.LabelServiceTest" -i 2>&1 | tail -20
```

Expected: compilation errors (classes not yet created).

- [ ] **Step 3: Create LabelRequest.kt and LabelResponse.kt**

Create `backend/src/main/kotlin/com/taskowolf/labels/api/dto/LabelRequest.kt`:

```kotlin
package com.taskowolf.labels.api.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class LabelRequest(
    @field:NotBlank @field:Size(max = 50)
    val name: String,

    @field:NotBlank @field:Pattern(regexp = "^#[0-9a-fA-F]{6}$")
    val color: String
)
```

Create `backend/src/main/kotlin/com/taskowolf/labels/api/dto/LabelResponse.kt`:

```kotlin
package com.taskowolf.labels.api.dto

import com.taskowolf.labels.domain.Label
import java.util.UUID

data class LabelResponse(
    val id: UUID,
    val name: String,
    val color: String
) {
    companion object {
        fun from(l: Label) = LabelResponse(id = l.id, name = l.name, color = l.color)
    }
}
```

- [ ] **Step 4: Create LabelService.kt**

Create `backend/src/main/kotlin/com/taskowolf/labels/application/LabelService.kt`:

```kotlin
package com.taskowolf.labels.application

import com.taskowolf.auth.domain.User
import com.taskowolf.core.infrastructure.ConflictException
import com.taskowolf.core.infrastructure.NotFoundException
import com.taskowolf.labels.api.dto.LabelRequest
import com.taskowolf.labels.domain.Label
import com.taskowolf.labels.infrastructure.LabelRepository
import com.taskowolf.projects.application.ProjectService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class LabelService(
    private val labelRepository: LabelRepository,
    private val projectService: ProjectService
) {
    @Transactional(readOnly = true)
    fun list(projectKey: String, userId: UUID): List<Label> {
        val project = projectService.requireMember(projectKey, userId)
        return labelRepository.findByProjectId(project.id)
    }

    @Transactional
    fun create(projectKey: String, request: LabelRequest, actor: User): Label {
        val project = projectService.requireMember(projectKey, actor.id)
        if (labelRepository.existsByProjectIdAndName(project.id, request.name)) {
            throw ConflictException("Label '${request.name}' already exists in this project")
        }
        return labelRepository.save(Label(name = request.name, color = request.color, project = project))
    }

    @Transactional
    fun update(projectKey: String, labelId: UUID, request: LabelRequest, actor: User): Label {
        val project = projectService.requireMember(projectKey, actor.id)
        val label = labelRepository.findById(labelId)
            .filter { it.project.id == project.id }
            .orElseThrow { NotFoundException("Label not found: $labelId") }
        if (label.name != request.name && labelRepository.existsByProjectIdAndName(project.id, request.name)) {
            throw ConflictException("Label '${request.name}' already exists in this project")
        }
        label.name = request.name
        label.color = request.color
        return labelRepository.save(label)
    }

    @Transactional
    fun delete(projectKey: String, labelId: UUID, actor: User) {
        val project = projectService.requireMember(projectKey, actor.id)
        val label = labelRepository.findById(labelId)
            .filter { it.project.id == project.id }
            .orElseThrow { NotFoundException("Label not found: $labelId") }
        labelRepository.delete(label)
    }
}
```

- [ ] **Step 5: Create LabelController.kt**

Create `backend/src/main/kotlin/com/taskowolf/labels/api/LabelController.kt`:

```kotlin
package com.taskowolf.labels.api

import com.taskowolf.auth.domain.User
import com.taskowolf.labels.api.dto.LabelRequest
import com.taskowolf.labels.api.dto.LabelResponse
import com.taskowolf.labels.application.LabelService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/projects/{key}/labels")
class LabelController(private val labelService: LabelService) {

    @GetMapping
    fun list(@PathVariable key: String, @AuthenticationPrincipal user: User) =
        labelService.list(key, user.id).map { LabelResponse.from(it) }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @PathVariable key: String,
        @Valid @RequestBody request: LabelRequest,
        @AuthenticationPrincipal user: User
    ) = LabelResponse.from(labelService.create(key, request, user))

    @PutMapping("/{id}")
    fun update(
        @PathVariable key: String,
        @PathVariable id: UUID,
        @Valid @RequestBody request: LabelRequest,
        @AuthenticationPrincipal user: User
    ) = LabelResponse.from(labelService.update(key, id, request, user))

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable key: String,
        @PathVariable id: UUID,
        @AuthenticationPrincipal user: User
    ) = labelService.delete(key, id, user)
}
```

- [ ] **Step 6: Run tests**

```bash
cd backend && ./gradlew test --tests "com.taskowolf.labels.LabelServiceTest" -i 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL` and all 5 tests pass.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/labels/ \
        backend/src/test/kotlin/com/taskowolf/labels/
git commit -m "feat(labels): LabelService, LabelController, LabelResponse, LabelRequest"
```

---

## Task 3: Issue-Labels Integration (Backend)

**Files:**
- Modify: `backend/src/main/kotlin/com/taskowolf/issues/domain/Issue.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/issues/api/dto/IssueResponse.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/issues/api/dto/UpdateIssueRequest.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/issues/application/IssueService.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/issues/infrastructure/IssueRepository.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/issues/api/IssueController.kt`
- Modify: `backend/src/test/kotlin/com/taskowolf/issues/IssueServiceTest.kt`

**Interfaces:**
- Consumes: `LabelRepository.findByIssueId()`, `LabelRepository.findAllById()`
- Produces: `IssueResponse.labels: List<LabelResponse>`; `UpdateIssueRequest.labelIds`; `IssueController.list(labelId)`; `IssueController.get()` returns labels

---

- [ ] **Step 1: Add `@ManyToMany` to Issue.kt**

In `backend/src/main/kotlin/com/taskowolf/issues/domain/Issue.kt`, add the import and field. The file currently ends with `var slaStartTime: Instant? = null`. Add after it:

New import to add at the top of the file:
```kotlin
import com.taskowolf.labels.domain.Label
import jakarta.persistence.ManyToMany
import jakarta.persistence.JoinTable
import jakarta.persistence.JoinColumn
```

New field to add inside the `Issue` class, after `var slaStartTime`:
```kotlin
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "issue_labels",
        joinColumns = [JoinColumn(name = "issue_id")],
        inverseJoinColumns = [JoinColumn(name = "label_id")]
    )
    var labels: MutableSet<Label> = mutableSetOf()
```

- [ ] **Step 2: Extend IssueResponse.kt**

In `IssueResponse.kt`, add `labels: List<LabelResponse> = emptyList()` as the last constructor parameter, and update `from()`:

```kotlin
package com.taskowolf.issues.api.dto

import com.taskowolf.integrations.api.dto.IssueRefResponse
import com.taskowolf.issues.domain.Issue
import com.taskowolf.issues.domain.IssuePriority
import com.taskowolf.issues.domain.IssueType
import com.taskowolf.labels.api.dto.LabelResponse
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
    val labels: List<LabelResponse> = emptyList()
) {
    companion object {
        fun from(
            i: Issue,
            refs: List<IssueRefResponse> = emptyList(),
            labels: List<LabelResponse> = emptyList()
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
            labels = labels
        )
    }
}
```

- [ ] **Step 3: Add `labelIds` to UpdateIssueRequest.kt**

Replace the content of `UpdateIssueRequest.kt`:

```kotlin
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
    val labelIds: List<UUID>? = null
)
```

- [ ] **Step 4: Write failing label tests in IssueServiceTest.kt**

Add these two tests to the end of `IssueServiceTest.kt` (inside the class, after the last `}`):

```kotlin
    @Test
    fun `update sets labels when labelIds provided`() {
        val label = com.taskowolf.labels.domain.Label(
            name = "bug", color = "#e11d48", project = project
        )
        val labelRepository = mockk<com.taskowolf.labels.infrastructure.LabelRepository>()
        // Re-create service with labelRepository
        val serviceWithLabels = com.taskowolf.issues.application.IssueService(
            issueRepository, projectService, workflowService, userRepository, eventPublisher, sprintRepository, labelRepository
        )
        every { projectService.requireMember("WOLF", owner.id) } returns project
        every { issueRepository.findById(issue.id) } returns java.util.Optional.of(issue)
        every { labelRepository.findAllById(listOf(label.id)) } returns listOf(label)
        every { issueRepository.save(any()) } returnsArgument 0

        val updated = serviceWithLabels.update(
            "WOLF", issue.id,
            com.taskowolf.issues.api.dto.UpdateIssueRequest(labelIds = listOf(label.id)),
            owner
        )

        assert(updated.labels.contains(label))
    }

    @Test
    fun `update clears labels when labelIds is empty list`() {
        val label = com.taskowolf.labels.domain.Label(
            name = "bug", color = "#e11d48", project = project
        )
        issue.labels.add(label)
        val labelRepository = mockk<com.taskowolf.labels.infrastructure.LabelRepository>()
        val serviceWithLabels = com.taskowolf.issues.application.IssueService(
            issueRepository, projectService, workflowService, userRepository, eventPublisher, sprintRepository, labelRepository
        )
        every { projectService.requireMember("WOLF", owner.id) } returns project
        every { issueRepository.findById(issue.id) } returns java.util.Optional.of(issue)
        every { labelRepository.findAllById(emptyList()) } returns emptyList()
        every { issueRepository.save(any()) } returnsArgument 0

        val updated = serviceWithLabels.update(
            "WOLF", issue.id,
            com.taskowolf.issues.api.dto.UpdateIssueRequest(labelIds = emptyList()),
            owner
        )

        assert(updated.labels.isEmpty())
    }
```

Note: these tests will fail until IssueService is updated to accept LabelRepository.

- [ ] **Step 5: Run IssueServiceTest to confirm existing tests still pass (new tests will fail)**

```bash
cd backend && ./gradlew test --tests "com.taskowolf.issues.IssueServiceTest" -i 2>&1 | tail -30
```

Expected: existing tests pass, new label tests fail with compilation error.

- [ ] **Step 6: Update IssueService.kt to inject LabelRepository and handle labelIds**

The full updated IssueService constructor and update() section. Change the class signature and add the label block inside `update()`:

**New constructor** (add `labelRepository` as last parameter):
```kotlin
@Service
class IssueService(
    private val issueRepository: IssueRepository,
    private val projectService: ProjectService,
    private val workflowService: WorkflowService,
    private val userRepository: UserRepository,
    private val eventPublisher: DomainEventPublisher,
    private val sprintRepository: SprintRepository,
    private val labelRepository: com.taskowolf.labels.infrastructure.LabelRepository
) {
```

**Inside `update()`, add at the end before `return issueRepository.save(issue)`:**
```kotlin
        request.labelIds?.let { ids ->
            val newLabels = if (ids.isEmpty()) mutableSetOf()
                            else labelRepository.findAllById(ids).toMutableSet()
            val oldNames = issue.labels.map { it.name }.sorted().joinToString(", ")
            issue.labels.clear()
            issue.labels.addAll(newLabels)
            val newNames = newLabels.map { it.name }.sorted().joinToString(", ")
            eventPublisher.publish(
                IssueFieldChangedEvent(issue, currentUser, "labels",
                    oldNames.ifEmpty { null }, newNames.ifEmpty { null })
            )
        }
```

- [ ] **Step 7: Add `findAllByProjectIdAndLabelId` to IssueRepository.kt**

Add at the end of the interface:

```kotlin
    @Query("SELECT i FROM Issue i JOIN i.labels l WHERE i.project.id = :projectId AND l.id = :labelId")
    fun findAllByProjectIdAndLabelId(
        projectId: UUID,
        labelId: UUID,
        pageable: Pageable
    ): Page<Issue>
```

- [ ] **Step 8: Update IssueService.findByProject() to accept labelId**

Change the signature and body of `findByProject()`:

```kotlin
    @Transactional(readOnly = true)
    fun findByProject(
        projectKey: String,
        userId: UUID,
        page: Int,
        size: Int,
        assigneeMe: Boolean = false,
        sort: String? = null,
        overdue: Boolean = false,
        labelId: UUID? = null
    ): org.springframework.data.domain.Page<Issue> {
        val project = projectService.requireMember(projectKey, userId)
        val pageable = when (sort) {
            "updatedAt" -> PageRequest.of(page, size, org.springframework.data.domain.Sort.by("updatedAt").descending())
            else -> PageRequest.of(page, size)
        }
        if (labelId != null) return issueRepository.findAllByProjectIdAndLabelId(project.id, labelId, pageable)
        return when {
            overdue && assigneeMe -> issueRepository.findOverdueByProjectIdAndAssigneeId(project.id, userId, StatusCategory.DONE, pageable)
            overdue -> issueRepository.findOverdueByProjectId(project.id, StatusCategory.DONE, pageable)
            assigneeMe -> issueRepository.findByProjectIdAndAssigneeId(project.id, userId, pageable)
            else -> issueRepository.findAllByProjectId(project.id, pageable)
        }
    }
```

- [ ] **Step 9: Update IssueController.kt to inject LabelRepository, pass labels in `get()`, add `labelId` to `list()`**

Full replacement of `IssueController.kt`:

```kotlin
package com.taskowolf.issues.api

import com.taskowolf.auth.domain.User
import com.taskowolf.integrations.api.dto.IssueRefResponse
import com.taskowolf.integrations.infrastructure.IssueRefRepository
import com.taskowolf.issues.api.dto.CreateIssueRequest
import com.taskowolf.issues.api.dto.IssueResponse
import com.taskowolf.issues.api.dto.UpdateIssueRequest
import com.taskowolf.issues.application.IssueService
import com.taskowolf.labels.api.dto.LabelResponse
import com.taskowolf.labels.infrastructure.LabelRepository
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/projects/{key}/issues")
class IssueController(
    private val issueService: IssueService,
    private val issueRefRepository: IssueRefRepository,
    private val labelRepository: LabelRepository
) {

    @GetMapping
    fun list(
        @PathVariable key: String,
        @AuthenticationPrincipal user: User,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        @RequestParam(defaultValue = "false") assigneeMe: Boolean,
        @RequestParam(required = false) sort: String?,
        @RequestParam(defaultValue = "false") overdue: Boolean,
        @RequestParam(required = false) labelId: UUID?
    ) = issueService.findByProject(key, user.id, page, size, assigneeMe, sort, overdue, labelId)
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
        val refs = issueRefRepository.findByIssueIdOrderByCreatedAtAsc(issue.id).map { IssueRefResponse.from(it) }
        val labels = labelRepository.findByIssueId(issue.id).map { LabelResponse.from(it) }
        return IssueResponse.from(issue, refs, labels)
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

- [ ] **Step 10: Run all issue and label tests**

```bash
cd backend && ./gradlew test --tests "com.taskowolf.issues.IssueServiceTest" --tests "com.taskowolf.labels.LabelServiceTest" -i 2>&1 | tail -30
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 11: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/issues/ \
        backend/src/test/kotlin/com/taskowolf/issues/IssueServiceTest.kt
git commit -m "feat(labels): issue-labels integration — @ManyToMany, UpdateIssueRequest.labelIds, list filter"
```

---

## Task 4: Frontend Types, API, Hooks

**Files:**
- Modify: `frontend/src/types/index.ts`
- Create: `frontend/src/api/labels.ts`
- Create: `frontend/src/hooks/useLabels.ts`

**Interfaces:**
- Produces: `Label` type `{id, name, color}`; `Issue.labels: Label[]`; `labelsApi`; `useLabels`, `useCreateLabel`, `useUpdateLabel`, `useDeleteLabel`

---

- [ ] **Step 1: Add `Label` type and update `Issue` in types/index.ts**

After the `export interface Project { ... }` block, add:

```typescript
export interface Label {
  id: string
  name: string
  color: string
}
```

In the `export interface Issue { ... }` block, add after `refs?: IssueRefResponse[]`:

```typescript
  labels?: Label[]
```

- [ ] **Step 2: Create labels.ts API**

Create `frontend/src/api/labels.ts`:

```typescript
import { apiClient } from './client'
import type { Label } from '@/types'

export const labelsApi = {
  list: (projectKey: string) =>
    apiClient.get<Label[]>(`/projects/${projectKey}/labels`),
  create: (projectKey: string, data: { name: string; color: string }) =>
    apiClient.post<Label>(`/projects/${projectKey}/labels`, data),
  update: (projectKey: string, id: string, data: { name: string; color: string }) =>
    apiClient.put<Label>(`/projects/${projectKey}/labels/${id}`, data),
  delete: (projectKey: string, id: string) =>
    apiClient.delete(`/projects/${projectKey}/labels/${id}`),
}
```

- [ ] **Step 3: Create useLabels.ts hook**

Create `frontend/src/hooks/useLabels.ts`:

```typescript
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { labelsApi } from '@/api/labels'

export function useLabels(projectKey: string) {
  return useQuery({
    queryKey: ['labels', projectKey],
    queryFn: () => labelsApi.list(projectKey).then(r => r.data),
  })
}

export function useCreateLabel(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: { name: string; color: string }) =>
      labelsApi.create(projectKey, data).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['labels', projectKey] }),
  })
}

export function useUpdateLabel(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, ...data }: { id: string; name: string; color: string }) =>
      labelsApi.update(projectKey, id, data).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['labels', projectKey] }),
  })
}

export function useDeleteLabel(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => labelsApi.delete(projectKey, id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['labels', projectKey] }),
  })
}
```

- [ ] **Step 4: Type-check**

```bash
cd frontend && npx tsc --noEmit 2>&1 | head -30
```

Expected: no errors.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/types/index.ts \
        frontend/src/api/labels.ts \
        frontend/src/hooks/useLabels.ts
git commit -m "feat(labels): frontend Label type, labelsApi, useLabels hooks"
```

---

## Task 5: LabelChip + LabelSelector + IssueDetailPage

**Files:**
- Create: `frontend/src/components/issue/LabelChip.tsx`
- Create: `frontend/src/components/issue/LabelSelector.tsx`
- Modify: `frontend/src/pages/issues/IssueDetailPage.tsx`

**Interfaces:**
- Consumes: `Label` type; `useLabels`, `useCreateLabel`; `useUpdateIssue` (already in IssueDetailPage)
- Produces: `<LabelChip label={label} onClick?={fn} />`; `<LabelSelector projectKey value labels onSave />`

---

- [ ] **Step 1: Create LabelChip.tsx**

Create `frontend/src/components/issue/LabelChip.tsx`:

```tsx
import type { Label } from '@/types'

interface Props {
  label: Label
  onClick?: () => void
}

export function LabelChip({ label, onClick }: Props) {
  const base = 'inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium border'
  return (
    <span
      className={`${base} ${onClick ? 'cursor-pointer hover:opacity-80' : ''}`}
      style={{
        backgroundColor: label.color + '26',  // ~15% opacity
        color: label.color,
        borderColor: label.color + '4d',      // ~30% opacity
      }}
      onClick={onClick}
    >
      {label.name}
    </span>
  )
}
```

- [ ] **Step 2: Create LabelSelector.tsx**

Create `frontend/src/components/issue/LabelSelector.tsx`:

```tsx
import { useState, useRef, useEffect } from 'react'
import type { Label } from '@/types'
import { LabelChip } from './LabelChip'
import { useCreateLabel } from '@/hooks/useLabels'

const PALETTE = [
  '#e11d48','#f97316','#eab308','#22c55e',
  '#14b8a6','#3b82f6','#8b5cf6','#ec4899',
  '#64748b','#0ea5e9','#84cc16','#f43f5e',
]

interface Props {
  projectKey: string
  value: Label[]          // currently assigned labels
  allLabels: Label[]      // all project labels
  onSave: (labelIds: string[]) => void
}

export function LabelSelector({ projectKey, value, allLabels, onSave }: Props) {
  const [open, setOpen] = useState(false)
  const [search, setSearch] = useState('')
  const [selected, setSelected] = useState<Label[]>(value)
  const ref = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLInputElement>(null)
  const createLabel = useCreateLabel(projectKey)

  useEffect(() => { setSelected(value) }, [value])

  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        if (open) {
          onSave(selected.map(l => l.id))
          setOpen(false)
          setSearch('')
        }
      }
    }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [open, selected, onSave])

  useEffect(() => { if (open) inputRef.current?.focus() }, [open])

  const filtered = allLabels.filter(l =>
    l.name.toLowerCase().includes(search.toLowerCase())
  )
  const exactMatch = allLabels.some(l => l.name.toLowerCase() === search.toLowerCase())

  function toggle(label: Label) {
    setSelected(prev =>
      prev.some(l => l.id === label.id)
        ? prev.filter(l => l.id !== label.id)
        : [...prev, label]
    )
  }

  async function handleCreate() {
    const newLabel = await createLabel.mutateAsync({
      name: search.trim(),
      color: PALETTE[allLabels.length % PALETTE.length],
    })
    setSelected(prev => [...prev, newLabel])
    setSearch('')
  }

  return (
    <div ref={ref} className="relative">
      <div
        className="flex flex-wrap gap-1 cursor-pointer min-h-[24px]"
        onClick={() => setOpen(o => !o)}
      >
        {selected.length === 0
          ? <span className="text-sm text-gray-500 hover:text-gray-300">None</span>
          : selected.map(l => <LabelChip key={l.id} label={l} />)
        }
      </div>
      {open && (
        <div className="absolute z-50 top-7 left-0 bg-gray-800 border border-gray-700 rounded shadow-lg py-1 min-w-52 max-h-64 overflow-y-auto">
          <div className="px-2 pb-1">
            <input
              ref={inputRef}
              value={search}
              onChange={e => setSearch(e.target.value)}
              placeholder="Search labels…"
              className="w-full bg-gray-700 text-sm text-white rounded px-2 py-1 outline-none"
            />
          </div>
          {filtered.map(l => (
            <button
              key={l.id}
              onClick={() => toggle(l)}
              className="w-full text-left px-3 py-1.5 text-sm text-gray-300 hover:bg-gray-700 flex items-center gap-2"
            >
              <span className={`w-3 h-3 rounded-full flex-shrink-0 ${selected.some(s => s.id === l.id) ? 'ring-2 ring-white' : ''}`}
                    style={{ backgroundColor: l.color }} />
              <LabelChip label={l} />
            </button>
          ))}
          {search.trim() && !exactMatch && (
            <button
              onClick={handleCreate}
              disabled={createLabel.isPending}
              className="w-full text-left px-3 py-1.5 text-sm text-blue-400 hover:bg-gray-700"
            >
              + Create label "{search.trim()}"
            </button>
          )}
        </div>
      )}
    </div>
  )
}
```

- [ ] **Step 3: Wire LabelSelector into IssueDetailPage**

In `frontend/src/pages/issues/IssueDetailPage.tsx`:

Add imports at the top:
```typescript
import { LabelSelector } from '@/components/issue/LabelSelector'
import { useLabels } from '@/hooks/useLabels'
import { useNavigate } from 'react-router-dom'
import { LabelChip } from '@/components/issue/LabelChip'
```

Add inside the component, after `const { data: sprints = [] } = useSprints(key!)`:
```typescript
  const { data: allLabels = [] } = useLabels(key!)
  const navigate = useNavigate()
```

In the sidebar (right column), add after the `<SidebarField label="Due Date">` block and before `{issue.storyPoints != null && ...}`:
```tsx
            <SidebarField label="Labels">
              <LabelSelector
                projectKey={key!}
                value={issue.labels ?? []}
                allLabels={allLabels}
                onSave={labelIds => patch({ labelIds })}
              />
            </SidebarField>
```

Also update the `useNavigate` usage: in the LabelChip click in this page (for the filter navigation), labels in the detail view don't show as clickable chips here yet — that is handled in Task 7.

- [ ] **Step 4: Type-check**

```bash
cd frontend && npx tsc --noEmit 2>&1 | head -30
```

Expected: no errors.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/issue/LabelChip.tsx \
        frontend/src/components/issue/LabelSelector.tsx \
        frontend/src/pages/issues/IssueDetailPage.tsx
git commit -m "feat(labels): LabelChip, LabelSelector, IssueDetailPage sidebar integration"
```

---

## Task 6: Labels Settings Page + Router + Nav

**Files:**
- Create: `frontend/src/pages/projects/settings/LabelsPage.tsx`
- Modify: `frontend/src/app/router.tsx`
- Modify: `frontend/src/layouts/AppLayout.tsx`

**Interfaces:**
- Consumes: `useLabels`, `useCreateLabel`, `useUpdateLabel`, `useDeleteLabel`; `LabelChip`

---

- [ ] **Step 1: Create LabelsPage.tsx**

Create `frontend/src/pages/projects/settings/LabelsPage.tsx`:

```tsx
import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { useLabels, useCreateLabel, useUpdateLabel, useDeleteLabel } from '@/hooks/useLabels'
import { LabelChip } from '@/components/issue/LabelChip'
import type { Label } from '@/types'

const PALETTE = [
  '#e11d48','#f97316','#eab308','#22c55e',
  '#14b8a6','#3b82f6','#8b5cf6','#ec4899',
  '#64748b','#0ea5e9','#84cc16','#f43f5e',
]

function LabelForm({
  initial,
  onSubmit,
  onCancel,
}: {
  initial?: { name: string; color: string }
  onSubmit: (name: string, color: string) => void
  onCancel: () => void
}) {
  const [name, setName] = useState(initial?.name ?? '')
  const [color, setColor] = useState(initial?.color ?? PALETTE[0])
  const [error, setError] = useState('')

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!name.trim()) { setError('Name is required'); return }
    if (name.trim().length > 50) { setError('Max 50 characters'); return }
    onSubmit(name.trim(), color)
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-3 p-4 bg-gray-800 rounded-lg border border-gray-700">
      <div>
        <label className="block text-xs text-gray-400 mb-1">Name</label>
        <input
          value={name}
          onChange={e => { setName(e.target.value); setError('') }}
          maxLength={50}
          placeholder="Label name"
          autoFocus
          className="w-full bg-gray-700 border border-gray-600 rounded px-3 py-1.5 text-sm text-white outline-none focus:border-blue-500"
        />
        {error && <p className="text-xs text-red-400 mt-1">{error}</p>}
      </div>
      <div>
        <label className="block text-xs text-gray-400 mb-1">Color</label>
        <div className="flex flex-wrap gap-2">
          {PALETTE.map(c => (
            <button
              key={c}
              type="button"
              onClick={() => setColor(c)}
              className={`w-6 h-6 rounded-full border-2 transition-transform ${color === c ? 'border-white scale-110' : 'border-transparent hover:scale-105'}`}
              style={{ backgroundColor: c }}
            />
          ))}
        </div>
      </div>
      <div>
        <label className="block text-xs text-gray-400 mb-1">Preview</label>
        <LabelChip label={{ id: '', name: name || 'Preview', color }} />
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

export function LabelsPage() {
  const { key } = useParams<{ key: string }>()
  const { data: labels = [], isLoading } = useLabels(key!)
  const createLabel = useCreateLabel(key!)
  const updateLabel = useUpdateLabel(key!)
  const deleteLabel = useDeleteLabel(key!)

  const [showCreate, setShowCreate] = useState(false)
  const [editing, setEditing] = useState<Label | null>(null)
  const [apiError, setApiError] = useState('')

  async function handleCreate(name: string, color: string) {
    try {
      await createLabel.mutateAsync({ name, color })
      setShowCreate(false)
      setApiError('')
    } catch {
      setApiError('A label with that name already exists.')
    }
  }

  async function handleUpdate(name: string, color: string) {
    if (!editing) return
    try {
      await updateLabel.mutateAsync({ id: editing.id, name, color })
      setEditing(null)
      setApiError('')
    } catch {
      setApiError('A label with that name already exists.')
    }
  }

  async function handleDelete(id: string) {
    if (!confirm('Delete this label? It will be removed from all issues.')) return
    await deleteLabel.mutateAsync(id)
  }

  if (isLoading) return <div className="text-gray-400 p-6">Loading…</div>

  return (
    <div className="p-6 space-y-6 max-w-2xl">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Labels</h1>
        {!showCreate && (
          <button
            onClick={() => setShowCreate(true)}
            className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded text-sm font-medium"
          >
            + New Label
          </button>
        )}
      </div>

      {apiError && <p className="text-sm text-red-400">{apiError}</p>}

      {showCreate && (
        <LabelForm onSubmit={handleCreate} onCancel={() => { setShowCreate(false); setApiError('') }} />
      )}

      <div className="flex flex-col gap-2">
        {labels.map(label => (
          <div key={label.id}>
            {editing?.id === label.id ? (
              <LabelForm
                initial={{ name: label.name, color: label.color }}
                onSubmit={handleUpdate}
                onCancel={() => { setEditing(null); setApiError('') }}
              />
            ) : (
              <div className="flex items-center gap-3 px-4 py-3 bg-gray-900 border border-gray-800 rounded-lg">
                <LabelChip label={label} />
                <span className="text-xs text-gray-500 font-mono">{label.color}</span>
                <div className="ml-auto flex gap-2">
                  <button
                    onClick={() => setEditing(label)}
                    className="text-xs text-gray-400 hover:text-white px-2 py-1 rounded hover:bg-gray-700"
                  >
                    Edit
                  </button>
                  <button
                    onClick={() => handleDelete(label.id)}
                    className="text-xs text-red-400 hover:text-red-300 px-2 py-1 rounded hover:bg-gray-700"
                  >
                    Delete
                  </button>
                </div>
              </div>
            )}
          </div>
        ))}
        {labels.length === 0 && !showCreate && (
          <p className="text-sm text-gray-500 py-8 text-center">No labels yet. Create your first one!</p>
        )}
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Add route in router.tsx**

In `frontend/src/app/router.tsx`, add the import:
```typescript
import { LabelsPage } from '@/pages/projects/settings/LabelsPage'
```

In the routes array, after `{ path: '/p/:key/settings/audit', element: <ProjectAuditPage /> }`, add:
```typescript
      { path: '/p/:key/settings/labels', element: <LabelsPage /> },
```

- [ ] **Step 3: Add nav link in AppLayout.tsx**

In `frontend/src/layouts/AppLayout.tsx`, in the Settings section (inside the `{insideProject && projectKey && ...}` block), after the Audit Log nav link, add:

```tsx
                  <NavLink to={`/p/${projectKey}/settings/labels`} className={subNavLinkClass}>
                    Labels
                  </NavLink>
```

- [ ] **Step 4: Type-check**

```bash
cd frontend && npx tsc --noEmit 2>&1 | head -30
```

Expected: no errors.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/projects/settings/LabelsPage.tsx \
        frontend/src/app/router.tsx \
        frontend/src/layouts/AppLayout.tsx
git commit -m "feat(labels): LabelsPage settings page, route, nav link"
```

---

## Task 7: Issue List Label Filter

**Files:**
- Modify: `frontend/src/api/issues.ts`
- Modify: `frontend/src/hooks/useIssues.ts`
- Modify: `frontend/src/pages/issues/IssueListPage.tsx`
- Modify: `frontend/src/pages/issues/IssueDetailPage.tsx` (add navigate-on-chip-click)

**Interfaces:**
- Consumes: `useLabels`, `labelsApi`; `LabelChip` (clickable)
- Produces: `IssueListPage` with label filter dropdown; clicking a `LabelChip` in IssueDetailPage navigates to filtered list

---

- [ ] **Step 1: Update issuesApi.list() to accept labelId**

In `frontend/src/api/issues.ts`, replace the `list` method:

```typescript
  list: (projectKey: string, page = 0, size = 50, labelId?: string) =>
    apiClient.get<Page<Issue>>(`/projects/${projectKey}/issues`, {
      params: { page, size, ...(labelId ? { labelId } : {}) }
    }),
```

- [ ] **Step 2: Update useIssues to accept labelId**

In `frontend/src/hooks/useIssues.ts`, replace `useIssues`:

```typescript
export function useIssues(projectKey: string, labelId?: string) {
  return useQuery({
    queryKey: ['issues', projectKey, { labelId }],
    queryFn: () => issuesApi.list(projectKey, 0, 50, labelId).then(r => r.data)
  })
}
```

- [ ] **Step 3: Update IssueListPage to add label filter dropdown**

Replace the entire content of `frontend/src/pages/issues/IssueListPage.tsx`:

```tsx
import { useState } from 'react'
import { useParams, Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useIssues, useCreateIssue } from '@/hooks/useIssues'
import { useLabels } from '@/hooks/useLabels'
import { StatusBadge } from '@/components/issue/StatusBadge'

export function IssueListPage() {
  const { key } = useParams<{ key: string }>()
  const [searchParams, setSearchParams] = useSearchParams()
  const labelId = searchParams.get('labelId') ?? undefined

  const { data: page, isLoading } = useIssues(key!, labelId)
  const { data: labels = [] } = useLabels(key!)
  const createIssue = useCreateIssue(key!)
  const [title, setTitle] = useState('')
  const [showForm, setShowForm] = useState(false)

  const selectedLabel = labels.find(l => l.id === labelId)

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!title.trim()) return
    await createIssue.mutateAsync({ title })
    setTitle('')
    setShowForm(false)
  }

  function setLabelFilter(id: string | undefined) {
    if (id) setSearchParams({ labelId: id })
    else setSearchParams({})
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
      <div className="flex items-center gap-3 mb-4">
        <select
          value={labelId ?? ''}
          onChange={e => setLabelFilter(e.target.value || undefined)}
          className="bg-gray-800 border border-gray-700 text-sm text-white rounded px-3 py-1.5 outline-none"
        >
          <option value="">All Labels</option>
          {labels.map(l => (
            <option key={l.id} value={l.id}>{l.name}</option>
          ))}
        </select>
        {labelId && (
          <button
            onClick={() => setLabelFilter(undefined)}
            className="text-xs text-gray-400 hover:text-white"
          >
            ✕ Clear
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

- [ ] **Step 4: Make LabelChips in IssueDetailPage navigable**

In `frontend/src/pages/issues/IssueDetailPage.tsx`, the `navigate` import was added in Task 5. Now update the `LabelSelector` wrapper in the sidebar to also show clickable chips when not in edit mode. Replace the `<SidebarField label="Labels">` block added in Task 5 with:

```tsx
            <SidebarField label="Labels">
              <LabelSelector
                projectKey={key!}
                value={issue.labels ?? []}
                allLabels={allLabels}
                onSave={labelIds => patch({ labelIds })}
              />
              {(issue.labels ?? []).length > 0 && (
                <div className="flex flex-wrap gap-1 mt-1">
                  {(issue.labels ?? []).map(l => (
                    <LabelChip
                      key={l.id}
                      label={l}
                      onClick={() => navigate(`/p/${key}/issues?labelId=${l.id}`)}
                    />
                  ))}
                </div>
              )}
            </SidebarField>
```

Wait — the LabelSelector already renders the chips. To avoid duplicate rendering, instead wire the `onClick` into the LabelSelector's chip display (its trigger area). The cleanest approach: render chips with `onClick` outside the selector for navigation, while the selector itself handles assignment on click. But this creates two sets of chips.

Simpler: pass an optional `onChipClick` prop to `LabelSelector` and use it in the chip trigger area. Replace Step 4 with the following:

Update `LabelSelector` props (in `LabelSelector.tsx`) to add `onChipClick?: (label: Label) => void`:

```tsx
interface Props {
  projectKey: string
  value: Label[]
  allLabels: Label[]
  onSave: (labelIds: string[]) => void
  onChipClick?: (label: Label) => void
}
```

In the trigger `<div>` of `LabelSelector`, replace `<LabelChip key={l.id} label={l} />` with:
```tsx
<LabelChip key={l.id} label={l} onClick={onChipClick ? (e) => { e.stopPropagation(); onChipClick(l) } : undefined} />
```

Wait, but `LabelChip.onClick` is `() => void`, not an event handler. We need to stop propagation so clicking a chip doesn't open the selector. Update `LabelChip` to accept the click and stop propagation properly:

Update `LabelChip.tsx` `onClick` to:
```tsx
onClick={onClick ? (e: React.MouseEvent) => { e.stopPropagation(); onClick() } : undefined}
```

And in the `span`:
```tsx
<span
  className={...}
  style={...}
  onClick={onClick ? (e) => { e.stopPropagation(); onClick() } : undefined}
>
```

Then in `IssueDetailPage`, pass `onChipClick`:
```tsx
              <LabelSelector
                projectKey={key!}
                value={issue.labels ?? []}
                allLabels={allLabels}
                onSave={labelIds => patch({ labelIds })}
                onChipClick={l => navigate(`/p/${key}/issues?labelId=${l.id}`)}
              />
```

Apply all these changes in the files.

- [ ] **Step 5: Type-check**

```bash
cd frontend && npx tsc --noEmit 2>&1 | head -30
```

Expected: no errors.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/api/issues.ts \
        frontend/src/hooks/useIssues.ts \
        frontend/src/pages/issues/IssueListPage.tsx \
        frontend/src/pages/issues/IssueDetailPage.tsx \
        frontend/src/components/issue/LabelChip.tsx \
        frontend/src/components/issue/LabelSelector.tsx
git commit -m "feat(labels): issue list label filter, click-to-filter from issue detail"
```

---

## Task 8: Wiki Documentation

**Files:**
- Create: `mkdocs/developer-guide/backend/labels.md`
- Modify: `mkdocs/developer-guide/backend/issues.md`
- Modify: `mkdocs.yml`
- Modify: `mkdocs/developer-guide/frontend/components.md`
- Modify: `mkdocs/developer-guide/frontend/hooks.md`
- Modify: `mkdocs/developer-guide/frontend/pages.md`

**Interfaces:**
- Consumes: all implemented code from Tasks 1–7
- Produces: `python -m mkdocs build --strict` passes with zero warnings

---

- [ ] **Step 1: Create `mkdocs/developer-guide/backend/labels.md`**

```markdown
# Module: labels

## Purpose

Manages project-scoped colored labels. Labels can be assigned to issues in any quantity (many-to-many) and used to filter the issue list. Label CRUD is performed by project members via a dedicated settings page.

---

## Entities Owned

| Entity | Table | Key Fields |
|---|---|---|
| `Label` | `labels` | `name` VARCHAR(50) NOT NULL, `color` VARCHAR(7) NOT NULL (hex), `project` FK→projects NOT NULL; UNIQUE (project_id, name) |

The `issue_labels` join table is owned by `Issue.labels` (`@JoinTable` on `Issue`), not by `Label`.

---

## DB Schema

### `labels` (V23)

\`\`\`sql
CREATE TABLE labels (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    project_id UUID        NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name       VARCHAR(50) NOT NULL,
    color      VARCHAR(7)  NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (project_id, name)
);
\`\`\`

### `issue_labels` (V23)

Join table owned by `Issue.labels`. Cascade-deletes when either the issue or label is deleted.

| Column | Type | Constraint |
|---|---|---|
| `issue_id` | UUID | FK→issues ON DELETE CASCADE |
| `label_id` | UUID | FK→labels ON DELETE CASCADE |

Primary key: `(issue_id, label_id)`.

---

## API Endpoints

### `LabelController` — `/api/v1/projects/{key}/labels`

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/v1/projects/{key}/labels` | USER | Lists all labels for the project |
| POST | `/api/v1/projects/{key}/labels` | USER | Creates a label; 409 if name already exists in project |
| PUT | `/api/v1/projects/{key}/labels/{id}` | USER | Replaces name and color; 409 on name conflict |
| DELETE | `/api/v1/projects/{key}/labels/{id}` | USER | Deletes label; `issue_labels` rows removed by DB cascade |

All endpoints require project membership (`ProjectService.requireMember()`).

---

## Events Emitted

None. Label assignment changes on issues are recorded as `IssueFieldChangedEvent` by `IssueService.update()` in the issues module.

---

## Events Consumed

None.

---

## Key Files

| File | Purpose |
|---|---|
| `backend/src/main/resources/db/migration/V23__labels.sql` | Creates `labels` and `issue_labels` tables |
| `backend/src/main/kotlin/com/taskowolf/labels/domain/Label.kt` | JPA entity |
| `backend/src/main/kotlin/com/taskowolf/labels/infrastructure/LabelRepository.kt` | `findByProjectId`, `existsByProjectIdAndName`, `findByIssueId` (native SQL) |
| `backend/src/main/kotlin/com/taskowolf/labels/application/LabelService.kt` | CRUD logic |
| `backend/src/main/kotlin/com/taskowolf/labels/api/LabelController.kt` | REST controller |
| `backend/src/main/kotlin/com/taskowolf/labels/api/dto/LabelRequest.kt` | `{name, color}` for POST/PUT |
| `backend/src/main/kotlin/com/taskowolf/labels/api/dto/LabelResponse.kt` | `{id, name, color}` |
| `backend/src/test/kotlin/com/taskowolf/labels/LabelServiceTest.kt` | Unit tests |

---

## Extension Points

The color palette (`PALETTE`) is defined as a constant in `frontend/src/components/issue/LabelSelector.tsx`. The backend accepts any valid hex string — the palette is only enforced client-side.

---

## Common Pitfalls

- **`IssueController.get()` injects `LabelRepository` directly.** This is the only deliberate cross-module repository injection in the codebase. It exists because `findByIssueId` uses native SQL on `issue_labels`, a join table whose `@JoinTable` is declared on `Issue`. Do not replicate this pattern elsewhere.
- **`null` vs empty list on `UpdateIssueRequest.labelIds`.** `null` = no change to labels; `[]` = remove all labels. The service checks `request.labelIds != null` before touching the label set.
- **`@ManyToMany(fetch=LAZY)` on `Issue.labels`.** Do not access `issue.labels` outside a transaction. `IssueController.get()` fetches labels explicitly via `LabelRepository.findByIssueId()` to avoid lazy-loading surprises.

---

## Example

\`\`\`kotlin
// Create a label then assign it to an issue
val label = labelService.create("WOLF", LabelRequest("bug", "#e11d48"), actor)
issueService.update("WOLF", issueId, UpdateIssueRequest(labelIds = listOf(label.id)), actor)
\`\`\`

---

## Test Patterns

| File | What is tested |
|---|---|
| `LabelServiceTest` | `list` returns labels for the correct project |
| `LabelServiceTest` | `create` saves a new label |
| `LabelServiceTest` | `create` throws `ConflictException` when name already exists in project |
| `LabelServiceTest` | `update` changes name and color |
| `LabelServiceTest` | `delete` removes the label |
| `LabelServiceTest` | `delete` throws `NotFoundException` when label belongs to a different project |
| `IssueServiceTest` | `update` sets labels when `labelIds` is a non-empty list |
| `IssueServiceTest` | `update` clears labels when `labelIds` is an empty list |
```

- [ ] **Step 2: Update `mkdocs/developer-guide/backend/issues.md`**

**2a — Entities Owned table:** Append `, `labels: MutableSet<Label>` (@ManyToMany LAZY, join table `issue_labels`, owned by Issue)` to the end of the Issue row's Key Fields cell.

**2b — DB Schema:** After the `issue_links` section, add:

```markdown
### `labels` and `issue_labels` (V23)

See the [labels module](labels.md) for the full schema. `issue_labels` is the join table declared via `@JoinTable` on `Issue.labels`.
```

**2c — API Endpoints table:** Update the two affected rows:

- GET list: change description to `Lists issues paginated (`page`, `size`); optional `assigneeMe=true`, `sort=updatedAt`, `overdue=true`, `labelId=<UUID>` (single-label filter); `refs[]` is empty on list; `labels[]` is empty on list`
- PATCH: change description to `Partial update by issue UUID; `labelIds: List<UUID>?` replaces the full label set (null = no change, [] = remove all); publishes `IssueFieldChangedEvent` or `IssueStatusChangedEvent` per changed field`

**2d — Common Pitfalls:** Add after the existing pitfall list:

```markdown
- **`IssueController.get()` injects `LabelRepository` directly.** This is a deliberate exception to the no-cross-module-injection rule, required to fetch labels via native SQL on `issue_labels`. The list endpoint (`IssueController.list()`) does not populate labels; only the single-issue GET does.
```

**2e — Test Patterns (unit tests table):** Add two rows:

```markdown
| `IssueServiceTest` | `update` sets labels when `labelIds` is a non-empty list |
| `IssueServiceTest` | `update` clears labels when `labelIds` is an empty list |
```

- [ ] **Step 3: Update `mkdocs.yml`**

In the backend nav section, add `labels` after `issues`:

```yaml
          - issues: developer-guide/backend/issues.md
          - labels: developer-guide/backend/labels.md
```

- [ ] **Step 4: Update `mkdocs/developer-guide/frontend/components.md`**

In the component directory listing block, update the `issue/` line from:

```
  issue/          # StatusBadge, AssigneeSelector, PrioritySelector, InlineEditTitle,
  #               # RichTextEditor, DueDatePicker, TypeSelector, SprintSelector
```

to:

```
  issue/          # StatusBadge, AssigneeSelector, PrioritySelector, InlineEditTitle,
  #               # RichTextEditor, DueDatePicker, TypeSelector, SprintSelector,
  #               # LabelChip, LabelSelector
```

- [ ] **Step 5: Update `mkdocs/developer-guide/frontend/hooks.md`**

In the Query Key Conventions table, add after the `['members', projectKey]` row:

```markdown
| `['labels', projectKey]` | `useLabels`, `useCreateLabel`, `useUpdateLabel`, `useDeleteLabel` |
```

- [ ] **Step 6: Update `mkdocs/developer-guide/frontend/pages.md`**

**6a — Page directory listing:** Update the `projects/settings/` line from:

```
  projects/settings/          # ProjectAuditPage
```

to:

```
  projects/settings/          # ProjectAuditPage, LabelsPage
```

**6b — AppLayout table:** In the `Project settings` row of the AppLayout slots table, append `, Labels (/p/:key/settings/labels)` to the description.

- [ ] **Step 7: Verify build**

```bash
python -m mkdocs build --strict 2>&1 | tail -20
```

Expected: `INFO - Documentation built successfully.` with zero warnings.

- [ ] **Step 8: Commit**

```bash
git add mkdocs/developer-guide/backend/labels.md \
        mkdocs/developer-guide/backend/issues.md \
        mkdocs/developer-guide/frontend/components.md \
        mkdocs/developer-guide/frontend/hooks.md \
        mkdocs/developer-guide/frontend/pages.md \
        mkdocs.yml
git commit -m "docs(wiki): add labels module page and update issues, frontend pages"
```

---

## Self-Review Checklist

**Spec coverage:**
- [x] V23 migration: `labels` + `issue_labels` — Task 1
- [x] GET/POST/PUT/DELETE `/projects/{key}/labels` — Task 2
- [x] `IssueResponse.labels` — Task 3
- [x] `UpdateIssueRequest.labelIds` — Task 3
- [x] `IssueController.list(?labelId)` — Task 3
- [x] `Label` type + `Issue.labels` — Task 4
- [x] `LabelChip` + `LabelSelector` (with on-the-fly create) — Task 5
- [x] `LabelSelector` in IssueDetailPage sidebar — Task 5
- [x] Settings page `/p/:key/settings/labels` with CRUD — Task 6
- [x] Nav link in AppLayout — Task 6
- [x] Issue list label filter dropdown — Task 7
- [x] Click chip → navigate to filtered list — Task 7
- [x] `labels.md` developer-guide page — Task 8
- [x] `issues.md` updated (entity, schema, endpoints, pitfalls, tests) — Task 8
- [x] `mkdocs.yml` nav entry — Task 8
- [x] `components.md`, `hooks.md`, `pages.md` updated — Task 8
- [x] `mkdocs build --strict` passes — Task 8
