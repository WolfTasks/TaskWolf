package com.taskowolf.issues.application

import com.taskowolf.auth.domain.User
import com.taskowolf.auth.infrastructure.UserRepository
import com.taskowolf.core.application.DomainEventPublisher
import com.taskowolf.core.infrastructure.NotFoundException
import com.taskowolf.issues.api.dto.CreateIssueRequest
import com.taskowolf.issues.api.dto.UpdateIssueRequest
import com.taskowolf.issues.domain.Issue
import com.taskowolf.issues.domain.IssueType
import com.taskowolf.issues.domain.events.IssueCreatedEvent
import com.taskowolf.issues.domain.events.IssueFieldChangedEvent
import com.taskowolf.issues.domain.events.IssueStatusChangedEvent
import com.taskowolf.issues.infrastructure.IssueRepository
import com.taskowolf.labels.infrastructure.LabelRepository
import com.taskowolf.projects.application.ProjectService
import com.taskowolf.sprints.infrastructure.SprintRepository
import com.taskowolf.workflows.application.WorkflowService
import com.taskowolf.workflows.domain.StatusCategory
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
    private val eventPublisher: DomainEventPublisher,
    private val sprintRepository: SprintRepository,
    private val labelRepository: LabelRepository
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
        eventPublisher.publish(IssueCreatedEvent(issue, actorEmail = reporter.email, actorId = reporter.id))
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

        if (request.clearAssignee) {
            val old = issue.assignee?.displayName
            issue.assignee = null
            if (old != null) eventPublisher.publish(IssueFieldChangedEvent(issue, currentUser, "assignee", old, null))
        } else {
            request.assigneeId?.let { assigneeId ->
                val newAssignee = resolveAssignee(assigneeId, project)
                if (issue.assignee?.id != newAssignee.id) {
                    val old = issue.assignee?.displayName
                    issue.assignee = newAssignee
                    eventPublisher.publish(IssueFieldChangedEvent(issue, currentUser, "assignee", old, newAssignee.displayName))
                }
            }
        }

        request.type?.let { newType ->
            if (issue.type != newType) {
                val old = issue.type.name
                issue.type = newType
                eventPublisher.publish(IssueFieldChangedEvent(issue, currentUser, "type", old, newType.name))
            }
        }

        when {
            request.clearDueDate -> {
                val old = issue.dueDate?.toString()
                issue.dueDate = null
                if (old != null) eventPublisher.publish(IssueFieldChangedEvent(issue, currentUser, "dueDate", old, null))
            }
            request.dueDate != null -> {
                val old = issue.dueDate?.toString()
                if (old != request.dueDate.toString()) {
                    issue.dueDate = request.dueDate
                    eventPublisher.publish(IssueFieldChangedEvent(issue, currentUser, "dueDate", old, request.dueDate.toString()))
                }
            }
        }

        when {
            request.clearSprint -> {
                val old = issue.sprint?.name
                issue.sprint = null
                if (old != null) eventPublisher.publish(IssueFieldChangedEvent(issue, currentUser, "sprint", old, null))
            }
            request.sprintId != null -> {
                val newSprint = sprintRepository.findById(request.sprintId)
                    .filter { it.project.id == project.id }
                    .orElseThrow { NotFoundException("Sprint not found: ${request.sprintId}") }
                if (issue.sprint?.id != newSprint.id) {
                    val old = issue.sprint?.name
                    issue.sprint = newSprint
                    eventPublisher.publish(IssueFieldChangedEvent(issue, currentUser, "sprint", old, newSprint.name))
                }
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

        request.labelIds?.let { ids ->
            val newLabels = if (ids.isEmpty()) mutableSetOf()
                            else labelRepository.findAllById(ids).toMutableSet()
            val oldNames = issue.labels.map { it.name }.sorted().joinToString(", ")
            issue.labels.clear()
            issue.labels.addAll(newLabels)
            val newNames = newLabels.map { it.name }.sorted().joinToString(", ")
            eventPublisher.publish(
                IssueFieldChangedEvent(issue, currentUser, "labels",
                    oldNames.ifEmpty { null }, newNames.ifEmpty { null })
            )
        }

        return issueRepository.save(issue)
    }

    /**
     * Filters issues for a project. When [overdue] is true, results are always ordered by dueDate ASC
     * regardless of the [sort] parameter (the overdue query hardcodes ORDER BY dueDate ASC).
     */
    @Transactional(readOnly = true)
    fun findByProject(
        projectKey: String,
        userId: UUID,
        page: Int,
        size: Int,
        assigneeMe: Boolean = false,
        sort: String? = null,
        overdue: Boolean = false,
        labelId: UUID? = null
    ): org.springframework.data.domain.Page<Issue> {
        val project = projectService.requireMember(projectKey, userId)
        val pageable = when (sort) {
            "updatedAt" -> PageRequest.of(page, size, org.springframework.data.domain.Sort.by("updatedAt").descending())
            else -> PageRequest.of(page, size)
        }
        if (labelId != null) return issueRepository.findAllByProjectIdAndLabelId(project.id, labelId, pageable)
        return when {
            overdue && assigneeMe -> issueRepository.findOverdueByProjectIdAndAssigneeId(project.id, userId, StatusCategory.DONE, pageable)
            overdue -> issueRepository.findOverdueByProjectId(project.id, StatusCategory.DONE, pageable)
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

    /**
     * Creates a ticket from an incoming email. Uses the project owner as the system reporter.
     * Only called when IMAP ingestion is enabled ([EmailIngestionService]).
     */
    @Transactional
    fun createTicketFromEmail(projectId: UUID, title: String, body: String, senderEmail: String): Issue {
        val project = projectService.findById(projectId)
        val workflow = project.workflow ?: throw com.taskowolf.core.infrastructure.NotFoundException("Project has no workflow")
        val status = workflowService.getDefaultStatus(workflow.id)
        val nextNumber = issueRepository.maxKeyNumberByProject(project.id) + 1
        val reporter = project.owner

        val issue = issueRepository.save(
            Issue(
                key = "${project.key}-$nextNumber",
                keyNumber = nextNumber,
                title = title,
                description = "**From:** $senderEmail\n\n$body",
                type = IssueType.TASK,
                status = status,
                project = project,
                reporter = reporter
            )
        )
        eventPublisher.publish(IssueCreatedEvent(issue, actorEmail = senderEmail, actorId = reporter.id))
        return issue
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
