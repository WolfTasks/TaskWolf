package com.taskowolf.auth.infrastructure

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableMethodSecurity
class SecurityConfig(
    private val jwtAuthFilter: JwtAuthFilter,
    private val apiKeyAuthFilter: ApiKeyAuthFilter,
    private val accessTokenAuthFilter: AccessTokenAuthFilter,
    private val dbClientRegistrationRepository: DbClientRegistrationRepository,
    private val oidcLoginHandlers: OidcLoginHandlers
) {
    @Bean
    fun passwordEncoder() = BCryptPasswordEncoder()

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED) }
            .exceptionHandling { it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)) }
            .authorizeHttpRequests {
                it.requestMatchers(
                    "/api/v1/auth/register",
                    "/api/v1/auth/login",
                    "/api/v1/auth/refresh",
                    "/api/v1/integrations/github/*/webhook",
                    "/api/v1/integrations/gitlab/*/webhook",
                    "/api/v1/projects/*/service-desk/tickets",
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                    "/h2-console/**",
                    "/ws/**",
                    "/ws-stomp/**",
                    "/login/oauth2/**",
                    "/oauth2/**"
                ).permitAll()
                it.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/admin/sso/public").permitAll()
                it.requestMatchers("/api/v1/auth/switch-org/**").authenticated()
                it.anyRequest().authenticated()
            }
            .headers { it.frameOptions { fo -> fo.disable() } }
            .oauth2Login { oauth2 ->
                oauth2
                    .clientRegistrationRepository(dbClientRegistrationRepository)
                    .successHandler(oidcLoginHandlers.success())
                    .failureHandler(oidcLoginHandlers.failure())
            }
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(apiKeyAuthFilter, JwtAuthFilter::class.java)
            .addFilterBefore(accessTokenAuthFilter, JwtAuthFilter::class.java)
        return http.build()
    }
}
