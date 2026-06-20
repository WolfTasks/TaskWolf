# Phase 5: Dashboards & Reports — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a per-project configurable dashboard with a drag-and-drop widget canvas (react-grid-layout), Cycle Time report, and Issue List widget with three filter modes.

**Architecture:** New `Dashboard` and `DashboardWidget` JPA entities in the `reports` module. `DashboardService` enforces member-read / admin-write. Cycle Time is computed from existing `issue_activities` STATUS_CHANGED rows — no schema change needed. Frontend uses `react-grid-layout` for the free-form widget canvas; chart widgets reuse existing Recharts components.

**Tech Stack:** Kotlin/Spring Boot (backend), React 19 + TypeScript + react-grid-layout + Recharts (frontend), Flyway V12 migration, MockK (unit tests), Spring MockMvc + IntegrationTestBase (integration tests).

---

## File Map

### New backend files
- `backend/src/main/resources/db/migration/V12__create_dashboard_tables.sql`
- `backend/src/main/kotlin/com/taskowolf/reports/domain/WidgetType.kt`
- `backend/src/main/kotlin/com/taskowolf/reports/domain/Dashboard.kt`
- `backend/src/main/kotlin/com/taskowolf/reports/domain/DashboardWidget.kt`
- `backend/src/main/kotlin/com/taskowolf/reports/infrastructure/DashboardRepository.kt`
- `backend/src/main/kotlin/com/taskowolf/reports/infrastructure/DashboardWidgetRepository.kt`
- `backend/src/main/kotlin/com/taskowolf/reports/api/dto/DashboardResponse.kt`
- `backend/src/main/kotlin/com/taskowolf/reports/api/dto/WidgetResponse.kt`
- `backend/src/main/kotlin/com/taskowolf/reports/api/dto/AddWidgetRequest.kt`
- `backend/src/main/kotlin/com/taskowolf/reports/api/dto/LayoutItem.kt`
- `backend/src/main/kotlin/com/taskowolf/reports/api/dto/CycleTimeAggregateResponse.kt`
- `backend/src/main/kotlin/com/taskowolf/reports/api/dto/CycleTimeResponse.kt`
- `backend/src/main/kotlin/com/taskowolf/reports/application/DashboardService.kt`
- `backend/src/main/kotlin/com/taskowolf/reports/api/DashboardController.kt`
- `backend/src/test/kotlin/com/taskowolf/reports/DashboardServiceTest.kt`
- `backend/src/test/kotlin/com/taskowolf/reports/ReportsServiceCycleTimeTest.kt`
- `backend/src/test/kotlin/com/taskowolf/reports/DashboardControllerTest.kt`

### Modified backend files
- `backend/src/main/kotlin/com/taskowolf/comments/infrastructure/IssueActivityRepository.kt` — add `findByIssueIdAndType`
- `backend/src/main/kotlin/com/taskowolf/reports/application/ReportsService.kt` — add `getCycleTime`, `getCycleTimeAggregate`
- `backend/src/main/kotlin/com/taskowolf/reports/api/ReportsController.kt` — add cycle-time endpoints
- `backend/src/main/kotlin/com/taskowolf/issues/infrastructure/IssueRepository.kt` — add filtered query methods
- `backend/src/main/kotlin/com/taskowolf/issues/application/IssueService.kt` — add `assigneeMe`, `sort`, `overdue` params to `findByProject`
- `backend/src/main/kotlin/com/taskowolf/issues/api/IssueController.kt` — add new `@RequestParam`s

### New frontend files
- `frontend/src/pages/project-dashboard/ProjectDashboardPage.tsx`
- `frontend/src/components/dashboard/DashboardCanvas.tsx`
- `frontend/src/components/dashboard/WidgetWrapper.tsx`
- `frontend/src/components/dashboard/WidgetPalette.tsx`
- `frontend/src/components/dashboard/BurndownWidget.tsx`
- `frontend/src/components/dashboard/VelocityWidget.tsx`
- `frontend/src/components/dashboard/CycleTimeWidget.tsx`
- `frontend/src/components/dashboard/IssueCountWidget.tsx`
- `frontend/src/components/dashboard/IssuesByStatusWidget.tsx`
- `frontend/src/components/dashboard/IssueListWidget.tsx`
- `frontend/src/hooks/useProjectDashboard.ts`
- `frontend/src/hooks/useCycleTime.ts`

### Modified frontend files
- `frontend/src/app/router.tsx` — add `/p/:key/dashboard` route
- `frontend/src/layouts/AppLayout.tsx` — add "Dashboard" sub-nav link
- `frontend/src/api/reports.ts` (or `client.ts`) — add cycle-time + dashboard API calls

---

## Task 1: Flyway migration + Dashboard domain entities

**Files:**
- Create: `backend/src/main/resources/db/migration/V12__create_dashboard_tables.sql`
- Create: `backend/src/main/kotlin/com/taskowolf/reports/domain/WidgetType.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/reports/domain/Dashboard.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/reports/domain/DashboardWidget.kt`

- [ ] **Step 1: Write the migration**

`backend/src/main/resources/db/migration/V12__create_dashboard_tables.sql`:
```sql
CREATE TABLE dashboard (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    project_id UUID        NOT NULL UNIQUE REFERENCES projects(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE dashboard_widget (
    id           UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    dashboard_id UUID        NOT NULL REFERENCES dashboard(id) ON DELETE CASCADE,
    type         VARCHAR(40) NOT NULL,
    config       TEXT,
    grid_x       INT         NOT NULL DEFAULT 0,
    grid_y       INT         NOT NULL DEFAULT 0,
    grid_w       INT         NOT NULL DEFAULT 4,
    grid_h       INT         NOT NULL DEFAULT 4,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL
);
```

- [ ] **Step 2: Create WidgetType enum**

`backend/src/main/kotlin/com/taskowolf/reports/domain/WidgetType.kt`:
```kotlin
package com.taskowolf.reports.domain

enum class WidgetType {
    BURNDOWN, VELOCITY, CYCLE_TIME, ISSUE_COUNT, ISSUES_BY_STATUS, ISSUE_LIST
}
```

- [ ] **Step 3: Create Dashboard entity**

`backend/src/main/kotlin/com/taskowolf/reports/domain/Dashboard.kt`:
```kotlin
package com.taskowolf.reports.domain

import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "dashboard")
class Dashboard(
    @Column(name = "project_id", nullable = false, unique = true)
    val projectId: UUID,

    @OneToMany(
        mappedBy = "dashboard",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    val widgets: MutableList<DashboardWidget> = mutableListOf()
) : AuditableEntity()
```

- [ ] **Step 4: Create DashboardWidget entity**

`backend/src/main/kotlin/com/taskowolf/reports/domain/DashboardWidget.kt`:
```kotlin
package com.taskowolf.reports.domain

import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.*

@Entity
@Table(name = "dashboard_widget")
class DashboardWidget(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dashboard_id", nullable = false)
    val dashboard: Dashboard,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    val type: WidgetType,

    @Column(columnDefinition = "TEXT")
    val config: String? = null,

    @Column(name = "grid_x", nullable = false)
    var gridX: Int = 0,

    @Column(name = "grid_y", nullable = false)
    var gridY: Int = 0,

    @Column(name = "grid_w", nullable = false)
    var gridW: Int = 4,

    @Column(name = "grid_h", nullable = false)
    var gridH: Int = 4
) : AuditableEntity()
```

- [ ] **Step 5: Verify the app starts with the migration**

```bash
cd backend && ./gradlew bootRun --args='--spring.profiles.active=dev' &
# wait ~15 seconds, then Ctrl-C
# Expected: "Started TaskWolfApplication" in output, no Flyway errors
```

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/db/migration/V12__create_dashboard_tables.sql \
        backend/src/main/kotlin/com/taskowolf/reports/domain/
git commit -m "feat(reports): add Dashboard and DashboardWidget domain entities with V12 migration"
```

---

## Task 2: Dashboard repositories + DTOs

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/reports/infrastructure/DashboardRepository.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/reports/infrastructure/DashboardWidgetRepository.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/reports/api/dto/DashboardResponse.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/reports/api/dto/WidgetResponse.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/reports/api/dto/AddWidgetRequest.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/reports/api/dto/LayoutItem.kt`

- [ ] **Step 1: Create DashboardRepository**

`backend/src/main/kotlin/com/taskowolf/reports/infrastructure/DashboardRepository.kt`:
```kotlin
package com.taskowolf.reports.infrastructure

import com.taskowolf.reports.domain.Dashboard
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface DashboardRepository : JpaRepository<Dashboard, UUID> {
    fun findByProjectId(projectId: UUID): Optional<Dashboard>
}
```

- [ ] **Step 2: Create DashboardWidgetRepository**

`backend/src/main/kotlin/com/taskowolf/reports/infrastructure/DashboardWidgetRepository.kt`:
```kotlin
package com.taskowolf.reports.infrastructure

import com.taskowolf.reports.domain.DashboardWidget
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DashboardWidgetRepository : JpaRepository<DashboardWidget, UUID>
```

- [ ] **Step 3: Create WidgetResponse DTO**

