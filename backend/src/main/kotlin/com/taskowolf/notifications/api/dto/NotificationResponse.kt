package com.taskowolf.notifications.api.dto

import com.taskowolf.notifications.domain.Notification
import com.taskowolf.notifications.domain.NotificationType
import java.time.Instant
import java.util.UUID

data class NotificationResponse(
    val id: UUID,
    val type: NotificationType,
    val title: String,
    val body: String?,
    val link: String?,
    val read: Boolean,
    val createdAt: Instant
) {
    companion object {
        fun from(n: Notification) = NotificationResponse(
            id = n.id,
            type = n.type,
            title = n.title,
            body = n.body,
            link = n.link,
            read = n.read,
            createdAt = n.createdAt!!
        )
    }
}
