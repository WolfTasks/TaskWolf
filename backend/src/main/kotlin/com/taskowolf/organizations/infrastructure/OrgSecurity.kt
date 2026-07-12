package com.taskowolf.organizations.infrastructure

import com.taskowolf.auth.domain.User
import com.taskowolf.organizations.application.OrganizationService
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component
import java.util.UUID

@Component("orgSecurity")
class OrgSecurity(private val orgService: OrganizationService) {
    fun isOrgAdmin(orgId: UUID, authentication: Authentication): Boolean {
        val user = authentication.principal as? User ?: return false
        return try { orgService.isOrgAdmin(orgId, user) } catch (_: Exception) { false }
    }
}
