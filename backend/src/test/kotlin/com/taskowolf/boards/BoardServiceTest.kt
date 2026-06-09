package com.taskowolf.boards

import com.taskowolf.auth.domain.User
import com.taskowolf.boards.application.BoardService
import com.taskowolf.issues.infrastructure.IssueRepository
import com.taskowolf.projects.application.ProjectService
import com.taskowolf.projects.domain.Project
import com.taskowolf.sprints.domain.Sprint
import com.taskowolf.sprints.domain.SprintStatus
import com.taskowolf.sprints.infrastructure.SprintRepository
import com.taskowolf.workflows.domain.StatusCategory
import com.taskowolf.workflows.domain.Workflow
import com.taskowolf.workflows.domain.WorkflowStatus
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

class BoardServiceTest {

    private val projectService = mockk<ProjectService>()
    private val sprintRepository = mockk<SprintRepository>()
    private val issueRepository = mockk<IssueRepository>()
    private val service = BoardService(projectService, sprintRepository, issueRepository)

    private val owner = User(email = "owner@test.com", displayName = "Owner")
    private val workflow = mockk<Workflow>()
    private val project = Project(key = "WOLF", name = "TaskWolf", owner = owner, workflow = workflow)
    private val status = WorkflowStatus("To Do", StatusCategory.TODO, "#6c8fef", 0, workflow)

    @Test
    fun `getBoard returns null when no active sprint`() {
        every { projectService.requireMember("WOLF", owner.id) } returns project
        every { sprintRepository.findByProjectIdAndStatus(project.id, SprintStatus.ACTIVE) } returns emptyList()

        val result = service.getBoard("WOLF", owner.id)

        assert(result == null)
    }

    @Test
    fun `getBoard groups issues into columns by status`() {
        val sprint = Sprint(name = "Sprint 1", status = SprintStatus.ACTIVE, project = project)
        val issue = com.taskowolf.issues.domain.Issue(
            "WOLF-1", 1, "Test", status = status, project = project, reporter = owner
        )
        every { projectService.requireMember("WOLF", owner.id) } returns project
        every { sprintRepository.findByProjectIdAndStatus(project.id, SprintStatus.ACTIVE) } returns listOf(sprint)
        every { workflow.statuses } returns mutableListOf(status)
        every { issueRepository.findBySprintId(sprint.id) } returns listOf(issue)

        val result = service.getBoard("WOLF", owner.id)!!

        assert(result.columns.size == 1)
        assert(result.columns[0].issues.size == 1)
        assert(result.columns[0].issues[0].key == "WOLF-1")
    }

    @Test
    fun `getBacklog returns planned sprints and unassigned issues`() {
        val sprint = Sprint(name = "Sprint 1", project = project)
        val issue = com.taskowolf.issues.domain.Issue(
            "WOLF-1", 1, "Backlog issue", status = status, project = project, reporter = owner
        )
        every { projectService.requireMember("WOLF", owner.id) } returns project
        every { sprintRepository.findByProjectIdAndStatus(project.id, SprintStatus.PLANNED) } returns listOf(sprint)
        every { issueRepository.findBySprintId(sprint.id) } returns emptyList()
        every { issueRepository.findByProjectIdAndSprintIsNull(project.id) } returns listOf(issue)

        val result = service.getBacklog("WOLF", owner.id)

        assert(result.sprints.size == 1)
        assert(result.backlogIssues.size == 1)
    }
}
