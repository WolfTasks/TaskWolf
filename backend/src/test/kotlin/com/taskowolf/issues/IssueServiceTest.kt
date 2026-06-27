package com.taskowolf.issues

import com.taskowolf.auth.domain.User
import com.taskowolf.auth.infrastructure.UserRepository
import com.taskowolf.core.application.DomainEventPublisher
import com.taskowolf.core.infrastructure.NotFoundException
import com.taskowolf.issues.api.dto.CreateIssueRequest
import com.taskowolf.issues.api.dto.UpdateIssueRequest
import com.taskowolf.issues.application.IssueService
import com.taskowolf.issues.domain.Issue
import com.taskowolf.issues.domain.IssueType
import com.taskowolf.issues.domain.events.IssueFieldChangedEvent
import com.taskowolf.issues.domain.events.IssueStatusChangedEvent
import com.taskowolf.issues.infrastructure.IssueRepository
import com.taskowolf.labels.domain.Label
import com.taskowolf.labels.infrastructure.LabelRepository
import com.taskowolf.projects.application.ProjectService
import com.taskowolf.projects.domain.Project
import com.taskowolf.workflows.application.WorkflowService
import com.taskowolf.workflows.domain.*
import io.mockk.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.util.UUID

class IssueServiceTest {

    private val issueRepository = mockk<IssueRepository>()
    private val projectService = mockk<ProjectService>()
    private val workflowService = mockk<WorkflowService>()
    private val userRepository = mockk<UserRepository>()
    private val eventPublisher = mockk<DomainEventPublisher>(relaxed = true)
    private val sprintRepository = mockk<com.taskowolf.sprints.infrastructure.SprintRepository>()
    private val labelRepository = mockk<LabelRepository>()
    private val service = IssueService(issueRepository, projectService, workflowService, userRepository, eventPublisher, sprintRepository, labelRepository)

    private val owner = User(email = "owner@test.com", displayName = "Owner")
    private val workflow = mockk<Workflow>()
    private val status = WorkflowStatus("To Do", StatusCategory.TODO, "#6c8fef", 0, workflow)
    private val project = Project(key = "WOLF", name = "TaskWolf", owner = owner, workflow = workflow)
    private val issue = Issue(
        key = "WOLF-1", keyNumber = 1, title = "Test", type = IssueType.TASK,
        status = status, project = project, reporter = owner
    )

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
        val stranger = User(email = "stranger@test.com", displayName = "Stranger")
        every { workflow.id } returns workflowId
        every { projectService.requireMember("WOLF", owner.id) } returns project
        every { workflowService.getDefaultStatus(workflowId) } returns status
        every { issueRepository.maxKeyNumberByProject(project.id) } returns 0
        every { issueRepository.save(any()) } returnsArgument 0
        every { userRepository.findById(stranger.id) } returns java.util.Optional.of(stranger)
        every { projectService.isMember(project, stranger.id) } returns false

        assertThrows<NotFoundException> {
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
        val foreignParent = Issue(
            key = "OTHER-1", keyNumber = 1, title = "Foreign", type = IssueType.TASK,
            status = status, project = foreignProject, reporter = owner
        )
        every { issueRepository.findById(foreignParent.id) } returns java.util.Optional.of(foreignParent)

        assertThrows<NotFoundException> {
            service.create("WOLF", CreateIssueRequest("Child", parentId = foreignParent.id), owner)
        }
    }

    @Test
    fun `update publishes IssueFieldChangedEvent when title changes`() {
        val workflowId = UUID.randomUUID()
        every { workflow.id } returns workflowId
        every { projectService.requireMember("WOLF", owner.id) } returns project
        every { issueRepository.findById(issue.id) } returns java.util.Optional.of(issue)
        every { issueRepository.save(any()) } returnsArgument 0

        val events = mutableListOf<Any>()
        every { eventPublisher.publish(capture(events)) } answers { Unit }

        service.update("WOLF", issue.id, UpdateIssueRequest(title = "New Title"), owner)

        val fieldEvent = events.filterIsInstance<IssueFieldChangedEvent>().first()
        assertEquals("title", fieldEvent.field)
        assertEquals("Test", fieldEvent.oldValue)
        assertEquals("New Title", fieldEvent.newValue)
        assertEquals(owner, fieldEvent.actor)
    }

