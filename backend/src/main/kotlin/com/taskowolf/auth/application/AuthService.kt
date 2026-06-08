package com.taskowolf.auth.application

import com.taskowolf.auth.api.dto.AuthResponse
import com.taskowolf.auth.api.dto.LoginRequest
import com.taskowolf.auth.api.dto.RegisterRequest
import com.taskowolf.auth.domain.User
import com.taskowolf.auth.infrastructure.UserRepository
import com.taskowolf.core.infrastructure.ConflictException
import com.taskowolf.core.infrastructure.ForbiddenException
import com.taskowolf.core.infrastructure.NotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService
) {
    @Transactional
    fun register(request: RegisterRequest): AuthResponse {
        if (userRepository.existsByEmail(request.email)) {
            throw ConflictException("Email already registered: ${request.email}")
        }
        val user = userRepository.save(
            User(
                email = request.email,
                displayName = request.displayName,
                passwordHash = passwordEncoder.encode(request.password)
            )
        )
        return tokenPair(user.id)
    }

    fun login(request: LoginRequest): AuthResponse {
        val user = userRepository.findByEmail(request.email)
            ?: throw NotFoundException("User not found")
        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw ForbiddenException("Invalid credentials")
        }
        return tokenPair(user.id)
    }

    fun refresh(refreshToken: String): AuthResponse {
        val userId = jwtService.validateToken(refreshToken)
            ?: throw ForbiddenException("Invalid refresh token")
        return tokenPair(userId)
    }

    fun me(userId: UUID) = userRepository.findById(userId)
        .orElseThrow { NotFoundException("User not found") }

    private fun tokenPair(userId: UUID) = AuthResponse(
        accessToken = jwtService.generateAccessToken(userId),
        refreshToken = jwtService.generateRefreshToken(userId)
    )
}
