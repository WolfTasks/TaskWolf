package com.taskowolf.organizations.api.dto

import com.taskowolf.organizations.domain.Organization
import com.taskowolf.organizations.domain.OrganizationMember
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
    val orgId: UUID,
    val userId: UUID,
    val role: OrgRole
) {
    companion object {
        fun from(m: OrganizationMember) = OrganizationMemberResponse(
            orgId = m.id.orgId,
            userId = m.id.userId,
            role = m.role
        )
    }
}

data class AddMemberRequest(val userId: UUID, val role: OrgRole = OrgRole.MEMBER)

data class SwitchOrgResponse(val accessToken: String)
