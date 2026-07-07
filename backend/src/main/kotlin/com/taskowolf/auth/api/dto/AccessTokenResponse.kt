package com.taskowolf.auth.api.dto

import com.taskowolf.auth.domain.AccessToken
import com.taskowolf.auth.domain.TokenScope
import java.time.Instant
import java.util.UUID

data class AccessTokenResponse(
    val id: UUID,
    val name: String,
    val tokenPrefix: String,
    val scope: TokenScope,
    val lastUsedAt: Instant?,
    val expiresAt: Instant?,
    val createdAt: Instant?
) {
    companion object {
        fun from(t: AccessToken) = AccessTokenResponse(
            t.id, t.name, t.tokenPrefix, t.scope, t.lastUsedAt, t.expiresAt, t.createdAt
        )
    }
}
