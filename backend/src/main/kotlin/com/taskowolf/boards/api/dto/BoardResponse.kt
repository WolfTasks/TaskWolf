package com.taskowolf.boards.api.dto

import com.taskowolf.issues.api.dto.IssueResponse
import java.time.LocalDate
import java.util.UUID

data class StatusSummary(val id: UUID, val name: String, val category: String, val color: String)

data class BoardSprintSummary(
    val id: UUID, val name: String, val goal: String?,
    val startDate: LocalDate?, val endDate: LocalDate?,
    val daysRemaining: Long?, val totalPoints: Int?, val completedPoints: Int
)

data class BoardColumnResponse(val status: StatusSummary, val issues: List<IssueResponse>)

data class BoardResponse(val sprint: BoardSprintSummary, val columns: List<BoardColumnResponse>)
