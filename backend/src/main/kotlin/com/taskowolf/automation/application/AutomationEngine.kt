package com.taskowolf.automation.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.taskowolf.automation.domain.TriggerType
import com.taskowolf.automation.domain.events.AutomationFiredEvent
import com.taskowolf.automation.infrastructure.AutomationRuleRepository
import com.taskowolf.automation.infrastructure.RuleConditionGroupRepository
import com.taskowolf.comments.domain.events.CommentCreatedEvent
import com.taskowolf.core.application.DomainEventPublisher
import com.taskowolf.issues.domain.Issue
import com.taskowolf.issues.domain.events.IssueCreatedEvent
import com.taskowolf.issues.domain.events.IssueFieldChangedEvent
import com.taskowolf.issues.domain.events.IssueStatusChangedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class AutomationEngine(
    private val ruleRepository: AutomationRuleRepository,
    private val groupRepository: RuleConditionGroupRepository,
    private val conditionEvaluator: ConditionEvaluator,
    private val actionExecutor: ActionExecutor,
    private val eventPublisher: DomainEventPublisher,
    private val mapper: ObjectMapper
) {
    @EventListener
    fun onIssueCreated(event: IssueCreatedEvent) =
        fire(TriggerType.ISSUE_CREATED, event.issue, event.issue.project.id, emptyMap())

    @EventListener
    fun onStatusChanged(event: IssueStatusChangedEvent) =
        fire(TriggerType.STATUS_CHANGED, event.issue, event.issue.project.id,
            mapOf("toStatusId" to event.newStatus.id.toString()))

    @EventListener
    fun onFieldChanged(event: IssueFieldChangedEvent) {
        when (event.field) {
            "priority" -> fire(TriggerType.PRIORITY_CHANGED, event.issue, event.issue.project.id,
                mapOf("priority" to (event.newValue ?: "")))
            "assignee" -> fire(TriggerType.ASSIGNEE_CHANGED, event.issue, event.issue.project.id, emptyMap())
        }
    }

    @EventListener
    fun onCommentCreated(event: CommentCreatedEvent) =
        fire(TriggerType.COMMENT_ADDED, event.issue, event.issue.project.id, emptyMap())

    private fun fire(
        triggerType: TriggerType,
        issue: Issue,
        projectId: UUID,
        eventPayload: Map<String, String>
    ) {
        val rules = ruleRepository.findActiveByTriggerTypeAndProject(triggerType, projectId)
        for (rule in rules) {
            if (!payloadMatches(rule.triggerPayload, eventPayload)) continue
            val rootGroup = groupRepository.findByRuleIdAndParentGroupIsNull(rule.id) ?: continue
            if (conditionEvaluator.evaluate(rootGroup, issue)) {
                actionExecutor.execute(rule.actions, issue)
                eventPublisher.publish(AutomationFiredEvent(rule, issue))
            }
        }
    }

    private fun payloadMatches(rulePayload: String?, eventPayload: Map<String, String>): Boolean {
        if (rulePayload.isNullOrBlank()) return true
        val required: Map<String, String> = mapper.readValue(rulePayload)
        return required.all { (k, v) -> eventPayload[k] == v }
    }
}