    @Test
    fun `update publishes IssueStatusChangedEvent with actor when status changes`() {
        val workflowId = UUID.randomUUID()
        val newStatus = WorkflowStatus("Done", StatusCategory.DONE, "#aaa", 2, workflow)
        every { workflow.id } returns workflowId
        every { projectService.requireMember("WOLF", owner.id) } returns project
        every { issueRepository.findById(issue.id) } returns java.util.Optional.of(issue)
        every { issueRepository.save(any()) } returnsArgument 0
        every { workflowService.findStatusById(newStatus.id) } returns newStatus
        every { workflowService.validateTransition(any(), any(), any()) } just Runs

        val events = mutableListOf<Any>()
        every { eventPublisher.publish(capture(events)) } answers { Unit }

        service.update("WOLF", issue.id, UpdateIssueRequest(statusId = newStatus.id), owner)

        val statusEvent = events.filterIsInstance<IssueStatusChangedEvent>().first()
        assertEquals(owner, statusEvent.actor)
        assertEquals("Done", statusEvent.newStatus.name)
    }

    @Test
    fun `findByProject with overdue=true calls findOverdueByProjectId with StatusCategory DONE`() {
        val emptyPage = PageImpl<Issue>(emptyList())
        every { projectService.requireMember("WOLF", owner.id) } returns project
        every { issueRepository.findOverdueByProjectId(project.id, StatusCategory.DONE, any()) } returns emptyPage

        service.findByProject("WOLF", owner.id, 0, 20, overdue = true)

        verify(exactly = 1) { issueRepository.findOverdueByProjectId(project.id, StatusCategory.DONE, any()) }
        verify(exactly = 0) { issueRepository.findByProjectIdAndAssigneeId(any(), any(), any()) }
        verify(exactly = 0) { issueRepository.findAllByProjectId(any(), any()) }
    }

    @Test
    fun `findByProject with overdue=true and assigneeMe=true calls findOverdueByProjectIdAndAssigneeId`() {
        val emptyPage = PageImpl<Issue>(emptyList())
        every { projectService.requireMember("WOLF", owner.id) } returns project
        every { issueRepository.findOverdueByProjectIdAndAssigneeId(project.id, owner.id, StatusCategory.DONE, any()) } returns emptyPage

        service.findByProject("WOLF", owner.id, 0, 20, assigneeMe = true, overdue = true)

        verify(exactly = 1) { issueRepository.findOverdueByProjectIdAndAssigneeId(project.id, owner.id, StatusCategory.DONE, any()) }
        verify(exactly = 0) { issueRepository.findOverdueByProjectId(any(), any(), any()) }
        verify(exactly = 0) { issueRepository.findByProjectIdAndAssigneeId(any(), any(), any()) }
    }

    @Test
    fun `findByProject with assigneeMe=true calls findByProjectIdAndAssigneeId`() {
        val emptyPage = PageImpl<Issue>(emptyList())
        every { projectService.requireMember("WOLF", owner.id) } returns project
        every { issueRepository.findByProjectIdAndAssigneeId(project.id, owner.id, any()) } returns emptyPage

        service.findByProject("WOLF", owner.id, 0, 20, assigneeMe = true)

        verify(exactly = 1) { issueRepository.findByProjectIdAndAssigneeId(project.id, owner.id, any()) }
        verify(exactly = 0) { issueRepository.findOverdueByProjectId(any(), any(), any()) }
        verify(exactly = 0) { issueRepository.findAllByProjectId(any(), any()) }
    }

    @Test
    fun `update sets type when type provided`() {
        every { projectService.requireMember("WOLF", owner.id) } returns project
        every { issueRepository.findById(issue.id) } returns java.util.Optional.of(issue)
        every { issueRepository.save(any()) } returnsArgument 0

        val updated = service.update("WOLF", issue.id,
            UpdateIssueRequest(type = IssueType.BUG),
            owner)

        assert(updated.type == IssueType.BUG)
    }

    @Test
    fun `update sets dueDate when provided`() {
        val date = java.time.LocalDate.of(2026, 12, 31)
        every { projectService.requireMember("WOLF", owner.id) } returns project
        every { issueRepository.findById(issue.id) } returns java.util.Optional.of(issue)
        every { issueRepository.save(any()) } returnsArgument 0

        val updated = service.update("WOLF", issue.id,
            UpdateIssueRequest(dueDate = date),
            owner)

        assert(updated.dueDate == date)
    }

    @Test
    fun `update clears dueDate when clearDueDate is true`() {
        issue.dueDate = java.time.LocalDate.of(2026, 1, 1)
        every { projectService.requireMember("WOLF", owner.id) } returns project
        every { issueRepository.findById(issue.id) } returns java.util.Optional.of(issue)
        every { issueRepository.save(any()) } returnsArgument 0

        val updated = service.update("WOLF", issue.id,
            UpdateIssueRequest(clearDueDate = true),
            owner)

        assert(updated.dueDate == null)
    }

