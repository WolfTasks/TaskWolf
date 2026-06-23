package com.taskowolf.servicedesk.domain

import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.util.UUID

@Entity
@Table(name = "escalation_rules")
class EscalationRule(
    @Column(nullable = false)
    val slaPolicyId: UUID,

    @Column(nullable = false)
    val escalateAfterMinutes: Int,

    val assigneeId: UUID? = null,

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "notify_user_ids", columnDefinition = "UUID[]")
    val notifyUserIds: Array<UUID> = emptyArray()
) : AuditableEntity()
