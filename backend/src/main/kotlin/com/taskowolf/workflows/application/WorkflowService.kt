package com.taskowolf.workflows.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.taskowolf.auth.domain.User
import com.taskowolf.core.infrastructure.BadRequestException
import com.taskowolf.core.infrastructure.NotFoundException
import com.taskowolf.projects.domain.Project
import com.taskowolf.projects.infrastructure.ProjectMemberRepository
import com.taskowolf.workflows.domain.*
import com.taskowolf.workflows.infrastructure.WorkflowRepository
import com.taskowolf.workflows.infrastructure.WorkflowStatusPositionRepository
import com.taskowolf.workflows.infrastructure.WorkflowStatusRepository
import com.taskowolf.workflows.infrastructure.WorkflowTransitionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class WorkflowService(
    private val workflowRepository: WorkflowRepository,
    private val statusRepository: WorkflowStatusRepository,
    private val transitionRepository: WorkflowTransitionRepository,
    private val positionRepository: WorkflowStatusPositionRepository,
    private val projectMemberRepository: ProjectMemberRepository,
    private val mapper: ObjectMapper
) {

    @Transactional
    fun createDefault(project: Project): Workflow {
        val workflow = workflowRepository.save(
            Workflow(name = "Default Workflow", project = project, isDefault = true)
        )
        val todo = statusRepository.save(WorkflowStatus("To Do", StatusCategory.TODO, "#6c8fef", 0, workflow))
        val inProgress = statusRepository.save(WorkflowStatus("In Progress", StatusCategory.IN_PROGRESS, "#ffb432", 1, workflow))
        val done = statusRepository.save(WorkflowStatus("Done", StatusCategory.DONE, "#63dc78", 2, workflow))
        workflow.statuses.addAll(listOf(todo, inProgress, done))
        return workflow
    }

    @Transactional(readOnly = true)
    fun findByProject(projectId: UUID) = workflowRepository.findByProjectId(projectId)

    @Transactional(readOnly = true)
    fun findStatusById(statusId: UUID) = statusRepository.findById(statusId)
        .orElseThrow { NotFoundException.keyed("workflow.statusNotFound", statusId) }

    @Transactional(readOnly = true)
    fun getDefaultStatus(workflowId: UUID): WorkflowStatus {
        val workflow = workflowRepository.findById(workflowId)
            .orElseThrow { NotFoundException.keyed("workflow.notFound", workflowId) }
        return workflow.statuses
            .filter { it.category == StatusCategory.TODO }
            .minByOrNull { it.position }
            ?: throw NotFoundException.keyed("workflow.noTodoStatus", workflowId)
    }

    @Transactional(readOnly = true)
    fun findWorkflowByProjectId(projectId: UUID): Workflow =
        workflowRepository.findByProjectId(projectId).firstOrNull()
            ?: throw NotFoundException.keyed("workflow.noneForProject", projectId)

    @Transactional(readOnly = true)
    fun findWorkflowForEditor(projectId: UUID): WorkflowEditorData {
        val wf = findWorkflowByProjectId(projectId)
        // Access lazy collections inside the transaction so they are initialized
        val statuses = wf.statuses.toList()
        val transitions = wf.transitions.toList()
        val layout = positionRepository.findByIdWorkflowId(wf.id)
        return WorkflowEditorData(wf.id, wf.name, statuses, transitions, layout)
    }

    // ---- Transition guard validation ----

    @Transactional(readOnly = true)
    fun validateTransition(issue: com.taskowolf.issues.domain.Issue, toStatusId: UUID, actor: User) {
        val workflowId = issue.project.workflow?.id ?: return
        // When no transitions are configured, all moves are allowed
        if (!transitionRepository.existsByWorkflowId(workflowId)) return
        val transition = transitionRepository.findByWorkflowIdAndFromStatusIdAndToStatusId(
            workflowId, issue.status.id, toStatusId
        ) ?: throw BadRequestException.keyed("workflow.transitionNotAllowed", issue.status.name, toStatusId)

        val guards: List<TransitionGuard> = transition.guards
            ?.let { mapper.readValue(it) } ?: emptyList()

        val issueMap = mapOf(
            "title" to issue.title,
            "description" to issue.description,
            "assigneeId" to issue.assignee?.id?.toString(),
            "storyPoints" to issue.storyPoints?.toString(),
            "dueDate" to issue.dueDate?.toString()
        )

        for (guard in guards) {
            when (guard) {
                is RequiredFieldGuard -> {
                    val value = issueMap[guard.field]
                    if (value.isNullOrBlank())
                        throw BadRequestException.keyed("workflow.transitionFieldRequired", guard.field)
                }
                is RoleRestrictionGuard -> {
                    val member = projectMemberRepository.findByProjectIdAndUserId(issue.project.id, actor.id)
                    val userRole = member?.role?.name ?: "NONE"
                    if (userRole !in guard.roles)
                        throw BadRequestException.keyed("workflow.transitionRoleNotPermitted", userRole)
                }
            }
        }
    }

    // ---- Editor: Status CRUD ----

    @Transactional
    fun createStatus(workflowId: UUID, name: String, category: StatusCategory, color: String): WorkflowStatus {
        val workflow = workflowRepository.findById(workflowId)
            .orElseThrow { NotFoundException.keyed("workflow.notFound", workflowId) }
        val position = (workflow.statuses.maxOfOrNull { it.position } ?: -1) + 1
        return statusRepository.save(WorkflowStatus(name, category, color, position, workflow))
    }

    @Transactional
    fun updateStatus(statusId: UUID, name: String?, category: StatusCategory?, color: String?): WorkflowStatus {
        val status = statusRepository.findById(statusId)
            .orElseThrow { NotFoundException.keyed("workflow.statusNotFound", statusId) }
        name?.let { status.name = it }
        category?.let { status.category = it }
        color?.let { status.color = it }
        return statusRepository.save(status)
    }

    @Transactional
    fun deleteStatus(statusId: UUID) {
        if (!statusRepository.existsById(statusId)) throw NotFoundException.keyed("workflow.statusNotFound", statusId)
        statusRepository.deleteById(statusId)
    }

    // ---- Editor: Transition CRUD ----

    @Transactional
    fun createTransition(workflowId: UUID, fromStatusId: UUID?, toStatusId: UUID): WorkflowTransition {
        val workflow = workflowRepository.findById(workflowId)
            .orElseThrow { NotFoundException.keyed("workflow.notFound", workflowId) }
        val toStatus = statusRepository.findById(toStatusId)
            .orElseThrow { NotFoundException.keyed("workflow.statusNotFound", toStatusId) }
        val fromStatus = fromStatusId?.let {
            statusRepository.findById(it).orElseThrow { NotFoundException.keyed("workflow.statusNotFound", it) }
        }
        return transitionRepository.save(WorkflowTransition(workflow, fromStatus, toStatus))
    }

    @Transactional
    fun deleteTransition(transitionId: UUID) {
        if (!transitionRepository.existsById(transitionId)) throw NotFoundException.keyed("workflow.transitionNotFound", transitionId)
        transitionRepository.deleteById(transitionId)
    }

    @Transactional
    fun updateGuards(transitionId: UUID, guards: List<TransitionGuard>): WorkflowTransition {
        val transition = transitionRepository.findById(transitionId)
            .orElseThrow { NotFoundException.keyed("workflow.transitionNotFound", transitionId) }
        transition.guards = mapper.writeValueAsString(guards)
        return transitionRepository.save(transition)
    }

    // ---- Editor: Canvas layout ----

    @Transactional
    fun saveLayout(workflowId: UUID, positions: List<StatusPositionInput>) {
        positionRepository.deleteByWorkflowId(workflowId)
        positions.forEach { p ->
            positionRepository.save(
                WorkflowStatusPosition(WorkflowStatusPositionId(workflowId, p.statusId), p.x, p.y)
            )
        }
    }

    @Transactional(readOnly = true)
    fun getLayout(workflowId: UUID): List<WorkflowStatusPosition> =
        positionRepository.findByIdWorkflowId(workflowId)
}

data class StatusPositionInput(val statusId: UUID, val x: Int, val y: Int)

data class WorkflowEditorData(
    val id: UUID,
    val name: String,
    val statuses: List<com.taskowolf.workflows.domain.WorkflowStatus>,
    val transitions: List<com.taskowolf.workflows.domain.WorkflowTransition>,
    val layout: List<com.taskowolf.workflows.domain.WorkflowStatusPosition>
)
