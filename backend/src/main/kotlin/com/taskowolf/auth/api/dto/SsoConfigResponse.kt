package com.taskowolf.auth.api.dto

data class SsoConfigResponse(
    val id: String,
    val name: String,
    val issuerUrl: String,
    val clientId: String,
    val enabled: Boolean,
    val autoProvision: Boolean
)
