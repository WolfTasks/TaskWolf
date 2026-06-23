package com.taskowolf.servicedesk.infrastructure

import com.taskowolf.servicedesk.domain.Incident
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface IncidentRepository : JpaRepository<Incident, UUID> {
    fun findByIssueId(issueId: UUID): List<Incident>
}
