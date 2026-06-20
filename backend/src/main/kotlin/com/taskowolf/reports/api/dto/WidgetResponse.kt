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
