package com.taskowolf.notifications.api

import com.taskowolf.auth.domain.User
import com.taskowolf.notifications.api.dto.NotificationResponse
import com.taskowolf.notifications.application.NotificationService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(private val notificationService: NotificationService) {

    @GetMapping
    fun list(
        @AuthenticationPrincipal user: User,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ) = notificationService.listForUser(user.id, page, size).map { NotificationResponse.from(it) }

    @GetMapping("/unread-count")
    fun unreadCount(@AuthenticationPrincipal user: User) =
        mapOf("count" to notificationService.countUnread(user.id))

    @PatchMapping("/{id}/read")
    fun markRead(
        @PathVariable id: UUID,
        @AuthenticationPrincipal user: User
    ) = NotificationResponse.from(notificationService.markRead(id, user.id))
}
