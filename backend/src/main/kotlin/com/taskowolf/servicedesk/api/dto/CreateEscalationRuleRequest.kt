package com.taskowolf.servicedesk.api.dto

import java.util.UUID

data class CreateEscalationRuleRequest(
    val escalateAfterMinutes: Int,
    val assigneeId: UUID?,
    val notifyUserIds: List<UUID>
)
