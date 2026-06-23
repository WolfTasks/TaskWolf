package com.taskowolf.auth

import com.taskowolf.auth.application.SsoService
import com.taskowolf.auth.domain.SsoConfig
import com.taskowolf.auth.infrastructure.DbClientRegistrationRepository
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DbClientRegistrationRepositoryTest {
    private val ssoService = mockk<SsoService>()
    private val repo = DbClientRegistrationRepository(ssoService)

    @Test
    fun `findByRegistrationId returns null for unknown id`() {
        every { ssoService.listEnabled() } returns emptyList()
        assertNull(repo.findByRegistrationId("unknown"))
    }

    @Test
    fun `findByRegistrationId returns registration for known config`() {
        val config = mockk<SsoConfig>(relaxed = true)
        every { config.id.toString() } returns "cfg-id"
        every { config.issuerUrl } returns "https://issuer.example.com"
        every { config.clientId } returns "client-id"
        every { ssoService.listEnabled() } returns listOf(config)
        every { ssoService.decryptSecret(any()) } returns "secret"
        assertNotNull(repo.findByRegistrationId("cfg-id"))
    }
}
