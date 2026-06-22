package com.taskowolf.integrations.domain

import com.taskowolf.core.domain.AuditableEntity
import com.taskowolf.integrations.infrastructure.StringListConverter
import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "webhooks")
class Webhook(
    @Column(name = "project_id", nullable = false)
    val projectId: UUID,

    @Column(nullable = false, length = 2048)
    var url: String,

    @Column(nullable = false)
    var secretHash: String,

    @Convert(converter = StringListConverter::class)
    @Column(nullable = false, columnDefinition = "TEXT")
    var events: List<String> = emptyList(),

    @Column(nullable = false)
    var enabled: Boolean = true,

    @Column(nullable = false)
    val createdBy: UUID
) : AuditableEntity()
