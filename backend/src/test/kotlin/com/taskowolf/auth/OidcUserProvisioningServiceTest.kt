package com.taskowolf.auth

import com.taskowolf.auth.application.JwtService
import com.taskowolf.auth.application.OidcUserProvisioningService
import com.taskowolf.auth.application.RefreshTokenService
import com.taskowolf.auth.application.SsoTokens
import com.taskowolf.auth.domain.SsoConfig
import com.taskowolf.auth.domain.User
import com.taskowolf.auth.infrastructure.SsoConfigRepository
import com.taskowolf.auth.infrastructure.UserRepository
import com.taskowolf.core.infrastructure.ForbiddenException
import com.taskowolf.organizations.infrastructure.OrganizationMemberRepository
import com.taskowolf.organizations.infrastructure.OrganizationRepository
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import java.util.Optional
import java.util.UUID

class OidcUserProvisioningServiceTest {

    private val userRepository = mockk<UserRepository>()
    private val ssoConfigRepository = mockk<SsoConfigRepository>()
    private val jwtService = mockk<JwtService>()
    private val refreshTokenService = mockk<RefreshTokenService>()
    private val orgRepo = mockk<OrganizationRepository>()
    private val orgMemberRepo = mockk<OrganizationMemberRepository>()
    private val service = OidcUserProvisioningService(
        userRepository, ssoConfigRepository, jwtService, refreshTokenService, orgRepo, orgMemberRepo
    )

    @Test
    fun `handleOidcLogin throws ForbiddenException when auto-provisioning is disabled for an unknown email`() {
        val registrationId = UUID.randomUUID()
        val config = mockk<SsoConfig>()
        every { config.autoProvision } returns false
        every { ssoConfigRepository.findById(registrationId) } returns Optional.of(config)
        every { userRepository.findByEmail("new-user@test.com") } returns null

        val oidcUser = mockk<OidcUser>()
        every { oidcUser.email } returns "new-user@test.com"

        assertThrows<ForbiddenException> {
            service.handleOidcLogin(oidcUser, registrationId.toString())
        }
    }

    @Test
    fun `handleOidcLogin returns access and the stored refresh token`() {
        val registrationId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val user = mockk<User>()
        every { user.id } returns userId
        every { ssoConfigRepository.findById(registrationId) } returns Optional.empty()
        every { userRepository.findByEmail("known@test.com") } returns user
        every { orgRepo.findBySlug("default") } returns null
        every { jwtService.generateRefreshToken(userId) } returns "refresh-1"
        every { refreshTokenService.store("refresh-1", userId) } just Runs
        every { jwtService.generateAccessToken(userId, null) } returns "access-1"

        val oidcUser = mockk<OidcUser>()
        every { oidcUser.email } returns "known@test.com"

        assertEquals(SsoTokens("access-1", "refresh-1"), service.handleOidcLogin(oidcUser, registrationId.toString()))
        verify { refreshTokenService.store("refresh-1", userId) }
    }
}
