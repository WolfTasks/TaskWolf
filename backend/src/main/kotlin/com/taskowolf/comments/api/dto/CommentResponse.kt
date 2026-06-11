package com.taskowolf.comments.api.dto

import com.taskowolf.comments.domain.Comment
import java.time.Instant
import java.util.UUID

data class CommentResponse(
    val id: UUID,
    val issueId: UUID,
    val authorId: UUID,
    val body: String?,
    val editedAt: Instant?,
    val deleted: Boolean,
    val createdAt: Instant
) {
    companion object {
        fun from(c: Comment) = CommentResponse(
            id = c.id,
            issueId = c.issueId,
            authorId = c.authorId,
            body = if (c.deletedAt != null) null else c.body,
            editedAt = c.editedAt,
            deleted = c.deletedAt != null,
            createdAt = c.createdAt
        )
    }
}
