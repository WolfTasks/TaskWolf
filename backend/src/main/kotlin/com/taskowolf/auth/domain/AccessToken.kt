package com.taskowolf.auth.domain

import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "access_tokens")
class AccessToken(
    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(nullable = false)
    val name: String,

    @Column(nullable = false, unique = true)
    val tokenHash: String,

    @Column(nullable = false)
    val tokenPrefix: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val scope: TokenScope,

    @Column
    var lastUsedAt: Instant? = null,

    @Column
    val expiresAt: Instant? = null,

    @Column
    var revokedAt: Instant? = null
) : AuditableEntity()
