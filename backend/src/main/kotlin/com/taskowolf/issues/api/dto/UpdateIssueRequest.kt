package com.taskowolf.issues.api.dto

import com.taskowolf.issues.domain.IssuePriority
import java.util.UUID

data class UpdateIssueRequest(
    val title: String? = null,
    val description: String? = null,
    val statusId: UUID? = null,
    val assigneeId: UUID? = null,
    val priority: IssuePriority? = null,
    val storyPoints: Int? = null
)
