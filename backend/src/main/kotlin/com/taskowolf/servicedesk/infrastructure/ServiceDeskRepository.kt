package com.taskowolf.servicedesk.infrastructure

import com.taskowolf.servicedesk.domain.ServiceDesk
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ServiceDeskRepository : JpaRepository<ServiceDesk, UUID> {
    fun findByProjectId(projectId: UUID): ServiceDesk?
}
