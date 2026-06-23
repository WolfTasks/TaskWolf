package com.taskowolf.audit.application

import com.taskowolf.audit.domain.AuditAction
import com.taskowolf.audit.domain.AuditLevel
import com.taskowolf.comments.domain.events.CommentCreatedEvent
import com.taskowolf.issues.domain.events.IssueCreatedEvent
import com.taskowolf.issues.domain.events.IssueFieldChangedEvent
import com.taskowolf.issues.domain.events.IssueStatusChangedEvent
import com.taskowolf.sprints.domain.events.SprintCompletedEvent
import com.taskowolf.sprints.domain.events.SprintStartedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class WriteAuditListener(private val auditService: AuditService) {

    @EventListener
    fun onIssueCreated(e: IssueCreatedEvent) =
        auditService.log(
            AuditLevel.WRITE, AuditAction.ISSUE_CREATED, e.actorEmail,
            userId = e.actorId, projectId = e.issue.project.id,
            resourceType = "ISSUE", resourceId = e.issue.id.toString()
        )

    @EventListener
    fun onIssueUpdated(e: IssueFieldChangedEvent) =
        auditService.log(
            AuditLevel.WRITE, AuditAction.ISSUE_UPDATED, e.actor.email,
            userId = e.actor.id, projectId = e.issue.project.id,
            resourceType = "ISSUE", resourceId = e.issue.id.toString()
        )

    @EventListener
    fun onIssueTransitioned(e: IssueStatusChangedEvent) =
        auditService.log(
            AuditLevel.WRITE, AuditAction.ISSUE_TRANSITIONED, e.actor?.email ?: "system",
            userId = e.actor?.id, projectId = e.issue.project.id,
            resourceType = "ISSUE", resourceId = e.issue.id.toString()
        )

    @EventListener
    fun onCommentCreated(e: CommentCreatedEvent) =
        auditService.log(
            AuditLevel.WRITE, AuditAction.COMMENT_CREATED, e.actorEmail,
            userId = e.actorId, projectId = e.issue.project.id,
            resourceType = "COMMENT", resourceId = e.comment.id.toString()
        )

    @EventListener
    fun onSprintStarted(e: SprintStartedEvent) =
        auditService.log(
            AuditLevel.WRITE, AuditAction.SPRINT_STARTED, e.actorEmail,
            userId = e.actorId, projectId = e.sprint.project.id,
            resourceType = "SPRINT", resourceId = e.sprint.id.toString()
        )

    @EventListener
    fun onSprintCompleted(e: SprintCompletedEvent) =
        auditService.log(
            AuditLevel.WRITE, AuditAction.SPRINT_COMPLETED, e.actorEmail,
            userId = e.actorId, projectId = e.sprint.project.id,
            resourceType = "SPRINT", resourceId = e.sprint.id.toString()
        )
}
