# Phase 2: Agile Boards — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Sprint Board (Kanban-View des aktiven Sprints), Backlog mit Sprint Planning, Burndown- und Velocity-Charts, Real-time WebSocket-Updates.

**Architecture:** `sprints`-Modul als vollständiges hexagonales Modul. `boards` und `reports` sind dünne Aggregations-Schichten ohne eigene Domain-Entitäten. WebSocket-Events werden via `BoardEventPublisher` aus bestehenden Domain Events abgeleitet (kein SockJS — native WebSocket über `/ws-stomp`-Endpunkt).

**Tech Stack:** Kotlin, Spring Boot, Spring WebSocket + STOMP (native WS) | React, @dnd-kit/core, @dnd-kit/utilities, @stomp/stompjs, recharts, React Query, TypeScript

---

## File Structure

```
backend/src/main/kotlin/com/taskowolf/
  sprints/
    domain/Sprint.kt
    domain/SprintStatus.kt
    domain/events/SprintStartedEvent.kt
    domain/events/SprintCompletedEvent.kt
    application/SprintService.kt          # incl. SprintCompleteResult
    infrastructure/SprintRepository.kt
    api/SprintController.kt
    api/dto/CreateSprintRequest.kt
    api/dto/UpdateSprintRequest.kt
    api/dto/SprintResponse.kt
    api/dto/SprintCompleteResponse.kt
  boards/
    application/BoardService.kt
    api/BoardController.kt
    api/dto/BoardResponse.kt              # incl. BoardSprintSummary, BoardColumnResponse, StatusSummary
    api/dto/BoardMoveRequest.kt
    api/dto/BacklogResponse.kt            # incl. BacklogSprintEntry
    events/BoardEventPublisher.kt
  reports/
    application/ReportsService.kt
    api/ReportsController.kt
    api/dto/BurndownResponse.kt           # incl. BurndownDay
    api/dto/VelocityResponse.kt           # incl. VelocityEntry
  core/
    infrastructure/WebSocketConfig.kt     # NEW

backend/src/main/resources/db/migration/
  V5__create_sprints.sql

backend/src/test/kotlin/com/taskowolf/
  sprints/SprintServiceTest.kt
  boards/BoardServiceTest.kt
  sprints/SprintLifecycleIntegrationTest.kt

-- Modified:
backend/src/main/kotlin/com/taskowolf/issues/domain/Issue.kt      # add sprint ManyToOne
backend/src/main/kotlin/com/taskowolf/issues/infrastructure/IssueRepository.kt  # add sprint queries

frontend/src/
  api/sprints.ts
  api/board.ts
  api/reports.ts
  hooks/useSprints.ts
  hooks/useBoard.ts
  hooks/useReports.ts
  hooks/useProjectSocket.ts
  pages/board/BoardPage.tsx
  pages/backlog/BacklogPage.tsx
  pages/reports/ReportsPage.tsx
  components/board/BoardColumn.tsx
  components/board/DraggableCard.tsx
  components/sprint/SprintHeader.tsx
  components/sprint/CompleteSprintDialog.tsx
  components/sprint/CreateSprintForm.tsx

-- Modified:
frontend/src/types/index.ts
frontend/src/app/router.tsx
frontend/src/layouts/AppLayout.tsx
frontend/vite.config.ts             # add /ws-stomp proxy
```

---

## Task 1: DB Migration V5

**Files:**
- Create: `backend/src/main/resources/db/migration/V5__create_sprints.sql`

- [ ] **Step 1: Create `V5__create_sprints.sql`**

```sql
CREATE TABLE sprints (
    id               UUID         NOT NULL PRIMARY KEY,
    name             VARCHAR(255) NOT NULL,
    goal             TEXT,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PLANNED',
    start_date       DATE,
    end_date         DATE,
    planned_points   INT,
    completed_points INT,
    project_id       UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    created_at       TIMESTAMP    NOT NULL,
    updated_at       TIMESTAMP    NOT NULL
);

CREATE INDEX idx_sprints_project ON sprints(project_id);

ALTER TABLE issues ADD CONSTRAINT fk_issues_sprint
    FOREIGN KEY (sprint_id) REFERENCES sprints(id) ON DELETE SET NULL;
```

- [ ] **Step 2: Verify migration runs**

```bash
cd backend && SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun &
sleep 15
curl -s http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"v5@test.com","displayName":"Test","password":"password123"}' | grep accessToken
kill %1
```
Expected: `accessToken` in response — app started cleanly with V5 applied.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/db/migration/V5__create_sprints.sql
git commit -m "feat: add V5 migration — sprints table, sprint_id FK on issues"
```

---

## Task 2: Sprint Domain

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/sprints/domain/Sprint.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/sprints/domain/SprintStatus.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/sprints/domain/events/SprintStartedEvent.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/sprints/domain/events/SprintCompletedEvent.kt`

- [ ] **Step 1: Create `SprintStatus.kt`**

```kotlin
package com.taskowolf.sprints.domain

enum class SprintStatus { PLANNED, ACTIVE, CLOSED }
```

- [ ] **Step 2: Create `Sprint.kt`**

```kotlin
package com.taskowolf.sprints.domain

import com.taskowolf.core.domain.AuditableEntity
import com.taskowolf.projects.domain.Project
import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(name = "sprints")
class Sprint(
    @Column(nullable = false)
    var name: String,

    @Column(columnDefinition = "TEXT")
    var goal: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: SprintStatus = SprintStatus.PLANNED,

    @Column
    var startDate: LocalDate? = null,

    @Column
    var endDate: LocalDate? = null,

    @Column
    var plannedPoints: Int? = null,

    @Column
    var completedPoints: Int? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    val project: Project
) : AuditableEntity()
```

- [ ] **Step 3: Create `SprintStartedEvent.kt`**

```kotlin
package com.taskowolf.sprints.domain.events

import com.taskowolf.sprints.domain.Sprint

data class SprintStartedEvent(val sprint: Sprint)
```

- [ ] **Step 4: Create `SprintCompletedEvent.kt`**

```kotlin
package com.taskowolf.sprints.domain.events

import com.taskowolf.sprints.domain.Sprint

data class SprintCompletedEvent(val sprint: Sprint, val movedToBacklogCount: Int)
```

- [ ] **Step 5: Compile check**

```bash
cd backend && ./gradlew compileKotlin 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/sprints/domain/
git commit -m "feat: add sprints domain — Sprint entity, SprintStatus, domain events"
```

---

## Task 3: Update Issue Entity + IssueRepository

**Files:**
- Modify: `backend/src/main/kotlin/com/taskowolf/issues/domain/Issue.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/issues/infrastructure/IssueRepository.kt`

- [ ] **Step 1: Add `sprint` relationship to `Issue.kt`**

Add after the `parent` field (line 53):

```kotlin
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sprint_id")
    var sprint: com.taskowolf.sprints.domain.Sprint? = null,
```

Full `Issue.kt` after edit:
```kotlin
package com.taskowolf.issues.domain

import com.taskowolf.auth.domain.User
import com.taskowolf.core.domain.AuditableEntity
import com.taskowolf.projects.domain.Project
import com.taskowolf.workflows.domain.WorkflowStatus
import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(name = "issues")
class Issue(
    @Column(name = "\"key\"", nullable = false, unique = true)
    val key: String,

    @Column(nullable = false)
    val keyNumber: Int,

    @Column(nullable = false, length = 500)
    var title: String,

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: IssueType = IssueType.TASK,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var priority: IssuePriority = IssuePriority.MEDIUM,

    var storyPoints: Int? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_id", nullable = false)
    var status: WorkflowStatus,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    val project: Project,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    var assignee: User? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    val reporter: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sprint_id")
    var sprint: com.taskowolf.sprints.domain.Sprint? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    var parent: Issue? = null,

    var dueDate: LocalDate? = null
) : AuditableEntity()
```

- [ ] **Step 2: Add sprint queries to `IssueRepository.kt`**

```kotlin
package com.taskowolf.issues.infrastructure

import com.taskowolf.issues.domain.Issue
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface IssueRepository : JpaRepository<Issue, UUID> {
    fun findByKey(key: String): Issue?
    fun findAllByProjectId(projectId: UUID, pageable: Pageable): Page<Issue>
    fun countByProjectId(projectId: UUID): Long

    @Query("SELECT COALESCE(MAX(i.keyNumber), 0) FROM Issue i WHERE i.project.id = :projectId")
    fun maxKeyNumberByProject(projectId: UUID): Int

    fun findBySprintId(sprintId: UUID): List<Issue>

    fun findByProjectIdAndSprintIsNull(projectId: UUID): List<Issue>

    @Query("SELECT COALESCE(SUM(COALESCE(i.storyPoints, 0)), 0) FROM Issue i WHERE i.sprint.id = :sprintId")
    fun sumStoryPointsBySprintId(sprintId: UUID): Int
}
```

- [ ] **Step 3: Compile check**

```bash
cd backend && ./gradlew compileKotlin 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/issues/
git commit -m "feat: add sprint relationship to Issue entity and IssueRepository sprint queries"
```

---

## Task 4: Sprint Repository

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/sprints/infrastructure/SprintRepository.kt`

- [ ] **Step 1: Create `SprintRepository.kt`**

```kotlin
package com.taskowolf.sprints.infrastructure

import com.taskowolf.sprints.domain.Sprint
import com.taskowolf.sprints.domain.SprintStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SprintRepository : JpaRepository<Sprint, UUID> {
    fun findByProjectId(projectId: UUID): List<Sprint>
    fun findByProjectIdAndStatus(projectId: UUID, status: SprintStatus): List<Sprint>
    fun existsByProjectIdAndStatus(projectId: UUID, status: SprintStatus): Boolean
}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/sprints/infrastructure/
git commit -m "feat: add SprintRepository"
```

---

## Task 5: SprintService — TDD

**Files:**
- Create: `backend/src/test/kotlin/com/taskowolf/sprints/SprintServiceTest.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/sprints/application/SprintService.kt`

- [ ] **Step 1: Write failing tests in `SprintServiceTest.kt`**

```kotlin
package com.taskowolf.sprints

import com.taskowolf.auth.domain.User
import com.taskowolf.core.application.DomainEventPublisher
import com.taskowolf.core.infrastructure.ConflictException
import com.taskowolf.issues.domain.Issue
import com.taskowolf.issues.domain.IssueType
import com.taskowolf.issues.domain.IssuePriority
import com.taskowolf.issues.infrastructure.IssueRepository
import com.taskowolf.projects.application.ProjectService
import com.taskowolf.projects.domain.Project
import com.taskowolf.sprints.api.dto.CreateSprintRequest
import com.taskowolf.sprints.application.SprintService
import com.taskowolf.sprints.domain.Sprint
import com.taskowolf.sprints.domain.SprintStatus
import com.taskowolf.sprints.infrastructure.SprintRepository
import com.taskowolf.workflows.domain.StatusCategory
import com.taskowolf.workflows.domain.Workflow
import com.taskowolf.workflows.domain.WorkflowStatus
import io.mockk.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Optional

