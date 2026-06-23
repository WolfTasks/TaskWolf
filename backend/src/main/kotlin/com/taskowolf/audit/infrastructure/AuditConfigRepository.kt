package com.taskowolf.audit.infrastructure

import com.taskowolf.audit.domain.AuditConfig
import com.taskowolf.audit.domain.AuditLevel
import org.springframework.data.jpa.repository.JpaRepository

interface AuditConfigRepository : JpaRepository<AuditConfig, AuditLevel>
