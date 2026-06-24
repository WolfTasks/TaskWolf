package com.taskowolf.auth.infrastructure

import com.taskowolf.auth.application.SsoService
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.registration.ClientRegistrations
import org.springframework.stereotype.Component

@Component
class DbClientRegistrationRepository(private val ssoService: SsoService) : ClientRegistrationRepository {

    override fun findByRegistrationId(registrationId: String): ClientRegistration? {
        val config = ssoService.listEnabled().find { it.id.toString() == registrationId } ?: return null
        return ClientRegistrations.fromOidcIssuerLocation(config.issuerUrl)
            .registrationId(registrationId)
            .clientId(config.clientId)
            .clientSecret(ssoService.decryptSecret(config.clientSecretEnc))
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .scope("openid", "profile", "email")
            .clientName(config.name)
            .build()
    }
}
