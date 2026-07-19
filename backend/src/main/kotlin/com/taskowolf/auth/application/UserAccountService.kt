package com.taskowolf.auth.application

import com.taskowolf.audit.application.SecurityAuditListener
import com.taskowolf.auth.api.dto.AdminUserResponse
import com.taskowolf.auth.domain.SystemRole
import com.taskowolf.auth.domain.User
import com.taskowolf.auth.infrastructure.UserRepository
import com.taskowolf.core.infrastructure.BadRequestException
import com.taskowolf.core.infrastructure.ConflictException
import com.taskowolf.core.infrastructure.ForbiddenException
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
        val user = userRepository.findById(userId).orElseThrow { NotFoundException.keyed("user.notFound") }
        requireNotLastActiveAdmin(user)
        user.active = false
        userRepository.save(user)
        accessTokenService.revokeAllForUser(userId)
        refreshTokenService.revokeAllForUser(userId)
    }

    @Transactional
    fun activate(userId: UUID) {
        val user = userRepository.findById(userId).orElseThrow { NotFoundException.keyed("user.notFound") }
        if (user.deletedAt != null) {
            throw ConflictException.keyed("auth.cannotReactivateDeleted")
        }
        user.active = true
        userRepository.save(user)
    }

    @Transactional
    fun softDelete(userId: UUID) {
        val user = userRepository.findById(userId).orElseThrow { NotFoundException.keyed("user.notFound") }
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
        val user = userRepository.findById(userId).orElseThrow { NotFoundException.keyed("user.notFound") }
        user.displayName = displayName
        val saved = userRepository.save(user)
        securityAuditListener.onProfileUpdated(user.email)
        return saved
    }

    @Transactional
    fun changePassword(userId: UUID, currentPassword: String, newPassword: String) {
        val user = userRepository.findById(userId).orElseThrow { NotFoundException.keyed("user.notFound") }
        val hash = user.passwordHash
            ?: throw ConflictException.keyed("auth.noPasswordSet")
        if (!passwordEncoder.matches(currentPassword, hash)) {
            throw ForbiddenException.keyed("auth.currentPasswordIncorrect")
        }
        user.passwordHash = passwordEncoder.encode(newPassword)
        userRepository.save(user)
        refreshTokenService.revokeAllForUser(userId)
        securityAuditListener.onPasswordChanged(user.email)
    }

    @Transactional
    fun updateLanguage(userId: UUID, language: String): User {
        if (language !in SUPPORTED_LANGUAGES) {
            throw BadRequestException.keyed("auth.unsupportedLanguage")
        }
        val user = userRepository.findById(userId).orElseThrow { NotFoundException.keyed("user.notFound") }
        user.language = language
        return userRepository.save(user)
    }

    private fun requireNotLastActiveAdmin(user: User) {
        if (user.systemRole == SystemRole.ADMIN && user.active) {
            if (userRepository.countBySystemRoleAndActiveTrue(SystemRole.ADMIN) <= 1) {
                throw ConflictException.keyed("auth.lastAdmin")
            }
        }
    }

    companion object {
        private val SUPPORTED_LANGUAGES = setOf("en", "de")
    }
}
