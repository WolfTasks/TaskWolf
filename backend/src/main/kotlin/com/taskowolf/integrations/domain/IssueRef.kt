package com.taskowolf.integrations.domain

import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "issue_refs",
    uniqueConstraints = [UniqueConstraint(columnNames = ["issue_id", "provider", "ref_type", "external_id"])])
class IssueRef(
    @Column(name = "issue_id", nullable = false)
    val issueId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val provider: IntegrationProvider,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val refType: RefType,

    @Column(nullable = false)
    val externalId: String,

    @Column(nullable = false, length = 2048)
    val url: String,

    @Column(length = 1024)
    val title: String? = null
) : AuditableEntity()
