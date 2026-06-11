package com.taskowolf.notifications

import com.taskowolf.auth.domain.User
import com.taskowolf.comments.domain.Comment
import com.taskowolf.comments.domain.events.MentionEvent
import com.taskowolf.core.infrastructure.NotFoundException
import com.taskowolf.issues.domain.Issue
import com.taskowolf.issues.domain.IssueType
import com.taskowolf.issues.domain.events.IssueFieldChangedEvent
import com.taskowolf.notifications.application.NotificationService
import com.taskowolf.notifications.domain.Notification
import com.taskowolf.notifications.domain.NotificationType
import com.taskowolf.notifications.infrastructure.NotificationRepository
import com.taskowolf.projects.domain.Project
import com.taskowolf.workflows.domain.StatusCategory
import com.taskowolf.workflows.domain.Workflow
import com.taskowolf.workflows.domain.WorkflowStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import java.util.Optional
import java.util.UUID

class NotificationServiceTest {

    private val repository = mockk<NotificationRepository>()
    private val service = NotificationService(repository)

    private val user = User(email = "user@test.com", displayName = "User")
    private val actor = User(email = "actor@test.com", displayName = "Actor")
    private val owner = User(email = "owner@test.com", displayName = "Owner")

    // Minimal issue for events — check Workflow constructor before using
    private val workflow = buildWorkflow(owner)
    private val status = WorkflowStatus("To Do", StatusCategory.TODO, "#6c8fef", 0, workflow)
    private val project = Project(key = "WOLF", name = "TaskWolf", owner = owner, workflow = workflow)
    private val issue = Issue(key = "WOLF-1", keyNumber = 1, title = "Test Issue",
        type = IssueType.TASK, status = status, project = project, reporter = owner, assignee = user)

    @Test
    fun `onMention saves COMMENT_MENTION notification for mentioned user`() {
        every { repository.save(any()) } returnsArgument 0
        val comment = Comment(issueId = issue.id, authorId = actor.id, body = "Hey @User check this")
        val event = MentionEvent(mentionedUser = user, comment = comment, issue = issue)

        service.onMention(event)

        val slot = slot<Notification>()
        verify { repository.save(capture(slot)) }
        assertEquals(user.id, slot.captured.userId)
        assertEquals(NotificationType.COMMENT_MENTION, slot.captured.type)
    }

    @Test
    fun `onIssueFieldChanged saves ISSUE_ASSIGNED notification when assignee changes`() {
        every { repository.save(any()) } returnsArgument 0
        val event = IssueFieldChangedEvent(issue = issue, actor = actor,
            field = "assignee", oldValue = null, newValue = user.displayName)

        service.onIssueFieldChanged(event)

        val slot = slot<Notification>()
        verify { repository.save(capture(slot)) }
        assertEquals(user.id, slot.captured.userId)
        assertEquals(NotificationType.ISSUE_ASSIGNED, slot.captured.type)
    }

    @Test
    fun `markRead marks notification as read`() {
        val notif = Notification(userId = user.id, type = NotificationType.COMMENT_MENTION, title = "Test")
        every { repository.findByIdAndUserId(notif.id, user.id) } returns notif
        every { repository.save(any()) } returnsArgument 0

        service.markRead(notif.id, user.id)

        assert(notif.read)
        verify { repository.save(notif) }
    }

    @Test
    fun `markRead throws NotFoundException for unknown notification`() {
        every { repository.findByIdAndUserId(any(), any()) } returns null
        assertThrows<NotFoundException> { service.markRead(UUID.randomUUID(), user.id) }
    }

    companion object {
        fun buildWorkflow(owner: User): Workflow {
            val project = Project(key = "WOLF", name = "TaskWolf", owner = owner, workflow = null)
            return Workflow(name = "Default", project = project)
        }
    }
}
