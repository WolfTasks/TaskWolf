package com.taskowolf.integrations.domain

object WebhookEventType {
    const val ISSUE_CREATED        = "issue.created"
    const val ISSUE_UPDATED        = "issue.updated"
    const val ISSUE_STATUS_CHANGED = "issue.status_changed"
    const val ISSUE_ASSIGNED       = "issue.assigned"
    const val ISSUE_DELETED        = "issue.deleted"
    const val SPRINT_STARTED       = "sprint.started"
    const val SPRINT_COMPLETED     = "sprint.completed"
    const val COMMENT_CREATED      = "comment.created"
    const val ATTACHMENT_ADDED     = "attachment.added"

    val ALL = listOf(
        ISSUE_CREATED, ISSUE_UPDATED, ISSUE_STATUS_CHANGED, ISSUE_ASSIGNED,
        ISSUE_DELETED, SPRINT_STARTED, SPRINT_COMPLETED, COMMENT_CREATED, ATTACHMENT_ADDED
    )
}
