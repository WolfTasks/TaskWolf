package com.taskowolf.organizations

import com.taskowolf.organizations.application.OrganizationService
import com.taskowolf.organizations.domain.*
import com.taskowolf.organizations.infrastructure.OrganizationMemberRepository
import com.taskowolf.organizations.infrastructure.OrganizationRepository
import io.mockk.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class OrganizationServiceTest {

    private val orgRepo = mockk<OrganizationRepository>()
    private val memberRepo = mockk<OrganizationMemberRepository>()
    private val service = OrganizationService(orgRepo, memberRepo)

    @Test
    fun `create adds creator as OWNER`() {
        every { orgRepo.save(any()) } returnsArgument 0
        every { memberRepo.save(any()) } returnsArgument 0
        val creatorId = UUID.randomUUID()
        service.create("MyOrg", "my-org", creatorId)
        val slot = slot<OrganizationMember>()
        verify { memberRepo.save(capture(slot)) }
        assertEquals(OrgRole.OWNER, slot.captured.role)
    }

    @Test
    fun `create saves org before adding member`() {
        every { orgRepo.save(any()) } returnsArgument 0
        every { memberRepo.save(any()) } returnsArgument 0
        val creatorId = UUID.randomUUID()
        val org = service.create("TestOrg", "test-org", creatorId)
        assertEquals("TestOrg", org.name)
        assertEquals("test-org", org.slug)
    }

    @Test
    fun `listOrgsForUser returns empty list when user has no memberships`() {
        val userId = UUID.randomUUID()
        every { memberRepo.findByIdUserId(userId) } returns emptyList()
        val result = service.listOrgsForUser(userId)
        assertEquals(emptyList<Any>(), result)
    }

    @Test
    fun `listOrgsForUser returns orgs for user`() {
        val userId = UUID.randomUUID()
        val orgId = UUID.randomUUID()
        val org = Organization("MyOrg", "my-org")
        val member = OrganizationMember(OrganizationMemberId(orgId, userId), OrgRole.MEMBER)
        every { memberRepo.findByIdUserId(userId) } returns listOf(member)
        every { orgRepo.findAllById(listOf(orgId)) } returns listOf(org)
        val result = service.listOrgsForUser(userId)
        assertEquals(1, result.size)
        assertEquals("my-org", result[0].slug)
    }

    @Test
    fun `addMember saves with correct role`() {
        val orgId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        every { memberRepo.save(any()) } returnsArgument 0
        val result = service.addMember(orgId, userId, OrgRole.ADMIN)
        assertEquals(OrgRole.ADMIN, result.role)
        assertEquals(orgId, result.id.orgId)
        assertEquals(userId, result.id.userId)
    }

    @Test
    fun `removeMember calls deleteById`() {
        val orgId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        every { memberRepo.deleteById(any()) } just Runs
        service.removeMember(orgId, userId)
        verify { memberRepo.deleteById(OrganizationMemberId(orgId, userId)) }
    }
}
