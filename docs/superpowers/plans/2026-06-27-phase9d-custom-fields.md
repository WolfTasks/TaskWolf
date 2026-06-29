# Phase 9d — Custom Fields Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add project-scoped custom fields (text, number, date, dropdown, checkbox) that appear on the issue create form and detail page, support required validation, and are filterable via AND-combined JPA Specifications in the issue list.

**Architecture:** A new `com.taskowolf.customfields` Spring module owns field definitions, options, and values. Values are stored with typed columns in `custom_field_values`. Issue filtering is refactored to use JPA `Specification<Issue>` (replacing the if-return-early JPQL query pattern), enabling composable AND-combination of any filter type including custom fields.

**Tech Stack:** Kotlin 2, Spring Boot 3.3, Spring Data JPA + `JpaSpecificationExecutor`, Flyway V25, React 19 + TypeScript, React Query, `@dnd-kit/sortable` (already installed), Tailwind CSS 4.

## Global Constraints

- Backend package root: `com.taskowolf`
- Flyway migration must be named `V25__custom_fields.sql` (V24 is the latest)
- Tests use MockK (`io.mockk`), not Mockito
- Exception classes: `BadRequestException`, `ConflictException`, `NotFoundException` from `com.taskowolf.core.infrastructure`
- Run backend tests: `cd backend && ./gradlew test`
- Run frontend typecheck: `cd frontend && npm run build`
- All entities that need UUID PK without `AuditableEntity` declare `@Id val id: UUID = UUID.randomUUID()` directly
- `AuditableEntity` (used by Label, Version, Issue, Project, etc.) provides `id`, `createdAt`, `updatedAt`
- Frontend routes use `/p/:key/...` prefix (not `/projects/:key/...`)
- Frontend settings nav links are in `frontend/src/layouts/AppLayout.tsx`
- Frontend routes are in `frontend/src/app/router.tsx`
- Frontend settings pages live in `frontend/src/pages/projects/settings/`

---

### Task 1: V25 Migration + `customfields` Module Entities + Repositories

**Files:**
- Create: `backend/src/main/resources/db/migration/V25__custom_fields.sql`
- Create: `backend/src/main/kotlin/com/taskowolf/customfields/domain/FieldType.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/customfields/domain/CustomFieldDefinition.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/customfields/domain/CustomFieldOption.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/customfields/domain/CustomFieldValue.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/customfields/infrastructure/CustomFieldDefinitionRepository.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/customfields/infrastructure/CustomFieldOptionRepository.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/customfields/infrastructure/CustomFieldValueRepository.kt`

**Interfaces:**
- Produces:
  - `FieldType` enum: `TEXT, NUMBER, DATE, DROPDOWN, CHECKBOX`
  - `CustomFieldDefinition` entity (project-scoped, name, type, required, sortOrder)
  - `CustomFieldOption` entity (field-scoped, label, sortOrder)
  - `CustomFieldValue` entity (issue_id UUID + field FK + typed value columns + option FK)
  - `CustomFieldDefinitionRepository.findByProjectIdOrderBySortOrder(projectId)`, `existsByProjectIdAndName`
  - `CustomFieldOptionRepository.findByFieldIdOrderBySortOrder(fieldId)`
  - `CustomFieldValueRepository.findByIssueId(issueId)`, `findByIssueIdAndField_Id(issueId, fieldId)`, `deleteByIssueIdAndField_Id(issueId, fieldId)`

- [ ] **Step 1: Write the migration**

```sql
-- backend/src/main/resources/db/migration/V25__custom_fields.sql
CREATE TABLE custom_field_definitions (
    id         UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    project_id UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name       VARCHAR(100) NOT NULL,
    type       VARCHAR(10)  NOT NULL CHECK (type IN ('TEXT','NUMBER','DATE','DROPDOWN','CHECKBOX')),
    required   BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order INT          NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (project_id, name)
);

CREATE TABLE custom_field_options (
    id         UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    field_id   UUID         NOT NULL REFERENCES custom_field_definitions(id) ON DELETE CASCADE,
    label      VARCHAR(100) NOT NULL,
    sort_order INT          NOT NULL DEFAULT 0,
    UNIQUE (field_id, label)
);

CREATE TABLE custom_field_values (
    id            UUID    NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    issue_id      UUID    NOT NULL REFERENCES issues(id)                   ON DELETE CASCADE,
    field_id      UUID    NOT NULL REFERENCES custom_field_definitions(id) ON DELETE CASCADE,
    text_value    TEXT,
    number_value  NUMERIC,
    date_value    DATE,
    boolean_value BOOLEAN,
    option_id     UUID    REFERENCES custom_field_options(id) ON DELETE SET NULL,
    UNIQUE (issue_id, field_id)
);
```

- [ ] **Step 2: Create `FieldType.kt`**

```kotlin
// backend/src/main/kotlin/com/taskowolf/customfields/domain/FieldType.kt
package com.taskowolf.customfields.domain

enum class FieldType { TEXT, NUMBER, DATE, DROPDOWN, CHECKBOX }
```

- [ ] **Step 3: Create `CustomFieldDefinition.kt`**

```kotlin
// backend/src/main/kotlin/com/taskowolf/customfields/domain/CustomFieldDefinition.kt
package com.taskowolf.customfields.domain

import com.taskowolf.core.domain.AuditableEntity
import com.taskowolf.projects.domain.Project
import jakarta.persistence.*

@Entity
@Table(
    name = "custom_field_definitions",
    uniqueConstraints = [UniqueConstraint(columnNames = ["project_id", "name"])]
)
class CustomFieldDefinition(
    @Column(nullable = false, length = 100)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    val type: FieldType,

    @Column(nullable = false)
    var required: Boolean = false,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    val project: Project
) : AuditableEntity()
```

- [ ] **Step 4: Create `CustomFieldOption.kt`**

```kotlin
// backend/src/main/kotlin/com/taskowolf/customfields/domain/CustomFieldOption.kt
package com.taskowolf.customfields.domain

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(
    name = "custom_field_options",
    uniqueConstraints = [UniqueConstraint(columnNames = ["field_id", "label"])]
)
class CustomFieldOption(
    @Column(nullable = false, length = 100)
    var label: String,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "field_id", nullable = false)
    val field: CustomFieldDefinition
) {
    @Id
    val id: UUID = UUID.randomUUID()
}
```

- [ ] **Step 5: Create `CustomFieldValue.kt`**

```kotlin
// backend/src/main/kotlin/com/taskowolf/customfields/domain/CustomFieldValue.kt
package com.taskowolf.customfields.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(
    name = "custom_field_values",
    uniqueConstraints = [UniqueConstraint(columnNames = ["issue_id", "field_id"])]
)
class CustomFieldValue(
    @Column(name = "issue_id", nullable = false)
    val issueId: UUID,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "field_id", nullable = false)
    val field: CustomFieldDefinition,

    @Column(name = "text_value")
    var textValue: String? = null,

    @Column(name = "number_value", precision = 19, scale = 4)
    var numberValue: BigDecimal? = null,

    @Column(name = "date_value")
    var dateValue: LocalDate? = null,

    @Column(name = "boolean_value")
    var booleanValue: Boolean? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "option_id")
    var option: CustomFieldOption? = null
) {
    @Id
    val id: UUID = UUID.randomUUID()
}
```

- [ ] **Step 6: Create the three repositories**

```kotlin
// CustomFieldDefinitionRepository.kt
package com.taskowolf.customfields.infrastructure

import com.taskowolf.customfields.domain.CustomFieldDefinition
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CustomFieldDefinitionRepository : JpaRepository<CustomFieldDefinition, UUID> {
    fun findByProjectIdOrderBySortOrder(projectId: UUID): List<CustomFieldDefinition>
    fun existsByProjectIdAndName(projectId: UUID, name: String): Boolean
}
```

```kotlin
// CustomFieldOptionRepository.kt
package com.taskowolf.customfields.infrastructure

import com.taskowolf.customfields.domain.CustomFieldOption
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CustomFieldOptionRepository : JpaRepository<CustomFieldOption, UUID> {
    fun findByFieldIdOrderBySortOrder(fieldId: UUID): List<CustomFieldOption>
}
```

```kotlin
// CustomFieldValueRepository.kt
package com.taskowolf.customfields.infrastructure

import com.taskowolf.customfields.domain.CustomFieldValue
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.util.Optional
import java.util.UUID

interface CustomFieldValueRepository : JpaRepository<CustomFieldValue, UUID> {
    fun findByIssueId(issueId: UUID): List<CustomFieldValue>
    fun findByIssueIdAndField_Id(issueId: UUID, fieldId: UUID): Optional<CustomFieldValue>

    @Modifying
    @Transactional
    @Query("DELETE FROM CustomFieldValue cv WHERE cv.issueId = :issueId AND cv.field.id = :fieldId")
    fun deleteByIssueIdAndFieldId(issueId: UUID, fieldId: UUID)
}
```

- [ ] **Step 7: Verify the app starts with the new migration**

Run: `cd backend && ./gradlew bootRun` — should start without Flyway or JPA errors, then Ctrl+C.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/resources/db/migration/V25__custom_fields.sql \
        backend/src/main/kotlin/com/taskowolf/customfields/
git commit -m "feat(customfields): add V25 migration, domain entities, and repositories"
```

---

### Task 2: `CustomFieldService` + `CustomFieldController` + DTOs + Tests

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/customfields/api/dto/CustomFieldDefinitionRequest.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/customfields/api/dto/CustomFieldDefinitionResponse.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/customfields/api/dto/CustomFieldOptionRequest.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/customfields/api/dto/CustomFieldOptionResponse.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/customfields/api/dto/CustomFieldValueResponse.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/customfields/application/CustomFieldService.kt`
- Create: `backend/src/main/kotlin/com/taskowolf/customfields/api/CustomFieldController.kt`
- Create: `backend/src/test/kotlin/com/taskowolf/customfields/CustomFieldServiceTest.kt`

