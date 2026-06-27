package com.taskowolf.labels

import com.taskowolf.auth.domain.User
import com.taskowolf.core.infrastructure.ConflictException
import com.taskowolf.core.infrastructure.NotFoundException
import com.taskowolf.labels.api.dto.LabelRequest
import com.taskowolf.labels.application.LabelService
import com.taskowolf.labels.domain.Label
import com.taskowolf.labels.infrastructure.LabelRepository
import com.taskowolf.projects.application.ProjectService
import com.taskowolf.projects.domain.Project
import io.mockk.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Optional
import java.util.UUID

class LabelServiceTest {

    private val labelRepository = mockk<LabelRepository>()
    private val projectService = mockk<ProjectService>()
    private val service = LabelService(labelRepository, projectService)

    private val actor = User(email = "alice@test.com", displayName = "Alice")
    private val project = Project(key = "WOLF", name = "TaskWolf", owner = actor, workflow = null)

    @Test
    fun `list returns labels for project`() {
        val label = Label(name = "bug", color = "#e11d48", project = project)
        every { projectService.requireMember("WOLF", actor.id) } returns project
        every { labelRepository.findByProjectId(project.id) } returns listOf(label)

        val result = service.list("WOLF", actor.id)

        assertEquals(1, result.size)
        assertEquals("bug", result[0].name)
    }

    @Test
    fun `create saves new label`() {
        every { projectService.requireMember("WOLF", actor.id) } returns project
        every { labelRepository.existsByProjectIdAndName(project.id, "bug") } returns false
        every { labelRepository.save(any()) } answers { firstArg() }

        val result = service.create("WOLF", LabelRequest("bug", "#e11d48"), actor)

        assertEquals("bug", result.name)
        assertEquals("#e11d48", result.color)
        verify { labelRepository.save(any()) }
    }

    @Test
    fun `create throws ConflictException when name already exists`() {
        every { projectService.requireMember("WOLF", actor.id) } returns project
        every { labelRepository.existsByProjectIdAndName(project.id, "bug") } returns true

        assertThrows<ConflictException> {
            service.create("WOLF", LabelRequest("bug", "#e11d48"), actor)
        }
    }

    @Test
    fun `update changes name and color`() {
        val label = Label(name = "old", color = "#000000", project = project)
        every { projectService.requireMember("WOLF", actor.id) } returns project
        every { labelRepository.findById(label.id) } returns Optional.of(label)
        every { labelRepository.existsByProjectIdAndName(project.id, "new") } returns false
        every { labelRepository.save(any()) } answers { firstArg() }

        val result = service.update("WOLF", label.id, LabelRequest("new", "#ffffff"), actor)

        assertEquals("new", result.name)
        assertEquals("#ffffff", result.color)
    }

    @Test
    fun `delete removes label`() {
        val label = Label(name = "bug", color = "#e11d48", project = project)
        every { projectService.requireMember("WOLF", actor.id) } returns project
        every { labelRepository.findById(label.id) } returns Optional.of(label)
        every { labelRepository.delete(any()) } just Runs

        service.delete("WOLF", label.id, actor)

        verify { labelRepository.delete(label) }
    }

    @Test
    fun `delete throws NotFoundException when label not in project`() {
        val otherProject = Project(key = "OTHER", name = "Other", owner = actor, workflow = null)
        val label = Label(name = "bug", color = "#e11d48", project = otherProject)
        every { projectService.requireMember("WOLF", actor.id) } returns project
        every { labelRepository.findById(label.id) } returns Optional.of(label)

        assertThrows<NotFoundException> {
            service.delete("WOLF", label.id, actor)
        }
    }
}
