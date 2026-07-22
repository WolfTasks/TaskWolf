package com.taskowolf.notifications

import com.taskowolf.auth.domain.User
import com.taskowolf.auth.infrastructure.UserRepository
import com.taskowolf.comments.domain.Comment
import com.taskowolf.comments.domain.events.MentionEvent
import com.taskowolf.core.infrastructure.LocalizedMessages
import com.taskowolf.core.infrastructure.NotFoundException
import com.taskowolf.issues.domain.Issue
import com.taskowolf.issues.domain.IssueType
import com.taskowolf.issues.domain.events.IssueFieldChangedEvent
import com.taskowolf.notifications.application.NotificationService
import com.taskowolf.notifications.domain.Notification
import com.taskowolf.notifications.domain.NotificationChannel
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.support.ResourceBundleMessageSource
import java.util.Optional
import java.util.UUID

class NotificationServiceTest {

    private val repository = mockk<NotificationRepository>()
    private val preferences = mockk<com.taskowolf.notifications.application.NotificationPreferenceService>()
    private val userRepository = mockk<UserRepository>()
    private val messageSource = ResourceBundleMessageSource().apply {
        setBasename("messages"); setDefaultEncoding("UTF-8"); setFallbackToSystemLocale(false)
    }
    private val localizedMessages = LocalizedMessages(messageSource)
    private val service = NotificationService(repository, preferences, localizedMessages, userRepository)

    private val user = User(email = "user@test.com", displayName = "User")
    private val actor = User(email = "actor@test.com", displayName = "Actor")
    private val owner = User(email = "owner@test.com", displayName = "Owner")

    private val workflow = buildWorkflow(owner)
    private val status = WorkflowStatus("To Do", StatusCategory.TODO, "#6c8fef", 0, workflow)
    private val project = Project(key = "WOLF", name = "TaskWolf", owner = owner, workflow = workflow)
    private val issue = Issue(key = "WOLF-1", keyNumber = 1, title = "Test Issue",
        type = IssueType.TASK, status = status, project = project, reporter = owner, assignee = user)

    @Test
    fun `onMention saves COMMENT_MENTION notification with English title for null language`() {
        every { preferences.isEnabled(any(), any(), any()) } returns true
        every { repository.save(any()) } returnsArgument 0
        val comment = Comment(issueId = issue.id, authorId = actor.id, body = "Hey @User check this")
        val event = MentionEvent(mentionedUser = user, comment = comment, issue = issue)

        service.onMention(event)

        val slot = slot<Notification>()
        verify { repository.save(capture(slot)) }
        assertEquals(user.id, slot.captured.userId)
        assertEquals(NotificationType.COMMENT_MENTION, slot.captured.type)
        assertEquals("You were mentioned in WOLF-1", slot.captured.title)
        assertEquals("Hey @User check this", slot.captured.body) // user-content body unchanged
    }

    @Test
    fun `onMention renders title in recipient German`() {
        every { preferences.isEnabled(any(), any(), any()) } returns true
        every { repository.save(any()) } returnsArgument 0
        val germanUser = User(email = "de@test.com", displayName = "DE").apply { language = "de" }
        val comment = Comment(issueId = issue.id, authorId = actor.id, body = "Hallo")
        val event = MentionEvent(mentionedUser = germanUser, comment = comment, issue = issue)

        service.onMention(event)

        val slot = slot<Notification>()
        verify { repository.save(capture(slot)) }
        assertEquals("Sie wurden in WOLF-1 erwähnt", slot.captured.title)
    }

    @Test
    fun `onIssueFieldChanged saves ISSUE_ASSIGNED with localized title, body unchanged`() {
        every { preferences.isEnabled(any(), any(), any()) } returns true
        every { repository.save(any()) } returnsArgument 0
        val event = IssueFieldChangedEvent(issue = issue, actor = actor,
            field = "assignee", oldValue = null, newValue = user.displayName)

        service.onIssueFieldChanged(event)

        val slot = slot<Notification>()
        verify { repository.save(capture(slot)) }
        assertEquals(user.id, slot.captured.userId)
        assertEquals(NotificationType.ISSUE_ASSIGNED, slot.captured.type)
        assertEquals("You were assigned to WOLF-1", slot.captured.title)
        assertEquals("Test Issue", slot.captured.body) // issue.title stays as user-content
    }

