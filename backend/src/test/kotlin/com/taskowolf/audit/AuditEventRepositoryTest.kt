package com.taskowolf.audit

import com.taskowolf.audit.domain.AuditAction
import com.taskowolf.audit.domain.AuditEvent
import com.taskowolf.audit.domain.AuditLevel
import com.taskowolf.audit.infrastructure.AuditEventRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.domain.Pageable
import org.springframework.test.context.TestPropertySource

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false;INIT=CREATE TYPE IF NOT EXISTS JSONB AS TEXT",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
])
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
