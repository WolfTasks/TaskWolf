package com.taskowolf.issues.application

import com.taskowolf.auth.domain.User
import com.taskowolf.auth.infrastructure.UserRepository
import com.taskowolf.core.application.DomainEventPublisher
import com.taskowolf.core.infrastructure.NotFoundException
import com.taskowolf.issues.api.dto.CreateIssueRequest
import com.taskowolf.issues.api.dto.UpdateIssueRequest
import com.taskowolf.issues.domain.Issue
import com.taskowolf.issues.domain.events.IssueCreatedEvent
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
                assignee = request.assigneeId?.let {
                    userRepository.findById(it).orElseThrow { NotFoundException("Assignee not found: $it") }
                },
                reporter = reporter,
                parent = request.parentId?.let {
                    issueRepository.findById(it).orElseThrow { NotFoundException("Parent issue not found: $it") }
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

        request.title?.let { issue.title = it }
        request.description?.let { issue.description = it }
        request.priority?.let { issue.priority = it }
        request.storyPoints?.let { issue.storyPoints = it }
        request.assigneeId?.let {
            issue.assignee = userRepository.findById(it).orElseThrow { NotFoundException("Assignee not found: $it") }
        }
        request.statusId?.let { newStatusId ->
            val oldStatus = issue.status
            val newStatus = workflowService.findStatusById(newStatusId)
            val projectWorkflowId = issue.project.workflow?.id
            if (projectWorkflowId != null && newStatus.workflow.id != projectWorkflowId) {
                throw com.taskowolf.core.infrastructure.ForbiddenException("Status does not belong to project's workflow")
            }
            issue.status = newStatus
            if (oldStatus.id != newStatus.id) {
                eventPublisher.publish(IssueStatusChangedEvent(issue, oldStatus, newStatus))
            }
        }
        return issueRepository.save(issue)
    }

    @Transactional(readOnly = true)
    fun findByProject(projectKey: String, userId: UUID, page: Int, size: Int): org.springframework.data.domain.Page<Issue> {
        val project = projectService.requireMember(projectKey, userId)
        return issueRepository.findAllByProjectId(project.id, PageRequest.of(page, size))
    }

    @Transactional(readOnly = true)
    fun findByKey(projectKey: String, issueKey: String, userId: UUID): Issue {
        projectService.requireMember(projectKey, userId)
        return issueRepository.findByKey(issueKey) ?: throw NotFoundException("Issue not found: $issueKey")
    }

    @Transactional
    fun delete(projectKey: String, issueId: UUID, currentUser: User) {
        val project = projectService.requireMember(projectKey, currentUser.id)
        val issue = issueRepository.findById(issueId)
            .filter { it.project.id == project.id }
            .orElseThrow { NotFoundException("Issue not found") }
        issueRepository.delete(issue)
    }
}
