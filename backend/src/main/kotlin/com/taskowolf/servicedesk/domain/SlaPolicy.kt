package com.taskowolf.servicedesk.domain

import com.taskowolf.core.domain.AuditableEntity
import com.taskowolf.issues.domain.IssuePriority
import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "sla_policies")
class SlaPolicy(
    @Column(nullable = false)
    val serviceDeskId: UUID,

    @Column(nullable = false)
    val name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val priority: IssuePriority,

    @Column(nullable = false)
    val responseMinutes: Int,

    @Column(nullable = false)
    val resolutionMinutes: Int
) : AuditableEntity()
