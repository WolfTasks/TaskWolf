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
