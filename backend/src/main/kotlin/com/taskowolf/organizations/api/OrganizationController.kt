package com.taskowolf.organizations.api

import com.taskowolf.auth.domain.User
import com.taskowolf.organizations.api.dto.*
import com.taskowolf.organizations.application.OrganizationService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/organizations")
class OrganizationController(
    private val orgService: OrganizationService
) {

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    fun listAll() = orgService.listAll().map { OrganizationResponse.from(it) }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody req: CreateOrganizationRequest,
        @AuthenticationPrincipal user: User
    ) = OrganizationResponse.from(orgService.create(req.name, req.slug, user.id))

    @GetMapping("/mine")
    fun listMine(@AuthenticationPrincipal user: User) =
        orgService.listOrgsForUser(user.id).map { OrganizationResponse.from(it) }

    @GetMapping("/{id}")
    fun getById(@PathVariable id: UUID) =
        OrganizationResponse.from(orgService.findById(id))

    @GetMapping("/{id}/members")
    fun listMembers(@PathVariable id: UUID) =
        orgService.listMembers(id).map { OrganizationMemberResponse.from(it) }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    fun addMember(
        @PathVariable id: UUID,
        @RequestBody req: AddMemberRequest
    ) = OrganizationMemberResponse.from(orgService.addMember(id, req.userId, req.role))

    @DeleteMapping("/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeMember(@PathVariable id: UUID, @PathVariable userId: UUID) {
        orgService.removeMember(id, userId)
    }
}
