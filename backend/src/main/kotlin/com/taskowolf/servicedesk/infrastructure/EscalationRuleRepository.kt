package com.taskowolf.servicedesk.infrastructure

import com.taskowolf.servicedesk.domain.EscalationRule
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface EscalationRuleRepository : JpaRepository<EscalationRule, UUID> {
    fun findBySlaPolicyId(slaPolicyId: UUID): List<EscalationRule>
}
