package com.taskowolf.notifications.domain

import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(
    name = "notification_preferences",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "type"])]
)
class NotificationPreference(
    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val type: NotificationType,

    @Column(name = "in_app_enabled", nullable = false)
    var inAppEnabled: Boolean = true,

    @Column(name = "email_enabled", nullable = false)
    var emailEnabled: Boolean = true
) : AuditableEntity()
