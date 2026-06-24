package com.taskowolf.auth

import com.taskowolf.auth.application.SsoService
import com.taskowolf.auth.domain.SsoConfig
import com.taskowolf.auth.infrastructure.DbClientRegistrationRepository
import io.mockk.every
import io.mockk.mockk
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DbClientRegistrationRepositoryTest {

    private val ssoService = mockk<SsoService>()
    private val repo = DbClientRegistrationRepository(ssoService)
    private val server = MockWebServer()

    @BeforeEach
    fun setUp() = server.start()

    @AfterEach
    fun tearDown() = server.shutdown()

    @Test
    fun `findByRegistrationId returns null for unknown id`() {
        every { ssoService.listEnabled() } returns emptyList()
        assertNull(repo.findByRegistrationId("unknown"))
    }

    @Test
    fun `findByRegistrationId discovers endpoints from well-known openid-configuration`() {
        val baseUrl = server.url("").toString().trimEnd('/')
        val discoveryJson = """
            {
              "issuer": "$baseUrl",
              "authorization_endpoint": "$baseUrl/authorize",
              "token_endpoint": "$baseUrl/token",
              "jwks_uri": "$baseUrl/jwks",
              "userinfo_endpoint": "$baseUrl/userinfo",
              "response_types_supported": ["code"],
              "subject_types_supported": ["public"],
              "id_token_signing_alg_values_supported": ["RS256"]
            }
        """.trimIndent()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(discoveryJson)
        )

        val config = mockk<SsoConfig>(relaxed = true)
        every { config.id.toString() } returns "cfg-id"
        every { config.issuerUrl } returns baseUrl
        every { config.clientId } returns "client-id"
        every { config.clientSecretEnc } returns "enc"
        every { config.name } returns "My IdP"
        every { ssoService.listEnabled() } returns listOf(config)
        every { ssoService.decryptSecret("enc") } returns "plain-secret"

        val reg = repo.findByRegistrationId("cfg-id")

        assertNotNull(reg)
        assertEquals("client-id", reg!!.clientId)
        assertEquals("plain-secret", reg.clientSecret)
        assertEquals("$baseUrl/authorize", reg.providerDetails.authorizationUri)
        assertEquals("$baseUrl/token", reg.providerDetails.tokenUri)
        assertEquals("$baseUrl/userinfo", reg.providerDetails.userInfoEndpoint.uri)
    }
}
