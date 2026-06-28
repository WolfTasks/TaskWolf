package com.taskowolf.customfields.api

import com.taskowolf.auth.domain.User
import com.taskowolf.customfields.api.dto.CustomFieldDefinitionRequest
import com.taskowolf.customfields.api.dto.CustomFieldDefinitionResponse
import com.taskowolf.customfields.api.dto.CustomFieldOptionRequest
import com.taskowolf.customfields.api.dto.CustomFieldOptionResponse
import com.taskowolf.customfields.application.CustomFieldService
import com.taskowolf.customfields.application.ReorderEntry
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/projects/{key}/custom-fields")
class CustomFieldController(private val service: CustomFieldService) {

    @GetMapping
    fun list(@PathVariable key: String, @AuthenticationPrincipal user: User) =
        service.list(key, user.id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @PathVariable key: String,
        @Valid @RequestBody request: CustomFieldDefinitionRequest,
        @AuthenticationPrincipal user: User
    ): CustomFieldDefinitionResponse {
        val field = service.create(key, request, user)
        return CustomFieldDefinitionResponse.from(field)
    }

    @PutMapping("/{id}")
    fun update(
        @PathVariable key: String,
        @PathVariable id: UUID,
        @Valid @RequestBody request: CustomFieldDefinitionRequest,
        @AuthenticationPrincipal user: User
    ): CustomFieldDefinitionResponse {
        val field = service.update(key, id, request, user)
        return CustomFieldDefinitionResponse.from(field)
    }

    @PutMapping("/reorder")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun reorder(
        @PathVariable key: String,
        @RequestBody reorders: List<ReorderEntry>,
        @AuthenticationPrincipal user: User
    ) = service.reorder(key, reorders, user)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable key: String,
        @PathVariable id: UUID,
        @AuthenticationPrincipal user: User
    ) = service.delete(key, id, user)

    @PostMapping("/{id}/options")
    @ResponseStatus(HttpStatus.CREATED)
    fun createOption(
        @PathVariable key: String,
        @PathVariable id: UUID,
        @Valid @RequestBody request: CustomFieldOptionRequest,
        @AuthenticationPrincipal user: User
    ) = CustomFieldOptionResponse.from(service.createOption(key, id, request, user))

    @PutMapping("/{id}/options/{optId}")
    fun updateOption(
        @PathVariable key: String,
        @PathVariable id: UUID,
        @PathVariable optId: UUID,
        @Valid @RequestBody request: CustomFieldOptionRequest,
        @AuthenticationPrincipal user: User
    ) = CustomFieldOptionResponse.from(service.updateOption(key, id, optId, request, user))

    @DeleteMapping("/{id}/options/{optId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteOption(
        @PathVariable key: String,
        @PathVariable id: UUID,
        @PathVariable optId: UUID,
        @AuthenticationPrincipal user: User
    ) = service.deleteOption(key, id, optId, user)
}
