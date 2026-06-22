package com.taskowolf.integrations.application

import com.taskowolf.integrations.infrastructure.WebhookDeliveryRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component
class DeliveryCleanupJob(
    private val deliveryRepository: WebhookDeliveryRepository
) {
    private val log = LoggerFactory.getLogger(DeliveryCleanupJob::class.java)

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    fun cleanupOldDeliveries() {
        val cutoff = Instant.now().minus(30, ChronoUnit.DAYS)
        val deleted = deliveryRepository.deleteOlderThan(cutoff)
        log.info("Cleaned up {} webhook deliveries older than 30 days", deleted)
    }
}