class SprintServiceTest {

    private val sprintRepository = mockk<SprintRepository>()
    private val projectService = mockk<ProjectService>()
    private val issueRepository = mockk<IssueRepository>()
    private val eventPublisher = mockk<DomainEventPublisher>(relaxed = true)
    private val service = SprintService(sprintRepository, projectService, issueRepository, eventPublisher)

    private val owner = User(email = "owner@test.com", displayName = "Owner")
    private val workflow = mockk<Workflow>()
    private val project = Project(key = "WOLF", name = "TaskWolf", owner = owner, workflow = workflow)

    @Test
    fun `start throws ConflictException when active sprint already exists`() {
        val sprint = Sprint(name = "Sprint 1", project = project)
        every { projectService.requireMember("WOLF", owner.id) } returns project
        every { sprintRepository.findById(sprint.id) } returns Optional.of(sprint)
        every { sprintRepository.existsByProjectIdAndStatus(project.id, SprintStatus.ACTIVE) } returns true

        assertThrows<ConflictException> {
            service.start("WOLF", sprint.id, owner)
        }
    }

    @Test
    fun `start sets status to ACTIVE and snapshots planned points`() {
        val sprint = Sprint(name = "Sprint 1", project = project)
        every { projectService.requireMember("WOLF", owner.id) } returns project
        every { sprintRepository.findById(sprint.id) } returns Optional.of(sprint)
        every { sprintRepository.existsByProjectIdAndStatus(project.id, SprintStatus.ACTIVE) } returns false
        every { issueRepository.sumStoryPointsBySprintId(sprint.id) } returns 13
        every { sprintRepository.save(any()) } returnsArgument 0

        val result = service.start("WOLF", sprint.id, owner)

        assert(result.status == SprintStatus.ACTIVE)
        assert(result.plannedPoints == 13)
    }

    @Test
    fun `complete moves non-DONE issues to backlog and returns count`() {
        val doneStatus = WorkflowStatus("Done", StatusCategory.DONE, "#63dc78", 2, workflow)
        val todoStatus = WorkflowStatus("To Do", StatusCategory.TODO, "#6c8fef", 0, workflow)
        val sprint = Sprint(name = "Sprint 1", status = SprintStatus.ACTIVE, project = project)
        val doneIssue = Issue("WOLF-1", 1, "Done issue", status = doneStatus, project = project, reporter = owner)
        val openIssue = Issue("WOLF-2", 2, "Open issue", status = todoStatus, project = project, reporter = owner)
        openIssue.sprint = sprint

        every { projectService.requireMember("WOLF", owner.id) } returns project
        every { sprintRepository.findById(sprint.id) } returns Optional.of(sprint)
        every { issueRepository.findBySprintId(sprint.id) } returns listOf(doneIssue, openIssue)
        every { issueRepository.saveAll(any<List<Issue>>()) } returnsArgument 0
        every { sprintRepository.save(any()) } returnsArgument 0

        val result = service.complete("WOLF", sprint.id, owner)

        assert(result.sprint.status == SprintStatus.CLOSED)
        assert(result.movedToBacklogCount == 1)
        assert(openIssue.sprint == null)
    }

    @Test
    fun `create persists sprint with project`() {
        every { projectService.requireMember("WOLF", owner.id) } returns project
        every { sprintRepository.save(any()) } returnsArgument 0

        val result = service.create("WOLF", CreateSprintRequest("Sprint 1"), owner)

        assert(result.name == "Sprint 1")
        verify(exactly = 1) { sprintRepository.save(any()) }
    }
}
```

- [ ] **Step 2: Run to verify tests fail**

```bash
cd backend && ./gradlew test --tests "com.taskowolf.sprints.SprintServiceTest" 2>&1 | tail -5
```
Expected: compile error — `SprintService` not found.

- [ ] **Step 3: Create `SprintService.kt`**

```kotlin
package com.taskowolf.sprints.application

import com.taskowolf.auth.domain.User
import com.taskowolf.core.application.DomainEventPublisher
import com.taskowolf.core.infrastructure.ConflictException
import com.taskowolf.core.infrastructure.ForbiddenException
import com.taskowolf.core.infrastructure.NotFoundException
import com.taskowolf.issues.infrastructure.IssueRepository
import com.taskowolf.projects.application.ProjectService
import com.taskowolf.sprints.api.dto.CreateSprintRequest
import com.taskowolf.sprints.api.dto.UpdateSprintRequest
import com.taskowolf.sprints.domain.Sprint
import com.taskowolf.sprints.domain.SprintStatus
import com.taskowolf.sprints.domain.events.SprintCompletedEvent
import com.taskowolf.sprints.domain.events.SprintStartedEvent
import com.taskowolf.sprints.infrastructure.SprintRepository
import com.taskowolf.workflows.domain.StatusCategory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

data class SprintCompleteResult(val sprint: Sprint, val movedToBacklogCount: Int)

