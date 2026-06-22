package com.taskowolf.integrations.api.dto

import com.taskowolf.integrations.domain.Webhook
import java.time.Instant
import java.util.UUID

data class WebhookResponse(
    val id: UUID,
    val url: String,
    val events: List<String>,
    val enabled: Boolean,
    val createdAt: Instant?
) {
    companion object {
        fun from(w: Webhook) = WebhookResponse(w.id, w.url, w.events, w.enabled, w.createdAt)
    }
}
