package com.taskowolf.automation.infrastructure

import com.taskowolf.automation.domain.AutomationRule
import com.taskowolf.automation.domain.TriggerType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface AutomationRuleRepository : JpaRepository<AutomationRule, UUID> {
    @Query("""
        SELECT r FROM AutomationRule r
        WHERE r.triggerType = :triggerType AND r.enabled = true
          AND (r.scope = 'SYSTEM' OR r.projectId = :projectId)
    """)
    fun findActiveByTriggerTypeAndProject(triggerType: TriggerType, projectId: UUID): List<AutomationRule>

    fun findByProjectId(projectId: UUID, pageable: Pageable): Page<AutomationRule>

    @Query("SELECT r FROM AutomationRule r WHERE r.scope = 'SYSTEM'")
    fun findSystemRules(pageable: Pageable): Page<AutomationRule>
}
