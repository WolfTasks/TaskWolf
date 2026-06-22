package com.taskowolf.integrations.api.dto

import java.util.UUID

data class CreateProjectIntegrationResponse(
    val id: UUID,
    val provider: String,
    val webhookUrl: String,
    val plaintextSecret: String,
    val repoUrl: String?
)
