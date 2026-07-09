package com.taskowolf.notifications.application

import com.taskowolf.comments.domain.events.MentionEvent
import com.taskowolf.core.infrastructure.NotFoundException
import com.taskowolf.issues.domain.events.IssueFieldChangedEvent
import com.taskowolf.notifications.domain.Notification
import com.taskowolf.notifications.domain.NotificationChannel
import com.taskowolf.notifications.domain.NotificationType
import com.taskowolf.notifications.infrastructure.NotificationRepository
import org.springframework.context.event.EventListener
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class NotificationService(
    private val repository: NotificationRepository,
    private val preferences: NotificationPreferenceService
) {

    @EventListener
    @Transactional
    fun onMention(event: MentionEvent) {
        if (!preferences.isEnabled(event.mentionedUser.id, NotificationType.COMMENT_MENTION, NotificationChannel.IN_APP)) return
        repository.save(
            Notification(
                userId = event.mentionedUser.id,
                type = NotificationType.COMMENT_MENTION,
                title = "You were mentioned in ${event.issue.key}",
                body = event.comment.body.take(200),
                link = "/issues/${event.issue.key}"
            )
        )
    }

    @EventListener
    @Transactional
    fun onIssueFieldChanged(event: IssueFieldChangedEvent) {
        if (event.field != "assignee") return
        val assignee = event.issue.assignee ?: return
        if (!preferences.isEnabled(assignee.id, NotificationType.ISSUE_ASSIGNED, NotificationChannel.IN_APP)) return
        repository.save(
            Notification(
                userId = assignee.id,
                type = NotificationType.ISSUE_ASSIGNED,
                title = "You were assigned to ${event.issue.key}",
                body = event.issue.title,
                link = "/issues/${event.issue.key}"
            )
        )
    }

    @Transactional
    fun markRead(notificationId: UUID, userId: UUID): Notification {
        val notif = repository.findByIdAndUserId(notificationId, userId)
            ?: throw NotFoundException("Notification not found")
        notif.read = true
        return repository.save(notif)
    }

    @Transactional(readOnly = true)
    fun listForUser(userId: UUID, page: Int, size: Int): Page<Notification> =
        repository.findAllByUserIdOrderByCreatedAtDesc(
            userId,
            PageRequest.of(page, size)
        )

    @Transactional(readOnly = true)
    fun countUnread(userId: UUID): Long = repository.countByUserIdAndReadFalse(userId)

    @Transactional
    fun createDirect(userId: UUID, type: NotificationType, title: String, body: String, link: String) {
        if (!preferences.isEnabled(userId, type, NotificationChannel.IN_APP)) return
        repository.save(Notification(userId = userId, type = type, title = title, body = body, link = link))
    }
}
