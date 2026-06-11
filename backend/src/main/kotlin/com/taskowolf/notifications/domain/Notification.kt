package com.taskowolf.notifications.domain

import com.taskowolf.core.domain.AuditableEntity
import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "notifications")
class Notification(
    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val type: NotificationType,

    @Column(nullable = false, length = 255)
    val title: String,

    @Column(columnDefinition = "TEXT")
    val body: String? = null,

    @Column(length = 500)
    val link: String? = null,

    @Column(nullable = false)
    var read: Boolean = false
) : AuditableEntity()
