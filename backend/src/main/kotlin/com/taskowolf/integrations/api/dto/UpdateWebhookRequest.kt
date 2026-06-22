package com.taskowolf.integrations.api.dto

data class UpdateWebhookRequest(
    val url: String? = null,
    val events: List<String>? = null,
    val enabled: Boolean? = null
)
