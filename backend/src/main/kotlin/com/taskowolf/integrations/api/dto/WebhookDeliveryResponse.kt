package com.taskowolf.integrations.api.dto

import com.taskowolf.integrations.domain.WebhookDelivery
import java.time.Instant
import java.util.UUID

data class WebhookDeliveryResponse(
    val id: UUID,
    val webhookId: UUID,
    val eventType: String,
    val payload: String,
    val responseStatus: Int?,
    val responseBody: String?,
    val attemptCount: Int,
    val deliveredAt: Instant?,
    val createdAt: Instant?
) {
    companion object {
        fun from(d: WebhookDelivery) = WebhookDeliveryResponse(
            d.id, d.webhookId, d.eventType, d.payload,
            d.responseStatus, d.responseBody, d.attemptCount, d.deliveredAt, d.createdAt
        )
    }
}