**Interfaces:**
- Consumes: entities and repos from Task 1; `ProjectService.requireMember()`
- Produces:
  - `CustomFieldService.list(projectKey, userId)` → `List<CustomFieldDefinitionResponse>`
  - `CustomFieldService.create(projectKey, request, actor)` → `CustomFieldDefinition`
  - `CustomFieldService.update(projectKey, fieldId, request, actor)` → `CustomFieldDefinition`
  - `CustomFieldService.reorder(projectKey, reorders: List<ReorderEntry>, actor)` — `ReorderEntry` is a local data class `data class ReorderEntry(val id: UUID, val sortOrder: Int)`
  - `CustomFieldService.delete(projectKey, fieldId, actor)`
  - `CustomFieldService.createOption(projectKey, fieldId, request, actor)` → `CustomFieldOption`
  - `CustomFieldService.updateOption(projectKey, fieldId, optId, request, actor)` → `CustomFieldOption`
  - `CustomFieldService.deleteOption(projectKey, fieldId, optId, actor)`
  - `CustomFieldService.getValuesForIssue(projectId, issueId)` → `List<CustomFieldValueResponse>` (used by IssueController in Task 3)
  - `CustomFieldService.getFieldType(fieldId)` → `FieldType?` (used by IssueService in Task 4)
  - REST endpoints at `/api/v1/projects/{key}/custom-fields` (see spec)

- [ ] **Step 1: Write the failing tests first**

```kotlin
// backend/src/test/kotlin/com/taskowolf/customfields/CustomFieldServiceTest.kt
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
```

- [ ] **Step 2: Run tests to confirm they fail**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.customfields.CustomFieldServiceTest"`
Expected: compilation error — `CustomFieldService`, `ReorderEntry` do not exist yet.

- [ ] **Step 3: Create the DTOs**

```kotlin
// CustomFieldDefinitionRequest.kt
package com.taskowolf.customfields.api.dto

import com.taskowolf.customfields.domain.FieldType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CustomFieldDefinitionRequest(
    @field:NotBlank @field:Size(max = 100) val name: String,
    val type: FieldType,
    val required: Boolean = false,
    val sortOrder: Int = 0
)
```

```kotlin
// CustomFieldOptionRequest.kt
package com.taskowolf.customfields.api.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CustomFieldOptionRequest(
    @field:NotBlank @field:Size(max = 100) val label: String,
    val sortOrder: Int = 0
)
```

```kotlin
// CustomFieldOptionResponse.kt
package com.taskowolf.customfields.api.dto

import com.taskowolf.customfields.domain.CustomFieldOption
import java.util.UUID

data class CustomFieldOptionResponse(val id: UUID, val label: String, val sortOrder: Int) {
    companion object {
        fun from(o: CustomFieldOption) = CustomFieldOptionResponse(o.id, o.label, o.sortOrder)
    }
}
```

```kotlin
// CustomFieldDefinitionResponse.kt
package com.taskowolf.customfields.api.dto

import com.taskowolf.customfields.domain.CustomFieldDefinition
import com.taskowolf.customfields.domain.CustomFieldOption
import java.util.UUID

data class CustomFieldDefinitionResponse(
    val id: UUID,
    val name: String,
    val type: String,
    val required: Boolean,
    val sortOrder: Int,
    val options: List<CustomFieldOptionResponse> = emptyList()
) {
    companion object {
        fun from(d: CustomFieldDefinition, options: List<CustomFieldOption> = emptyList()) =
            CustomFieldDefinitionResponse(d.id, d.name, d.type.name, d.required, d.sortOrder,
                options.map { CustomFieldOptionResponse.from(it) })
    }
}
```

```kotlin
// CustomFieldValueResponse.kt
package com.taskowolf.customfields.api.dto

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class CustomFieldValueResponse(
    val fieldId: UUID,
    val fieldName: String,
    val type: String,
    val required: Boolean,
    val textValue: String? = null,
    val numberValue: BigDecimal? = null,
    val dateValue: LocalDate? = null,
    val booleanValue: Boolean? = null,
    val optionId: UUID? = null,
    val optionLabel: String? = null
)
```

- [ ] **Step 4: Create `ReorderEntry` and `CustomFieldService`**

```kotlin
// backend/src/main/kotlin/com/taskowolf/customfields/application/CustomFieldService.kt
package com.taskowolf.customfields.application

import com.taskowolf.auth.domain.User
import com.taskowolf.core.infrastructure.ConflictException
import com.taskowolf.core.infrastructure.NotFoundException
import com.taskowolf.customfields.api.dto.CustomFieldDefinitionRequest
import com.taskowolf.customfields.api.dto.CustomFieldDefinitionResponse
import com.taskowolf.customfields.api.dto.CustomFieldOptionRequest
import com.taskowolf.customfields.api.dto.CustomFieldValueResponse
import com.taskowolf.customfields.domain.CustomFieldDefinition
import com.taskowolf.customfields.domain.CustomFieldOption
import com.taskowolf.customfields.infrastructure.CustomFieldDefinitionRepository
import com.taskowolf.customfields.infrastructure.CustomFieldOptionRepository
import com.taskowolf.customfields.infrastructure.CustomFieldValueRepository
import com.taskowolf.customfields.domain.FieldType
import com.taskowolf.projects.application.ProjectService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class ReorderEntry(val id: UUID, val sortOrder: Int)

@Service
class CustomFieldService(
    private val definitionRepo: CustomFieldDefinitionRepository,
    private val optionRepo: CustomFieldOptionRepository,
    private val valueRepo: CustomFieldValueRepository,
    private val projectService: ProjectService
) {
    @Transactional(readOnly = true)
    fun list(projectKey: String, userId: UUID): List<CustomFieldDefinitionResponse> {
        val project = projectService.requireMember(projectKey, userId)
        val defs = definitionRepo.findByProjectIdOrderBySortOrder(project.id)
        return defs.map { d ->
            val options = if (d.type == FieldType.DROPDOWN) optionRepo.findByFieldIdOrderBySortOrder(d.id) else emptyList()
            CustomFieldDefinitionResponse.from(d, options)
        }
    }

    @Transactional
    fun create(projectKey: String, request: CustomFieldDefinitionRequest, actor: User): CustomFieldDefinition {
        val project = projectService.requireMember(projectKey, actor.id)
        if (definitionRepo.existsByProjectIdAndName(project.id, request.name)) {
            throw ConflictException("Custom field '${request.name}' already exists in this project")
        }
        return definitionRepo.save(
            CustomFieldDefinition(request.name, request.type, request.required, request.sortOrder, project)
        )
    }

    @Transactional
    fun update(projectKey: String, fieldId: UUID, request: CustomFieldDefinitionRequest, actor: User): CustomFieldDefinition {
        val project = projectService.requireMember(projectKey, actor.id)
        val field = definitionRepo.findById(fieldId)
            .filter { it.project.id == project.id }
            .orElseThrow { NotFoundException("Custom field not found: $fieldId") }
        if (field.name != request.name && definitionRepo.existsByProjectIdAndName(project.id, request.name)) {
            throw ConflictException("Custom field '${request.name}' already exists in this project")
        }
        field.name = request.name
        field.required = request.required
        field.sortOrder = request.sortOrder
        return definitionRepo.save(field)
    }

    @Transactional
    fun reorder(projectKey: String, reorders: List<ReorderEntry>, actor: User) {
        val project = projectService.requireMember(projectKey, actor.id)
        val fields = definitionRepo.findAllById(reorders.map { it.id })
            .filter { it.project.id == project.id }
        val orderMap = reorders.associateBy { it.id }
        fields.forEach { it.sortOrder = orderMap[it.id]?.sortOrder ?: it.sortOrder }
        definitionRepo.saveAll(fields)
    }

    @Transactional
    fun delete(projectKey: String, fieldId: UUID, actor: User) {
        val project = projectService.requireMember(projectKey, actor.id)
        val field = definitionRepo.findById(fieldId)
            .filter { it.project.id == project.id }
            .orElseThrow { NotFoundException("Custom field not found: $fieldId") }
        definitionRepo.delete(field)
    }

    @Transactional
    fun createOption(projectKey: String, fieldId: UUID, request: CustomFieldOptionRequest, actor: User): CustomFieldOption {
        val project = projectService.requireMember(projectKey, actor.id)
        val field = definitionRepo.findById(fieldId)
            .filter { it.project.id == project.id }
            .orElseThrow { NotFoundException("Custom field not found: $fieldId") }
        if (optionRepo.findByFieldIdOrderBySortOrder(field.id).any { it.label.equals(request.label, ignoreCase = false) }) {
            throw ConflictException("Option '${request.label}' already exists for this field")
        }
        return optionRepo.save(CustomFieldOption(request.label, request.sortOrder, field))
    }

    @Transactional
    fun updateOption(projectKey: String, fieldId: UUID, optId: UUID, request: CustomFieldOptionRequest, actor: User): CustomFieldOption {
        val project = projectService.requireMember(projectKey, actor.id)
        val field = definitionRepo.findById(fieldId)
            .filter { it.project.id == project.id }
            .orElseThrow { NotFoundException("Custom field not found: $fieldId") }
        val option = optionRepo.findById(optId)
            .filter { it.field.id == field.id }
            .orElseThrow { NotFoundException("Option not found: $optId") }
        option.label = request.label
        option.sortOrder = request.sortOrder
        return optionRepo.save(option)
    }

    @Transactional
    fun deleteOption(projectKey: String, fieldId: UUID, optId: UUID, actor: User) {
        val project = projectService.requireMember(projectKey, actor.id)
        val field = definitionRepo.findById(fieldId)
            .filter { it.project.id == project.id }
            .orElseThrow { NotFoundException("Custom field not found: $fieldId") }
        val option = optionRepo.findById(optId)
            .filter { it.field.id == field.id }
            .orElseThrow { NotFoundException("Option not found: $optId") }
        optionRepo.delete(option)
    }

    @Transactional(readOnly = true)
    fun getValuesForIssue(projectId: UUID, issueId: UUID): List<CustomFieldValueResponse> {
        val defs = definitionRepo.findByProjectIdOrderBySortOrder(projectId)
        val valuesByFieldId = valueRepo.findByIssueId(issueId).associateBy { it.field.id }
        return defs.map { d ->
            val v = valuesByFieldId[d.id]
            CustomFieldValueResponse(
                fieldId = d.id,
                fieldName = d.name,
                type = d.type.name,
                required = d.required,
                textValue = v?.textValue,
                numberValue = v?.numberValue,
                dateValue = v?.dateValue,
                booleanValue = v?.booleanValue,
                optionId = v?.option?.id,
                optionLabel = v?.option?.label
            )
        }
    }

    fun getFieldType(fieldId: UUID): FieldType? =
        definitionRepo.findById(fieldId).map { it.type }.orElse(null)
}
```

