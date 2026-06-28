package com.taskowolf.customfields.infrastructure

import com.taskowolf.customfields.domain.CustomFieldOption
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CustomFieldOptionRepository : JpaRepository<CustomFieldOption, UUID> {
    fun findByFieldIdOrderBySortOrder(fieldId: UUID): List<CustomFieldOption>
}
