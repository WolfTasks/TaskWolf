package com.taskowolf.projects.api.dto

import com.taskowolf.projects.domain.ProjectRole
import jakarta.validation.constraints.NotNull
import java.util.UUID

data class AddProjectMemberRequest(
    @field:NotNull val userId: UUID,
    @field:NotNull val role: ProjectRole
)
