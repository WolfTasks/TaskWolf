package com.taskowolf.notifications.infrastructure

import com.taskowolf.notifications.domain.NotificationPreference
import com.taskowolf.notifications.domain.NotificationType
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface NotificationPreferenceRepository : JpaRepository<NotificationPreference, UUID> {
    fun findByUserId(userId: UUID): List<NotificationPreference>
    fun findByUserIdAndType(userId: UUID, type: NotificationType): NotificationPreference?
}
