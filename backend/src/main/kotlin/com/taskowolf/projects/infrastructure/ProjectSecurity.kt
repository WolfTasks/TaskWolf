package com.taskowolf.projects.infrastructure

import com.taskowolf.auth.domain.User
import com.taskowolf.projects.application.ProjectService
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component

@Component("projectSecurity")
class ProjectSecurity(private val projectService: ProjectService) {
    fun isProjectAdmin(key: String, authentication: Authentication): Boolean {
        val user = authentication.principal as? User ?: return false
        return try { projectService.isProjectAdmin(key, user.id) } catch (_: Exception) { false }
    }

    fun canWrite(key: String, authentication: Authentication): Boolean {
        val user = authentication.principal as? User ?: return false
        return try { projectService.canWrite(key, user.id) } catch (_: Exception) { false }
    }
}
