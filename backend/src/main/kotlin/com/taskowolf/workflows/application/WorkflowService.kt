package com.taskowolf.workflows.application

import com.taskowolf.core.infrastructure.NotFoundException
import com.taskowolf.projects.domain.Project
import com.taskowolf.workflows.domain.*
import com.taskowolf.workflows.infrastructure.WorkflowRepository
import com.taskowolf.workflows.infrastructure.WorkflowStatusRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class WorkflowService(
    private val workflowRepository: WorkflowRepository,
    private val statusRepository: WorkflowStatusRepository
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
        .orElseThrow { NotFoundException("Status not found: $statusId") }

    @Transactional(readOnly = true)
    fun getDefaultStatus(workflow: Workflow) = workflow.statuses
        .filter { it.category == StatusCategory.TODO }
        .minByOrNull { it.position }
        ?: throw NotFoundException("No TODO status in workflow")
}
