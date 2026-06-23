package com.taskowolf.servicedesk.domain

import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "incidents")
class Incident(
    @Column(nullable = false)
    val issueId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val severity: IncidentSeverity,

    var onCallAssigneeId: UUID? = null,

    @Column(columnDefinition = "TEXT")
    var postmortemBody: String? = null,

    var resolvedAt: Instant? = null
) : AuditableEntity()
