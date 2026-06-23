package com.taskowolf.servicedesk.api.dto

data class CreateSlaPolicyRequest(
    val name: String,
    val priority: String,
    val responseMinutes: Int,
    val resolutionMinutes: Int
)
