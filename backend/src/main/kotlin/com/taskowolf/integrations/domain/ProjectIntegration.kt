package com.taskowolf.integrations.domain

import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "project_integrations",
    uniqueConstraints = [UniqueConstraint(columnNames = ["project_id", "provider"])])
class ProjectIntegration(
    @Column(name = "project_id", nullable = false)
    val projectId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val provider: IntegrationProvider,

    @Column(nullable = false)
    var webhookSecretHash: String,

    @Column(length = 2048)
    var repoUrl: String? = null
) : AuditableEntity()