`backend/src/main/kotlin/com/taskowolf/reports/api/dto/WidgetResponse.kt`:
```kotlin
package com.taskowolf.reports.api.dto

import com.taskowolf.reports.domain.DashboardWidget
import com.taskowolf.reports.domain.WidgetType
import java.util.UUID

data class WidgetResponse(
    val id: UUID,
    val type: WidgetType,
    val config: String?,
    val gridX: Int,
    val gridY: Int,
    val gridW: Int,
    val gridH: Int
) {
    companion object {
        fun from(w: DashboardWidget) = WidgetResponse(
            id = w.id,
            type = w.type,
            config = w.config,
            gridX = w.gridX,
            gridY = w.gridY,
            gridW = w.gridW,
            gridH = w.gridH
        )
    }
}
```

- [ ] **Step 4: Create DashboardResponse DTO**

`backend/src/main/kotlin/com/taskowolf/reports/api/dto/DashboardResponse.kt`:
```kotlin
package com.taskowolf.reports.api.dto

import com.taskowolf.reports.domain.Dashboard
import java.util.UUID

data class DashboardResponse(
    val id: UUID,
    val projectId: UUID,
    val widgets: List<WidgetResponse>
) {
    companion object {
        fun from(d: Dashboard) = DashboardResponse(
            id = d.id,
            projectId = d.projectId,
            widgets = d.widgets.map { WidgetResponse.from(it) }
        )
    }
}
```

- [ ] **Step 5: Create AddWidgetRequest and LayoutItem DTOs**

`backend/src/main/kotlin/com/taskowolf/reports/api/dto/AddWidgetRequest.kt`:
```kotlin
package com.taskowolf.reports.api.dto

import com.taskowolf.reports.domain.WidgetType

data class AddWidgetRequest(
    val type: WidgetType,
    val config: String? = null,
    val gridX: Int = 0,
    val gridY: Int = 0,
    val gridW: Int = 4,
    val gridH: Int = 4
)
```

`backend/src/main/kotlin/com/taskowolf/reports/api/dto/LayoutItem.kt`:
```kotlin
package com.taskowolf.reports.api.dto

import java.util.UUID

data class LayoutItem(
    val widgetId: UUID,
    val gridX: Int,
    val gridY: Int,
    val gridW: Int,
    val gridH: Int
)
```

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/reports/infrastructure/ \
        backend/src/main/kotlin/com/taskowolf/reports/api/dto/DashboardResponse.kt \
        backend/src/main/kotlin/com/taskowolf/reports/api/dto/WidgetResponse.kt \
        backend/src/main/kotlin/com/taskowolf/reports/api/dto/AddWidgetRequest.kt \
        backend/src/main/kotlin/com/taskowolf/reports/api/dto/LayoutItem.kt
git commit -m "feat(reports): add Dashboard repositories and DTOs"
```

---

## Task 3: DashboardService (TDD)

**Files:**
- Create: `backend/src/test/kotlin/com/taskowolf/reports/DashboardServiceTest.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/reports/application/DashboardService.kt`

- [ ] **Step 1: Write failing tests**

`backend/src/test/kotlin/com/taskowolf/reports/DashboardServiceTest.kt`:
```kotlin
package com.taskowolf.reports

import com.taskowolf.core.infrastructure.ForbiddenException
import com.taskowolf.core.infrastructure.NotFoundException
import com.taskowolf.projects.application.ProjectService
import com.taskowolf.projects.domain.Project
import com.taskowolf.auth.domain.User
import com.taskowolf.reports.api.dto.AddWidgetRequest
import com.taskowolf.reports.api.dto.LayoutItem
import com.taskowolf.reports.application.DashboardService
import com.taskowolf.reports.domain.Dashboard
import com.taskowolf.reports.domain.DashboardWidget
import com.taskowolf.reports.domain.WidgetType
import com.taskowolf.reports.infrastructure.DashboardRepository
import com.taskowolf.reports.infrastructure.DashboardWidgetRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Optional
import java.util.UUID

class DashboardServiceTest {

    private val projectService = mockk<ProjectService>()
    private val dashboardRepository = mockk<DashboardRepository>()
    private val dashboardWidgetRepository = mockk<DashboardWidgetRepository>()
    private val service = DashboardService(projectService, dashboardRepository, dashboardWidgetRepository)

    private val owner = User(email = "owner@test.com", displayName = "Owner")
    private val project = Project(key = "WOLF", name = "TaskWolf", owner = owner, workflow = null)
    private val userId = owner.id

    @BeforeEach
    fun setUp() {
        every { dashboardRepository.save(any()) } returnsArgument 0
        every { dashboardWidgetRepository.save(any()) } returnsArgument 0
    }

    @Test
    fun `getDashboard auto-creates dashboard when none exists`() {
        every { projectService.requireMember("WOLF", userId) } returns project
        every { dashboardRepository.findByProjectId(project.id) } returns Optional.empty()

        val result = service.getDashboard("WOLF", userId)

        verify { dashboardRepository.save(any<Dashboard>()) }
        assertEquals(project.id, result.projectId)
        assertTrue(result.widgets.isEmpty())
    }

    @Test
    fun `getDashboard returns existing dashboard`() {
        val dashboard = Dashboard(projectId = project.id)
        every { projectService.requireMember("WOLF", userId) } returns project
        every { dashboardRepository.findByProjectId(project.id) } returns Optional.of(dashboard)

        val result = service.getDashboard("WOLF", userId)

        assertEquals(dashboard.id, result.id)
    }

    @Test
    fun `addWidget requires admin role`() {
        every { projectService.requireAdmin("WOLF", userId) } throws ForbiddenException("admin required")

        assertThrows<ForbiddenException> {
            service.addWidget("WOLF", AddWidgetRequest(type = WidgetType.VELOCITY), userId)
        }
    }

    @Test
    fun `addWidget saves widget and returns response`() {
        val dashboard = Dashboard(projectId = project.id)
        every { projectService.requireAdmin("WOLF", userId) } returns project
        every { dashboardRepository.findByProjectId(project.id) } returns Optional.of(dashboard)

        val request = AddWidgetRequest(type = WidgetType.VELOCITY, gridX = 2, gridY = 0, gridW = 6, gridH = 4)
        val result = service.addWidget("WOLF", request, userId)

        val widgetSlot = slot<DashboardWidget>()
        verify { dashboardWidgetRepository.save(capture(widgetSlot)) }
        assertEquals(WidgetType.VELOCITY, widgetSlot.captured.type)
        assertEquals(2, widgetSlot.captured.gridX)
    }

    @Test
    fun `removeWidget throws NotFoundException when widget not in dashboard`() {
        val dashboard = Dashboard(projectId = project.id)
        val otherWidgetId = UUID.randomUUID()
        every { projectService.requireAdmin("WOLF", userId) } returns project
        every { dashboardRepository.findByProjectId(project.id) } returns Optional.of(dashboard)
        every { dashboardWidgetRepository.findById(otherWidgetId) } returns Optional.empty()

        assertThrows<NotFoundException> {
            service.removeWidget("WOLF", otherWidgetId, userId)
        }
    }

    @Test
    fun `saveLayout updates grid positions of existing widgets`() {
        val dashboard = Dashboard(projectId = project.id)
        val widget = DashboardWidget(dashboard = dashboard, type = WidgetType.BURNDOWN)
        every { projectService.requireAdmin("WOLF", userId) } returns project
        every { dashboardRepository.findByProjectId(project.id) } returns Optional.of(dashboard)
        every { dashboardWidgetRepository.findById(widget.id) } returns Optional.of(widget)

        service.saveLayout("WOLF", listOf(LayoutItem(widgetId = widget.id, gridX = 3, gridY = 1, gridW = 6, gridH = 5)), userId)

        assertEquals(3, widget.gridX)
        assertEquals(1, widget.gridY)
        assertEquals(6, widget.gridW)
        assertEquals(5, widget.gridH)
    }
}
```

- [ ] **Step 2: Run tests — expect compile failure**

```bash
cd backend && ./gradlew test --tests "com.taskowolf.reports.DashboardServiceTest" 2>&1 | tail -20
# Expected: compile error — DashboardService does not exist yet
```

- [ ] **Step 3: Create DashboardService**

`backend/src/main/kotlin/com/taskowolf/reports/application/DashboardService.kt`:
```kotlin
package com.taskowolf.reports.application

