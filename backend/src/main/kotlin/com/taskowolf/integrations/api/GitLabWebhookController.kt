package com.taskowolf.integrations.api

import com.taskowolf.integrations.application.IncomingWebhookService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/integrations/gitlab")
class GitLabWebhookController(private val incomingWebhookService: IncomingWebhookService) {

    @PostMapping("/{projectKey}/webhook")
    fun receive(
        @PathVariable projectKey: String,
        @RequestBody payload: String,
        @RequestHeader(value = "X-Gitlab-Token", required = false) token: String?
    ): ResponseEntity<Map<String, String>> {
        return try {
            incomingWebhookService.handleGitLab(projectKey, payload, token)
            ResponseEntity.ok(mapOf("status" to "ok"))
        } catch (e: SecurityException) {
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to e.message.orEmpty()))
        }
    }
}
