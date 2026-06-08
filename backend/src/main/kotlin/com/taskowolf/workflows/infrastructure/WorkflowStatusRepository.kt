package com.taskowolf.workflows.infrastructure

import com.taskowolf.workflows.domain.WorkflowStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface WorkflowStatusRepository : JpaRepository<WorkflowStatus, UUID>
