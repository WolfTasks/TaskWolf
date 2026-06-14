package com.taskowolf.workflows.domain

import jakarta.persistence.*
import java.io.Serializable
import java.util.UUID

@Embeddable
data class WorkflowStatusPositionId(
    val workflowId: UUID = UUID.randomUUID(),
    val statusId: UUID = UUID.randomUUID()
) : Serializable

@Entity
@Table(name = "workflow_status_positions")
class WorkflowStatusPosition(
    @EmbeddedId
    val id: WorkflowStatusPositionId,

    @Column(nullable = false)
    var x: Int = 0,

    @Column(nullable = false)
    var y: Int = 0
)
