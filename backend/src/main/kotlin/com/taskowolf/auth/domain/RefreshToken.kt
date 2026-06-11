package com.taskowolf.auth.domain

import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "refresh_tokens")
class RefreshToken(
    @Column(nullable = false, unique = true)
    val tokenHash: String,

    @Column(nullable = false)
    val userId: UUID,

    @Column(nullable = false)
    val expiresAt: Instant,

    @Column(nullable = false)
    var revoked: Boolean = false
) : AuditableEntity()
