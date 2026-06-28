package com.taskowolf.customfields

import com.taskowolf.auth.domain.User
import com.taskowolf.core.infrastructure.BadRequestException
import com.taskowolf.core.infrastructure.ConflictException
import com.taskowolf.core.infrastructure.NotFoundException
import com.taskowolf.customfields.api.dto.CustomFieldDefinitionRequest
import com.taskowolf.customfields.application.CustomFieldService
import com.taskowolf.customfields.application.ReorderEntry
import com.taskowolf.customfields.domain.CustomFieldDefinition
import com.taskowolf.customfields.domain.FieldType
import com.taskowolf.customfields.infrastructure.CustomFieldDefinitionRepository
import com.taskowolf.customfields.infrastructure.CustomFieldOptionRepository
import com.taskowolf.customfields.infrastructure.CustomFieldValueRepository
import com.taskowolf.projects.application.ProjectService
import com.taskowolf.projects.domain.Project
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Optional
import java.util.UUID

class CustomFieldServiceTest {

    private val definitionRepo = mockk<CustomFieldDefinitionRepository>()
    private val optionRepo = mockk<CustomFieldOptionRepository>()
    private val valueRepo = mockk<CustomFieldValueRepository>()
    private val projectService = mockk<ProjectService>()
    private val service = CustomFieldService(definitionRepo, optionRepo, valueRepo, projectService)

    private val actor = User(email = "alice@test.com", displayName = "Alice")
    private val project = Project(key = "WOLF", name = "TaskWolf", owner = actor, workflow = null)

    @Test
    fun `create saves new field`() {
        every { projectService.requireMember("WOLF", actor.id) } returns project
        every { definitionRepo.existsByProjectIdAndName(project.id, "Severity") } returns false
        every { definitionRepo.save(any()) } answers { firstArg() }

        val result = service.create("WOLF", CustomFieldDefinitionRequest("Severity", FieldType.DROPDOWN, required = false, sortOrder = 0), actor)

        assertEquals("Severity", result.name)
        assertEquals(FieldType.DROPDOWN, result.type)
        verify { definitionRepo.save(any()) }
    }

    @Test
    fun `create throws ConflictException when name already exists`() {
        every { projectService.requireMember("WOLF", actor.id) } returns project
        every { definitionRepo.existsByProjectIdAndName(project.id, "Severity") } returns true

        assertThrows<ConflictException> {
            service.create("WOLF", CustomFieldDefinitionRequest("Severity", FieldType.DROPDOWN), actor)
        }
    }

    @Test
    fun `update changes name and required`() {
        val field = CustomFieldDefinition("Old", FieldType.TEXT, false, 0, project)
        every { projectService.requireMember("WOLF", actor.id) } returns project
        every { definitionRepo.findById(field.id) } returns Optional.of(field)
        every { definitionRepo.existsByProjectIdAndName(project.id, "New") } returns false
        every { definitionRepo.save(any()) } answers { firstArg() }

        val result = service.update("WOLF", field.id, CustomFieldDefinitionRequest("New", FieldType.TEXT, required = true, sortOrder = 0), actor)

        assertEquals("New", result.name)
        assertTrue(result.required)
    }

    @Test
    fun `update throws NotFoundException when field not in project`() {
        val otherProject = Project(key = "OTHER", name = "Other", owner = actor, workflow = null)
        val field = CustomFieldDefinition("Old", FieldType.TEXT, false, 0, otherProject)
        every { projectService.requireMember("WOLF", actor.id) } returns project
        every { definitionRepo.findById(field.id) } returns Optional.of(field)

        assertThrows<NotFoundException> {
            service.update("WOLF", field.id, CustomFieldDefinitionRequest("New", FieldType.TEXT), actor)
        }
    }

    @Test
    fun `reorder updates sortOrder for all listed fields`() {
        val f1 = CustomFieldDefinition("A", FieldType.TEXT, false, 0, project)
        val f2 = CustomFieldDefinition("B", FieldType.TEXT, false, 1, project)
        every { projectService.requireMember("WOLF", actor.id) } returns project
        every { definitionRepo.findAllById(listOf(f1.id, f2.id)) } returns listOf(f1, f2)
        every { definitionRepo.saveAll(any<List<CustomFieldDefinition>>()) } answers { firstArg() }

        service.reorder("WOLF", listOf(ReorderEntry(f1.id, 1), ReorderEntry(f2.id, 0)), actor)

        assertEquals(1, f1.sortOrder)
        assertEquals(0, f2.sortOrder)
    }

    @Test
    fun `delete removes field`() {
        val field = CustomFieldDefinition("X", FieldType.TEXT, false, 0, project)
        every { projectService.requireMember("WOLF", actor.id) } returns project
        every { definitionRepo.findById(field.id) } returns Optional.of(field)
        every { definitionRepo.delete(any()) } just Runs

        service.delete("WOLF", field.id, actor)

        verify { definitionRepo.delete(field) }
    }

    @Test
    fun `getFieldType returns type for known field`() {
        val field = CustomFieldDefinition("X", FieldType.NUMBER, false, 0, project)
        every { definitionRepo.findById(field.id) } returns Optional.of(field)

        assertEquals(FieldType.NUMBER, service.getFieldType(field.id))
    }

    @Test
    fun `getFieldType returns null for unknown field`() {
        val id = UUID.randomUUID()
        every { definitionRepo.findById(id) } returns Optional.empty()

        assertNull(service.getFieldType(id))
    }
}
