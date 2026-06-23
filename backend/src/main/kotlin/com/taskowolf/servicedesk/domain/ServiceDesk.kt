package com.taskowolf.servicedesk.domain

import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "service_desks")
class ServiceDesk(
    @Column(nullable = false)
    val projectId: UUID,

    var emailAddress: String? = null,

    @Column(nullable = false)
    var enabled: Boolean = true
) : AuditableEntity()
