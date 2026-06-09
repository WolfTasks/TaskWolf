package com.taskowolf.workflows.domain

import com.taskowolf.core.domain.AuditableEntity
import com.taskowolf.projects.domain.Project
import jakarta.persistence.*

@Entity
@Table(name = "workflows")
class Workflow(
    @Column(nullable = false)
    var name: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    val project: Project,

    @Column(nullable = false)
    var isDefault: Boolean = false,

    @OneToMany(mappedBy = "workflow", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("position ASC")
    val statuses: MutableList<WorkflowStatus> = mutableListOf(),

    @OneToMany(mappedBy = "workflow", cascade = [CascadeType.ALL], orphanRemoval = true)
    val transitions: MutableList<WorkflowTransition> = mutableListOf()
) : AuditableEntity()
