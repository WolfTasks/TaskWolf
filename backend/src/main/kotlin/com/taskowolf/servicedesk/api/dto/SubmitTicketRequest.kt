package com.taskowolf.servicedesk.api.dto

data class SubmitTicketRequest(
    val title: String,
    val description: String,
    val senderEmail: String?
)
