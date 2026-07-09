package com.taskowolf.auth.api.dto

import jakarta.validation.constraints.NotBlank

data class UpdateProfileRequest(
    @field:NotBlank val displayName: String
)
