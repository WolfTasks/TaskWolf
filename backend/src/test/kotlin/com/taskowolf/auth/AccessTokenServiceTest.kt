package com.taskowolf.auth

import com.taskowolf.auth.application.AccessTokenService
import com.taskowolf.auth.domain.AccessToken
import com.taskowolf.auth.domain.SystemRole
import com.taskowolf.auth.domain.TokenScope
import com.taskowolf.auth.domain.User
import com.taskowolf.auth.infrastructure.AccessTokenRepository
import com.taskowolf.auth.infrastructure.UserRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.time.Instant
import java.util.*

@ExtendWith(MockitoExtension::class)
class AccessTokenServiceTest {

    @Mock private lateinit var accessTokenRepository: AccessTokenRepository
    @Mock private lateinit var userRepository: UserRepository
    @InjectMocks private lateinit var service: AccessTokenService

    private fun mockUser(active: Boolean = true) =
        User(email = "t@t.com", displayName = "T", systemRole = SystemRole.MEMBER, active = active)

    @Test
    fun `create returns plaintext with twk_ prefix and stores hash only`() {
        val user = mockUser()
        whenever(accessTokenRepository.save(any<AccessToken>())).thenAnswer { it.arguments[0] as AccessToken }

        val res = service.create(user, "CI", TokenScope.READ_ONLY, null)

        assertTrue(res.plaintext.startsWith("twk_"), "plaintext must start with twk_")
        assertTrue(res.tokenPrefix.startsWith("twk_"), "prefix must start with twk_")
        assertEquals(TokenScope.READ_ONLY, res.scope)
        verify(accessTokenRepository).save(argThat { tokenHash == service.sha256(res.plaintext) })
    }

    @Test
    fun `authenticate returns user and scope for valid token`() {
        val user = mockUser()
        val raw = "twk_validtoken1234567890"
        val stored = AccessToken(
            userId = user.id, name = "t", tokenHash = service.sha256(raw),
            tokenPrefix = "twk_validto", scope = TokenScope.READ_WRITE
        )
        whenever(accessTokenRepository.findByTokenHash(service.sha256(raw))).thenReturn(stored)
        whenever(userRepository.findById(user.id)).thenReturn(Optional.of(user))
        whenever(accessTokenRepository.save(any<AccessToken>())).thenReturn(stored)

        val result = service.authenticate(raw)

        assertEquals(user.id, result?.user?.id)
        assertEquals(TokenScope.READ_WRITE, result?.scope)
    }

    @Test
    fun `authenticate returns null for non-twk token`() {
        assertNull(service.authenticate("tw_projectkey123456"))
        assertNull(service.authenticate("eyJhbGciOiJIUzI1NiJ9.some.jwt"))
    }

    @Test
    fun `authenticate returns null for revoked token`() {
        val raw = "twk_revoked1234567890"
        val stored = AccessToken(
            userId = UUID.randomUUID(), name = "t", tokenHash = service.sha256(raw),
            tokenPrefix = "twk_revoked", scope = TokenScope.READ_WRITE, revokedAt = Instant.now()
        )
        whenever(accessTokenRepository.findByTokenHash(service.sha256(raw))).thenReturn(stored)

        assertNull(service.authenticate(raw))
    }

    @Test
    fun `authenticate returns null for expired token`() {
        val raw = "twk_expired1234567890"
        val stored = AccessToken(
            userId = UUID.randomUUID(), name = "t", tokenHash = service.sha256(raw),
            tokenPrefix = "twk_expired", scope = TokenScope.READ_WRITE,
            expiresAt = Instant.now().minusSeconds(60)
        )
        whenever(accessTokenRepository.findByTokenHash(service.sha256(raw))).thenReturn(stored)

        assertNull(service.authenticate(raw))
    }

    @Test
    fun `authenticate returns null for inactive user`() {
        val user = mockUser(active = false)
        val raw = "twk_inactiveuser1234"
        val stored = AccessToken(
            userId = user.id, name = "t", tokenHash = service.sha256(raw),
            tokenPrefix = "twk_inactiv", scope = TokenScope.READ_WRITE
        )
        whenever(accessTokenRepository.findByTokenHash(service.sha256(raw))).thenReturn(stored)
        whenever(userRepository.findById(user.id)).thenReturn(Optional.of(user))

        assertNull(service.authenticate(raw))
    }
}