- [ ] **Step 5: Create `CustomFieldController`**

```kotlin
// backend/src/main/kotlin/com/taskowolf/customfields/api/CustomFieldController.kt
package com.taskowolf.customfields.api

import com.taskowolf.auth.domain.User
import com.taskowolf.customfields.api.dto.CustomFieldDefinitionRequest
import com.taskowolf.customfields.api.dto.CustomFieldDefinitionResponse
import com.taskowolf.customfields.api.dto.CustomFieldOptionRequest
import com.taskowolf.customfields.api.dto.CustomFieldOptionResponse
import com.taskowolf.customfields.application.CustomFieldService
import com.taskowolf.customfields.application.ReorderEntry
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/projects/{key}/custom-fields")
class CustomFieldController(private val service: CustomFieldService) {

    @GetMapping
    fun list(@PathVariable key: String, @AuthenticationPrincipal user: User) =
        service.list(key, user.id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @PathVariable key: String,
        @Valid @RequestBody request: CustomFieldDefinitionRequest,
        @AuthenticationPrincipal user: User
    ): CustomFieldDefinitionResponse {
        val field = service.create(key, request, user)
        return CustomFieldDefinitionResponse.from(field)
    }

    @PutMapping("/{id}")
    fun update(
        @PathVariable key: String,
        @PathVariable id: UUID,
        @Valid @RequestBody request: CustomFieldDefinitionRequest,
        @AuthenticationPrincipal user: User
    ): CustomFieldDefinitionResponse {
        val field = service.update(key, id, request, user)
        return CustomFieldDefinitionResponse.from(field)
    }

    @PutMapping("/reorder")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun reorder(
        @PathVariable key: String,
        @RequestBody reorders: List<ReorderEntry>,
        @AuthenticationPrincipal user: User
    ) = service.reorder(key, reorders, user)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable key: String,
        @PathVariable id: UUID,
        @AuthenticationPrincipal user: User
    ) = service.delete(key, id, user)

    @PostMapping("/{id}/options")
    @ResponseStatus(HttpStatus.CREATED)
    fun createOption(
        @PathVariable key: String,
        @PathVariable id: UUID,
        @Valid @RequestBody request: CustomFieldOptionRequest,
        @AuthenticationPrincipal user: User
    ) = CustomFieldOptionResponse.from(service.createOption(key, id, request, user))

    @PutMapping("/{id}/options/{optId}")
    fun updateOption(
        @PathVariable key: String,
        @PathVariable id: UUID,
        @PathVariable optId: UUID,
        @Valid @RequestBody request: CustomFieldOptionRequest,
        @AuthenticationPrincipal user: User
    ) = CustomFieldOptionResponse.from(service.updateOption(key, id, optId, request, user))

    @DeleteMapping("/{id}/options/{optId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteOption(
        @PathVariable key: String,
        @PathVariable id: UUID,
        @PathVariable optId: UUID,
        @AuthenticationPrincipal user: User
    ) = service.deleteOption(key, id, optId, user)
}
```

- [ ] **Step 6: Run the tests**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.customfields.CustomFieldServiceTest"`
Expected: All 8 tests PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/customfields/ \
        backend/src/test/kotlin/com/taskowolf/customfields/
git commit -m "feat(customfields): add CustomFieldService, controller, DTOs, and service tests"
```

---

### Task 3: Issue Backend Integration

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/issues/api/dto/CustomFieldValueInput.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/issues/api/dto/CreateIssueRequest.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/issues/api/dto/UpdateIssueRequest.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/issues/api/dto/IssueResponse.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/issues/application/IssueService.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/issues/api/IssueController.kt`
- Modify: `backend/src/test/kotlin/com/taskowolf/issues/IssueServiceTest.kt`

**Interfaces:**
- Consumes: `CustomFieldDefinitionRepository`, `CustomFieldOptionRepository`, `CustomFieldValueRepository`, `CustomFieldService.getValuesForIssue()` from Tasks 1–2
- Produces:
  - `IssueResponse.customFields: List<CustomFieldValueResponse>` (populated on single-GET only)
  - `IssueService.create()` accepts `customFieldValues` and validates required fields
  - `IssueService.update()` accepts `customFieldValues` and upserts values

- [ ] **Step 1: Create `CustomFieldValueInput.kt`**

```kotlin
// backend/src/main/kotlin/com/taskowolf/issues/api/dto/CustomFieldValueInput.kt
package com.taskowolf.issues.api.dto

import java.util.UUID

data class CustomFieldValueInput(
    val fieldId: UUID,
    val value: String?  // null = clear the value
)
```

- [ ] **Step 2: Extend `CreateIssueRequest.kt`**

Add to the existing data class:
```kotlin
val customFieldValues: List<CustomFieldValueInput>? = null
```

The full file becomes:
```kotlin
package com.taskowolf.issues.api.dto

import com.taskowolf.issues.domain.IssuePriority
import com.taskowolf.issues.domain.IssueType
import jakarta.validation.constraints.NotBlank
import java.util.UUID

data class CreateIssueRequest(
    @field:NotBlank val title: String,
    val description: String? = null,
    val type: IssueType = IssueType.TASK,
    val priority: IssuePriority = IssuePriority.MEDIUM,
    val assigneeId: UUID? = null,
    val parentId: UUID? = null,
    val storyPoints: Int? = null,
    val customFieldValues: List<CustomFieldValueInput>? = null
)
```

- [ ] **Step 3: Extend `UpdateIssueRequest.kt`**

Add at the end of the existing data class:
```kotlin
val customFieldValues: List<CustomFieldValueInput>? = null  // null = no change
```

- [ ] **Step 4: Extend `IssueResponse.kt`**

Add to the data class and its `from()` companion:

```kotlin
// Add to IssueResponse data class fields (at the end, with default):
val customFields: List<com.taskowolf.customfields.api.dto.CustomFieldValueResponse> = emptyList()

// Update from() signature and body:
fun from(
    i: Issue,
    refs: List<IssueRefResponse> = emptyList(),
    labels: List<LabelResponse> = emptyList(),
    fixVersions: List<VersionResponse> = emptyList(),
    affectsVersions: List<VersionResponse> = emptyList(),
    customFields: List<com.taskowolf.customfields.api.dto.CustomFieldValueResponse> = emptyList()
) = IssueResponse(
    // ... all existing fields unchanged ...
    customFields = customFields
)
```

- [ ] **Step 5: Add private helper to `IssueService` and update `create()` / `update()`**

Add three new constructor parameters to `IssueService`:
```kotlin
private val customFieldDefinitionRepository: com.taskowolf.customfields.infrastructure.CustomFieldDefinitionRepository,
private val customFieldOptionRepository: com.taskowolf.customfields.infrastructure.CustomFieldOptionRepository,
private val customFieldValueRepository: com.taskowolf.customfields.infrastructure.CustomFieldValueRepository
```

Add private helper method (add before the existing `resolveAssignee`):
```kotlin
private fun applyCustomFieldValues(
    issueId: UUID,
    inputs: List<CustomFieldValueInput>,
    projectId: UUID
) {
    val definitions = customFieldDefinitionRepository
        .findByProjectIdOrderBySortOrder(projectId)
        .associateBy { it.id }

    for (input in inputs) {
        val definition = definitions[input.fieldId] ?: continue

        if (input.value == null) {
            customFieldValueRepository.deleteByIssueIdAndFieldId(issueId, definition.id)
            continue
        }

        val cfv = customFieldValueRepository
            .findByIssueIdAndField_Id(issueId, definition.id)
            .orElse(com.taskowolf.customfields.domain.CustomFieldValue(issueId = issueId, field = definition))

        when (definition.type) {
            com.taskowolf.customfields.domain.FieldType.TEXT -> cfv.textValue = input.value
            com.taskowolf.customfields.domain.FieldType.NUMBER -> cfv.numberValue = input.value.toBigDecimalOrNull()
                ?: throw com.taskowolf.core.infrastructure.BadRequestException("Invalid number for field '${definition.name}': ${input.value}")
            com.taskowolf.customfields.domain.FieldType.DATE -> cfv.dateValue = runCatching {
                java.time.LocalDate.parse(input.value)
            }.getOrElse {
                throw com.taskowolf.core.infrastructure.BadRequestException("Invalid date for field '${definition.name}': ${input.value}")
            }
            com.taskowolf.customfields.domain.FieldType.CHECKBOX ->
                cfv.booleanValue = input.value.equals("true", ignoreCase = true)
            com.taskowolf.customfields.domain.FieldType.DROPDOWN -> {
                val optId = runCatching { UUID.fromString(input.value) }.getOrElse {
                    throw com.taskowolf.core.infrastructure.BadRequestException("Invalid option ID for field '${definition.name}'")
                }
                cfv.option = customFieldOptionRepository.findById(optId)
                    .filter { it.field.id == definition.id }
                    .orElseThrow { com.taskowolf.core.infrastructure.BadRequestException("Option not found: $optId") }
            }
        }
        customFieldValueRepository.save(cfv)
    }
}

private fun validateRequiredCustomFields(projectId: UUID, inputs: List<CustomFieldValueInput>?) {
    val requiredFields = customFieldDefinitionRepository
        .findByProjectIdOrderBySortOrder(projectId)
        .filter { it.required }
    val providedMap = inputs?.associateBy { it.fieldId } ?: emptyMap()
    requiredFields.forEach { field ->
        val input = providedMap[field.id]
        if (input == null || input.value == null || input.value.isBlank()) {
            throw com.taskowolf.core.infrastructure.BadRequestException("Required custom field '${field.name}' must have a value")
        }
    }
}
```

