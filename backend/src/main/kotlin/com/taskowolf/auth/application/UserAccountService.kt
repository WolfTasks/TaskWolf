package com.taskowolf.auth.application

import com.taskowolf.audit.application.SecurityAuditListener
import com.taskowolf.auth.api.dto.AdminUserResponse
import com.taskowolf.auth.domain.SystemRole
import com.taskowolf.auth.domain.User
import com.taskowolf.auth.infrastructure.UserRepository
import com.taskowolf.core.infrastructure.ConflictException
import com.taskowolf.core.infrastructure.NotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class UserAccountService(
    private val userRepository: UserRepository,
    private val accessTokenService: AccessTokenService,
    private val refreshTokenService: RefreshTokenService,
    private val passwordEncoder: PasswordEncoder,
    private val securityAuditListener: SecurityAuditListener
) {
    @Transactional(readOnly = true)
    fun list(): List<AdminUserResponse> = userRepository.findByDeletedAtIsNull().map { AdminUserResponse.from(it) }

    @Transactional
    fun deactivate(userId: UUID) {
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User not found") }
        requireNotLastActiveAdmin(user)
        user.active = false
        userRepository.save(user)
        accessTokenService.revokeAllForUser(userId)
        refreshTokenService.revokeAllForUser(userId)
    }

    @Transactional
    fun activate(userId: UUID) {
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User not found") }
        if (user.deletedAt != null) {
            throw ConflictException("Cannot reactivate a deleted account")
        }
        user.active = true
        userRepository.save(user)
    }

    @Transactional
    fun softDelete(userId: UUID) {
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User not found") }
        requireNotLastActiveAdmin(user)
        user.active = false
        user.deletedAt = Instant.now()
        user.email = "deleted-${user.id}@deleted.invalid"
        user.displayName = "Deleted User"
        user.passwordHash = null
        user.oauthProvider = null
        user.oauthSubject = null
        user.avatarUrl = null
        userRepository.save(user)
        accessTokenService.revokeAllForUser(userId)
        refreshTokenService.revokeAllForUser(userId)
    }

    @Transactional
    fun updateProfile(userId: UUID, displayName: String): User {
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User not found") }
        user.displayName = displayName
        val saved = userRepository.save(user)
        securityAuditListener.onProfileUpdated(user.email)
        return saved
    }

    private fun requireNotLastActiveAdmin(user: User) {
        if (user.systemRole == SystemRole.ADMIN && user.active) {
            if (userRepository.countBySystemRoleAndActiveTrue(SystemRole.ADMIN) <= 1) {
                throw ConflictException("Cannot deactivate or delete the last active admin")
            }
        }
    }
}
