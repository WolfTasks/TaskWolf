package com.taskowolf.auth.application

import com.taskowolf.auth.domain.SystemRole
import com.taskowolf.auth.domain.User
import com.taskowolf.auth.infrastructure.SsoConfigRepository
import com.taskowolf.auth.infrastructure.UserRepository
import com.taskowolf.core.infrastructure.ForbiddenException
import com.taskowolf.organizations.domain.OrgRole
import com.taskowolf.organizations.domain.OrganizationMember
import com.taskowolf.organizations.domain.OrganizationMemberId
import com.taskowolf.organizations.infrastructure.OrganizationMemberRepository
import com.taskowolf.organizations.infrastructure.OrganizationRepository
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class OidcUserProvisioningService(
    private val userRepository: UserRepository,
    private val ssoConfigRepository: SsoConfigRepository,
    private val jwtService: JwtService,
    private val refreshTokenService: RefreshTokenService,
    private val orgRepo: OrganizationRepository,
    private val orgMemberRepo: OrganizationMemberRepository
) {
    @Transactional
    fun handleOidcLogin(oidcUser: OidcUser, registrationId: String): String {
        val email = oidcUser.email ?: error("OIDC user has no email")
        val config = ssoConfigRepository.findById(UUID.fromString(registrationId)).orElse(null)
        val user = userRepository.findByEmail(email) ?: run {
            if (config?.autoProvision == false) throw ForbiddenException.keyed("auth.autoProvisionDisabled")
            val newUser = userRepository.save(
                User(
                    email = email,
                    displayName = oidcUser.fullName ?: email,
                    avatarUrl = oidcUser.picture,
                    oauthProvider = "oidc",
                    oauthSubject = oidcUser.subject,
                    systemRole = SystemRole.MEMBER
                )
            )
            // Assign to default org
            val defaultOrg = orgRepo.findBySlug("default")
            if (defaultOrg != null) {
                orgMemberRepo.save(OrganizationMember(
                    OrganizationMemberId(defaultOrg.id, newUser.id),
                    OrgRole.MEMBER
                ))
            }
            newUser
        }
        val defaultOrg = orgRepo.findBySlug("default")
        val refreshToken = jwtService.generateRefreshToken(user.id)
        refreshTokenService.store(refreshToken, user.id)
        return jwtService.generateAccessToken(user.id, defaultOrg?.id)
    }
}
