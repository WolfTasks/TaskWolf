package com.taskowolf.auth.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@Order(1)
class AuthRateLimitFilter(
    private val rateLimiter: RateLimiter,
    private val objectMapper: ObjectMapper
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        if (!isAuthEndpoint(request.requestURI)) {
            chain.doFilter(request, response)
            return
        }

        val clientIp = request.getHeader("X-Forwarded-For")
            ?.split(",")?.firstOrNull()?.trim()
            ?: request.remoteAddr

        if (!rateLimiter.isAllowed(clientIp)) {
            response.status = HttpStatus.TOO_MANY_REQUESTS.value()
            response.contentType = "application/json"
            response.writer.write(
                objectMapper.writeValueAsString(
                    mapOf("code" to "RATE_LIMITED", "message" to "Too many requests. Please try again later.")
                )
            )
            return
        }

        chain.doFilter(request, response)
    }

    private fun isAuthEndpoint(uri: String) =
        uri.startsWith("/api/v1/auth/login") || uri.startsWith("/api/v1/auth/register")
}
