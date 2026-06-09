package com.taskowolf.sprints.domain

import com.taskowolf.core.domain.AuditableEntity
import com.taskowolf.projects.domain.Project
import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(name = "sprints")
class Sprint(
    @Column(nullable = false)
    var name: String,

    @Column(columnDefinition = "TEXT")
    var goal: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: SprintStatus = SprintStatus.PLANNED,

    @Column
    var startDate: LocalDate? = null,

    @Column
    var endDate: LocalDate? = null,

    @Column
    var plannedPoints: Int? = null,

    @Column
    var completedPoints: Int? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    val project: Project
) : AuditableEntity()
