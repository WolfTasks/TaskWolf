package com.taskowolf.sprints.api.dto

import com.taskowolf.sprints.domain.Sprint
import java.time.LocalDate
import java.util.UUID

data class SprintResponse(
    val id: UUID,
    val name: String,
    val goal: String?,
    val status: String,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val plannedPoints: Int?,
    val completedPoints: Int?,
    val projectId: UUID
) {
    companion object {
        fun from(s: Sprint) = SprintResponse(
            s.id, s.name, s.goal, s.status.name,
            s.startDate, s.endDate, s.plannedPoints, s.completedPoints, s.project.id
        )
    }
}
