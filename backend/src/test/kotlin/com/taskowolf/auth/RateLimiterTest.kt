package com.taskowolf.auth

import com.taskowolf.auth.infrastructure.RateLimiter
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach

class RateLimiterTest {

    private lateinit var limiter: RateLimiter

    @BeforeEach
    fun setup() {
        limiter = RateLimiter(maxRequests = 3L, windowSeconds = 60L)
    }

    @Test
    fun `allows requests within limit`() {
        repeat(3) { assertTrue(limiter.isAllowed("192.168.1.1")) }
    }

    @Test
    fun `rejects requests exceeding limit`() {
        repeat(3) { limiter.isAllowed("192.168.1.2") }
        assertFalse(limiter.isAllowed("192.168.1.2"))
    }

    @Test
    fun `different IPs have independent limits`() {
        repeat(3) { limiter.isAllowed("10.0.0.1") }
        // 10.0.0.1 is at limit, but 10.0.0.2 is not
        assertFalse(limiter.isAllowed("10.0.0.1"))
        assertTrue(limiter.isAllowed("10.0.0.2"))
    }

    @Test
    fun `reset clears limit for IP`() {
        repeat(3) { limiter.isAllowed("10.0.0.3") }
        assertFalse(limiter.isAllowed("10.0.0.3"))
        limiter.reset("10.0.0.3")
        assertTrue(limiter.isAllowed("10.0.0.3"))
    }
}
