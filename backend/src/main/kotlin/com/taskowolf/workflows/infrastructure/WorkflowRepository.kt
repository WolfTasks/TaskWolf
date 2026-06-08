package com.taskowolf.workflows.infrastructure

import com.taskowolf.workflows.domain.Workflow
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface WorkflowRepository : JpaRepository<Workflow, UUID> {
    fun findByProjectId(projectId: UUID): List<Workflow>
}
