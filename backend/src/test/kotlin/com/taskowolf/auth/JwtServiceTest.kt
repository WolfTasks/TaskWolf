package com.taskowolf.auth

import com.taskowolf.auth.application.JwtService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class JwtServiceTest {

    @Test
    fun `startup validation rejects blank secret with actionable message`() {
        val service = JwtService(secret = "", accessExpiry = 900, refreshExpiry = 604800)
        val ex = assertThrows<IllegalArgumentException> { service.validate() }
        assert(ex.message!!.contains("TW_JWT_SECRET"))
    }

    @Test
    fun `startup validation rejects short secret`() {
        val service = JwtService(secret = "too-short", accessExpiry = 900, refreshExpiry = 604800)
        assertThrows<IllegalArgumentException> { service.validate() }
    }
}
