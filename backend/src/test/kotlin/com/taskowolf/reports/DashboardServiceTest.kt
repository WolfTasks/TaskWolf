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
        every { dashboardRepository.findByProjectId(project.id) } returns null

        val result = service.getDashboard("WOLF", userId)

        verify { dashboardRepository.save(any<Dashboard>()) }
        assertEquals(project.id, result.projectId)
        assertTrue(result.widgets.isEmpty())
    }

    @Test
    fun `getDashboard returns existing dashboard`() {
        val dashboard = Dashboard(projectId = project.id)
        every { projectService.requireMember("WOLF", userId) } returns project
        every { dashboardRepository.findByProjectId(project.id) } returns dashboard

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
        every { dashboardRepository.findByProjectId(project.id) } returns dashboard

        val request = AddWidgetRequest(type = WidgetType.VELOCITY, gridX = 2, gridY = 0, gridW = 6, gridH = 4)
        service.addWidget("WOLF", request, userId)

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
        every { dashboardRepository.findByProjectId(project.id) } returns dashboard
        every { dashboardWidgetRepository.findById(otherWidgetId) } returns java.util.Optional.empty()

        assertThrows<NotFoundException> {
            service.removeWidget("WOLF", otherWidgetId, userId)
        }
    }

    @Test
    fun `saveLayout updates grid positions of existing widgets`() {
        val dashboard = Dashboard(projectId = project.id)
        val widget = DashboardWidget(dashboard = dashboard, type = WidgetType.BURNDOWN)
        every { projectService.requireAdmin("WOLF", userId) } returns project
        every { dashboardRepository.findByProjectId(project.id) } returns dashboard
        every { dashboardWidgetRepository.findById(widget.id) } returns java.util.Optional.of(widget)

        val result = service.saveLayout("WOLF", listOf(LayoutItem(widgetId = widget.id, gridX = 3, gridY = 1, gridW = 6, gridH = 5)), userId)

        assertEquals(3, widget.gridX)
        assertEquals(1, widget.gridY)
        assertEquals(6, widget.gridW)
        assertEquals(5, widget.gridH)
        // Also verify the response reflects what the API caller receives
        assertEquals(dashboard.id, result.id)
        assertEquals(project.id, result.projectId)
    }

    @Test
    fun `saveLayout throws NotFoundException when widget belongs to different dashboard`() {
        val dashboard = Dashboard(projectId = project.id)
        val otherDashboard = Dashboard(projectId = UUID.randomUUID())
        val widget = DashboardWidget(dashboard = otherDashboard, type = WidgetType.BURNDOWN)
        every { projectService.requireAdmin("WOLF", userId) } returns project
        every { dashboardRepository.findByProjectId(project.id) } returns dashboard
        every { dashboardWidgetRepository.findById(widget.id) } returns java.util.Optional.of(widget)

        assertThrows<NotFoundException> {
            service.saveLayout("WOLF", listOf(LayoutItem(widgetId = widget.id, gridX = 1, gridY = 0, gridW = 4, gridH = 4)), userId)
        }
    }

    @Test
    fun `getDashboard is accessible to any project member, not just admins`() {
        val member = User(email = "member@test.com", displayName = "Member")
        val memberDashboard = Dashboard(projectId = project.id)
        // requireMember succeeds (returns project) for any member
        every { projectService.requireMember("WOLF", member.id) } returns project
        every { dashboardRepository.findByProjectId(project.id) } returns memberDashboard

        val result = service.getDashboard("WOLF", member.id)

        // Member can read the dashboard — no ForbiddenException
        assertEquals(memberDashboard.id, result.id)
    }
}
