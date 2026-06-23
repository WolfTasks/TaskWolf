package com.taskowolf.audit.api.dto

import com.taskowolf.audit.domain.AuditEvent
import java.time.Instant
import java.util.UUID

data class AuditEventResponse(
    val id: UUID,
    val timestamp: Instant,
    val userEmail: String,
    val userId: UUID?,
    val projectId: UUID?,
    val action: String,
    val level: String,
    val resourceType: String?,
    val resourceId: String?,
    val ipAddress: String?
) {
    companion object {
        fun from(e: AuditEvent) = AuditEventResponse(
            id = e.id,
            timestamp = e.timestamp,
            userEmail = e.userEmail,
            userId = e.userId,
            projectId = e.projectId,
            action = e.action.name,
            level = e.level.name,
            resourceType = e.resourceType,
            resourceId = e.resourceId,
            ipAddress = e.ipAddress
        )
    }
}
