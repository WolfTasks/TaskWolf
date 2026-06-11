package com.taskowolf.comments.api.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class EditCommentRequest(
    @field:NotBlank @field:Size(max = 50000)
    val body: String
)
