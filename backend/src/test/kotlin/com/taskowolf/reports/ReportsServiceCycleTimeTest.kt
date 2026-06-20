package com.taskowolf.reports

import com.taskowolf.auth.domain.User
import com.taskowolf.comments.domain.ActivityType
import com.taskowolf.comments.domain.IssueActivity
import com.taskowolf.comments.infrastructure.IssueActivityRepository
import com.taskowolf.issues.domain.Issue
import com.taskowolf.issues.domain.IssueType
import com.taskowolf.issues.infrastructure.IssueRepository
import com.taskowolf.projects.application.ProjectService
import com.taskowolf.projects.domain.Project
import com.taskowolf.reports.application.ReportsService
import com.taskowolf.sprints.domain.Sprint
import com.taskowolf.sprints.domain.SprintStatus
import com.taskowolf.sprints.infrastructure.SprintRepository
import com.taskowolf.workflows.domain.StatusCategory
import com.taskowolf.workflows.domain.Workflow
import com.taskowolf.workflows.domain.WorkflowStatus
import com.taskowolf.workflows.infrastructure.WorkflowStatusRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Optional
import java.util.UUID

class ReportsServiceCycleTimeTest {

    private val projectService = mockk<ProjectService>()
    private val sprintRepository = mockk<SprintRepository>()
    private val issueRepository = mockk<IssueRepository>()
    private val activityRepository = mockk<IssueActivityRepository>()
    private val statusRepository = mockk<WorkflowStatusRepository>()
    private val service = ReportsService(projectService, sprintRepository, issueRepository, activityRepository, statusRepository)

    private val owner = User(email = "owner@test.com", displayName = "Owner")
    private val project = Project(key = "WOLF", name = "TaskWolf", owner = owner, workflow = null)
    private val workflow = Workflow(name = "Default", project = project)
    private val todoStatus = WorkflowStatus("To Do", StatusCategory.TODO, "#aaa", 0, workflow)
    private val inProgressStatus = WorkflowStatus("In Progress", StatusCategory.IN_PROGRESS, "#bbb", 1, workflow)
    private val doneStatus = WorkflowStatus("Done", StatusCategory.DONE, "#ccc", 2, workflow)
    private val sprint = Sprint(name = "Sprint 1", status = SprintStatus.CLOSED, project = project)

    @BeforeEach
    fun setUp() {
        every { projectService.requireMember("WOLF", owner.id) } returns project
        every { sprintRepository.findById(sprint.id) } returns Optional.of(sprint)
        // Pre-load status map — the new approach
        every { statusRepository.findByWorkflowProjectId(project.id) } returns listOf(todoStatus, inProgressStatus, doneStatus)
    }

    private fun makeIssue(key: String) = Issue(
        key = key, keyNumber = 1, title = key, type = IssueType.TASK,
        status = doneStatus, project = project, reporter = owner
    )

    private val baseInstant = Instant.EPOCH

    private fun makeActivity(issueId: UUID, newValue: String, hoursAgo: Long): IssueActivity {
        val activity = IssueActivity(
            issueId = issueId,
            actorId = owner.id,
            type = ActivityType.STATUS_CHANGED,
            newValue = newValue
        )
        // createdAt is declared in AuditableEntity (direct superclass of IssueActivity)
        val createdAtField = activity.javaClass.superclass.getDeclaredField("createdAt")
        createdAtField.isAccessible = true
        createdAtField.set(activity, baseInstant.minus(Duration.ofHours(hoursAgo)))
        return activity
    }

    @Test
    fun `getCycleTime returns correct hours for single issue`() {
        val issue = makeIssue("WOLF-1")
        every { issueRepository.findBySprintId(sprint.id) } returns listOf(issue)
        every { activityRepository.findByIssueIdAndTypeOrderByCreatedAtAsc(issue.id, ActivityType.STATUS_CHANGED) } returns listOf(
            makeActivity(issue.id, "In Progress", 10),
            makeActivity(issue.id, "Done", 2)
        )

        val result = service.getCycleTime("WOLF", sprint.id, owner.id)

        assertEquals(1, result.issues.size)
        assertNotNull(result.averageCycleTimeHours)
        assertEquals(8.0, result.averageCycleTimeHours!!, 0.001)
    }

