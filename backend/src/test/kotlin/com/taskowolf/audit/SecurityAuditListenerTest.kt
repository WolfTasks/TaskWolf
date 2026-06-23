package com.taskowolf.audit

import com.taskowolf.audit.application.AuditService
import com.taskowolf.audit.application.SecurityAuditListener
import com.taskowolf.audit.domain.AuditAction
import com.taskowolf.audit.domain.AuditLevel
import io.mockk.*
import org.junit.jupiter.api.Test

class SecurityAuditListenerTest {
    private val auditService = mockk<AuditService>(relaxed = true)
    private val listener = SecurityAuditListener(auditService)

    @Test
    fun `onLoginSuccess logs LOGIN_SUCCESS`() {
        listener.onLoginSuccess("user@example.com", "1.2.3.4")
        verify { auditService.log(AuditLevel.SECURITY, AuditAction.LOGIN_SUCCESS, "user@example.com", ipAddress = "1.2.3.4") }
    }

    @Test
    fun `onLoginFailed logs LOGIN_FAILED`() {
        listener.onLoginFailed("bad@example.com", "1.2.3.4")
        verify { auditService.log(AuditLevel.SECURITY, AuditAction.LOGIN_FAILED, "bad@example.com", ipAddress = "1.2.3.4") }
    }
}
