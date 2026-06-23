package com.taskowolf.servicedesk.api.dto

import com.taskowolf.servicedesk.domain.Incident
import com.taskowolf.servicedesk.domain.IncidentSeverity
import java.time.Instant
import java.util.UUID

data class IncidentResponse(
    val id: UUID,
    val issueId: UUID,
    val severity: IncidentSeverity,
    val onCallAssigneeId: UUID?,
    val postmortemBody: String?,
    val resolvedAt: Instant?
) {
    companion object {
        fun from(i: Incident) = IncidentResponse(
            id = i.id,
            issueId = i.issueId,
            severity = i.severity,
            onCallAssigneeId = i.onCallAssigneeId,
            postmortemBody = i.postmortemBody,
            resolvedAt = i.resolvedAt
        )
    }
}
