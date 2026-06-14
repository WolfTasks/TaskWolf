package com.taskowolf.automation

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.taskowolf.automation.application.ActionExecutor
import com.taskowolf.automation.domain.ActionType
import com.taskowolf.automation.domain.AutomationRule
import com.taskowolf.automation.domain.RuleAction
import com.taskowolf.comments.infrastructure.CommentRepository
import com.taskowolf.issues.domain.Issue
import com.taskowolf.issues.domain.IssuePriority
import com.taskowolf.issues.infrastructure.IssueRepository
import com.taskowolf.notifications.application.NotificationService
import com.taskowolf.notifications.domain.NotificationType
import com.taskowolf.workflows.infrastructure.WorkflowStatusRepository
import io.mockk.*
import org.junit.jupiter.api.Test
import java.util.UUID

class ActionExecutorTest {
    private val issueRepo = mockk<IssueRepository>(relaxed = true)
    private val statusRepo = mockk<WorkflowStatusRepository>(relaxed = true)
    private val notificationService = mockk<NotificationService>(relaxed = true)
    private val commentRepo = mockk<CommentRepository>(relaxed = true)

    private val executor = ActionExecutor(issueRepo, statusRepo, notificationService, commentRepo, jacksonObjectMapper())

    private fun makeAction(type: ActionType, params: String): RuleAction {
        val rule = mockk<AutomationRule> { every { id } returns UUID.randomUUID() }
        return RuleAction(rule, 0, type, params)
    }

    private fun makeIssue(): Issue {
        val status = mockk<com.taskowolf.workflows.domain.WorkflowStatus>()
        val project = mockk<com.taskowolf.projects.domain.Project> {
            every { key } returns "TEST"
            every { id } returns UUID.randomUUID()
        }
        val reporter = mockk<com.taskowolf.auth.domain.User> { every { id } returns UUID.randomUUID() }
        return mockk(relaxed = true) {
            every { this@mockk.status } returns status
            every { this@mockk.project } returns project
            every { this@mockk.reporter } returns reporter
            every { this@mockk.assignee } returns null
            every { this@mockk.key } returns "TEST-1"
            every { this@mockk.id } returns UUID.randomUUID()
            every { priority } returns IssuePriority.MEDIUM
        }
    }

    @Test
    fun `SET_PRIORITY updates issue priority`() {
        val issue = makeIssue()
        every { issueRepo.save(issue) } returns issue
        executor.execute(listOf(makeAction(ActionType.SET_PRIORITY, """{"priority":"HIGH"}""")), issue)
        verify { issue.priority = IssuePriority.HIGH }
        verify { issueRepo.save(issue) }
    }

    @Test
    fun `SEND_NOTIFICATION calls notificationService`() {
        val issue = makeIssue()
        executor.execute(listOf(makeAction(ActionType.SEND_NOTIFICATION, """{"message":"Hello"}""")), issue)
        verify { notificationService.createDirect(any(), NotificationType.AUTOMATION, any(), any(), any()) }
    }
}
