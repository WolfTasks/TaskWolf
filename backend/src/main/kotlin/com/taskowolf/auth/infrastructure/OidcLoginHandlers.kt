package com.taskowolf.auth.infrastructure

import com.taskowolf.auth.application.OidcUserProvisioningService
import com.taskowolf.core.infrastructure.ForbiddenException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Ends the browser-side OIDC flow on SPA routes. Tokens travel in the URL fragment, which browsers never
 * send to a server, so they stay out of nginx/backend access logs and the Referer header. Targets are absolute
 * on the public base URL (TW_BASE_URL); a relative redirect would be made absolute from the proxied request
 * (wrong scheme behind TLS termination, backend port in dev).
 */
@Component
class OidcLoginHandlers(
    private val oidcUserProvisioningService: OidcUserProvisioningService,
    @Value("\${taskowolf.base-url}") baseUrl: String
) {
    private val spa = baseUrl.trimEnd('/')
    private val log = LoggerFactory.getLogger(javaClass)

    fun success(): AuthenticationSuccessHandler = AuthenticationSuccessHandler { request, response, authentication ->
        val oidcUser = authentication.principal as OidcUser
        val tokens = try {
            oidcUserProvisioningService.handleOidcLogin(oidcUser, registrationId(request))
        } catch (_: ForbiddenException) {
            response.sendRedirect("$spa/login?sso=denied")
            return@AuthenticationSuccessHandler
        } catch (e: RuntimeException) {
            // e.g. the IdP sent no email claim
            log.warn("OIDC login could not be completed: {}", e.message)
            response.sendRedirect("$spa/login?sso=failed")
            return@AuthenticationSuccessHandler
        }
        response.sendRedirect(
            "$spa/auth/sso/callback#accessToken=${enc(tokens.accessToken)}&refreshToken=${enc(tokens.refreshToken)}"
        )
    }

    fun failure(): AuthenticationFailureHandler = AuthenticationFailureHandler { _, response, _ ->
        response.sendRedirect("$spa/login?sso=failed")
    }

    // URI pattern: /login/oauth2/code/{registrationId}
    private fun registrationId(request: HttpServletRequest) = request.requestURI.substringAfterLast("/")

    private fun enc(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8)
}