@Service
class SprintService(
    private val sprintRepository: SprintRepository,
    private val projectService: ProjectService,
    private val issueRepository: IssueRepository,
    private val eventPublisher: DomainEventPublisher
) {
    @Transactional
    fun create(projectKey: String, request: CreateSprintRequest, actor: User): Sprint {
        val project = projectService.requireMember(projectKey, actor.id)
        return sprintRepository.save(
            Sprint(name = request.name, goal = request.goal, startDate = request.startDate, endDate = request.endDate, project = project)
        )
    }

    @Transactional
    fun update(projectKey: String, sprintId: UUID, request: UpdateSprintRequest, actor: User): Sprint {
        val project = projectService.requireMember(projectKey, actor.id)
        val sprint = requireSprint(sprintId, project.id)
        request.name?.let { sprint.name = it }
        request.goal?.let { sprint.goal = it }
        request.startDate?.let { sprint.startDate = it }
        request.endDate?.let { sprint.endDate = it }
        return sprintRepository.save(sprint)
    }

    @Transactional
    fun start(projectKey: String, sprintId: UUID, actor: User): Sprint {
        val project = projectService.requireMember(projectKey, actor.id)
        val sprint = requireSprint(sprintId, project.id)
        if (sprint.status != SprintStatus.PLANNED) throw ConflictException("Sprint is not in PLANNED state")
        if (sprintRepository.existsByProjectIdAndStatus(project.id, SprintStatus.ACTIVE))
            throw ConflictException("Project already has an active sprint")
        sprint.status = SprintStatus.ACTIVE
        if (sprint.startDate == null) sprint.startDate = LocalDate.now()
        sprint.plannedPoints = issueRepository.sumStoryPointsBySprintId(sprint.id)
        val saved = sprintRepository.save(sprint)
        eventPublisher.publish(SprintStartedEvent(saved))
        return saved
    }

    @Transactional
    fun complete(projectKey: String, sprintId: UUID, actor: User): SprintCompleteResult {
        val project = projectService.requireMember(projectKey, actor.id)
        val sprint = requireSprint(sprintId, project.id)
        if (sprint.status != SprintStatus.ACTIVE) throw ConflictException("Sprint is not ACTIVE")
        val allIssues = issueRepository.findBySprintId(sprint.id)
        val openIssues = allIssues.filter { it.status.category != StatusCategory.DONE }
        openIssues.forEach { it.sprint = null }
        issueRepository.saveAll(openIssues)
        sprint.completedPoints = allIssues.filter { it.status.category == StatusCategory.DONE }.sumOf { it.storyPoints ?: 0 }
        sprint.status = SprintStatus.CLOSED
        val saved = sprintRepository.save(sprint)
        eventPublisher.publish(SprintCompletedEvent(saved, openIssues.size))
        return SprintCompleteResult(saved, openIssues.size)
    }

    fun listByProject(projectKey: String, userId: UUID): List<Sprint> {
        val project = projectService.requireMember(projectKey, userId)
        return sprintRepository.findByProjectId(project.id)
    }

    @Transactional
    fun assignIssue(projectKey: String, sprintId: UUID, issueId: UUID, actor: User) {
        val project = projectService.requireMember(projectKey, actor.id)
        val sprint = requireSprint(sprintId, project.id)
        val issue = issueRepository.findById(issueId)
            .filter { it.project.id == project.id }
            .orElseThrow { NotFoundException("Issue not found") }
        issue.sprint = sprint
        issueRepository.save(issue)
    }

    @Transactional
    fun unassignIssue(projectKey: String, sprintId: UUID, issueId: UUID, actor: User) {
        val project = projectService.requireMember(projectKey, actor.id)
        requireSprint(sprintId, project.id)
        val issue = issueRepository.findById(issueId)
            .filter { it.project.id == project.id }
            .orElseThrow { NotFoundException("Issue not found") }
        issue.sprint = null
        issueRepository.save(issue)
    }

    private fun requireSprint(sprintId: UUID, projectId: UUID): Sprint {
        val sprint = sprintRepository.findById(sprintId).orElseThrow { NotFoundException("Sprint not found") }
        if (sprint.project.id != projectId) throw ForbiddenException("Sprint does not belong to this project")
        return sprint
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd backend && ./gradlew test --tests "com.taskowolf.sprints.SprintServiceTest" 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`, 4 tests passed.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/sprints/application/ \
        backend/src/test/kotlin/com/taskowolf/sprints/SprintServiceTest.kt
git commit -m "feat: add SprintService with TDD — lifecycle, assign/unassign"
```

---

## Task 6: Sprint DTOs + Controller

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/sprints/api/dto/CreateSprintRequest.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/sprints/api/dto/UpdateSprintRequest.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/sprints/api/dto/SprintResponse.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/sprints/api/dto/SprintCompleteResponse.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/sprints/api/SprintController.kt`

- [ ] **Step 1: Create DTOs**

`CreateSprintRequest.kt`:
```kotlin
package com.taskowolf.sprints.api.dto

import jakarta.validation.constraints.NotBlank
import java.time.LocalDate

data class CreateSprintRequest(
    @field:NotBlank val name: String,
    val goal: String? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null
)
```

`UpdateSprintRequest.kt`:
```kotlin
package com.taskowolf.sprints.api.dto

import java.time.LocalDate

data class UpdateSprintRequest(
    val name: String? = null,
    val goal: String? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null
)
```

`SprintResponse.kt`:
```kotlin
package com.taskowolf.sprints.api.dto

import com.taskowolf.sprints.domain.Sprint
import java.time.LocalDate
import java.util.UUID

data class SprintResponse(
    val id: UUID,
    val name: String,
    val goal: String?,
    val status: String,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val plannedPoints: Int?,
    val completedPoints: Int?,
    val projectId: UUID
) {
    companion object {
        fun from(s: Sprint) = SprintResponse(
            s.id, s.name, s.goal, s.status.name,
            s.startDate, s.endDate, s.plannedPoints, s.completedPoints, s.project.id
        )
    }
}
```

`SprintCompleteResponse.kt`:
```kotlin
package com.taskowolf.sprints.api.dto

data class SprintCompleteResponse(val sprint: SprintResponse, val movedToBacklogCount: Int)
```

- [ ] **Step 2: Create `SprintController.kt`**

```kotlin
package com.taskowolf.sprints.api

import com.taskowolf.auth.domain.User
import com.taskowolf.sprints.api.dto.*
import com.taskowolf.sprints.application.SprintService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/projects/{key}/sprints")
class SprintController(private val sprintService: SprintService) {

    @GetMapping
    fun list(@PathVariable key: String, @AuthenticationPrincipal user: User) =
        sprintService.listByProject(key, user.id).map { SprintResponse.from(it) }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @PathVariable key: String,
        @Valid @RequestBody request: CreateSprintRequest,
        @AuthenticationPrincipal user: User
    ) = SprintResponse.from(sprintService.create(key, request, user))

    @PatchMapping("/{sprintId}")
    fun update(
        @PathVariable key: String,
        @PathVariable sprintId: UUID,
        @RequestBody request: UpdateSprintRequest,
        @AuthenticationPrincipal user: User
    ) = SprintResponse.from(sprintService.update(key, sprintId, request, user))

    @PostMapping("/{sprintId}/start")
    fun start(
        @PathVariable key: String,
        @PathVariable sprintId: UUID,
        @AuthenticationPrincipal user: User
    ) = SprintResponse.from(sprintService.start(key, sprintId, user))

    @PostMapping("/{sprintId}/complete")
    fun complete(
        @PathVariable key: String,
        @PathVariable sprintId: UUID,
        @AuthenticationPrincipal user: User
    ): SprintCompleteResponse {
        val result = sprintService.complete(key, sprintId, user)
        return SprintCompleteResponse(SprintResponse.from(result.sprint), result.movedToBacklogCount)
    }

    @PutMapping("/{sprintId}/issues/{issueId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun assignIssue(
        @PathVariable key: String,
        @PathVariable sprintId: UUID,
        @PathVariable issueId: UUID,
        @AuthenticationPrincipal user: User
    ) = sprintService.assignIssue(key, sprintId, issueId, user)

    @DeleteMapping("/{sprintId}/issues/{issueId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unassignIssue(
        @PathVariable key: String,
        @PathVariable sprintId: UUID,
        @PathVariable issueId: UUID,
        @AuthenticationPrincipal user: User
    ) = sprintService.unassignIssue(key, sprintId, issueId, user)
}
```

- [ ] **Step 3: Run all tests**

```bash
cd backend && ./gradlew test 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/sprints/api/
git commit -m "feat: add Sprint DTOs and SprintController"
```

---

## Task 7: BoardService — TDD

**Files:**
- Create: `backend/src/test/kotlin/com/taskowolf/boards/BoardServiceTest.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/boards/application/BoardService.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/boards/api/dto/BoardResponse.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/boards/api/dto/BoardMoveRequest.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/boards/api/dto/BacklogResponse.kt`

- [ ] **Step 1: Write failing `BoardServiceTest.kt`**

```kotlin
package com.taskowolf.boards

import com.taskowolf.auth.domain.User
import com.taskowolf.boards.application.BoardService
import com.taskowolf.issues.infrastructure.IssueRepository
import com.taskowolf.projects.application.ProjectService
import com.taskowolf.projects.domain.Project
import com.taskowolf.sprints.domain.Sprint
import com.taskowolf.sprints.domain.SprintStatus
import com.taskowolf.sprints.infrastructure.SprintRepository
import com.taskowolf.workflows.domain.StatusCategory
import com.taskowolf.workflows.domain.Workflow
import com.taskowolf.workflows.domain.WorkflowStatus
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

class BoardServiceTest {

    private val projectService = mockk<ProjectService>()
    private val sprintRepository = mockk<SprintRepository>()
    private val issueRepository = mockk<IssueRepository>()
    private val service = BoardService(projectService, sprintRepository, issueRepository)

    private val owner = User(email = "owner@test.com", displayName = "Owner")
    private val workflow = mockk<Workflow>()
    private val project = Project(key = "WOLF", name = "TaskWolf", owner = owner, workflow = workflow)
    private val status = WorkflowStatus("To Do", StatusCategory.TODO, "#6c8fef", 0, workflow)

    @Test
    fun `getBoard returns null when no active sprint`() {
        every { projectService.requireMember("WOLF", owner.id) } returns project
        every { sprintRepository.findByProjectIdAndStatus(project.id, SprintStatus.ACTIVE) } returns emptyList()

        val result = service.getBoard("WOLF", owner.id)

        assert(result == null)
    }

    @Test
    fun `getBoard groups issues into columns by status`() {
        val sprint = Sprint(name = "Sprint 1", status = SprintStatus.ACTIVE, project = project)
        val issue = com.taskowolf.issues.domain.Issue(
            "WOLF-1", 1, "Test", status = status, project = project, reporter = owner
        )
        every { projectService.requireMember("WOLF", owner.id) } returns project
        every { sprintRepository.findByProjectIdAndStatus(project.id, SprintStatus.ACTIVE) } returns listOf(sprint)
        every { workflow.statuses } returns mutableListOf(status)
        every { issueRepository.findBySprintId(sprint.id) } returns listOf(issue)

        val result = service.getBoard("WOLF", owner.id)!!

        assert(result.columns.size == 1)
        assert(result.columns[0].issues.size == 1)
        assert(result.columns[0].issues[0].key == "WOLF-1")
    }

    @Test
    fun `getBacklog returns planned sprints and unassigned issues`() {
        val sprint = Sprint(name = "Sprint 1", project = project)
        val issue = com.taskowolf.issues.domain.Issue(
            "WOLF-1", 1, "Backlog issue", status = status, project = project, reporter = owner
        )
        every { projectService.requireMember("WOLF", owner.id) } returns project
        every { sprintRepository.findByProjectIdAndStatus(project.id, SprintStatus.PLANNED) } returns listOf(sprint)
        every { issueRepository.findBySprintId(sprint.id) } returns emptyList()
        every { issueRepository.findByProjectIdAndSprintIsNull(project.id) } returns listOf(issue)

        val result = service.getBacklog("WOLF", owner.id)

        assert(result.sprints.size == 1)
        assert(result.backlogIssues.size == 1)
    }
}
```

- [ ] **Step 2: Run to verify failure**

```bash
cd backend && ./gradlew test --tests "com.taskowolf.boards.BoardServiceTest" 2>&1 | tail -5
```
Expected: compile error — `BoardService` not found.

- [ ] **Step 3: Create Board DTOs**

`BoardResponse.kt`:
```kotlin
package com.taskowolf.boards.api.dto

import com.taskowolf.issues.api.dto.IssueResponse
import java.time.LocalDate
import java.util.UUID

data class StatusSummary(val id: UUID, val name: String, val category: String, val color: String)

data class BoardSprintSummary(
    val id: UUID, val name: String, val goal: String?,
    val startDate: LocalDate?, val endDate: LocalDate?,
    val daysRemaining: Long?, val totalPoints: Int?, val completedPoints: Int
)

data class BoardColumnResponse(val status: StatusSummary, val issues: List<IssueResponse>)

data class BoardResponse(val sprint: BoardSprintSummary, val columns: List<BoardColumnResponse>)
```

`BoardMoveRequest.kt`:
```kotlin
package com.taskowolf.boards.api.dto

import java.util.UUID

data class BoardMoveRequest(val issueId: UUID, val newStatusId: UUID)
```

`BacklogResponse.kt`:
```kotlin
package com.taskowolf.boards.api.dto

import com.taskowolf.issues.api.dto.IssueResponse
import com.taskowolf.sprints.api.dto.SprintResponse

data class BacklogSprintEntry(val sprint: SprintResponse, val issues: List<IssueResponse>, val totalPoints: Int)

data class BacklogResponse(val sprints: List<BacklogSprintEntry>, val backlogIssues: List<IssueResponse>)
```

- [ ] **Step 4: Create `BoardService.kt`**

```kotlin
package com.taskowolf.boards.application

import com.taskowolf.boards.api.dto.*
import com.taskowolf.issues.api.dto.IssueResponse
import com.taskowolf.issues.infrastructure.IssueRepository
import com.taskowolf.core.infrastructure.NotFoundException
import com.taskowolf.projects.application.ProjectService
import com.taskowolf.sprints.api.dto.SprintResponse
import com.taskowolf.sprints.domain.SprintStatus
import com.taskowolf.sprints.infrastructure.SprintRepository
import com.taskowolf.workflows.domain.StatusCategory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class BoardService(
    private val projectService: ProjectService,
    private val sprintRepository: SprintRepository,
    private val issueRepository: IssueRepository
) {
    fun getBoard(projectKey: String, userId: UUID): BoardResponse? {
        val project = projectService.requireMember(projectKey, userId)
        val sprint = sprintRepository.findByProjectIdAndStatus(project.id, SprintStatus.ACTIVE)
            .firstOrNull() ?: return null
        val workflow = project.workflow ?: throw NotFoundException("Project has no workflow")
        val issues = issueRepository.findBySprintId(sprint.id)
        val issuesByStatus = issues.groupBy { it.status.id }
        val columns = workflow.statuses.map { status ->
            BoardColumnResponse(
                status = StatusSummary(status.id, status.name, status.category.name, status.color),
                issues = (issuesByStatus[status.id] ?: emptyList()).map { IssueResponse.from(it) }
            )
        }
        val completedPoints = issues.filter { it.status.category == StatusCategory.DONE }
            .sumOf { it.storyPoints ?: 0 }
        val daysRemaining = sprint.endDate?.let { ChronoUnit.DAYS.between(LocalDate.now(), it).coerceAtLeast(0) }
        return BoardResponse(
            sprint = BoardSprintSummary(
                sprint.id, sprint.name, sprint.goal,
                sprint.startDate, sprint.endDate,
                daysRemaining, sprint.plannedPoints, completedPoints
            ),
            columns = columns
        )
    }

    fun getBacklog(projectKey: String, userId: UUID): BacklogResponse {
        val project = projectService.requireMember(projectKey, userId)
        val plannedSprints = sprintRepository.findByProjectIdAndStatus(project.id, SprintStatus.PLANNED)
        val sprintEntries = plannedSprints.map { sprint ->
            val issues = issueRepository.findBySprintId(sprint.id)
            val totalPoints = issues.sumOf { it.storyPoints ?: 0 }
            BacklogSprintEntry(
                sprint = SprintResponse.from(sprint),
                issues = issues.map { IssueResponse.from(it) },
                totalPoints = totalPoints
            )
        }
        val backlogIssues = issueRepository.findByProjectIdAndSprintIsNull(project.id)
            .map { IssueResponse.from(it) }
        return BacklogResponse(sprintEntries, backlogIssues)
    }
}
```

- [ ] **Step 5: Run tests to verify pass**

```bash
cd backend && ./gradlew test --tests "com.taskowolf.boards.BoardServiceTest" 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`, 3 tests passed.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/boards/ \
        backend/src/test/kotlin/com/taskowolf/boards/
git commit -m "feat: add BoardService with TDD — board aggregation, backlog"
```

---

## Task 8: BoardController

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/boards/api/BoardController.kt`

- [ ] **Step 1: Create `BoardController.kt`**

```kotlin
package com.taskowolf.boards.api

import com.taskowolf.auth.domain.User
import com.taskowolf.boards.api.dto.BoardMoveRequest
import com.taskowolf.boards.application.BoardService
import com.taskowolf.issues.api.dto.UpdateIssueRequest
import com.taskowolf.issues.application.IssueService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/projects/{key}")
class BoardController(
    private val boardService: BoardService,
    private val issueService: IssueService
) {
    @GetMapping("/board")
    fun getBoard(@PathVariable key: String, @AuthenticationPrincipal user: User): ResponseEntity<*> {
        val board = boardService.getBoard(key, user.id)
            ?: return ResponseEntity.noContent().build<Unit>()
        return ResponseEntity.ok(board)
    }

    @PatchMapping("/board/move")
    fun move(
        @PathVariable key: String,
        @RequestBody request: BoardMoveRequest,
        @AuthenticationPrincipal user: User
    ) {
        issueService.update(key, request.issueId, UpdateIssueRequest(statusId = request.newStatusId), user)
    }

    @GetMapping("/backlog")
    fun getBacklog(@PathVariable key: String, @AuthenticationPrincipal user: User) =
        boardService.getBacklog(key, user.id)
}
```

- [ ] **Step 2: Run all tests**

```bash
cd backend && ./gradlew test 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/boards/api/BoardController.kt
git commit -m "feat: add BoardController — board, move, backlog endpoints"
```

---

## Task 9: Reports

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/reports/application/ReportsService.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/reports/api/ReportsController.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/reports/api/dto/BurndownResponse.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/reports/api/dto/VelocityResponse.kt`

- [ ] **Step 1: Create Report DTOs**

`BurndownResponse.kt`:
```kotlin
package com.taskowolf.reports.api.dto

import java.time.LocalDate
import java.util.UUID

data class BurndownDay(val date: LocalDate, val idealPoints: Int, val remainingPoints: Int)

data class BurndownResponse(val sprintId: UUID, val days: List<BurndownDay>)
```

`VelocityResponse.kt`:
```kotlin
package com.taskowolf.reports.api.dto

import java.util.UUID

data class VelocityEntry(val sprintId: UUID, val sprintName: String, val plannedPoints: Int, val completedPoints: Int)

data class VelocityResponse(val entries: List<VelocityEntry>)
```

- [ ] **Step 2: Create `ReportsService.kt`**

```kotlin
package com.taskowolf.reports.application

import com.taskowolf.core.infrastructure.NotFoundException
import com.taskowolf.issues.infrastructure.IssueRepository
import com.taskowolf.projects.application.ProjectService
import com.taskowolf.reports.api.dto.*
import com.taskowolf.sprints.domain.SprintStatus
import com.taskowolf.sprints.infrastructure.SprintRepository
import com.taskowolf.workflows.domain.StatusCategory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class ReportsService(
    private val projectService: ProjectService,
    private val sprintRepository: SprintRepository,
    private val issueRepository: IssueRepository
) {
    fun getBurndown(projectKey: String, sprintId: UUID, userId: UUID): BurndownResponse {
        projectService.requireMember(projectKey, userId)
        val sprint = sprintRepository.findById(sprintId).orElseThrow { NotFoundException("Sprint not found") }
        val issues = issueRepository.findBySprintId(sprintId)
        val startDate = sprint.startDate ?: return BurndownResponse(sprintId, emptyList())
        val endDate = sprint.endDate ?: startDate.plusDays(13)
        val plannedPoints = sprint.plannedPoints ?: issues.sumOf { it.storyPoints ?: 0 }
        val sprintLengthDays = ChronoUnit.DAYS.between(startDate, endDate).toInt().coerceAtLeast(1)
        val today = LocalDate.now()
        val days = mutableListOf<BurndownDay>()
        var date = startDate
        while (!date.isAfter(endDate)) {
            val dayIndex = ChronoUnit.DAYS.between(startDate, date).toInt()
            val idealPoints = (plannedPoints * (sprintLengthDays - dayIndex).toDouble() / sprintLengthDays).toInt()
            val remainingPoints = if (date.isAfter(today)) {
                issues.filter { it.status.category != StatusCategory.DONE }.sumOf { it.storyPoints ?: 0 }
            } else {
                val endOfDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
                issues.filter { issue ->
                    !(issue.status.category == StatusCategory.DONE && issue.updatedAt.isBefore(endOfDay))
                }.sumOf { it.storyPoints ?: 0 }
            }
            days.add(BurndownDay(date, idealPoints, remainingPoints))
            date = date.plusDays(1)
        }
        return BurndownResponse(sprintId, days)
    }

    fun getVelocity(projectKey: String, userId: UUID): VelocityResponse {
        val project = projectService.requireMember(projectKey, userId)
        val entries = sprintRepository.findByProjectIdAndStatus(project.id, SprintStatus.CLOSED).map { sprint ->
            VelocityEntry(sprint.id, sprint.name, sprint.plannedPoints ?: 0, sprint.completedPoints ?: 0)
        }
        return VelocityResponse(entries)
    }
}
```

- [ ] **Step 3: Create `ReportsController.kt`**

```kotlin
package com.taskowolf.reports.api

import com.taskowolf.auth.domain.User
import com.taskowolf.reports.application.ReportsService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/projects/{key}/reports")
class ReportsController(private val reportsService: ReportsService) {

    @GetMapping("/burndown")
    fun burndown(
        @PathVariable key: String,
        @RequestParam sprintId: UUID,
        @AuthenticationPrincipal user: User
    ) = reportsService.getBurndown(key, sprintId, user.id)

    @GetMapping("/velocity")
    fun velocity(@PathVariable key: String, @AuthenticationPrincipal user: User) =
        reportsService.getVelocity(key, user.id)
}
```

- [ ] **Step 4: Run all tests**

```bash
cd backend && ./gradlew test 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/reports/
git commit -m "feat: add reports module — burndown and velocity endpoints"
```

---

## Task 10: WebSocket

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/core/infrastructure/WebSocketConfig.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/boards/events/BoardEventPublisher.kt`

- [ ] **Step 1: Create `WebSocketConfig.kt`**

```kotlin
package com.taskowolf.core.infrastructure

import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer

@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig : WebSocketMessageBrokerConfigurer {

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        registry.enableSimpleBroker("/topic", "/queue")
        registry.setApplicationDestinationPrefixes("/app")
    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS()
        registry.addEndpoint("/ws-stomp").setAllowedOriginPatterns("*")
    }
}
```

- [ ] **Step 2: Create `BoardEventPublisher.kt`**

```kotlin
package com.taskowolf.boards.events

