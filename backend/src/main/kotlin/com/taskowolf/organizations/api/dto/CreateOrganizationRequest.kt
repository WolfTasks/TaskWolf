package com.taskowolf.organizations.api.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class CreateOrganizationRequest(
    @field:NotBlank val name: String,
    @field:NotBlank @field:Pattern(regexp = "^[a-z0-9-]+$", message = "{org.slug.pattern}") val slug: String
)
