package com.taskowolf.audit.infrastructure

import com.taskowolf.audit.domain.AuditEvent
import com.taskowolf.audit.domain.AuditLevel
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface AuditEventRepository : JpaRepository<AuditEvent, UUID> {
    @Query("""SELECT e FROM AuditEvent e WHERE
        (:from IS NULL OR e.timestamp >= :from) AND
        (:to IS NULL OR e.timestamp <= :to) AND
        (:userId IS NULL OR e.userId = :userId) AND
        (:action IS NULL OR CAST(e.action AS String) = :action) AND
        (:level IS NULL OR CAST(e.level AS String) = :level)
        ORDER BY e.timestamp DESC""")
    fun findFiltered(from: Instant?, to: Instant?, userId: UUID?, action: String?, level: String?, pageable: Pageable): Page<AuditEvent>

    @Query("""SELECT e FROM AuditEvent e WHERE e.projectId = :projectId AND
        (:from IS NULL OR e.timestamp >= :from) AND
        (:to IS NULL OR e.timestamp <= :to) AND
        (:action IS NULL OR CAST(e.action AS String) = :action)
        ORDER BY e.timestamp DESC""")
    fun findByProject(projectId: UUID, from: Instant?, to: Instant?, action: String?, pageable: Pageable): Page<AuditEvent>
}
