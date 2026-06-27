// backend/src/main/kotlin/com/taskowolf/versions/domain/Version.kt
package com.taskowolf.versions.domain

import com.taskowolf.core.domain.AuditableEntity
import com.taskowolf.projects.domain.Project
import jakarta.persistence.*

@Entity
@Table(
    name = "versions",
    uniqueConstraints = [UniqueConstraint(columnNames = ["project_id", "name"])]
)
class Version(
    @Column(nullable = false, length = 50)
    var name: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    val project: Project
) : AuditableEntity()
