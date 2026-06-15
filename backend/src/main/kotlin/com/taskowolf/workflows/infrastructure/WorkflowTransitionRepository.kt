package com.taskowolf.workflows.infrastructure

import com.taskowolf.workflows.domain.WorkflowTransition
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface WorkflowTransitionRepository : JpaRepository<WorkflowTransition, UUID> {
    fun findByWorkflowId(workflowId: UUID): List<WorkflowTransition>
    fun existsByWorkflowId(workflowId: UUID): Boolean
    fun findByWorkflowIdAndFromStatusIdAndToStatusId(
        workflowId: UUID, fromStatusId: UUID, toStatusId: UUID
    ): WorkflowTransition?
}
