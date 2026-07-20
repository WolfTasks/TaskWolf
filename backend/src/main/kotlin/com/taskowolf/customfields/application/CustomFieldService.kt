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
            throw ConflictException.keyed("customField.alreadyExists", request.name)
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
            .orElseThrow { NotFoundException.keyed("customField.notFound", fieldId) }
        if (field.name != request.name && definitionRepo.existsByProjectIdAndName(project.id, request.name)) {
            throw ConflictException.keyed("customField.alreadyExists", request.name)
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
            .orElseThrow { NotFoundException.keyed("customField.notFound", fieldId) }
        definitionRepo.delete(field)
    }

    @Transactional
    fun createOption(projectKey: String, fieldId: UUID, request: CustomFieldOptionRequest, actor: User): CustomFieldOption {
        val project = projectService.requireMember(projectKey, actor.id)
        val field = definitionRepo.findById(fieldId)
            .filter { it.project.id == project.id }
            .orElseThrow { NotFoundException.keyed("customField.notFound", fieldId) }
        if (optionRepo.findByFieldIdOrderBySortOrder(field.id).any { it.label.equals(request.label, ignoreCase = false) }) {
            throw ConflictException.keyed("customField.optionAlreadyExists", request.label)
        }
        return optionRepo.save(CustomFieldOption(request.label, request.sortOrder, field))
    }

    @Transactional
    fun updateOption(projectKey: String, fieldId: UUID, optId: UUID, request: CustomFieldOptionRequest, actor: User): CustomFieldOption {
        val project = projectService.requireMember(projectKey, actor.id)
        val field = definitionRepo.findById(fieldId)
            .filter { it.project.id == project.id }
            .orElseThrow { NotFoundException.keyed("customField.notFound", fieldId) }
        val option = optionRepo.findById(optId)
            .filter { it.field.id == field.id }
            .orElseThrow { NotFoundException.keyed("customField.optionNotFound", optId) }
        option.label = request.label
        option.sortOrder = request.sortOrder
        return optionRepo.save(option)
    }

    @Transactional
    fun deleteOption(projectKey: String, fieldId: UUID, optId: UUID, actor: User) {
        val project = projectService.requireMember(projectKey, actor.id)
        val field = definitionRepo.findById(fieldId)
            .filter { it.project.id == project.id }
            .orElseThrow { NotFoundException.keyed("customField.notFound", fieldId) }
        val option = optionRepo.findById(optId)
            .filter { it.field.id == field.id }
            .orElseThrow { NotFoundException.keyed("customField.optionNotFound", optId) }
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
