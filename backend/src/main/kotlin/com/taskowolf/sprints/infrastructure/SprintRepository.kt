package com.taskowolf.sprints.infrastructure

import com.taskowolf.sprints.domain.Sprint
import com.taskowolf.sprints.domain.SprintStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SprintRepository : JpaRepository<Sprint, UUID> {
    fun findByProjectId(projectId: UUID): List<Sprint>
    fun findByProjectIdAndStatus(projectId: UUID, status: SprintStatus): List<Sprint>
    fun existsByProjectIdAndStatus(projectId: UUID, status: SprintStatus): Boolean
}
