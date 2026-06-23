package com.taskowolf.servicedesk.api.dto

import java.util.UUID

data class CreateIncidentRequest(
    val issueId: UUID,
    val severity: String,
    val onCallAssigneeId: UUID?,
    val notifyUserIds: List<UUID> = emptyList()
)
