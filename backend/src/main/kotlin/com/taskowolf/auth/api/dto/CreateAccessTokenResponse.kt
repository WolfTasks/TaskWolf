package com.taskowolf.auth.api.dto

import com.taskowolf.auth.domain.TokenScope
import java.util.UUID

data class CreateAccessTokenResponse(
    val id: UUID,
    val name: String,
    val tokenPrefix: String,
    val scope: TokenScope,
    val plaintext: String
)
