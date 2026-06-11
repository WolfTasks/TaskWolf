package com.taskowolf.issues

import com.taskowolf.auth.domain.User
import com.taskowolf.auth.infrastructure.UserRepository
import com.taskowolf.core.application.DomainEventPublisher
import com.taskowolf.issues.api.dto.CreateIssueRequest
import com.taskowolf.issues.application.IssueService
import com.taskowolf.issues.domain.IssueType
import com.taskowolf.issues.infrastructure.IssueRepository
import com.taskowolf.projects.application.ProjectService
import com.taskowolf.projects.domain.Project
import com.taskowolf.workflows.application.WorkflowService
import com.taskowolf.workflows.domain.*
import io.mockk.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import com.taskowolf.core.infrastructure.NotFoundException
import java.util.UUID

class IssueServiceTest {

    private val issueRepository = mockk<IssueRepository>()
    private val projectService = mockk<ProjectService>()
    private val workflowService = mockk<WorkflowService>()
    private val userRepository = mockk<UserRepository>()
    private val eventPublisher = mockk<DomainEventPublisher>(relaxed = true)
    private val service = IssueService(issueRepository, projectService, workflowService, userRepository, eventPublisher)

    private val owner = User(email = "owner@test.com", displayName = "Owner")
    private val workflow = mockk<Workflow>()
    private val status = WorkflowStatus("To Do", StatusCategory.TODO, "#6c8fef", 0, workflow)
    private val project = Project(key = "WOLF", name = "TaskWolf", owner = owner, workflow = workflow)

    @Test
    fun `create issue assigns next key number`() {
        val workflowId = UUID.randomUUID()
        every { workflow.id } returns workflowId
        every { projectService.requireMember("WOLF", owner.id) } returns project
        every { workflowService.getDefaultStatus(workflowId) } returns status
        every { issueRepository.maxKeyNumberByProject(project.id) } returns 5
        every { issueRepository.save(any()) } returnsArgument 0

        val issue = service.create("WOLF", CreateIssueRequest("Fix bug"), owner)

        assert(issue.key == "WOLF-6")
        assert(issue.keyNumber == 6)
    }

    @Test
    fun `create EPIC sets type correctly`() {
        val workflowId = UUID.randomUUID()
        every { workflow.id } returns workflowId
        every { projectService.requireMember("WOLF", owner.id) } returns project
        every { workflowService.getDefaultStatus(workflowId) } returns status
        every { issueRepository.maxKeyNumberByProject(project.id) } returns 0
        every { issueRepository.save(any()) } returnsArgument 0

        val issue = service.create("WOLF", CreateIssueRequest("Big Epic", type = IssueType.EPIC), owner)

        assert(issue.type == IssueType.EPIC)
    }

    @Test
    fun `create throws NotFoundException when project has no workflow`() {
        val projectWithoutWorkflow = Project(key = "WOLF", name = "TaskWolf", owner = owner, workflow = null)
        every { projectService.requireMember("WOLF", owner.id) } returns projectWithoutWorkflow

        assertThrows<NotFoundException> {
            service.create("WOLF", CreateIssueRequest("Issue"), owner)
        }
    }

    @Test
    fun `findByKey does not return issues from another project`() {
        every { projectService.requireMember("WOLF", owner.id) } returns project
        every { issueRepository.findByKeyAndProjectId("OTHER-1", project.id) } returns null

        assertThrows<NotFoundException> {
            service.findByKey("WOLF", "OTHER-1", owner.id)
        }
    }

    @Test
    fun `create rejects assignee who is not a project member`() {
        val workflowId = UUID.randomUUID()
        val stranger = com.taskowolf.auth.domain.User(email = "stranger@test.com", displayName = "Stranger")
        every { workflow.id } returns workflowId
        every { projectService.requireMember("WOLF", owner.id) } returns project
        every { workflowService.getDefaultStatus(workflowId) } returns status
        every { issueRepository.maxKeyNumberByProject(project.id) } returns 0
        every { issueRepository.save(any()) } returnsArgument 0
        every { userRepository.findById(stranger.id) } returns java.util.Optional.of(stranger)
        every { projectService.isMember(project, stranger.id) } returns false

        assertThrows<com.taskowolf.core.infrastructure.NotFoundException> {
            service.create("WOLF", CreateIssueRequest("Task", assigneeId = stranger.id), owner)
        }
    }

    @Test
    fun `create rejects parent issue from another project`() {
        val workflowId = UUID.randomUUID()
        every { workflow.id } returns workflowId
        every { projectService.requireMember("WOLF", owner.id) } returns project
        every { workflowService.getDefaultStatus(workflowId) } returns status
        every { issueRepository.maxKeyNumberByProject(project.id) } returns 0
        every { issueRepository.save(any()) } returnsArgument 0

        val foreignProject = Project(key = "OTHER", name = "Other", owner = owner, workflow = workflow)
        val foreignParent = com.taskowolf.issues.domain.Issue(
            key = "OTHER-1", keyNumber = 1, title = "Foreign", type = com.taskowolf.issues.domain.IssueType.TASK,
            status = status, project = foreignProject, reporter = owner
        )
        every { issueRepository.findById(foreignParent.id) } returns java.util.Optional.of(foreignParent)

        assertThrows<com.taskowolf.core.infrastructure.NotFoundException> {
            service.create("WOLF", CreateIssueRequest("Child", parentId = foreignParent.id), owner)
        }
    }
}
