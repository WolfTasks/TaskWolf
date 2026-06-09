package com.taskowolf.sprints.application

import com.taskowolf.auth.domain.User
import com.taskowolf.core.application.DomainEventPublisher
import com.taskowolf.core.infrastructure.ConflictException
import com.taskowolf.core.infrastructure.ForbiddenException
import com.taskowolf.core.infrastructure.NotFoundException
import com.taskowolf.issues.infrastructure.IssueRepository
import com.taskowolf.projects.application.ProjectService
import com.taskowolf.sprints.api.dto.CreateSprintRequest
import com.taskowolf.sprints.api.dto.UpdateSprintRequest
import com.taskowolf.sprints.domain.Sprint
import com.taskowolf.sprints.domain.SprintStatus
import com.taskowolf.sprints.domain.events.SprintCompletedEvent
import com.taskowolf.sprints.domain.events.SprintStartedEvent
import com.taskowolf.sprints.infrastructure.SprintRepository
import com.taskowolf.workflows.domain.StatusCategory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

data class SprintCompleteResult(val sprint: Sprint, val movedToBacklogCount: Int)

@Service
class SprintService(
    private val sprintRepository: SprintRepository,
    private val projectService: ProjectService,
    private val issueRepository: IssueRepository,
    private val eventPublisher: DomainEventPublisher
) {
    @Transactional
    fun create(projectKey: String, request: CreateSprintRequest, actor: User): Sprint {
        val project = projectService.requireMember(projectKey, actor.id)
        return sprintRepository.save(
            Sprint(name = request.name, goal = request.goal, startDate = request.startDate, endDate = request.endDate, project = project)
        )
    }

    @Transactional
    fun update(projectKey: String, sprintId: UUID, request: UpdateSprintRequest, actor: User): Sprint {
        val project = projectService.requireMember(projectKey, actor.id)
        val sprint = requireSprint(sprintId, project.id)
        request.name?.let { sprint.name = it }
        request.goal?.let { sprint.goal = it }
        if (request.startDate != null || request.endDate != null) {
            if (sprint.status != SprintStatus.PLANNED) throw ConflictException("Cannot change sprint dates once sprint is started")
        }
        request.startDate?.let { sprint.startDate = it }
        request.endDate?.let { sprint.endDate = it }
        return sprintRepository.save(sprint)
    }

    @Transactional
    fun start(projectKey: String, sprintId: UUID, actor: User): Sprint {
        val project = projectService.requireMember(projectKey, actor.id)
        val sprint = requireSprint(sprintId, project.id)
        if (sprint.status != SprintStatus.PLANNED) throw ConflictException("Sprint is not in PLANNED state")
        if (sprintRepository.existsByProjectIdAndStatus(project.id, SprintStatus.ACTIVE))
            throw ConflictException("Project already has an active sprint")
        sprint.status = SprintStatus.ACTIVE
        if (sprint.startDate == null) sprint.startDate = LocalDate.now()
        sprint.plannedPoints = issueRepository.sumStoryPointsBySprintId(sprint.id).toInt()
        val saved = sprintRepository.save(sprint)
        eventPublisher.publish(SprintStartedEvent(saved))
        return saved
    }

    @Transactional
    fun complete(projectKey: String, sprintId: UUID, actor: User): SprintCompleteResult {
        val project = projectService.requireMember(projectKey, actor.id)
        val sprint = requireSprint(sprintId, project.id)
        if (sprint.status != SprintStatus.ACTIVE) throw ConflictException("Sprint is not ACTIVE")
        val allIssues = issueRepository.findBySprintId(sprint.id)
        val openIssues = allIssues.filter { it.status.category != StatusCategory.DONE }
        openIssues.forEach { it.sprint = null }
        issueRepository.saveAll(openIssues)
        sprint.completedPoints = allIssues.filter { it.status.category == StatusCategory.DONE }.sumOf { it.storyPoints ?: 0 }
        sprint.status = SprintStatus.CLOSED
        val saved = sprintRepository.save(sprint)
        eventPublisher.publish(SprintCompletedEvent(saved, openIssues.size))
        return SprintCompleteResult(saved, openIssues.size)
    }

    fun listByProject(projectKey: String, userId: UUID): List<Sprint> {
        val project = projectService.requireMember(projectKey, userId)
        return sprintRepository.findByProjectId(project.id)
    }

    @Transactional
    fun assignIssue(projectKey: String, sprintId: UUID, issueId: UUID, actor: User) {
        val project = projectService.requireMember(projectKey, actor.id)
        val sprint = requireSprint(sprintId, project.id)
        if (sprint.status == SprintStatus.CLOSED) throw ConflictException("Cannot assign issues to a closed sprint")
        val issue = issueRepository.findById(issueId)
            .filter { it.project.id == project.id }
            .orElseThrow { NotFoundException("Issue not found") }
        issue.sprint = sprint
        issueRepository.save(issue)
    }

    @Transactional
    fun unassignIssue(projectKey: String, sprintId: UUID, issueId: UUID, actor: User) {
        val project = projectService.requireMember(projectKey, actor.id)
        requireSprint(sprintId, project.id)
        val issue = issueRepository.findById(issueId)
            .filter { it.project.id == project.id }
            .orElseThrow { NotFoundException("Issue not found") }
        if (issue.sprint?.id != sprintId) throw ConflictException("Issue is not in this sprint")
        issue.sprint = null
        issueRepository.save(issue)
    }

    private fun requireSprint(sprintId: UUID, projectId: UUID): Sprint {
        val sprint = sprintRepository.findById(sprintId).orElseThrow { NotFoundException("Sprint not found") }
        if (sprint.project.id != projectId) throw ForbiddenException("Sprint does not belong to this project")
        return sprint
    }
}
