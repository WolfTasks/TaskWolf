package com.taskowolf.sprints

import com.taskowolf.auth.domain.User
import com.taskowolf.core.application.DomainEventPublisher
import com.taskowolf.core.infrastructure.ConflictException
import com.taskowolf.issues.domain.Issue
import com.taskowolf.issues.domain.IssueType
import com.taskowolf.issues.domain.IssuePriority
import com.taskowolf.issues.infrastructure.IssueRepository
import com.taskowolf.projects.application.ProjectService
import com.taskowolf.projects.domain.Project
import com.taskowolf.sprints.api.dto.CreateSprintRequest
import com.taskowolf.sprints.application.SprintService
import com.taskowolf.sprints.domain.Sprint
import com.taskowolf.sprints.domain.SprintStatus
import com.taskowolf.sprints.infrastructure.SprintRepository
import com.taskowolf.workflows.domain.StatusCategory
import com.taskowolf.workflows.domain.Workflow
import com.taskowolf.workflows.domain.WorkflowStatus
import io.mockk.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Optional

class SprintServiceTest {

    private val sprintRepository = mockk<SprintRepository>()
    private val projectService = mockk<ProjectService>()
    private val issueRepository = mockk<IssueRepository>()
    private val eventPublisher = mockk<DomainEventPublisher>(relaxed = true)
    private val service = SprintService(sprintRepository, projectService, issueRepository, eventPublisher)

    private val owner = User(email = "owner@test.com", displayName = "Owner")
    private val workflow = mockk<Workflow>()
    private val project = Project(key = "WOLF", name = "TaskWolf", owner = owner, workflow = workflow)

    @Test
    fun `start throws ConflictException when active sprint already exists`() {
        val sprint = Sprint(name = "Sprint 1", project = project)
        every { projectService.requireMember("WOLF", owner.id) } returns project
        every { sprintRepository.findById(sprint.id) } returns Optional.of(sprint)
        every { sprintRepository.existsByProjectIdAndStatus(project.id, SprintStatus.ACTIVE) } returns true

        assertThrows<ConflictException> {
            service.start("WOLF", sprint.id, owner)
        }
    }

    @Test
    fun `start sets status to ACTIVE and snapshots planned points`() {
        val sprint = Sprint(name = "Sprint 1", project = project)
        every { projectService.requireMember("WOLF", owner.id) } returns project
        every { sprintRepository.findById(sprint.id) } returns Optional.of(sprint)
        every { sprintRepository.existsByProjectIdAndStatus(project.id, SprintStatus.ACTIVE) } returns false
        every { issueRepository.sumStoryPointsBySprintId(sprint.id) } returns 13L
        every { sprintRepository.save(any()) } returnsArgument 0

        val result = service.start("WOLF", sprint.id, owner)

        assert(result.status == SprintStatus.ACTIVE)
        assert(result.plannedPoints == 13)
    }

    @Test
    fun `complete moves non-DONE issues to backlog and returns count`() {
        val doneStatus = WorkflowStatus("Done", StatusCategory.DONE, "#63dc78", 2, workflow)
        val todoStatus = WorkflowStatus("To Do", StatusCategory.TODO, "#6c8fef", 0, workflow)
        val sprint = Sprint(name = "Sprint 1", status = SprintStatus.ACTIVE, project = project)
        val doneIssue = Issue("WOLF-1", 1, "Done issue", status = doneStatus, project = project, reporter = owner)
        val openIssue = Issue("WOLF-2", 2, "Open issue", status = todoStatus, project = project, reporter = owner)
        openIssue.sprint = sprint

        every { projectService.requireMember("WOLF", owner.id) } returns project
        every { sprintRepository.findById(sprint.id) } returns Optional.of(sprint)
        every { issueRepository.findBySprintId(sprint.id) } returns listOf(doneIssue, openIssue)
        every { issueRepository.saveAll(any<List<Issue>>()) } returnsArgument 0
        every { sprintRepository.save(any()) } returnsArgument 0

        val result = service.complete("WOLF", sprint.id, owner)

        assert(result.sprint.status == SprintStatus.CLOSED)
        assert(result.movedToBacklogCount == 1)
        assert(openIssue.sprint == null)
    }

    @Test
    fun `create persists sprint with project`() {
        every { projectService.requireMember("WOLF", owner.id) } returns project
        every { sprintRepository.save(any()) } returnsArgument 0

        val result = service.create("WOLF", CreateSprintRequest("Sprint 1"), owner)

        assert(result.name == "Sprint 1")
        verify(exactly = 1) { sprintRepository.save(any()) }
    }
}
