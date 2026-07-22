package com.taskowolf.notifications

import com.taskowolf.auth.domain.User
import com.taskowolf.comments.domain.Comment
import com.taskowolf.comments.domain.events.MentionEvent
import com.taskowolf.core.infrastructure.LocalizedMessages
import com.taskowolf.issues.domain.Issue
import com.taskowolf.issues.domain.IssueType
import com.taskowolf.issues.domain.events.IssueFieldChangedEvent
import com.taskowolf.notifications.application.EmailService
import com.taskowolf.notifications.application.NotificationPreferenceService
import com.taskowolf.notifications.domain.NotificationChannel
import com.taskowolf.notifications.domain.NotificationType
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
import org.springframework.context.support.ResourceBundleMessageSource
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender

class EmailServiceTest {

    private val preferences = mockk<NotificationPreferenceService>()
    private val mailSender = mockk<JavaMailSender>(relaxed = true)
    private val messageSource = ResourceBundleMessageSource().apply {
        setBasename("messages"); setDefaultEncoding("UTF-8"); setFallbackToSystemLocale(false)
    }
    private val localizedMessages = LocalizedMessages(messageSource)
    private val service = EmailService(preferences, mailSender, "smtp.test", "TaskWolf <noreply@test.com>", localizedMessages)

    private val owner = User(email = "owner@test.com", displayName = "Owner")
    private val workflow = Workflow(name = "Default", project = Project(key = "WOLF", name = "TaskWolf", owner = owner, workflow = null))
    private val status = WorkflowStatus("To Do", StatusCategory.TODO, "#6c8fef", 0, workflow)
    private val project = Project(key = "WOLF", name = "TaskWolf", owner = owner, workflow = workflow)

    private fun recipient(language: String?) = User(email = "r@test.com", displayName = "R").apply { this.language = language }
    private fun issueFor(assignee: User?) = Issue(key = "WOLF-1", keyNumber = 1, title = "My Issue",
        type = IssueType.TASK, status = status, project = project, reporter = owner, assignee = assignee)

    private fun captureMail(): SimpleMailMessage {
        val slot = slot<SimpleMailMessage>()
        verify { mailSender.send(capture(slot)) }
        return slot.captured
    }

    @Test
    fun `onMention renders subject and body in recipient German`() {
        every { preferences.isEnabled(any(), any(), any()) } returns true
        val user = recipient("de")
        val issue = issueFor(assignee = null)
        val comment = Comment(issueId = issue.id, authorId = owner.id, body = "Great comment")

        service.onMention(MentionEvent(mentionedUser = user, comment = comment, issue = issue))

        val mail = captureMail()
        assertEquals("Sie wurden in WOLF-1 erwähnt", mail.subject)
        assertEquals("WOLF-1: My Issue\n\nGreat comment", mail.text)
    }

    @Test
    fun `onMention renders subject in English when language null`() {
        every { preferences.isEnabled(any(), any(), any()) } returns true
        val user = recipient(null)
        val issue = issueFor(assignee = null)
        val comment = Comment(issueId = issue.id, authorId = owner.id, body = "Great comment")

        service.onMention(MentionEvent(mentionedUser = user, comment = comment, issue = issue))

        assertEquals("You were mentioned in WOLF-1", captureMail().subject)
    }

    @Test
    fun `onAssigned renders subject and body in recipient German`() {
        every { preferences.isEnabled(any(), any(), any()) } returns true
        val assignee = recipient("de")
        val issue = issueFor(assignee = assignee)
        val event = IssueFieldChangedEvent(issue = issue, actor = owner,
            field = "assignee", oldValue = null, newValue = assignee.displayName)

        service.onAssigned(event)

        val mail = captureMail()
        assertEquals("Ihnen wurde WOLF-1 zugewiesen", mail.subject)
        assertEquals("Sie wurden zugewiesen zu: WOLF-1\nMy Issue", mail.text)
    }

    @Test
    fun `onAssigned renders subject in English when language en`() {
        every { preferences.isEnabled(any(), any(), any()) } returns true
        val assignee = recipient("en")
        val issue = issueFor(assignee = assignee)
        val event = IssueFieldChangedEvent(issue = issue, actor = owner,
            field = "assignee", oldValue = null, newValue = assignee.displayName)

        service.onAssigned(event)

        assertEquals("You were assigned to WOLF-1", captureMail().subject)
    }

    // --- Pre-existing guard-condition coverage (predates Phase 3; retained so the
    // LocalizedMessages constructor change doesn't silently drop this coverage) ---

    @Test
    fun `onMention skips when SMTP host is blank`() {
        val blankHostService = EmailService(preferences, mailSender, "", "TaskWolf <noreply@test.com>", localizedMessages)
        val user = recipient(null)
        val issue = issueFor(assignee = null)
        val comment = Comment(issueId = issue.id, authorId = owner.id, body = "Mention")

        blankHostService.onMention(MentionEvent(mentionedUser = user, comment = comment, issue = issue))

        verify(exactly = 0) { mailSender.send(any<SimpleMailMessage>()) }
    }

    @Test
    fun `onAssigned skips when field is not assignee`() {
        val assignee = recipient(null)
        val issue = issueFor(assignee = assignee)
        val event = IssueFieldChangedEvent(issue = issue, actor = owner,
            field = "title", oldValue = "Old", newValue = "New")

        service.onAssigned(event)

        verify(exactly = 0) { mailSender.send(any<SimpleMailMessage>()) }
    }

    @Test
    fun `onMention skips email when preference disabled`() {
        val user = recipient(null)
        every { preferences.isEnabled(user.id, NotificationType.COMMENT_MENTION, NotificationChannel.EMAIL) } returns false
        val issue = issueFor(assignee = null)
        val comment = Comment(issueId = issue.id, authorId = owner.id, body = "Mention")

        service.onMention(MentionEvent(mentionedUser = user, comment = comment, issue = issue))

        verify(exactly = 0) { mailSender.send(any<SimpleMailMessage>()) }
    }
}
