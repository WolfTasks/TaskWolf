package com.taskowolf.organizations.api.dto

import com.taskowolf.organizations.domain.OrgRole
import jakarta.validation.constraints.NotNull

data class UpdateOrgMemberRoleRequest(@field:NotNull val role: OrgRole)
