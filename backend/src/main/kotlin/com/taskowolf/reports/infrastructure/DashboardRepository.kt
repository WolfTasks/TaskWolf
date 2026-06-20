package com.taskowolf.reports.infrastructure

import com.taskowolf.reports.domain.Dashboard
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DashboardRepository : JpaRepository<Dashboard, UUID> {
    fun findByProjectId(projectId: UUID): Dashboard?
}
