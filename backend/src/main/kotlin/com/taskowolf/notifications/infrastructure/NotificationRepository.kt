package com.taskowolf.notifications.infrastructure

import com.taskowolf.notifications.domain.Notification
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface NotificationRepository : JpaRepository<Notification, UUID> {
    fun findAllByUserIdOrderByCreatedAtDesc(userId: UUID, pageable: Pageable): Page<Notification>
    fun findByIdAndUserId(id: UUID, userId: UUID): Notification?
    fun countByUserIdAndReadFalse(userId: UUID): Long
}
