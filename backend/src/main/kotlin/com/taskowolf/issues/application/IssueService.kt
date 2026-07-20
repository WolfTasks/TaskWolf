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
import com.taskowolf.issues.infrastructure.IssueSpecification
import com.taskowolf.labels.infrastructure.LabelRepository
import com.taskowolf.projects.application.ProjectService
import com.taskowolf.versions.domain.IssueVersion
import com.taskowolf.versions.infrastructure.IssueVersionRepository
import com.taskowolf.versions.infrastructure.VersionRepository
import com.taskowolf.sprints.infrastructure.SprintRepository
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
    private val eventPublisher: DomainEventPublisher,
    private val sprintRepository: SprintRepository,
    private val labelRepository: LabelRepository,
    private val versionRepository: com.taskowolf.versions.infrastructure.VersionRepository,
    private val issueVersionRepository: com.taskowolf.versions.infrastructure.IssueVersionRepository,
    private val customFieldDefinitionRepository: com.taskowolf.customfields.infrastructure.CustomFieldDefinitionRepository,
    private val customFieldOptionRepository: com.taskowolf.customfields.infrastructure.CustomFieldOptionRepository,
    private val customFieldValueRepository: com.taskowolf.customfields.infrastructure.CustomFieldValueRepository
) {
    @Transactional
    fun create(projectKey: String, request: CreateIssueRequest, reporter: User): Issue {
        val project = projectService.requireMember(projectKey, reporter.id)
        val workflow = project.workflow ?: throw NotFoundException.keyed("project.noWorkflow")
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
                        .orElseThrow { NotFoundException.keyed("issue.parentNotFound", parentId) }
                }
            )
        )
        validateRequiredCustomFields(project.id, request.customFieldValues)
        request.customFieldValues?.let { applyCustomFieldValues(issue.id, it, project.id) }
        eventPublisher.publish(IssueCreatedEvent(issue, actorEmail = reporter.email, actorId = reporter.id))
        return issue
    }

    @Transactional
    fun update(projectKey: String, issueId: UUID, request: UpdateIssueRequest, currentUser: User): Issue {
        val project = projectService.requireMember(projectKey, currentUser.id)
        val issue = issueRepository.findById(issueId)
            .filter { it.project.id == project.id }
            .orElseThrow { NotFoundException.keyed("issue.notFound", issueId) }

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

        when {
            request.clearStoryPoints -> {
                val old = issue.storyPoints?.toString()
                issue.storyPoints = null
                if (old != null) eventPublisher.publish(IssueFieldChangedEvent(issue, currentUser, "storyPoints", old, null))
            }
            request.storyPoints != null -> {
                val newSP = request.storyPoints
                if (issue.storyPoints != newSP) {
                    val old = issue.storyPoints?.toString()
                    issue.storyPoints = newSP
                    eventPublisher.publish(IssueFieldChangedEvent(issue, currentUser, "storyPoints", old, newSP.toString()))
                }
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
                    .orElseThrow { NotFoundException.keyed("sprint.notFound", request.sprintId) }
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
                throw com.taskowolf.core.infrastructure.ForbiddenException.keyed("issue.statusNotInWorkflow")
            }
            if (oldStatus.id != newStatus.id) {
                workflowService.validateTransition(issue, newStatusId, currentUser)
                issue.status = newStatus
                eventPublisher.publish(IssueStatusChangedEvent(issue, oldStatus, newStatus, actor = currentUser))
            }
        }

        request.labelIds?.let { ids ->
            val newLabels = if (ids.isEmpty()) mutableSetOf()
                            else labelRepository.findAllById(ids)
                                .filter { it.project.id == project.id }
                                .toMutableSet()
            val oldNames = issue.labels.map { it.name }.sorted().joinToString(", ")
            val newNames = newLabels.map { it.name }.sorted().joinToString(", ")
            if (oldNames != newNames) {
                issue.labels.clear()
                issue.labels.addAll(newLabels)
                eventPublisher.publish(
                    IssueFieldChangedEvent(issue, currentUser, "labels",
                        oldNames.ifEmpty { null }, newNames.ifEmpty { null })
                )
            }
        }

        request.fixVersionIds?.let { ids ->
            val oldVersions = versionRepository.findByIssueIdAndType(issue.id, "FIX")
            val oldNames = oldVersions.map { it.name }.sorted().joinToString(", ")
            val newVersions = if (ids.isEmpty()) emptyList()
                              else versionRepository.findAllById(ids).filter { it.project.id == project.id }
            val newNames = newVersions.map { it.name }.sorted().joinToString(", ")
            if (oldNames != newNames) {
                issueVersionRepository.deleteByIssueIdAndType(issue.id, "FIX")
                issueVersionRepository.saveAll(newVersions.map { IssueVersion(issue.id, it.id, "FIX") })
                eventPublisher.publish(
                    IssueFieldChangedEvent(issue, currentUser, "fixVersions",
                        oldNames.ifEmpty { null }, newNames.ifEmpty { null })
                )
            }
        }

        request.affectsVersionIds?.let { ids ->
            val oldVersions = versionRepository.findByIssueIdAndType(issue.id, "AFFECTS")
            val oldNames = oldVersions.map { it.name }.sorted().joinToString(", ")
            val newVersions = if (ids.isEmpty()) emptyList()
                              else versionRepository.findAllById(ids).filter { it.project.id == project.id }
            val newNames = newVersions.map { it.name }.sorted().joinToString(", ")
            if (oldNames != newNames) {
                issueVersionRepository.deleteByIssueIdAndType(issue.id, "AFFECTS")
                issueVersionRepository.saveAll(newVersions.map { IssueVersion(issue.id, it.id, "AFFECTS") })
                eventPublisher.publish(
                    IssueFieldChangedEvent(issue, currentUser, "affectsVersions",
                        oldNames.ifEmpty { null }, newNames.ifEmpty { null })
                )
            }
        }

        request.customFieldValues?.let { applyCustomFieldValues(issue.id, it, project.id) }

        return issueRepository.save(issue)
    }

    /**
     * Filters issues for a project using composable JPA Specifications.
     * When [overdue] is true, results are ordered by dueDate ASC regardless of [sort].
     * Custom field filters are passed as [customFieldFilters]: a map of field UUID → raw string value.
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
        labelId: UUID? = null,
        fixVersionId: UUID? = null,
        affectsVersionId: UUID? = null,
        customFieldFilters: Map<UUID, String> = emptyMap()
    ): org.springframework.data.domain.Page<Issue> {
        val project = projectService.requireMember(projectKey, userId)

        val pageable = when {
            overdue -> PageRequest.of(page, size, org.springframework.data.domain.Sort.by("dueDate").ascending())
            sort == "updatedAt" -> PageRequest.of(page, size, org.springframework.data.domain.Sort.by("updatedAt").descending())
            else -> PageRequest.of(page, size)
        }

        var spec = IssueSpecification.inProject(project.id)
        if (assigneeMe) spec = spec.and(IssueSpecification.assignedTo(userId))
        if (overdue) spec = spec.and(IssueSpecification.overdue())
        if (labelId != null) spec = spec.and(IssueSpecification.hasLabel(labelId))
        if (fixVersionId != null) spec = spec.and(IssueSpecification.hasFixVersion(fixVersionId))
        if (affectsVersionId != null) spec = spec.and(IssueSpecification.hasAffectsVersion(affectsVersionId))

        for ((fieldId, rawValue) in customFieldFilters) {
            val fieldType = customFieldDefinitionRepository.findById(fieldId).map { it.type }.orElse(null) ?: continue
            spec = spec.and(IssueSpecification.hasCustomFieldValue(fieldId, rawValue, fieldType))
        }

        return issueRepository.findAll(spec, pageable)
    }

    @Transactional(readOnly = true)
    fun findByKey(projectKey: String, issueKey: String, userId: UUID): Issue {
        val project = projectService.requireMember(projectKey, userId)
        return issueRepository.findByKeyAndProjectId(issueKey, project.id)
            ?: throw NotFoundException.keyed("issue.notFound", issueKey)
    }

    @Transactional
    fun delete(projectKey: String, issueId: UUID, currentUser: User) {
        val project = projectService.requireMember(projectKey, currentUser.id)
        val issue = issueRepository.findById(issueId)
            .filter { it.project.id == project.id }
            .orElseThrow { NotFoundException.keyed("issue.notFoundGeneric") }
        issueRepository.delete(issue)
    }

    /**
     * Creates a ticket from an incoming email. Uses the project owner as the system reporter.
     * Only called when IMAP ingestion is enabled ([EmailIngestionService]).
     */
    @Transactional
    fun createTicketFromEmail(projectId: UUID, title: String, body: String, senderEmail: String): Issue {
        val project = projectService.findById(projectId)
        val workflow = project.workflow ?: throw com.taskowolf.core.infrastructure.NotFoundException.keyed("project.noWorkflow")
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

    private fun applyCustomFieldValues(
        issueId: UUID,
        inputs: List<com.taskowolf.issues.api.dto.CustomFieldValueInput>,
        projectId: UUID
    ) {
        val definitions = customFieldDefinitionRepository
            .findByProjectIdOrderBySortOrder(projectId)
            .associateBy { it.id }

        for (input in inputs) {
            val definition = definitions[input.fieldId] ?: continue

            if (input.value == null) {
                customFieldValueRepository.deleteByIssueIdAndFieldId(issueId, definition.id)
                continue
            }

            val cfv = customFieldValueRepository
                .findByIssueIdAndField_Id(issueId, definition.id)
                .orElse(com.taskowolf.customfields.domain.CustomFieldValue(issueId = issueId, field = definition))

            when (definition.type) {
                com.taskowolf.customfields.domain.FieldType.TEXT -> cfv.textValue = input.value
                com.taskowolf.customfields.domain.FieldType.NUMBER -> cfv.numberValue = input.value.toBigDecimalOrNull()
                    ?: throw com.taskowolf.core.infrastructure.BadRequestException.keyed("customField.invalidNumber", definition.name, input.value)
                com.taskowolf.customfields.domain.FieldType.DATE -> cfv.dateValue = runCatching {
                    java.time.LocalDate.parse(input.value)
                }.getOrElse {
                    throw com.taskowolf.core.infrastructure.BadRequestException.keyed("customField.invalidDate", definition.name, input.value)
                }
                com.taskowolf.customfields.domain.FieldType.CHECKBOX ->
                    cfv.booleanValue = input.value.equals("true", ignoreCase = true)
                com.taskowolf.customfields.domain.FieldType.DROPDOWN -> {
                    val optId = runCatching { UUID.fromString(input.value) }.getOrElse {
                        throw com.taskowolf.core.infrastructure.BadRequestException.keyed("customField.invalidOptionId", definition.name)
                    }
                    cfv.option = customFieldOptionRepository.findById(optId)
                        .filter { it.field.id == definition.id }
                        .orElseThrow { com.taskowolf.core.infrastructure.BadRequestException.keyed("customField.optionNotFound", optId) }
                }
            }
            customFieldValueRepository.save(cfv)
        }
    }

    private fun validateRequiredCustomFields(projectId: UUID, inputs: List<com.taskowolf.issues.api.dto.CustomFieldValueInput>?) {
        val requiredFields = customFieldDefinitionRepository
            .findByProjectIdOrderBySortOrder(projectId)
            .filter { it.required }
        val providedMap = inputs?.associateBy { it.fieldId } ?: emptyMap()
        requiredFields.forEach { field ->
            val input = providedMap[field.id]
            if (input == null || input.value == null || input.value.isBlank()) {
                throw com.taskowolf.core.infrastructure.BadRequestException.keyed("customField.requiredMissing", field.name)
            }
        }
    }

    private fun resolveAssignee(assigneeId: UUID, project: com.taskowolf.projects.domain.Project): com.taskowolf.auth.domain.User {
        val assignee = userRepository.findById(assigneeId)
            .orElseThrow { NotFoundException.keyed("issue.assigneeNotFound", assigneeId) }
        if (!projectService.isMember(project, assignee.id)) {
            throw NotFoundException.keyed("issue.assigneeNotFound", assigneeId)
        }
        return assignee
    }
}
