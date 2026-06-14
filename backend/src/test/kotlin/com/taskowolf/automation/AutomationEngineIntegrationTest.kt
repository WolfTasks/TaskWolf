package com.taskowolf.automation

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.taskowolf.automation.application.*
import com.taskowolf.automation.domain.*
import com.taskowolf.automation.domain.events.AutomationFiredEvent
import com.taskowolf.automation.infrastructure.AutomationRuleRepository
import com.taskowolf.automation.infrastructure.RuleConditionGroupRepository
import com.taskowolf.core.application.DomainEventPublisher
import com.taskowolf.issues.domain.Issue
import com.taskowolf.issues.domain.IssuePriority
import com.taskowolf.issues.domain.events.IssueFieldChangedEvent
import io.mockk.*
import org.junit.jupiter.api.Test
import java.util.UUID

class AutomationEngineIntegrationTest {
    private val ruleRepo = mockk<AutomationRuleRepository>()
    private val groupRepo = mockk<RuleConditionGroupRepository>()
    private val evaluator = ConditionEvaluator(jacksonObjectMapper())
    private val actionExecutor = mockk<ActionExecutor>(relaxed = true)
    private val eventPublisher = mockk<DomainEventPublisher>(relaxed = true)

    private val engine = AutomationEngine(ruleRepo, groupRepo, evaluator, actionExecutor, eventPublisher, jacksonObjectMapper())

    @Test
    fun `rule fires when conditions match`() {
        val projectId = UUID.randomUUID()
        val project = mockk<com.taskowolf.projects.domain.Project> { every { id } returns projectId; every { key } returns "T" }
        val issue = mockk<Issue> {
            every { this@mockk.project } returns project
            every { priority } returns IssuePriority.CRITICAL
            every { type } returns com.taskowolf.issues.domain.IssueType.BUG
            every { assignee } returns null
            every { status } returns mockk { every { id } returns UUID.randomUUID() }
        }

        val rule = mockk<AutomationRule> {
            every { id } returns UUID.randomUUID()
            every { triggerPayload } returns null
            every { actions } returns mutableListOf()
        }

        val rootGroup = RuleConditionGroup(rule = rule, logic = GroupLogic.AND)

        every { ruleRepo.findActiveByTriggerTypeAndProject(TriggerType.PRIORITY_CHANGED, projectId) } returns listOf(rule)
        every { groupRepo.findByRuleIdAndParentGroupIsNull(rule.id) } returns rootGroup

        val actor = mockk<com.taskowolf.auth.domain.User>()
        engine.onFieldChanged(IssueFieldChangedEvent(issue, actor, "priority", "MEDIUM", "CRITICAL"))

        verify { actionExecutor.execute(any(), eq(issue)) }
        verify { eventPublisher.publish(any<AutomationFiredEvent>()) }
    }

    @Test
    fun `rule does not fire when conditions do not match`() {
        val projectId = UUID.randomUUID()
        val project = mockk<com.taskowolf.projects.domain.Project> { every { id } returns projectId }
        val issue = mockk<Issue> {
            every { this@mockk.project } returns project
            every { priority } returns IssuePriority.LOW
            every { assignee } returns null
            every { status } returns mockk { every { id } returns UUID.randomUUID() }
        }

        val rule = mockk<AutomationRule> { every { id } returns UUID.randomUUID(); every { triggerPayload } returns null; every { actions } returns mutableListOf() }
        val priorityCond = RuleCondition(
            group = mockk(), type = ConditionType.PRIORITY, operator = "IS", params = """{"value":"CRITICAL"}"""
        )
        val rootGroup = RuleConditionGroup(rule = rule, logic = GroupLogic.AND)
        rootGroup.conditions.add(priorityCond)

        every { ruleRepo.findActiveByTriggerTypeAndProject(any(), any()) } returns listOf(rule)
        every { groupRepo.findByRuleIdAndParentGroupIsNull(any()) } returns rootGroup

        val actor = mockk<com.taskowolf.auth.domain.User>()
        engine.onFieldChanged(IssueFieldChangedEvent(issue, actor, "priority", "MEDIUM", "LOW"))

        verify(exactly = 0) { actionExecutor.execute(any(), any()) }
    }
}
