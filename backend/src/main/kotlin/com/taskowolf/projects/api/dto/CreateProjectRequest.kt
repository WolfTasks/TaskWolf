package com.taskowolf.projects.api.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class CreateProjectRequest(
    @field:NotBlank
    @field:Size(min = 2, max = 10)
    @field:Pattern(regexp = "[A-Z0-9]+", message = "{project.key.pattern}")
    val key: String,

    @field:NotBlank val name: String,
    val description: String? = null
)