In `create()`, after the `issueRepository.save(...)` call and before the `eventPublisher.publish(...)`:
```kotlin
validateRequiredCustomFields(project.id, request.customFieldValues)
request.customFieldValues?.let { applyCustomFieldValues(issue.id, it, project.id) }
```

In `update()`, at the end of the method before `return issueRepository.save(issue)`, add:
```kotlin
request.customFieldValues?.let { applyCustomFieldValues(issue.id, it, project.id) }
```

Note: `update()` does NOT re-validate required fields (partial updates are allowed — only the provided fields are changed).

- [ ] **Step 6: Update `IssueController.get()` to load custom fields**

Add `customFieldService: com.taskowolf.customfields.application.CustomFieldService` to the `IssueController` constructor.

In `get()`, add after loading `affectsVersions`:
```kotlin
val customFields = customFieldService.getValuesForIssue(issue.project.id, issue.id)
```

Update the return statement:
```kotlin
return IssueResponse.from(issue, refs, labels, fixVersions, affectsVersions, customFields)
```

- [ ] **Step 7: Update `IssueServiceTest` for new constructor parameters**

Add mocks and update the service instantiation:
```kotlin
private val customFieldDefinitionRepository = mockk<com.taskowolf.customfields.infrastructure.CustomFieldDefinitionRepository>()
private val customFieldOptionRepository = mockk<com.taskowolf.customfields.infrastructure.CustomFieldOptionRepository>()
private val customFieldValueRepository = mockk<com.taskowolf.customfields.infrastructure.CustomFieldValueRepository>()

// Update service instantiation:
private val service = IssueService(
    issueRepository, projectService, workflowService, userRepository, eventPublisher,
    sprintRepository, labelRepository, versionRepository, issueVersionRepository,
    customFieldDefinitionRepository, customFieldOptionRepository, customFieldValueRepository
)
```

In each existing test that calls `service.create(...)`, add a stub so the required-field validation doesn't fail:
```kotlin
every { customFieldDefinitionRepository.findByProjectIdOrderBySortOrder(project.id) } returns emptyList()
```

- [ ] **Step 8: Run tests**

Run: `cd backend && ./gradlew test --tests "com.taskowolf.issues.IssueServiceTest"`
Expected: All existing tests PASS.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/issues/ \
        backend/src/test/kotlin/com/taskowolf/issues/IssueServiceTest.kt
git commit -m "feat(customfields): wire custom field values into issue create, update, and single-GET"
```

---

### Task 4: JPA Specification Filter

**Files:**
- Create: `backend/src/main/kotlin/com/taskowolf/issues/infrastructure/IssueSpecification.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/issues/infrastructure/IssueRepository.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/issues/application/IssueService.kt`
- Modify: `backend/src/main/kotlin/com/taskowolf/issues/api/IssueController.kt`

**Interfaces:**
- Consumes: `FieldType` and `CustomFieldValue` from Task 1; `CustomFieldDefinitionRepository.findById()` from Task 1; `IssueVersion` from `com.taskowolf.versions.domain`
- Produces:
  - `IssueRepository` implements `JpaSpecificationExecutor<Issue>` — exposes `findAll(spec, pageable)`
  - `IssueSpecification` object with static factory methods: `inProject`, `assignedTo`, `overdue`, `hasLabel`, `hasFixVersion`, `hasAffectsVersion`, `hasCustomFieldValue`
  - `IssueService.findByProject()` accepts `customFieldFilters: Map<UUID, String> = emptyMap()` and uses Specifications
  - `IssueController.list()` accepts repeated `@RequestParam("cf") cf: List<String>?` in `"fieldId:value"` format

- [ ] **Step 1: Create `IssueSpecification.kt`**

```kotlin
// backend/src/main/kotlin/com/taskowolf/issues/infrastructure/IssueSpecification.kt
package com.taskowolf.issues.infrastructure

import com.taskowolf.customfields.domain.CustomFieldValue
import com.taskowolf.customfields.domain.FieldType
import com.taskowolf.issues.domain.Issue
import com.taskowolf.versions.domain.IssueVersion
import com.taskowolf.workflows.domain.StatusCategory
import jakarta.persistence.criteria.*
import org.springframework.data.jpa.domain.Specification
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

object IssueSpecification {

    fun inProject(projectId: UUID): Specification<Issue> =
        Specification { root, _, cb ->
            cb.equal(root.get<Any>("project").get<UUID>("id"), projectId)
        }

    fun assignedTo(userId: UUID): Specification<Issue> =
        Specification { root, _, cb ->
            cb.equal(root.get<Any>("assignee").get<UUID>("id"), userId)
        }

    fun overdue(): Specification<Issue> =
        Specification { root, _, cb ->
            cb.and(
                cb.lessThan(root.get("dueDate"), LocalDate.now()),
                cb.notEqual(root.get<Any>("status").get<StatusCategory>("category"), StatusCategory.DONE)
            )
        }

    fun hasLabel(labelId: UUID): Specification<Issue> =
        Specification { root, query, cb ->
            query!!.distinct(true)
            val labelJoin = root.join<Issue, Any>("labels", JoinType.INNER)
            cb.equal(labelJoin.get<UUID>("id"), labelId)
        }

    fun hasFixVersion(versionId: UUID): Specification<Issue> =
        Specification { root, query, cb ->
            val sub = query!!.subquery(Long::class.java)
            val iv = sub.from(IssueVersion::class.java)
            sub.select(cb.literal(1L)).where(
                cb.equal(iv.get<UUID>("issueId"), root.get<UUID>("id")),
                cb.equal(iv.get<UUID>("versionId"), versionId),
                cb.equal(iv.get<String>("type"), "FIX")
            )
            cb.exists(sub)
        }

    fun hasAffectsVersion(versionId: UUID): Specification<Issue> =
        Specification { root, query, cb ->
            val sub = query!!.subquery(Long::class.java)
            val iv = sub.from(IssueVersion::class.java)
            sub.select(cb.literal(1L)).where(
                cb.equal(iv.get<UUID>("issueId"), root.get<UUID>("id")),
                cb.equal(iv.get<UUID>("versionId"), versionId),
                cb.equal(iv.get<String>("type"), "AFFECTS")
            )
            cb.exists(sub)
        }

    fun hasCustomFieldValue(fieldId: UUID, rawValue: String, fieldType: FieldType): Specification<Issue> =
        Specification { root, query, cb ->
            val sub = query!!.subquery(Long::class.java)
            val cfv = sub.from(CustomFieldValue::class.java)
            val fieldJoin = cfv.join<Any, Any>("field", JoinType.INNER)

            val issueIdMatch = cb.equal(cfv.get<UUID>("issueId"), root.get<UUID>("id"))
            val fieldIdMatch = cb.equal(fieldJoin.get<UUID>("id"), fieldId)

            val valueMatch: Predicate = when (fieldType) {
                FieldType.TEXT -> cb.like(cb.lower(cfv.get("textValue")), "%${rawValue.lowercase()}%")
                FieldType.NUMBER -> rawValue.toBigDecimalOrNull()?.let { num ->
                    cb.equal(cfv.get<BigDecimal>("numberValue"), num)
                } ?: cb.conjunction()
                FieldType.DATE -> runCatching { LocalDate.parse(rawValue) }.getOrNull()?.let { date ->
                    cb.equal(cfv.get<LocalDate>("dateValue"), date)
                } ?: cb.conjunction()
                FieldType.CHECKBOX -> cb.equal(
                    cfv.get<Boolean>("booleanValue"),
                    rawValue.equals("true", ignoreCase = true)
                )
                FieldType.DROPDOWN -> runCatching { UUID.fromString(rawValue) }.getOrNull()?.let { optId ->
                    val optJoin = cfv.join<Any, Any>("option", JoinType.INNER)
                    cb.equal(optJoin.get<UUID>("id"), optId)
                } ?: cb.conjunction()
            }

            sub.select(cb.literal(1L)).where(issueIdMatch, fieldIdMatch, valueMatch)
            cb.exists(sub)
        }
}
```

- [ ] **Step 2: Extend `IssueRepository`**

Add `JpaSpecificationExecutor<Issue>` to the interface declaration:

```kotlin
interface IssueRepository : JpaRepository<Issue, UUID>, JpaSpecificationExecutor<Issue> {
    // ... all existing methods unchanged ...
}
```

Add the import: `import org.springframework.data.jpa.repository.JpaSpecificationExecutor`

- [ ] **Step 3: Refactor `IssueService.findByProject()` to use Specifications**

Replace the entire `findByProject()` method body. First add `customFieldFilters` parameter:

```kotlin
@Transactional(readOnly = true)
fun findByProject(
    projectKey: String,
    userId: UUID,
    page: Int,
    size: Int,
    assigneeMe: Boolean = false,
    sort: String? = null,
    overdue: Boolean = false,
    labelId: UUID? = null,
    fixVersionId: UUID? = null,
    affectsVersionId: UUID? = null,
    customFieldFilters: Map<UUID, String> = emptyMap()
): org.springframework.data.domain.Page<Issue> {
    val project = projectService.requireMember(projectKey, userId)

    val pageable = when {
        overdue -> PageRequest.of(page, size, org.springframework.data.domain.Sort.by("dueDate").ascending())
        sort == "updatedAt" -> PageRequest.of(page, size, org.springframework.data.domain.Sort.by("updatedAt").descending())
        else -> PageRequest.of(page, size)
    }

    var spec = IssueSpecification.inProject(project.id)
    if (assigneeMe) spec = spec.and(IssueSpecification.assignedTo(userId))
    if (overdue) spec = spec.and(IssueSpecification.overdue())
    if (labelId != null) spec = spec.and(IssueSpecification.hasLabel(labelId))
    if (fixVersionId != null) spec = spec.and(IssueSpecification.hasFixVersion(fixVersionId))
    if (affectsVersionId != null) spec = spec.and(IssueSpecification.hasAffectsVersion(affectsVersionId))

    for ((fieldId, rawValue) in customFieldFilters) {
        val fieldType = customFieldDefinitionRepository.findById(fieldId).map { it.type }.orElse(null) ?: continue
        spec = spec.and(IssueSpecification.hasCustomFieldValue(fieldId, rawValue, fieldType))
    }

    return issueRepository.findAll(spec, pageable)
}
```

Remove the old import: `import org.springframework.data.domain.PageRequest` (it's still needed, keep it).
Add import: `import com.taskowolf.issues.infrastructure.IssueSpecification`

- [ ] **Step 4: Update `IssueController.list()` to accept `cf` params**

Add to the `list()` method signature:
```kotlin
@RequestParam(value = "cf", required = false) cf: List<String>?
```

Parse and forward to service:
```kotlin
val customFieldFilters: Map<UUID, String> = cf?.mapNotNull { param ->
    val colonIdx = param.indexOf(':')
    if (colonIdx < 0) return@mapNotNull null
    runCatching { UUID.fromString(param.substring(0, colonIdx)) to param.substring(colonIdx + 1) }.getOrNull()
}?.toMap() ?: emptyMap()

