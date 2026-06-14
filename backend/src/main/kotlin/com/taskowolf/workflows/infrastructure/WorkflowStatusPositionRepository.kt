package com.taskowolf.workflows.infrastructure

import com.taskowolf.workflows.domain.WorkflowStatusPosition
import com.taskowolf.workflows.domain.WorkflowStatusPositionId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional

interface WorkflowStatusPositionRepository : JpaRepository<WorkflowStatusPosition, WorkflowStatusPositionId> {
    fun findByIdWorkflowId(workflowId: java.util.UUID): List<WorkflowStatusPosition>

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("DELETE FROM WorkflowStatusPosition p WHERE p.id.workflowId = :workflowId")
    fun deleteByWorkflowId(workflowId: java.util.UUID)
}
