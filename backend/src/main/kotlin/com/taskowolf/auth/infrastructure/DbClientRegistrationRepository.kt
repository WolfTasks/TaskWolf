package com.taskowolf.auth.infrastructure

import com.taskowolf.auth.application.SsoService
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames
import org.springframework.stereotype.Component

@Component
class DbClientRegistrationRepository(private val ssoService: SsoService) : ClientRegistrationRepository {
    override fun findByRegistrationId(registrationId: String): ClientRegistration? {
        val config = ssoService.listEnabled().find { it.id.toString() == registrationId } ?: return null
        return ClientRegistration.withRegistrationId(registrationId)
            .clientId(config.clientId)
            .clientSecret(ssoService.decryptSecret(config.clientSecretEnc))
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .scope("openid", "profile", "email")
            .authorizationUri("${config.issuerUrl}/protocol/openid-connect/auth")
            .tokenUri("${config.issuerUrl}/protocol/openid-connect/token")
            .userInfoUri("${config.issuerUrl}/protocol/openid-connect/userinfo")
            .userNameAttributeName(IdTokenClaimNames.SUB)
            .issuerUri(config.issuerUrl)
            .clientName(config.name)
            .build()
    }
}
