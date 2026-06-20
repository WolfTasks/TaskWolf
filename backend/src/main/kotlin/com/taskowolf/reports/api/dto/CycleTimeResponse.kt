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
