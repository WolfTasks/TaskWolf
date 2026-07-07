package com.taskowolf.auth.api.dto

import com.taskowolf.auth.domain.TokenScope
import jakarta.validation.constraints.NotBlank
import java.time.Instant

data class CreateAccessTokenRequest(
    @field:NotBlank val name: String,
    val scope: TokenScope = TokenScope.READ_WRITE,
    val expiresAt: Instant? = null
)