return issueService.findByProject(key, user.id, page, size, assigneeMe, sort, overdue,
    labelId, fixVersionId, affectsVersionId, customFieldFilters)
    .map { IssueResponse.from(it) }
```

- [ ] **Step 5: Update `IssueServiceTest` for the new `findByProject` signature**

Any test that calls or mocks `issueRepository.findAllByProjectId(...)` should be updated to mock `issueRepository.findAll(any<org.springframework.data.jpa.domain.Specification<Issue>>(), any())`.

Example: if there is a test for `findByProject`, update:
```kotlin
every { issueRepository.findAll(any<org.springframework.data.jpa.domain.Specification<Issue>>(), any<org.springframework.data.domain.Pageable>()) } returns PageImpl(listOf(issue))
```

- [ ] **Step 6: Run all backend tests**

Run: `cd backend && ./gradlew test`
Expected: ALL tests PASS. Fix any failures before continuing.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/kotlin/com/taskowolf/issues/
git commit -m "feat(customfields): add JPA Specification filter; refactor findByProject to composable specs"
```

---

### Task 5: Frontend — Types + API + Hooks

**Files:**
- Modify: `frontend/src/types/index.ts`
- Create: `frontend/src/api/customFields.ts`
- Create: `frontend/src/hooks/useCustomFields.ts`
- Modify: `frontend/src/api/issues.ts`
- Modify: `frontend/src/hooks/useIssues.ts`

**Interfaces:**
- Produces:
  - `CustomFieldDefinition`, `CustomFieldOption`, `CustomFieldValue` types
  - `Issue.customFields?: CustomFieldValue[]`
  - `customFieldsApi.listDefinitions`, `.createDefinition`, `.updateDefinition`, `.reorder`, `.deleteDefinition`, `.createOption`, `.updateOption`, `.deleteOption`
  - `useCustomFields(projectKey)` → `{ data: CustomFieldDefinition[] }`
  - `useCreateCustomField`, `useUpdateCustomField`, `useDeleteCustomField`, `useReorderCustomFields`
  - `useCreateOption`, `useUpdateOption`, `useDeleteOption`
  - `issuesApi.list()` accepts `customFieldFilters?: Record<string, string>` → appends `cf=fieldId:value` params
  - `useIssues(projectKey, opts)` accepts `customFieldFilters?: Record<string, string>`

- [ ] **Step 1: Add types to `types/index.ts`**

After the `Version` interface, add:

```typescript
export interface CustomFieldOption {
  id: string
  label: string
  sortOrder: number
}

export interface CustomFieldDefinition {
  id: string
  name: string
  type: 'TEXT' | 'NUMBER' | 'DATE' | 'DROPDOWN' | 'CHECKBOX'
  required: boolean
  sortOrder: number
  options?: CustomFieldOption[]
}

export interface CustomFieldValue {
  fieldId: string
  fieldName: string
  type: string
  required: boolean
  textValue?: string
  numberValue?: number
  dateValue?: string
  booleanValue?: boolean
  optionId?: string
  optionLabel?: string
}
```

In the `Issue` interface, add at the end:
```typescript
customFields?: CustomFieldValue[]
```

- [ ] **Step 2: Create `api/customFields.ts`**

```typescript
// frontend/src/api/customFields.ts
import { apiClient } from './client'
import type { CustomFieldDefinition, CustomFieldOption } from '@/types'

export const customFieldsApi = {
  listDefinitions: (projectKey: string) =>
    apiClient.get<CustomFieldDefinition[]>(`/projects/${projectKey}/custom-fields`),

  createDefinition: (projectKey: string, data: { name: string; type: string; required: boolean; sortOrder: number }) =>
    apiClient.post<CustomFieldDefinition>(`/projects/${projectKey}/custom-fields`, data),

  updateDefinition: (projectKey: string, id: string, data: { name: string; type: string; required: boolean; sortOrder: number }) =>
    apiClient.put<CustomFieldDefinition>(`/projects/${projectKey}/custom-fields/${id}`, data),

  reorder: (projectKey: string, reorders: { id: string; sortOrder: number }[]) =>
    apiClient.put(`/projects/${projectKey}/custom-fields/reorder`, reorders),

  deleteDefinition: (projectKey: string, id: string) =>
    apiClient.delete(`/projects/${projectKey}/custom-fields/${id}`),

  createOption: (projectKey: string, fieldId: string, data: { label: string; sortOrder: number }) =>
    apiClient.post<CustomFieldOption>(`/projects/${projectKey}/custom-fields/${fieldId}/options`, data),

  updateOption: (projectKey: string, fieldId: string, optId: string, data: { label: string; sortOrder: number }) =>
    apiClient.put<CustomFieldOption>(`/projects/${projectKey}/custom-fields/${fieldId}/options/${optId}`, data),

  deleteOption: (projectKey: string, fieldId: string, optId: string) =>
    apiClient.delete(`/projects/${projectKey}/custom-fields/${fieldId}/options/${optId}`),
}
```

- [ ] **Step 3: Create `hooks/useCustomFields.ts`**

```typescript
// frontend/src/hooks/useCustomFields.ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { customFieldsApi } from '@/api/customFields'
import type { CustomFieldDefinition } from '@/types'

export function useCustomFields(projectKey: string) {
  return useQuery({
    queryKey: ['custom-fields', projectKey],
    queryFn: () => customFieldsApi.listDefinitions(projectKey).then(r => r.data),
  })
}

export function useCreateCustomField(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: { name: string; type: string; required: boolean; sortOrder: number }) =>
      customFieldsApi.createDefinition(projectKey, data).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['custom-fields', projectKey] }),
  })
}

export function useUpdateCustomField(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, ...data }: { id: string; name: string; type: string; required: boolean; sortOrder: number }) =>
      customFieldsApi.updateDefinition(projectKey, id, data).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['custom-fields', projectKey] }),
  })
}

export function useDeleteCustomField(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => customFieldsApi.deleteDefinition(projectKey, id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['custom-fields', projectKey] }),
  })
}

export function useReorderCustomFields(projectKey: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (reorders: { id: string; sortOrder: number }[]) =>
      customFieldsApi.reorder(projectKey, reorders),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['custom-fields', projectKey] }),
  })
}

export function useCreateOption(projectKey: string, fieldId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: { label: string; sortOrder: number }) =>
      customFieldsApi.createOption(projectKey, fieldId, data).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['custom-fields', projectKey] }),
  })
}

export function useUpdateOption(projectKey: string, fieldId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ optId, ...data }: { optId: string; label: string; sortOrder: number }) =>
      customFieldsApi.updateOption(projectKey, fieldId, optId, data).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['custom-fields', projectKey] }),
  })
}

export function useDeleteOption(projectKey: string, fieldId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (optId: string) => customFieldsApi.deleteOption(projectKey, fieldId, optId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['custom-fields', projectKey] }),
  })
}
```

- [ ] **Step 4: Extend `api/issues.ts`** to forward custom field filter params

Update the `list` function:
```typescript
list: (projectKey: string, page = 0, size = 50, labelId?: string, fixVersionId?: string, affectsVersionId?: string, customFieldFilters?: Record<string, string>) =>
  apiClient.get<Page<Issue>>(`/projects/${projectKey}/issues`, {
    params: {
      page, size,
      ...(labelId ? { labelId } : {}),
      ...(fixVersionId ? { fixVersionId } : {}),
      ...(affectsVersionId ? { affectsVersionId } : {}),
      ...(customFieldFilters
        ? Object.entries(customFieldFilters).map(([k, v]) => [`cf`, `${k}:${v}`]).reduce(
            (acc, [key, val]) => { (acc[key] ??= []).push(val); return acc },
            {} as Record<string, string[]>
          )
        : {}),
    },
    // Use paramsSerializer for repeated `cf` params
  }),
```

**Note:** Axios repeats array values by default with `cf[0]=...&cf[1]=...`. To get `cf=...&cf=...`, use a custom params serializer. Update the `apiClient` configuration in `api/client.ts` to use `qs.stringify` with `arrayFormat: 'repeat'`:

