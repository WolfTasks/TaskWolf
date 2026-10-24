package com.taskowolf.auth.infrastructure

import com.taskowolf.core.infrastructure.LocalizedMessages
import tools.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.net.InetAddress

@Component
@Order(1)
class AuthRateLimitFilter(
    private val rateLimiter: RateLimiter,
    private val objectMapper: ObjectMapper,
    private val messages: LocalizedMessages
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

        if (!rateLimiter.isAllowed(clientIp(request))) {
            response.status = HttpStatus.TOO_MANY_REQUESTS.value()
            response.contentType = "application/json"
            response.characterEncoding = "UTF-8"
            response.writer.write(
                objectMapper.writeValueAsString(
                    mapOf("code" to "RATE_LIMITED", "message" to messages.get("auth.rateLimited"))
                )
            )
            return
        }

        chain.doFilter(request, response)
    }

    /**
     * The key must be an address the client cannot pick (H5). X-Forwarded-For is client-controlled and ignored.
     * X-Real-IP is only trusted when the direct peer is on a private/loopback network, i.e. our nginx in the
     * Docker network, which overwrites it with $remote_addr. Anyone else is keyed by their own address.
     */
    private fun clientIp(request: HttpServletRequest): String {
        val peer = request.remoteAddr
        val forwarded = request.getHeader("X-Real-IP")?.trim()
        return if (!forwarded.isNullOrEmpty() && isInternal(peer)) forwarded else peer
    }

    private fun isInternal(ip: String): Boolean {
        // remoteAddr is always an IP literal; the guard keeps getByName from ever doing a DNS lookup
        if (!ip.matches(Regex("[0-9a-fA-F:.%]+"))) return false
        val addr = runCatching { InetAddress.getByName(ip) }.getOrNull() ?: return false
        val uniqueLocalV6 = addr.address.size == 16 && (addr.address[0].toInt() and 0xfe) == 0xfc
        return addr.isLoopbackAddress || addr.isSiteLocalAddress || uniqueLocalV6
    }

    private fun isAuthEndpoint(uri: String) =
        uri.startsWith("/api/v1/auth/login") || uri.startsWith("/api/v1/auth/register")
}
