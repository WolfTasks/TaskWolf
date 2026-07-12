package com.taskowolf.organizations.application

import com.taskowolf.organizations.domain.OrgRole
import com.taskowolf.organizations.domain.OrganizationMemberId
import com.taskowolf.organizations.infrastructure.OrganizationMemberRepository
import org.springframework.stereotype.Component
import java.util.UUID

interface OrgMembershipLookup {
    /** Die Rolle des Users in der Org, oder null wenn kein Mitglied. */
    fun roleOf(orgId: UUID, userId: UUID): OrgRole?

    /** Alle Org-Ids, in denen der User Mitglied ist. */
    fun orgIdsForUser(userId: UUID): List<UUID>

    /** True if the user is OWNER or ADMIN of at least one organization. */
    fun isOrgAdminOfAny(userId: UUID): Boolean
}

@Component
class OrgMembershipLookupImpl(
    private val memberRepo: OrganizationMemberRepository
) : OrgMembershipLookup {

    override fun roleOf(orgId: UUID, userId: UUID): OrgRole? =
        memberRepo.findById(OrganizationMemberId(orgId, userId)).map { it.role }.orElse(null)

    override fun orgIdsForUser(userId: UUID): List<UUID> =
        memberRepo.findByIdUserId(userId).map { it.id.orgId }

    override fun isOrgAdminOfAny(userId: UUID): Boolean =
        memberRepo.findByIdUserId(userId).any { it.role == OrgRole.OWNER || it.role == OrgRole.ADMIN }
}
