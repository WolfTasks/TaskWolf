package com.taskowolf.projects.domain

import com.taskowolf.auth.domain.User
import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "projects")
class Project(
    @Column(nullable = false, unique = true, length = 10)
    val key: String,

    @Column(nullable = false)
    var name: String,

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    val owner: User,

    @Column(name = "workflow_id")
    var workflowId: UUID? = null,

    @Column(nullable = false)
    var archived: Boolean = false,

    @Column
    var nodeId: String? = null
) : AuditableEntity()
