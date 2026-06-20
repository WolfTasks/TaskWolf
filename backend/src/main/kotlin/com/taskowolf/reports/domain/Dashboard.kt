package com.taskowolf.reports.domain

import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "dashboard")
class Dashboard(
    @Column(name = "project_id", nullable = false, unique = true)
    val projectId: UUID,

    @OneToMany(
        mappedBy = "dashboard",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    val widgets: MutableList<DashboardWidget> = mutableListOf()
) : AuditableEntity()
