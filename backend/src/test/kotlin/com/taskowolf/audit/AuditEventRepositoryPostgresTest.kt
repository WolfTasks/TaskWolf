package com.taskowolf.audit

import com.taskowolf.IntegrationTestBase
import com.taskowolf.audit.domain.AuditAction
import com.taskowolf.audit.domain.AuditEvent
import com.taskowolf.audit.domain.AuditLevel
import com.taskowolf.audit.infrastructure.AuditEventRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Pageable
import java.util.UUID

/**
 * Reproduces the PostgreSQL-only failure where `(:param IS NULL OR ...)` with an
 * unbound null parameter triggers "42P18: could not determine data type of parameter".
 * The existing [AuditEventRepositoryTest] runs on H2, which tolerates untyped null
 * parameters, so it cannot catch this. These tests must run against real Postgres.
 */
class AuditEventRepositoryPostgresTest : IntegrationTestBase() {

    @Autowired
    lateinit var repo: AuditEventRepository

    @Test
    fun `findFiltered with all-null filters does not fail on postgres`() {
        repo.save(AuditEvent(userEmail = "a@b.com", action = AuditAction.LOGIN_SUCCESS, level = AuditLevel.SECURITY))
        repo.save(AuditEvent(userEmail = "a@b.com", action = AuditAction.ISSUE_CREATED, level = AuditLevel.WRITE))

        val result = repo.findFiltered(null, null, null, null, null, Pageable.ofSize(50))

        assertTrue(result.totalElements >= 2) { "expected at least the two saved events, got ${result.totalElements}" }
    }

    @Test
    fun `findByProject with null optional filters does not fail on postgres`() {
        // No matching rows needed: the bug is the query failing to bind untyped null
        // parameters, which throws before any row is even examined.
        val result = repo.findByProject(UUID.randomUUID(), null, null, null, Pageable.ofSize(50))

        assertEquals(0, result.totalElements)
    }
}
