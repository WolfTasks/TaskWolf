package com.taskowolf.automation.api.dto

import com.taskowolf.automation.domain.AutomationRule
import java.util.UUID

data class AutomationRuleResponse(
    val id: UUID,
    val name: String,
    val triggerType: String,
    val triggerPayload: String?,
    val scope: String,
    val enabled: Boolean,
    val projectId: UUID?
) {
    companion object {
        fun from(r: AutomationRule) = AutomationRuleResponse(
            r.id, r.name, r.triggerType.name, r.triggerPayload, r.scope.name, r.enabled, r.projectId
        )
    }
}
