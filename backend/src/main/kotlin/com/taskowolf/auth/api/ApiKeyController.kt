package com.taskowolf.auth.api

import com.taskowolf.auth.api.dto.CreateApiKeyRequest
import com.taskowolf.auth.application.ApiKeyService
import com.taskowolf.auth.domain.User
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/projects/{key}/api-keys")
class ApiKeyController(private val apiKeyService: ApiKeyService) {

    @GetMapping
    fun list(@PathVariable key: String, @AuthenticationPrincipal user: User) =
        apiKeyService.list(key, user)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @PathVariable key: String,
        @Valid @RequestBody request: CreateApiKeyRequest,
        @AuthenticationPrincipal user: User
    ) = apiKeyService.generate(key, request.name, request.expiresAt, user)

    @DeleteMapping("/{keyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revoke(
        @PathVariable key: String,
        @PathVariable keyId: UUID,
        @AuthenticationPrincipal user: User
    ) = apiKeyService.revoke(key, keyId, user)
}
