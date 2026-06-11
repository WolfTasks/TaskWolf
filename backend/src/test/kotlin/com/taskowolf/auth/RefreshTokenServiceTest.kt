package com.taskowolf.auth

import com.taskowolf.auth.application.RefreshTokenService
import com.taskowolf.auth.domain.RefreshToken
import com.taskowolf.auth.infrastructure.RefreshTokenRepository
import com.taskowolf.core.infrastructure.ForbiddenException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

class RefreshTokenServiceTest {

    private val repository = mockk<RefreshTokenRepository>(relaxed = true)
    private val service = RefreshTokenService(repository, refreshExpiry = 604800)
    private val userId = UUID.randomUUID()

    @Test
    fun `consume revokes a valid token (rotation)`() {
        val stored = RefreshToken(
            tokenHash = service.hash("the-token"), userId = userId,
            expiresAt = Instant.now().plusSeconds(3600)
        )
        every { repository.findByTokenHash(service.hash("the-token")) } returns stored
        every { repository.save(any<RefreshToken>()) } returnsArgument 0

        service.consume("the-token")

        assert(stored.revoked)
        verify { repository.save(stored) }
    }

    @Test
    fun `consume rejects an unknown token`() {
        every { repository.findByTokenHash(any()) } returns null
        assertThrows<ForbiddenException> { service.consume("unknown") }
    }

    @Test
    fun `consume rejects an already revoked token`() {
        val stored = RefreshToken(
            tokenHash = service.hash("re-used"), userId = userId,
            expiresAt = Instant.now().plusSeconds(3600), revoked = true
        )
        every { repository.findByTokenHash(service.hash("re-used")) } returns stored
        assertThrows<ForbiddenException> { service.consume("re-used") }
    }

    @Test
    fun `consume rejects an expired token`() {
        val stored = RefreshToken(
            tokenHash = service.hash("old"), userId = userId,
            expiresAt = Instant.now().minusSeconds(60)
        )
        every { repository.findByTokenHash(service.hash("old")) } returns stored
        assertThrows<ForbiddenException> { service.consume("old") }
    }
}
