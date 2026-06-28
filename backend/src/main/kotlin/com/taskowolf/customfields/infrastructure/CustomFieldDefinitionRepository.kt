package com.taskowolf.customfields.infrastructure

import com.taskowolf.customfields.domain.CustomFieldDefinition
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CustomFieldDefinitionRepository : JpaRepository<CustomFieldDefinition, UUID> {
    fun findByProjectIdOrderBySortOrder(projectId: UUID): List<CustomFieldDefinition>
    fun existsByProjectIdAndName(projectId: UUID, name: String): Boolean
}
