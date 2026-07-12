package com.taskowolf.organizations.api

import com.taskowolf.auth.domain.User
import com.taskowolf.organizations.api.dto.*
import com.taskowolf.organizations.application.OrganizationService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
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
    @PreAuthorize("hasRole('ADMIN')")
    fun create(
        @Valid @RequestBody req: CreateOrganizationRequest,
        @AuthenticationPrincipal user: User
    ) = OrganizationResponse.from(orgService.create(req.name, req.slug, user.id))

    @GetMapping("/mine")
    fun listMine(@AuthenticationPrincipal user: User) =
        orgService.listOrgsForUser(user.id).map { OrganizationResponse.from(it) }

    @GetMapping("/{id}")
    fun getById(
        @PathVariable id: UUID,
        @AuthenticationPrincipal user: User
    ): OrganizationResponse {
        orgService.requireMembershipOrAdmin(id, user)
        return OrganizationResponse.from(orgService.findById(id))
    }

    @GetMapping("/{id}/members")
    @Transactional(readOnly = true)
    fun listMembers(
        @PathVariable id: UUID,
        @AuthenticationPrincipal user: User
    ): List<OrganizationMemberResponse> {
        orgService.requireMembershipOrAdmin(id, user)
        return orgService.listMembersWithUsers(id).map { OrganizationMemberResponse.from(it) }
    }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@orgSecurity.isOrgAdmin(#id, authentication)")
    fun addMember(
        @PathVariable id: UUID,
        @RequestBody req: AddMemberRequest,
        @AuthenticationPrincipal actor: User
    ) = OrganizationMemberResponse.from(orgService.addMember(id, actor, req.userId, req.role))

    @DeleteMapping("/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@orgSecurity.isOrgAdmin(#id, authentication)")
    fun removeMember(
        @PathVariable id: UUID,
        @PathVariable userId: UUID,
        @AuthenticationPrincipal actor: User
    ) {
        orgService.removeMember(id, actor, userId)
    }

    @PatchMapping("/{id}/members/{userId}")
    @PreAuthorize("@orgSecurity.isOrgAdmin(#id, authentication)")
    @Transactional
    fun changeMemberRole(
        @PathVariable id: UUID,
        @PathVariable userId: UUID,
        @Valid @RequestBody req: UpdateOrgMemberRoleRequest,
        @AuthenticationPrincipal actor: User
    ) = OrganizationMemberResponse.from(orgService.changeMemberRole(id, actor, userId, req.role))
}
