package com.taskowolf.automation.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.taskowolf.automation.domain.*
import com.taskowolf.issues.domain.Issue
import org.springframework.stereotype.Component

@Component
class ConditionEvaluator(private val mapper: ObjectMapper) {

    fun evaluate(group: RuleConditionGroup, issue: Issue): Boolean {
        val conditionResults = group.conditions.map { evaluateOne(it, issue) }
        val childResults = group.childGroups.map { evaluate(it, issue) }
        val all = conditionResults + childResults
        if (all.isEmpty()) return true
        return if (group.logic == GroupLogic.AND) all.all { it } else all.any { it }
    }

    private fun evaluateOne(condition: RuleCondition, issue: Issue): Boolean {
        val params: Map<String, String> = mapper.readValue(condition.params)
        val value = params["value"] ?: return false
        val actual: String? = when (condition.type) {
            ConditionType.ISSUE_TYPE   -> issue.type.name
            ConditionType.PRIORITY     -> issue.priority.name
            ConditionType.ASSIGNEE     -> issue.assignee?.id?.toString()
            ConditionType.STATUS       -> issue.status.id.toString()
            ConditionType.STORY_POINTS -> issue.storyPoints?.toString()
            ConditionType.PROJECT      -> issue.project.id.toString()
        }
        return when (condition.operator) {
            "IS"       -> actual == value
            "IS_NOT"   -> actual != value
            "CONTAINS" -> actual?.contains(value, ignoreCase = true) == true
            "GT"       -> actual?.toDoubleOrNull()?.let { it > value.toDouble() } == true
            "LT"       -> actual?.toDoubleOrNull()?.let { it < value.toDouble() } == true
            else       -> false
        }
    }
}
