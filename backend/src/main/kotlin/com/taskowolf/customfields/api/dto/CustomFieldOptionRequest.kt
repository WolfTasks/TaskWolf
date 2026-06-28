package com.taskowolf.customfields.api.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CustomFieldOptionRequest(
    @field:NotBlank @field:Size(max = 100) val label: String,
    val sortOrder: Int = 0
)
