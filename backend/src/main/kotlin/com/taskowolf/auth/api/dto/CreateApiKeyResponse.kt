package com.taskowolf.auth.api.dto

import java.util.UUID

data class CreateApiKeyResponse(
    val id: UUID,
    val name: String,
    val keyPrefix: String,
    val plaintext: String
)
