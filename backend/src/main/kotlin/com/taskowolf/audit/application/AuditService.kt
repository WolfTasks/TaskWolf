package com.taskowolf.audit.application

import com.taskowolf.audit.domain.AuditAction
import com.taskowolf.audit.domain.AuditEvent
import com.taskowolf.audit.domain.AuditLevel
import com.taskowolf.audit.infrastructure.AuditConfigRepository
import com.taskowolf.audit.infrastructure.AuditEventRepository
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class AuditService(
    private val eventRepo: AuditEventRepository,
    private val configRepo: AuditConfigRepository
) {
    private fun isEnabled(level: AuditLevel): Boolean {
        if (level == AuditLevel.SECURITY) return true
        return configRepo.findAll().find { it.level == level }?.enabled ?: false
    }

    @Async
    @Transactional
    fun log(
        level: AuditLevel, action: AuditAction, userEmail: String,
        userId: UUID? = null, projectId: UUID? = null,
        resourceType: String? = null, resourceId: String? = null,
        details: String? = null, ipAddress: String? = null, userAgent: String? = null
    ) {
        if (!isEnabled(level)) return
        eventRepo.save(AuditEvent(
            userEmail = userEmail, userId = userId, projectId = projectId,
            action = action, level = level, resourceType = resourceType,
            resourceId = resourceId, details = details,
            ipAddress = ipAddress, userAgent = userAgent
        ))
    }

    @Transactional(readOnly = true)
    fun getConfig() = configRepo.findAll().associate { it.level to it.enabled }

    @Transactional
    fun updateConfig(level: AuditLevel, enabled: Boolean) {
        val config = configRepo.findById(level).orElseThrow()
        config.enabled = enabled
    }
}