import com.taskowolf.issues.domain.events.IssueStatusChangedEvent
import com.taskowolf.sprints.domain.events.SprintCompletedEvent
import com.taskowolf.sprints.domain.events.SprintStartedEvent
import org.springframework.context.event.EventListener
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component

@Component
class BoardEventPublisher(private val messagingTemplate: SimpMessagingTemplate) {

    @EventListener
    fun onIssueStatusChanged(event: IssueStatusChangedEvent) {
        messagingTemplate.convertAndSend(
            "/topic/projects/${event.issue.project.key}",
            mapOf("type" to "ISSUE_MOVED", "issueId" to event.issue.id, "newStatusId" to event.newStatus.id, "projectKey" to event.issue.project.key)
        )
    }

    @EventListener
    fun onSprintStarted(event: SprintStartedEvent) {
        messagingTemplate.convertAndSend(
            "/topic/projects/${event.sprint.project.key}",
            mapOf("type" to "SPRINT_UPDATED", "sprintId" to event.sprint.id, "projectKey" to event.sprint.project.key)
        )
    }

    @EventListener
    fun onSprintCompleted(event: SprintCompletedEvent) {
        messagingTemplate.convertAndSend(
            "/topic/projects/${event.sprint.project.key}",
            mapOf("type" to "SPRINT_UPDATED", "sprintId" to event.sprint.id, "projectKey" to event.sprint.project.key)
        )
    }
}
```

- [ ] **Step 3: Run all tests**

```bash
cd backend && ./gradlew test 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/core/infrastructure/WebSocketConfig.kt \
        backend/src/main/kotlin/com/taskowolf/boards/events/
