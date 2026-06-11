package com.taskowolf.attachments

import com.taskowolf.attachments.application.AttachmentService
import com.taskowolf.attachments.application.StorageService
import com.taskowolf.attachments.domain.Attachment
import com.taskowolf.attachments.domain.events.AttachmentAddedEvent
import com.taskowolf.attachments.domain.events.AttachmentRemovedEvent
import com.taskowolf.attachments.infrastructure.AttachmentRepository
import com.taskowolf.auth.domain.User
import com.taskowolf.core.application.DomainEventPublisher
import com.taskowolf.core.infrastructure.ForbiddenException
import com.taskowolf.issues.application.IssueService
import com.taskowolf.issues.domain.Issue
import com.taskowolf.issues.domain.IssueType
import com.taskowolf.projects.application.ProjectService
import com.taskowolf.projects.domain.Project
import com.taskowolf.workflows.domain.StatusCategory
import com.taskowolf.workflows.domain.Workflow
import com.taskowolf.workflows.domain.WorkflowStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.mock.web.MockMultipartFile
import java.util.Optional
import java.util.UUID

class AttachmentServiceTest {

    private val attachmentRepository = mockk<AttachmentRepository>(relaxed = true)
    private val storageService = mockk<StorageService>(relaxed = true)
    private val issueService = mockk<IssueService>()
    private val projectService = mockk<ProjectService>()
    private val eventPublisher = mockk<DomainEventPublisher>(relaxed = true)
    private val service = AttachmentService(
        attachmentRepository, storageService, issueService, projectService, eventPublisher
    )

    private val uploader = User(email = "uploader@test.com", displayName = "Uploader")
    private val other = User(email = "other@test.com", displayName = "Other")
    private val owner = User(email = "owner@test.com", displayName = "Owner")

    private val project = Project(key = "WOLF", name = "TaskWolf", owner = owner, workflow = null)
    // Use Workflow(name, project) — adjust if constructor is different
    private val workflow = Workflow(name = "Default", project = project)
    private val status = WorkflowStatus("To Do", StatusCategory.TODO, "#6c8fef", 0, workflow)
    private val issue = Issue(
        key = "WOLF-1", keyNumber = 1, title = "Test", type = IssueType.TASK,
        status = status, project = project, reporter = owner
    )
    private val file = MockMultipartFile("file", "report.pdf", "application/pdf", "data".toByteArray())

    @Test
    fun `upload stores file and saves attachment`() {
        every { issueService.findByKey("WOLF", "WOLF-1", uploader.id) } returns issue
        every { storageService.store(file) } returns "uuid.pdf"
        every { attachmentRepository.save(any()) } returnsArgument 0

        val attachment = service.upload("WOLF", "WOLF-1", file, uploader)

        assertEquals(issue.id, attachment.issueId)
        assertEquals(uploader.id, attachment.uploaderId)
        assertEquals("report.pdf", attachment.filename)
        assertEquals("uuid.pdf", attachment.storedName)
    }

    @Test
    fun `upload publishes AttachmentAddedEvent`() {
        every { issueService.findByKey("WOLF", "WOLF-1", uploader.id) } returns issue
        every { storageService.store(file) } returns "uuid.pdf"
        every { attachmentRepository.save(any()) } returnsArgument 0

        service.upload("WOLF", "WOLF-1", file, uploader)

        val slot = slot<AttachmentAddedEvent>()
        verify { eventPublisher.publish(capture(slot)) }
        assertEquals(issue, slot.captured.issue)
    }

    @Test
    fun `delete removes file and attachment by uploader`() {
        val attachment = Attachment(
            issueId = issue.id, uploaderId = uploader.id, filename = "doc.pdf",
            storedName = "uuid.pdf", contentType = "application/pdf", size = 100L
        )
        every { attachmentRepository.findById(attachment.id) } returns Optional.of(attachment)
        every { issueService.findByKey("WOLF", "WOLF-1", uploader.id) } returns issue

        service.delete("WOLF", "WOLF-1", attachment.id, uploader)

        verify { storageService.delete("uuid.pdf") }
        verify { attachmentRepository.delete(attachment) }
    }

    @Test
    fun `delete allows project admin to remove any attachment`() {
        val attachment = Attachment(
            issueId = issue.id, uploaderId = uploader.id, filename = "doc.pdf",
            storedName = "uuid.pdf", contentType = "application/pdf", size = 100L
        )
        every { attachmentRepository.findById(attachment.id) } returns Optional.of(attachment)
        every { issueService.findByKey("WOLF", "WOLF-1", owner.id) } returns issue
        every { projectService.isProjectAdmin("WOLF", owner.id) } returns true

        service.delete("WOLF", "WOLF-1", attachment.id, owner)

        verify { storageService.delete("uuid.pdf") }
    }

    @Test
    fun `delete throws ForbiddenException when non-uploader non-admin tries to delete`() {
        val attachment = Attachment(
            issueId = issue.id, uploaderId = uploader.id, filename = "doc.pdf",
            storedName = "uuid.pdf", contentType = "application/pdf", size = 100L
        )
        every { attachmentRepository.findById(attachment.id) } returns Optional.of(attachment)
        every { issueService.findByKey("WOLF", "WOLF-1", other.id) } returns issue
        every { projectService.isProjectAdmin("WOLF", other.id) } returns false

        assertThrows<ForbiddenException> {
            service.delete("WOLF", "WOLF-1", attachment.id, other)
        }
    }
}
