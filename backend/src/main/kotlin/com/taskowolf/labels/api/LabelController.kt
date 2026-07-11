package com.taskowolf.labels.api

import com.taskowolf.auth.domain.User
import com.taskowolf.labels.api.dto.LabelRequest
import com.taskowolf.labels.api.dto.LabelResponse
import com.taskowolf.labels.application.LabelService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/projects/{key}/labels")
class LabelController(private val labelService: LabelService) {

    @GetMapping
    fun list(@PathVariable key: String, @AuthenticationPrincipal user: User) =
        labelService.list(key, user.id).map { LabelResponse.from(it) }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@projectSecurity.canWrite(#key, authentication)")
    fun create(
        @PathVariable key: String,
        @Valid @RequestBody request: LabelRequest,
        @AuthenticationPrincipal user: User
    ) = LabelResponse.from(labelService.create(key, request, user))

    @PutMapping("/{id}")
    @PreAuthorize("@projectSecurity.canWrite(#key, authentication)")
    fun update(
        @PathVariable key: String,
        @PathVariable id: UUID,
        @Valid @RequestBody request: LabelRequest,
        @AuthenticationPrincipal user: User
    ) = LabelResponse.from(labelService.update(key, id, request, user))

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@projectSecurity.canWrite(#key, authentication)")
    fun delete(
        @PathVariable key: String,
        @PathVariable id: UUID,
        @AuthenticationPrincipal user: User
    ) = labelService.delete(key, id, user)
}
