package com.taskowolf.customfields.domain

import com.taskowolf.core.domain.AuditableEntity
import com.taskowolf.projects.domain.Project
import jakarta.persistence.*

@Entity
@Table(
    name = "custom_field_definitions",
    uniqueConstraints = [UniqueConstraint(columnNames = ["project_id", "name"])]
)
class CustomFieldDefinition(
    @Column(nullable = false, length = 100)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    val type: FieldType,

    @Column(nullable = false)
    var required: Boolean = false,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    val project: Project
) : AuditableEntity()
