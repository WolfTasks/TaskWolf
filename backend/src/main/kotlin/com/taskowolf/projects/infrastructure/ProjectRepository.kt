package com.taskowolf.projects.infrastructure

import com.taskowolf.projects.domain.Project
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface ProjectRepository : JpaRepository<Project, UUID> {
    fun findByKey(key: String): Project?
    fun existsByKey(key: String): Boolean
    fun existsByOwnerId(ownerId: UUID): Boolean

    @Query("""
        SELECT DISTINCT p FROM Project p
        LEFT JOIN ProjectMember m ON m.project = p AND m.user.id = :userId
        WHERE p.owner.id = :userId OR m.user.id = :userId
    """)
    fun findAllByMemberOrOwner(userId: UUID): List<Project>

    fun findAllByOrgIdIn(orgIds: Collection<UUID>): List<Project>
}
