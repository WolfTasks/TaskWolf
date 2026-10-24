package com.taskowolf.auth

import com.taskowolf.auth.application.OidcUserProvisioningService
import com.taskowolf.auth.application.SsoTokens
import com.taskowolf.auth.infrastructure.OidcLoginHandlers
import com.taskowolf.core.infrastructure.ForbiddenException
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.oidc.user.OidcUser

class OidcLoginHandlersTest {

    private val provisioning = mockk<OidcUserProvisioningService>()
    private val handlers = OidcLoginHandlers(provisioning, "https://tasks.example.com/")
    private val oidcUser = mockk<OidcUser>()
    private val auth = TestingAuthenticationToken(oidcUser, null)

    private fun callback(registrationId: String) =
        MockHttpServletRequest("GET", "/login/oauth2/code/$registrationId").apply { requestURI = "/login/oauth2/code/$registrationId" }

    @Test
    fun `success redirects to the SPA callback with both tokens in the fragment, not the query`() {
        every { provisioning.handleOidcLogin(oidcUser, "reg-1") } returns SsoTokens("a.b.c", "r+/=")
        val response = MockHttpServletResponse()

        handlers.success().onAuthenticationSuccess(callback("reg-1"), response, auth)

        assertEquals("https://tasks.example.com/auth/sso/callback#accessToken=a.b.c&refreshToken=r%2B%2F%3D", response.redirectedUrl)
    }

    @Test
    fun `disabled auto-provisioning sends the user back to the login page instead of an error body`() {
        every { provisioning.handleOidcLogin(oidcUser, "reg-1") } throws ForbiddenException.keyed("auth.autoProvisionDisabled")
        val response = MockHttpServletResponse()

        handlers.success().onAuthenticationSuccess(callback("reg-1"), response, auth)

        assertEquals("https://tasks.example.com/login?sso=denied", response.redirectedUrl)
    }

    @Test
    fun `provisioning errors such as a missing email claim end on the login page, not an error body`() {
        every { provisioning.handleOidcLogin(oidcUser, "reg-1") } throws IllegalStateException("OIDC user has no email")
        val response = MockHttpServletResponse()

        handlers.success().onAuthenticationSuccess(callback("reg-1"), response, auth)

        assertEquals("https://tasks.example.com/login?sso=failed", response.redirectedUrl)
    }

    @Test
    fun `failed OIDC exchange sends the user back to the login page`() {
        val response = MockHttpServletResponse()

        handlers.failure().onAuthenticationFailure(callback("reg-1"), response, OAuth2AuthenticationException("invalid_token"))

        assertEquals("https://tasks.example.com/login?sso=failed", response.redirectedUrl)
    }
}
