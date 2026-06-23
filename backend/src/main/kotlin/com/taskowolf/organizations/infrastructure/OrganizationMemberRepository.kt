package com.taskowolf.organizations.infrastructure

import com.taskowolf.organizations.domain.OrganizationMember
import com.taskowolf.organizations.domain.OrganizationMemberId
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OrganizationMemberRepository : JpaRepository<OrganizationMember, OrganizationMemberId> {
    fun findByIdOrgId(orgId: UUID): List<OrganizationMember>
    fun findByIdUserId(userId: UUID): List<OrganizationMember>
}
