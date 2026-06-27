package com.taskowolf.issues.api.dto

import com.taskowolf.integrations.api.dto.IssueRefResponse
import com.taskowolf.issues.domain.Issue
import com.taskowolf.issues.domain.IssuePriority
import com.taskowolf.issues.domain.IssueType
import com.taskowolf.labels.api.dto.LabelResponse
import java.time.Instant
import java.time.LocalDate
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
    val assigneeName: String?,
    val reporterId: UUID,
    val reporterName: String,
    val parentId: UUID?,
    val dueDate: LocalDate?,
    val sprintId: UUID?,
    val sprintName: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val refs: List<IssueRefResponse> = emptyList(),
    val labels: List<LabelResponse> = emptyList()
) {
    companion object {
        fun from(
            i: Issue,
            refs: List<IssueRefResponse> = emptyList(),
            labels: List<LabelResponse> = emptyList()
        ) = IssueResponse(
            id = i.id,
            key = i.key,
            title = i.title,
            description = i.description,
            type = i.type,
            priority = i.priority,
            storyPoints = i.storyPoints,
            statusId = i.status.id,
            statusName = i.status.name,
            statusCategory = i.status.category.name,
            projectId = i.project.id,
            assigneeId = i.assignee?.id,
            assigneeName = i.assignee?.displayName,
            reporterId = i.reporter.id,
            reporterName = i.reporter.displayName,
            parentId = i.parent?.id,
            dueDate = i.dueDate,
            sprintId = i.sprint?.id,
            sprintName = i.sprint?.name,
            createdAt = i.createdAt ?: Instant.now(),
            updatedAt = i.updatedAt,
            refs = refs,
            labels = labels
        )
    }
}
