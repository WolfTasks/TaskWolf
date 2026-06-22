package com.taskowolf.integrations.api

import com.taskowolf.auth.domain.User
import com.taskowolf.integrations.api.dto.CreateWebhookRequest
import com.taskowolf.integrations.api.dto.UpdateWebhookRequest
import com.taskowolf.integrations.application.WebhookService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/projects/{key}/webhooks")
class WebhookController(private val webhookService: WebhookService) {

    @GetMapping
    fun list(@PathVariable key: String, @AuthenticationPrincipal user: User) =
        webhookService.list(key, user)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @PathVariable key: String,
        @Valid @RequestBody req: CreateWebhookRequest,
        @AuthenticationPrincipal user: User
    ): Map<String, Any> {
        val result = webhookService.create(key, req, user)
        return mapOf("webhook" to result.webhook, "plaintextSecret" to result.plaintextSecret)
    }

    @PutMapping("/{webhookId}")
    fun update(
        @PathVariable key: String,
        @PathVariable webhookId: UUID,
        @RequestBody req: UpdateWebhookRequest,
        @AuthenticationPrincipal user: User
    ) = webhookService.update(key, webhookId, req, user)

    @DeleteMapping("/{webhookId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable key: String,
        @PathVariable webhookId: UUID,
        @AuthenticationPrincipal user: User
    ) = webhookService.delete(key, webhookId, user)

    @GetMapping("/{webhookId}/deliveries")
    fun deliveries(
        @PathVariable key: String,
        @PathVariable webhookId: UUID,
        @AuthenticationPrincipal user: User,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ) = webhookService.listDeliveries(key, webhookId, user, page, size)

    @PostMapping("/{webhookId}/test")
    @ResponseStatus(HttpStatus.CREATED)
    fun testPing(
        @PathVariable key: String,
        @PathVariable webhookId: UUID,
        @AuthenticationPrincipal user: User
    ) = webhookService.testPing(key, webhookId, user)
}
