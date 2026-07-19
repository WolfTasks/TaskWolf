package com.taskowolf.auth.application

import com.taskowolf.auth.api.dto.AccessTokenResponse
import com.taskowolf.auth.api.dto.CreateAccessTokenResponse
import com.taskowolf.auth.domain.AccessToken
import com.taskowolf.auth.domain.TokenScope
import com.taskowolf.auth.domain.User
import com.taskowolf.auth.infrastructure.AccessTokenRepository
import com.taskowolf.auth.infrastructure.UserRepository
import com.taskowolf.core.infrastructure.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Service
class AccessTokenService(
    private val accessTokenRepository: AccessTokenRepository,
    private val userRepository: UserRepository
) {
    @Transactional
    fun create(user: User, name: String, scope: TokenScope, expiresAt: Instant?): CreateAccessTokenResponse {
        val plaintext = "twk_" + secureToken()
        val token = accessTokenRepository.save(
            AccessToken(
                userId = user.id,
                name = name,
                tokenHash = sha256(plaintext),
                tokenPrefix = plaintext.take(12),
                scope = scope,
                expiresAt = expiresAt
            )
        )
        return CreateAccessTokenResponse(token.id, token.name, token.tokenPrefix, token.scope, plaintext)
    }

    @Transactional(readOnly = true)
    fun list(user: User): List<AccessTokenResponse> =
        accessTokenRepository.findByUserIdAndRevokedAtIsNull(user.id).map { AccessTokenResponse.from(it) }

    @Transactional
    fun revoke(user: User, tokenId: UUID) {
        val token = accessTokenRepository.findByIdAndUserId(tokenId, user.id)
            ?: throw NotFoundException.keyed("auth.accessTokenNotFound", tokenId)
        if (token.revokedAt == null) {
            token.revokedAt = Instant.now()
            accessTokenRepository.save(token)
        }
    }

    @Transactional
    fun revokeAllForUser(userId: UUID) {
        val now = Instant.now()
        accessTokenRepository.findByUserIdAndRevokedAtIsNull(userId).forEach {
            it.revokedAt = now
            accessTokenRepository.save(it)
        }
    }

    @Transactional
    fun authenticate(rawToken: String): AuthenticatedToken? {
        if (!rawToken.startsWith("twk_")) return null
        val token = accessTokenRepository.findByTokenHash(sha256(rawToken)) ?: return null
        if (token.revokedAt != null) return null
        val expiresAt = token.expiresAt
        if (expiresAt != null && expiresAt.isBefore(Instant.now())) return null
        val user = userRepository.findById(token.userId).orElse(null) ?: return null
        if (!user.active) return null
        token.lastUsedAt = Instant.now()
        accessTokenRepository.save(token)
        return AuthenticatedToken(user, token.scope)
    }

    fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun secureToken(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
