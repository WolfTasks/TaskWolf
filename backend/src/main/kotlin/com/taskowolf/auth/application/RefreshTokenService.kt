package com.taskowolf.auth.application

import com.taskowolf.auth.domain.RefreshToken
import com.taskowolf.auth.infrastructure.RefreshTokenRepository
import com.taskowolf.core.infrastructure.ForbiddenException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

@Service
class RefreshTokenService(
    private val repository: RefreshTokenRepository,
    @Value("\${taskowolf.jwt.refresh-token-expiry}") private val refreshExpiry: Long
) {
    fun store(token: String, userId: UUID) {
        repository.save(
            RefreshToken(
                tokenHash = hash(token),
                userId = userId,
                expiresAt = Instant.now().plusSeconds(refreshExpiry)
            )
        )
    }

    @Transactional
    fun consume(token: String) {
        val stored = repository.findByTokenHash(hash(token))
            ?: throw ForbiddenException("Invalid refresh token")
        if (stored.revoked || stored.expiresAt.isBefore(Instant.now())) {
            throw ForbiddenException("Invalid refresh token")
        }
        stored.revoked = true
        repository.save(stored)
    }

    @Transactional
    fun revokeAllForUser(userId: UUID) {
        repository.revokeAllByUserId(userId)
    }

    fun hash(token: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
