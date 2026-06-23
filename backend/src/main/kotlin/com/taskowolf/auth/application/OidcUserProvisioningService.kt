package com.taskowolf.auth.application

import com.taskowolf.auth.domain.SystemRole
import com.taskowolf.auth.domain.User
import com.taskowolf.auth.infrastructure.SsoConfigRepository
import com.taskowolf.auth.infrastructure.UserRepository
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class OidcUserProvisioningService(
    private val userRepository: UserRepository,
    private val ssoConfigRepository: SsoConfigRepository,
    private val jwtService: JwtService,
    private val refreshTokenService: RefreshTokenService
) {
    @Transactional
    fun handleOidcLogin(oidcUser: OidcUser, registrationId: String): String {
        val email = oidcUser.email ?: error("OIDC user has no email")
        val config = ssoConfigRepository.findById(UUID.fromString(registrationId)).orElse(null)
        val user = userRepository.findByEmail(email) ?: run {
            check(config?.autoProvision != false) { "Auto-provisioning disabled" }
            userRepository.save(
                User(
                    email = email,
                    displayName = oidcUser.fullName ?: email,
                    avatarUrl = oidcUser.picture,
                    oauthProvider = "oidc",
                    oauthSubject = oidcUser.subject,
                    systemRole = SystemRole.MEMBER
                )
            )
        }
        val refreshToken = jwtService.generateRefreshToken(user.id)
        refreshTokenService.store(refreshToken, user.id)
        return jwtService.generateAccessToken(user.id)
    }
}
