package com.taskowolf.core.infrastructure

data class ErrorResponse(
    val code: String,
    val message: String,
    val details: Map<String, String> = emptyMap()
)
