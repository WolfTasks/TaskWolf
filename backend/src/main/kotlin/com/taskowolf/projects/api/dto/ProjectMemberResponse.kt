package com.taskowolf.projects.api.dto

import com.taskowolf.auth.api.dto.UserResponse
import com.taskowolf.projects.domain.ProjectMember
import com.taskowolf.projects.domain.ProjectRole

data class ProjectMemberResponse(val user: UserResponse, val role: ProjectRole) {
    companion object {
        fun from(m: ProjectMember) = ProjectMemberResponse(UserResponse.from(m.user), m.role)
    }
}
