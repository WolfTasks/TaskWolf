package com.taskowolf.comments.api.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateCommentRequest(
    @field:NotBlank @field:Size(max = 50000)
    val body: String
)
