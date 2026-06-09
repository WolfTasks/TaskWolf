package com.taskowolf.boards.application

import com.taskowolf.boards.api.dto.*
import com.taskowolf.issues.api.dto.IssueResponse
import com.taskowolf.issues.infrastructure.IssueRepository
import com.taskowolf.core.infrastructure.NotFoundException
import com.taskowolf.projects.application.ProjectService
import com.taskowolf.sprints.api.dto.SprintResponse
import com.taskowolf.sprints.domain.SprintStatus
import com.taskowolf.sprints.infrastructure.SprintRepository
import com.taskowolf.workflows.domain.StatusCategory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class BoardService(
    private val projectService: ProjectService,
    private val sprintRepository: SprintRepository,
    private val issueRepository: IssueRepository
) {
    fun getBoard(projectKey: String, userId: UUID): BoardResponse? {
        val project = projectService.requireMember(projectKey, userId)
        val sprint = sprintRepository.findByProjectIdAndStatus(project.id, SprintStatus.ACTIVE)
            .firstOrNull() ?: return null
        val workflow = project.workflow ?: throw NotFoundException("Project has no workflow")
        val issues = issueRepository.findBySprintId(sprint.id)
        val issuesByStatus = issues.groupBy { it.status.id }
        val columns = workflow.statuses.map { status ->
            BoardColumnResponse(
                status = StatusSummary(status.id, status.name, status.category.name, status.color),
                issues = (issuesByStatus[status.id] ?: emptyList()).map { IssueResponse.from(it) }
            )
        }
        val completedPoints = issues.filter { it.status.category == StatusCategory.DONE }
            .sumOf { it.storyPoints ?: 0 }
        val daysRemaining = sprint.endDate?.let { ChronoUnit.DAYS.between(LocalDate.now(), it).coerceAtLeast(0) }
        return BoardResponse(
            sprint = BoardSprintSummary(
                sprint.id, sprint.name, sprint.goal,
                sprint.startDate, sprint.endDate,
                daysRemaining, sprint.plannedPoints, completedPoints
            ),
            columns = columns
        )
    }

    fun getBacklog(projectKey: String, userId: UUID): BacklogResponse {
        val project = projectService.requireMember(projectKey, userId)
        val plannedSprints = sprintRepository.findByProjectIdAndStatus(project.id, SprintStatus.PLANNED)
        val sprintEntries = plannedSprints.map { sprint ->
            val issues = issueRepository.findBySprintId(sprint.id)
            val totalPoints = issues.sumOf { it.storyPoints ?: 0 }
            BacklogSprintEntry(
                sprint = SprintResponse.from(sprint),
                issues = issues.map { IssueResponse.from(it) },
                totalPoints = totalPoints
            )
        }
        val backlogIssues = issueRepository.findByProjectIdAndSprintIsNull(project.id)
            .map { IssueResponse.from(it) }
        return BacklogResponse(sprintEntries, backlogIssues)
    }
}
