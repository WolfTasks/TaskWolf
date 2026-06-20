package com.taskowolf.issues.application

import com.taskowolf.auth.domain.User
import com.taskowolf.auth.infrastructure.UserRepository
import com.taskowolf.core.application.DomainEventPublisher
import com.taskowolf.core.infrastructure.NotFoundException
import com.taskowolf.issues.api.dto.CreateIssueRequest
import com.taskowolf.issues.api.dto.UpdateIssueRequest
import com.taskowolf.issues.domain.Issue
import com.taskowolf.issues.domain.events.IssueCreatedEvent
import com.taskowolf.issues.domain.events.IssueFieldChangedEvent
import com.taskowolf.issues.domain.events.IssueStatusChangedEvent
import com.taskowolf.issues.infrastructure.IssueRepository
import com.taskowolf.projects.application.ProjectService
import com.taskowolf.workflows.application.WorkflowService
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class IssueService(
    private val issueRepository: IssueRepository,
    private val projectService: ProjectService,
    private val workflowService: WorkflowService,
    private val userRepository: UserRepository,
    private val eventPublisher: DomainEventPublisher
) {
    @Transactional
    fun create(projectKey: String, request: CreateIssueRequest, reporter: User): Issue {
        val project = projectService.requireMember(projectKey, reporter.id)
        val workflow = project.workflow ?: throw NotFoundException("Project has no workflow")
        val status = workflowService.getDefaultStatus(workflow.id)
        val nextNumber = issueRepository.maxKeyNumberByProject(project.id) + 1

        val issue = issueRepository.save(
            Issue(
                key = "${project.key}-$nextNumber",
                keyNumber = nextNumber,
                title = request.title,
                description = request.description,
                type = request.type,
                priority = request.priority,
                storyPoints = request.storyPoints,
                status = status,
                project = project,
                assignee = request.assigneeId?.let { resolveAssignee(it, project) },
                reporter = reporter,
                parent = request.parentId?.let { parentId ->
                    issueRepository.findById(parentId)
                        .filter { it.project.id == project.id }
                        .orElseThrow { NotFoundException("Parent issue not found: $parentId") }
                }
            )
        )
        eventPublisher.publish(IssueCreatedEvent(issue))
        return issue
    }

    @Transactional
    fun update(projectKey: String, issueId: UUID, request: UpdateIssueRequest, currentUser: User): Issue {
        val project = projectService.requireMember(projectKey, currentUser.id)
        val issue = issueRepository.findById(issueId)
            .filter { it.project.id == project.id }
            .orElseThrow { NotFoundException("Issue not found: $issueId") }

        request.title?.let { newTitle ->
            if (issue.title != newTitle) {
                val old = issue.title
                issue.title = newTitle
                eventPublisher.publish(IssueFieldChangedEvent(issue, currentUser, "title", old, newTitle))
            }
        }

        request.description?.let { newDesc ->
            if (issue.description != newDesc) {
                val old = issue.description
                issue.description = newDesc
                eventPublisher.publish(IssueFieldChangedEvent(issue, currentUser, "description", old, newDesc))
            }
        }

        request.priority?.let { newPriority ->
            if (issue.priority != newPriority) {
                val old = issue.priority.name
                issue.priority = newPriority
                eventPublisher.publish(IssueFieldChangedEvent(issue, currentUser, "priority", old, newPriority.name))
            }
        }

        request.storyPoints?.let { newSP ->
            if (issue.storyPoints != newSP) {
                val old = issue.storyPoints?.toString()
                issue.storyPoints = newSP
                eventPublisher.publish(IssueFieldChangedEvent(issue, currentUser, "storyPoints", old, newSP.toString()))
            }
        }

        request.assigneeId?.let { assigneeId ->
            val newAssignee = resolveAssignee(assigneeId, project)
            if (issue.assignee?.id != newAssignee.id) {
                val old = issue.assignee?.displayName
                issue.assignee = newAssignee
                eventPublisher.publish(IssueFieldChangedEvent(issue, currentUser, "assignee", old, newAssignee.displayName))
            }
        }

        request.statusId?.let { newStatusId ->
            val oldStatus = issue.status
            val newStatus = workflowService.findStatusById(newStatusId)
            val projectWorkflowId = issue.project.workflow?.id
            if (projectWorkflowId != null && newStatus.workflow.id != projectWorkflowId) {
                throw com.taskowolf.core.infrastructure.ForbiddenException("Status does not belong to project's workflow")
            }
            if (oldStatus.id != newStatus.id) {
                workflowService.validateTransition(issue, newStatusId, currentUser)
                issue.status = newStatus
                eventPublisher.publish(IssueStatusChangedEvent(issue, oldStatus, newStatus, actor = currentUser))
            }
        }

        return issueRepository.save(issue)
    }

    @Transactional(readOnly = true)
    fun findByProject(
        projectKey: String,
        userId: UUID,
        page: Int,
        size: Int,
        assigneeMe: Boolean = false,
        sort: String? = null,
        overdue: Boolean = false
    ): org.springframework.data.domain.Page<Issue> {
        val project = projectService.requireMember(projectKey, userId)
        val pageable = when (sort) {
            "updatedAt" -> PageRequest.of(page, size, org.springframework.data.domain.Sort.by("updatedAt").descending())
            else -> PageRequest.of(page, size)
        }
        return when {
            overdue -> issueRepository.findOverdueByProjectId(project.id, pageable)
            assigneeMe -> issueRepository.findByProjectIdAndAssigneeId(project.id, userId, pageable)
            else -> issueRepository.findAllByProjectId(project.id, pageable)
        }
    }

    @Transactional(readOnly = true)
    fun findByKey(projectKey: String, issueKey: String, userId: UUID): Issue {
        val project = projectService.requireMember(projectKey, userId)
        return issueRepository.findByKeyAndProjectId(issueKey, project.id)
            ?: throw NotFoundException("Issue not found: $issueKey")
    }

    @Transactional
    fun delete(projectKey: String, issueId: UUID, currentUser: User) {
        val project = projectService.requireMember(projectKey, currentUser.id)
        val issue = issueRepository.findById(issueId)
            .filter { it.project.id == project.id }
            .orElseThrow { NotFoundException("Issue not found") }
        issueRepository.delete(issue)
    }

    private fun resolveAssignee(assigneeId: UUID, project: com.taskowolf.projects.domain.Project): com.taskowolf.auth.domain.User {
        val assignee = userRepository.findById(assigneeId)
            .orElseThrow { NotFoundException("Assignee not found: $assigneeId") }
        if (!projectService.isMember(project, assignee.id)) {
            throw NotFoundException("Assignee not found: $assigneeId")
        }
        return assignee
    }
}
