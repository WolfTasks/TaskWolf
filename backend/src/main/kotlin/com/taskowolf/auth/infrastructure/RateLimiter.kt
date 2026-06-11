package com.taskowolf.auth.infrastructure

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class RateLimiter(
    private val maxRequests: Long = 10L,
    private val windowSeconds: Long = 60L
) {
    // [count, windowStart (epoch seconds)]
    private val cache = ConcurrentHashMap<String, LongArray>()

    fun isAllowed(key: String): Boolean {
        val now = System.currentTimeMillis() / 1000
        var allowed = true
        cache.compute(key) { _, existing ->
            when {
                existing == null || now - existing[1] >= windowSeconds -> {
                    longArrayOf(1L, now)
                }
                else -> {
                    existing[0]++
                    if (existing[0] > maxRequests) allowed = false
                    existing
                }
            }
        }
        return allowed
    }

    fun reset(key: String) = cache.remove(key)
}
