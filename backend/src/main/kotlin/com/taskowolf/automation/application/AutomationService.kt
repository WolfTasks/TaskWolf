package com.taskowolf.automation.application

import com.taskowolf.automation.domain.*
import com.taskowolf.automation.infrastructure.AutomationRuleRepository
import com.taskowolf.automation.infrastructure.RuleConditionGroupRepository
import com.taskowolf.core.infrastructure.NotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class CreateRuleRequest(
    val name: String,
    val triggerType: TriggerType,
    val triggerPayload: String?,
    val rootGroupLogic: GroupLogic,
    val scope: RuleScope = RuleScope.PROJECT
)

@Service
class AutomationService(
    private val ruleRepository: AutomationRuleRepository,
    private val groupRepository: RuleConditionGroupRepository
) {
    @Transactional
    fun create(request: CreateRuleRequest, projectId: UUID?, createdBy: UUID): AutomationRule {
        val rule = ruleRepository.save(
            AutomationRule(
                projectId = projectId,
                scope = request.scope,
                name = request.name,
                triggerType = request.triggerType,
                triggerPayload = request.triggerPayload,
                createdBy = createdBy
            )
        )
        groupRepository.save(RuleConditionGroup(rule = rule, logic = request.rootGroupLogic))
        return rule
    }

    @Transactional
    fun rename(ruleId: UUID, name: String): AutomationRule {
        val rule = find(ruleId)
        rule.name = name
        return ruleRepository.save(rule)
    }

    @Transactional
    fun toggle(ruleId: UUID): AutomationRule {
        val rule = find(ruleId)
        rule.enabled = !rule.enabled
        return ruleRepository.save(rule)
    }

    @Transactional
    fun delete(ruleId: UUID) {
        if (!ruleRepository.existsById(ruleId)) throw NotFoundException("Rule not found: $ruleId")
        ruleRepository.deleteById(ruleId)
    }

    @Transactional(readOnly = true)
    fun find(ruleId: UUID): AutomationRule =
        ruleRepository.findById(ruleId).orElseThrow { NotFoundException("Rule not found: $ruleId") }

    @Transactional(readOnly = true)
    fun listByProject(projectId: UUID, pageable: Pageable): Page<AutomationRule> =
        ruleRepository.findByProjectId(projectId, pageable)

    @Transactional(readOnly = true)
    fun listSystem(pageable: Pageable): Page<AutomationRule> =
        ruleRepository.findSystemRules(pageable)
}
