package com.taskowolf.integrations.domain

import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "webhook_deliveries")
class WebhookDelivery(
    @Column(name = "webhook_id", nullable = false)
    val webhookId: UUID,

    @Column(nullable = false)
    val eventType: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    val payload: String,

    @Column
    var responseStatus: Int? = null,

    @Column(columnDefinition = "TEXT")
    var responseBody: String? = null,

    @Column(nullable = false)
    var attemptCount: Int = 0,

    @Column
    var nextRetryAt: Instant? = null,

    @Column
    var deliveredAt: Instant? = null
) : AuditableEntity()
