package com.taskowolf.integrations.infrastructure

import com.taskowolf.integrations.domain.Webhook
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface WebhookRepository : JpaRepository<Webhook, UUID> {
    fun findByProjectId(projectId: UUID): List<Webhook>
    fun findByProjectIdAndEnabled(projectId: UUID, enabled: Boolean): List<Webhook>
    fun findByIdAndProjectId(id: UUID, projectId: UUID): Webhook?
}
