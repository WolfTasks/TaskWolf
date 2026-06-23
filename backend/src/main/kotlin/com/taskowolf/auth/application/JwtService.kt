package com.taskowolf.auth.application

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Date
import java.util.UUID

@Service
class JwtService(
    @Value("\${taskowolf.jwt.secret}") private val secret: String,
    @Value("\${taskowolf.jwt.access-token-expiry}") private val accessExpiry: Long,
    @Value("\${taskowolf.jwt.refresh-token-expiry}") private val refreshExpiry: Long
) {
    private val key by lazy { Keys.hmacShaKeyFor(secret.toByteArray()) }

    @PostConstruct
    fun validate() {
        require(secret.isNotBlank() && secret.toByteArray().size >= 32) {
            "TW_JWT_SECRET must be set to a random value of at least 32 bytes " +
                "(e.g. generate one with: openssl rand -base64 48)"
        }
    }

    fun generateAccessToken(userId: UUID, orgId: UUID? = null): String = Jwts.builder()
        .id(UUID.randomUUID().toString())
        .subject(userId.toString())
        .claim("type", "access")
        .apply { orgId?.let { claim("orgId", it.toString()) } }
        .issuedAt(Date())
        .expiration(Date(System.currentTimeMillis() + accessExpiry * 1000))
        .signWith(key)
        .compact()

    fun generateRefreshToken(userId: UUID): String = buildToken(userId, refreshExpiry * 1000, "refresh")

    fun validateToken(token: String, expectedType: String = "access"): UUID? = runCatching {
        val claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload
        require(claims["type"] == expectedType)
        UUID.fromString(claims.subject)
    }.getOrNull()

    fun extractOrgId(token: String): UUID? = runCatching {
        val claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload
        claims.get("orgId", String::class.java)?.let { UUID.fromString(it) }
    }.getOrNull()

    private fun buildToken(userId: UUID, expiryMs: Long, type: String) = Jwts.builder()
        .id(UUID.randomUUID().toString())
        .subject(userId.toString())
        .claim("type", type)
        .issuedAt(Date())
        .expiration(Date(System.currentTimeMillis() + expiryMs))
        .signWith(key)
        .compact()
}
