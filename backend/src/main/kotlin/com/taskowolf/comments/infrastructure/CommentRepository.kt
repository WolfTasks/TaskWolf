package com.taskowolf.comments.infrastructure

import com.taskowolf.comments.domain.Comment
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CommentRepository : JpaRepository<Comment, UUID> {
    fun findAllByIssueId(issueId: UUID): List<Comment>
    fun findByIssueIdAndDeletedAtIsNullOrderByCreatedAtDesc(
        issueId: UUID,
        pageable: Pageable
    ): Page<Comment>
}
