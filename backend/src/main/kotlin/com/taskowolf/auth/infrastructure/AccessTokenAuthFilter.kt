package com.taskowolf.auth.infrastructure

import com.taskowolf.auth.application.AccessTokenService
import com.taskowolf.auth.domain.TokenScope
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class AccessTokenAuthFilter(
    private val accessTokenService: AccessTokenService
) : OncePerRequestFilter() {

    private val safeMethods = setOf("GET", "HEAD", "OPTIONS")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        val raw = request.getHeader("Authorization")
            ?.takeIf { it.startsWith("Bearer twk_") }
            ?.substring(7)

        if (raw != null && SecurityContextHolder.getContext().authentication == null) {
            val authenticated = accessTokenService.authenticate(raw)
            if (authenticated != null) {
                if (authenticated.scope == TokenScope.READ_ONLY && request.method !in safeMethods) {
                    response.sendError(
                        HttpServletResponse.SC_FORBIDDEN,
                        "Read-only token cannot perform ${request.method}"
                    )
                    return
                }
                SecurityContextHolder.getContext().authentication =
                    UsernamePasswordAuthenticationToken(
                        authenticated.user, null,
                        listOf(SimpleGrantedAuthority("ROLE_${authenticated.user.systemRole.name}"))
                    )
            }
        }
        chain.doFilter(request, response)
    }
}
