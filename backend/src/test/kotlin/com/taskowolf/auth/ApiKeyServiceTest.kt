package com.taskowolf.auth

import com.taskowolf.auth.application.ApiKeyService
import com.taskowolf.auth.domain.ApiKey
import com.taskowolf.auth.domain.SystemRole
import com.taskowolf.auth.domain.User
import com.taskowolf.auth.infrastructure.ApiKeyRepository
import com.taskowolf.auth.infrastructure.UserRepository
import com.taskowolf.projects.application.ProjectService
import com.taskowolf.projects.domain.Project
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.*

class ApiKeyServiceTest {

    private val apiKeyRepository = mockk<ApiKeyRepository>()
    private val userRepository = mockk<UserRepository>()
    private val projectService = mockk<ProjectService>()
    private val service = ApiKeyService(apiKeyRepository, userRepository, projectService)

    private fun mockUser() = User(email = "test@test.com", displayName = "Test", systemRole = SystemRole.MEMBER)
    private fun mockProject(user: User) = Project(key = "WOLF", name = "Wolf", owner = user)

    @Test
    fun `generate returns plaintext starting with tw_ and stores hash only`() {
        val user = mockUser()
        val project = mockProject(user)
        every { projectService.requireAdmin("WOLF", user.id) } returns project
        val savedSlot = slot<ApiKey>()
        every { apiKeyRepository.save(capture(savedSlot)) } returnsArgument 0

        val response = service.generate("WOLF", "CI Key", null, user)

        assertTrue(response.plaintext.startsWith("tw_"), "plaintext must start with tw_")
        assertTrue(response.keyPrefix.startsWith("tw_"), "prefix must start with tw_")
        assertEquals(service.sha256(response.plaintext), savedSlot.captured.keyHash)
    }

    @Test
    fun `authenticate returns user for valid unexpired token`() {
        val user = mockUser()
        val token = "tw_validtoken1234567890123456"
        val storedKey = ApiKey(
            name = "test", keyHash = service.sha256(token), keyPrefix = "tw_validtoke",
            projectId = null, createdBy = user.id, expiresAt = null
        )
        every { apiKeyRepository.findByKeyHash(service.sha256(token)) } returns storedKey
        every { userRepository.findById(user.id) } returns Optional.of(user)
        every { apiKeyRepository.save(any()) } returns storedKey

        val result = service.authenticate(token)

        assertEquals(user.id, result?.id)
    }

    @Test
    fun `authenticate returns null for non-tw_ token`() {
        assertNull(service.authenticate("eyJhbGciOiJIUzI1NiJ9.some.jwt"))
    }

    @Test
    fun `authenticate returns null for expired key`() {
        val token = "tw_expiredtoken1234567890123"
        val expiredKey = ApiKey(
            name = "test", keyHash = service.sha256(token), keyPrefix = "tw_expiredto",
            projectId = null, createdBy = UUID.randomUUID(),
            expiresAt = Instant.now().minusSeconds(60)
        )
        every { apiKeyRepository.findByKeyHash(service.sha256(token)) } returns expiredKey

        assertNull(service.authenticate(token))
    }

    @Test
    fun `authenticate returns null for unknown token`() {
        val token = "tw_unknowntoken123456789012"
        every { apiKeyRepository.findByKeyHash(service.sha256(token)) } returns null

        assertNull(service.authenticate(token))
    }
}
