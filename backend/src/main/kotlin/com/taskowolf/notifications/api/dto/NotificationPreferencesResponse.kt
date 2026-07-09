package com.taskowolf.notifications.api.dto

import com.taskowolf.notifications.domain.NotificationPreference

data class NotificationPreferenceItem(
    val type: String,
    val inApp: Boolean,
    val email: Boolean
)

data class NotificationPreferencesResponse(val preferences: List<NotificationPreferenceItem>) {
    companion object {
        fun from(prefs: List<NotificationPreference>) = NotificationPreferencesResponse(
            prefs.map { NotificationPreferenceItem(it.type.name, it.inAppEnabled, it.emailEnabled) }
        )
    }
}
