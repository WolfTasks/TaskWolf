package com.taskowolf.projects.infrastructure

import com.taskowolf.projects.domain.ProjectMember
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ProjectMemberRepository : JpaRepository<ProjectMember, UUID> {
    fun findByProjectIdAndUserId(projectId: UUID, userId: UUID): ProjectMember?
    fun findAllByProjectId(projectId: UUID): List<ProjectMember>
    fun existsByProjectIdAndUserId(projectId: UUID, userId: UUID): Boolean
}
