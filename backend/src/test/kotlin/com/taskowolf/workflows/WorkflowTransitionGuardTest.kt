package com.taskowolf.workflows

import tools.jackson.module.kotlin.jacksonObjectMapper
import com.taskowolf.auth.domain.User
import com.taskowolf.core.infrastructure.BadRequestException
import com.taskowolf.issues.domain.Issue
import com.taskowolf.projects.domain.Project
import com.taskowolf.projects.domain.ProjectMember
import com.taskowolf.projects.domain.ProjectRole
import com.taskowolf.projects.infrastructure.ProjectMemberRepository
import com.taskowolf.workflows.application.WorkflowService
import com.taskowolf.workflows.domain.*
import com.taskowolf.workflows.infrastructure.WorkflowRepository
import com.taskowolf.workflows.infrastructure.WorkflowStatusPositionRepository
import com.taskowolf.workflows.infrastructure.WorkflowStatusRepository
import com.taskowolf.workflows.infrastructure.WorkflowTransitionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class WorkflowTransitionGuardTest {

    private val workflowRepository = mockk<WorkflowRepository>()
    private val statusRepository = mockk<WorkflowStatusRepository>()
    private val transitionRepository = mockk<WorkflowTransitionRepository>()
    private val positionRepository = mockk<WorkflowStatusPositionRepository>()
    private val projectMemberRepository = mockk<ProjectMemberRepository>()
    private val mapper = jacksonObjectMapper()

    private val service = WorkflowService(
        workflowRepository,
        statusRepository,
        transitionRepository,
        positionRepository,
        projectMemberRepository,
        mapper
    )

    private val actor = User(email = "actor@test.com", displayName = "Actor")

    // Helper to build a mocked issue whose project has NO workflow
    private fun issueWithNoWorkflow(): Issue {
        val project = mockk<Project>()
        every { project.workflow } returns null

        val status = mockk<WorkflowStatus>()
        every { status.id } returns UUID.randomUUID()
        every { status.name } returns "To Do"

        val issue = mockk<Issue>()
        every { issue.project } returns project
        every { issue.status } returns status
        return issue
    }

    // Helper to build a mocked issue whose project HAS a workflow
    private fun issueWithWorkflow(workflowId: UUID, statusId: UUID, storyPoints: Int? = null): Issue {
        val workflow = mockk<Workflow>()
        every { workflow.id } returns workflowId
        every { transitionRepository.existsByWorkflowId(workflowId) } returns true

        val project = mockk<Project>()
        every { project.workflow } returns workflow
        every { project.id } returns UUID.randomUUID()

        val status = mockk<WorkflowStatus>()
        every { status.id } returns statusId
        every { status.name } returns "To Do"

        val issue = mockk<Issue>()
        every { issue.project } returns project
        every { issue.status } returns status
        every { issue.title } returns "Test Issue"
        every { issue.description } returns null
        every { issue.assignee } returns null
        every { issue.storyPoints } returns storyPoints
        every { issue.dueDate } returns null
        return issue
    }

    @Test
    fun `passes when workflow is null`() {
        val issue = issueWithNoWorkflow()
        val toStatusId = UUID.randomUUID()

        assertDoesNotThrow {
            service.validateTransition(issue, toStatusId, actor)
        }
    }

    @Test
    fun `throws BadRequestException when transition not found`() {
        val workflowId = UUID.randomUUID()
        val fromStatusId = UUID.randomUUID()
        val toStatusId = UUID.randomUUID()
        val issue = issueWithWorkflow(workflowId, fromStatusId)

        every {
            transitionRepository.findByWorkflowIdAndFromStatusIdAndToStatusId(workflowId, fromStatusId, toStatusId)
        } returns null

        assertThrows<BadRequestException> {
            service.validateTransition(issue, toStatusId, actor)
        }
    }

    @Test
    fun `passes when transition has no guards`() {
        val workflowId = UUID.randomUUID()
        val fromStatusId = UUID.randomUUID()
        val toStatusId = UUID.randomUUID()
        val issue = issueWithWorkflow(workflowId, fromStatusId)

        val transition = mockk<WorkflowTransition>()
        every { transition.guards } returns null

        every {
            transitionRepository.findByWorkflowIdAndFromStatusIdAndToStatusId(workflowId, fromStatusId, toStatusId)
        } returns transition

        assertDoesNotThrow {
            service.validateTransition(issue, toStatusId, actor)
        }
    }

    @Test
    fun `throws BadRequestException when required field is missing`() {
        val workflowId = UUID.randomUUID()
        val fromStatusId = UUID.randomUUID()
        val toStatusId = UUID.randomUUID()
        val issue = issueWithWorkflow(workflowId, fromStatusId, storyPoints = null)

        val guardsJson = """[{"type":"REQUIRED_FIELD","field":"storyPoints"}]"""
        val transition = mockk<WorkflowTransition>()
        every { transition.guards } returns guardsJson

        every {
            transitionRepository.findByWorkflowIdAndFromStatusIdAndToStatusId(workflowId, fromStatusId, toStatusId)
        } returns transition

        assertThrows<BadRequestException> {
            service.validateTransition(issue, toStatusId, actor)
        }
    }

    @Test
    fun `passes when required field is present`() {
        val workflowId = UUID.randomUUID()
        val fromStatusId = UUID.randomUUID()
        val toStatusId = UUID.randomUUID()
        val issue = issueWithWorkflow(workflowId, fromStatusId, storyPoints = 5)

        val guardsJson = """[{"type":"REQUIRED_FIELD","field":"storyPoints"}]"""
        val transition = mockk<WorkflowTransition>()
        every { transition.guards } returns guardsJson

        every {
            transitionRepository.findByWorkflowIdAndFromStatusIdAndToStatusId(workflowId, fromStatusId, toStatusId)
        } returns transition

        assertDoesNotThrow {
            service.validateTransition(issue, toStatusId, actor)
        }
    }

    // ---- Write path (B4): updateGuards must persist the polymorphic "type" discriminator ----

    private fun storedGuardsAfterUpdate(guards: List<TransitionGuard>): String {
        val transitionId = UUID.randomUUID()
        val transition = WorkflowTransition(workflow = mockk(), toStatus = mockk())
        every { transitionRepository.findById(transitionId) } returns java.util.Optional.of(transition)
        every { transitionRepository.save(any<WorkflowTransition>()) } answers { firstArg() }

        service.updateGuards(transitionId, guards)

        verify { transitionRepository.save(transition) }
        return transition.guards!!
    }

    @Test
    fun `updateGuards writes the type discriminator for every guard`() {
        val stored = storedGuardsAfterUpdate(
            listOf(RequiredFieldGuard("storyPoints"), RoleRestrictionGuard(listOf("ADMIN", "MEMBER")))
        )

        val nodes = mapper.readTree(stored)
        assertEquals("REQUIRED_FIELD", nodes[0].get("type")?.asString())
        assertEquals("storyPoints", nodes[0].get("field")?.asString())
        assertEquals("ROLE_RESTRICTION", nodes[1].get("type")?.asString())
        val roles = nodes[1].get("roles")
        assertEquals(listOf("ADMIN", "MEMBER"), (0 until roles.size()).map { roles[it].asString() })
    }

    @Test
    fun `guards written by updateGuards are read back by validateTransition`() {
        val stored = storedGuardsAfterUpdate(listOf(RequiredFieldGuard("storyPoints")))

        val workflowId = UUID.randomUUID()
        val fromStatusId = UUID.randomUUID()
        val toStatusId = UUID.randomUUID()
        val issue = issueWithWorkflow(workflowId, fromStatusId, storyPoints = null)
        val transition = mockk<WorkflowTransition>()
        every { transition.guards } returns stored
        every {
            transitionRepository.findByWorkflowIdAndFromStatusIdAndToStatusId(workflowId, fromStatusId, toStatusId)
        } returns transition

        // Before the fix the untyped JSON made readValue throw (→ 500); now the guard itself rejects the move.
        val ex = assertThrows<BadRequestException> { service.validateTransition(issue, toStatusId, actor) }
        assertEquals("workflow.transitionFieldRequired", ex.messageKey)
    }
}
