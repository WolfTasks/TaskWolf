package com.taskowolf.organizations.application

import com.taskowolf.organizations.domain.*
import com.taskowolf.organizations.infrastructure.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class OrganizationService(
    private val orgRepo: OrganizationRepository,
    private val memberRepo: OrganizationMemberRepository
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
    fun listMembers(orgId: UUID) = memberRepo.findByIdOrgId(orgId)

    @Transactional
    fun addMember(orgId: UUID, userId: UUID, role: OrgRole) =
        memberRepo.save(OrganizationMember(OrganizationMemberId(orgId, userId), role))

    @Transactional
    fun removeMember(orgId: UUID, userId: UUID) =
        memberRepo.deleteById(OrganizationMemberId(orgId, userId))

    @Transactional(readOnly = true)
    fun listOrgsForUser(userId: UUID) = memberRepo.findByIdUserId(userId).map { it.id.orgId }
        .let { ids -> if (ids.isEmpty()) emptyList() else orgRepo.findAllById(ids) }
}
