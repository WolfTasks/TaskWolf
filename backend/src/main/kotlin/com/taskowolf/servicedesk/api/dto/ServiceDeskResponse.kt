package com.taskowolf.servicedesk.api.dto

import com.taskowolf.servicedesk.domain.ServiceDesk
import java.util.UUID

data class ServiceDeskResponse(
    val id: UUID,
    val projectId: UUID,
    val emailAddress: String?,
    val enabled: Boolean
) {
    companion object {
        fun from(sd: ServiceDesk) = ServiceDeskResponse(
            id = sd.id,
            projectId = sd.projectId,
            emailAddress = sd.emailAddress,
            enabled = sd.enabled
        )
    }
}
