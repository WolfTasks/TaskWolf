package com.taskowolf.workflows.domain

import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.*

@Entity
@Table(name = "workflow_statuses")
class WorkflowStatus(
    @Column(nullable = false)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var category: StatusCategory,

    @Column(nullable = false, length = 7)
    var color: String = "#6c8fef",

    @Column(nullable = false)
    var position: Int = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", nullable = false)
    val workflow: Workflow
) : AuditableEntity()
