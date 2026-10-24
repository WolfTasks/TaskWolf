package com.taskowolf.auth

import com.taskowolf.auth.infrastructure.AuthRateLimitFilter
import com.taskowolf.auth.infrastructure.RateLimiter
import com.taskowolf.core.infrastructure.LocalizedMessages
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.context.support.ResourceBundleMessageSource
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import tools.jackson.databind.json.JsonMapper
import java.util.Locale

/** H5: the login rate limit must key on an IP the client cannot choose. */
class AuthRateLimitFilterTest {

    private val messages = LocalizedMessages(ResourceBundleMessageSource().apply {
        setBasename("messages"); setDefaultEncoding("UTF-8"); setFallbackToSystemLocale(false)
    })
    private val filter = AuthRateLimitFilter(RateLimiter(maxRequests = 2, windowSeconds = 60), JsonMapper.builder().build(), messages)

    @AfterEach fun reset() = LocaleContextHolder.resetLocaleContext()

    private fun login(remoteAddr: String, headers: Map<String, String> = emptyMap()): MockHttpServletResponse {
        val request = MockHttpServletRequest("POST", "/api/v1/auth/login").apply {
            this.remoteAddr = remoteAddr
            headers.forEach { (k, v) -> addHeader(k, v) }
        }
        return MockHttpServletResponse().also { filter.doFilter(request, it, MockFilterChain()) }
    }

    @Test
    fun `a spoofed X-Forwarded-For per request does not get a fresh bucket`() {
        val statuses = (1..3).map { login("203.0.113.7", mapOf("X-Forwarded-For" to "198.51.100.$it")).status }
        assertEquals(listOf(200, 200, 429), statuses)
    }

    @Test
    fun `X-Real-IP set by our proxy on the private network separates clients`() {
        repeat(2) { login("172.18.0.5", mapOf("X-Real-IP" to "203.0.113.1")) }
        assertEquals(429, login("172.18.0.5", mapOf("X-Real-IP" to "203.0.113.1")).status)
        // a different client behind the same nginx is not locked out
        assertEquals(200, login("172.18.0.5", mapOf("X-Real-IP" to "203.0.113.2")).status)
    }

    @Test
    fun `X-Real-IP from a public peer is ignored`() {
        val statuses = (1..3).map { login("203.0.113.9", mapOf("X-Real-IP" to "198.51.100.$it")).status }
        assertEquals(listOf(200, 200, 429), statuses)
    }

    @Test
    fun `429 message is localized`() {
        LocaleContextHolder.setLocale(Locale.GERMAN)
        repeat(2) { login("203.0.113.20") }
        val body = login("203.0.113.20").contentAsString
        assertTrue(body.contains("\"code\":\"RATE_LIMITED\""), body)
        assertTrue(body.contains("Zu viele Anfragen"), body)
    }
}
