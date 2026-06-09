package com.taskowolf.reports.api.dto

import java.util.UUID

data class VelocityEntry(val sprintId: UUID, val sprintName: String, val plannedPoints: Int, val completedPoints: Int)

data class VelocityResponse(val entries: List<VelocityEntry>)
