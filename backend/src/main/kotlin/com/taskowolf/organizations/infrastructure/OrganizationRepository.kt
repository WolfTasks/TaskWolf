package com.taskowolf.organizations.infrastructure

import com.taskowolf.organizations.domain.Organization
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OrganizationRepository : JpaRepository<Organization, UUID> {
    fun findBySlug(slug: String): Organization?
}
