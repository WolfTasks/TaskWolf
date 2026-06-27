// backend/src/test/kotlin/com/taskowolf/versions/VersionServiceTest.kt
package com.taskowolf.versions

import com.taskowolf.auth.domain.User
import com.taskowolf.core.infrastructure.ConflictException
import com.taskowolf.core.infrastructure.NotFoundException
import com.taskowolf.projects.application.ProjectService
import com.taskowolf.projects.domain.Project
import com.taskowolf.versions.api.dto.VersionRequest
import com.taskowolf.versions.application.VersionService
import com.taskowolf.versions.domain.Version
import com.taskowolf.versions.infrastructure.VersionRepository
import io.mockk.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Optional
import java.util.UUID

class VersionServiceTest {

    private val versionRepository = mockk<VersionRepository>()
    private val projectService = mockk<ProjectService>()
    private val service = VersionService(versionRepository, projectService)

    private val actor = User(email = "alice@test.com", displayName = "Alice")
    private val project = Project(key = "WOLF", name = "TaskWolf", owner = actor, workflow = null)

    @Test
    fun `list returns versions for project`() {
        val version = Version(name = "v1.0", project = project)
        every { projectService.requireMember("WOLF", actor.id) } returns project
        every { versionRepository.findByProjectId(project.id) } returns listOf(version)

        val result = service.list("WOLF", actor.id)

        assertEquals(1, result.size)
        assertEquals("v1.0", result[0].name)
    }

    @Test
    fun `create saves new version`() {
        every { projectService.requireMember("WOLF", actor.id) } returns project
        every { versionRepository.existsByProjectIdAndName(project.id, "v1.0") } returns false
        every { versionRepository.save(any()) } answers { firstArg() }

        val result = service.create("WOLF", VersionRequest("v1.0"), actor)

        assertEquals("v1.0", result.name)
        verify { versionRepository.save(any()) }
    }

    @Test
    fun `create throws ConflictException when name already exists`() {
        every { projectService.requireMember("WOLF", actor.id) } returns project
        every { versionRepository.existsByProjectIdAndName(project.id, "v1.0") } returns true

        assertThrows<ConflictException> {
            service.create("WOLF", VersionRequest("v1.0"), actor)
        }
    }

    @Test
    fun `update renames version`() {
        val version = Version(name = "v1.0", project = project)
        every { projectService.requireMember("WOLF", actor.id) } returns project
        every { versionRepository.findById(version.id) } returns Optional.of(version)
        every { versionRepository.existsByProjectIdAndName(project.id, "v1.1") } returns false
        every { versionRepository.save(any()) } answers { firstArg() }

        val result = service.update("WOLF", version.id, VersionRequest("v1.1"), actor)

        assertEquals("v1.1", result.name)
    }

    @Test
    fun `update throws ConflictException when new name already exists`() {
        val version = Version(name = "v1.0", project = project)
        every { projectService.requireMember("WOLF", actor.id) } returns project
        every { versionRepository.findById(version.id) } returns Optional.of(version)
        every { versionRepository.existsByProjectIdAndName(project.id, "v1.1") } returns true

        assertThrows<ConflictException> {
            service.update("WOLF", version.id, VersionRequest("v1.1"), actor)
        }
    }

    @Test
    fun `delete removes version`() {
        val version = Version(name = "v1.0", project = project)
        every { projectService.requireMember("WOLF", actor.id) } returns project
        every { versionRepository.findById(version.id) } returns Optional.of(version)
        every { versionRepository.delete(any()) } just Runs

        service.delete("WOLF", version.id, actor)

        verify { versionRepository.delete(version) }
    }

    @Test
    fun `delete throws NotFoundException when version not in project`() {
        val other = Project(key = "OTHER", name = "Other", owner = actor, workflow = null)
        val version = Version(name = "v1.0", project = other)
        every { projectService.requireMember("WOLF", actor.id) } returns project
        every { versionRepository.findById(version.id) } returns Optional.of(version)

        assertThrows<NotFoundException> {
            service.delete("WOLF", version.id, actor)
        }
    }
}
