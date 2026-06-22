package com.taskowolf.auth.domain

import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "api_keys")
class ApiKey(
    @Column(nullable = false)
    val name: String,

    @Column(nullable = false, unique = true)
    val keyHash: String,

    @Column(nullable = false)
    val keyPrefix: String,

    @Column(name = "project_id")
    val projectId: UUID? = null,

    @Column(nullable = false)
    val createdBy: UUID,

    @Column
    var lastUsedAt: Instant? = null,

    @Column
    val expiresAt: Instant? = null
) : AuditableEntity()