import com.taskowolf.core.infrastructure.NotFoundException
import com.taskowolf.projects.application.ProjectService
import com.taskowolf.reports.api.dto.*
import com.taskowolf.reports.domain.Dashboard
import com.taskowolf.reports.domain.DashboardWidget
import com.taskowolf.reports.infrastructure.DashboardRepository
import com.taskowolf.reports.infrastructure.DashboardWidgetRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class DashboardService(
    private val projectService: ProjectService,
    private val dashboardRepository: DashboardRepository,
    private val dashboardWidgetRepository: DashboardWidgetRepository
) {
    @Transactional
    fun getDashboard(projectKey: String, userId: UUID): DashboardResponse {
        val project = projectService.requireMember(projectKey, userId)
        val dashboard = dashboardRepository.findByProjectId(project.id)
            .orElseGet { dashboardRepository.save(Dashboard(projectId = project.id)) }
        return DashboardResponse.from(dashboard)
    }

    @Transactional
    fun saveLayout(projectKey: String, items: List<LayoutItem>, userId: UUID): DashboardResponse {
        val project = projectService.requireAdmin(projectKey, userId)
        val dashboard = dashboardRepository.findByProjectId(project.id)
            .orElseThrow { NotFoundException("Dashboard not found") }
        items.forEach { item ->
            val widget = dashboardWidgetRepository.findById(item.widgetId)
                .filter { it.dashboard.id == dashboard.id }
                .orElseThrow { NotFoundException("Widget not found: ${item.widgetId}") }
            widget.gridX = item.gridX
            widget.gridY = item.gridY
            widget.gridW = item.gridW
            widget.gridH = item.gridH
        }
        return DashboardResponse.from(dashboard)
    }

    @Transactional
    fun addWidget(projectKey: String, request: AddWidgetRequest, userId: UUID): WidgetResponse {
        val project = projectService.requireAdmin(projectKey, userId)
        val dashboard = dashboardRepository.findByProjectId(project.id)
            .orElseGet { dashboardRepository.save(Dashboard(projectId = project.id)) }
        val widget = dashboardWidgetRepository.save(
            DashboardWidget(
                dashboard = dashboard,
                type = request.type,
                config = request.config,
                gridX = request.gridX,
                gridY = request.gridY,
                gridW = request.gridW,
                gridH = request.gridH
            )
        )
        return WidgetResponse.from(widget)
    }

    @Transactional
    fun removeWidget(projectKey: String, widgetId: UUID, userId: UUID) {
        val project = projectService.requireAdmin(projectKey, userId)
        val dashboard = dashboardRepository.findByProjectId(project.id)
            .orElseThrow { NotFoundException("Dashboard not found") }
        val widget = dashboardWidgetRepository.findById(widgetId)
            .filter { it.dashboard.id == dashboard.id }
            .orElseThrow { NotFoundException("Widget not found: $widgetId") }
        dashboardWidgetRepository.delete(widget)
    }
}
```

- [ ] **Step 4: Run tests — expect all green**

```bash
cd backend && ./gradlew test --tests "com.taskowolf.reports.DashboardServiceTest"
# Expected: 5 tests PASSED
```

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/reports/application/DashboardService.kt \
        backend/src/test/kotlin/com/taskowolf/reports/DashboardServiceTest.kt
git commit -m "feat(reports): add DashboardService with TDD unit tests"
```

---

## Task 4: DashboardController + integration test

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/reports/api/DashboardController.kt`
- Create: `backend/src/test/kotlin/com/taskowolf/reports/DashboardControllerTest.kt`

- [ ] **Step 1: Create DashboardController**

`backend/src/main/kotlin/com/taskowolf/reports/api/DashboardController.kt`:
```kotlin
package com.taskowolf.reports.api

import com.taskowolf.auth.domain.User
import com.taskowolf.reports.api.dto.AddWidgetRequest
import com.taskowolf.reports.api.dto.LayoutItem
import com.taskowolf.reports.application.DashboardService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/projects/{key}/dashboard")
class DashboardController(private val dashboardService: DashboardService) {

    @GetMapping
    fun get(@PathVariable key: String, @AuthenticationPrincipal user: User) =
        dashboardService.getDashboard(key, user.id)

    @PutMapping("/layout")
    fun saveLayout(
        @PathVariable key: String,
        @RequestBody items: List<LayoutItem>,
        @AuthenticationPrincipal user: User
    ) = dashboardService.saveLayout(key, items, user.id)

    @PostMapping("/widgets")
    @ResponseStatus(HttpStatus.CREATED)
    fun addWidget(
        @PathVariable key: String,
        @RequestBody request: AddWidgetRequest,
        @AuthenticationPrincipal user: User
    ) = dashboardService.addWidget(key, request, user.id)

