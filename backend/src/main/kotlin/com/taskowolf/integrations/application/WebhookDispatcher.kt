package com.taskowolf.integrations.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.attachments.domain.events.AttachmentAddedEvent
import com.taskowolf.comments.domain.events.CommentCreatedEvent
import com.taskowolf.integrations.domain.WebhookDelivery
import com.taskowolf.integrations.domain.WebhookEventType
import com.taskowolf.integrations.infrastructure.WebhookDeliveryRepository
import com.taskowolf.integrations.infrastructure.WebhookRepository
import com.taskowolf.issues.domain.events.IssueCreatedEvent
import com.taskowolf.issues.domain.events.IssueFieldChangedEvent
import com.taskowolf.issues.domain.events.IssueStatusChangedEvent
import com.taskowolf.sprints.domain.events.SprintCompletedEvent
import com.taskowolf.sprints.domain.events.SprintStartedEvent
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestTemplate
import java.time.Instant
import java.util.UUID

@Component
class WebhookDispatcher(
    private val webhookRepository: WebhookRepository,
    private val deliveryRepository: WebhookDeliveryRepository,
    private val hmacSigner: HmacSigner,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(WebhookDispatcher::class.java)
    private val restTemplate = RestTemplate()

    @EventListener
    fun onIssueCreated(event: IssueCreatedEvent) =
        dispatch(WebhookEventType.ISSUE_CREATED, event.issue.project.id,
            mapOf("issueId" to event.issue.id, "issueKey" to event.issue.key, "title" to event.issue.title))

    @EventListener
    fun onStatusChanged(event: IssueStatusChangedEvent) =
        dispatch(WebhookEventType.ISSUE_STATUS_CHANGED, event.issue.project.id,
            mapOf("issueId" to event.issue.id, "issueKey" to event.issue.key,
                  "newStatus" to event.newStatus.name, "oldStatus" to event.oldStatus.name))

    @EventListener
    fun onFieldChanged(event: IssueFieldChangedEvent) {
        val eventType = if (event.field == "assignee") WebhookEventType.ISSUE_ASSIGNED
                        else WebhookEventType.ISSUE_UPDATED
        dispatch(eventType, event.issue.project.id,
            mapOf("issueId" to event.issue.id, "issueKey" to event.issue.key,
                  "field" to event.field, "newValue" to (event.newValue ?: "")))
    }

    @EventListener
    fun onSprintStarted(event: SprintStartedEvent) =
        dispatch(WebhookEventType.SPRINT_STARTED, event.sprint.project.id,
            mapOf("sprintId" to event.sprint.id, "sprintName" to event.sprint.name))

    @EventListener
    fun onSprintCompleted(event: SprintCompletedEvent) =
        dispatch(WebhookEventType.SPRINT_COMPLETED, event.sprint.project.id,
            mapOf("sprintId" to event.sprint.id, "sprintName" to event.sprint.name))

    @EventListener
    fun onCommentCreated(event: CommentCreatedEvent) =
        dispatch(WebhookEventType.COMMENT_CREATED, event.issue.project.id,
            mapOf("issueId" to event.issue.id, "commentId" to event.comment.id))

    @EventListener
    fun onAttachmentAdded(event: AttachmentAddedEvent) =
        dispatch(WebhookEventType.ATTACHMENT_ADDED, event.issue.project.id,
            mapOf("attachmentId" to event.attachment.id, "filename" to event.attachment.filename))

    private fun dispatch(eventType: String, projectId: UUID, data: Map<String, Any?>) {
        val hooks = webhookRepository.findByProjectIdAndEnabled(projectId, true)
            .filter { it.events.contains(eventType) }
        if (hooks.isEmpty()) return

        val payload = objectMapper.writeValueAsString(
            mapOf("event" to eventType, "projectId" to projectId, "data" to data,
                  "timestamp" to Instant.now().toString())
        )
        for (hook in hooks) {
            val delivery = deliveryRepository.save(
                WebhookDelivery(webhookId = hook.id, eventType = eventType, payload = payload,
                    nextRetryAt = Instant.now())
            )
            sendAsync(delivery.id)
        }
    }

    @Async
    @Transactional
    fun sendAsync(deliveryId: UUID) {
        val delivery = deliveryRepository.findById(deliveryId).orElse(null) ?: return
        val hook = webhookRepository.findById(delivery.webhookId).orElse(null) ?: return
        send(delivery, hook.url, hook.secretHash)
    }

    fun send(delivery: WebhookDelivery, url: String, secretHash: String) {
        delivery.attemptCount++
        try {
            val signature = hmacSigner.sign(delivery.payload, secretHash)
            val headers = HttpHeaders().apply {
                set("Content-Type", "application/json")
                set("X-TaskWolf-Signature", signature)
                set("X-TaskWolf-Event", delivery.eventType)
            }
            val response = restTemplate.postForEntity(url, HttpEntity(delivery.payload, headers), String::class.java)
            delivery.responseStatus = response.statusCode.value()
            delivery.responseBody = response.body?.take(4096)
            delivery.deliveredAt = Instant.now()
            delivery.nextRetryAt = null
        } catch (e: Exception) {
            log.warn("Webhook delivery {} failed (attempt {}): {}", delivery.id, delivery.attemptCount, e.message)
            delivery.responseBody = e.message?.take(4096)
            if (delivery.attemptCount < 3) {
                delivery.nextRetryAt = Instant.now().plusSeconds(retryDelaySeconds(delivery.attemptCount))
            }
        }
        deliveryRepository.save(delivery)
    }

    private fun retryDelaySeconds(attempt: Int): Long = when (attempt) {
        1 -> 60L
        2 -> 300L
        else -> 1800L
    }
}
