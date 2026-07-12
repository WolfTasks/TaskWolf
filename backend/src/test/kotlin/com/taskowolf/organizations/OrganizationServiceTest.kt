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
    private val userRepository = mockk<com.taskowolf.auth.infrastructure.UserRepository>()
    private val service = OrganizationService(orgRepo, memberRepo, userRepository)

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
    fun `addMember returns a view with the correct role`() {
        val orgId = UUID.randomUUID()
        val user = com.taskowolf.auth.domain.User(email = "a@test.com", displayName = "A")
        every { memberRepo.findById(OrganizationMemberId(orgId, user.id)) } returns java.util.Optional.empty()
        every { userRepository.findById(user.id) } returns java.util.Optional.of(user)
        every { memberRepo.save(any()) } returnsArgument 0
        val result = service.addMember(orgId, user.id, OrgRole.ADMIN)
        assertEquals(OrgRole.ADMIN, result.role)
        assertEquals(user.id, result.user.id)
    }

    @Test
    fun `removeMember deletes a normal member`() {
        val orgId = UUID.randomUUID()
        val actor = com.taskowolf.auth.domain.User(email = "act@test.com", displayName = "A")
            .apply { systemRole = com.taskowolf.auth.domain.SystemRole.ADMIN }
        val targetId = UUID.randomUUID()
        every { memberRepo.findById(OrganizationMemberId(orgId, targetId)) } returns
            java.util.Optional.of(OrganizationMember(OrganizationMemberId(orgId, targetId), OrgRole.MEMBER))
        every { memberRepo.deleteById(OrganizationMemberId(orgId, targetId)) } just Runs
        service.removeMember(orgId, actor, targetId)
        verify { memberRepo.deleteById(OrganizationMemberId(orgId, targetId)) }
    }

    @Test
    fun `removeMember forbids removing the last owner`() {
        val orgId = UUID.randomUUID()
        val actor = com.taskowolf.auth.domain.User(email = "act2@test.com", displayName = "A")
            .apply { systemRole = com.taskowolf.auth.domain.SystemRole.ADMIN }
        val targetId = UUID.randomUUID()
        every { memberRepo.findById(OrganizationMemberId(orgId, targetId)) } returns
            java.util.Optional.of(OrganizationMember(OrganizationMemberId(orgId, targetId), OrgRole.OWNER))
        every { memberRepo.findByIdOrgId(orgId) } returns
            listOf(OrganizationMember(OrganizationMemberId(orgId, targetId), OrgRole.OWNER))
        org.junit.jupiter.api.assertThrows<com.taskowolf.core.infrastructure.ForbiddenException> {
            service.removeMember(orgId, actor, targetId)
        }
    }

    // --- requireMembershipOrAdmin ---

    @Test
    fun `requireMembershipOrAdmin permits SYSTEM_ADMIN regardless of membership`() {
        val orgId = UUID.randomUUID()
        val user = mockk<com.taskowolf.auth.domain.User>(relaxed = true)
        every { user.systemRole } returns com.taskowolf.auth.domain.SystemRole.ADMIN
        // must not throw
        service.requireMembershipOrAdmin(orgId, user)
    }

    @Test
    fun `requireMembershipOrAdmin permits org member`() {
        val orgId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val user = mockk<com.taskowolf.auth.domain.User>(relaxed = true)
        every { user.systemRole } returns com.taskowolf.auth.domain.SystemRole.MEMBER
        every { user.id } returns userId
        val member = OrganizationMember(OrganizationMemberId(orgId, userId), OrgRole.MEMBER)
        every { memberRepo.findByIdOrgId(orgId) } returns listOf(member)
        // must not throw
        service.requireMembershipOrAdmin(orgId, user)
    }

    @Test
    fun `requireMembershipOrAdmin denies non-member SYSTEM_MEMBER`() {
        val orgId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val user = mockk<com.taskowolf.auth.domain.User>(relaxed = true)
        every { user.systemRole } returns com.taskowolf.auth.domain.SystemRole.MEMBER
        every { user.id } returns userId
        every { memberRepo.findByIdOrgId(orgId) } returns emptyList()
        org.junit.jupiter.api.assertThrows<org.springframework.security.access.AccessDeniedException> {
            service.requireMembershipOrAdmin(orgId, user)
        }
    }

    // --- changeMemberRole ---

    @Test
    fun `changeMemberRole forbids changing your own role`() {
        val orgId = UUID.randomUUID()
        val actor = com.taskowolf.auth.domain.User(email = "self@test.com", displayName = "S")
        org.junit.jupiter.api.assertThrows<com.taskowolf.core.infrastructure.ForbiddenException> {
            service.changeMemberRole(orgId, actor, actor.id, OrgRole.ADMIN)
        }
    }

    @Test
    fun `changeMemberRole forbids demoting the last owner`() {
        val orgId = UUID.randomUUID()
        val actor = com.taskowolf.auth.domain.User(email = "sys@test.com", displayName = "Sys")
            .apply { systemRole = com.taskowolf.auth.domain.SystemRole.ADMIN }
        val target = com.taskowolf.auth.domain.User(email = "owner@test.com", displayName = "O")
        every { memberRepo.findById(OrganizationMemberId(orgId, target.id)) } returns
            java.util.Optional.of(OrganizationMember(OrganizationMemberId(orgId, target.id), OrgRole.OWNER))
        every { memberRepo.findByIdOrgId(orgId) } returns
            listOf(OrganizationMember(OrganizationMemberId(orgId, target.id), OrgRole.OWNER))
        org.junit.jupiter.api.assertThrows<com.taskowolf.core.infrastructure.ForbiddenException> {
            service.changeMemberRole(orgId, actor, target.id, OrgRole.MEMBER)
        }
    }

    @Test
    fun `changeMemberRole updates a normal member`() {
        val orgId = UUID.randomUUID()
        val actor = com.taskowolf.auth.domain.User(email = "sys2@test.com", displayName = "Sys")
            .apply { systemRole = com.taskowolf.auth.domain.SystemRole.ADMIN }
        val target = com.taskowolf.auth.domain.User(email = "m@test.com", displayName = "M")
        every { memberRepo.findById(OrganizationMemberId(orgId, target.id)) } returns
            java.util.Optional.of(OrganizationMember(OrganizationMemberId(orgId, target.id), OrgRole.MEMBER))
        every { userRepository.findById(target.id) } returns java.util.Optional.of(target)
        every { memberRepo.save(any()) } returnsArgument 0
        val result = service.changeMemberRole(orgId, actor, target.id, OrgRole.ADMIN)
        assertEquals(OrgRole.ADMIN, result.role)
    }
}
