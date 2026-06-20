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