git commit -m "feat: add WebSocket config and BoardEventPublisher for real-time updates"
```

---

## Task 11: Sprint Lifecycle Integration Test

**Files:**
- Create: `backend/src/test/kotlin/com/taskowolf/sprints/SprintLifecycleIntegrationTest.kt`

- [ ] **Step 1: Create `SprintLifecycleIntegrationTest.kt`**

```kotlin
package com.taskowolf.sprints

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class SprintLifecycleIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper

    private fun registerAndGetToken(email: String): String {
        val result = mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","displayName":"Dev","password":"password123"}""")
        ).andReturn()
        return objectMapper.readTree(result.response.contentAsString).get("accessToken").asText()
    }

    @Test
    fun `full sprint lifecycle — create, start, move issue, complete, check backlog`() {
        val token = registerAndGetToken("sprint-flow@test.com")

        // Create project
        mockMvc.perform(
            post("/api/v1/projects")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"key":"SPR1","name":"Sprint Test"}""")
        ).andExpect(status().isCreated)

        // Create two issues with story points
        val issue1Result = mockMvc.perform(
            post("/api/v1/projects/SPR1/issues")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"Issue 1","storyPoints":5}""")
        ).andExpect(status().isCreated).andReturn()
        val issue1Id = objectMapper.readTree(issue1Result.response.contentAsString).get("id").asText()

        val issue2Result = mockMvc.perform(
            post("/api/v1/projects/SPR1/issues")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"Issue 2","storyPoints":3}""")
        ).andExpect(status().isCreated).andReturn()
        val issue2Id = objectMapper.readTree(issue2Result.response.contentAsString).get("id").asText()

        // Create sprint
        val sprintResult = mockMvc.perform(
            post("/api/v1/projects/SPR1/sprints")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Sprint 1","goal":"Ship it"}""")
        ).andExpect(status().isCreated).andReturn()
        val sprintId = objectMapper.readTree(sprintResult.response.contentAsString).get("id").asText()

        // Assign issues to sprint
        mockMvc.perform(
            put("/api/v1/projects/SPR1/sprints/$sprintId/issues/$issue1Id")
                .header("Authorization", "Bearer $token")
        ).andExpect(status().isNoContent)

        mockMvc.perform(
            put("/api/v1/projects/SPR1/sprints/$sprintId/issues/$issue2Id")
                .header("Authorization", "Bearer $token")
        ).andExpect(status().isNoContent)

        // Start sprint — plannedPoints should be 8
        mockMvc.perform(
            post("/api/v1/projects/SPR1/sprints/$sprintId/start")
                .header("Authorization", "Bearer $token")
        ).andExpect(status().isOk)
         .andExpect(jsonPath("$.status").value("ACTIVE"))
         .andExpect(jsonPath("$.plannedPoints").value(8))

        // Board should show 2 issues
        mockMvc.perform(
            get("/api/v1/projects/SPR1/board")
                .header("Authorization", "Bearer $token")
        ).andExpect(status().isOk)
         .andExpect(jsonPath("$.sprint.name").value("Sprint 1"))

        // Get workflow statuses to find DONE status
        val workflowResult = mockMvc.perform(
            get("/api/v1/projects/SPR1/workflows")
                .header("Authorization", "Bearer $token")
        ).andReturn()
        val statuses = objectMapper.readTree(workflowResult.response.contentAsString)[0]["statuses"]
        val doneStatusId = statuses.first { it["category"].asText() == "DONE" }.get("id").asText()

        // Move issue1 to DONE
        mockMvc.perform(
            patch("/api/v1/projects/SPR1/board/move")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"issueId":"$issue1Id","newStatusId":"$doneStatusId"}""")
        ).andExpect(status().isOk)

        // Complete sprint — issue2 should go back to backlog
        mockMvc.perform(
            post("/api/v1/projects/SPR1/sprints/$sprintId/complete")
                .header("Authorization", "Bearer $token")
        ).andExpect(status().isOk)
         .andExpect(jsonPath("$.sprint.status").value("CLOSED"))
         .andExpect(jsonPath("$.movedToBacklogCount").value(1))

        // Backlog should contain issue2
        mockMvc.perform(
            get("/api/v1/projects/SPR1/backlog")
                .header("Authorization", "Bearer $token")
        ).andExpect(status().isOk)
         .andExpect(jsonPath("$.backlogIssues.length()").value(1))

        // Velocity should show one entry
        mockMvc.perform(
            get("/api/v1/projects/SPR1/reports/velocity")
                .header("Authorization", "Bearer $token")
        ).andExpect(status().isOk)
         .andExpect(jsonPath("$.entries.length()").value(1))
         .andExpect(jsonPath("$.entries[0].completedPoints").value(5))
    }
}
```

- [ ] **Step 2: Run the integration test**

```bash
cd backend && ./gradlew test --tests "com.taskowolf.sprints.SprintLifecycleIntegrationTest" 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`, 1 test passed.

- [ ] **Step 3: Run full test suite**

```bash
cd backend && ./gradlew test 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`, all tests green.

- [ ] **Step 4: Commit**

```bash
git add backend/src/test/kotlin/com/taskowolf/sprints/SprintLifecycleIntegrationTest.kt
git commit -m "test: add SprintLifecycleIntegrationTest — full flow E2E"
```

---

## Task 12: Frontend — Dependencies + Types + API Modules

**Files:**
- Modify: `frontend/package.json`
- Modify: `frontend/vite.config.ts`
- Modify: `frontend/src/types/index.ts`
- Create: `frontend/src/api/sprints.ts`
- Create: `frontend/src/api/board.ts`
- Create: `frontend/src/api/reports.ts`

- [ ] **Step 1: Add dependencies to `frontend/package.json`**

Add to `dependencies`:
```json
"@dnd-kit/core": "^6.1.0",
"@dnd-kit/utilities": "^3.2.2",
"@stomp/stompjs": "^7.0.0",
"recharts": "^2.12.0"
```

- [ ] **Step 2: Update `frontend/vite.config.ts`** — add `/ws-stomp` proxy

```typescript
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'path'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') }
  },
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      '/ws': { target: 'ws://localhost:8080', ws: true },
      '/ws-stomp': { target: 'ws://localhost:8080', ws: true }
    }
  }
})
```

- [ ] **Step 3: Install dependencies**

```bash
cd frontend && npm install
```
Expected: no errors.

- [ ] **Step 4: Append Sprint + Board + Report types to `frontend/src/types/index.ts`**

Add to the end of the existing file:
```typescript
export interface Sprint {
  id: string
  name: string
  goal: string | null
  status: 'PLANNED' | 'ACTIVE' | 'CLOSED'
  startDate: string | null
  endDate: string | null
  plannedPoints: number | null
  completedPoints: number | null
  projectId: string
}

export interface BoardSprintSummary {
  id: string
  name: string
  goal: string | null
  startDate: string | null
  endDate: string | null
  daysRemaining: number | null
  totalPoints: number | null
  completedPoints: number
}

export interface BoardColumn {
  status: { id: string; name: string; category: string; color: string }
  issues: Issue[]
}

export interface BoardResponse {
  sprint: BoardSprintSummary
  columns: BoardColumn[]
}

export interface BacklogSprintEntry {
  sprint: Sprint
  issues: Issue[]
  totalPoints: number
}

export interface BacklogResponse {
  sprints: BacklogSprintEntry[]
  backlogIssues: Issue[]
}

export interface BurndownDay {
  date: string
  idealPoints: number
  remainingPoints: number
}

export interface BurndownResponse {
  sprintId: string
  days: BurndownDay[]
}

export interface VelocityEntry {
  sprintId: string
  sprintName: string
  plannedPoints: number
  completedPoints: number
}

export interface VelocityResponse {
  entries: VelocityEntry[]
}
```

- [ ] **Step 5: Create `frontend/src/api/sprints.ts`**

```typescript
import { apiClient } from './client'
import type { Sprint } from '@/types'

export const sprintsApi = {
  list: (projectKey: string) =>
    apiClient.get<Sprint[]>(`/projects/${projectKey}/sprints`),
  create: (projectKey: string, data: { name: string; goal?: string; startDate?: string; endDate?: string }) =>
    apiClient.post<Sprint>(`/projects/${projectKey}/sprints`, data),
  update: (projectKey: string, sprintId: string, data: Partial<{ name: string; goal: string; startDate: string; endDate: string }>) =>
    apiClient.patch<Sprint>(`/projects/${projectKey}/sprints/${sprintId}`, data),
  start: (projectKey: string, sprintId: string) =>
    apiClient.post<Sprint>(`/projects/${projectKey}/sprints/${sprintId}/start`),
  complete: (projectKey: string, sprintId: string) =>
    apiClient.post<{ sprint: Sprint; movedToBacklogCount: number }>(`/projects/${projectKey}/sprints/${sprintId}/complete`),
  assignIssue: (projectKey: string, sprintId: string, issueId: string) =>
    apiClient.put(`/projects/${projectKey}/sprints/${sprintId}/issues/${issueId}`),
  unassignIssue: (projectKey: string, sprintId: string, issueId: string) =>
    apiClient.delete(`/projects/${projectKey}/sprints/${sprintId}/issues/${issueId}`),
}
```

- [ ] **Step 6: Create `frontend/src/api/board.ts`**

```typescript
import { apiClient } from './client'
import type { BoardResponse, BacklogResponse } from '@/types'

export const boardApi = {
  getBoard: (projectKey: string) =>
    apiClient.get<BoardResponse>(`/projects/${projectKey}/board`),
  move: (projectKey: string, issueId: string, newStatusId: string) =>
    apiClient.patch(`/projects/${projectKey}/board/move`, { issueId, newStatusId }),
  getBacklog: (projectKey: string) =>
    apiClient.get<BacklogResponse>(`/projects/${projectKey}/backlog`),
}
```

- [ ] **Step 7: Create `frontend/src/api/reports.ts`**

```typescript
import { apiClient } from './client'
import type { BurndownResponse, VelocityResponse } from '@/types'

export const reportsApi = {
  burndown: (projectKey: string, sprintId: string) =>
    apiClient.get<BurndownResponse>(`/projects/${projectKey}/reports/burndown`, { params: { sprintId } }),
  velocity: (projectKey: string) =>
    apiClient.get<VelocityResponse>(`/projects/${projectKey}/reports/velocity`),
}
```

- [ ] **Step 8: Verify TypeScript compiles**

```bash
cd frontend && npm run build 2>&1 | tail -5
```
Expected: `✓ built in` — no TypeScript errors.

- [ ] **Step 9: Commit**

```bash
git add frontend/package.json frontend/package-lock.json frontend/vite.config.ts \
        frontend/src/types/index.ts frontend/src/api/
git commit -m "feat: add frontend deps (dnd-kit, stomp, recharts), types, API modules for phase 2"
```

---

## Task 13: Frontend Hooks

**Files:**
- Create: `frontend/src/hooks/useSprints.ts`
- Create: `frontend/src/hooks/useBoard.ts`
- Create: `frontend/src/hooks/useReports.ts`

- [ ] **Step 1: Create `frontend/src/hooks/useSprints.ts`**

```typescript
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { sprintsApi } from '@/api/sprints'

export function useSprints(projectKey: string) {
  return useQuery({
    queryKey: ['sprints', projectKey],
    queryFn: () => sprintsApi.list(projectKey).then(r => r.data),
  })
}

export function useCreateSprint(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: { name: string; goal?: string; startDate?: string; endDate?: string }) =>
      sprintsApi.create(projectKey, data).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['sprints', projectKey] }),
  })
}

export function useStartSprint(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (sprintId: string) => sprintsApi.start(projectKey, sprintId).then(r => r.data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['sprints', projectKey] })
      qc.invalidateQueries({ queryKey: ['board', projectKey] })
      qc.invalidateQueries({ queryKey: ['backlog', projectKey] })
    },
  })
}

export function useCompleteSprint(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (sprintId: string) => sprintsApi.complete(projectKey, sprintId).then(r => r.data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['sprints', projectKey] })
      qc.invalidateQueries({ queryKey: ['board', projectKey] })
      qc.invalidateQueries({ queryKey: ['backlog', projectKey] })
    },
  })
}

export function useAssignIssue(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ sprintId, issueId }: { sprintId: string; issueId: string }) =>
      sprintsApi.assignIssue(projectKey, sprintId, issueId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['backlog', projectKey] }),
  })
}

export function useUnassignIssue(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ sprintId, issueId }: { sprintId: string; issueId: string }) =>
      sprintsApi.unassignIssue(projectKey, sprintId, issueId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['backlog', projectKey] }),
  })
}
```

- [ ] **Step 2: Create `frontend/src/hooks/useBoard.ts`**

```typescript
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { boardApi } from '@/api/board'

export function useBoard(projectKey: string) {
  return useQuery({
    queryKey: ['board', projectKey],
    queryFn: () => boardApi.getBoard(projectKey).then(r => r.status === 204 ? null : r.data),
  })
}

export function useBacklog(projectKey: string) {
  return useQuery({
    queryKey: ['backlog', projectKey],
    queryFn: () => boardApi.getBacklog(projectKey).then(r => r.data),
  })
}

export function useMoveIssue(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ issueId, newStatusId }: { issueId: string; newStatusId: string }) =>
      boardApi.move(projectKey, issueId, newStatusId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['board', projectKey] }),
  })
}
```

- [ ] **Step 3: Create `frontend/src/hooks/useReports.ts`**

```typescript
import { useQuery } from '@tanstack/react-query'
import { reportsApi } from '@/api/reports'

export function useBurndown(projectKey: string, sprintId: string | null) {
  return useQuery({
    queryKey: ['burndown', projectKey, sprintId],
    queryFn: () => reportsApi.burndown(projectKey, sprintId!).then(r => r.data),
    enabled: !!sprintId,
  })
}

export function useVelocity(projectKey: string) {
  return useQuery({
    queryKey: ['velocity', projectKey],
    queryFn: () => reportsApi.velocity(projectKey).then(r => r.data),
  })
}
```

- [ ] **Step 4: Verify build**

```bash
cd frontend && npm run build 2>&1 | tail -5
```
Expected: `✓ built in` — no errors.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/hooks/useSprints.ts frontend/src/hooks/useBoard.ts \
        frontend/src/hooks/useReports.ts
git commit -m "feat: add useSprints, useBoard, useReports hooks"
```

---

## Task 14: Frontend Board Page

**Files:**
- Create: `frontend/src/components/board/DraggableCard.tsx`
- Create: `frontend/src/components/board/BoardColumn.tsx`
- Create: `frontend/src/components/sprint/SprintHeader.tsx`
- Create: `frontend/src/components/sprint/CompleteSprintDialog.tsx`
- Create: `frontend/src/pages/board/BoardPage.tsx`

- [ ] **Step 1: Create `frontend/src/components/board/DraggableCard.tsx`**

```tsx
import { useDraggable } from '@dnd-kit/core'
import { CSS } from '@dnd-kit/utilities'
import { cn } from '@/lib/utils'
import type { Issue } from '@/types'

const priorityColor: Record<string, string> = {
  CRITICAL: 'text-red-400',
  HIGH: 'text-orange-400',
  MEDIUM: 'text-yellow-400',
  LOW: 'text-green-400',
}

interface Props { issue: Issue }

export function DraggableCard({ issue }: Props) {
  const { attributes, listeners, setNodeRef, transform, isDragging } = useDraggable({ id: issue.id })

  return (
    <div
      ref={setNodeRef}
      style={{ transform: CSS.Translate.toString(transform) }}
      {...attributes}
      {...listeners}
      className={cn(
        'bg-gray-900 border border-gray-800 rounded-lg p-3 cursor-grab active:cursor-grabbing select-none',
        isDragging && 'opacity-50 border-blue-500 z-50'
      )}
    >
      <div className="text-xs text-gray-500 font-mono mb-1">{issue.key}</div>
      <div className="text-sm text-white mb-2 line-clamp-2">{issue.title}</div>
      <div className="flex items-center gap-2">
        <span className={cn('text-xs font-medium', priorityColor[issue.priority] ?? 'text-gray-400')}>
          {issue.priority}
        </span>
        {issue.storyPoints != null && (
          <span className="ml-auto text-xs bg-gray-800 text-gray-400 px-1.5 py-0.5 rounded font-mono">
            {issue.storyPoints}
          </span>
        )}
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Create `frontend/src/components/board/BoardColumn.tsx`**

```tsx
import { useDroppable } from '@dnd-kit/core'
import { cn } from '@/lib/utils'
import { DraggableCard } from './DraggableCard'
import type { BoardColumn as BoardColumnType } from '@/types'

interface Props { column: BoardColumnType }

export function BoardColumn({ column }: Props) {
  const { setNodeRef, isOver } = useDroppable({ id: column.status.id })

  return (
    <div
      ref={setNodeRef}
      className={cn(
        'flex flex-col min-w-56 w-64 rounded-lg p-3 transition-colors',
        isOver ? 'bg-gray-800' : 'bg-gray-900/50'
      )}
    >
      <div className="flex items-center gap-2 mb-3">
        <div className="w-2.5 h-2.5 rounded-full shrink-0" style={{ backgroundColor: column.status.color }} />
        <span className="text-xs font-semibold text-gray-400 uppercase tracking-wide">{column.status.name}</span>
        <span className="ml-auto text-xs text-gray-600 bg-gray-800 px-1.5 py-0.5 rounded">
          {column.issues.length}
        </span>
      </div>
      <div className="flex flex-col gap-2 min-h-16">
        {column.issues.map(issue => (
          <DraggableCard key={issue.id} issue={issue} />
        ))}
      </div>
    </div>
  )
}
```

- [ ] **Step 3: Create `frontend/src/components/sprint/SprintHeader.tsx`**

```tsx
import type { BoardSprintSummary } from '@/types'

interface Props {
  sprint: BoardSprintSummary
  onComplete: () => void
}

export function SprintHeader({ sprint, onComplete }: Props) {
  const pct = sprint.totalPoints && sprint.totalPoints > 0
    ? Math.round((sprint.completedPoints / sprint.totalPoints) * 100)
    : 0

  return (
    <div className="mb-6">
      <div className="flex items-start justify-between mb-2">
        <div>
          <h1 className="text-xl font-bold text-white">{sprint.name}</h1>
          {sprint.goal && <p className="text-sm text-gray-400 mt-0.5">{sprint.goal}</p>}
        </div>
        <button
          onClick={onComplete}
          className="text-sm bg-gray-800 hover:bg-gray-700 text-gray-300 px-3 py-1.5 rounded border border-gray-700"
        >
          Complete Sprint
        </button>
      </div>
      <div className="flex items-center gap-4 text-sm text-gray-500">
        {sprint.daysRemaining != null && (
          <span>{sprint.daysRemaining} day{sprint.daysRemaining !== 1 ? 's' : ''} remaining</span>
        )}
        {sprint.totalPoints != null && (
          <span>{sprint.completedPoints} / {sprint.totalPoints} pts</span>
        )}
      </div>
      {sprint.totalPoints != null && sprint.totalPoints > 0 && (
        <div className="mt-2 h-1.5 bg-gray-800 rounded-full overflow-hidden">
          <div className="h-full bg-blue-500 transition-all" style={{ width: `${pct}%` }} />
        </div>
      )}
    </div>
  )
}
```

- [ ] **Step 4: Create `frontend/src/components/sprint/CompleteSprintDialog.tsx`**

```tsx
interface Props {
  sprintName: string
  openIssueCount: number
  onConfirm: () => void
  onCancel: () => void
  loading: boolean
}

export function CompleteSprintDialog({ sprintName, openIssueCount, onConfirm, onCancel, loading }: Props) {
  return (
    <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50">
      <div className="bg-gray-900 border border-gray-800 rounded-xl p-6 max-w-md w-full mx-4">
        <h2 className="text-lg font-bold text-white mb-2">Complete "{sprintName}"?</h2>
        {openIssueCount > 0 ? (
          <p className="text-sm text-gray-400 mb-6">
            <span className="text-yellow-400 font-medium">{openIssueCount} issue{openIssueCount !== 1 ? 's' : ''}</span>{' '}
            are not done and will be moved back to the backlog.
          </p>
        ) : (
          <p className="text-sm text-gray-400 mb-6">All issues are done. Great sprint!</p>
        )}
        <div className="flex gap-3 justify-end">
          <button onClick={onCancel} className="px-4 py-2 text-sm text-gray-400 hover:text-white">
            Cancel
          </button>
          <button
            onClick={onConfirm}
            disabled={loading}
            className="px-4 py-2 text-sm bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white rounded"
          >
            {loading ? 'Completing...' : 'Complete Sprint'}
          </button>
        </div>
      </div>
    </div>
  )
}
```

- [ ] **Step 5: Create `frontend/src/pages/board/BoardPage.tsx`**

```tsx
import { useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { DndContext, DragEndEvent, PointerSensor, useSensor, useSensors } from '@dnd-kit/core'
import { useBoard, useMoveIssue } from '@/hooks/useBoard'
import { useCompleteSprint } from '@/hooks/useSprints'
import { BoardColumn } from '@/components/board/BoardColumn'
import { SprintHeader } from '@/components/sprint/SprintHeader'
import { CompleteSprintDialog } from '@/components/sprint/CompleteSprintDialog'

export function BoardPage() {
  const { key } = useParams<{ key: string }>()
  const { data: board, isLoading } = useBoard(key!)
  const moveIssue = useMoveIssue(key!)
  const completeSprint = useCompleteSprint(key!)
  const [showComplete, setShowComplete] = useState(false)
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 5 } }))

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event
    if (!over) return
    const issueId = active.id as string
    const newStatusId = over.id as string
    const currentStatusId = board?.columns.find(c => c.issues.some(i => i.id === issueId))?.status.id
    if (currentStatusId !== newStatusId) {
      moveIssue.mutate({ issueId, newStatusId })
    }
  }

  const openIssueCount = board?.columns
    .filter(c => c.status.category !== 'DONE')
    .reduce((sum, c) => sum + c.issues.length, 0) ?? 0

  if (isLoading) return <div className="text-gray-400">Loading...</div>

  if (!board) {
    return (
      <div className="flex flex-col items-center justify-center py-24 text-center">
        <p className="text-gray-400 mb-4">No active sprint.</p>
        <Link to={`/p/${key}/backlog`} className="text-blue-400 hover:underline text-sm">
          Go to Backlog to start a sprint →
        </Link>
      </div>
    )
  }

  return (
    <div>
      <SprintHeader sprint={board.sprint} onComplete={() => setShowComplete(true)} />
      <DndContext sensors={sensors} onDragEnd={handleDragEnd}>
        <div className="flex gap-4 overflow-x-auto pb-4">
          {board.columns.map(col => (
            <BoardColumn key={col.status.id} column={col} />
          ))}
        </div>
      </DndContext>
      {showComplete && (
        <CompleteSprintDialog
          sprintName={board.sprint.name}
          openIssueCount={openIssueCount}
          loading={completeSprint.isPending}
          onCancel={() => setShowComplete(false)}
          onConfirm={() => {
            completeSprint.mutate(board.sprint.id, {
              onSuccess: () => setShowComplete(false),
            })
          }}
        />
      )}
    </div>
  )
}
```

- [ ] **Step 6: Verify build**

```bash
cd frontend && npm run build 2>&1 | tail -5
```
Expected: `✓ built in` — no errors.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/components/board/ frontend/src/components/sprint/CompleteSprintDialog.tsx \
        frontend/src/components/sprint/SprintHeader.tsx frontend/src/pages/board/
git commit -m "feat: add Board page — Kanban columns, drag & drop, sprint header, complete dialog"
```

---

## Task 15: Frontend Backlog Page

**Files:**
- Create: `frontend/src/components/sprint/CreateSprintForm.tsx`
- Create: `frontend/src/pages/backlog/BacklogPage.tsx`

- [ ] **Step 1: Create `frontend/src/components/sprint/CreateSprintForm.tsx`**

```tsx
import { useState } from 'react'
import { useCreateSprint } from '@/hooks/useSprints'

interface Props {
  projectKey: string
  onCreated: () => void
  onCancel: () => void
}

export function CreateSprintForm({ projectKey, onCreated, onCancel }: Props) {
  const createSprint = useCreateSprint(projectKey)
  const [name, setName] = useState(`Sprint ${Date.now()}`.slice(0, 20))
  const [goal, setGoal] = useState('')

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    await createSprint.mutateAsync({ name, goal: goal || undefined })
    onCreated()
  }

  return (
    <form onSubmit={handleSubmit} className="bg-gray-900 border border-gray-800 rounded-lg p-4 flex gap-3 items-end">
      <div className="flex-1">
        <label className="text-xs text-gray-400 block mb-1">Sprint Name</label>
        <input
          value={name} onChange={e => setName(e.target.value)} required
          className="w-full bg-gray-800 border border-gray-700 rounded px-3 py-1.5 text-sm text-white"
        />
      </div>
      <div className="flex-1">
        <label className="text-xs text-gray-400 block mb-1">Goal (optional)</label>
        <input
          value={goal} onChange={e => setGoal(e.target.value)}
          className="w-full bg-gray-800 border border-gray-700 rounded px-3 py-1.5 text-sm text-white"
        />
      </div>
      <button type="submit" disabled={createSprint.isPending}
        className="bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white text-sm px-4 py-1.5 rounded">
        Create
      </button>
      <button type="button" onClick={onCancel} className="text-gray-400 hover:text-white text-sm px-3 py-1.5">
        Cancel
      </button>
    </form>
  )
}
```

- [ ] **Step 2: Create `frontend/src/pages/backlog/BacklogPage.tsx`**

```tsx
import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { useBacklog } from '@/hooks/useBoard'
import { useStartSprint, useAssignIssue, useUnassignIssue } from '@/hooks/useSprints'
import { CreateSprintForm } from '@/components/sprint/CreateSprintForm'
import { StatusBadge } from '@/components/issue/StatusBadge'
import type { Issue } from '@/types'

function IssueRow({ issue, action }: { issue: Issue; action: React.ReactNode }) {
  return (
    <div className="flex items-center gap-3 px-4 py-2.5 bg-gray-900/50 rounded border border-gray-800/50 hover:border-gray-700">
      <span className="text-xs text-gray-500 font-mono w-20 shrink-0">{issue.key}</span>
      <span className="flex-1 text-sm text-white truncate">{issue.title}</span>
      <StatusBadge name={issue.statusName} category={issue.statusCategory} />
      {issue.storyPoints != null && (
        <span className="text-xs bg-gray-800 text-gray-400 px-1.5 py-0.5 rounded font-mono">{issue.storyPoints}</span>
      )}
      {action}
    </div>
  )
}

export function BacklogPage() {
  const { key } = useParams<{ key: string }>()
  const { data: backlog, isLoading } = useBacklog(key!)
  const startSprint = useStartSprint(key!)
  const assignIssue = useAssignIssue(key!)
  const unassignIssue = useUnassignIssue(key!)
  const [showCreateForm, setShowCreateForm] = useState(false)
  const [collapsed, setCollapsed] = useState<Set<string>>(new Set())

  const toggleCollapse = (id: string) =>
    setCollapsed(prev => { const s = new Set(prev); s.has(id) ? s.delete(id) : s.add(id); return s })

  if (isLoading) return <div className="text-gray-400">Loading...</div>

  return (
    <div className="max-w-4xl">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">Backlog</h1>
        {!showCreateForm && (
          <button onClick={() => setShowCreateForm(true)}
            className="text-sm bg-gray-800 hover:bg-gray-700 text-gray-300 px-3 py-1.5 rounded border border-gray-700">
            + New Sprint
          </button>
        )}
      </div>

      {showCreateForm && (
        <div className="mb-4">
          <CreateSprintForm
            projectKey={key!}
            onCreated={() => setShowCreateForm(false)}
            onCancel={() => setShowCreateForm(false)}
          />
        </div>
      )}

      {backlog?.sprints.map(entry => (
        <div key={entry.sprint.id} className="mb-4">
          <div className="flex items-center gap-3 mb-2">
            <button onClick={() => toggleCollapse(entry.sprint.id)} className="text-gray-400 hover:text-white text-xs">
              {collapsed.has(entry.sprint.id) ? '▶' : '▼'}
            </button>
            <span className="font-semibold text-white">{entry.sprint.name}</span>
            {entry.sprint.goal && <span className="text-xs text-gray-500">— {entry.sprint.goal}</span>}
            <span className="text-xs text-gray-500 ml-auto">{entry.issues.length} issues · {entry.totalPoints} pts</span>
            <button
              onClick={() => startSprint.mutate(entry.sprint.id)}
              disabled={startSprint.isPending}
              className="text-xs bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white px-3 py-1 rounded"
            >
              Start Sprint
            </button>
          </div>
          {!collapsed.has(entry.sprint.id) && (
            <div className="flex flex-col gap-1 ml-5">
              {entry.issues.map(issue => (
                <IssueRow
                  key={issue.id}
                  issue={issue}
                  action={
                    <button
                      onClick={() => unassignIssue.mutate({ sprintId: entry.sprint.id, issueId: issue.id })}
                      className="text-xs text-gray-500 hover:text-red-400 shrink-0"
                    >
                      ✕
                    </button>
                  }
                />
              ))}
              {entry.issues.length === 0 && (
                <p className="text-xs text-gray-600 py-2 ml-1">No issues in sprint yet</p>
              )}
            </div>
          )}
        </div>
      ))}

      <div className="mt-6">
        <div className="flex items-center gap-3 mb-2">
          <span className="font-semibold text-gray-300">Backlog</span>
          <span className="text-xs text-gray-500">{backlog?.backlogIssues.length ?? 0} issues</span>
        </div>
        <div className="flex flex-col gap-1">
          {backlog?.backlogIssues.map(issue => (
            <IssueRow
              key={issue.id}
              issue={issue}
              action={
                backlog.sprints.length > 0 ? (
                  <select
                    onChange={e => e.target.value && assignIssue.mutate({ sprintId: e.target.value, issueId: issue.id })}
                    defaultValue=""
                    className="text-xs bg-gray-800 text-gray-400 border border-gray-700 rounded px-2 py-1 shrink-0"
                  >
                    <option value="" disabled>Add to sprint</option>
                    {backlog.sprints.map(s => (
                      <option key={s.sprint.id} value={s.sprint.id}>{s.sprint.name}</option>
                    ))}
                  </select>
                ) : null
              }
            />
          ))}
          {backlog?.backlogIssues.length === 0 && (
            <p className="text-sm text-gray-600 py-4 text-center">No issues in backlog</p>
          )}
        </div>
      </div>
    </div>
  )
}
```

- [ ] **Step 3: Verify build**

```bash
cd frontend && npm run build 2>&1 | tail -5
```
Expected: `✓ built in` — no errors.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/sprint/CreateSprintForm.tsx \
        frontend/src/pages/backlog/
git commit -m "feat: add Backlog page — sprint planning, issue assignment, start sprint"
```

---

## Task 16: Frontend Reports Page

**Files:**
- Create: `frontend/src/pages/reports/ReportsPage.tsx`

- [ ] **Step 1: Create `frontend/src/pages/reports/ReportsPage.tsx`**

```tsx
import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { useSprints } from '@/hooks/useSprints'
import { useBurndown, useVelocity } from '@/hooks/useReports'
import {
  LineChart, Line, BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer
} from 'recharts'

export function ReportsPage() {
  const { key } = useParams<{ key: string }>()
  const { data: sprints } = useSprints(key!)
  const closedSprints = sprints?.filter(s => s.status === 'CLOSED') ?? []
  const activeSprint = sprints?.find(s => s.status === 'ACTIVE')
  const defaultSprintId = closedSprints[closedSprints.length - 1]?.id ?? activeSprint?.id ?? null
  const [selectedSprintId, setSelectedSprintId] = useState<string | null>(null)
  const sprintId = selectedSprintId ?? defaultSprintId

  const { data: burndown } = useBurndown(key!, sprintId)
  const { data: velocity } = useVelocity(key!)

  const burndownData = burndown?.days.map(d => ({
    date: d.date.slice(5),
    Ideal: d.idealPoints,
    Actual: d.remainingPoints,
  })) ?? []

  const velocityData = velocity?.entries.map(e => ({
    name: e.sprintName,
    Planned: e.plannedPoints,
    Completed: e.completedPoints,
  })) ?? []

  const selectableSprints = sprints?.filter(s => s.status !== 'PLANNED') ?? []

  return (
    <div className="max-w-4xl">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">Reports</h1>
        {selectableSprints.length > 0 && (
          <select
            value={sprintId ?? ''}
            onChange={e => setSelectedSprintId(e.target.value)}
            className="bg-gray-800 border border-gray-700 text-sm text-white rounded px-3 py-1.5"
          >
            {selectableSprints.map(s => (
              <option key={s.id} value={s.id}>{s.name}</option>
            ))}
          </select>
        )}
      </div>

      <div className="mb-8">
        <h2 className="text-lg font-semibold text-white mb-4">Burndown Chart</h2>
        {burndownData.length === 0 ? (
          <p className="text-gray-500 text-sm">No burndown data available. Sprint must have a start date and story points.</p>
        ) : (
          <ResponsiveContainer width="100%" height={280}>
            <LineChart data={burndownData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
              <XAxis dataKey="date" stroke="#6b7280" tick={{ fontSize: 11 }} />
              <YAxis stroke="#6b7280" tick={{ fontSize: 11 }} />
              <Tooltip contentStyle={{ backgroundColor: '#1f2937', border: '1px solid #374151', borderRadius: '6px' }} />
              <Legend />
              <Line type="monotone" dataKey="Ideal" stroke="#6b7280" strokeDasharray="5 5" dot={false} />
              <Line type="monotone" dataKey="Actual" stroke="#3b82f6" strokeWidth={2} dot={false} />
            </LineChart>
          </ResponsiveContainer>
        )}
      </div>

      <div>
        <h2 className="text-lg font-semibold text-white mb-4">Velocity</h2>
        {velocityData.length === 0 ? (
          <p className="text-gray-500 text-sm">No completed sprints yet.</p>
        ) : (
          <ResponsiveContainer width="100%" height={240}>
            <BarChart data={velocityData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
              <XAxis dataKey="name" stroke="#6b7280" tick={{ fontSize: 11 }} />
              <YAxis stroke="#6b7280" tick={{ fontSize: 11 }} />
              <Tooltip contentStyle={{ backgroundColor: '#1f2937', border: '1px solid #374151', borderRadius: '6px' }} />
              <Legend />
              <Bar dataKey="Planned" fill="#374151" radius={[3, 3, 0, 0]} />
              <Bar dataKey="Completed" fill="#3b82f6" radius={[3, 3, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        )}
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Verify build**

```bash
cd frontend && npm run build 2>&1 | tail -5
```
Expected: `✓ built in` — no errors.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/pages/reports/
git commit -m "feat: add Reports page — burndown and velocity charts with recharts"
```

---

## Task 17: WebSocket Hook + Wire into Pages

**Files:**
- Create: `frontend/src/hooks/useProjectSocket.ts`
- Modify: `frontend/src/pages/board/BoardPage.tsx`
- Modify: `frontend/src/pages/backlog/BacklogPage.tsx`

- [ ] **Step 1: Create `frontend/src/hooks/useProjectSocket.ts`**

```typescript
import { useEffect } from 'react'
import { Client } from '@stomp/stompjs'
import { useQueryClient } from '@tanstack/react-query'

export function useProjectSocket(projectKey: string) {
  const queryClient = useQueryClient()

  useEffect(() => {
    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
    const client = new Client({
      brokerURL: `${protocol}://${window.location.host}/ws-stomp`,
      reconnectDelay: 5000,
      onConnect: () => {
        client.subscribe(`/topic/projects/${projectKey}`, (message) => {
          const event = JSON.parse(message.body) as { type: string }
          if (event.type === 'ISSUE_MOVED') {
            queryClient.invalidateQueries({ queryKey: ['board', projectKey] })
          } else if (event.type === 'SPRINT_UPDATED') {
            queryClient.invalidateQueries({ queryKey: ['board', projectKey] })
            queryClient.invalidateQueries({ queryKey: ['sprints', projectKey] })
            queryClient.invalidateQueries({ queryKey: ['backlog', projectKey] })
          }
        })
      },
    })
    client.activate()
    return () => { client.deactivate() }
  }, [projectKey, queryClient])
}
```

- [ ] **Step 2: Add `useProjectSocket` to `BoardPage.tsx`**

Add import after existing imports:
```tsx
import { useProjectSocket } from '@/hooks/useProjectSocket'
```

Add as first line inside `BoardPage()` function body:
```tsx
useProjectSocket(key!)
```

- [ ] **Step 3: Add `useProjectSocket` to `BacklogPage.tsx`**

Add import after existing imports:
```tsx
import { useProjectSocket } from '@/hooks/useProjectSocket'
```

Add as first line inside `BacklogPage()` function body:
```tsx
useProjectSocket(key!)
```

- [ ] **Step 4: Verify build**

```bash
cd frontend && npm run build 2>&1 | tail -5
```
Expected: `✓ built in` — no errors.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/hooks/useProjectSocket.ts \
        frontend/src/pages/board/BoardPage.tsx \
        frontend/src/pages/backlog/BacklogPage.tsx
git commit -m "feat: add WebSocket hook for real-time board and backlog updates"
```

---

## Task 18: Navigation + Router

**Files:**
- Modify: `frontend/src/layouts/AppLayout.tsx`
- Modify: `frontend/src/app/router.tsx`

- [ ] **Step 1: Update `frontend/src/layouts/AppLayout.tsx`** — add project sub-nav

```tsx
import { Outlet, Link, useNavigate, useParams, useMatch } from 'react-router-dom'

export function AppLayout() {
  const navigate = useNavigate()
  const { key } = useParams<{ key?: string }>()
  const logout = () => {
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    navigate('/login')
  }

  return (
    <div className="min-h-screen bg-gray-950 text-white flex">
      <aside className="w-56 bg-gray-900 border-r border-gray-800 flex flex-col p-4 shrink-0">
        <Link to="/" className="text-xl font-bold mb-8">🐺 TaskWolf</Link>
        <nav className="flex flex-col gap-1 flex-1">
          <Link to="/" className="px-3 py-2 rounded hover:bg-gray-800 text-sm text-gray-300">Dashboard</Link>
          <Link to="/projects" className="px-3 py-2 rounded hover:bg-gray-800 text-sm text-gray-300">Projects</Link>
          {key && (
            <div className="mt-4">
              <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide px-3 mb-1">{key}</p>
              <Link to={`/p/${key}/board`} className="px-3 py-2 rounded hover:bg-gray-800 text-sm text-gray-300 flex items-center gap-2">
                <span>Board</span>
              </Link>
              <Link to={`/p/${key}/backlog`} className="px-3 py-2 rounded hover:bg-gray-800 text-sm text-gray-300 flex items-center gap-2">
                <span>Backlog</span>
              </Link>
              <Link to={`/p/${key}/issues`} className="px-3 py-2 rounded hover:bg-gray-800 text-sm text-gray-300 flex items-center gap-2">
                <span>Issues</span>
              </Link>
              <Link to={`/p/${key}/reports`} className="px-3 py-2 rounded hover:bg-gray-800 text-sm text-gray-300 flex items-center gap-2">
                <span>Reports</span>
              </Link>
            </div>
          )}
        </nav>
        <button onClick={logout} className="px-3 py-2 text-sm text-gray-400 hover:text-white text-left">
          Logout
        </button>
      </aside>
      <main className="flex-1 overflow-auto p-8">
        <Outlet />
      </main>
    </div>
  )
}
```

- [ ] **Step 2: Update `frontend/src/app/router.tsx`** — add board, backlog, reports routes

```tsx
import { createBrowserRouter, Navigate } from 'react-router-dom'
import { AuthLayout } from '@/layouts/AuthLayout'
import { AppLayout } from '@/layouts/AppLayout'
import { LoginPage } from '@/pages/auth/LoginPage'
import { RegisterPage } from '@/pages/auth/RegisterPage'
import { DashboardPage } from '@/pages/dashboard/DashboardPage'
import { ProjectListPage } from '@/pages/projects/ProjectListPage'
import { ProjectCreatePage } from '@/pages/projects/ProjectCreatePage'
import { IssueListPage } from '@/pages/issues/IssueListPage'
import { IssueDetailPage } from '@/pages/issues/IssueDetailPage'
import { BoardPage } from '@/pages/board/BoardPage'
import { BacklogPage } from '@/pages/backlog/BacklogPage'
import { ReportsPage } from '@/pages/reports/ReportsPage'

const isAuthenticated = () => !!localStorage.getItem('accessToken')

function RequireAuth({ children }: { children: React.ReactNode }) {
  return isAuthenticated() ? <>{children}</> : <Navigate to="/login" replace />
}

export const router = createBrowserRouter([
  {
    element: <AuthLayout />,
    children: [
      { path: '/login', element: <LoginPage /> },
      { path: '/register', element: <RegisterPage /> },
    ],
  },
  {
    element: <RequireAuth><AppLayout /></RequireAuth>,
    children: [
      { path: '/', element: <DashboardPage /> },
      { path: '/projects', element: <ProjectListPage /> },
      { path: '/projects/new', element: <ProjectCreatePage /> },
      { path: '/p/:key/board', element: <BoardPage /> },
      { path: '/p/:key/backlog', element: <BacklogPage /> },
      { path: '/p/:key/issues', element: <IssueListPage /> },
      { path: '/p/:key/issues/:issueKey', element: <IssueDetailPage /> },
      { path: '/p/:key/reports', element: <ReportsPage /> },
    ],
  },
])
```

- [ ] **Step 3: Verify final build**

```bash
cd frontend && npm run build 2>&1 | tail -5
```
Expected: `✓ built in` — no TypeScript errors.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/layouts/AppLayout.tsx frontend/src/app/router.tsx
git commit -m "feat: add board/backlog/reports routes and project sub-nav to sidebar"
```

---

## Task 19: Final Integration Test

- [ ] **Step 1: Build backend JAR**

```bash
cd backend && ./gradlew bootJar 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Build and start full stack**

```bash
docker compose build && docker compose up -d
sleep 25
curl -s http://localhost/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@test.com","displayName":"Admin","password":"password123"}'
```
Expected: `{"accessToken":"...","refreshToken":"..."}`

- [ ] **Step 3: Manual flow verification**

Open `http://localhost` and verify:
1. Login → redirect to dashboard
2. Create project (key: `DEMO`) → lands on issues page
3. Navigate to **Backlog** → click `+ New Sprint` → create "Sprint 1"
4. Create 2 issues from Issues page with story points (5 and 3)
5. On Backlog page: assign both issues to Sprint 1 via dropdown
6. Click **Start Sprint** → sidebar shows Board
7. Navigate to **Board** → two issues visible in "To Do" column
8. Drag one issue to "Done" column
9. Click **Complete Sprint** → dialog shows 1 unfinished issue → confirm
10. Navigate to **Backlog** → unfinished issue appears in backlog
11. Navigate to **Reports** → Velocity shows Sprint 1 with 5 completed points

- [ ] **Step 4: Stop stack**

```bash
docker compose down
```

- [ ] **Step 5: Final commit**

```bash
git add .
git commit -m "feat: Phase 2 complete — Agile Boards, Sprints, Burndown, WebSocket"
```

---

## Self-Review Notes

- `Issue.sprint` added as ManyToOne — V5 migration adds the FK constraint that was deferred from Phase 1
- `SprintService` depends on `IssueRepository` directly (cross-module) — acceptable for Phase 2; Phase 4 automation will use events instead
- Burndown uses `issue.updatedAt` as a proxy for completion date — an approximation; a `StatusHistory` table would be more accurate but is deferred to Phase 5
- `BoardController.move()` delegates directly to `IssueService.update()` — fires `IssueStatusChangedEvent` which `BoardEventPublisher` picks up, completing the real-time loop
- `useProjectSocket` uses native WebSocket via `/ws-stomp` endpoint — avoids sockjs-client Vite compatibility issues
- `AppLayout` reads `:key` from URL params — shows project sub-nav only when inside a project route
