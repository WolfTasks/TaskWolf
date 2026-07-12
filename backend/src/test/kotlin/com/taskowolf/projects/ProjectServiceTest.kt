package com.taskowolf.projects

import com.taskowolf.auth.domain.User
import com.taskowolf.core.infrastructure.ConflictException
import com.taskowolf.core.infrastructure.ForbiddenException
import com.taskowolf.core.infrastructure.NotFoundException
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
    private val userRepository = mockk<com.taskowolf.auth.infrastructure.UserRepository>()
    private val orgLookup = mockk<com.taskowolf.organizations.application.OrgMembershipLookup>()
    private val service = ProjectService(projectRepository, memberRepository, workflowService, userRepository, orgLookup)
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

    @Test
    fun `addMember saves a new member`() {
        val target = User(email = "t@test.com", displayName = "T")
        val project = projectOwnedBy(owner)
        every { projectRepository.findByKey("WOLF") } returns project
        every { memberRepository.existsByProjectIdAndUserId(project.id, owner.id) } returns false
        every { memberRepository.existsByProjectIdAndUserId(project.id, target.id) } returns false
        every { userRepository.findById(target.id) } returns java.util.Optional.of(target)
        every { memberRepository.save(any()) } returnsArgument 0

        val result = service.addMember("WOLF", owner.id, target.id, ProjectRole.VIEWER)

        assertEquals(ProjectRole.VIEWER, result.role)
        verify(exactly = 1) { memberRepository.save(any()) }
    }

    @Test
    fun `addMember throws Conflict when already a member`() {
        val target = User(email = "t2@test.com", displayName = "T2")
        val project = projectOwnedBy(owner)
        every { projectRepository.findByKey("WOLF") } returns project
        every { memberRepository.existsByProjectIdAndUserId(project.id, owner.id) } returns false
        every { memberRepository.existsByProjectIdAndUserId(project.id, target.id) } returns true

        assertThrows<ConflictException> {
            service.addMember("WOLF", owner.id, target.id, ProjectRole.MEMBER)
        }
    }

    @Test
    fun `addMember throws Conflict when target is the owner`() {
        val project = projectOwnedBy(owner)
        every { projectRepository.findByKey("WOLF") } returns project
        every { memberRepository.existsByProjectIdAndUserId(project.id, owner.id) } returns false

        assertThrows<ConflictException> {
            service.addMember("WOLF", owner.id, owner.id, ProjectRole.ADMIN)
        }
    }

    @Test
    fun `addMember throws NotFound when user unknown`() {
        val unknownId = java.util.UUID.randomUUID()
        val project = projectOwnedBy(owner)
        every { projectRepository.findByKey("WOLF") } returns project
        every { memberRepository.existsByProjectIdAndUserId(project.id, owner.id) } returns false
        every { memberRepository.existsByProjectIdAndUserId(project.id, unknownId) } returns false
        every { userRepository.findById(unknownId) } returns java.util.Optional.empty()

        assertThrows<NotFoundException> {
            service.addMember("WOLF", owner.id, unknownId, ProjectRole.MEMBER)
        }
    }

    @Test
    fun `changeMemberRole throws Forbidden for owner target`() {
        val project = projectOwnedBy(owner)
        every { projectRepository.findByKey("WOLF") } returns project
        every { memberRepository.existsByProjectIdAndUserId(project.id, owner.id) } returns false

        assertThrows<ForbiddenException> {
            service.changeMemberRole("WOLF", owner.id, owner.id, ProjectRole.VIEWER)
        }
    }

    @Test
    fun `changeMemberRole updates an existing member`() {
        val target = User(email = "cr@test.com", displayName = "CR")
        val project = projectOwnedBy(owner)
        val member = ProjectMember(project = project, user = target, role = ProjectRole.VIEWER)
        every { projectRepository.findByKey("WOLF") } returns project
        every { memberRepository.existsByProjectIdAndUserId(project.id, owner.id) } returns false
        every { memberRepository.findByProjectIdAndUserId(project.id, target.id) } returns member
        every { memberRepository.save(any()) } returnsArgument 0

        val result = service.changeMemberRole("WOLF", owner.id, target.id, ProjectRole.ADMIN)

        assertEquals(ProjectRole.ADMIN, result.role)
    }

    @Test
    fun `changeMemberRole throws NotFound when target not a member`() {
        val target = User(email = "nm@test.com", displayName = "NM")
        val project = projectOwnedBy(owner)
        every { projectRepository.findByKey("WOLF") } returns project
        every { memberRepository.existsByProjectIdAndUserId(project.id, owner.id) } returns false
        every { memberRepository.findByProjectIdAndUserId(project.id, target.id) } returns null

        assertThrows<NotFoundException> {
            service.changeMemberRole("WOLF", owner.id, target.id, ProjectRole.MEMBER)
        }
    }

    @Test
    fun `removeMember throws Forbidden for owner target`() {
        val project = projectOwnedBy(owner)
        every { projectRepository.findByKey("WOLF") } returns project
        every { memberRepository.existsByProjectIdAndUserId(project.id, owner.id) } returns false

        assertThrows<ForbiddenException> {
            service.removeMember("WOLF", owner.id, owner.id)
        }
    }

    @Test
    fun `removeMember deletes an existing member`() {
        val target = User(email = "rm@test.com", displayName = "RM")
        val project = projectOwnedBy(owner)
        val member = ProjectMember(project = project, user = target, role = ProjectRole.MEMBER)
        every { projectRepository.findByKey("WOLF") } returns project
        every { memberRepository.existsByProjectIdAndUserId(project.id, owner.id) } returns false
        every { memberRepository.findByProjectIdAndUserId(project.id, target.id) } returns member
        every { memberRepository.delete(member) } returns Unit

        service.removeMember("WOLF", owner.id, target.id)

        verify(exactly = 1) { memberRepository.delete(member) }
    }
}
