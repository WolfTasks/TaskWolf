package com.taskowolf.integrations.infrastructure

import com.taskowolf.integrations.domain.IssueRef
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface IssueRefRepository : JpaRepository<IssueRef, UUID> {
    fun findByIssueIdOrderByCreatedAtAsc(issueId: UUID): List<IssueRef>
}
