package com.taskowolf.auth

import com.taskowolf.auth.application.JwtService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

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

    // Issued with jjwt-jackson 0.13.0 (M3 Phase 2, 2026-10-19), expires 2100-01-01.
    // Must stay valid after the switch to jjwt-gson, otherwise every user is logged out on deploy.
    private val fixtureSecret = "jwt-fixture-secret-0123456789abcdef"
    private val fixtureToken =
        "eyJhbGciOiJIUzI1NiJ9.eyJqdGkiOiIwMDAwMDAwMC0wMDAwLTAwMDAtMDAwMC0wMDAwMDAwMDAwYWEiLCJzdWIiOiIxMTExMTExMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTEiLCJ0eXBlIjoiYWNjZXNzIiwib3JnSWQiOiIyMjIyMjIyMi0yMjIyLTIyMjItMjIyMi0yMjIyMjIyMjIyMjIiLCJpYXQiOjE3NjAwMDAwMDAsImV4cCI6NDEwMjQ0NDgwMH0.0EK0B6blrj-HABY2Neh6jz25gaARsfg19kpMo1D2sus"

    @Test
    fun `token issued with the previous JSON serializer stays valid`() {
        val service = JwtService(secret = fixtureSecret, accessExpiry = 900, refreshExpiry = 604800)
        assertEquals(UUID.fromString("11111111-1111-1111-1111-111111111111"), service.validateToken(fixtureToken))
        assertEquals(UUID.fromString("22222222-2222-2222-2222-222222222222"), service.extractOrgId(fixtureToken))
    }

    @Test
    fun `freshly issued access token round-trips user and org`() {
        val service = JwtService(secret = fixtureSecret, accessExpiry = 900, refreshExpiry = 604800)
        val userId = UUID.randomUUID()
        val orgId = UUID.randomUUID()
        val token = service.generateAccessToken(userId, orgId)
        assertEquals(userId, service.validateToken(token))
        assertEquals(orgId, service.extractOrgId(token))
    }
}
