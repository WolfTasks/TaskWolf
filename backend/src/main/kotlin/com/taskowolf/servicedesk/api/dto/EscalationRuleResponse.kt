package com.taskowolf.servicedesk.api.dto

import com.taskowolf.servicedesk.domain.EscalationRule
import java.util.UUID

data class EscalationRuleResponse(
    val id: UUID,
    val slaPolicyId: UUID,
    val escalateAfterMinutes: Int,
    val assigneeId: UUID?,
    val notifyUserIds: List<UUID>
) {
    companion object {
        fun from(r: EscalationRule) = EscalationRuleResponse(
            id = r.id,
            slaPolicyId = r.slaPolicyId,
            escalateAfterMinutes = r.escalateAfterMinutes,
            assigneeId = r.assigneeId,
            notifyUserIds = r.notifyUserIds.toList()
        )
    }
}
