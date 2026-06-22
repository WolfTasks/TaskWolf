package com.taskowolf.integrations.infrastructure

import com.taskowolf.integrations.domain.WebhookDelivery
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface WebhookDeliveryRepository : JpaRepository<WebhookDelivery, UUID> {
    fun findByWebhookId(webhookId: UUID, pageable: Pageable): Page<WebhookDelivery>

    @Query("""
        SELECT d FROM WebhookDelivery d
        WHERE d.deliveredAt IS NULL
          AND d.nextRetryAt <= :now
          AND d.attemptCount < 3
    """)
    fun findPendingRetries(now: Instant): List<WebhookDelivery>

    @Modifying
    @Query("DELETE FROM WebhookDelivery d WHERE d.createdAt < :cutoff")
    fun deleteOlderThan(cutoff: Instant): Int
}
