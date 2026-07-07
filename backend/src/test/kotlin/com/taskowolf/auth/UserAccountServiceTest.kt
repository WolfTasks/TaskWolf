package com.taskowolf.auth

import com.taskowolf.auth.application.AccessTokenService
import com.taskowolf.auth.application.RefreshTokenService
import com.taskowolf.auth.application.UserAccountService
import com.taskowolf.auth.domain.SystemRole
import com.taskowolf.auth.domain.User
import com.taskowolf.auth.infrastructure.UserRepository
import com.taskowolf.core.infrastructure.ConflictException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.*

class UserAccountServiceTest {

    private val userRepository = mockk<UserRepository>()
    private val accessTokenService = mockk<AccessTokenService>(relaxed = true)
    private val refreshTokenService = mockk<RefreshTokenService>(relaxed = true)
    private val service = UserAccountService(userRepository, accessTokenService, refreshTokenService)

    @Test
    fun `softDelete anonymizes user and revokes tokens`() {
        val user = User(email = "real@x.com", displayName = "Real", systemRole = SystemRole.MEMBER)
        every { userRepository.findById(user.id) } returns Optional.of(user)
        every { userRepository.save(any()) } returns user

        service.softDelete(user.id)

        assertFalse(user.active)
        assertNotNull(user.deletedAt)
        assertEquals("Deleted User", user.displayName)
        assertTrue(user.email.startsWith("deleted-"))
        assertNull(user.passwordHash)
        verify { accessTokenService.revokeAllForUser(user.id) }
        verify { refreshTokenService.revokeAllForUser(user.id) }
    }

    @Test
    fun `deactivate blocks the last active admin`() {
        val admin = User(email = "a@x.com", displayName = "A", systemRole = SystemRole.ADMIN)
        every { userRepository.findById(admin.id) } returns Optional.of(admin)
        every { userRepository.countBySystemRoleAndActiveTrue(SystemRole.ADMIN) } returns 1L

        assertThrows(ConflictException::class.java) { service.deactivate(admin.id) }
        assertTrue(admin.active)
    }

    @Test
    fun `deactivate allowed when other admins exist`() {
        val admin = User(email = "a2@x.com", displayName = "A2", systemRole = SystemRole.ADMIN)
        every { userRepository.findById(admin.id) } returns Optional.of(admin)
        every { userRepository.countBySystemRoleAndActiveTrue(SystemRole.ADMIN) } returns 2L
        every { userRepository.save(any()) } returns admin

        service.deactivate(admin.id)

        assertFalse(admin.active)
        verify { accessTokenService.revokeAllForUser(admin.id) }
        verify { refreshTokenService.revokeAllForUser(admin.id) }
    }

    @Test
    fun `activate throws ConflictException for a soft-deleted account`() {
        val user = User(email = "deleted-x@deleted.invalid", displayName = "Deleted User", systemRole = SystemRole.MEMBER)
        user.active = false
        user.deletedAt = Instant.now()
        every { userRepository.findById(user.id) } returns Optional.of(user)

        assertThrows<ConflictException> { service.activate(user.id) }
        assertFalse(user.active)
    }

    @Test
    fun `list returns only non-deleted users`() {
        val user = User(email = "real2@x.com", displayName = "Real2", systemRole = SystemRole.MEMBER)
        every { userRepository.findByDeletedAtIsNull() } returns listOf(user)

        val result = service.list()

        assertEquals(1, result.size)
        assertEquals(user.id, result[0].id)
        verify { userRepository.findByDeletedAtIsNull() }
    }
}
