package com.taskowolf.audit.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "audit_events")
class AuditEvent(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(nullable = false) val timestamp: Instant = Instant.now(),
    val userId: UUID? = null,
    @Column(nullable = false) val userEmail: String,
    val projectId: UUID? = null,
    @Enumerated(EnumType.STRING) @Column(nullable = false) val action: AuditAction,
    @Enumerated(EnumType.STRING) @Column(nullable = false) val level: AuditLevel,
    val resourceType: String? = null,
    val resourceId: String? = null,
    @Column(columnDefinition = "JSONB") val details: String? = null,
    val ipAddress: String? = null,
    val userAgent: String? = null
)
