package com.taskowolf.projects.api.dto

import com.taskowolf.projects.domain.Project
import java.util.UUID

data class ProjectResponse(
    val id: UUID,
    val key: String,
    val name: String,
    val description: String?,
    val ownerId: UUID,
    val archived: Boolean
) {
    companion object {
        fun from(p: Project) = ProjectResponse(p.id, p.key, p.name, p.description, p.owner.id, p.archived)
    }
}
