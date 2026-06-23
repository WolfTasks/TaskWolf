package com.taskowolf.auth.api.dto

data class SsoConfigRequest(
    val name: String,
    val issuerUrl: String,
    val clientId: String,
    val clientSecret: String?,
    val enabled: Boolean = true,
    val autoProvision: Boolean = true
)
