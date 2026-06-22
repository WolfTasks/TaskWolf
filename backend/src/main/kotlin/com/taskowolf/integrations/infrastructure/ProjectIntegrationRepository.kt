package com.taskowolf.integrations.infrastructure

import com.taskowolf.integrations.domain.IntegrationProvider
import com.taskowolf.integrations.domain.ProjectIntegration
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ProjectIntegrationRepository : JpaRepository<ProjectIntegration, UUID> {
    fun findByProjectId(projectId: UUID): List<ProjectIntegration>
    fun findByProjectIdAndProvider(projectId: UUID, provider: IntegrationProvider): ProjectIntegration?
    fun findByIdAndProjectId(id: UUID, projectId: UUID): ProjectIntegration?
}
