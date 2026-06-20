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
