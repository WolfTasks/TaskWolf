package com.taskowolf.issues.api.dto

import com.taskowolf.issues.domain.IssuePriority
import com.taskowolf.issues.domain.IssueType
import jakarta.validation.constraints.NotBlank
import java.util.UUID

data class CreateIssueRequest(
    @field:NotBlank val title: String,
    val description: String? = null,
    val type: IssueType = IssueType.TASK,
    val priority: IssuePriority = IssuePriority.MEDIUM,
    val assigneeId: UUID? = null,
    val parentId: UUID? = null,
    val storyPoints: Int? = null
)
