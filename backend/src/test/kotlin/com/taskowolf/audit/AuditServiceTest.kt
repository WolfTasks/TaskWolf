package com.taskowolf.audit

import com.taskowolf.audit.application.AuditService
import com.taskowolf.audit.domain.AuditAction
import com.taskowolf.audit.domain.AuditConfig
import com.taskowolf.audit.domain.AuditEvent
import com.taskowolf.audit.domain.AuditLevel
import com.taskowolf.audit.infrastructure.AuditConfigRepository
import com.taskowolf.audit.infrastructure.AuditEventRepository
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AuditServiceTest {
    private val eventRepo = mockk<AuditEventRepository>()
    private val configRepo = mockk<AuditConfigRepository>()
    private val service = AuditService(eventRepo, configRepo)

    @Test
    fun `SECURITY events are always logged regardless of config`() {
        every { configRepo.findAll() } returns listOf(
            AuditConfig(AuditLevel.SECURITY, false)
        )
        every { eventRepo.save(any<AuditEvent>()) } returns mockk()
        service.log(AuditLevel.SECURITY, AuditAction.LOGIN_SUCCESS, "u@e.com")
        // Give async execution time to complete
        Thread.sleep(100)
        verify { eventRepo.save(any<AuditEvent>()) }
    }

    @Test
    fun `WRITE events are skipped when disabled`() {
        every { configRepo.findAll() } returns listOf(
            AuditConfig(AuditLevel.WRITE, false)
        )
        service.log(AuditLevel.WRITE, AuditAction.ISSUE_CREATED, "u@e.com")
        // Give async execution time to complete
        Thread.sleep(100)
        verify(exactly = 0) { eventRepo.save(any<AuditEvent>()) }
    }

    @Test
    fun `WRITE events are logged when enabled`() {
        every { configRepo.findAll() } returns listOf(
            AuditConfig(AuditLevel.WRITE, true)
        )
        every { eventRepo.save(any<AuditEvent>()) } returns mockk()
        service.log(AuditLevel.WRITE, AuditAction.ISSUE_CREATED, "u@e.com")
        // Give async execution time to complete
        Thread.sleep(100)
        verify { eventRepo.save(any<AuditEvent>()) }
    }
}
