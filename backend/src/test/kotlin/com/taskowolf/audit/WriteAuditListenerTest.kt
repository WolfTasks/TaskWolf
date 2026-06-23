package com.taskowolf.audit

import com.taskowolf.audit.application.AuditService
import com.taskowolf.audit.application.WriteAuditListener
import com.taskowolf.audit.domain.AuditAction
import com.taskowolf.audit.domain.AuditLevel
import com.taskowolf.auth.domain.User
import com.taskowolf.comments.domain.Comment
import com.taskowolf.comments.domain.events.CommentCreatedEvent
import com.taskowolf.issues.domain.events.IssueCreatedEvent
import com.taskowolf.issues.domain.events.IssueFieldChangedEvent
import com.taskowolf.issues.domain.events.IssueStatusChangedEvent
import com.taskowolf.sprints.domain.events.SprintCompletedEvent
import com.taskowolf.sprints.domain.events.SprintStartedEvent
import com.taskowolf.workflows.domain.StatusCategory
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.UUID

class WriteAuditListenerTest {
    private val auditService = mockk<AuditService>(relaxed = true)
    private val listener = WriteAuditListener(auditService)

    private val actorEmail = "dev@example.com"
    private val actorId = UUID.randomUUID()
    private val actor = mockk<User>(relaxed = true).also {
        io.mockk.every { it.email } returns actorEmail
        io.mockk.every { it.id } returns actorId
    }

    private val issue = mockk<com.taskowolf.issues.domain.Issue>(relaxed = true)
    private val sprint = mockk<com.taskowolf.sprints.domain.Sprint>(relaxed = true)
    private val comment = mockk<Comment>(relaxed = true)
    private val issueForComment = mockk<com.taskowolf.issues.domain.Issue>(relaxed = true)

    @Test
    fun `IssueCreatedEvent logs ISSUE_CREATED`() {
        val event = IssueCreatedEvent(
            issue = issue,
            actorEmail = actorEmail,
            actorId = actorId
        )
        listener.onIssueCreated(event)
        verify {
            auditService.log(
                AuditLevel.WRITE, AuditAction.ISSUE_CREATED, actorEmail,
                userId = actorId, projectId = any(), resourceType = "ISSUE", resourceId = any()
            )
        }
    }

    @Test
    fun `IssueFieldChangedEvent logs ISSUE_UPDATED`() {
        val event = IssueFieldChangedEvent(
            issue = issue,
            actor = actor,
            field = "title",
            oldValue = "Old",
            newValue = "New"
        )
        listener.onIssueUpdated(event)
        verify {
            auditService.log(
                AuditLevel.WRITE, AuditAction.ISSUE_UPDATED, actorEmail,
                userId = actorId, projectId = any(), resourceType = "ISSUE", resourceId = any()
            )
        }
    }

    @Test
    fun `IssueStatusChangedEvent logs ISSUE_TRANSITIONED with actor`() {
        val oldStatus = mockk<com.taskowolf.workflows.domain.WorkflowStatus>(relaxed = true)
        val newStatus = mockk<com.taskowolf.workflows.domain.WorkflowStatus>(relaxed = true)
        val event = IssueStatusChangedEvent(
            issue = issue,
            oldStatus = oldStatus,
            newStatus = newStatus,
            actor = actor
        )
        listener.onIssueTransitioned(event)
        verify {
            auditService.log(
                AuditLevel.WRITE, AuditAction.ISSUE_TRANSITIONED, actorEmail,
                userId = actorId, projectId = any(), resourceType = "ISSUE", resourceId = any()
            )
        }
    }

    @Test
    fun `IssueStatusChangedEvent logs ISSUE_TRANSITIONED without actor uses system`() {
        val oldStatus = mockk<com.taskowolf.workflows.domain.WorkflowStatus>(relaxed = true)
        val newStatus = mockk<com.taskowolf.workflows.domain.WorkflowStatus>(relaxed = true)
        val event = IssueStatusChangedEvent(
            issue = issue,
            oldStatus = oldStatus,
            newStatus = newStatus,
            actor = null
        )
        listener.onIssueTransitioned(event)
        verify {
            auditService.log(
                AuditLevel.WRITE, AuditAction.ISSUE_TRANSITIONED, "system",
                userId = null, projectId = any(), resourceType = "ISSUE", resourceId = any()
            )
        }
    }

    @Test
    fun `CommentCreatedEvent logs COMMENT_CREATED`() {
        val event = CommentCreatedEvent(
            comment = comment,
            issue = issueForComment,
            actorEmail = actorEmail,
            actorId = actorId
        )
        listener.onCommentCreated(event)
        verify {
            auditService.log(
                AuditLevel.WRITE, AuditAction.COMMENT_CREATED, actorEmail,
                userId = actorId, projectId = any(), resourceType = "COMMENT", resourceId = any()
            )
        }
    }

    @Test
    fun `SprintStartedEvent logs SPRINT_STARTED`() {
        val event = SprintStartedEvent(
            sprint = sprint,
            actorEmail = actorEmail,
            actorId = actorId
        )
        listener.onSprintStarted(event)
        verify {
            auditService.log(
                AuditLevel.WRITE, AuditAction.SPRINT_STARTED, actorEmail,
                userId = actorId, projectId = any(), resourceType = "SPRINT", resourceId = any()
            )
        }
    }

    @Test
    fun `SprintCompletedEvent logs SPRINT_COMPLETED`() {
        val event = SprintCompletedEvent(
            sprint = sprint,
            movedToBacklogCount = 3,
            actorEmail = actorEmail,
            actorId = actorId
        )
        listener.onSprintCompleted(event)
        verify {
            auditService.log(
                AuditLevel.WRITE, AuditAction.SPRINT_COMPLETED, actorEmail,
                userId = actorId, projectId = any(), resourceType = "SPRINT", resourceId = any()
            )
        }
    }
}
