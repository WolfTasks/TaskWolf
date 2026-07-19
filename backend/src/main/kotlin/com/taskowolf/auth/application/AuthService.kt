package com.taskowolf.auth.application

import com.taskowolf.audit.application.SecurityAuditListener
import com.taskowolf.auth.api.dto.AuthResponse
import com.taskowolf.auth.api.dto.LoginRequest
import com.taskowolf.auth.api.dto.RegisterRequest
import com.taskowolf.auth.domain.SystemRole
import com.taskowolf.auth.domain.User
import com.taskowolf.auth.infrastructure.UserRepository
import com.taskowolf.core.infrastructure.ConflictException
import com.taskowolf.core.infrastructure.ForbiddenException
import com.taskowolf.core.infrastructure.NotFoundException
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val refreshTokenService: RefreshTokenService,
    @Value("\${taskowolf.auth.registration-enabled:true}") private val registrationEnabled: Boolean = true,
    private val securityAuditListener: SecurityAuditListener
) {
    @Transactional
    fun register(request: RegisterRequest): AuthResponse {
        if (!registrationEnabled) throw ForbiddenException.keyed("auth.registrationDisabled")
        if (userRepository.existsByEmail(request.email)) {
            throw ConflictException.keyed("auth.emailAlreadyRegistered", request.email)
        }
        val isFirstUser = userRepository.count() == 0L
        val user = userRepository.save(
            User(
                email = request.email,
                displayName = request.displayName,
                passwordHash = passwordEncoder.encode(request.password),
                systemRole = if (isFirstUser) SystemRole.ADMIN else SystemRole.MEMBER
            )
        )
        securityAuditListener.onRegister(user.email)
        return tokenPair(user.id)
    }

    @Transactional
    fun login(request: LoginRequest): AuthResponse {
        val user = userRepository.findByEmail(request.email)
        val hash = user?.passwordHash
        if (user == null || hash == null || !passwordEncoder.matches(request.password, hash)) {
            securityAuditListener.onLoginFailed(request.email, null)
            throw ForbiddenException.keyed("auth.invalidCredentials")
        }
        if (!user.active) {
            securityAuditListener.onLoginFailed(request.email, null)
            throw ForbiddenException.keyed("auth.accountDisabled")
        }
        val response = tokenPair(user.id)
        securityAuditListener.onLoginSuccess(user.email, null)
        return response
    }

    @Transactional
    fun refresh(refreshToken: String): AuthResponse {
        val userId = jwtService.validateToken(refreshToken, "refresh")
            ?: throw ForbiddenException.keyed("auth.invalidRefreshToken")
        refreshTokenService.consume(refreshToken)
        return tokenPair(userId)
    }

    @Transactional
    fun logout(userId: UUID) {
        refreshTokenService.revokeAllForUser(userId)
        val user = userRepository.findById(userId).orElse(null)
        user?.let { securityAuditListener.onLogout(it.email) }
    }

    @Transactional(readOnly = true)
    fun me(userId: UUID) = userRepository.findById(userId)
        .orElseThrow { NotFoundException.keyed("user.notFound") }

    private fun tokenPair(userId: UUID): AuthResponse {
        val refreshToken = jwtService.generateRefreshToken(userId)
        refreshTokenService.store(refreshToken, userId)
        return AuthResponse(
            accessToken = jwtService.generateAccessToken(userId),
            refreshToken = refreshToken
        )
    }
}
