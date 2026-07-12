package com.taskowolf.auth

import com.taskowolf.auth.application.AccessTokenService
import com.taskowolf.auth.application.RefreshTokenService
import com.taskowolf.auth.application.UserAccountService
import com.taskowolf.auth.domain.User
import com.taskowolf.auth.infrastructure.UserRepository
import com.taskowolf.core.infrastructure.BadRequestException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.Optional
import java.util.UUID

class UserLanguageServiceTest {

    private val userRepository = mockk<UserRepository>()
    private val accessTokenService = mockk<AccessTokenService>(relaxed = true)
    private val refreshTokenService = mockk<RefreshTokenService>(relaxed = true)
    private val passwordEncoder = mockk<PasswordEncoder>(relaxed = true)
    private val securityAuditListener = mockk<com.taskowolf.audit.application.SecurityAuditListener>(relaxed = true)
    private val service = UserAccountService(
        userRepository, accessTokenService, refreshTokenService, passwordEncoder, securityAuditListener
    )

    @Test
    fun `updateLanguage persists a supported language`() {
        val id = UUID.randomUUID()
        val user = User(email = "a@b.c", displayName = "A")
        every { userRepository.findById(id) } returns Optional.of(user)
        val saved = slot<User>()
        every { userRepository.save(capture(saved)) } answers { saved.captured }

        service.updateLanguage(id, "de")

        assertEquals("de", saved.captured.language)
        verify { userRepository.save(any()) }
    }

    @Test
    fun `updateLanguage rejects an unsupported language`() {
        val id = UUID.randomUUID()
        every { userRepository.findById(id) } returns Optional.of(User(email = "a@b.c", displayName = "A"))
        assertThrows<BadRequestException> { service.updateLanguage(id, "fr") }
    }
}
