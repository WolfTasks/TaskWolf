package com.taskowolf.auth

import com.taskowolf.auth.application.AuthService
import com.taskowolf.auth.application.RefreshTokenService
import com.taskowolf.auth.api.dto.RegisterRequest
import com.taskowolf.auth.infrastructure.UserRepository
import com.taskowolf.core.infrastructure.ConflictException
import com.taskowolf.core.infrastructure.ForbiddenException
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.crypto.password.PasswordEncoder

class AuthServiceTest {

    private val userRepository = mockk<UserRepository>()
    private val passwordEncoder = mockk<PasswordEncoder>()
    private val jwtService = mockk<com.taskowolf.auth.application.JwtService>()
    private val refreshTokenService = mockk<RefreshTokenService>(relaxed = true)
    private val authService = AuthService(userRepository, passwordEncoder, jwtService, refreshTokenService)

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
}
