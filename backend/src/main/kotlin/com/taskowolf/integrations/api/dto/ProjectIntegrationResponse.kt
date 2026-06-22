package com.taskowolf.integrations.api.dto

import com.taskowolf.integrations.domain.ProjectIntegration
import java.time.Instant
import java.util.UUID

data class ProjectIntegrationResponse(
    val id: UUID,
    val provider: String,
    val repoUrl: String?,
    val createdAt: Instant?
) {
    companion object {
        fun from(p: ProjectIntegration) =
            ProjectIntegrationResponse(p.id, p.provider.name, p.repoUrl, p.createdAt)
    }
}
