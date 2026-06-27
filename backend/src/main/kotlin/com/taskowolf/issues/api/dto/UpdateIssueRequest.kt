package com.taskowolf.issues.api.dto

import com.taskowolf.issues.domain.IssuePriority
import com.taskowolf.issues.domain.IssueType
import java.time.LocalDate
import java.util.UUID

data class UpdateIssueRequest(
    val title: String? = null,
    val description: String? = null,
    val statusId: UUID? = null,
    val assigneeId: UUID? = null,
    val clearAssignee: Boolean = false,
    val priority: IssuePriority? = null,
    val storyPoints: Int? = null,
    val type: IssueType? = null,
    val dueDate: LocalDate? = null,
    val clearDueDate: Boolean = false,
    val sprintId: UUID? = null,
    val clearSprint: Boolean = false,
    val labelIds: List<UUID>? = null
)
