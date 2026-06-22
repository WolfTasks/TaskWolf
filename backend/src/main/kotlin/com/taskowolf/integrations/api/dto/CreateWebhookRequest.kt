package com.taskowolf.integrations.api.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty

data class CreateWebhookRequest(
    @field:NotBlank val url: String,
    @field:NotEmpty val events: List<String>,
    val secret: String? = null,
    val enabled: Boolean = true
)
