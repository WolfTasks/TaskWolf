package com.taskowolf.auth

import com.taskowolf.audit.application.SecurityAuditListener
import com.taskowolf.auth.application.AuthService
import com.taskowolf.auth.application.RefreshTokenService
import com.taskowolf.auth.api.dto.RegisterRequest
import com.taskowolf.auth.infrastructure.UserRepository
import com.taskowolf.core.infrastructure.ConflictException
import com.taskowolf.core.infrastructure.ForbiddenException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Assertions.assertEquals
import org.springframework.security.crypto.password.PasswordEncoder

class AuthServiceTest {

    private val userRepository = mockk<UserRepository>()
    private val passwordEncoder = mockk<PasswordEncoder>()
    private val jwtService = mockk<com.taskowolf.auth.application.JwtService>()
    private val refreshTokenService = mockk<RefreshTokenService>(relaxed = true)
    private val securityAuditListener = mockk<SecurityAuditListener>(relaxed = true)
    private val authService = AuthService(userRepository, passwordEncoder, jwtService, refreshTokenService, registrationEnabled = true, securityAuditListener = securityAuditListener)

    @Test
    fun `register throws ConflictException when email already exists`() {
        every { userRepository.existsByEmail("test@example.com") } returns true

        assertThrows<ConflictException> {
            authService.register(RegisterRequest("test@example.com", "Test User", "password123"))
        }
    }

    @Test
    fun `register creates user and returns tokens`() {
        every { userRepository.existsByEmail("new@example.com") } returns false
        every { userRepository.count() } returns 1L
        every { passwordEncoder.encode(any()) } returns "hashed"
        every { userRepository.save(any()) } returnsArgument 0
        every { jwtService.generateAccessToken(any()) } returns "access-token"
        every { jwtService.generateRefreshToken(any()) } returns "refresh-token"

        val result = authService.register(RegisterRequest("new@example.com", "New User", "password123"))

        assert(result.accessToken == "access-token")
        assert(result.refreshToken == "refresh-token")
    }

    @Test
    fun `refresh rejects access token`() {
        every { jwtService.validateToken("some-access-token", "refresh") } returns null

        assertThrows<ForbiddenException> {
            authService.refresh("some-access-token")
        }
    }

    @Test
    fun `register throws ForbiddenException when registration is disabled`() {
        val disabledService = AuthService(userRepository, passwordEncoder, jwtService, refreshTokenService, registrationEnabled = false, securityAuditListener = securityAuditListener)

        assertThrows<ForbiddenException> {
            disabledService.register(RegisterRequest("new@example.com", "New User", "password123"))
        }
    }

    @Test
    fun `register promotes first user to ADMIN`() {
        every { userRepository.existsByEmail("first@example.com") } returns false
        every { userRepository.count() } returns 0L
        every { passwordEncoder.encode(any()) } returns "hashed"
        every { jwtService.generateAccessToken(any()) } returns "access-token"
        every { jwtService.generateRefreshToken(any()) } returns "refresh-token"

        val slot = slot<com.taskowolf.auth.domain.User>()
        every { userRepository.save(capture(slot)) } returnsArgument 0

        authService.register(RegisterRequest("first@example.com", "First User", "password123"))

        assertEquals(com.taskowolf.auth.domain.SystemRole.ADMIN, slot.captured.systemRole)
    }
}
