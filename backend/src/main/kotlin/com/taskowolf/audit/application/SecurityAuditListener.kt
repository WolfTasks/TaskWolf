package com.taskowolf.audit.application

import com.taskowolf.audit.domain.AuditAction
import com.taskowolf.audit.domain.AuditLevel
import org.springframework.stereotype.Component

@Component
class SecurityAuditListener(private val auditService: AuditService) {
    fun onLoginSuccess(email: String, ip: String?) =
        auditService.log(AuditLevel.SECURITY, AuditAction.LOGIN_SUCCESS, email, ipAddress = ip)

    fun onLoginFailed(email: String, ip: String?) =
        auditService.log(AuditLevel.SECURITY, AuditAction.LOGIN_FAILED, email, ipAddress = ip)

    fun onLogout(email: String) =
        auditService.log(AuditLevel.SECURITY, AuditAction.LOGOUT, email)

    fun onRegister(email: String) =
        auditService.log(AuditLevel.SECURITY, AuditAction.USER_REGISTERED, email)

    fun onOAuthLogin(email: String) =
        auditService.log(AuditLevel.SECURITY, AuditAction.OAUTH_LOGIN, email)
}
