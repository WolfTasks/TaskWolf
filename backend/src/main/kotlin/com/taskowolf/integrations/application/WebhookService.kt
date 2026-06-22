package com.taskowolf.integrations.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskowolf.auth.domain.User
import com.taskowolf.core.infrastructure.NotFoundException
import com.taskowolf.integrations.api.dto.CreateWebhookRequest
import com.taskowolf.integrations.api.dto.UpdateWebhookRequest
import com.taskowolf.integrations.api.dto.WebhookDeliveryResponse
import com.taskowolf.integrations.api.dto.WebhookResponse
import com.taskowolf.integrations.domain.Webhook
import com.taskowolf.integrations.domain.WebhookDelivery
import com.taskowolf.integrations.infrastructure.WebhookDeliveryRepository
import com.taskowolf.integrations.infrastructure.WebhookRepository
import com.taskowolf.projects.application.ProjectService
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Service
class WebhookService(
    private val webhookRepository: WebhookRepository,
    private val deliveryRepository: WebhookDeliveryRepository,
    private val projectService: ProjectService,
    private val ssrfValidator: SsrfValidator,
    private val hmacSigner: HmacSigner,
    private val objectMapper: ObjectMapper
) {
    data class CreateResult(val webhook: WebhookResponse, val plaintextSecret: String)

    @Transactional
    fun create(projectKey: String, req: CreateWebhookRequest, caller: User): CreateResult {
        val project = projectService.requireAdmin(projectKey, caller.id)
        ssrfValidator.validate(req.url)
        val plainSecret = req.secret?.takeIf { it.isNotBlank() } ?: generateSecret()
        val hook = webhookRepository.save(
            Webhook(
                projectId = project.id, url = req.url,
                secretHash = sha256(plainSecret), events = req.events,
                enabled = req.enabled, createdBy = caller.id
            )
        )
        return CreateResult(WebhookResponse.from(hook), plainSecret)
    }

    @Transactional(readOnly = true)
    fun list(projectKey: String, caller: User): List<WebhookResponse> {
        val project = projectService.requireMember(projectKey, caller.id)
        return webhookRepository.findByProjectId(project.id).map { WebhookResponse.from(it) }
    }

    @Transactional
    fun update(projectKey: String, webhookId: UUID, req: UpdateWebhookRequest, caller: User): WebhookResponse {
        val project = projectService.requireAdmin(projectKey, caller.id)
        val hook = webhookRepository.findByIdAndProjectId(webhookId, project.id)
            ?: throw NotFoundException("Webhook not found: $webhookId")
        req.url?.let { ssrfValidator.validate(it); hook.url = it }
        req.events?.let { hook.events = it }
        req.enabled?.let { hook.enabled = it }
        return WebhookResponse.from(webhookRepository.save(hook))
    }

    @Transactional
    fun delete(projectKey: String, webhookId: UUID, caller: User) {
        val project = projectService.requireAdmin(projectKey, caller.id)
        val hook = webhookRepository.findByIdAndProjectId(webhookId, project.id)
            ?: throw NotFoundException("Webhook not found: $webhookId")
        webhookRepository.delete(hook)
    }

    @Transactional(readOnly = true)
    fun listDeliveries(
        projectKey: String,
        webhookId: UUID,
        caller: User,
        page: Int,
        size: Int
    ): List<WebhookDeliveryResponse> {
        val project = projectService.requireMember(projectKey, caller.id)
        webhookRepository.findByIdAndProjectId(webhookId, project.id)
            ?: throw NotFoundException("Webhook not found: $webhookId")
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        return deliveryRepository.findByWebhookId(webhookId, pageable).content.map { WebhookDeliveryResponse.from(it) }
    }

    @Transactional
    fun testPing(projectKey: String, webhookId: UUID, caller: User): WebhookDeliveryResponse {
        val project = projectService.requireAdmin(projectKey, caller.id)
        val hook = webhookRepository.findByIdAndProjectId(webhookId, project.id)
            ?: throw NotFoundException("Webhook not found: $webhookId")
        val payload = objectMapper.writeValueAsString(
            mapOf("event" to "ping", "project" to projectKey, "timestamp" to Instant.now().toString())
        )
        val delivery = deliveryRepository.save(
            WebhookDelivery(webhookId = hook.id, eventType = "ping", payload = payload)
        )
        return WebhookDeliveryResponse.from(delivery)
    }

    fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun generateSecret(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
