package com.taskowolf.notifications.application

import com.taskowolf.auth.infrastructure.UserRepository
import com.taskowolf.comments.domain.events.MentionEvent
import com.taskowolf.core.infrastructure.LocalizedMessages
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
import java.util.Locale
import java.util.UUID

@Service
class NotificationService(
    private val repository: NotificationRepository,
    private val preferences: NotificationPreferenceService,
    private val localizedMessages: LocalizedMessages,
    private val userRepository: UserRepository
) {

    @EventListener
    @Transactional
    fun onMention(event: MentionEvent) {
        if (!preferences.isEnabled(event.mentionedUser.id, NotificationType.COMMENT_MENTION, NotificationChannel.IN_APP)) return
        val locale = localizedMessages.localeOf(event.mentionedUser)
        repository.save(
            Notification(
                userId = event.mentionedUser.id,
                type = NotificationType.COMMENT_MENTION,
                title = localizedMessages.get("notification.mention.title", locale, event.issue.key),
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
        val locale = localizedMessages.localeOf(assignee)
        repository.save(
            Notification(
                userId = assignee.id,
                type = NotificationType.ISSUE_ASSIGNED,
                title = localizedMessages.get("notification.assigned.title", locale, event.issue.key),
                body = event.issue.title,
                link = "/issues/${event.issue.key}"
            )
        )
    }

    @Transactional
    fun markRead(notificationId: UUID, userId: UUID): Notification {
        val notif = repository.findByIdAndUserId(notificationId, userId)
            ?: throw NotFoundException.keyed("notification.notFound")
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

    /**
     * Creates a notification, rendering [titleKey] (and optional [bodyKey]) in the recipient's
     * stored language. Pass [rawBody] for a user-content body that must not be translated; if
     * neither [bodyKey] nor [rawBody] is set, the body is empty.
     */
    @Transactional
    fun createDirect(
        userId: UUID,
        type: NotificationType,
        titleKey: String,
        link: String,
        titleArgs: Array<out Any?> = emptyArray(),
        bodyKey: String? = null,
        bodyArgs: Array<out Any?> = emptyArray(),
        rawBody: String? = null,
    ) {
        if (!preferences.isEnabled(userId, type, NotificationChannel.IN_APP)) return
        val locale = userRepository.findById(userId)
            .map(localizedMessages::localeOf).orElse(Locale.ENGLISH)
        val title = localizedMessages.get(titleKey, locale, *titleArgs)
        val body = if (bodyKey != null) localizedMessages.get(bodyKey, locale, *bodyArgs) else rawBody.orEmpty()
        repository.save(Notification(userId = userId, type = type, title = title, body = body, link = link))
    }
}
