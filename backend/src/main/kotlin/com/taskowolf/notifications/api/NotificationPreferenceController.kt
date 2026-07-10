package com.taskowolf.notifications.api

import com.taskowolf.auth.domain.User
import com.taskowolf.core.infrastructure.BadRequestException
import com.taskowolf.notifications.api.dto.NotificationPreferencesRequest
import com.taskowolf.notifications.api.dto.NotificationPreferencesResponse
import com.taskowolf.notifications.application.NotificationPreferenceService
import com.taskowolf.notifications.domain.NotificationType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/me/notification-preferences")
class NotificationPreferenceController(
    private val service: NotificationPreferenceService
) {
    @GetMapping
    fun get(@AuthenticationPrincipal user: User): NotificationPreferencesResponse =
        NotificationPreferencesResponse.from(service.getMatrix(user.id))

    @PutMapping
    fun update(
        @RequestBody request: NotificationPreferencesRequest,
        @AuthenticationPrincipal user: User
    ): NotificationPreferencesResponse {
        val map = request.preferences.associate { item ->
            val type = NotificationType.entries.find { it.name == item.type }
                ?: throw BadRequestException("Unknown notification type: ${item.type}")
            type to Pair(item.inApp, item.email)
        }
        service.update(user.id, map)
        return NotificationPreferencesResponse.from(service.getMatrix(user.id))
    }
}
