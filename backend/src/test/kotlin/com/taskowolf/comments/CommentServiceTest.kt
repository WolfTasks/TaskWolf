package com.taskowolf.comments

import com.taskowolf.auth.domain.User
import com.taskowolf.comments.application.CommentService
import com.taskowolf.comments.domain.Comment
import com.taskowolf.comments.domain.events.CommentCreatedEvent
import com.taskowolf.comments.infrastructure.CommentRepository
import com.taskowolf.core.application.DomainEventPublisher
import com.taskowolf.core.infrastructure.ForbiddenException
import com.taskowolf.core.infrastructure.NotFoundException
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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.util.Optional
import java.util.UUID

class CommentServiceTest {

    private val commentRepository = mockk<CommentRepository>()
    private val issueService = mockk<IssueService>()
    private val projectService = mockk<ProjectService>()
    private val eventPublisher = mockk<DomainEventPublisher>(relaxed = true)
    private val service = CommentService(commentRepository, issueService, projectService, eventPublisher)

    private val author = User(email = "author@test.com", displayName = "Author")
    private val other = User(email = "other@test.com", displayName = "Other")
    private val owner = User(email = "owner@test.com", displayName = "Owner")

    // Build minimal issue
    private val workflow = Workflow(name = "Default", project = Project(key = "WOLF", name = "TaskWolf", owner = owner, workflow = null))
    private val status = WorkflowStatus("To Do", StatusCategory.TODO, "#6c8fef", 0, workflow)
    private val project = Project(key = "WOLF", name = "TaskWolf", owner = owner, workflow = workflow)
    private val issue = Issue(key = "WOLF-1", keyNumber = 1, title = "Test Issue", type = IssueType.TASK, status = status, project = project, reporter = owner)

    @Test
    fun `addComment creates comment and publishes CommentCreatedEvent`() {
        every { issueService.findByKey("WOLF", "WOLF-1", author.id) } returns issue
        every { commentRepository.save(any()) } returnsArgument 0

        val comment = service.addComment("WOLF", "WOLF-1", "Hello world", author)

        assertEquals(author.id, comment.authorId)
        assertEquals(issue.id, comment.issueId)
        assertEquals("Hello world", comment.body)

        val eventSlot = slot<CommentCreatedEvent>()
        verify { eventPublisher.publish(capture(eventSlot)) }
        assertEquals(comment, eventSlot.captured.comment)
    }

    @Test
    fun `listComments returns newest-first page excluding deleted`() {
        val newest = Comment(issueId = issue.id, authorId = author.id, body = "newest")
        every { issueService.findByKey("WOLF", "WOLF-1", author.id) } returns issue
        every {
            commentRepository.findByIssueIdAndDeletedAtIsNullOrderByCreatedAtDesc(issue.id, any())
        } returns PageImpl(listOf(newest), PageRequest.of(0, 5), 1)

        val result = service.listComments("WOLF", "WOLF-1", author.id, 0, 5)

        assertEquals(1, result.totalElements)
        assertEquals("newest", result.content[0].body)
    }

    @Test
    fun `editComment updates body and sets editedAt`() {
        val existing = Comment(issueId = issue.id, authorId = author.id, body = "Old")
        every { commentRepository.findById(existing.id) } returns Optional.of(existing)
        every { commentRepository.save(any()) } returnsArgument 0

        val updated = service.editComment(existing.id, "New body", author)

        assertEquals("New body", updated.body)
        assert(updated.editedAt != null)
    }

    @Test
    fun `editComment throws ForbiddenException when editor is not the author`() {
        val existing = Comment(issueId = issue.id, authorId = author.id, body = "Mine")
        every { commentRepository.findById(existing.id) } returns Optional.of(existing)

        assertThrows<ForbiddenException> {
            service.editComment(existing.id, "Hijacked", other)
        }
    }

    @Test
    fun `deleteComment soft-deletes own comment`() {
        val existing = Comment(issueId = issue.id, authorId = author.id, body = "Mine")
        every { commentRepository.findById(existing.id) } returns Optional.of(existing)
        every { commentRepository.save(any()) } returnsArgument 0

        service.deleteComment(existing.id, "WOLF", author)

        assert(existing.deletedAt != null)
        verify { commentRepository.save(existing) }
    }

    @Test
    fun `deleteComment allows project admin to delete any comment`() {
        val existing = Comment(issueId = issue.id, authorId = author.id, body = "Theirs")
        every { commentRepository.findById(existing.id) } returns Optional.of(existing)
        every { projectService.isProjectAdmin("WOLF", owner.id) } returns true
        every { commentRepository.save(any()) } returnsArgument 0

        service.deleteComment(existing.id, "WOLF", owner)

        assert(existing.deletedAt != null)
    }
}
