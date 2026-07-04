package com.taskowolf.audit.infrastructure

import com.taskowolf.audit.domain.AuditEvent
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface AuditEventRepository : JpaRepository<AuditEvent, UUID> {
    // The `CAST(:param AS ...)` on each null-check is required for PostgreSQL: an
    // untyped `:param IS NULL` bind produces "42P18: could not determine data type
    // of parameter". Casting gives the driver an explicit type. (H2 tolerates the
    // untyped form, which is why AuditEventRepositoryTest never caught this.)
    @Query("""SELECT e FROM AuditEvent e WHERE
        (CAST(:from AS timestamp) IS NULL OR e.timestamp >= :from) AND
        (CAST(:to AS timestamp) IS NULL OR e.timestamp <= :to) AND
        (CAST(:userId AS uuid) IS NULL OR e.userId = :userId) AND
        (CAST(:action AS string) IS NULL OR CAST(e.action AS String) = :action) AND
        (CAST(:level AS string) IS NULL OR CAST(e.level AS String) = :level)
        ORDER BY e.timestamp DESC""")
    fun findFiltered(from: Instant?, to: Instant?, userId: UUID?, action: String?, level: String?, pageable: Pageable): Page<AuditEvent>

    @Query("""SELECT e FROM AuditEvent e WHERE e.projectId = :projectId AND
        (CAST(:from AS timestamp) IS NULL OR e.timestamp >= :from) AND
        (CAST(:to AS timestamp) IS NULL OR e.timestamp <= :to) AND
        (CAST(:action AS string) IS NULL OR CAST(e.action AS String) = :action)
        ORDER BY e.timestamp DESC""")
    fun findByProject(projectId: UUID, from: Instant?, to: Instant?, action: String?, pageable: Pageable): Page<AuditEvent>
}
