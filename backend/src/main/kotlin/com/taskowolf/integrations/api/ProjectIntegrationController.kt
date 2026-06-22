package com.taskowolf.integrations.api

import com.taskowolf.auth.domain.User
import com.taskowolf.integrations.api.dto.CreateProjectIntegrationRequest
import com.taskowolf.integrations.application.ProjectIntegrationService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/projects/{key}/integrations")
class ProjectIntegrationController(private val service: ProjectIntegrationService) {

    @GetMapping
    fun list(@PathVariable key: String, @AuthenticationPrincipal user: User) =
        service.list(key, user)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @PathVariable key: String,
        @RequestBody req: CreateProjectIntegrationRequest,
        @AuthenticationPrincipal user: User
    ) = service.create(key, req, user)

    @DeleteMapping("/{integrationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable key: String,
        @PathVariable integrationId: UUID,
        @AuthenticationPrincipal user: User
    ) = service.delete(key, integrationId, user)
}
