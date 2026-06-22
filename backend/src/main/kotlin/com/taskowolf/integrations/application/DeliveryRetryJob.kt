package com.taskowolf.integrations.application

import com.taskowolf.integrations.infrastructure.WebhookDeliveryRepository
import com.taskowolf.integrations.infrastructure.WebhookRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class DeliveryRetryJob(
    private val deliveryRepository: WebhookDeliveryRepository,
    private val webhookRepository: WebhookRepository,
    private val dispatcher: WebhookDispatcher
) {
    private val log = LoggerFactory.getLogger(DeliveryRetryJob::class.java)

    @Scheduled(fixedDelay = 30_000)
    fun retryPending() {
        val pending = deliveryRepository.findPendingRetries(Instant.now())
        if (pending.isNotEmpty()) {
            log.info("Retrying {} webhook deliveries", pending.size)
        }
        for (delivery in pending) {
            val hook = webhookRepository.findById(delivery.webhookId).orElse(null) ?: continue
            dispatcher.send(delivery, hook.url, hook.secretHash)
        }
    }
}