    @Test
    fun `getCycleTime skips issues that never reached IN_PROGRESS`() {
        val issue = makeIssue("WOLF-2")
        every { issueRepository.findBySprintId(sprint.id) } returns listOf(issue)
        every { activityRepository.findByIssueIdAndTypeOrderByCreatedAtAsc(issue.id, ActivityType.STATUS_CHANGED) } returns listOf(
            makeActivity(issue.id, "Done", 2)
        )

        val result = service.getCycleTime("WOLF", sprint.id, owner.id)

        assertTrue(result.issues.all { it.cycleTimeHours == null })
        assertNull(result.averageCycleTimeHours)
    }

    @Test
    fun `getCycleTime skips issues in progress but never done`() {
        val issue = makeIssue("WOLF-3")
        every { issueRepository.findBySprintId(sprint.id) } returns listOf(issue)
        every { activityRepository.findByIssueIdAndTypeOrderByCreatedAtAsc(issue.id, ActivityType.STATUS_CHANGED) } returns listOf(
            makeActivity(issue.id, "In Progress", 5)
        )

        val result = service.getCycleTime("WOLF", sprint.id, owner.id)

        assertTrue(result.issues.all { it.cycleTimeHours == null })
        assertNull(result.averageCycleTimeHours)
    }

    @Test
    fun `getCycleTime averages correctly across multiple issues`() {
        val issue1 = makeIssue("WOLF-4")
        val issue2 = makeIssue("WOLF-5")
        every { issueRepository.findBySprintId(sprint.id) } returns listOf(issue1, issue2)
        every { activityRepository.findByIssueIdAndTypeOrderByCreatedAtAsc(issue1.id, ActivityType.STATUS_CHANGED) } returns listOf(
            makeActivity(issue1.id, "In Progress", 10),
            makeActivity(issue1.id, "Done", 2)   // ~8h
        )
        every { activityRepository.findByIssueIdAndTypeOrderByCreatedAtAsc(issue2.id, ActivityType.STATUS_CHANGED) } returns listOf(
            makeActivity(issue2.id, "In Progress", 20),
            makeActivity(issue2.id, "Done", 4)   // ~16h
        )

        val result = service.getCycleTime("WOLF", sprint.id, owner.id)

        assertNotNull(result.averageCycleTimeHours)
        assertEquals(12.0, result.averageCycleTimeHours!!, 0.001)
    }

    @Test
    fun `getCycleTime returns null average when no issues in sprint`() {
        every { issueRepository.findBySprintId(sprint.id) } returns emptyList()

        val result = service.getCycleTime("WOLF", sprint.id, owner.id)

        assertTrue(result.issues.isEmpty())
        assertNull(result.averageCycleTimeHours)
    }

    @Test
    fun `getCycleTimeAggregate returns empty list when no closed sprints`() {
        every { sprintRepository.findByProjectIdAndStatus(project.id, SprintStatus.CLOSED) } returns emptyList()

        val result = service.getCycleTimeAggregate("WOLF", owner.id)

        assertTrue(result.sprints.isEmpty())
    }

    @Test
    fun `getCycleTimeAggregate returns average cycle time per closed sprint`() {
        val issue = makeIssue("WOLF-6")
        every { sprintRepository.findByProjectIdAndStatus(project.id, SprintStatus.CLOSED) } returns listOf(sprint)
        every { issueRepository.findBySprintId(sprint.id) } returns listOf(issue)
        every { activityRepository.findByIssueIdAndTypeOrderByCreatedAtAsc(issue.id, ActivityType.STATUS_CHANGED) } returns listOf(
            makeActivity(issue.id, "In Progress", 10),
            makeActivity(issue.id, "Done", 2)  // ~8h
        )

        val result = service.getCycleTimeAggregate("WOLF", owner.id)

        assertEquals(1, result.sprints.size)
        assertNotNull(result.sprints[0].averageCycleTimeHours)
        assertEquals(8.0, result.sprints[0].averageCycleTimeHours!!, 0.001)
        assertEquals(sprint.name, result.sprints[0].sprintName)
    }
}
