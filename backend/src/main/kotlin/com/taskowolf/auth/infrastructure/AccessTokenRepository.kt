package com.taskowolf.auth.infrastructure

import com.taskowolf.auth.domain.AccessToken
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AccessTokenRepository : JpaRepository<AccessToken, UUID> {
    fun findByTokenHash(tokenHash: String): AccessToken?
    fun findByUserIdAndRevokedAtIsNull(userId: UUID): List<AccessToken>
    fun findByIdAndUserId(id: UUID, userId: UUID): AccessToken?
}
