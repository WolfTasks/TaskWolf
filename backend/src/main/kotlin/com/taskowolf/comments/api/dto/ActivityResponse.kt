package com.taskowolf.comments.api.dto

import com.taskowolf.comments.domain.ActivityType
import com.taskowolf.comments.domain.IssueActivity
import java.time.Instant
import java.util.UUID

data class ActivityResponse(
    val id: UUID,
    val issueId: UUID,
    val actorId: UUID,
    val type: ActivityType,
    val commentId: UUID?,
    val oldValue: String?,
    val newValue: String?,
    val createdAt: Instant
) {
    companion object {
        fun from(a: IssueActivity) = ActivityResponse(
            id = a.id,
            issueId = a.issueId,
            actorId = a.actorId,
            type = a.type,
            commentId = a.commentId,
            oldValue = a.oldValue,
            newValue = a.newValue,
            createdAt = a.createdAt!!
        )
    }
}
