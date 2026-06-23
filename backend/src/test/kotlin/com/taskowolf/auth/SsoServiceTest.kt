package com.taskowolf.auth

import com.taskowolf.auth.application.SsoService
import com.taskowolf.auth.infrastructure.SsoConfigRepository
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SsoServiceTest {
    private val repo = mockk<SsoConfigRepository>(relaxed = true)
    private val service = SsoService(repo, jwtSecret = "this-is-a-32-byte-secret-for-aes!")

    @Test
    fun `encrypt and decrypt round-trip`() {
        val secret = "my-client-secret"
        val encrypted = service.encryptSecret(secret)
        assertNotEquals(secret, encrypted)
        assertEquals(secret, service.decryptSecret(encrypted))
    }

    @Test
    fun `createConfig saves with encrypted secret`() {
        every { repo.save(any()) } returnsArgument 0
        val config = service.createConfig("Okta", "https://issuer.example.com", "client-id", "plain-secret")
        assertNotEquals("plain-secret", config.clientSecretEnc)
        verify { repo.save(any()) }
    }
}
