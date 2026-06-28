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
