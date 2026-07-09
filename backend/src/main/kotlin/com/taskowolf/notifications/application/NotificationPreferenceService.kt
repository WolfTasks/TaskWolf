package com.taskowolf.notifications.application

import com.taskowolf.notifications.domain.NotificationChannel
import com.taskowolf.notifications.domain.NotificationPreference
import com.taskowolf.notifications.domain.NotificationType
import com.taskowolf.notifications.infrastructure.NotificationPreferenceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class NotificationPreferenceService(
    private val repository: NotificationPreferenceRepository
) {
    @Transactional(readOnly = true)
    fun getMatrix(userId: UUID): List<NotificationPreference> {
        val existing = repository.findByUserId(userId).associateBy { it.type }
        return NotificationType.entries.map { type ->
            existing[type] ?: NotificationPreference(userId = userId, type = type)
        }
    }

    @Transactional
    fun update(userId: UUID, prefs: Map<NotificationType, Pair<Boolean, Boolean>>) {
        prefs.forEach { (type, flags) ->
            val row = repository.findByUserIdAndType(userId, type)
                ?: NotificationPreference(userId = userId, type = type)
            row.inAppEnabled = flags.first
            row.emailEnabled = flags.second
            repository.save(row)
        }
    }

    @Transactional(readOnly = true)
    fun isEnabled(userId: UUID, type: NotificationType, channel: NotificationChannel): Boolean {
        val pref = repository.findByUserIdAndType(userId, type) ?: return true
        return when (channel) {
            NotificationChannel.IN_APP -> pref.inAppEnabled
            NotificationChannel.EMAIL -> pref.emailEnabled
        }
    }
}
