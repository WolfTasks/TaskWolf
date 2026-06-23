package com.taskowolf.auth.infrastructure

import com.taskowolf.auth.application.OidcUserProvisioningService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableMethodSecurity
class SecurityConfig(
    private val jwtAuthFilter: JwtAuthFilter,
    private val apiKeyAuthFilter: ApiKeyAuthFilter,
    private val dbClientRegistrationRepository: DbClientRegistrationRepository,
    private val oidcUserProvisioningService: OidcUserProvisioningService
) {
    @Bean
    fun passwordEncoder() = BCryptPasswordEncoder()

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED) }
            .authorizeHttpRequests {
                it.requestMatchers(
                    "/api/v1/auth/**",
                    "/api/v1/integrations/github/*/webhook",
                    "/api/v1/integrations/gitlab/*/webhook",
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                    "/h2-console/**",
                    "/ws/**",
                    "/ws-stomp/**",
                    "/login/oauth2/**",
                    "/oauth2/**"
                ).permitAll()
                it.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/admin/sso").permitAll()
                it.anyRequest().authenticated()
            }
            .headers { it.frameOptions { fo -> fo.disable() } }
            .oauth2Login { oauth2 ->
                oauth2
                    .clientRegistrationRepository(dbClientRegistrationRepository)
                    .successHandler(oidcSuccessHandler())
            }
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(apiKeyAuthFilter, JwtAuthFilter::class.java)
        return http.build()
    }

    private fun oidcSuccessHandler(): AuthenticationSuccessHandler =
        AuthenticationSuccessHandler { request: HttpServletRequest, response: HttpServletResponse, authentication ->
            val oidcUser = authentication.principal as OidcUser
            val registrationId = extractRegistrationId(request)
            val accessToken = oidcUserProvisioningService.handleOidcLogin(oidcUser, registrationId)
            response.sendRedirect("/auth/sso/callback?token=$accessToken")
        }

    private fun extractRegistrationId(request: HttpServletRequest): String {
        // OAuth2 stores the registration ID in the session under the key used by the authorization request
        val uri = request.requestURI
        // URI pattern: /login/oauth2/code/{registrationId}
        return uri.substringAfterLast("/")
    }
}
