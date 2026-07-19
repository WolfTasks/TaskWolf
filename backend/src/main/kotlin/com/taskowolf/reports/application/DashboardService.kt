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
            ?: dashboardRepository.save(Dashboard(projectId = project.id))
        return DashboardResponse.from(dashboard)
    }

    @Transactional
    fun saveLayout(projectKey: String, items: List<LayoutItem>, userId: UUID): DashboardResponse {
        val project = projectService.requireAdmin(projectKey, userId)
        val dashboard = dashboardRepository.findByProjectId(project.id)
            ?: throw NotFoundException.keyed("report.dashboardNotFound")
        items.forEach { item ->
            val widget = dashboardWidgetRepository.findById(item.widgetId)
                .filter { it.dashboard.id == dashboard.id }
                .orElseThrow { NotFoundException.keyed("report.widgetNotFound", item.widgetId) }
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
            ?: dashboardRepository.save(Dashboard(projectId = project.id))
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
            ?: throw NotFoundException.keyed("report.dashboardNotFound")
        val widget = dashboardWidgetRepository.findById(widgetId)
            .filter { it.dashboard.id == dashboard.id }
            .orElseThrow { NotFoundException.keyed("report.widgetNotFound", widgetId) }
        dashboardWidgetRepository.delete(widget)
    }
}
