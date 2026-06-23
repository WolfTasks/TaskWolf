package com.taskowolf.audit

import com.taskowolf.audit.domain.AuditAction
import com.taskowolf.audit.domain.AuditEvent
import com.taskowolf.audit.domain.AuditLevel
import com.taskowolf.audit.infrastructure.AuditEventRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.domain.Pageable

@DataJpaTest
class AuditEventRepositoryTest {
    @Autowired lateinit var repo: AuditEventRepository

    @Test
    fun `findFiltered returns matching events`() {
        repo.save(AuditEvent(userEmail = "a@b.com", action = AuditAction.LOGIN_SUCCESS, level = AuditLevel.SECURITY))
        repo.save(AuditEvent(userEmail = "a@b.com", action = AuditAction.ISSUE_CREATED, level = AuditLevel.WRITE))
        val result = repo.findFiltered(null, null, null, "LOGIN_SUCCESS", null, Pageable.ofSize(10))
        assertEquals(1, result.totalElements)
    }
}
