package com.taskowolf.projects

import com.taskowolf.auth.domain.User
import com.taskowolf.core.infrastructure.ConflictException
import com.taskowolf.projects.api.dto.CreateProjectRequest
import com.taskowolf.projects.application.ProjectService
import com.taskowolf.projects.domain.Project
import com.taskowolf.projects.domain.ProjectMember
import com.taskowolf.projects.domain.ProjectRole
import com.taskowolf.projects.infrastructure.ProjectMemberRepository
import com.taskowolf.projects.infrastructure.ProjectRepository
import com.taskowolf.workflows.application.WorkflowService
import com.taskowolf.workflows.domain.Workflow
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ProjectServiceTest {

    private val projectRepository = mockk<ProjectRepository>()
    private val memberRepository = mockk<ProjectMemberRepository>()
    private val workflowService = mockk<WorkflowService>()
    private val service = ProjectService(projectRepository, memberRepository, workflowService)
    private val owner = User(email = "owner@test.com", displayName = "Owner")

    @Test
    fun `create throws ConflictException when key already exists`() {
        every { projectRepository.existsByKey("WOLF") } returns true

        assertThrows<ConflictException> {
            service.create(CreateProjectRequest("WOLF", "TaskWolf"), owner)
        }
    }

    @Test
    fun `create saves project and creates default workflow`() {
        val mockWorkflow = mockk<Workflow>()
        every { projectRepository.existsByKey("WOLF") } returns false
        every { projectRepository.save(any()) } returnsArgument 0
        every { memberRepository.save(any()) } returnsArgument 0
        every { workflowService.createDefault(any()) } returns mockWorkflow

        service.create(CreateProjectRequest("WOLF", "TaskWolf"), owner)

        verify(exactly = 2) { projectRepository.save(any()) }
        verify(exactly = 1) { memberRepository.save(any()) }
        verify(exactly = 1) { workflowService.createDefault(any()) }
    }

    private fun projectOwnedBy(o: User) =
        com.taskowolf.projects.domain.Project(key = "WOLF", name = "TaskWolf", description = null, owner = o)

    @Test
    fun `roleOf returns ADMIN for owner`() {
        val project = projectOwnedBy(owner)
        assertEquals(ProjectRole.ADMIN, service.roleOf(project, owner.id))
    }

    @Test
    fun `roleOf returns member role for a member`() {
        val member = User(email = "m@test.com", displayName = "M")
        val project = projectOwnedBy(owner)
        every { memberRepository.findByProjectIdAndUserId(project.id, member.id) } returns
            ProjectMember(project = project, user = member, role = ProjectRole.VIEWER)
        assertEquals(ProjectRole.VIEWER, service.roleOf(project, member.id))
    }

    @Test
    fun `roleOf returns null for non-member`() {
        val other = User(email = "o@test.com", displayName = "O")
        val project = projectOwnedBy(owner)
        every { memberRepository.findByProjectIdAndUserId(project.id, other.id) } returns null
        assertNull(service.roleOf(project, other.id))
    }

    @Test
    fun `canWrite is true for owner and false for viewer and non-member`() {
        val viewer = User(email = "v@test.com", displayName = "V")
        val other = User(email = "n@test.com", displayName = "N")
        val project = projectOwnedBy(owner)
        every { projectRepository.findByKey("WOLF") } returns project
        every { memberRepository.findByProjectIdAndUserId(project.id, viewer.id) } returns
            ProjectMember(project = project, user = viewer, role = ProjectRole.VIEWER)
        every { memberRepository.findByProjectIdAndUserId(project.id, other.id) } returns null

        assertTrue(service.canWrite("WOLF", owner.id))
        assertFalse(service.canWrite("WOLF", viewer.id))
        assertFalse(service.canWrite("WOLF", other.id))
    }

    @Test
    fun `canWrite is true for MEMBER role`() {
        val member = User(email = "rw@test.com", displayName = "RW")
        val project = projectOwnedBy(owner)
        every { projectRepository.findByKey("WOLF") } returns project
        every { memberRepository.findByProjectIdAndUserId(project.id, member.id) } returns
            ProjectMember(project = project, user = member, role = ProjectRole.MEMBER)
        assertTrue(service.canWrite("WOLF", member.id))
    }
}