First check if `qs` is installed: `grep "\"qs\"" frontend/package.json`. If not installed, run `cd frontend && npm install qs` and `npm install --save-dev @types/qs`.

Then update `api/issues.ts`:
```typescript
import qs from 'qs'

list: (projectKey: string, page = 0, size = 50, labelId?: string, fixVersionId?: string, affectsVersionId?: string, customFieldFilters?: Record<string, string>) => {
  const cf = customFieldFilters
    ? Object.entries(customFieldFilters).map(([k, v]) => `${k}:${v}`)
    : undefined
  return apiClient.get<Page<Issue>>(`/projects/${projectKey}/issues`, {
    params: { page, size, ...(labelId ? { labelId } : {}), ...(fixVersionId ? { fixVersionId } : {}), ...(affectsVersionId ? { affectsVersionId } : {}), ...(cf ? { cf } : {}) },
    paramsSerializer: params => qs.stringify(params, { arrayFormat: 'repeat' }),
  })
},
```

- [ ] **Step 5: Extend `hooks/useIssues.ts`**

Update `IssueListOpts` and `useIssues`:
```typescript
interface IssueListOpts {
  labelId?: string
  fixVersionId?: string
  affectsVersionId?: string
  customFieldFilters?: Record<string, string>
}

export function useIssues(projectKey: string, opts: IssueListOpts = {}) {
  const { labelId, fixVersionId, affectsVersionId, customFieldFilters } = opts
  return useQuery({
    queryKey: ['issues', projectKey, { labelId, fixVersionId, affectsVersionId, customFieldFilters }],
    queryFn: () => issuesApi.list(projectKey, 0, 50, labelId, fixVersionId, affectsVersionId, customFieldFilters).then(r => r.data)
  })
}
```

Also update `useCreateIssue` to accept `customFieldValues` in the mutation payload:
```typescript
mutationFn: (data: { title: string; type?: string; priority?: string; description?: string; customFieldValues?: { fieldId: string; value: string | null }[] }) =>
  issuesApi.create(projectKey, data).then(r => r.data),
```

- [ ] **Step 6: Verify TypeScript compiles**

Run: `cd frontend && npm run build`
Expected: no type errors. Fix any before continuing.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/types/index.ts frontend/src/api/ frontend/src/hooks/useIssues.ts frontend/src/hooks/useCustomFields.ts
git commit -m "feat(customfields): add frontend types, API client, and hooks"
```

---

### Task 6: `CustomFieldInput` Component + `CustomFieldsPage` + Nav/Routing

**Files:**
- Create: `frontend/src/components/issue/CustomFieldInput.tsx`
- Create: `frontend/src/pages/projects/settings/CustomFieldsPage.tsx`
- Modify: `frontend/src/layouts/AppLayout.tsx`
- Modify: `frontend/src/app/router.tsx`

**Interfaces:**
- Consumes: `CustomFieldDefinition`, `CustomFieldValue`, `CustomFieldOption` types; `useCustomFields`, `useCreateCustomField`, `useUpdateCustomField`, `useDeleteCustomField`, `useReorderCustomFields`, `useCreateOption`, `useUpdateOption`, `useDeleteOption` hooks
- Produces:
  - `<CustomFieldInput definition={CustomFieldDefinition} value={CustomFieldValue | undefined} onChange={(value: string | null) => void} />` — renders the correct input type, shows required marker
  - `CustomFieldsPage` at `/p/:key/settings/custom-fields`
  - Nav link in AppLayout after "Versions" entry

- [ ] **Step 1: Create `CustomFieldInput.tsx`**

```tsx
// frontend/src/components/issue/CustomFieldInput.tsx
import type { CustomFieldDefinition, CustomFieldValue } from '@/types'

interface Props {
  definition: CustomFieldDefinition
  value: CustomFieldValue | undefined
  onChange: (value: string | null) => void
  disabled?: boolean
}

export function CustomFieldInput({ definition, value, onChange, disabled }: Props) {
  const label = definition.required ? `${definition.name} *` : definition.name
  const inputClass = "w-full bg-gray-700 border border-gray-600 rounded px-3 py-1.5 text-sm text-white outline-none focus:border-blue-500"

  switch (definition.type) {
    case 'TEXT':
      return (
        <input
          type="text"
          className={inputClass}
          placeholder={label}
          value={value?.textValue ?? ''}
          disabled={disabled}
          onChange={e => onChange(e.target.value || null)}
          onBlur={e => onChange(e.target.value || null)}
        />
      )
    case 'NUMBER':
      return (
        <input
          type="number"
          className={inputClass}
          placeholder={label}
          value={value?.numberValue ?? ''}
          disabled={disabled}
          onChange={e => onChange(e.target.value || null)}
          onBlur={e => onChange(e.target.value || null)}
        />
      )
    case 'DATE':
      return (
        <input
          type="date"
          className={inputClass}
          value={value?.dateValue ?? ''}
          disabled={disabled}
          onChange={e => onChange(e.target.value || null)}
        />
      )
    case 'CHECKBOX':
      return (
        <input
          type="checkbox"
          className="w-4 h-4 cursor-pointer"
          checked={value?.booleanValue ?? false}
          disabled={disabled}
          onChange={e => onChange(e.target.checked ? 'true' : 'false')}
        />
      )
    case 'DROPDOWN':
      return (
        <select
          className={inputClass}
          value={value?.optionId ?? ''}
          disabled={disabled}
          onChange={e => onChange(e.target.value || null)}
        >
          <option value="">— None —</option>
          {definition.options?.map(opt => (
            <option key={opt.id} value={opt.id}>{opt.label}</option>
          ))}
        </select>
      )
    default:
      return null
  }
}
```

- [ ] **Step 2: Create `CustomFieldsPage.tsx`**

This page manages field definitions with drag & drop reordering (via `@dnd-kit/sortable`) and inline option management for DROPDOWN fields.

```tsx
// frontend/src/pages/projects/settings/CustomFieldsPage.tsx
import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { DndContext, closestCenter, DragEndEvent } from '@dnd-kit/core'
import { SortableContext, verticalListSortingStrategy, arrayMove, useSortable } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import {
  useCustomFields, useCreateCustomField, useUpdateCustomField,
  useDeleteCustomField, useReorderCustomFields,
  useCreateOption, useUpdateOption, useDeleteOption
} from '@/hooks/useCustomFields'
import type { CustomFieldDefinition, CustomFieldOption } from '@/types'

const FIELD_TYPES = ['TEXT', 'NUMBER', 'DATE', 'DROPDOWN', 'CHECKBOX'] as const

function SortableField({
  field,
  onEdit,
  onDelete,
}: {
  field: CustomFieldDefinition
  onEdit: () => void
  onDelete: () => void
}) {
  const { attributes, listeners, setNodeRef, transform, transition } = useSortable({ id: field.id })
  const style = { transform: CSS.Transform.toString(transform), transition }

  return (
    <div ref={setNodeRef} style={style}
      className="flex items-center gap-3 px-4 py-3 bg-gray-900 border border-gray-800 rounded-lg">
      <span {...attributes} {...listeners} className="cursor-grab text-gray-500 select-none">⠿</span>
      <span className="text-sm text-white font-medium flex-1">{field.name}</span>
      <span className="text-xs text-gray-500 bg-gray-800 px-2 py-0.5 rounded">{field.type}</span>
      {field.required && <span className="text-xs text-red-400">required</span>}
      <button onClick={onEdit} className="text-xs text-gray-400 hover:text-white px-2 py-1 rounded hover:bg-gray-700">Edit</button>
      <button onClick={onDelete} className="text-xs text-red-400 hover:text-red-300 px-2 py-1 rounded hover:bg-gray-700">Delete</button>
    </div>
  )
}

