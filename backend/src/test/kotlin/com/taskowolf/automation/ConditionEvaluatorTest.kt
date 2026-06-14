package com.taskowolf.automation

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.taskowolf.automation.application.ConditionEvaluator
import com.taskowolf.automation.domain.*
import com.taskowolf.issues.domain.Issue
import com.taskowolf.issues.domain.IssuePriority
import com.taskowolf.issues.domain.IssueType
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class ConditionEvaluatorTest {
    private val evaluator = ConditionEvaluator(jacksonObjectMapper())

    private fun makeGroup(logic: GroupLogic, vararg conditions: RuleCondition): RuleConditionGroup {
        val rule = mockk<AutomationRule>()
        val group = RuleConditionGroup(rule = rule, logic = logic)
        group.conditions.addAll(conditions.toList())
        return group
    }

    private fun makeCondition(type: ConditionType, operator: String, value: String): RuleCondition {
        val group = mockk<RuleConditionGroup>()
        return RuleCondition(group, type, operator, """{"value":"$value"}""")
    }

    private fun makeIssue(
        priority: IssuePriority = IssuePriority.MEDIUM,
        type: IssueType = IssueType.TASK,
        storyPoints: Int? = null
    ): Issue {
        val status = mockk<com.taskowolf.workflows.domain.WorkflowStatus> { every { id } returns UUID.randomUUID() }
        val project = mockk<com.taskowolf.projects.domain.Project> { every { id } returns UUID.randomUUID() }
        return mockk {
            every { this@mockk.priority } returns priority
            every { this@mockk.type } returns type
            every { this@mockk.storyPoints } returns storyPoints
            every { this@mockk.status } returns status
            every { this@mockk.project } returns project
            every { assignee } returns null
        }
    }

    @Test
    fun `AND group — all conditions must pass`() {
        val issue = makeIssue(priority = IssuePriority.HIGH, type = IssueType.BUG)
        val c1 = makeCondition(ConditionType.PRIORITY, "IS", "HIGH")
        val c2 = makeCondition(ConditionType.ISSUE_TYPE, "IS", "BUG")
        val group = makeGroup(GroupLogic.AND, c1, c2)
        assertTrue(evaluator.evaluate(group, issue))
    }

    @Test
    fun `AND group — fails if one condition fails`() {
        val issue = makeIssue(priority = IssuePriority.LOW, type = IssueType.BUG)
        val c1 = makeCondition(ConditionType.PRIORITY, "IS", "HIGH")
        val c2 = makeCondition(ConditionType.ISSUE_TYPE, "IS", "BUG")
        val group = makeGroup(GroupLogic.AND, c1, c2)
        assertFalse(evaluator.evaluate(group, issue))
    }

    @Test
    fun `OR group — passes if one condition passes`() {
        val issue = makeIssue(priority = IssuePriority.LOW, type = IssueType.BUG)
        val c1 = makeCondition(ConditionType.PRIORITY, "IS", "HIGH")
        val c2 = makeCondition(ConditionType.ISSUE_TYPE, "IS", "BUG")
        val group = makeGroup(GroupLogic.OR, c1, c2)
        assertTrue(evaluator.evaluate(group, issue))
    }

    @Test
    fun `GT operator — storyPoints greater than value`() {
        val issue = makeIssue(storyPoints = 8)
        val c = makeCondition(ConditionType.STORY_POINTS, "GT", "5")
        val group = makeGroup(GroupLogic.AND, c)
        assertTrue(evaluator.evaluate(group, issue))
    }

    @Test
    fun `empty group returns true`() {
        val rule = mockk<AutomationRule>()
        val group = RuleConditionGroup(rule = rule, logic = GroupLogic.AND)
        assertTrue(evaluator.evaluate(group, makeIssue()))
    }
}
