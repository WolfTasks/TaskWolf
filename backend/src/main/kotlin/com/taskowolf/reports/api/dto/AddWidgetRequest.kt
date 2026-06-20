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
