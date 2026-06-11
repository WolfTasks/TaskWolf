package com.taskowolf.comments

import com.taskowolf.attachments.domain.Attachment
import com.taskowolf.attachments.domain.events.AttachmentAddedEvent
import com.taskowolf.auth.domain.User
import com.taskowolf.comments.application.ActivityService
import com.taskowolf.comments.domain.ActivityType
import com.taskowolf.comments.domain.Comment
import com.taskowolf.comments.domain.IssueActivity
import com.taskowolf.comments.domain.events.CommentCreatedEvent
import com.taskowolf.comments.infrastructure.IssueActivityRepository
import com.taskowolf.issues.domain.Issue
import com.taskowolf.issues.domain.IssueType
import com.taskowolf.issues.domain.events.IssueFieldChangedEvent
import com.taskowolf.issues.domain.events.IssueStatusChangedEvent
import com.taskowolf.projects.domain.Project
import com.taskowolf.workflows.domain.StatusCategory
import com.taskowolf.workflows.domain.Workflow
import com.taskowolf.workflows.domain.WorkflowStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class ActivityServiceTest {

    private val repository = mockk<IssueActivityRepository>()
    private val service = ActivityService(repository)

    @BeforeEach
    fun setUp() {
        every { repository.save(any()) } returnsArgument 0
    }

    private val owner = User(email = "owner@test.com", displayName = "Owner")
    private val project = Project(key = "WOLF", name = "TaskWolf", owner = owner, workflow = null)
    private val workflow = Workflow(name = "Default", project = project)
    private val status = WorkflowStatus("To Do", StatusCategory.TODO, "#6c8fef", 0, workflow)
    private val issue = Issue(key = "WOLF-1", keyNumber = 1, title = "Test", type = IssueType.TASK, status = status, project = project, reporter = owner)

    @Test
    fun `onCommentCreated saves COMMENT activity`() {
        val comment = Comment(issueId = issue.id, authorId = owner.id, body = "Hello")
        val event = CommentCreatedEvent(comment, issue)

        service.onCommentCreated(event)

        val slot = slot<IssueActivity>()
        verify { repository.save(capture(slot)) }
        assertEquals(ActivityType.COMMENT, slot.captured.type)
        assertEquals(issue.id, slot.captured.issueId)
        assertEquals(owner.id, slot.captured.actorId)
        assertEquals(comment.id, slot.captured.commentId)
    }

    @Test
    fun `onIssueFieldChanged saves TITLE_CHANGED activity`() {
        val event = IssueFieldChangedEvent(issue, owner, "title", "Old Title", "New Title")

        service.onIssueFieldChanged(event)

        val slot = slot<IssueActivity>()
        verify { repository.save(capture(slot)) }
        assertEquals(ActivityType.TITLE_CHANGED, slot.captured.type)
        assertEquals("Old Title", slot.captured.oldValue)
        assertEquals("New Title", slot.captured.newValue)
    }

    @Test
    fun `onIssueStatusChanged saves STATUS_CHANGED activity`() {
        val newStatus = WorkflowStatus("In Progress", StatusCategory.IN_PROGRESS, "#f0ad4e", 1, workflow)
        val event = IssueStatusChangedEvent(issue, status, newStatus, actor = owner)

        service.onIssueStatusChanged(event)

        val slot = slot<IssueActivity>()
        verify { repository.save(capture(slot)) }
        assertEquals(ActivityType.STATUS_CHANGED, slot.captured.type)
        assertEquals("To Do", slot.captured.oldValue)
        assertEquals("In Progress", slot.captured.newValue)
    }

    @Test
    fun `onAttachmentAdded saves ATTACHMENT_ADDED activity`() {
        val attachment = Attachment(issueId = issue.id, uploaderId = owner.id, filename = "spec.pdf",
            storedName = "abc123.pdf", contentType = "application/pdf", size = 12345)
        val event = AttachmentAddedEvent(attachment, issue)

        service.onAttachmentAdded(event)

        val slot = slot<IssueActivity>()
        verify { repository.save(capture(slot)) }
        assertEquals(ActivityType.ATTACHMENT_ADDED, slot.captured.type)
        assertEquals(issue.id, slot.captured.issueId)
        assertEquals(owner.id, slot.captured.actorId)
        assertEquals("spec.pdf", slot.captured.newValue)
    }
}
