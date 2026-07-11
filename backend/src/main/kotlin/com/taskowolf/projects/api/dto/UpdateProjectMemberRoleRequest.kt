package com.taskowolf.projects.api.dto

import com.taskowolf.projects.domain.ProjectRole
import jakarta.validation.constraints.NotNull

data class UpdateProjectMemberRoleRequest(
    @field:NotNull val role: ProjectRole
)
