package com.taskowolf.auth.api.dto

import jakarta.validation.constraints.NotBlank

data class UpdateLanguageRequest(
    @field:NotBlank val language: String
)