    @Test
    fun `onMention skips save when in-app preference disabled`() {
        every { preferences.isEnabled(user.id, NotificationType.COMMENT_MENTION, NotificationChannel.IN_APP) } returns false
        val comment = Comment(issueId = issue.id, authorId = actor.id, body = "Hey @User")
        val event = MentionEvent(mentionedUser = user, comment = comment, issue = issue)

        service.onMention(event)

        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `createDirect renders templated body in recipient German`() {
        val germanUser = User(email = "de@test.com", displayName = "DE").apply { language = "de" }
        every { preferences.isEnabled(germanUser.id, NotificationType.SLA_BREACHED, NotificationChannel.IN_APP) } returns true
        every { userRepository.findById(germanUser.id) } returns Optional.of(germanUser)
        every { repository.save(any()) } returnsArgument 0

        service.createDirect(
            userId = germanUser.id,
            type = NotificationType.SLA_BREACHED,
            titleKey = "notification.slaBreached.title",
            link = "/issues/WOLF-1",
            titleArgs = arrayOf("WOLF-1"),
            bodyKey = "notification.slaBreached.body",
            bodyArgs = arrayOf("WOLF-1", "60"),
        )

        val slot = slot<Notification>()
        verify { repository.save(capture(slot)) }
        assertEquals("SLA verletzt: WOLF-1", slot.captured.title)
        assertEquals("Vorgang WOLF-1 hat seine SLA-Lösungszeit von 60 Minuten überschritten.", slot.captured.body)
    }

    @Test
    fun `createDirect keeps rawBody as user-content and renders title`() {
        every { preferences.isEnabled(user.id, NotificationType.AUTOMATION, NotificationChannel.IN_APP) } returns true
        every { userRepository.findById(user.id) } returns Optional.of(user)
        every { repository.save(any()) } returnsArgument 0

        service.createDirect(
            userId = user.id,
            type = NotificationType.AUTOMATION,
            titleKey = "notification.automation.title",
            link = "/l",
            titleArgs = arrayOf("WOLF-1"),
            rawBody = "Custom automation message",
        )

        val slot = slot<Notification>()
        verify { repository.save(capture(slot)) }
        assertEquals("Automation: WOLF-1", slot.captured.title)
        assertEquals("Custom automation message", slot.captured.body)
    }

    @Test
    fun `createDirect falls back to English when recipient user not found`() {
        every { preferences.isEnabled(user.id, NotificationType.AUTOMATION, NotificationChannel.IN_APP) } returns true
        every { userRepository.findById(user.id) } returns Optional.empty()
        every { repository.save(any()) } returnsArgument 0

        service.createDirect(
            userId = user.id,
            type = NotificationType.AUTOMATION,
            titleKey = "notification.automation.title",
            link = "/l",
            titleArgs = arrayOf("WOLF-1"),
            rawBody = "msg",
        )

        val slot = slot<Notification>()
        verify { repository.save(capture(slot)) }
        assertEquals("Automation: WOLF-1", slot.captured.title)
    }

    @Test
    fun `createDirect skips save when in-app preference disabled`() {
        every { preferences.isEnabled(user.id, NotificationType.AUTOMATION, NotificationChannel.IN_APP) } returns false

        service.createDirect(
            userId = user.id,
            type = NotificationType.AUTOMATION,
            titleKey = "notification.automation.title",
            link = "/l",
            titleArgs = arrayOf("WOLF-1"),
        )

        verify(exactly = 0) { repository.save(any()) }
        verify(exactly = 0) { userRepository.findById(any()) }
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
