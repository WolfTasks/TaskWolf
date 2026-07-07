package com.taskowolf.auth.api

import com.taskowolf.auth.api.dto.CreateAccessTokenRequest
import com.taskowolf.auth.application.AccessTokenService
import com.taskowolf.auth.domain.User
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/me/tokens")
class AccessTokenController(private val accessTokenService: AccessTokenService) {

    @GetMapping
    fun list(@AuthenticationPrincipal user: User) = accessTokenService.list(user)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: CreateAccessTokenRequest,
        @AuthenticationPrincipal user: User
    ) = accessTokenService.create(user, request.name, request.scope, request.expiresAt)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revoke(@PathVariable id: UUID, @AuthenticationPrincipal user: User) =
        accessTokenService.revoke(user, id)
}
