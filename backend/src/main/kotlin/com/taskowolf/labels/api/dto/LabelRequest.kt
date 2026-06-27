package com.taskowolf.labels.api.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class LabelRequest(
    @field:NotBlank @field:Size(max = 50)
    val name: String,

    @field:NotBlank @field:Pattern(regexp = "^#[0-9a-fA-F]{6}$")
    val color: String
)
