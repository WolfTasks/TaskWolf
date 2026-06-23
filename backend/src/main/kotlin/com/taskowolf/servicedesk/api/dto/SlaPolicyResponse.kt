package com.taskowolf.servicedesk.api.dto

import com.taskowolf.issues.domain.IssuePriority
import com.taskowolf.servicedesk.domain.SlaPolicy
import java.util.UUID

data class SlaPolicyResponse(
    val id: UUID,
    val serviceDeskId: UUID,
    val name: String,
    val priority: IssuePriority,
    val responseMinutes: Int,
    val resolutionMinutes: Int
) {
    companion object {
        fun from(p: SlaPolicy) = SlaPolicyResponse(
            id = p.id,
            serviceDeskId = p.serviceDeskId,
            name = p.name,
            priority = p.priority,
            responseMinutes = p.responseMinutes,
            resolutionMinutes = p.resolutionMinutes
        )
    }
}
