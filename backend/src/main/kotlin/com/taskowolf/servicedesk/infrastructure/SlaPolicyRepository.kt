package com.taskowolf.servicedesk.infrastructure

import com.taskowolf.servicedesk.domain.SlaPolicy
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SlaPolicyRepository : JpaRepository<SlaPolicy, UUID> {
    fun findByServiceDeskId(serviceDeskId: UUID): List<SlaPolicy>
}