    @DeleteMapping("/widgets/{widgetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeWidget(
        @PathVariable key: String,
        @PathVariable widgetId: UUID,
        @AuthenticationPrincipal user: User
    ) = dashboardService.removeWidget(key, widgetId, user.id)
}
```

- [ ] **Step 2: Write integration test**

`backend/src/test/kotlin/com/taskowolf/reports/DashboardControllerTest.kt`:
```kotlin
package com.taskowolf.reports

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class DashboardControllerTest : IntegrationTestBase() {

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
    fun `GET dashboard auto-creates empty dashboard for project member`() {
        val token = registerAndLogin("dash1@test.com")
        createProject(token, "DASH1")

        mockMvc.perform(
            get("/api/v1/projects/DASH1/dashboard")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.projectId").isNotEmpty)
            .andExpect(jsonPath("$.widgets").isArray)
            .andExpect(jsonPath("$.widgets.length()").value(0))
    }

    @Test
    fun `POST widget adds widget to dashboard (owner is admin)`() {
        val token = registerAndLogin("dash2@test.com")
        createProject(token, "DASH2")

        mockMvc.perform(
            post("/api/v1/projects/DASH2/dashboard/widgets")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"type":"VELOCITY","gridX":0,"gridY":0,"gridW":6,"gridH":4}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.type").value("VELOCITY"))
            .andExpect(jsonPath("$.gridW").value(6))
    }

    @Test
    fun `non-member cannot GET dashboard`() {
        val ownerToken = registerAndLogin("dash3owner@test.com")
        val otherToken = registerAndLogin("dash3other@test.com")
        createProject(ownerToken, "DASH3")

        mockMvc.perform(
            get("/api/v1/projects/DASH3/dashboard")
                .header("Authorization", "Bearer $otherToken")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `DELETE widget removes it from dashboard`() {
        val token = registerAndLogin("dash4@test.com")
        createProject(token, "DASH4")

        val addResult = mockMvc.perform(
            post("/api/v1/projects/DASH4/dashboard/widgets")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"type":"ISSUE_COUNT","gridX":0,"gridY":0,"gridW":3,"gridH":2}""")
        ).andReturn()
        val widgetId = objectMapper.readTree(addResult.response.contentAsString).get("id").asText()

        mockMvc.perform(
            delete("/api/v1/projects/DASH4/dashboard/widgets/$widgetId")
                .header("Authorization", "Bearer $token")
        ).andExpect(status().isNoContent)

        mockMvc.perform(
            get("/api/v1/projects/DASH4/dashboard")
                .header("Authorization", "Bearer $token")
        ).andExpect(jsonPath("$.widgets.length()").value(0))
    }
}
```

- [ ] **Step 3: Run integration tests**

```bash
cd backend && ./gradlew test --tests "com.taskowolf.reports.DashboardControllerTest"
# Expected: 4 tests PASSED
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/reports/api/DashboardController.kt \
        backend/src/test/kotlin/com/taskowolf/reports/DashboardControllerTest.kt
git commit -m "feat(reports): add DashboardController with integration tests"
```

---

## Task 5: Cycle Time backend (TDD)

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/reports/api/dto/CycleTimeAggregateResponse.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/reports/api/dto/CycleTimeResponse.kt`
- Create: `backend/src/test/kotlin/com/taskowolf/reports/ReportsServiceCycleTimeTest.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/comments/infrastructure/IssueActivityRepository.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/reports/application/ReportsService.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/reports/api/ReportsController.kt`

- [ ] **Step 1: Create Cycle Time DTOs**

`backend/src/main/kotlin/com/taskowolf/reports/api/dto/CycleTimeResponse.kt`:
```kotlin
package com.taskowolf.reports.api.dto

import java.util.UUID

data class IssueCycleTime(
    val issueId: UUID,
    val issueKey: String,
    val cycleTimeHours: Double?
)

data class CycleTimeResponse(
    val sprintId: UUID,
    val issues: List<IssueCycleTime>,
    val averageCycleTimeHours: Double?
)
```

`backend/src/main/kotlin/com/taskowolf/reports/api/dto/CycleTimeAggregateResponse.kt`:
```kotlin
package com.taskowolf.reports.api.dto

import java.util.UUID

data class SprintCycleTime(
    val sprintId: UUID,
    val sprintName: String,
    val averageCycleTimeHours: Double?
)

data class CycleTimeAggregateResponse(
    val sprints: List<SprintCycleTime>
)
```

- [ ] **Step 2: Add findByIssueIdAndType to IssueActivityRepository**

`backend/src/main/kotlin/com/taskowolf/comments/infrastructure/IssueActivityRepository.kt`:
```kotlin
package com.taskowolf.comments.infrastructure

import com.taskowolf.comments.domain.ActivityType
import com.taskowolf.comments.domain.IssueActivity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface IssueActivityRepository : JpaRepository<IssueActivity, UUID> {
    fun findAllByIssueId(issueId: UUID, pageable: Pageable): Page<IssueActivity>
    fun findByIssueIdAndTypeOrderByCreatedAtAsc(issueId: UUID, type: ActivityType): List<IssueActivity>
}
```

- [ ] **Step 3: Write failing cycle time tests**

`backend/src/test/kotlin/com/taskowolf/reports/ReportsServiceCycleTimeTest.kt`:
```kotlin
package com.taskowolf.reports

import com.taskowolf.auth.domain.User
import com.taskowolf.comments.domain.ActivityType
import com.taskowolf.comments.domain.IssueActivity
import com.taskowolf.comments.infrastructure.IssueActivityRepository
import com.taskowolf.core.infrastructure.ForbiddenException
import com.taskowolf.issues.domain.Issue
import com.taskowolf.issues.domain.IssueType
import com.taskowolf.issues.infrastructure.IssueRepository
import com.taskowolf.projects.application.ProjectService
import com.taskowolf.projects.domain.Project
import com.taskowolf.reports.application.ReportsService
import com.taskowolf.sprints.domain.Sprint
import com.taskowolf.sprints.domain.SprintStatus
import com.taskowolf.sprints.infrastructure.SprintRepository
import com.taskowolf.workflows.domain.StatusCategory
import com.taskowolf.workflows.domain.Workflow
import com.taskowolf.workflows.domain.WorkflowStatus
import com.taskowolf.workflows.infrastructure.StatusRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Optional
import java.util.UUID

class ReportsServiceCycleTimeTest {

    private val projectService = mockk<ProjectService>()
    private val sprintRepository = mockk<SprintRepository>()
    private val issueRepository = mockk<IssueRepository>()
    private val activityRepository = mockk<IssueActivityRepository>()
    private val statusRepository = mockk<StatusRepository>()
    private val service = ReportsService(projectService, sprintRepository, issueRepository, activityRepository, statusRepository)

    private val owner = User(email = "owner@test.com", displayName = "Owner")
    private val project = Project(key = "WOLF", name = "TaskWolf", owner = owner, workflow = null)
    private val workflow = Workflow(name = "Default", project = project)
    private val todoStatus = WorkflowStatus("To Do", StatusCategory.TODO, "#aaa", 0, workflow)
    private val inProgressStatus = WorkflowStatus("In Progress", StatusCategory.IN_PROGRESS, "#bbb", 1, workflow)
    private val doneStatus = WorkflowStatus("Done", StatusCategory.DONE, "#ccc", 2, workflow)
    private val sprint = Sprint(name = "Sprint 1", status = SprintStatus.CLOSED, project = project)

    @BeforeEach
    fun setUp() {
        every { projectService.requireMember("WOLF", owner.id) } returns project
        every { sprintRepository.findById(sprint.id) } returns Optional.of(sprint)
        every { statusRepository.findByWorkflowIdAndName(any(), "In Progress") } returns inProgressStatus
        every { statusRepository.findByWorkflowIdAndName(any(), "Done") } returns doneStatus
        every { statusRepository.findByWorkflowIdAndName(any(), "To Do") } returns todoStatus
        every { statusRepository.findByName(any()) } answers {
            when (firstArg<String>()) {
                "In Progress" -> inProgressStatus
                "Done" -> doneStatus
                else -> todoStatus
            }
        }
    }

    private fun makeIssue(key: String) = Issue(
        key = key, keyNumber = 1, title = key, type = IssueType.TASK,
        status = doneStatus, project = project, reporter = owner
    )

    private fun makeActivity(issueId: UUID, newValue: String, hoursAgo: Long): IssueActivity {
        val activity = IssueActivity(
            issueId = issueId,
            actorId = owner.id,
            type = ActivityType.STATUS_CHANGED,
            newValue = newValue
        )
        // Reflectively set createdAt — AuditableEntity sets it via @CreatedDate, so set it directly in test
        val field = activity.javaClass.superclass.superclass.getDeclaredField("createdAt")
        field.isAccessible = true
        field.set(activity, Instant.now().minus(hoursAgo, ChronoUnit.HOURS))
        return activity
    }

    @Test
    fun `getCycleTime returns correct hours for single issue`() {
        val issue = makeIssue("WOLF-1")
        every { issueRepository.findBySprintId(sprint.id) } returns listOf(issue)
        every { activityRepository.findByIssueIdAndTypeOrderByCreatedAtAsc(issue.id, ActivityType.STATUS_CHANGED) } returns listOf(
            makeActivity(issue.id, "In Progress", 10),
            makeActivity(issue.id, "Done", 2)
        )

        val result = service.getCycleTime("WOLF", sprint.id, owner.id)

        assertEquals(1, result.issues.size)
        assertNotNull(result.averageCycleTimeHours)
        assertTrue(result.averageCycleTimeHours!! in 7.9..8.1, "Expected ~8h but got ${result.averageCycleTimeHours}")
    }

    @Test
    fun `getCycleTime skips issues that never reached IN_PROGRESS`() {
        val issue = makeIssue("WOLF-2")
        every { issueRepository.findBySprintId(sprint.id) } returns listOf(issue)
        every { activityRepository.findByIssueIdAndTypeOrderByCreatedAtAsc(issue.id, ActivityType.STATUS_CHANGED) } returns listOf(
            makeActivity(issue.id, "Done", 2)
        )

        val result = service.getCycleTime("WOLF", sprint.id, owner.id)

        assertTrue(result.issues.all { it.cycleTimeHours == null })
        assertNull(result.averageCycleTimeHours)
    }

    @Test
    fun `getCycleTime skips issues in progress but never done`() {
        val issue = makeIssue("WOLF-3")
        every { issueRepository.findBySprintId(sprint.id) } returns listOf(issue)
        every { activityRepository.findByIssueIdAndTypeOrderByCreatedAtAsc(issue.id, ActivityType.STATUS_CHANGED) } returns listOf(
            makeActivity(issue.id, "In Progress", 5)
        )

        val result = service.getCycleTime("WOLF", sprint.id, owner.id)

        assertTrue(result.issues.all { it.cycleTimeHours == null })
        assertNull(result.averageCycleTimeHours)
    }

    @Test
    fun `getCycleTime averages correctly across multiple issues`() {
        val issue1 = makeIssue("WOLF-4")
        val issue2 = makeIssue("WOLF-5")
        every { issueRepository.findBySprintId(sprint.id) } returns listOf(issue1, issue2)
        every { activityRepository.findByIssueIdAndTypeOrderByCreatedAtAsc(issue1.id, ActivityType.STATUS_CHANGED) } returns listOf(
            makeActivity(issue1.id, "In Progress", 10),
            makeActivity(issue1.id, "Done", 2)   // ~8h
        )
        every { activityRepository.findByIssueIdAndTypeOrderByCreatedAtAsc(issue2.id, ActivityType.STATUS_CHANGED) } returns listOf(
            makeActivity(issue2.id, "In Progress", 20),
            makeActivity(issue2.id, "Done", 4)   // ~16h
        )

        val result = service.getCycleTime("WOLF", sprint.id, owner.id)

        assertNotNull(result.averageCycleTimeHours)
        assertTrue(result.averageCycleTimeHours!! in 11.9..12.1, "Expected ~12h avg but got ${result.averageCycleTimeHours}")
    }

    @Test
    fun `getCycleTime returns null average when no issues in sprint`() {
        every { issueRepository.findBySprintId(sprint.id) } returns emptyList()

        val result = service.getCycleTime("WOLF", sprint.id, owner.id)

        assertTrue(result.issues.isEmpty())
        assertNull(result.averageCycleTimeHours)
    }
}
```

- [ ] **Step 4: Run tests — expect compile failure**

```bash
cd backend && ./gradlew test --tests "com.taskowolf.reports.ReportsServiceCycleTimeTest" 2>&1 | tail -20
# Expected: compile error — ReportsService constructor doesn't match yet
```

- [ ] **Step 5: Extend ReportsService with Cycle Time**

The current `ReportsService` constructor takes 3 args. Add `activityRepository` and `statusRepository`. Replace the full file:

`backend/src/main/kotlin/com/taskowolf/reports/application/ReportsService.kt`:
```kotlin
package com.taskowolf.reports.application

import com.taskowolf.comments.domain.ActivityType
import com.taskowolf.comments.infrastructure.IssueActivityRepository
import com.taskowolf.core.infrastructure.ForbiddenException
import com.taskowolf.core.infrastructure.NotFoundException
import com.taskowolf.issues.infrastructure.IssueRepository
import com.taskowolf.projects.application.ProjectService
import com.taskowolf.reports.api.dto.*
import com.taskowolf.sprints.domain.SprintStatus
import com.taskowolf.sprints.infrastructure.SprintRepository
import com.taskowolf.workflows.domain.StatusCategory
import com.taskowolf.workflows.infrastructure.StatusRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class ReportsService(
    private val projectService: ProjectService,
    private val sprintRepository: SprintRepository,
    private val issueRepository: IssueRepository,
    private val activityRepository: IssueActivityRepository,
    private val statusRepository: StatusRepository
) {
    @Transactional(readOnly = true)
    fun getBurndown(projectKey: String, sprintId: UUID, userId: UUID): BurndownResponse {
        val project = projectService.requireMember(projectKey, userId)
        val sprint = sprintRepository.findById(sprintId).orElseThrow { NotFoundException("Sprint not found") }
        if (sprint.project.id != project.id) throw ForbiddenException("Sprint does not belong to this project")
        val issues = issueRepository.findBySprintId(sprintId)
        val startDate = sprint.startDate ?: return BurndownResponse(sprintId, emptyList())
        val endDate = sprint.endDate ?: startDate.plusDays(13)
        val plannedPoints = sprint.plannedPoints ?: issues.sumOf { it.storyPoints ?: 0 }
        val sprintLengthDays = ChronoUnit.DAYS.between(startDate, endDate).toInt().coerceAtLeast(1)
        val today = LocalDate.now()
        val openIssuePoints = issues.filter { it.status.category != StatusCategory.DONE }.sumOf { it.storyPoints ?: 0 }
        val days = mutableListOf<BurndownDay>()
        var date = startDate
        while (!date.isAfter(endDate)) {
            val dayIndex = ChronoUnit.DAYS.between(startDate, date).toInt()
            val idealPoints = (plannedPoints * (sprintLengthDays - dayIndex).toDouble() / sprintLengthDays).toInt()
            val remainingPoints = if (date.isAfter(today)) {
                openIssuePoints
            } else {
                val endOfDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
                issues.filter { issue ->
                    !(issue.status.category == StatusCategory.DONE && issue.updatedAt!!.isBefore(endOfDay))
                }.sumOf { it.storyPoints ?: 0 }
            }
            days.add(BurndownDay(date, idealPoints, remainingPoints))
            date = date.plusDays(1)
        }
        return BurndownResponse(sprintId, days)
    }

    @Transactional(readOnly = true)
    fun getVelocity(projectKey: String, userId: UUID): VelocityResponse {
        val project = projectService.requireMember(projectKey, userId)
        val entries = sprintRepository.findByProjectIdAndStatus(project.id, SprintStatus.CLOSED).map { sprint ->
            VelocityEntry(sprint.id, sprint.name, sprint.plannedPoints ?: 0, sprint.completedPoints ?: 0)
        }
        return VelocityResponse(entries)
    }

    @Transactional(readOnly = true)
    fun getCycleTime(projectKey: String, sprintId: UUID, userId: UUID): CycleTimeResponse {
        val project = projectService.requireMember(projectKey, userId)
        val sprint = sprintRepository.findById(sprintId).orElseThrow { NotFoundException("Sprint not found") }
        if (sprint.project.id != project.id) throw ForbiddenException("Sprint does not belong to this project")
        val issues = issueRepository.findBySprintId(sprintId)
        val issueCycleTimes = issues.map { issue ->
            val activities = activityRepository.findByIssueIdAndTypeOrderByCreatedAtAsc(issue.id, ActivityType.STATUS_CHANGED)
            val inProgressAt = activities.firstOrNull { a ->
                val status = statusRepository.findByName(a.newValue ?: "")
                status?.category == StatusCategory.IN_PROGRESS
            }?.createdAt
            val doneAt = activities.firstOrNull { a ->
                val status = statusRepository.findByName(a.newValue ?: "")
                status?.category == StatusCategory.DONE
            }?.createdAt
            val cycleTimeHours = if (inProgressAt != null && doneAt != null && doneAt.isAfter(inProgressAt)) {
                ChronoUnit.MINUTES.between(inProgressAt, doneAt).toDouble() / 60.0
            } else null
            IssueCycleTime(issue.id, issue.key, cycleTimeHours)
        }
        val validTimes = issueCycleTimes.mapNotNull { it.cycleTimeHours }
        val average = if (validTimes.isEmpty()) null else validTimes.average()
        return CycleTimeResponse(sprintId, issueCycleTimes, average)
    }

    @Transactional(readOnly = true)
    fun getCycleTimeAggregate(projectKey: String, userId: UUID): CycleTimeAggregateResponse {
        val project = projectService.requireMember(projectKey, userId)
        val closedSprints = sprintRepository.findByProjectIdAndStatus(project.id, SprintStatus.CLOSED)
        val sprintCycleTimes = closedSprints.map { sprint ->
            val issues = issueRepository.findBySprintId(sprint.id)
            val validTimes = issues.mapNotNull { issue ->
                val activities = activityRepository.findByIssueIdAndTypeOrderByCreatedAtAsc(issue.id, ActivityType.STATUS_CHANGED)
                val inProgressAt = activities.firstOrNull { a ->
                    statusRepository.findByName(a.newValue ?: "")?.category == StatusCategory.IN_PROGRESS
                }?.createdAt
                val doneAt = activities.firstOrNull { a ->
                    statusRepository.findByName(a.newValue ?: "")?.category == StatusCategory.DONE
                }?.createdAt
                if (inProgressAt != null && doneAt != null && doneAt.isAfter(inProgressAt))
                    ChronoUnit.MINUTES.between(inProgressAt, doneAt).toDouble() / 60.0
                else null
            }
            SprintCycleTime(
                sprintId = sprint.id,
                sprintName = sprint.name,
                averageCycleTimeHours = if (validTimes.isEmpty()) null else validTimes.average()
            )
        }
        return CycleTimeAggregateResponse(sprintCycleTimes)
    }
}
```

- [ ] **Step 6: Add findByName to StatusRepository**

`StatusRepository` lives in `workflows/infrastructure/`. Add the missing method:

Find the file at `backend/src/main/kotlin/com/taskowolf/workflows/infrastructure/StatusRepository.kt` and add:

```kotlin
fun findByName(name: String): WorkflowStatus?
```

(The existing file likely has `findByWorkflowId` — keep those, just add this one method.)

- [ ] **Step 7: Extend ReportsController**

`backend/src/main/kotlin/com/taskowolf/reports/api/ReportsController.kt`:
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

    @GetMapping("/cycle-time")
    fun cycleTime(
        @PathVariable key: String,
        @RequestParam(required = false) sprintId: UUID?,
        @AuthenticationPrincipal user: User
    ) = if (sprintId != null)
        reportsService.getCycleTime(key, sprintId, user.id)
    else
        reportsService.getCycleTimeAggregate(key, user.id)
}
```

- [ ] **Step 8: Run cycle time tests**

```bash
cd backend && ./gradlew test --tests "com.taskowolf.reports.ReportsServiceCycleTimeTest"
# Expected: 5 tests PASSED
```

- [ ] **Step 9: Run full backend test suite**

```bash
cd backend && ./gradlew test
# Expected: all tests PASSED (including existing burndown/velocity tests)
```

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/reports/ \
        backend/src/main/kotlin/com/taskowolf/comments/infrastructure/IssueActivityRepository.kt \
        backend/src/main/kotlin/com/taskowolf/workflows/infrastructure/StatusRepository.kt \
        backend/src/test/kotlin/com/taskowolf/reports/ReportsServiceCycleTimeTest.kt
git commit -m "feat(reports): add Cycle Time report (aggregate + per-sprint) using existing activity log"
```

---

## Task 6: Issue List filter params

**Files:**
- Modify: `backend/src/main/kotlin/com/taskowolf/issues/infrastructure/IssueRepository.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/issues/application/IssueService.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/issues/api/IssueController.kt`

- [ ] **Step 1: Add filtered query methods to IssueRepository**

Add these methods to the existing `IssueRepository` interface (keep all existing methods):

```kotlin
fun findByProjectIdAndAssigneeId(projectId: UUID, assigneeId: UUID, pageable: Pageable): Page<Issue>

@Query("""
    SELECT i FROM Issue i 
    WHERE i.project.id = :projectId 
      AND i.dueDate < CURRENT_DATE 
      AND i.status.category <> com.taskowolf.workflows.domain.StatusCategory.DONE
    ORDER BY i.dueDate ASC
""")
fun findOverdueByProjectId(projectId: UUID, pageable: Pageable): Page<Issue>
```

- [ ] **Step 2: Extend IssueService.findByProject**

Replace `findByProject` in `IssueService` with the extended version. Keep all other methods unchanged:

```kotlin
@Transactional(readOnly = true)
fun findByProject(
    projectKey: String,
    userId: UUID,
    page: Int,
    size: Int,
    assigneeMe: Boolean = false,
    sort: String? = null,
    overdue: Boolean = false
): org.springframework.data.domain.Page<Issue> {
    val project = projectService.requireMember(projectKey, userId)
    val pageable = when (sort) {
        "updatedAt" -> PageRequest.of(page, size, org.springframework.data.domain.Sort.by("updatedAt").descending())
        else -> PageRequest.of(page, size)
    }
    return when {
        overdue -> issueRepository.findOverdueByProjectId(project.id, pageable)
        assigneeMe -> issueRepository.findByProjectIdAndAssigneeId(project.id, userId, pageable)
        else -> issueRepository.findAllByProjectId(project.id, pageable)
    }
}
```

- [ ] **Step 3: Extend IssueController list endpoint**

Replace the `list` handler in `IssueController`:

```kotlin
@GetMapping
fun list(
    @PathVariable key: String,
    @AuthenticationPrincipal user: User,
    @RequestParam(defaultValue = "0") page: Int,
    @RequestParam(defaultValue = "50") size: Int,
    @RequestParam(defaultValue = "false") assigneeMe: Boolean,
    @RequestParam(required = false) sort: String?,
    @RequestParam(defaultValue = "false") overdue: Boolean
) = issueService.findByProject(key, user.id, page, size, assigneeMe, sort, overdue).map { IssueResponse.from(it) }
```

- [ ] **Step 4: Run backend tests to verify nothing broke**

```bash
cd backend && ./gradlew test
# Expected: all tests PASSED
```

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/issues/
git commit -m "feat(issues): add assigneeMe, sort, overdue filter params to list endpoint"
```

---

## Task 7: Frontend setup — install react-grid-layout, route, nav, skeleton page

**Files:**
- Modify: `frontend/package.json` (via npm)
- Create: `frontend/src/pages/project-dashboard/ProjectDashboardPage.tsx`
- Modify: `frontend/src/app/router.tsx`
- Modify: `frontend/src/layouts/AppLayout.tsx`

- [ ] **Step 1: Install react-grid-layout**

```bash
cd frontend && npm install react-grid-layout && npm install --save-dev @types/react-grid-layout
```

- [ ] **Step 2: Create skeleton ProjectDashboardPage**

`frontend/src/pages/project-dashboard/ProjectDashboardPage.tsx`:
```tsx
export function ProjectDashboardPage() {
  return (
    <div className="max-w-7xl">
      <h1 className="text-2xl font-bold mb-6">Dashboard</h1>
      <p className="text-gray-500 text-sm">Dashboard coming soon.</p>
    </div>
  )
}
```

- [ ] **Step 3: Add route to router.tsx**

In `frontend/src/app/router.tsx`, add the import and route:

```tsx
// Add import alongside other page imports:
import { ProjectDashboardPage } from '@/pages/project-dashboard/ProjectDashboardPage'

// Add route inside the RequireAuth children array, alongside the other /p/:key/ routes:
{ path: '/p/:key/dashboard', element: <ProjectDashboardPage /> },
```

- [ ] **Step 4: Add Dashboard nav link to AppLayout**

In the project sub-nav section of `frontend/src/layouts/AppLayout.tsx`, add a Dashboard link as the first item:

```tsx
<NavLink to={`/p/${projectKey}/dashboard`} className={subNavLinkClass}>Dashboard</NavLink>
```

(Insert it before the existing `Board` link.)

- [ ] **Step 5: Verify the page loads in the browser**

```bash
cd frontend && npm run dev
# Open http://localhost:5173, navigate to a project, click "Dashboard"
# Expected: "Dashboard coming soon." text visible
```

- [ ] **Step 6: Commit**

```bash
cd frontend && git add src/pages/project-dashboard/ src/app/router.tsx src/layouts/AppLayout.tsx package.json package-lock.json
git commit -m "feat(dashboard): add ProjectDashboardPage skeleton, route, and nav link"
```

---

## Task 8: Dashboard API hooks + DashboardCanvas + WidgetWrapper

**Files:**
- Create: `frontend/src/hooks/useProjectDashboard.ts`
- Create: `frontend/src/components/dashboard/DashboardCanvas.tsx`
- Create: `frontend/src/components/dashboard/WidgetWrapper.tsx`

- [ ] **Step 1: Create dashboard API hooks**

`frontend/src/hooks/useProjectDashboard.ts`:
```typescript
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/api/client'

export interface WidgetData {
  id: string
  type: string
  config: string | null
  gridX: number
  gridY: number
  gridW: number
  gridH: number
}

export interface DashboardData {
  id: string
  projectId: string
  widgets: WidgetData[]
}

export interface LayoutItem {
  widgetId: string
  gridX: number
  gridY: number
  gridW: number
  gridH: number
}

export interface AddWidgetPayload {
  type: string
  config?: string
  gridX?: number
  gridY?: number
  gridW?: number
  gridH?: number
}

export function useProjectDashboard(projectKey: string) {
  return useQuery<DashboardData>({
    queryKey: ['dashboard', projectKey],
    queryFn: () => apiClient.get(`/projects/${projectKey}/dashboard`).then(r => r.data),
  })
}

export function useSaveDashboardLayout(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (items: LayoutItem[]) =>
      apiClient.put(`/projects/${projectKey}/dashboard/layout`, items).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['dashboard', projectKey] }),
  })
}

export function useAddWidget(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (payload: AddWidgetPayload) =>
      apiClient.post(`/projects/${projectKey}/dashboard/widgets`, payload).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['dashboard', projectKey] }),
  })
}

export function useRemoveWidget(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (widgetId: string) =>
      apiClient.delete(`/projects/${projectKey}/dashboard/widgets/${widgetId}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['dashboard', projectKey] }),
  })
}
```

- [ ] **Step 2: Create WidgetWrapper with error boundary**

`frontend/src/components/dashboard/WidgetWrapper.tsx`:
```tsx
import React from 'react'

interface Props {
  title: string
  widgetId: string
  editMode: boolean
  onRemove: (id: string) => void
  children: React.ReactNode
}

interface State { hasError: boolean }

class ErrorBoundary extends React.Component<{ children: React.ReactNode }, State> {
  state: State = { hasError: false }
  static getDerivedStateFromError() { return { hasError: true } }
  render() {
    if (this.state.hasError)
      return <div className="flex items-center justify-center h-full text-red-400 text-sm">Failed to load widget</div>
    return this.props.children
  }
}

export function WidgetWrapper({ title, widgetId, editMode, onRemove, children }: Props) {
  return (
    <div className="bg-gray-900 border border-gray-800 rounded-lg flex flex-col h-full overflow-hidden">
      <div className="flex items-center justify-between px-4 py-2 border-b border-gray-800 shrink-0">
        <span className="text-sm font-semibold text-gray-300">{title}</span>
        {editMode && (
          <button
            onClick={() => onRemove(widgetId)}
            className="text-gray-500 hover:text-red-400 text-xs ml-2"
          >
            ✕
          </button>
        )}
      </div>
      <div className="flex-1 min-h-0 p-3">
        <ErrorBoundary>{children}</ErrorBoundary>
      </div>
    </div>
  )
}
```

- [ ] **Step 3: Create DashboardCanvas**

`frontend/src/components/dashboard/DashboardCanvas.tsx`:
```tsx
import ReactGridLayout, { Layout } from 'react-grid-layout'
import 'react-grid-layout/css/styles.css'
import 'react-resizable/css/styles.css'
import { WidgetData } from '@/hooks/useProjectDashboard'

interface Props {
  widgets: WidgetData[]
  editMode: boolean
  onLayoutChange: (layout: Layout[]) => void
  renderWidget: (widget: WidgetData) => React.ReactNode
}

export function DashboardCanvas({ widgets, editMode, onLayoutChange, renderWidget }: Props) {
  const layout: Layout[] = widgets.map(w => ({
    i: w.id,
    x: w.gridX,
    y: w.gridY,
    w: w.gridW,
    h: w.gridH,
  }))

  return (
    <ReactGridLayout
      className="layout"
      layout={layout}
      cols={12}
      rowHeight={60}
      width={1200}
      isDraggable={editMode}
      isResizable={editMode}
      onLayoutChange={onLayoutChange}
      draggableHandle=".drag-handle"
    >
      {widgets.map(w => (
        <div key={w.id} className="drag-handle cursor-grab active:cursor-grabbing">
          {renderWidget(w)}
        </div>
      ))}
    </ReactGridLayout>
  )
}
```

- [ ] **Step 4: Commit**

```bash
cd frontend && git add src/hooks/useProjectDashboard.ts src/components/dashboard/DashboardCanvas.tsx src/components/dashboard/WidgetWrapper.tsx
git commit -m "feat(dashboard): add DashboardCanvas, WidgetWrapper, and dashboard hooks"
```

---

## Task 9: Chart widgets (Burndown, Velocity, Cycle Time)

**Files:**
- Create: `frontend/src/hooks/useCycleTime.ts`
- Create: `frontend/src/components/dashboard/BurndownWidget.tsx`
- Create: `frontend/src/components/dashboard/VelocityWidget.tsx`
- Create: `frontend/src/components/dashboard/CycleTimeWidget.tsx`

- [ ] **Step 1: Create cycle time hooks**

`frontend/src/hooks/useCycleTime.ts`:
```typescript
import { useQuery } from '@tanstack/react-query'
import { apiClient } from '@/api/client'

export interface SprintCycleTime {
  sprintId: string
  sprintName: string
  averageCycleTimeHours: number | null
}

export interface CycleTimeAggregateData {
  sprints: SprintCycleTime[]
}

export function useCycleTimeAggregate(projectKey: string) {
  return useQuery<CycleTimeAggregateData>({
    queryKey: ['cycleTimeAggregate', projectKey],
    queryFn: () => apiClient.get(`/projects/${projectKey}/reports/cycle-time`).then(r => r.data),
  })
}
```

- [ ] **Step 2: Create BurndownWidget**

`frontend/src/components/dashboard/BurndownWidget.tsx`:
```tsx
import { useState } from 'react'
import { useSprints } from '@/hooks/useSprints'
import { useBurndown } from '@/hooks/useReports'
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts'

interface Props { projectKey: string; config: string | null }

export function BurndownWidget({ projectKey, config }: Props) {
  const { data: sprints } = useSprints(projectKey)
  const parsed = config ? JSON.parse(config) : {}
  const defaultSprintId = parsed.sprintId ?? sprints?.find(s => s.status === 'ACTIVE')?.id ?? sprints?.filter(s => s.status === 'CLOSED').slice(-1)[0]?.id ?? null
  const [sprintId, setSprintId] = useState<string | null>(null)
  const activeId = sprintId ?? defaultSprintId
  const { data: burndown } = useBurndown(projectKey, activeId)

  const chartData = burndown?.days.map(d => ({
    date: d.date.slice(5),
    Ideal: d.idealPoints,
    Actual: d.remainingPoints,
  })) ?? []

  const selectable = sprints?.filter(s => s.status !== 'PLANNED') ?? []

  return (
    <div className="h-full flex flex-col gap-2">
      {selectable.length > 0 && (
        <select
          value={activeId ?? ''}
          onChange={e => setSprintId(e.target.value)}
          className="bg-gray-800 border border-gray-700 text-xs text-white rounded px-2 py-1 self-start"
        >
          {selectable.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
        </select>
      )}
      {chartData.length === 0
        ? <p className="text-gray-500 text-xs">No burndown data.</p>
        : (
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={chartData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
              <XAxis dataKey="date" stroke="#6b7280" tick={{ fontSize: 10 }} />
              <YAxis stroke="#6b7280" tick={{ fontSize: 10 }} />
              <Tooltip contentStyle={{ backgroundColor: '#1f2937', border: '1px solid #374151', borderRadius: '6px' }} />
              <Legend />
              <Line type="monotone" dataKey="Ideal" stroke="#6b7280" strokeDasharray="5 5" dot={false} />
              <Line type="monotone" dataKey="Actual" stroke="#3b82f6" strokeWidth={2} dot={false} />
            </LineChart>
          </ResponsiveContainer>
        )}
    </div>
  )
}
```

- [ ] **Step 3: Create VelocityWidget**

`frontend/src/components/dashboard/VelocityWidget.tsx`:
```tsx
import { useVelocity } from '@/hooks/useReports'
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts'

interface Props { projectKey: string }

export function VelocityWidget({ projectKey }: Props) {
  const { data: velocity } = useVelocity(projectKey)

  const chartData = velocity?.entries.map(e => ({
    name: e.sprintName,
    Planned: e.plannedPoints,
    Completed: e.completedPoints,
  })) ?? []

  if (chartData.length === 0) return <p className="text-gray-500 text-xs">No completed sprints yet.</p>

  return (
    <ResponsiveContainer width="100%" height="100%">
      <BarChart data={chartData}>
        <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
        <XAxis dataKey="name" stroke="#6b7280" tick={{ fontSize: 10 }} />
        <YAxis stroke="#6b7280" tick={{ fontSize: 10 }} />
        <Tooltip contentStyle={{ backgroundColor: '#1f2937', border: '1px solid #374151', borderRadius: '6px' }} />
        <Legend />
        <Bar dataKey="Planned" fill="#374151" radius={[3, 3, 0, 0]} />
        <Bar dataKey="Completed" fill="#3b82f6" radius={[3, 3, 0, 0]} />
      </BarChart>
    </ResponsiveContainer>
  )
}
```

- [ ] **Step 4: Create CycleTimeWidget**

`frontend/src/components/dashboard/CycleTimeWidget.tsx`:
```tsx
import { useCycleTimeAggregate } from '@/hooks/useCycleTime'
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts'

interface Props { projectKey: string }

export function CycleTimeWidget({ projectKey }: Props) {
  const { data } = useCycleTimeAggregate(projectKey)

  const chartData = data?.sprints
    .filter(s => s.averageCycleTimeHours != null)
    .map(s => ({
      name: s.sprintName,
      Hours: Math.round((s.averageCycleTimeHours ?? 0) * 10) / 10,
    })) ?? []

  if (chartData.length === 0) return <p className="text-gray-500 text-xs">No cycle time data yet.</p>

  return (
    <ResponsiveContainer width="100%" height="100%">
      <BarChart data={chartData}>
        <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
        <XAxis dataKey="name" stroke="#6b7280" tick={{ fontSize: 10 }} />
        <YAxis stroke="#6b7280" tick={{ fontSize: 10 }} label={{ value: 'Hours', angle: -90, position: 'insideLeft', fill: '#6b7280', fontSize: 10 }} />
        <Tooltip
          contentStyle={{ backgroundColor: '#1f2937', border: '1px solid #374151', borderRadius: '6px' }}
          formatter={(v: number) => [`${v}h`, 'Avg Cycle Time']}
        />
        <Bar dataKey="Hours" fill="#8b5cf6" radius={[3, 3, 0, 0]} />
      </BarChart>
    </ResponsiveContainer>
  )
}
```

- [ ] **Step 5: Commit**

```bash
cd frontend && git add src/hooks/useCycleTime.ts src/components/dashboard/BurndownWidget.tsx src/components/dashboard/VelocityWidget.tsx src/components/dashboard/CycleTimeWidget.tsx
git commit -m "feat(dashboard): add BurndownWidget, VelocityWidget, and CycleTimeWidget"
```

---

## Task 10: Stat widgets and Issue List widget

**Files:**
- Create: `frontend/src/components/dashboard/IssueCountWidget.tsx`
- Create: `frontend/src/components/dashboard/IssuesByStatusWidget.tsx`
- Create: `frontend/src/components/dashboard/IssueListWidget.tsx`

The stat widgets use the existing issues query. Check whether `useIssues` (or similar hook) exists in `frontend/src/hooks/useIssues.ts` — use whatever hook the codebase already has for `GET /projects/{key}/issues`.

- [ ] **Step 1: Identify the existing issues hook**

```bash
cat frontend/src/hooks/useIssues.ts
# Note the hook name and what it returns — you'll use it in the widgets below
```

- [ ] **Step 2: Create IssueCountWidget**

`frontend/src/components/dashboard/IssueCountWidget.tsx`:
```tsx
import { useIssues } from '@/hooks/useIssues'

interface Props { projectKey: string }

export function IssueCountWidget({ projectKey }: Props) {
  const { data } = useIssues(projectKey)
  const total = data?.totalElements ?? data?.content?.length ?? 0

  return (
    <div className="flex flex-col items-center justify-center h-full gap-1">
      <span className="text-4xl font-bold text-white">{total}</span>
      <span className="text-xs text-gray-400">Open Issues</span>
    </div>
  )
}
```

> Note: adapt `data?.totalElements` to whatever the issues API response shape is (check `IssueResponse.from()` and the controller return type).

- [ ] **Step 3: Create IssuesByStatusWidget**

`frontend/src/components/dashboard/IssuesByStatusWidget.tsx`:
```tsx
import { useIssues } from '@/hooks/useIssues'

interface Props { projectKey: string }

const CATEGORY_LABEL: Record<string, string> = {
  TODO: 'To Do',
  IN_PROGRESS: 'In Progress',
  DONE: 'Done',
}

const CATEGORY_COLOR: Record<string, string> = {
  TODO: 'text-gray-400',
  IN_PROGRESS: 'text-blue-400',
  DONE: 'text-green-400',
}

export function IssuesByStatusWidget({ projectKey }: Props) {
  const { data } = useIssues(projectKey)
  const issues = data?.content ?? []

  const counts = issues.reduce<Record<string, number>>((acc, issue) => {
    const cat = issue.statusCategory ?? 'TODO'
    acc[cat] = (acc[cat] ?? 0) + 1
    return acc
  }, {})

  return (
    <div className="flex gap-4 items-center justify-center h-full">
      {['TODO', 'IN_PROGRESS', 'DONE'].map(cat => (
        <div key={cat} className="flex flex-col items-center gap-1">
          <span className={`text-3xl font-bold ${CATEGORY_COLOR[cat]}`}>{counts[cat] ?? 0}</span>
          <span className="text-xs text-gray-500">{CATEGORY_LABEL[cat]}</span>
        </div>
      ))}
    </div>
  )
}
```

> Note: `issue.statusCategory` must be present in the `IssueResponse` — check the existing DTO. If the field is named differently (e.g. `status.category`), adapt accordingly.

- [ ] **Step 4: Create IssueListWidget**

`frontend/src/components/dashboard/IssueListWidget.tsx`:
```tsx
import { useQuery } from '@tanstack/react-query'
import { apiClient } from '@/api/client'
import { Link } from 'react-router-dom'

interface Props {
  projectKey: string
  config: string | null
}

type FilterMode = 'MY_OPEN' | 'RECENTLY_UPDATED' | 'OVERDUE'

const FILTER_LABELS: Record<FilterMode, string> = {
  MY_OPEN: 'My Open Issues',
  RECENTLY_UPDATED: 'Recently Updated',
  OVERDUE: 'Overdue',
}

function buildParams(filter: FilterMode) {
  switch (filter) {
    case 'MY_OPEN': return '?assigneeMe=true&size=20'
    case 'RECENTLY_UPDATED': return '?sort=updatedAt&size=20'
    case 'OVERDUE': return '?overdue=true&size=20'
  }
}

export function IssueListWidget({ projectKey, config }: Props) {
  const parsed = config ? JSON.parse(config) : {}
  const filter: FilterMode = parsed.filter ?? 'MY_OPEN'

  const { data, isLoading } = useQuery({
    queryKey: ['issueList', projectKey, filter],
    queryFn: () => apiClient.get(`/projects/${projectKey}/issues${buildParams(filter)}`).then(r => r.data),
  })

  const issues = data?.content ?? []

  return (
    <div className="flex flex-col gap-1 overflow-y-auto h-full">
      <p className="text-xs font-semibold text-gray-400 mb-1">{FILTER_LABELS[filter]}</p>
      {isLoading && <p className="text-gray-500 text-xs">Loading...</p>}
      {!isLoading && issues.length === 0 && <p className="text-gray-500 text-xs">No issues.</p>}
      {issues.map((issue: { id: string; key: string; title: string }) => (
        <Link
          key={issue.id}
          to={`/p/${projectKey}/issues/${issue.key}`}
          className="text-xs text-gray-300 hover:text-white hover:underline truncate"
        >
          <span className="text-gray-500 mr-1">{issue.key}</span>{issue.title}
        </Link>
      ))}
    </div>
  )
}
```

- [ ] **Step 5: Commit**

```bash
cd frontend && git add src/components/dashboard/IssueCountWidget.tsx src/components/dashboard/IssuesByStatusWidget.tsx src/components/dashboard/IssueListWidget.tsx
git commit -m "feat(dashboard): add IssueCountWidget, IssuesByStatusWidget, IssueListWidget"
```

---

## Task 11: WidgetPalette + wire up ProjectDashboardPage

**Files:**
- Create: `frontend/src/components/dashboard/WidgetPalette.tsx`
- Modify: `frontend/src/pages/project-dashboard/ProjectDashboardPage.tsx`

- [ ] **Step 1: Create WidgetPalette**

`frontend/src/components/dashboard/WidgetPalette.tsx`:
```tsx
import { useState } from 'react'

const WIDGET_OPTIONS = [
  { type: 'BURNDOWN',         label: 'Burndown Chart',    defaultW: 6, defaultH: 5 },
  { type: 'VELOCITY',         label: 'Velocity Chart',    defaultW: 6, defaultH: 5 },
  { type: 'CYCLE_TIME',       label: 'Cycle Time Chart',  defaultW: 6, defaultH: 5 },
  { type: 'ISSUE_COUNT',      label: 'Open Issue Count',  defaultW: 3, defaultH: 3 },
  { type: 'ISSUES_BY_STATUS', label: 'Issues by Status',  defaultW: 6, defaultH: 3 },
  { type: 'ISSUE_LIST',       label: 'Issue List',        defaultW: 4, defaultH: 6 },
]

const ISSUE_LIST_FILTERS = [
  { value: 'MY_OPEN',           label: 'My Open Issues' },
  { value: 'RECENTLY_UPDATED',  label: 'Recently Updated' },
  { value: 'OVERDUE',           label: 'Overdue' },
]

interface Props {
  onAdd: (type: string, config: string | undefined, w: number, h: number) => void
  onClose: () => void
}

export function WidgetPalette({ onAdd, onClose }: Props) {
  const [issueFilter, setIssueFilter] = useState('MY_OPEN')

  return (
    <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50" onClick={onClose}>
      <div
        className="bg-gray-900 border border-gray-700 rounded-lg p-6 w-96 shadow-xl"
        onClick={e => e.stopPropagation()}
      >
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-semibold">Add Widget</h2>
          <button onClick={onClose} className="text-gray-500 hover:text-white">✕</button>
        </div>
        <div className="flex flex-col gap-2">
          {WIDGET_OPTIONS.map(opt => (
            <div key={opt.type} className="flex flex-col gap-1">
              {opt.type === 'ISSUE_LIST' && (
                <select
                  value={issueFilter}
                  onChange={e => setIssueFilter(e.target.value)}
                  className="bg-gray-800 border border-gray-700 text-xs text-white rounded px-2 py-1"
                >
                  {ISSUE_LIST_FILTERS.map(f => (
                    <option key={f.value} value={f.value}>{f.label}</option>
                  ))}
                </select>
              )}
              <button
                onClick={() => {
                  const config = opt.type === 'ISSUE_LIST'
                    ? JSON.stringify({ filter: issueFilter })
                    : undefined
                  onAdd(opt.type, config, opt.defaultW, opt.defaultH)
                  onClose()
                }}
                className="w-full text-left px-4 py-2 rounded bg-gray-800 hover:bg-gray-700 text-sm text-white"
              >
                {opt.label}
              </button>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Wire up the full ProjectDashboardPage**

`frontend/src/pages/project-dashboard/ProjectDashboardPage.tsx`:
```tsx
import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { Layout } from 'react-grid-layout'
import { useProjectDashboard, useSaveDashboardLayout, useAddWidget, useRemoveWidget, WidgetData } from '@/hooks/useProjectDashboard'
import { DashboardCanvas } from '@/components/dashboard/DashboardCanvas'
import { WidgetWrapper } from '@/components/dashboard/WidgetWrapper'
import { WidgetPalette } from '@/components/dashboard/WidgetPalette'
import { BurndownWidget } from '@/components/dashboard/BurndownWidget'
import { VelocityWidget } from '@/components/dashboard/VelocityWidget'
import { CycleTimeWidget } from '@/components/dashboard/CycleTimeWidget'
import { IssueCountWidget } from '@/components/dashboard/IssueCountWidget'
import { IssuesByStatusWidget } from '@/components/dashboard/IssuesByStatusWidget'
import { IssueListWidget } from '@/components/dashboard/IssueListWidget'

const WIDGET_TITLES: Record<string, string> = {
  BURNDOWN:         'Burndown',
  VELOCITY:         'Velocity',
  CYCLE_TIME:       'Cycle Time',
  ISSUE_COUNT:      'Open Issues',
  ISSUES_BY_STATUS: 'Issues by Status',
  ISSUE_LIST:       'Issue List',
}

export function ProjectDashboardPage() {
  const { key } = useParams<{ key: string }>()
  const projectKey = key!
  const [editMode, setEditMode] = useState(false)
  const [showPalette, setShowPalette] = useState(false)
  const [pendingLayout, setPendingLayout] = useState<Layout[] | null>(null)

  const { data: dashboard, isLoading } = useProjectDashboard(projectKey)
  const saveLayout = useSaveDashboardLayout(projectKey)
  const addWidget = useAddWidget(projectKey)
  const removeWidget = useRemoveWidget(projectKey)

  function renderWidget(widget: WidgetData) {
    const inner = (() => {
      switch (widget.type) {
        case 'BURNDOWN':         return <BurndownWidget projectKey={projectKey} config={widget.config} />
        case 'VELOCITY':         return <VelocityWidget projectKey={projectKey} />
        case 'CYCLE_TIME':       return <CycleTimeWidget projectKey={projectKey} />
        case 'ISSUE_COUNT':      return <IssueCountWidget projectKey={projectKey} />
        case 'ISSUES_BY_STATUS': return <IssuesByStatusWidget projectKey={projectKey} />
        case 'ISSUE_LIST':       return <IssueListWidget projectKey={projectKey} config={widget.config} />
        default:                 return <p className="text-gray-500 text-xs">Unknown widget type: {widget.type}</p>
      }
    })()

    return (
      <WidgetWrapper
        key={widget.id}
        title={WIDGET_TITLES[widget.type] ?? widget.type}
        widgetId={widget.id}
        editMode={editMode}
        onRemove={id => removeWidget.mutate(id)}
      >
        {inner}
      </WidgetWrapper>
    )
  }

  function handleSave() {
    if (!pendingLayout) { setEditMode(false); return }
    const items = pendingLayout.map(l => ({
      widgetId: l.i,
      gridX: l.x,
      gridY: l.y,
      gridW: l.w,
      gridH: l.h,
    }))
    saveLayout.mutate(items, { onSuccess: () => { setEditMode(false); setPendingLayout(null) } })
  }

  if (isLoading) return <p className="text-gray-500 text-sm">Loading dashboard...</p>

  return (
    <div className="max-w-7xl">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">Dashboard</h1>
        <div className="flex gap-2">
          {editMode && (
            <>
              <button
                onClick={() => setShowPalette(true)}
                className="px-3 py-1.5 text-sm bg-gray-700 hover:bg-gray-600 text-white rounded"
              >
                + Add Widget
              </button>
              <button
                onClick={handleSave}
                className="px-3 py-1.5 text-sm bg-indigo-600 hover:bg-indigo-500 text-white rounded"
              >
                {saveLayout.isPending ? 'Saving...' : 'Save'}
              </button>
              <button
                onClick={() => { setEditMode(false); setPendingLayout(null) }}
                className="px-3 py-1.5 text-sm text-gray-400 hover:text-white"
              >
                Cancel
              </button>
            </>
          )}
          {!editMode && (
            <button
              onClick={() => setEditMode(true)}
              className="px-3 py-1.5 text-sm bg-gray-700 hover:bg-gray-600 text-white rounded"
            >
              Edit
            </button>
          )}
        </div>
      </div>

      {dashboard?.widgets.length === 0 && !editMode && (
        <div className="flex flex-col items-center justify-center py-20 text-center">
          <p className="text-gray-400 mb-3">No widgets yet.</p>
          <button
            onClick={() => setEditMode(true)}
            className="px-4 py-2 text-sm bg-indigo-600 hover:bg-indigo-500 text-white rounded"
          >
            Add your first widget
          </button>
        </div>
      )}

      {(dashboard?.widgets.length ?? 0) > 0 && (
        <DashboardCanvas
          widgets={dashboard!.widgets}
          editMode={editMode}
          onLayoutChange={layout => setPendingLayout(layout)}
          renderWidget={renderWidget}
        />
      )}

      {showPalette && (
        <WidgetPalette
          onAdd={(type, config, w, h) => addWidget.mutate({ type, config, gridX: 0, gridY: 9999, gridW: w, gridH: h })}
          onClose={() => setShowPalette(false)}
        />
      )}
    </div>
  )
}
```

- [ ] **Step 3: Test the full dashboard in the browser**

```bash
cd frontend && npm run dev
```

1. Navigate to a project → click "Dashboard" in sidebar
2. Click "Edit" → click "+ Add Widget" → add a Velocity widget → click "Save"
3. Click "Edit" again → drag the widget to a new position → click "Save"
4. Click "Edit" → click ✕ on the widget → verify it disappears from the dashboard
5. Add an "Issue List" widget with "My Open Issues" filter → verify it shows issues

- [ ] **Step 4: Run the full backend test suite one final time**

```bash
cd backend && ./gradlew test
# Expected: all tests PASSED
```

- [ ] **Step 5: Final commit**

```bash
cd frontend && git add src/components/dashboard/WidgetPalette.tsx src/pages/project-dashboard/ProjectDashboardPage.tsx
git commit -m "feat(dashboard): wire up full ProjectDashboardPage with WidgetPalette and all widget types"
```
