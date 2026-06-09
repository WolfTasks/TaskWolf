package com.taskowolf.sprints.api.dto

import jakarta.validation.constraints.NotBlank
import java.time.LocalDate

data class CreateSprintRequest(
    @field:NotBlank val name: String,
    val goal: String? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null
)
