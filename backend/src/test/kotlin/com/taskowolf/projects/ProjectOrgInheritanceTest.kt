package com.taskowolf.projects

import com.taskowolf.auth.domain.User
import com.taskowolf.auth.infrastructure.UserRepository
import com.taskowolf.organizations.application.OrgMembershipLookup
import com.taskowolf.organizations.domain.OrgRole
import com.taskowolf.projects.application.ProjectService
import com.taskowolf.projects.domain.Project
import com.taskowolf.projects.domain.ProjectMember
import com.taskowolf.projects.domain.ProjectRole
import com.taskowolf.projects.infrastructure.ProjectMemberRepository
import com.taskowolf.projects.infrastructure.ProjectRepository
import com.taskowolf.workflows.application.WorkflowService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

class ProjectOrgInheritanceTest {
    private val projectRepository = mockk<ProjectRepository>()
    private val memberRepository = mockk<ProjectMemberRepository>()
    private val workflowService = mockk<WorkflowService>()
    private val userRepository = mockk<UserRepository>()
    private val orgLookup = mockk<OrgMembershipLookup>()
    private val service = ProjectService(projectRepository, memberRepository, workflowService, userRepository, orgLookup)

    private val owner = User(email = "owner@test.com", displayName = "Owner")
    private val orgId = UUID.randomUUID()

    /** Projekt in einer Org, Owner ist ein anderer User (damit Owner-ADMIN nicht die Matrix verdeckt). */
    private fun orgProject() =
        Project(key = "ORG", name = "Org P", description = null, owner = owner).apply { this.orgId = this@ProjectOrgInheritanceTest.orgId }

    @Test
    fun `org MEMBER inherits VIEWER when no explicit project role`() {
        val u = UUID.randomUUID()
        val project = orgProject()
        every { memberRepository.findByProjectIdAndUserId(project.id, u) } returns null
        every { orgLookup.roleOf(orgId, u) } returns OrgRole.MEMBER
        assertEquals(ProjectRole.VIEWER, service.roleOf(project, u))
    }

    @Test
    fun `org ADMIN inherits project ADMIN`() {
        val u = UUID.randomUUID()
        val project = orgProject()
        every { memberRepository.findByProjectIdAndUserId(project.id, u) } returns null
        every { orgLookup.roleOf(orgId, u) } returns OrgRole.ADMIN
        assertEquals(ProjectRole.ADMIN, service.roleOf(project, u))
    }

    @Test
    fun `explicit project role raises inherited VIEWER to the max`() {
        val u = UUID.randomUUID()
        val project = orgProject()
        every { memberRepository.findByProjectIdAndUserId(project.id, u) } returns
            ProjectMember(project = project, user = User(email = "m@test.com", displayName = "M"), role = ProjectRole.MEMBER)
        every { orgLookup.roleOf(orgId, u) } returns OrgRole.MEMBER
        assertEquals(ProjectRole.MEMBER, service.roleOf(project, u))
    }

    @Test
    fun `non-member of org and project has no access`() {
        val u = UUID.randomUUID()
        val project = orgProject()
        every { memberRepository.findByProjectIdAndUserId(project.id, u) } returns null
        every { orgLookup.roleOf(orgId, u) } returns null
        assertNull(service.roleOf(project, u))
    }

    @Test
    fun `org lookup is not consulted when project has no org`() {
        val u = UUID.randomUUID()
        val project = Project(key = "NO", name = "No Org", description = null, owner = owner) // orgId = null
        every { memberRepository.findByProjectIdAndUserId(project.id, u) } returns null
        // orgLookup NICHT gestubbt → würde bei Aufruf werfen; Test beweist Kurzschluss
        assertNull(service.roleOf(project, u))
    }
}
