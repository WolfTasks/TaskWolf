package com.taskowolf.reports.application

import com.taskowolf.core.infrastructure.ForbiddenException
import com.taskowolf.core.infrastructure.NotFoundException
import com.taskowolf.issues.infrastructure.IssueRepository
import com.taskowolf.projects.application.ProjectService
import com.taskowolf.reports.api.dto.*
import com.taskowolf.sprints.domain.SprintStatus
import com.taskowolf.sprints.infrastructure.SprintRepository
import com.taskowolf.workflows.domain.StatusCategory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class ReportsService(
    private val projectService: ProjectService,
    private val sprintRepository: SprintRepository,
    private val issueRepository: IssueRepository
) {
    @Transactional(readOnly = true)
    fun getBurndown(projectKey: String, sprintId: UUID, userId: UUID): BurndownResponse {
        val project = projectService.requireMember(projectKey, userId)
        val sprint = sprintRepository.findById(sprintId).orElseThrow { NotFoundException("Sprint not found") }
        if (sprint.project.id != project.id) throw ForbiddenException("Sprint does not belong to this project")
        val issues = issueRepository.findBySprintId(sprintId)
        val startDate = sprint.startDate ?: return BurndownResponse(sprintId, emptyList())
        val endDate = sprint.endDate ?: startDate.plusDays(13)
        val plannedPoints = sprint.plannedPoints ?: issues.sumOf { it.storyPoints ?: 0 }
        val sprintLengthDays = ChronoUnit.DAYS.between(startDate, endDate).toInt().coerceAtLeast(1)
        val today = LocalDate.now()
        val openIssuePoints = issues.filter { it.status.category != StatusCategory.DONE }.sumOf { it.storyPoints ?: 0 }
        val days = mutableListOf<BurndownDay>()
        var date = startDate
        while (!date.isAfter(endDate)) {
            val dayIndex = ChronoUnit.DAYS.between(startDate, date).toInt()
            val idealPoints = (plannedPoints * (sprintLengthDays - dayIndex).toDouble() / sprintLengthDays).toInt()
            val remainingPoints = if (date.isAfter(today)) {
                openIssuePoints
            } else {
                val endOfDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
                issues.filter { issue ->
                    !(issue.status.category == StatusCategory.DONE && issue.updatedAt.isBefore(endOfDay))
                }.sumOf { it.storyPoints ?: 0 }
            }
            days.add(BurndownDay(date, idealPoints, remainingPoints))
            date = date.plusDays(1)
        }
        return BurndownResponse(sprintId, days)
    }

    @Transactional(readOnly = true)
    fun getVelocity(projectKey: String, userId: UUID): VelocityResponse {
        val project = projectService.requireMember(projectKey, userId)
        val entries = sprintRepository.findByProjectIdAndStatus(project.id, SprintStatus.CLOSED).map { sprint ->
            VelocityEntry(sprint.id, sprint.name, sprint.plannedPoints ?: 0, sprint.completedPoints ?: 0)
        }
        return VelocityResponse(entries)
    }
}
