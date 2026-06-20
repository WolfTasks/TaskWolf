package com.taskowolf.comments.infrastructure

import com.taskowolf.comments.domain.ActivityType
import com.taskowolf.comments.domain.IssueActivity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface IssueActivityRepository : JpaRepository<IssueActivity, UUID> {
    fun findAllByIssueId(issueId: UUID, pageable: Pageable): Page<IssueActivity>
    fun findByIssueIdAndTypeOrderByCreatedAtAsc(issueId: UUID, type: ActivityType): List<IssueActivity>
}