export function CustomFieldsPage() {
  const { key } = useParams<{ key: string }>()
  const { data: fields = [], isLoading } = useCustomFields(key!)
  const createField = useCreateCustomField(key!)
  const updateField = useUpdateCustomField(key!)
  const deleteField = useDeleteCustomField(key!)
  const reorderFields = useReorderCustomFields(key!)
  const createOption = useCreateOption(key!, '')  // fieldId resolved per action

  const [showCreate, setShowCreate] = useState(false)
  const [editingField, setEditingField] = useState<CustomFieldDefinition | null>(null)
  const [expandedField, setExpandedField] = useState<string | null>(null)
  const [newName, setNewName] = useState('')
  const [newType, setNewType] = useState<string>('TEXT')
  const [newRequired, setNewRequired] = useState(false)
  const [newOptionLabel, setNewOptionLabel] = useState<Record<string, string>>({})
  const [apiError, setApiError] = useState('')

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault()
    if (!newName.trim()) return
    try {
      await createField.mutateAsync({ name: newName.trim(), type: newType, required: newRequired, sortOrder: fields.length })
      setShowCreate(false)
      setNewName('')
      setNewType('TEXT')
      setNewRequired(false)
      setApiError('')
    } catch {
      setApiError('A field with that name already exists.')
    }
  }

  async function handleUpdate(e: React.FormEvent) {
    e.preventDefault()
    if (!editingField || !newName.trim()) return
    try {
      await updateField.mutateAsync({ id: editingField.id, name: newName.trim(), type: editingField.type, required: newRequired, sortOrder: editingField.sortOrder })
      setEditingField(null)
      setApiError('')
    } catch {
      setApiError('A field with that name already exists.')
    }
  }

  async function handleDelete(id: string) {
    if (!confirm('Delete this field? All values will be lost.')) return
    await deleteField.mutateAsync(id)
  }

  function handleDragEnd(e: DragEndEvent) {
    const { active, over } = e
    if (!over || active.id === over.id) return
    const oldIdx = fields.findIndex(f => f.id === active.id)
    const newIdx = fields.findIndex(f => f.id === over.id)
    const reordered = arrayMove(fields, oldIdx, newIdx)
    reorderFields.mutate(reordered.map((f, i) => ({ id: f.id, sortOrder: i })))
  }

  function startEdit(field: CustomFieldDefinition) {
    setEditingField(field)
    setNewName(field.name)
    setNewRequired(field.required)
    setApiError('')
  }

  if (isLoading) return <div className="text-gray-400 p-6">Loading…</div>

  return (
    <div className="p-6 space-y-6 max-w-2xl">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Custom Fields</h1>
        {!showCreate && !editingField && (
          <button onClick={() => { setShowCreate(true); setApiError('') }}
            className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded text-sm font-medium">
            + New Field
          </button>
        )}
      </div>

      {apiError && <p className="text-sm text-red-400">{apiError}</p>}

      {showCreate && (
        <form onSubmit={handleCreate} className="flex flex-col gap-3 p-4 bg-gray-800 rounded-lg border border-gray-700">
          <div className="flex gap-2">
            <input value={newName} onChange={e => setNewName(e.target.value)} placeholder="Field name" autoFocus
              className="flex-1 bg-gray-700 border border-gray-600 rounded px-3 py-1.5 text-sm text-white outline-none focus:border-blue-500" />
            <select value={newType} onChange={e => setNewType(e.target.value)}
              className="bg-gray-700 border border-gray-600 rounded px-3 py-1.5 text-sm text-white outline-none">
              {FIELD_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
            </select>
          </div>
          <label className="flex items-center gap-2 text-sm text-gray-300 cursor-pointer">
            <input type="checkbox" checked={newRequired} onChange={e => setNewRequired(e.target.checked)} />
            Required
          </label>
          <div className="flex gap-2">
            <button type="submit" className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-1.5 rounded text-sm font-medium">Create</button>
            <button type="button" onClick={() => { setShowCreate(false); setApiError('') }} className="text-gray-400 hover:text-white px-3 py-1.5 text-sm">Cancel</button>
          </div>
        </form>
      )}

      <DndContext collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
        <SortableContext items={fields.map(f => f.id)} strategy={verticalListSortingStrategy}>
          <div className="flex flex-col gap-2">
            {fields.map(field => (
              <div key={field.id}>
                {editingField?.id === field.id ? (
                  <form onSubmit={handleUpdate} className="flex flex-col gap-3 p-4 bg-gray-800 rounded-lg border border-gray-700">
                    <input value={newName} onChange={e => setNewName(e.target.value)} autoFocus
                      className="bg-gray-700 border border-gray-600 rounded px-3 py-1.5 text-sm text-white outline-none focus:border-blue-500" />
                    <label className="flex items-center gap-2 text-sm text-gray-300 cursor-pointer">
                      <input type="checkbox" checked={newRequired} onChange={e => setNewRequired(e.target.checked)} />
                      Required
                    </label>
                    <div className="flex gap-2">
                      <button type="submit" className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-1.5 rounded text-sm font-medium">Save</button>
                      <button type="button" onClick={() => { setEditingField(null); setApiError('') }} className="text-gray-400 hover:text-white px-3 py-1.5 text-sm">Cancel</button>
                    </div>
                  </form>
                ) : (
                  <div className="flex flex-col gap-1">
                    <SortableField field={field} onEdit={() => startEdit(field)} onDelete={() => handleDelete(field.id)} />
                    {field.type === 'DROPDOWN' && (
                      <DropdownOptionsPanel
                        projectKey={key!}
                        field={field}
                        expanded={expandedField === field.id}
                        onToggle={() => setExpandedField(expandedField === field.id ? null : field.id)}
                        newOptionLabel={newOptionLabel[field.id] ?? ''}
                        onNewOptionLabelChange={label => setNewOptionLabel(prev => ({ ...prev, [field.id]: label }))}
                      />
                    )}
                  </div>
                )}
              </div>
            ))}
            {fields.length === 0 && !showCreate && (
              <p className="text-sm text-gray-500 py-8 text-center">No custom fields yet.</p>
            )}
          </div>
        </SortableContext>
      </DndContext>
    </div>
  )
}

function DropdownOptionsPanel({
  projectKey, field, expanded, onToggle, newOptionLabel, onNewOptionLabelChange
}: {
  projectKey: string
  field: CustomFieldDefinition
  expanded: boolean
  onToggle: () => void
  newOptionLabel: string
  onNewOptionLabelChange: (label: string) => void
}) {
  const createOption = useCreateOption(projectKey, field.id)
  const deleteOption = useDeleteOption(projectKey, field.id)

  async function handleAddOption(e: React.FormEvent) {
    e.preventDefault()
    if (!newOptionLabel.trim()) return
    await createOption.mutateAsync({ label: newOptionLabel.trim(), sortOrder: (field.options?.length ?? 0) })
    onNewOptionLabelChange('')
  }

  return (
    <div className="ml-6 border-l border-gray-700 pl-3">
      <button onClick={onToggle} className="text-xs text-gray-400 hover:text-white py-1">
        {expanded ? '▾ Hide options' : '▸ Options'} ({field.options?.length ?? 0})
      </button>
      {expanded && (
        <div className="flex flex-col gap-1 mt-1">
          {field.options?.map(opt => (
            <div key={opt.id} className="flex items-center gap-2 text-sm text-gray-300">
              <span className="flex-1">{opt.label}</span>
              <button onClick={() => deleteOption.mutate(opt.id)} className="text-xs text-red-400 hover:text-red-300">✕</button>
            </div>
          ))}
          <form onSubmit={handleAddOption} className="flex gap-2 mt-1">
            <input value={newOptionLabel} onChange={e => onNewOptionLabelChange(e.target.value)}
              placeholder="New option label" className="flex-1 bg-gray-700 border border-gray-600 rounded px-2 py-1 text-xs text-white outline-none focus:border-blue-500" />
            <button type="submit" className="bg-blue-600 hover:bg-blue-700 text-white px-3 py-1 rounded text-xs">Add</button>
          </form>
        </div>
      )}
    </div>
  )
}
```

- [ ] **Step 3: Add nav link in `AppLayout.tsx`**

After the Versions nav link entry (line ~91), add:
```tsx
<NavLink to={`/p/${projectKey}/settings/custom-fields`} className={subNavLinkClass}>
  Custom Fields
</NavLink>
```

- [ ] **Step 4: Add route in `app/router.tsx`**

After the versions route import, add:
```tsx
import { CustomFieldsPage } from '@/pages/projects/settings/CustomFieldsPage'
```

After the versions route entry, add:
```tsx
{ path: '/p/:key/settings/custom-fields', element: <CustomFieldsPage /> },
```

- [ ] **Step 5: Verify TypeScript compiles**

Run: `cd frontend && npm run build`
Expected: no errors.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/issue/CustomFieldInput.tsx \
        frontend/src/pages/projects/settings/CustomFieldsPage.tsx \
        frontend/src/layouts/AppLayout.tsx \
        frontend/src/app/router.tsx
git commit -m "feat(customfields): add CustomFieldInput component, CustomFieldsPage settings, nav and routing"
```

---

### Task 7: Issue List — Custom Field Filter Controls

**Files:**
- Modify: `frontend/src/pages/issues/IssueListPage.tsx`

**Interfaces:**
- Consumes: `useCustomFields(key!)` for field definitions; `useIssues` with `customFieldFilters` from Task 5
- Produces: Dynamic filter controls below the existing Label/Version filters — one per custom field definition; `cf=fieldId:value` params in URL; "Clear filters" button extended to cover custom field params

- [ ] **Step 1: Add custom field filter state and controls to `IssueListPage.tsx`**

At the top of the component, add:
```tsx
import { useCustomFields } from '@/hooks/useCustomFields'
```

After the existing version/label state declarations, add:
```tsx
const { data: customFieldDefs = [] } = useCustomFields(key!)

// Build customFieldFilters map from URL params (key = fieldId, value = raw filter value)
const customFieldFilters: Record<string, string> = {}
customFieldDefs.forEach(def => {
  const v = searchParams.get(`cf_${def.id}`)
  if (v) customFieldFilters[def.id] = v
})
```

Update the `useIssues` call to include `customFieldFilters`:
```tsx
const { data: page, isLoading } = useIssues(key!, { labelId, fixVersionId, affectsVersionId, customFieldFilters: Object.keys(customFieldFilters).length > 0 ? customFieldFilters : undefined })
```

Update `setParam` to use the `cf_${fieldId}` key:
```tsx
function setCfParam(fieldId: string, value: string | undefined) {
  setSearchParams(prev => {
    const n = new URLSearchParams(prev)
    if (value) n.set(`cf_${fieldId}`, value)
    else n.delete(`cf_${fieldId}`)
    return n
  })
}
```

In the filter toolbar (after the affectsVersionId select), add:
```tsx
{customFieldDefs.map(def => (
  <div key={def.id} className="flex flex-col">
    <span className="text-xs text-gray-500 mb-0.5">{def.name}</span>
    {def.type === 'DROPDOWN' ? (
      <select
        value={customFieldFilters[def.id] ?? ''}
        onChange={e => setCfParam(def.id, e.target.value || undefined)}
        className="bg-gray-800 border border-gray-700 text-sm text-white rounded px-3 py-1.5 outline-none"
      >
        <option value="">All</option>
        {def.options?.map(opt => <option key={opt.id} value={opt.id}>{opt.label}</option>)}
      </select>
    ) : def.type === 'CHECKBOX' ? (
      <select
        value={customFieldFilters[def.id] ?? ''}
        onChange={e => setCfParam(def.id, e.target.value || undefined)}
        className="bg-gray-800 border border-gray-700 text-sm text-white rounded px-3 py-1.5 outline-none"
      >
        <option value="">All</option>
        <option value="true">Yes</option>
        <option value="false">No</option>
      </select>
    ) : (
      <input
        type={def.type === 'NUMBER' ? 'number' : def.type === 'DATE' ? 'date' : 'text'}
        value={customFieldFilters[def.id] ?? ''}
        onChange={e => setCfParam(def.id, e.target.value || undefined)}
        placeholder={`Filter by ${def.name}`}
        className="bg-gray-800 border border-gray-700 text-sm text-white rounded px-3 py-1.5 outline-none"
      />
    )}
  </div>
))}
```

