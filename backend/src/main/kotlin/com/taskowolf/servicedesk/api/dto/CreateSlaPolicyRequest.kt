package com.taskowolf.servicedesk.api.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

data class CreateSlaPolicyRequest(
    @field:NotBlank val name: String,
    val priority: String,
    @field:Min(1) val responseMinutes: Int,
    @field:Min(1) val resolutionMinutes: Int
)
