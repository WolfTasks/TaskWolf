package com.taskowolf.servicedesk.infrastructure

import com.taskowolf.servicedesk.domain.Incident
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface IncidentRepository : JpaRepository<Incident, UUID> {
    fun findByIssueId(issueId: UUID): List<Incident>

    @Query(
        value = "SELECT inc.* FROM incidents inc JOIN issues iss ON iss.id = inc.issue_id WHERE iss.project_id = :projectId",
        nativeQuery = true
    )
    fun findByProjectId(projectId: UUID): List<Incident>
}