Update the "Clear filters" condition and button:
```tsx
{(labelId || fixVersionId || affectsVersionId || Object.keys(customFieldFilters).length > 0) && (
  <button
    onClick={() => setSearchParams({})}
    className="text-xs text-gray-400 hover:text-white"
  >
    ✕ Clear filters
  </button>
)}
```

- [ ] **Step 2: Verify TypeScript compiles**

Run: `cd frontend && npm run build`
Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/pages/issues/IssueListPage.tsx
git commit -m "feat(customfields): add dynamic custom field filter controls to issue list"
```

---

### Task 8: Issue Create Form — Custom Fields Section

**Files:**
- Modify: `frontend/src/pages/issues/IssueListPage.tsx`

The inline create form in `IssueListPage` currently only accepts a title. Extend it to show custom fields.

**Interfaces:**
- Consumes: `CustomFieldInput` from Task 6; `useCustomFields` from Task 5; `useCreateIssue` updated payload from Task 5

- [ ] **Step 1: Extend the inline create form**

Add import:
```tsx
import { CustomFieldInput } from '@/components/issue/CustomFieldInput'
```

Add state for custom field values in the form:
```tsx
const [cfValues, setCfValues] = useState<Record<string, string>>({})
```

Update `handleCreate` to include `customFieldValues`:
```tsx
const handleCreate = async (e: React.FormEvent) => {
  e.preventDefault()
  if (!title.trim()) return

  // Validate required fields
  const missingRequired = customFieldDefs
    .filter(d => d.required && !cfValues[d.id])
    .map(d => d.name)
  if (missingRequired.length > 0) {
    alert(`Required fields missing: ${missingRequired.join(', ')}`)
    return
  }

  const customFieldValues = Object.entries(cfValues).map(([fieldId, value]) => ({ fieldId, value }))
  await createIssue.mutateAsync({ title, customFieldValues: customFieldValues.length > 0 ? customFieldValues : undefined })
  setTitle('')
  setCfValues({})
  setShowForm(false)
}
```

In the create form JSX, after the title input and before the buttons, add:
```tsx
{customFieldDefs.length > 0 && (
  <div className="flex flex-col gap-2 mt-2">
    <span className="text-xs text-gray-500 uppercase tracking-wider">Custom Fields</span>
    {customFieldDefs.map(def => (
      <div key={def.id} className="flex flex-col gap-0.5">
        <label className="text-xs text-gray-400">
          {def.name}{def.required ? ' *' : ''}
        </label>
        <CustomFieldInput
          definition={def}
          value={cfValues[def.id] ? { fieldId: def.id, fieldName: def.name, type: def.type, required: def.required, textValue: def.type === 'TEXT' ? cfValues[def.id] : undefined, numberValue: def.type === 'NUMBER' ? parseFloat(cfValues[def.id]) : undefined, dateValue: def.type === 'DATE' ? cfValues[def.id] : undefined, booleanValue: def.type === 'CHECKBOX' ? cfValues[def.id] === 'true' : undefined, optionId: def.type === 'DROPDOWN' ? cfValues[def.id] : undefined } : undefined}
          onChange={val => setCfValues(prev => val === null ? (({ [def.id]: _, ...rest }) => rest)(prev) : { ...prev, [def.id]: val })}
        />
      </div>
    ))}
  </div>
)}
```

- [ ] **Step 2: Verify TypeScript compiles**

Run: `cd frontend && npm run build`
Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/pages/issues/IssueListPage.tsx
git commit -m "feat(customfields): add custom fields section to issue create form"
```

---

### Task 9: IssueDetailPage — Custom Fields Sidebar

**Files:**
- Modify: `frontend/src/pages/issues/IssueDetailPage.tsx`

**Interfaces:**
- Consumes: `CustomFieldInput` from Task 6; `useCustomFields` from Task 5; `issue.customFields` from Task 3; `patch()` helper already in `IssueDetailPage`

- [ ] **Step 1: Add custom fields sidebar section to `IssueDetailPage.tsx`**

Add imports:
```tsx
import { useCustomFields } from '@/hooks/useCustomFields'
import { CustomFieldInput } from '@/components/issue/CustomFieldInput'
```

Add hook call at the top of the component (after `useVersions`):
```tsx
const { data: customFieldDefs = [] } = useCustomFields(key!)
```

In the sidebar JSX, add after the Affects Versions field section:
```tsx
{customFieldDefs.map(def => {
  const cfValue = issue.customFields?.find(cf => cf.fieldId === def.id)
  return (
    <SidebarField key={def.id} label={def.required ? `${def.name} *` : def.name}>
      <CustomFieldInput
        definition={def}
        value={cfValue}
        onChange={val => patch({
          customFieldValues: [{ fieldId: def.id, value: val }]
        })}
      />
    </SidebarField>
  )
})}
```

- [ ] **Step 2: Verify TypeScript compiles**

Run: `cd frontend && npm run build`
Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/pages/issues/IssueDetailPage.tsx
git commit -m "feat(customfields): add custom fields section to issue detail sidebar"
```

---

### Task 10: MkDocs Wiki Page

**Files:**
- Create: `mkdocs/docs/developer-guide/backend/custom-fields.md`
- Modify: `mkdocs.yml`

- [ ] **Step 1: Check the mkdocs docs directory for custom fields location**

```bash
ls mkdocs/docs/developer-guide/backend/
```

Expected: `labels.md`, `versions.md` (and others). Create the file alongside them.

- [ ] **Step 2: Write the wiki page**

```markdown
# Custom Fields

Custom fields are project-scoped field definitions that extend each issue with typed values. They support five types and are fully filterable in the issue list.

## Field Types

| Type       | Storage Column  | Filter UX               |
|------------|----------------|-------------------------|
| TEXT       | `text_value`    | Text input (ILIKE)      |
| NUMBER     | `number_value`  | Text input (exact match)|
| DATE       | `date_value`    | Date input (exact match)|
| DROPDOWN   | `option_id`     | Select from options     |
| CHECKBOX   | `boolean_value` | true / false select     |

## Database Schema (V25)

Three tables: `custom_field_definitions` (project-scoped, name, type, required, sort_order), `custom_field_options` (per DROPDOWN field, label, sort_order), `custom_field_values` (per-issue per-field, typed value columns).

`UNIQUE (issue_id, field_id)` — one value row per field per issue.
`option_id ON DELETE SET NULL` — deleting a dropdown option leaves the value row with a null option (not cascade-deleted).

## Backend Module

`com.taskowolf.customfields` — mirrors the labels and versions modules. REST base: `/api/v1/projects/{key}/custom-fields`.

Custom field values flow through `CreateIssueRequest` and `UpdateIssueRequest` as `customFieldValues: List<CustomFieldValueInput>?` (null = no change). The `IssueService` validates required fields on create and coerces string values to typed columns.

## Issue List Filtering (JPA Specification)

`IssueService.findByProject()` uses `JpaSpecificationExecutor<Issue>` and `IssueSpecification` factory methods. All filters (project, assignee, overdue, label, fix version, affects version, custom fields) are AND-combined via `Specification.and()`. Custom field filters are passed as repeated `cf=fieldId:value` query params; the controller parses them into a `Map<UUID, String>` and looks up the field type to build typed predicates.

## Frontend

- `useCustomFields(projectKey)` — React Query hook for field definitions with options
- `CustomFieldInput` — renders the correct control per type
- Settings at `/p/:key/settings/custom-fields` (Custom Fields nav link in sidebar)
- Issue create form shows all project fields; required fields block submit
- Issue detail sidebar shows all fields with click-to-edit via PATCH
- Issue list toolbar shows one filter control per field

## Out of Scope

Version lifecycle, multi-select dropdown, custom field audit trail, CSV export of custom field values.
```

- [ ] **Step 3: Add nav entry to `mkdocs.yml`**

After the versions entry (line ~56):
```yaml
          - custom-fields: developer-guide/backend/custom-fields.md
```

- [ ] **Step 4: Commit**

```bash
git add mkdocs/docs/developer-guide/backend/custom-fields.md mkdocs.yml
git commit -m "docs(wiki): add custom fields module page (Phase 9d)"
```

---

## Self-Review Checklist

After all tasks are complete:

- [ ] `CustomFieldServiceTest` covers create, conflict, update, not-found cross-project, reorder, delete, `getFieldType` known/unknown
- [ ] `IssueServiceTest` passes with new constructor params (3 new repos mocked, existing create tests stub `findByProjectIdOrderBySortOrder` → `emptyList()`)
- [ ] `IssueSpecification` covers all 7 filter types (inProject, assignedTo, overdue, hasLabel, hasFixVersion, hasAffectsVersion, hasCustomFieldValue × 5 field types)
- [ ] `IssueResponse.from()` passes `customFields` and defaults to `emptyList()` on list endpoint
- [ ] Required field validation fires on `IssueService.create()` not `update()`
- [ ] `PUT /reorder` body is `List<{id: UUID, sortOrder: Int}>` — matches `List<ReorderEntry>` in Kotlin
- [ ] Frontend `cf_${fieldId}` URL params are converted to `customFieldFilters[fieldId]` before passing to `issuesApi.list()`, which serializes them as `cf=fieldId:value` repeated params
- [ ] `qs` library installed and paramsSerializer wired in `issues.ts`
- [ ] `CustomFieldsPage` drag-and-drop calls `reorderFields.mutate()` on `DragEnd` with new sort order
- [ ] AppLayout nav link added after Versions entry, router entry added for `/p/:key/settings/custom-fields`
- [ ] Wiki page created and added to `mkdocs.yml` nav
