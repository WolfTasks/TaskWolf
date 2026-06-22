package com.taskowolf.integrations.api.dto

data class CreateProjectIntegrationRequest(
    val provider: String,
    val repoUrl: String? = null
)