    @Test
    fun `update unassigns when clearAssignee is true`() {
        val assignee = User(email = "dev@test.com", displayName = "Dev")
        issue.assignee = assignee
        every { projectService.requireMember("WOLF", owner.id) } returns project
        every { issueRepository.findById(issue.id) } returns java.util.Optional.of(issue)
        every { issueRepository.save(any()) } returnsArgument 0

        val updated = service.update("WOLF", issue.id,
            UpdateIssueRequest(clearAssignee = true),
            owner)

        assert(updated.assignee == null)
    }

    @Test
    fun `update assigns sprint when sprintId provided`() {
        val sprint = com.taskowolf.sprints.domain.Sprint(
            name = "Sprint 1", project = project,
            status = com.taskowolf.sprints.domain.SprintStatus.ACTIVE
        )
        every { projectService.requireMember("WOLF", owner.id) } returns project
        every { issueRepository.findById(issue.id) } returns java.util.Optional.of(issue)
        every { sprintRepository.findById(sprint.id) } returns java.util.Optional.of(sprint)
        every { issueRepository.save(any()) } returnsArgument 0

        val updated = service.update("WOLF", issue.id,
            UpdateIssueRequest(sprintId = sprint.id),
            owner)

        assert(updated.sprint?.id == sprint.id)
    }

    @Test
    fun `update clears sprint when clearSprint is true`() {
        val sprint = com.taskowolf.sprints.domain.Sprint(
            name = "Sprint 1", project = project,
            status = com.taskowolf.sprints.domain.SprintStatus.ACTIVE
        )
        issue.sprint = sprint
        every { projectService.requireMember("WOLF", owner.id) } returns project
        every { issueRepository.findById(issue.id) } returns java.util.Optional.of(issue)
        every { issueRepository.save(any()) } returnsArgument 0

        val updated = service.update("WOLF", issue.id,
            UpdateIssueRequest(clearSprint = true),
            owner)

        assert(updated.sprint == null)
    }

    @Test
    fun `update sets labels when labelIds provided`() {
        val label = Label(
            name = "bug", color = "#e11d48", project = project
        )
        val labelRepository = mockk<LabelRepository>()
        // Re-create service with labelRepository
        val serviceWithLabels = IssueService(
            issueRepository, projectService, workflowService, userRepository, eventPublisher, sprintRepository, labelRepository
        )
        every { projectService.requireMember("WOLF", owner.id) } returns project
        every { issueRepository.findById(issue.id) } returns java.util.Optional.of(issue)
        every { labelRepository.findAllById(listOf(label.id)) } returns listOf(label)
        every { issueRepository.save(any()) } returnsArgument 0

        val updated = serviceWithLabels.update(
            "WOLF", issue.id,
            UpdateIssueRequest(labelIds = listOf(label.id)),
            owner
        )

        assert(updated.labels.contains(label))
    }

    @Test
    fun `update clears labels when labelIds is empty list`() {
        val label = Label(
            name = "bug", color = "#e11d48", project = project
        )
        issue.labels.add(label)
        val labelRepository = mockk<LabelRepository>()
        val serviceWithLabels = IssueService(
            issueRepository, projectService, workflowService, userRepository, eventPublisher, sprintRepository, labelRepository
        )
        every { projectService.requireMember("WOLF", owner.id) } returns project
        every { issueRepository.findById(issue.id) } returns java.util.Optional.of(issue)
        every { labelRepository.findAllById(emptyList()) } returns emptyList()
        every { issueRepository.save(any()) } returnsArgument 0

        val updated = serviceWithLabels.update(
            "WOLF", issue.id,
            UpdateIssueRequest(labelIds = emptyList()),
            owner
        )

        assert(updated.labels.isEmpty())
    }

    @Test
    fun `update silently drops labels from other projects`() {
        val otherProject = Project(key = "OTHER", name = "Other", owner = owner, workflow = null)
        val foreignLabel = Label(name = "foreign", color = "#000000", project = otherProject)
        val labelRepository = mockk<LabelRepository>()
        val serviceWithLabels = IssueService(
            issueRepository, projectService, workflowService, userRepository, eventPublisher, sprintRepository, labelRepository
        )
        every { projectService.requireMember("WOLF", owner.id) } returns project
        every { issueRepository.findById(issue.id) } returns java.util.Optional.of(issue)
        every { labelRepository.findAllById(listOf(foreignLabel.id)) } returns listOf(foreignLabel)
        every { issueRepository.save(any()) } returnsArgument 0

        val updated = serviceWithLabels.update(
            "WOLF", issue.id,
            UpdateIssueRequest(labelIds = listOf(foreignLabel.id)),
            owner
        )

        assert(updated.labels.isEmpty()) { "Foreign label should not be assigned" }
    }
}
