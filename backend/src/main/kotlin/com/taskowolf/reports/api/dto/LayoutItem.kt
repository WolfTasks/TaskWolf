package com.taskowolf.reports.api.dto

import java.util.UUID

data class LayoutItem(
    val widgetId: UUID,
    val gridX: Int,
    val gridY: Int,
    val gridW: Int,
    val gridH: Int
)
