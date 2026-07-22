package com.taskowolf.notifications.application

import com.taskowolf.comments.domain.events.MentionEvent
import com.taskowolf.core.infrastructure.LocalizedMessages
import com.taskowolf.issues.domain.events.IssueFieldChangedEvent
import com.taskowolf.notifications.domain.NotificationChannel
import com.taskowolf.notifications.domain.NotificationType
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.event.EventListener
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Service

@Service
class EmailService(
    private val preferences: NotificationPreferenceService,
    private val mailSender: JavaMailSender,
    @Value("\${spring.mail.host:}") private val mailHost: String,
    @Value("\${taskowolf.smtp.from:TaskWolf <noreply@example.com>}") private val fromAddress: String,
    private val localizedMessages: LocalizedMessages
) {
    private val enabled get() = mailHost.isNotBlank()

    @EventListener
    fun onMention(event: MentionEvent) {
        if (!enabled) return
        if (!preferences.isEnabled(event.mentionedUser.id, NotificationType.COMMENT_MENTION, NotificationChannel.EMAIL)) return
        val locale = localizedMessages.localeOf(event.mentionedUser)
        val msg = SimpleMailMessage().apply {
            from = fromAddress
            setTo(event.mentionedUser.email)
            subject = localizedMessages.get("email.mention.subject", locale, event.issue.key)
            text = localizedMessages.get(
                "email.mention.body", locale,
                event.issue.key, event.issue.title, event.comment.body.take(500)
            )
        }
        mailSender.send(msg)
    }

    @EventListener
    fun onAssigned(event: IssueFieldChangedEvent) {
        if (!enabled || event.field != "assignee") return
        val assignee = event.issue.assignee ?: return
        if (!preferences.isEnabled(assignee.id, NotificationType.ISSUE_ASSIGNED, NotificationChannel.EMAIL)) return
        val locale = localizedMessages.localeOf(assignee)
        val msg = SimpleMailMessage().apply {
            from = fromAddress
            setTo(assignee.email)
            subject = localizedMessages.get("email.assigned.subject", locale, event.issue.key)
            text = localizedMessages.get("email.assigned.body", locale, event.issue.key, event.issue.title)
        }
        mailSender.send(msg)
    }
}
