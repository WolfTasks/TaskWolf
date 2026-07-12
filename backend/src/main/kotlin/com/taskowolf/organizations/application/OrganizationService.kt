package com.taskowolf.organizations.application

import com.taskowolf.organizations.domain.*
import com.taskowolf.organizations.infrastructure.*
import com.taskowolf.auth.domain.SystemRole
import com.taskowolf.auth.domain.User
import com.taskowolf.auth.infrastructure.UserRepository
import com.taskowolf.core.infrastructure.ConflictException
import com.taskowolf.core.infrastructure.ForbiddenException
import com.taskowolf.core.infrastructure.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.security.access.AccessDeniedException
import java.util.UUID

@Service
class OrganizationService(
    private val orgRepo: OrganizationRepository,
    private val memberRepo: OrganizationMemberRepository,
    private val userRepository: UserRepository
) {
    @Transactional
    fun create(name: String, slug: String, creatorId: UUID): Organization {
        val org = orgRepo.save(Organization(name, slug))
        memberRepo.save(OrganizationMember(OrganizationMemberId(org.id, creatorId), OrgRole.OWNER))
        return org
    }

    @Transactional(readOnly = true)
    fun findBySlug(slug: String) = orgRepo.findBySlug(slug) ?: error("Org not found: $slug")

    @Transactional(readOnly = true)
    fun findById(id: UUID) = orgRepo.findById(id).orElseThrow { NoSuchElementException("Org not found: $id") }

    @Transactional(readOnly = true)
    fun listAll(): List<Organization> = orgRepo.findAll()

    @Transactional(readOnly = true)
    fun listMembersWithUsers(orgId: UUID): List<OrgMemberView> {
        val members = memberRepo.findByIdOrgId(orgId)
        val users = userRepository.findAllById(members.map { it.id.userId }).associateBy { it.id }
        return members.mapNotNull { m -> users[m.id.userId]?.let { OrgMemberView(it, m.role) } }
    }

    @Transactional
    fun addMember(orgId: UUID, userId: UUID, role: OrgRole): OrgMemberView {
        if (memberRepo.findById(OrganizationMemberId(orgId, userId)).isPresent)
            throw ConflictException("User is already a member of this organization")
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User not found") }
        memberRepo.save(OrganizationMember(OrganizationMemberId(orgId, userId), role))
        return OrgMemberView(user, role)
    }

    @Transactional
    fun removeMember(orgId: UUID, actor: User, targetUserId: UUID) {
        val member = memberRepo.findById(OrganizationMemberId(orgId, targetUserId))
            .orElseThrow { NotFoundException("Member not found") }
        val isSystemAdmin = actor.systemRole == SystemRole.ADMIN
        if (member.role == OrgRole.OWNER && !isSystemAdmin)
            throw ForbiddenException("Cannot remove an owner")
        if (member.role == OrgRole.OWNER && isLastOwner(orgId))
            throw ForbiddenException("Cannot remove the last owner")
        memberRepo.deleteById(OrganizationMemberId(orgId, targetUserId))
    }

    @Transactional(readOnly = true)
    fun listOrgsForUser(userId: UUID) = memberRepo.findByIdUserId(userId).map { it.id.orgId }
        .let { ids -> if (ids.isEmpty()) emptyList() else orgRepo.findAllById(ids) }

    @Transactional(readOnly = true)
    fun isOrgAdmin(orgId: UUID, user: User): Boolean {
        if (user.systemRole == SystemRole.ADMIN) return true
        val role = memberRepo.findById(OrganizationMemberId(orgId, user.id)).map { it.role }.orElse(null)
        return role == OrgRole.OWNER || role == OrgRole.ADMIN
    }

    fun requireMembershipOrAdmin(orgId: UUID, user: User) {
        if (user.systemRole == SystemRole.ADMIN) return
        val isMember = memberRepo.findByIdOrgId(orgId).any { it.id.userId == user.id }
        if (!isMember) throw AccessDeniedException("Not a member of organization $orgId")
    }

    @Transactional
    fun changeMemberRole(orgId: UUID, actor: User, targetUserId: UUID, newRole: OrgRole): OrgMemberView {
        if (actor.id == targetUserId) throw ForbiddenException("You cannot change your own role")
        val member = memberRepo.findById(OrganizationMemberId(orgId, targetUserId))
            .orElseThrow { NotFoundException("Member not found") }
        val isSystemAdmin = actor.systemRole == SystemRole.ADMIN
        if (member.role == OrgRole.OWNER && !isSystemAdmin)
            throw ForbiddenException("Cannot change an owner's role")
        if (member.role == OrgRole.OWNER && newRole != OrgRole.OWNER && isLastOwner(orgId))
            throw ForbiddenException("Cannot demote the last owner")
        member.role = newRole
        memberRepo.save(member)
        val user = userRepository.findById(targetUserId).orElseThrow { NotFoundException("User not found") }
        return OrgMemberView(user, newRole)
    }

    private fun isLastOwner(orgId: UUID): Boolean =
        memberRepo.findByIdOrgId(orgId).count { it.role == OrgRole.OWNER } <= 1
}
