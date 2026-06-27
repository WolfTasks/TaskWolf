// backend/src/main/kotlin/com/taskowolf/versions/api/dto/VersionRequest.kt
package com.taskowolf.versions.api.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class VersionRequest(
    @field:NotBlank @field:Size(max = 50)
    val name: String
)
