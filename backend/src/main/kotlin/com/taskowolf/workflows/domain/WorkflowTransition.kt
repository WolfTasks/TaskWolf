package com.taskowolf.workflows.domain

import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.*

@Entity
@Table(name = "workflow_transitions")
class WorkflowTransition(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", nullable = false)
    val workflow: Workflow,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_status_id")
    val fromStatus: WorkflowStatus? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_status_id", nullable = false)
    val toStatus: WorkflowStatus
) : AuditableEntity()
