package com.taskowolf.labels.domain

import com.taskowolf.core.domain.AuditableEntity
import com.taskowolf.projects.domain.Project
import jakarta.persistence.*

@Entity
@Table(
    name = "labels",
    uniqueConstraints = [UniqueConstraint(columnNames = ["project_id", "name"])]
)
class Label(
    @Column(nullable = false, length = 50)
    var name: String,

    @Column(nullable = false, length = 7)
    var color: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    val project: Project
) : AuditableEntity()
