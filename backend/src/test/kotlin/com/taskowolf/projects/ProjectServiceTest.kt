package com.taskowolf.projects

import com.taskowolf.auth.domain.User
import com.taskowolf.core.infrastructure.ConflictException
import com.taskowolf.projects.api.dto.CreateProjectRequest
import com.taskowolf.projects.application.ProjectService
import com.taskowolf.projects.infrastructure.ProjectMemberRepository
import com.taskowolf.projects.infrastructure.ProjectRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ProjectServiceTest {

    private val projectRepository = mockk<ProjectRepository>()
    private val memberRepository = mockk<ProjectMemberRepository>()
    private val service = ProjectService(projectRepository, memberRepository)
    private val owner = User(email = "owner@test.com", displayName = "Owner")

    @Test
    fun `create throws ConflictException when key already exists`() {
        every { projectRepository.existsByKey("WOLF") } returns true

        assertThrows<ConflictException> {
            service.create(CreateProjectRequest("WOLF", "TaskWolf"), owner)
        }
    }

    @Test
    fun `create saves project and adds owner as ADMIN member`() {
        every { projectRepository.existsByKey("WOLF") } returns false
        every { projectRepository.save(any()) } returnsArgument 0
        every { memberRepository.save(any()) } returnsArgument 0

        service.create(CreateProjectRequest("WOLF", "TaskWolf"), owner)

        verify(exactly = 1) { projectRepository.save(any()) }
        verify(exactly = 1) { memberRepository.save(any()) }
    }
}
