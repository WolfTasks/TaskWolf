package com.taskowolf.auth.api.dto

import jakarta.validation.constraints.NotBlank
import java.time.Instant

data class CreateApiKeyRequest(
    @field:NotBlank val name: String,
    val expiresAt: Instant? = null
)
