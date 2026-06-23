package com.taskowolf.servicedesk.api.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SubmitTicketRequest(
    @field:NotBlank @field:Size(max = 255) val title: String,
    @field:Size(max = 5000) val description: String = "",
    @field:Size(max = 255) val senderEmail: String? = null
)
