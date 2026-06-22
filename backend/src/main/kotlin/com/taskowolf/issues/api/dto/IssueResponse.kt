package com.taskowolf.issues.api.dto

import com.taskowolf.integrations.api.dto.IssueRefResponse
import com.taskowolf.issues.domain.Issue
import com.taskowolf.issues.domain.IssuePriority
import com.taskowolf.issues.domain.IssueType
import java.util.UUID

data class IssueResponse(
    val id: UUID,
    val key: String,
    val title: String,
    val description: String?,
    val type: IssueType,
    val priority: IssuePriority,
    val storyPoints: Int?,
    val statusId: UUID,
    val statusName: String,
    val statusCategory: String,
    val projectId: UUID,
    val assigneeId: UUID?,
    val reporterId: UUID,
    val parentId: UUID?,
    val refs: List<IssueRefResponse> = emptyList()
) {
    companion object {
        fun from(i: Issue, refs: List<IssueRefResponse> = emptyList()) = IssueResponse(
            i.id, i.key, i.title, i.description, i.type, i.priority, i.storyPoints,
            i.status.id, i.status.name, i.status.category.name,
            i.project.id, i.assignee?.id, i.reporter.id, i.parent?.id,
            refs
        )
    }
}
