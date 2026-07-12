package com.taskowolf.organizations.api.dto

import com.taskowolf.auth.api.dto.UserResponse
import com.taskowolf.organizations.application.OrgMemberView
import com.taskowolf.organizations.domain.Organization
import com.taskowolf.organizations.domain.OrgRole
import java.util.UUID

data class OrganizationResponse(
    val id: UUID,
    val name: String,
    val slug: String
) {
    companion object {
        fun from(org: Organization) = OrganizationResponse(
            id = org.id,
            name = org.name,
            slug = org.slug
        )
    }
}

data class OrganizationMemberResponse(
    val user: UserResponse,
    val role: OrgRole
) {
    companion object {
        fun from(view: OrgMemberView) = OrganizationMemberResponse(UserResponse.from(view.user), view.role)
    }
}

data class AddMemberRequest(val userId: UUID, val role: OrgRole = OrgRole.MEMBER)

data class SwitchOrgResponse(val accessToken: String)
