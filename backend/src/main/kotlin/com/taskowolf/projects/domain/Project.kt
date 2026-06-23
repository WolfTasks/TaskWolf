package com.taskowolf.projects.domain

import com.taskowolf.auth.domain.User
import com.taskowolf.core.domain.AuditableEntity
import com.taskowolf.workflows.domain.Workflow
import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "projects")
class Project(
    @Column(name = "\"key\"", nullable = false, unique = true, length = 10)
    val key: String,

    @Column(nullable = false)
    var name: String,

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    val owner: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id")
    var workflow: Workflow? = null,

    @Column(nullable = false)
    var archived: Boolean = false,

    @Column
    var nodeId: String? = null,

    @Column
    var orgId: UUID? = null
) : AuditableEntity()
