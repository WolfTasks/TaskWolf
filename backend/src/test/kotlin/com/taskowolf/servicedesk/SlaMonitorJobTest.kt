package com.taskowolf.servicedesk

import com.taskowolf.audit.application.AuditService
import com.taskowolf.audit.domain.AuditAction
import com.taskowolf.audit.domain.AuditLevel
import com.taskowolf.auth.domain.User
import com.taskowolf.issues.domain.Issue
import com.taskowolf.issues.domain.IssuePriority
import com.taskowolf.issues.domain.IssueType
import com.taskowolf.issues.infrastructure.IssueRepository
import com.taskowolf.notifications.application.NotificationService
import com.taskowolf.notifications.domain.NotificationType
import com.taskowolf.projects.domain.Project
import com.taskowolf.servicedesk.application.SlaMonitorJob
import com.taskowolf.servicedesk.domain.EscalationRule
import com.taskowolf.servicedesk.domain.ServiceDesk
import com.taskowolf.servicedesk.domain.SlaPolicy
import com.taskowolf.servicedesk.infrastructure.EscalationRuleRepository
import com.taskowolf.servicedesk.infrastructure.ServiceDeskRepository
import com.taskowolf.servicedesk.infrastructure.SlaPolicyRepository
import com.taskowolf.workflows.domain.StatusCategory
import com.taskowolf.workflows.domain.Workflow
import com.taskowolf.workflows.domain.WorkflowStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class SlaMonitorJobTest {

    private val issueRepo = mockk<IssueRepository>()
    private val serviceDeskRepo = mockk<ServiceDeskRepository>()
    private val slaPolicyRepo = mockk<SlaPolicyRepository>()
    private val escalationRuleRepo = mockk<EscalationRuleRepository>()
    private val notificationService = mockk<NotificationService>(relaxed = true)
    private val auditService = mockk<AuditService>(relaxed = true)

    private val job = SlaMonitorJob(
        issueRepo, serviceDeskRepo, slaPolicyRepo,
        escalationRuleRepo, notificationService, auditService
    )

    private val owner = User(email = "owner@test.com", displayName = "Owner")
    private val workflow = buildWorkflow(owner)
    private val status = WorkflowStatus("In Progress", StatusCategory.IN_PROGRESS, "#6c8fef", 0, workflow)
    private val project = Project(key = "WOLF", name = "TaskWolf", owner = owner, workflow = workflow)
    private val projectId = project.id

    private fun buildIssue(slaStartTime: Instant?, priority: IssuePriority = IssuePriority.HIGH): Issue =
        Issue(
            key = "WOLF-1", keyNumber = 1, title = "Test Issue",
            type = IssueType.TASK, priority = priority,
            status = status, project = project,
            reporter = owner, slaStartTime = slaStartTime
        )

    private val serviceDeskId = UUID.randomUUID()
    private val slaPolicyId = UUID.randomUUID()
    private val desk = ServiceDesk(projectId = projectId, enabled = true)
    private val policy = SlaPolicy(serviceDeskId, "High SLA", IssuePriority.HIGH, 30, 60)

    @Test
    fun `run does not fire escalation for issue within SLA`() {
        val issueWithinSla = buildIssue(slaStartTime = Instant.now().minus(5, ChronoUnit.MINUTES))

        every { issueRepo.findBySlaStartTimeIsNotNull() } returns listOf(issueWithinSla)
        every { serviceDeskRepo.findByProjectId(projectId) } returns desk
        every { slaPolicyRepo.findByServiceDeskId(desk.id) } returns listOf(policy)

        job.run()

        verify(exactly = 0) { notificationService.createDirect(any(), any(), any(), any()) }
        verify(exactly = 0) { auditService.log(any(), any(), any()) }
    }

    @Test
    fun `run fires escalation for issue breaching SLA`() {
        val breachedIssue = buildIssue(slaStartTime = Instant.now().minus(90, ChronoUnit.MINUTES))
        val notifyUserId = UUID.randomUUID()
        val escalationRule = EscalationRule(
            slaPolicyId = slaPolicyId,
            escalateAfterMinutes = 60,
            assigneeId = null,
            notifyUserIds = arrayOf(notifyUserId)
        )

        every { issueRepo.findBySlaStartTimeIsNotNull() } returns listOf(breachedIssue)
        every { serviceDeskRepo.findByProjectId(projectId) } returns desk
        every { slaPolicyRepo.findByServiceDeskId(desk.id) } returns listOf(policy)
        every { escalationRuleRepo.findBySlaPolicyId(policy.id) } returns listOf(escalationRule)

        job.run()

        verify(exactly = 1) {
            notificationService.createDirect(
                userId = notifyUserId,
                type = NotificationType.SLA_BREACHED,
                titleKey = "notification.slaBreached.title",
                link = "/issues/WOLF-1",
                titleArgs = match { it.contains("WOLF-1") },
                bodyKey = "notification.slaBreached.body",
                bodyArgs = any(),
                rawBody = any(),
            )
        }
        verify(exactly = 1) {
            auditService.log(
                AuditLevel.WRITE, AuditAction.SLA_BREACHED, "system",
                projectId = projectId,
                resourceType = "ISSUE",
                resourceId = breachedIssue.id.toString()
            )
        }
    }

    @Test
    fun `run skips issue when no ServiceDesk found`() {
        val issue = buildIssue(slaStartTime = Instant.now().minus(90, ChronoUnit.MINUTES))

        every { issueRepo.findBySlaStartTimeIsNotNull() } returns listOf(issue)
        every { serviceDeskRepo.findByProjectId(projectId) } returns null

        job.run()

        verify(exactly = 0) { notificationService.createDirect(any(), any(), any(), any()) }
    }

    @Test
    fun `run skips issue when no matching SLA policy found`() {
        val issue = buildIssue(slaStartTime = Instant.now().minus(90, ChronoUnit.MINUTES), priority = IssuePriority.LOW)

        every { issueRepo.findBySlaStartTimeIsNotNull() } returns listOf(issue)
        every { serviceDeskRepo.findByProjectId(projectId) } returns desk
        // Policy is for HIGH, issue is LOW — no match
        every { slaPolicyRepo.findByServiceDeskId(desk.id) } returns listOf(policy)

        job.run()

        verify(exactly = 0) { notificationService.createDirect(any(), any(), any(), any()) }
    }

    @Test
    fun `run handles empty issue list gracefully`() {
        every { issueRepo.findBySlaStartTimeIsNotNull() } returns emptyList()

        job.run()

        verify(exactly = 0) { notificationService.createDirect(any(), any(), any(), any()) }
        verify(exactly = 0) { auditService.log(any(), any(), any()) }
    }

    companion object {
        fun buildWorkflow(owner: User): Workflow {
            val project = Project(key = "WOLF", name = "TaskWolf", owner = owner, workflow = null)
            return Workflow(name = "Default", project = project)
        }
    }
}
