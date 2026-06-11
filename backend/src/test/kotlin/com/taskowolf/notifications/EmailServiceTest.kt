package com.taskowolf.notifications

import com.taskowolf.auth.domain.User
import com.taskowolf.comments.domain.Comment
import com.taskowolf.comments.domain.events.MentionEvent
import com.taskowolf.issues.domain.Issue
import com.taskowolf.issues.domain.IssueType
import com.taskowolf.issues.domain.events.IssueFieldChangedEvent
import com.taskowolf.notifications.application.EmailService
import com.taskowolf.projects.domain.Project
import com.taskowolf.workflows.domain.StatusCategory
import com.taskowolf.workflows.domain.Workflow
import com.taskowolf.workflows.domain.WorkflowStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender

class EmailServiceTest {

    private val mailSender = mockk<JavaMailSender>(relaxed = true)

    private val owner = User(email = "owner@test.com", displayName = "Owner")
    private val assignee = User(email = "assignee@test.com", displayName = "Assignee")
    private val actor = User(email = "actor@test.com", displayName = "Actor")

    private val project = Project(key = "WOLF", name = "TaskWolf", owner = owner, workflow = null)
    private val workflow = Workflow(name = "Default", project = project)
    private val status = WorkflowStatus("To Do", StatusCategory.TODO, "#6c8fef", 0, workflow)
    private val issue = Issue(
        key = "WOLF-1", keyNumber = 1, title = "My Issue",
        type = IssueType.TASK, status = status, project = project,
        reporter = owner, assignee = assignee
    )

    @Test
    fun `onMention sends email when SMTP host is configured`() {
        val service = EmailService(mailSender, mailHost = "smtp.example.com", fromAddress = "noreply@example.com")
        val comment = Comment(issueId = issue.id, authorId = actor.id, body = "Hey @Assignee look at this")
        val event = MentionEvent(mentionedUser = assignee, comment = comment, issue = issue)

        service.onMention(event)

        verify { mailSender.send(any<SimpleMailMessage>()) }
    }

    @Test
    fun `onMention skips when SMTP host is blank`() {
        val service = EmailService(mailSender, mailHost = "", fromAddress = "noreply@example.com")
        val comment = Comment(issueId = issue.id, authorId = actor.id, body = "Mention")
        val event = MentionEvent(mentionedUser = assignee, comment = comment, issue = issue)

        service.onMention(event)

        verify(exactly = 0) { mailSender.send(any<SimpleMailMessage>()) }
    }

    @Test
    fun `onAssigned sends email when assignee changes`() {
        val service = EmailService(mailSender, mailHost = "smtp.example.com", fromAddress = "noreply@example.com")
        val event = IssueFieldChangedEvent(issue = issue, actor = actor,
            field = "assignee", oldValue = null, newValue = assignee.displayName)

        service.onAssigned(event)

        verify { mailSender.send(any<SimpleMailMessage>()) }
    }

    @Test
    fun `onAssigned skips when field is not assignee`() {
        val service = EmailService(mailSender, mailHost = "smtp.example.com", fromAddress = "noreply@example.com")
        val event = IssueFieldChangedEvent(issue = issue, actor = actor,
            field = "title", oldValue = "Old", newValue = "New")

        service.onAssigned(event)

        verify(exactly = 0) { mailSender.send(any<SimpleMailMessage>()) }
    }
}
