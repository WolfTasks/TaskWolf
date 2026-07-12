package com.taskowolf.organizations

import com.taskowolf.organizations.application.OrgMembershipLookupImpl
import com.taskowolf.organizations.domain.OrgRole
import com.taskowolf.organizations.domain.OrganizationMember
import com.taskowolf.organizations.domain.OrganizationMemberId
import com.taskowolf.organizations.infrastructure.OrganizationMemberRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.UUID

class OrgMembershipLookupTest {
    private val memberRepo = mockk<OrganizationMemberRepository>()
    private val lookup = OrgMembershipLookupImpl(memberRepo)

    @Test
    fun `roleOf returns the member role`() {
        val orgId = UUID.randomUUID(); val userId = UUID.randomUUID()
        every { memberRepo.findById(OrganizationMemberId(orgId, userId)) } returns
            Optional.of(OrganizationMember(OrganizationMemberId(orgId, userId), OrgRole.ADMIN))
        assertEquals(OrgRole.ADMIN, lookup.roleOf(orgId, userId))
    }

    @Test
    fun `roleOf returns null when not a member`() {
        val orgId = UUID.randomUUID(); val userId = UUID.randomUUID()
        every { memberRepo.findById(OrganizationMemberId(orgId, userId)) } returns Optional.empty()
        assertNull(lookup.roleOf(orgId, userId))
    }

    @Test
    fun `orgIdsForUser maps memberships to org ids`() {
        val userId = UUID.randomUUID(); val o1 = UUID.randomUUID(); val o2 = UUID.randomUUID()
        every { memberRepo.findByIdUserId(userId) } returns listOf(
            OrganizationMember(OrganizationMemberId(o1, userId), OrgRole.MEMBER),
            OrganizationMember(OrganizationMemberId(o2, userId), OrgRole.OWNER),
        )
        assertEquals(listOf(o1, o2), lookup.orgIdsForUser(userId))
    }
}
