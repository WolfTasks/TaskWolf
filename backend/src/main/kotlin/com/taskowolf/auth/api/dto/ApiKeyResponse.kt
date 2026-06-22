package com.taskowolf.auth.api.dto

import com.taskowolf.auth.domain.ApiKey
import java.time.Instant
import java.util.UUID

data class ApiKeyResponse(
    val id: UUID,
    val name: String,
    val keyPrefix: String,
    val lastUsedAt: Instant?,
    val expiresAt: Instant?,
    val createdAt: Instant?
) {
    companion object {
        fun from(k: ApiKey) = ApiKeyResponse(
            k.id, k.name, k.keyPrefix, k.lastUsedAt, k.expiresAt, k.createdAt
        )
    }
}
