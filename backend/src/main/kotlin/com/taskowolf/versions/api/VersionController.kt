// backend/src/main/kotlin/com/taskowolf/versions/api/VersionController.kt
package com.taskowolf.versions.api

import com.taskowolf.auth.domain.User
import com.taskowolf.versions.api.dto.VersionRequest
import com.taskowolf.versions.api.dto.VersionResponse
import com.taskowolf.versions.application.VersionService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/projects/{key}/versions")
class VersionController(private val versionService: VersionService) {

    @GetMapping
    fun list(@PathVariable key: String, @AuthenticationPrincipal user: User) =
        versionService.list(key, user.id).map { VersionResponse.from(it) }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @PathVariable key: String,
        @Valid @RequestBody request: VersionRequest,
        @AuthenticationPrincipal user: User
    ) = VersionResponse.from(versionService.create(key, request, user))

    @PutMapping("/{id}")
    fun update(
        @PathVariable key: String,
        @PathVariable id: UUID,
        @Valid @RequestBody request: VersionRequest,
        @AuthenticationPrincipal user: User
    ) = VersionResponse.from(versionService.update(key, id, request, user))

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable key: String,
        @PathVariable id: UUID,
        @AuthenticationPrincipal user: User
    ) = versionService.delete(key, id, user)
}
